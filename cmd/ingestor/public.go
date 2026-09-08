package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/onboarding"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

func runInspectPublic(args []string, stdout, stderr io.Writer) int {
	flags := flag.NewFlagSet("inspect-public", flag.ContinueOnError)
	flags.SetOutput(stderr)
	manifestPath := flags.String("manifest", "", "reviewed public-source import manifest")
	catalogPath := flags.String("catalog", "", "complete pinned canonical city catalog")
	rawPath := flags.String("raw", "", "retained original public artifact, outside Git")
	extractionPath := flags.String("extracted", "", "optional hash-bound pinned-method PDF text extraction")
	previousPath := flags.String("previous", "", "optional previous candidate for reproducible diff")
	atText := flags.String("at", "", "canonical UTC qualification time")
	output := flags.String("out", "", "new exclusive inspection JSON output, outside Git")
	if err := flags.Parse(args); err != nil {
		return 2
	}
	if flags.NArg() != 0 || *manifestPath == "" || *catalogPath == "" || *rawPath == "" || *atText == "" || *output == "" {
		fmt.Fprintln(stderr, "inspect-public requires manifest, catalog, raw, at and out")
		return 2
	}
	at, err := time.Parse(time.RFC3339, *atText)
	if err != nil || at.UTC().Format(time.RFC3339) != *atText {
		fmt.Fprintln(stderr, "inspect-public at must be canonical UTC RFC3339")
		return 2
	}
	result, err := inspectPublicFiles(*manifestPath, *catalogPath, *rawPath, *extractionPath, *previousPath, at)
	if err != nil {
		fmt.Fprintf(stderr, "inspect-public failed: %v\n", err)
		return 1
	}
	encoded, err := json.MarshalIndent(result, "", "  ")
	if err != nil {
		fmt.Fprintf(stderr, "encode public inspection: %v\n", err)
		return 1
	}
	file, err := os.OpenFile(*output, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		fmt.Fprintf(stderr, "create new public inspection: %v\n", err)
		return 1
	}
	_, writeErr := file.Write(append(encoded, '\n'))
	closeErr := file.Close()
	if writeErr != nil || closeErr != nil {
		fmt.Fprintf(stderr, "write public inspection: %v; close: %v\n", writeErr, closeErr)
		return 1
	}
	fmt.Fprintf(stdout, "qualified %s: %d days; proof %s\n", result.Candidate.Source.SourceID, len(result.Candidate.Days), result.Qualification.ID)
	return 0
}

func inspectPublicFiles(manifestPath, catalogPath, rawPath, extractionPath, previousPath string, at time.Time) (onboarding.Inspection, error) {
	var manifest onboarding.Manifest
	if err := readPublicJSON(manifestPath, 1024*1024, &manifest); err != nil {
		return onboarding.Inspection{}, fmt.Errorf("manifest: %w", err)
	}
	catalogBytes, err := readBoundedRegularFile(catalogPath, 256*1024*1024)
	if err != nil {
		return onboarding.Inspection{}, fmt.Errorf("catalog: %w", err)
	}
	catalog, err := geography.DecodeCatalog(catalogBytes)
	if err != nil {
		return onboarding.Inspection{}, err
	}
	raw, err := readBoundedRegularFile(rawPath, maxImportArtifactBytes)
	if err != nil {
		return onboarding.Inspection{}, fmt.Errorf("raw artifact: %w", err)
	}
	var extracted []byte
	if extractionPath != "" {
		extracted, err = readBoundedRegularFile(extractionPath, 1024*1024)
		if err != nil {
			return onboarding.Inspection{}, fmt.Errorf("extraction: %w", err)
		}
	}
	var previous *domain.CandidateSchedule
	if previousPath != "" {
		previous = &domain.CandidateSchedule{}
		if err := readPublicJSON(previousPath, 8*1024*1024, previous); err != nil {
			return onboarding.Inspection{}, fmt.Errorf("previous candidate: %w", err)
		}
	}
	return onboarding.Inspect(manifest, catalog, raw, extracted, previous, at)
}

func readPublicJSON(path string, maximum int64, target any) error {
	encoded, err := readBoundedRegularFile(path, maximum)
	if err != nil {
		return err
	}
	if !utf8.Valid(encoded) {
		return errors.New("JSON must be UTF-8")
	}
	if err := strictjson.RejectDuplicateObjectMembers(encoded); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(encoded))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		return errors.New("JSON must contain exactly one value")
	}
	return nil
}
