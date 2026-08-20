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

const pilotRawSHA256ForCLI = "11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c"
const pilotNormalizedSHA256ForCLI = "b050b6d6f0f567f018112883b80a78146ae42de282ba3f2fa4287963658f705b"
const pilotDiffSHA256ForCLI = "e6737632e901576039d4728c3d0919e2ca6be83caad6cec519cbab682d39fd76"
const annualRawSHA256ForCLI = "82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21"
const annualNormalizedSHA256ForCLI = "99a9c1946a21105413ff8fef4ca6d7637908678f3f89e079eb82f473193ae7c0"
const annualDiffSHA256ForCLI = "3e9164402713e6a2773d0cec64a160a04ab3c16b4d5251ffd669f30113461a8e"
