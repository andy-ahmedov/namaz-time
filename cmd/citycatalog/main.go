// Command citycatalog imports a pinned GeoNames Russia source into a
// deterministic NamazTime control-plane catalog. It never assigns prayer
// authorities or prayer policies.
package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
)

const (
	maximumManifestBytes = 128 * 1024
	maximumMappingBytes  = 512 * 1024
	maximumCatalogBytes  = 256 * 1024 * 1024
)

func main() {
	if err := run(os.Args[1:], os.Stderr); err != nil {
		log.New(os.Stderr, "citycatalog: ", 0).Println(err)
		os.Exit(1)
	}
}

func run(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("citycatalog", flag.ContinueOnError)
	flags.SetOutput(stderr)
	manifestPath := flags.String("manifest", "", "pinned GeoNames source manifest JSON")
	regionMapPath := flags.String("region-map", "", "reviewed GeoNames admin1 to ISO 3166-2 mapping JSON")
	inputDirectory := flags.String("input-dir", "", "directory containing the exact pinned source artifacts")
	outputPath := flags.String("output", "", "output catalog JSON path")
	previousPath := flags.String("previous", "", "optional previous catalog JSON")
	diffOutputPath := flags.String("diff-output", "", "required diff JSON output when -previous is used")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if flags.NArg() != 0 || *manifestPath == "" || *regionMapPath == "" || *inputDirectory == "" || *outputPath == "" {
		return errors.New("-manifest, -region-map, -input-dir and -output are required; positional arguments are forbidden")
	}
	if (*previousPath == "") != (*diffOutputPath == "") {
		return errors.New("-previous and -diff-output must be supplied together")
	}
	for _, pair := range [][2]string{{*outputPath, *previousPath}, {*outputPath, *diffOutputPath}, {*previousPath, *diffOutputPath}} {
		if pair[0] != "" && pair[1] != "" && sameCleanPath(pair[0], pair[1]) {
			return errors.New("input and output paths must be distinct")
		}
	}
	manifestBytes, err := readBoundedRegularFile(*manifestPath, maximumManifestBytes)
	if err != nil {
		return fmt.Errorf("read source manifest: %w", err)
	}
	manifest, err := geography.DecodeManifest(manifestBytes)
	if err != nil {
		return err
	}
	mappingBytes, err := readBoundedRegularFile(*regionMapPath, maximumMappingBytes)
	if err != nil {
		return fmt.Errorf("read region mapping: %w", err)
	}
	mapping, err := geography.DecodeRegionMappingFile(mappingBytes)
	if err != nil {
		return err
	}
	if mapping.SourceID != manifest.SourceID || mapping.CountryCode != manifest.CountryCode {
		return errors.New("source manifest and region mapping identities differ")
	}
	inputInfo, err := os.Lstat(*inputDirectory)
	if err != nil || !inputInfo.IsDir() || inputInfo.Mode()&os.ModeSymlink != 0 {
		return errors.New("input directory must be a non-symlink directory")
	}
	artifacts := make(map[string][]byte, len(manifest.Artifacts))
	for _, artifact := range manifest.Artifacts {
		artifactPath := filepath.Join(*inputDirectory, artifact.CacheFile)
		data, readErr := readBoundedRegularFile(artifactPath, maximumCatalogBytes)
		if readErr != nil {
			return fmt.Errorf("read %s artifact: %w", artifact.Role, readErr)
		}
		artifacts[artifact.Role] = data
	}
	catalog, err := geography.Import(geography.ImportRequest{
		Manifest: manifest, Artifacts: artifacts, RegionMappings: mapping.Regions, ExcludedAdminCodes: mapping.ExcludedAdminCodes,
	})
	if err != nil {
		return err
	}
	encoded, err := geography.Encode(catalog)
	if err != nil {
		return err
	}
	if err := writeAtomic(*outputPath, encoded); err != nil {
		return fmt.Errorf("write city catalog: %w", err)
	}
	if *previousPath != "" {
		previousBytes, readErr := readBoundedRegularFile(*previousPath, maximumCatalogBytes)
		if readErr != nil {
			return fmt.Errorf("read previous city catalog: %w", readErr)
		}
		previous, decodeErr := geography.DecodeCatalog(previousBytes)
		if decodeErr != nil {
			return decodeErr
		}
		diff := geography.Compare(previous, catalog)
		diffBytes, encodeErr := encodeJSON(diff)
		if encodeErr != nil {
			return fmt.Errorf("encode city catalog diff: %w", encodeErr)
		}
		if writeErr := writeAtomic(*diffOutputPath, diffBytes); writeErr != nil {
			return fmt.Errorf("write city catalog diff: %w", writeErr)
		}
	}
	return nil
}

func readBoundedRegularFile(path string, maximum int64) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 || info.Size() <= 0 || info.Size() > maximum {
		return nil, errors.New("path must be a bounded regular non-symlink file")
	}
	return os.ReadFile(path)
}

func writeAtomic(path string, data []byte) error {
	directory := filepath.Dir(path)
	if info, err := os.Lstat(directory); err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("output parent must be a non-symlink directory")
	}
	temporary, err := os.CreateTemp(directory, ".citycatalog-*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	removeTemporary := true
	defer func() {
		if removeTemporary {
			_ = os.Remove(temporaryPath)
		}
	}()
	if err := temporary.Chmod(0o644); err != nil {
		_ = temporary.Close()
		return err
	}
	if _, err := temporary.Write(data); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Sync(); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := os.Rename(temporaryPath, path); err != nil {
		return err
	}
	removeTemporary = false
	return nil
}

func encodeJSON(value any) ([]byte, error) {
	encoded, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(encoded, '\n'), nil
}

func sameCleanPath(left, right string) bool {
	leftAbsolute, leftErr := filepath.Abs(left)
	rightAbsolute, rightErr := filepath.Abs(right)
	return leftErr == nil && rightErr == nil && filepath.Clean(leftAbsolute) == filepath.Clean(rightAbsolute)
}
