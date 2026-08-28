package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestComponentName(t *testing.T) {
	t.Parallel()

	if componentName != "ingestor" {
		t.Fatalf("componentName = %q, want ingestor", componentName)
	}
}

func TestContainedImportFileRejectsTraversalSymlinkAndOversize(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	outside := filepath.Join(t.TempDir(), "outside.csv")
	if err := os.WriteFile(outside, []byte("secret"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(directory, "linked.csv")); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "oversized.csv"), bytes.Repeat([]byte("x"), 17), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "valid.csv"), []byte("valid"), 0o600); err != nil {
		t.Fatal(err)
	}

	for _, name := range []string{"../outside.csv", "linked.csv", "oversized.csv"} {
		if _, err := readContainedRegularFile(directory, name, 16); err == nil {
			t.Fatalf("readContainedRegularFile(%q) error = nil", name)
		}
	}
	data, err := readContainedRegularFile(directory, "valid.csv", 16)
	if err != nil {
		t.Fatal(err)
	}
	if string(data) != "valid" {
		t.Fatalf("valid data = %q", data)
	}
}

func TestInspectPilotFixtureProducesUnapprovedCandidateAndDiff(t *testing.T) {
	t.Parallel()

	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	fixtureDir := filepath.Join(workingDirectory, "..", "..", "fixtures", "pilot", "ulyanovsk-2026-08")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	if code := run([]string{"inspect", "--fixture-dir", fixtureDir}, &stdout, &stderr); code != 0 {
		t.Fatalf("run() = %d, stderr = %s", code, stderr.String())
	}
	var result inspection
	if err := json.Unmarshal(stdout.Bytes(), &result); err != nil {
		t.Fatalf("decode output: %v", err)
	}
	if result.Candidate.Status != domain.CandidateNeedsReview || result.Candidate.Artifact.SHA256 != pilotRawSHA256ForCLI {
		t.Fatalf("candidate = %#v", result.Candidate)
	}
	if result.Diff.SHA256 == "" || result.Diff.ChangedDays != 31 {
		t.Fatalf("diff = %#v", result.Diff)
	}
	if result.Candidate.NormalizedSHA256 != pilotNormalizedSHA256ForCLI || result.Diff.SHA256 != pilotDiffSHA256ForCLI {
		t.Fatalf("unexpected deterministic fingerprints: candidate=%s diff=%s", result.Candidate.NormalizedSHA256, result.Diff.SHA256)
	}
	if strings.Contains(stdout.String(), `"status": "approved"`) {
		t.Fatal("inspection output must not manufacture an approval")
	}
}

func TestInspectAnnualOfficialPDFFixtureProducesFullYearUnapprovedCandidate(t *testing.T) {
	t.Parallel()

	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	fixtureDir := filepath.Join(workingDirectory, "..", "..", "fixtures", "pilot", "ulyanovsk-2026")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	if code := run([]string{"inspect", "--fixture-dir", fixtureDir}, &stdout, &stderr); code != 0 {
		t.Fatalf("run() = %d, stderr = %s", code, stderr.String())
	}
	var result inspection
	if err := json.Unmarshal(stdout.Bytes(), &result); err != nil {
		t.Fatalf("decode output: %v", err)
	}
	if result.Candidate.Status != domain.CandidateNeedsReview || result.Candidate.Artifact.SHA256 != annualRawSHA256ForCLI {
		t.Fatalf("candidate = %#v", result.Candidate)
	}
	if result.Candidate.ParserVersion != "ulyanovsk-official-pdf-csv/v1" || len(result.Candidate.Days) != 365 {
		t.Fatalf("parser=%q days=%d", result.Candidate.ParserVersion, len(result.Candidate.Days))
	}
	if result.Candidate.Coverage != (domain.DateRange{From: "2026-01-01", To: "2026-12-31"}) || result.Diff.ChangedDays != 365 {
		t.Fatalf("coverage=%#v diff=%#v", result.Candidate.Coverage, result.Diff)
	}
	if result.Candidate.NormalizedSHA256 != annualNormalizedSHA256ForCLI || result.Diff.SHA256 != annualDiffSHA256ForCLI {
		t.Fatalf("unexpected deterministic fingerprints: candidate=%s diff=%s", result.Candidate.NormalizedSHA256, result.Diff.SHA256)
	}
	if strings.Contains(stdout.String(), `"status": "approved"`) {
		t.Fatal("inspection output must not manufacture an approval")
	}
}

