package main

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
)

const cliGazetteerRow = "479123\tUlyanovsk\tUlyanovsk\tUlyanovsk,Ульяновск\t54.32824\t48.38657\tP\tPPLA\tRU\t\t81\t479105\t\t\t626540\t\t176\tEurope/Ulyanovsk\t2022-10-16\n"
const cliAlternateRows = "1744169\t479123\ten\tUlyanovsk\t1\t\t\t\t\t\n2426202\t479123\tru\tУльяновск\t1\t\t\t\t\t\n"
const cliAdminRows = "RU.81\tUlyanovsk\tUlyanovsk\t479119\n"

func TestRunImportsPinnedCatalogAndWritesDeterministicDiff(t *testing.T) {
	directory := t.TempDir()
	inputDirectory := filepath.Join(directory, "input")
	if err := os.Mkdir(inputDirectory, 0o700); err != nil {
		t.Fatalf("Mkdir() error = %v", err)
	}
	gazetteer := zipBytes(t, "RU.txt", []byte(cliGazetteerRow))
	alternate := zipBytes(t, "RU.txt", []byte(cliAlternateRows))
	artifacts := []struct {
		role, file, member, url string
		data                    []byte
	}{
		{"gazetteer", "RU.zip", "RU.txt", "https://download.geonames.org/export/dump/RU.zip", gazetteer},
		{"alternate_names", "RU-alternateNames.zip", "RU.txt", "https://download.geonames.org/export/dump/alternatenames/RU.zip", alternate},
		{"admin1_codes", "admin1CodesASCII.txt", "", "https://download.geonames.org/export/dump/admin1CodesASCII.txt", []byte(cliAdminRows)},
	}
	manifest := geography.SourceManifest{
		SchemaVersion: "geonames-source-manifest/v1", SourceID: "geonames", SourceRevision: "2026-08-29", CountryCode: "RU",
		License: "CC BY 4.0", LicenseURL: "https://creativecommons.org/licenses/by/4.0/", Attribution: "GeoNames (https://www.geonames.org/)",
	}
	for _, artifact := range artifacts {
		digest := sha256.Sum256(artifact.data)
		manifest.Artifacts = append(manifest.Artifacts, geography.ArtifactManifest{
			Role: artifact.role, URL: artifact.url, CacheFile: artifact.file, ArchiveMember: artifact.member,
			ByteLength: int64(len(artifact.data)), SHA256: hex.EncodeToString(digest[:]),
		})
		if err := os.WriteFile(filepath.Join(inputDirectory, artifact.file), artifact.data, 0o600); err != nil {
			t.Fatalf("write artifact: %v", err)
		}
	}
	mapping := geography.RegionMappingFile{
		SchemaVersion: "geonames-ru-region-map/v1", SourceID: "geonames", CountryCode: "RU",
		Regions: []geography.RegionMapping{{SourceAdminCode: "81", ID: "ru-uly", FederalSubjectCode: "RU-ULY", NameRU: "Ульяновская область"}},
	}
	manifestPath := writeJSON(t, directory, "manifest.json", manifest)
	mappingPath := writeJSON(t, directory, "regions.json", mapping)
	outputPath := filepath.Join(directory, "catalog.json")
	diffPath := filepath.Join(directory, "diff.json")

	if err := run([]string{"-manifest", manifestPath, "-region-map", mappingPath, "-input-dir", inputDirectory, "-output", outputPath}, &bytes.Buffer{}); err != nil {
		t.Fatalf("first run() error = %v", err)
	}
	firstBytes, err := os.ReadFile(outputPath)
	if err != nil {
		t.Fatalf("read first catalog: %v", err)
	}
	var catalog geography.Catalog
	if err := json.Unmarshal(firstBytes, &catalog); err != nil {
		t.Fatalf("decode catalog: %v", err)
	}
	if catalog.Revision.ImportedCities != 1 || catalog.Cities[0].Name != "Ульяновск" {
		t.Fatalf("catalog = %#v", catalog)
	}

	secondPath := filepath.Join(directory, "catalog-second.json")
	if err := run([]string{"-manifest", manifestPath, "-region-map", mappingPath, "-input-dir", inputDirectory, "-output", secondPath, "-previous", outputPath, "-diff-output", diffPath}, &bytes.Buffer{}); err != nil {
		t.Fatalf("second run() error = %v", err)
	}
	secondBytes, err := os.ReadFile(secondPath)
	if err != nil {
		t.Fatalf("read second catalog: %v", err)
	}
	if !bytes.Equal(firstBytes, secondBytes) {
		t.Fatal("identical pinned import was not byte deterministic")
	}
	var diff geography.Diff
	diffBytes, err := os.ReadFile(diffPath)
	if err != nil || json.Unmarshal(diffBytes, &diff) != nil {
		t.Fatalf("read diff: %v", err)
	}
	if len(diff.AddedCityIDs)+len(diff.RemovedCityIDs)+len(diff.ChangedCityIDs) != 0 || diff.SHA256 == "" {
		t.Fatalf("diff = %#v", diff)
	}
}

func TestRunRejectsUnsafeOrIncompletePaths(t *testing.T) {
	for _, args := range [][]string{
		{},
		{"-manifest", "missing", "-region-map", "missing", "-input-dir", "missing", "-output", "missing", "extra"},
		{"-manifest", "missing", "-region-map", "missing", "-input-dir", "missing", "-output", "same", "-previous", "same", "-diff-output", "diff"},
	} {
		if err := run(args, &bytes.Buffer{}); err == nil {
			t.Fatalf("run(%q) succeeded", args)
		}
	}
}

func writeJSON(t *testing.T, directory, name string, value any) string {
	t.Helper()
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		t.Fatalf("marshal %s: %v", name, err)
	}
	path := filepath.Join(directory, name)
	if err := os.WriteFile(path, append(data, '\n'), 0o600); err != nil {
		t.Fatalf("write %s: %v", name, err)
	}
	return path
}

func zipBytes(t *testing.T, name string, contents []byte) []byte {
	t.Helper()
	var target bytes.Buffer
	writer := zip.NewWriter(&target)
	entry, err := writer.Create(name)
	if err != nil {
		t.Fatalf("create zip: %v", err)
	}
	if _, err := entry.Write(contents); err != nil {
		t.Fatalf("write zip: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close zip: %v", err)
	}
	return target.Bytes()
}
