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
	"github.com/andy-ahmedov/namaz-time/internal/providers/effective"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/providers/officialpdf"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

const componentName = "ingestor"

type importManifest struct {
	DataClassification    domain.DataClassification `json:"data_classification"`
	ParserVersion         string                    `json:"parser_version,omitempty"`
	Mosque                domain.Mosque             `json:"mosque"`
	ArtifactFilename      string                    `json:"artifact_filename"`
	ArtifactContentType   string                    `json:"artifact_content_type"`
	ArtifactCapturedAt    string                    `json:"artifact_captured_at"`
	ArtifactSHA256        string                    `json:"artifact_sha256"`
	TranscriptionFilename string                    `json:"transcription_filename"`
}

type inspection struct {
	Previous  *domain.CandidateSchedule `json:"previous_candidate,omitempty"`
	Candidate domain.CandidateSchedule  `json:"candidate"`
	Diff      publication.DiffReport    `json:"diff"`
}

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, stdout, stderr io.Writer) int {
	if len(args) == 0 {
		writeUsage(stderr)
		return 2
	}
	switch args[0] {
	case "inspect":
		return runInspect(args[1:], stdout, stderr)
	case "inspect-effective":
		return runInspectEffective(args[1:], stdout, stderr)
	default:
		writeUsage(stderr)
		return 2
	}
}

func runInspect(args []string, stdout, stderr io.Writer) int {
	flags := flag.NewFlagSet("inspect", flag.ContinueOnError)
	flags.SetOutput(stderr)
	fixtureDir := flags.String("fixture-dir", "", "directory containing import.json, source-record.json and source files")
	if err := flags.Parse(args); err != nil {
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
	return writeInspection(stdout, stderr, result)
}

func runInspectEffective(args []string, stdout, stderr io.Writer) int {
	flags := flag.NewFlagSet("inspect-effective", flag.ContinueOnError)
	flags.SetOutput(stderr)
	baselineDir := flags.String("baseline-dir", "", "directory containing the baseline fixture")
	overrideDir := flags.String("override-dir", "", "directory containing the bounded override fixture")
	policyFile := flags.String("policy-file", "", "effective policy JSON; defaults to <baseline-dir>/effective-policy.json")
	if err := flags.Parse(args); err != nil {
		return 2
	}
	if *baselineDir == "" || *overrideDir == "" || flags.NArg() != 0 {
		fmt.Fprintln(stderr, "inspect-effective requires --baseline-dir and --override-dir")
		return 2
	}
	path := *policyFile
	if path == "" {
		path = filepath.Join(*baselineDir, "effective-policy.json")
	}
	result, err := inspectEffective(*baselineDir, *overrideDir, path)
	if err != nil {
		fmt.Fprintf(stderr, "inspect-effective failed: %v\n", err)
		return 1
	}
	return writeInspection(stdout, stderr, result)
}

func writeInspection(stdout, stderr io.Writer, result inspection) int {
	encoder := json.NewEncoder(stdout)
	encoder.SetIndent("", "  ")
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(result); err != nil {
		fmt.Fprintf(stderr, "write inspection: %v\n", err)
		return 1
	}
	return 0
}

func writeUsage(stderr io.Writer) {
	fmt.Fprintln(stderr, "usage:")
	fmt.Fprintln(stderr, "  ingestor inspect --fixture-dir <directory>")
	fmt.Fprintln(stderr, "  ingestor inspect-effective --baseline-dir <directory> --override-dir <directory> [--policy-file <file>]")
}

func inspectEffective(baselineDirectory, overrideDirectory, policyPath string) (inspection, error) {
	baseline, err := inspectFixture(baselineDirectory)
	if err != nil {
		return inspection{}, fmt.Errorf("inspect baseline: %w", err)
	}
	override, err := inspectFixture(overrideDirectory)
	if err != nil {
		return inspection{}, fmt.Errorf("inspect override: %w", err)
	}
	policyBytes, err := os.ReadFile(policyPath)
	if err != nil {
		return inspection{}, fmt.Errorf("read effective policy: %w", err)
	}
	policy, err := effective.DecodePolicy(policyBytes)
	if err != nil {
		return inspection{}, err
	}
	decidedAt, err := time.Parse(time.RFC3339, policy.DecidedAt)
	if err != nil {
		return inspection{}, fmt.Errorf("parse effective policy decided_at: %w", err)
	}
	artifact, err := effective.CapturePolicyArtifact(filepath.Base(policyPath), decidedAt, bytes.NewReader(policyBytes))
	if err != nil {
		return inspection{}, err
	}
	candidate, err := effective.Compose(effective.ComposeRequest{
		Policy: policy, PolicyBytes: policyBytes, PolicyArtifact: artifact,
		Baseline: baseline.Candidate, Overrides: []domain.CandidateSchedule{override.Candidate},
	})
	if err != nil {
		return inspection{}, err
	}
	diff, err := publication.Diff(&baseline.Candidate, candidate)
	if err != nil {
		return inspection{}, err
	}
	return inspection{Previous: &baseline.Candidate, Candidate: candidate, Diff: diff}, nil
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
	artifactFile, err := os.Open(filepath.Join(directory, manifest.ArtifactFilename))
	if err != nil {
		return inspection{}, fmt.Errorf("open artifact: %w", err)
	}
	defer artifactFile.Close()
	transcription, err := os.ReadFile(filepath.Join(directory, manifest.TranscriptionFilename))
	if err != nil {
		return inspection{}, fmt.Errorf("read transcription: %w", err)
	}
	parserVersion := manifest.ParserVersion
	if parserVersion == "" {
		parserVersion = manual.ParserVersion
	}
	var candidate domain.CandidateSchedule
	switch parserVersion {
	case manual.ParserVersion:
		source, decodeErr := manual.DecodeSourceRecord(sourceData)
		if decodeErr != nil {
			return inspection{}, decodeErr
		}
		artifact, captureErr := manual.CaptureArtifact(manifest.ArtifactFilename, manifest.ArtifactContentType, capturedAt, artifactFile)
		if captureErr != nil {
			return inspection{}, captureErr
		}
		candidate, err = manual.Parse(manual.ParseConfig{
			Mosque: manifest.Mosque, Source: source, ExpectedRawSHA256: manifest.ArtifactSHA256,
			DataClassification: manifest.DataClassification,
		}, artifact, transcription)
	case officialpdf.ParserVersion:
		source, decodeErr := officialpdf.DecodeSourceRecord(sourceData)
		if decodeErr != nil {
			return inspection{}, decodeErr
		}
		artifact, captureErr := officialpdf.CaptureArtifact(manifest.ArtifactFilename, manifest.ArtifactContentType, capturedAt, artifactFile)
		if captureErr != nil {
			return inspection{}, captureErr
		}
		candidate, err = officialpdf.Parse(officialpdf.ParseConfig{
			Mosque: manifest.Mosque, Source: source, ExpectedRawSHA256: manifest.ArtifactSHA256,
			DataClassification: manifest.DataClassification,
		}, artifact, transcription)
	default:
		return inspection{}, fmt.Errorf("unsupported parser version %q", parserVersion)
	}
	if err != nil {
		return inspection{}, err
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		return inspection{}, err
	}
	return inspection{Candidate: candidate, Diff: diff}, nil
}
