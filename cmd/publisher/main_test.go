package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

func TestPublisherHasNoPrivateKeyInputAndRequiresExplicitWorkflow(t *testing.T) {
	t.Parallel()

	for _, args := range [][]string{{}, {"publish", "--private-key", "secret"}, {"prepare", "--private-key", "secret"}} {
		if err := run(args, io.Discard); err == nil {
			t.Fatalf("run(%q) unexpectedly succeeded", args)
		}
	}
}

func TestPublisherCommandsCompleteSyntheticAirGappedWorkflow(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	candidate := domain.CandidateSchedule{
		DataClassification:  domain.DataClassificationSynthetic,
		Mosque:              domain.Mosque{ID: "synthetic-publisher", Name: "Synthetic publisher mosque", CountryCode: "ZZ", Timezone: "Europe/Ulyanovsk"},
		Source:              domain.CandidateSource{SourceID: "synthetic-publisher", Kind: domain.ProviderKindManualImport, AuthorityName: "Synthetic test fixture", GeographicScope: "Tests only", PermissionStatus: "granted", LicenseReference: "Synthetic", ApprovalRequired: true},
		Artifact:            domain.RawArtifact{Filename: "synthetic.csv", ContentType: "text/csv", CapturedAt: "2026-08-20T00:00:00Z", ByteLength: 1, SHA256: strings.Repeat("a", 64)},
		TranscriptionSHA256: strings.Repeat("b", 64), ParserVersion: "manual-csv/v1", Coverage: domain.DateRange{From: "2026-08-20", To: "2026-08-20"},
		Days:   []domain.CandidatePrayerDay{{PrayerDay: domain.PrayerDay{Date: "2026-08-20", Fajr: "03:00", Sunrise: "05:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "19:00", Isha: "21:00", Flags: []string{"synthetic"}}}},
		Status: domain.CandidateNeedsReview,
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	approval := domain.ApprovalDecision{
		ID: "approval-synthetic-publisher", Decision: domain.ApprovalApproved, Actor: "synthetic-approver", ApprovedAt: "2026-08-20T00:00:00Z", Scope: "Synthetic test",
		CandidateID: candidate.ID, RawSHA256: candidate.Artifact.SHA256, TranscriptionSHA256: candidate.TranscriptionSHA256,
		NormalizedSHA256: candidate.NormalizedSHA256, DiffSHA256: diff.SHA256, ParserVersion: candidate.ParserVersion,
	}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	keyID := "synthetic-publisher-key"
	trustDocument := fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":"test","generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-08-20T00:00:00Z"}]}`, keyID, base64.StdEncoding.EncodeToString(publicKey))
	writeJSON := func(name string, value any) string {
		t.Helper()
		path := filepath.Join(directory, name)
		data, err := json.Marshal(value)
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, data, 0o600); err != nil {
			t.Fatal(err)
		}
		return path
	}
	inspectionPath := writeJSON("inspection.json", inspectionInput{Candidate: candidate, Diff: diff})
	approvalPath := writeJSON("approval.json", approval)
	trustPath := filepath.Join(directory, "trust.json")
	if err := os.WriteFile(trustPath, []byte(trustDocument), 0o600); err != nil {
		t.Fatal(err)
	}
	requestPath := filepath.Join(directory, "publication-request.json")
	if err := run([]string{"assemble", "-inspection", inspectionPath, "-approval", approvalPath, "-snapshot-id", "synthetic-publisher-v1", "-generated-at", "2026-08-20T00:01:00Z", "-signing-key-id", keyID, "-out", requestPath}, io.Discard); err != nil {
		t.Fatal(err)
	}
	preparedPath := filepath.Join(directory, "signing-request.json")
	if err := run([]string{"prepare", "-request", requestPath, "-trust-bundle", trustPath, "-out", preparedPath}, io.Discard); err != nil {
		t.Fatal(err)
	}
	var prepared publication.SigningRequest
	if err := readStrictJSON(preparedPath, &prepared); err != nil {
		t.Fatal(err)
	}
	canonical, err := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	if err != nil {
		t.Fatal(err)
	}
	response := publication.SigningResponse{
		SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: keyID,
		SignatureEd25519Base64: base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical)),
		SignerIdentity:         "synthetic-signer", SignedAt: "2026-08-20T00:01:30Z",
		PublishedAt: "2026-08-20T00:02:00Z", ChainGenesisReason: "synthetic CLI workflow genesis",
	}
	attestation, requestSHA256, err := publication.BuildAttestationPayload(prepared, response)
	if err != nil {
		t.Fatal(err)
	}
	response.SigningRequestSHA256 = requestSHA256
	response.AttestationEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, attestation))
	responsePath := writeJSON("signer-response.json", response)
	snapshotPath := filepath.Join(directory, "snapshot.json")
	receiptPath := filepath.Join(directory, "receipt.json")
	ledgerPath := filepath.Join(directory, "publication-ledger-head.json")
	if err := run([]string{"finalize", "-request", requestPath, "-signing-request", preparedPath, "-signer-response", responsePath, "-trust-bundle", trustPath, "-published-at", "2026-08-20T00:02:00Z", "-chain-genesis-reason", "synthetic CLI workflow genesis", "-ledger-head", ledgerPath, "-out-snapshot", snapshotPath, "-out-receipt", receiptPath}, io.Discard); err != nil {
		t.Fatal(err)
	}
	if err := run([]string{"verify", "-snapshot", snapshotPath, "-receipt", receiptPath, "-trust-bundle", trustPath}, io.Discard); err != nil {
		t.Fatal(err)
	}
	if _, release, err := acquirePublicationLedger(ledgerPath, "test", "", "unauthorized second genesis"); err == nil {
		release()
		t.Fatal("publication ledger accepted a repeated genesis")
	}
}

func TestWriteExclusiveNeverOverwritesEvidence(t *testing.T) {
	t.Parallel()

	path := filepath.Join(t.TempDir(), "receipt.json")
	if err := os.WriteFile(path, []byte("existing"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := writeExclusive(path, []byte("replacement")); err == nil {
		t.Fatal("writeExclusive() overwrote existing file")
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(data) != "existing" {
		t.Fatalf("existing evidence changed to %q", data)
	}
}

func TestVerifyTrustRejectsCrossEnvironmentPublicMaterialReuse(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	fixture, err := os.ReadFile(filepath.Join("..", "..", "fixtures", "verification", "phase1-trust-bundle.json"))
	if err != nil {
		t.Fatal(err)
	}
	testPath := filepath.Join(directory, "test.json")
	productionPath := filepath.Join(directory, "production.json")
	if err := os.WriteFile(testPath, fixture, 0o600); err != nil {
		t.Fatal(err)
	}
	production := bytes.Replace(fixture, []byte(`"environment": "test"`), []byte(`"environment": "production"`), 1)
	if err := os.WriteFile(productionPath, production, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := run([]string{
		"verify-trust", "-current", testPath, "-production-bundle", productionPath,
	}, io.Discard); err == nil {
		t.Fatal("verify-trust accepted public-key reuse across environments")
	}
}