func TestInspectEffectivePilotScheduleAppliesAugustPhotoPrecedence(t *testing.T) {
	t.Parallel()

	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	root := filepath.Join(workingDirectory, "..", "..", "fixtures", "pilot")
	annualDir := filepath.Join(root, "ulyanovsk-2026")
	monthlyDir := filepath.Join(root, "ulyanovsk-2026-08")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	if code := run([]string{
		"inspect-effective", "--baseline-dir", annualDir, "--override-dir", monthlyDir,
	}, &stdout, &stderr); code != 0 {
		t.Fatalf("run() = %d, stderr = %s", code, stderr.String())
	}
	var result inspection
	if err := json.Unmarshal(stdout.Bytes(), &result); err != nil {
		t.Fatalf("decode output: %v", err)
	}
	if result.Candidate.Status != domain.CandidateNeedsReview || result.Candidate.ParserVersion != "effective-schedule/v1" || len(result.Candidate.Days) != 365 {
		t.Fatalf("candidate = %#v", result.Candidate)
	}
	if result.Candidate.Artifact.SHA256 != effectivePolicySHA256ForCLI || len(result.Candidate.Components) != 2 {
		t.Fatalf("effective provenance = %#v", result.Candidate)
	}
	if len(result.Diff.SourceComponents) != 2 || result.Diff.SourceComponents[0].RawArtifact.SHA256 != annualRawSHA256ForCLI ||
		result.Diff.SourceComponents[1].RawArtifact.SHA256 != pilotRawSHA256ForCLI {
		t.Fatalf("diff source components = %#v", result.Diff.SourceComponents)
	}
	if got := result.Candidate.Days[235]; got.Date != "2026-08-24" || got.Dhuhr != "12:48" || got.DhuhrCongregation != "13:53" {
		t.Fatalf("August 24 effective row = %#v", got)
	}
	if result.Diff.ChangedDays != 31 || result.Diff.SHA256 == "" {
		t.Fatalf("effective diff = %#v", result.Diff)
	}
	if result.Candidate.NormalizedSHA256 != effectiveNormalizedSHA256ForCLI || result.Diff.SHA256 != effectiveDiffSHA256ForCLI {
		t.Fatalf("unexpected deterministic effective fingerprints: candidate=%s diff=%s", result.Candidate.NormalizedSHA256, result.Diff.SHA256)
	}
	if strings.Contains(stdout.String(), `requires_review_collective_outlier`) || strings.Contains(stdout.String(), `requires_review_dhuhr_source_value`) {
		t.Fatal("resolved source conflicts remain in effective inspection")
	}
	if strings.Contains(stdout.String(), `"status": "approved"`) {
		t.Fatal("effective inspection must not manufacture approval")
	}
}

const pilotRawSHA256ForCLI = "11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c"
const pilotNormalizedSHA256ForCLI = "85bdfc297bcd5168b0e1f6a0c89012eb4a753294a115197ab1a0892ed97f5c89"
const pilotDiffSHA256ForCLI = "812a9908a7cc0c067fc6a117f567c9ff4276050344b52283bf2bc8ec1ea6ecfd"
const annualRawSHA256ForCLI = "82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21"
const annualNormalizedSHA256ForCLI = "867862c453fc167a9c9b17240dfb4c9a5be0822882c3dfbc68e8c454a4c7efb2"
const annualDiffSHA256ForCLI = "41e63772fb6d8e96d41caf8fd88a729087e04a5dfc0bbdebc30a92c42c1339db"
const effectivePolicySHA256ForCLI = "c7d95bbc900a683b3be4fa66f6d1a8237ccf3e882452674a2cdd946c800d935a"
const effectiveNormalizedSHA256ForCLI = "e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77"
const effectiveDiffSHA256ForCLI = "de139a27b2f0f5b42253783f5a4aeca11d2f643bee572d860e2c4c24564d7e4e"
