package main

import (
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

func TestApproverCLIKeygenSignAndVerifyExactInspection(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	privatePath := filepath.Join(directory, "approver-private.pem")
	trustPath := filepath.Join(directory, "approver-trust.json")
	const identity = "approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly"
	const keyID = "ulyanovsk-approver-akhmedov-2026-01"
	if err := run([]string{
		"keygen", "-identity", identity, "-key-id", keyID,
		"-generated-at", "2026-08-20T14:00:00Z",
		"-private-key-out", privatePath, "-trust-bundle-out", trustPath,
	}, io.Discard); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(privatePath)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("private key mode = %o", info.Mode().Perm())
	}

	candidate := candidateFixture(t)
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	policy := publication.MosquePrayerPolicy{
		SchemaVersion: "1.0", PolicyID: "pilot-policy", MosqueID: candidate.Mosque.ID,
		ValidFrom: candidate.Coverage.From, ValidTo: candidate.Coverage.To,
		DhuhrAdhanSource:  publication.DhuhrAdhanFromCongregation,
		RamadanExceptions: "none", HolidayExceptions: "none", CorrectionOwner: "admin",
	}
	policyHash, err := publication.MosquePrayerPolicySHA256(policy)
	if err != nil {
		t.Fatal(err)
	}
	decision := domain.ApprovalDecision{
		ID: "approval-pilot-001", Decision: domain.ApprovalApproved, Actor: identity,
		ApprovedAt: "2026-08-20T14:01:00Z", Scope: "Pilot approval",
		CandidateID: candidate.ID, RawSHA256: candidate.Artifact.SHA256,
		TranscriptionSHA256: candidate.TranscriptionSHA256, NormalizedSHA256: candidate.NormalizedSHA256,
		DiffSHA256: diff.SHA256, ParserVersion: candidate.ParserVersion, PrayerPolicySHA256: policyHash,
		AcknowledgedWarningCodes: []string{"source_marker_preserved"},
	}
	inspectionPath := writeJSON(t, directory, "inspection.json", inspectionInput{Candidate: candidate, Diff: diff})
	decisionPath := writeJSON(t, directory, "decision.json", decision)
	policyPath := writeJSON(t, directory, "policy.json", policy)
	receiptPath := filepath.Join(directory, "receipt.json")
	if err := run([]string{
		"sign", "-private-key", privatePath, "-key-id", keyID,
		"-trust-bundle", trustPath,
		"-decision", decisionPath, "-inspection", inspectionPath,
		"-prayer-policy", policyPath, "-out", receiptPath,
	}, io.Discard); err != nil {
		t.Fatal(err)
	}
	if err := run([]string{
		"verify", "-receipt", receiptPath, "-trust-bundle", trustPath,
		"-inspection", inspectionPath, "-prayer-policy", policyPath,
	}, io.Discard); err != nil {
		t.Fatal(err)
	}

	tampered := decision
	tampered.DiffSHA256 = strings.Repeat("f", 64)
	tamperedPath := writeJSON(t, directory, "tampered-decision.json", tampered)
	if err := run([]string{
		"sign", "-private-key", privatePath, "-key-id", keyID,
		"-trust-bundle", trustPath,
		"-decision", tamperedPath, "-inspection", inspectionPath,
		"-prayer-policy", policyPath, "-out", filepath.Join(directory, "tampered-receipt.json"),
	}, io.Discard); err == nil {
		t.Fatal("approver signed a decision not bound to the inspection")
	}
}

func candidateFixture(t *testing.T) domain.CandidateSchedule {
	t.Helper()
	candidate := domain.CandidateSchedule{
		DataClassification:  domain.DataClassificationProduction,
		Mosque:              domain.Mosque{ID: "pilot-mosque", Name: "Pilot Mosque", CountryCode: "RU", Timezone: "Europe/Ulyanovsk"},
		Source:              domain.CandidateSource{SourceID: "pilot-source", Kind: domain.ProviderKindManualImport, AuthorityName: "Pilot", GeographicScope: "Ulyanovsk", PermissionStatus: "granted", ApprovalRequired: true},
		Artifact:            domain.RawArtifact{Filename: "pilot.csv", ContentType: "text/csv", CapturedAt: "2026-08-20T00:00:00Z", ByteLength: 1, SHA256: strings.Repeat("1", 64)},
		TranscriptionSHA256: strings.Repeat("2", 64), ParserVersion: manual.ParserVersion,
		Coverage:   domain.DateRange{From: "2026-08-20", To: "2026-08-20"},
		Days:       []domain.CandidatePrayerDay{{PrayerDay: domain.PrayerDay{Date: "2026-08-20", Fajr: "03:00", Sunrise: "05:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "19:00", Isha: "21:00"}, DhuhrCongregation: "12:15"}},
		Status:     domain.CandidateNeedsReview,
		Validation: domain.CandidateValidationReport{Warnings: []domain.CandidateDiagnostic{{Path: "days[0].flags", Code: "source_marker_preserved", Message: "test"}}},
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	return candidate
}

func writeJSON(t *testing.T, directory, name string, value any) string {
	t.Helper()
	data, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(directory, name)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}
