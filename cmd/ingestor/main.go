package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

const componentName = "ingestor"

type importManifest struct {
	DataClassification    domain.DataClassification `json:"data_classification"`
	Mosque                domain.Mosque             `json:"mosque"`
	ArtifactFilename      string                    `json:"artifact_filename"`
	ArtifactContentType   string                    `json:"artifact_content_type"`
	ArtifactCapturedAt    string                    `json:"artifact_captured_at"`
	ArtifactSHA256        string                    `json:"artifact_sha256"`
	TranscriptionFilename string                    `json:"transcription_filename"`
}

type inspection struct {
	Candidate domain.CandidateSchedule `json:"candidate"`
	Diff      publication.DiffReport   `json:"diff"`
}

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, stdout, stderr io.Writer) int {
	if len(args) == 0 || args[0] != "inspect" {
		fmt.Fprintln(stderr, "usage: ingestor inspect --fixture-dir <directory>")
		return 2
	}
	flags := flag.NewFlagSet("inspect", flag.ContinueOnError)
	flags.SetOutput(stderr)
	fixtureDir := flags.String("fixture-dir", "", "directory containing import.json, source-record.json and source files")
	if err := flags.Parse(args[1:]); err != nil {
		return 2
	}
	if *fixtureDir == "" || flags.NArg() != 0 {
		fmt.Fprintln(stderr, "inspect requires exactly one --fixture-dir")
		return 2
	}
	result, err := inspectFixture(*fixtureDir)
	if err != nil {
		fmt.Fprintf(stderr, "inspect failed: %v\n", err)
		return 1
	}
	encoder := json.NewEncoder(stdout)
	encoder.SetIndent("", "  ")
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(result); err != nil {
		fmt.Fprintf(stderr, "write inspection: %v\n", err)
		return 1
	}
	return 0
}

func inspectFixture(directory string) (inspection, error) {
	manifestData, err := os.ReadFile(filepath.Join(directory, "import.json"))
	if err != nil {
		return inspection{}, fmt.Errorf("read import manifest: %w", err)
	}
	var manifest importManifest
	decoder := json.NewDecoder(bytes.NewReader(manifestData))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&manifest); err != nil {
		return inspection{}, fmt.Errorf("decode import manifest: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return inspection{}, errors.New("decode import manifest: multiple JSON values")
	}
	capturedAt, err := time.Parse(time.RFC3339, manifest.ArtifactCapturedAt)
	if err != nil {
		return inspection{}, fmt.Errorf("parse artifact_captured_at: %w", err)
	}
	sourceData, err := os.ReadFile(filepath.Join(directory, "source-record.json"))
	if err != nil {
		return inspection{}, fmt.Errorf("read source record: %w", err)
	}
	source, err := manual.DecodeSourceRecord(sourceData)
	if err != nil {
		return inspection{}, err
	}
	artifactFile, err := os.Open(filepath.Join(directory, manifest.ArtifactFilename))
	if err != nil {
		return inspection{}, fmt.Errorf("open artifact: %w", err)
	}
	defer artifactFile.Close()
	artifact, err := manual.CaptureArtifact(manifest.ArtifactFilename, manifest.ArtifactContentType, capturedAt, artifactFile)
	if err != nil {
		return inspection{}, err
	}
	transcription, err := os.ReadFile(filepath.Join(directory, manifest.TranscriptionFilename))
	if err != nil {
		return inspection{}, fmt.Errorf("read transcription: %w", err)
	}
	candidate, err := manual.Parse(manual.ParseConfig{
		Mosque: manifest.Mosque, Source: source, ExpectedRawSHA256: manifest.ArtifactSHA256,
		DataClassification: manifest.DataClassification,
	}, artifact, transcription)
	if err != nil {
		return inspection{}, err
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		return inspection{}, err
	}
	return inspection{Candidate: candidate, Diff: diff}, nil
}
