package publication_test

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

func TestDiffIsDeterministicAndNamesChangedPrayer(t *testing.T) {
	t.Parallel()

	before := candidate()
	after := candidate()
	after.Artifact.SHA256 = strings.Repeat("b", 64)
	after.Days[0].Fajr = "03:01"

	first, err := publication.Diff(&before, after)
	if err != nil {
		t.Fatal(err)
	}
	second, err := publication.Diff(&before, after)
	if err != nil {
		t.Fatal(err)
	}
	if first.SHA256 != second.SHA256 || len(first.Changes) != 1 {
		t.Fatalf("diffs = %#v %#v", first, second)
	}
	change := first.Changes[0]
	if change.Date != "2025-01-01" || change.Field != "fajr" || change.Before != "03:00" || change.After != "03:01" {
		t.Fatalf("change = %#v", change)
	}
	if first.PreviousRawSHA256 == first.CandidateRawSHA256 || first.ParserVersion == "" {
		t.Fatalf("provenance missing from diff: %#v", first)
	}
	if len(first.PrayerDeltas) != 1 || first.PrayerDeltas[0].Prayer != "fajr" || first.PrayerDeltas[0].MaximumMinutes != 1 || first.PrayerDeltas[0].MedianMinutes != 1 {
		t.Fatalf("prayer deltas = %#v", first.PrayerDeltas)
	}
}

func TestDiffExplainsHijriAndEveryReviewRelevantMetadataChange(t *testing.T) {
	t.Parallel()

	before := candidate()
	after := candidate()
	after.DataClassification = domain.DataClassificationProduction
	after.Mosque.Name = "Changed mosque name"
	after.Mosque.Region = "Changed region"
	after.Source.AuthorityBranch = "Changed branch"
	after.Source.CanonicalURL = "https://example.org/changed"
	after.Source.LicenseReference = "Changed license"
	after.Source.Attribution = "Changed attribution"
	after.Artifact.Filename = "changed.csv"
	after.Coverage.To = "2025-01-02"
	after.Days[0].HijriDay = 2
	after.Days[0].HijriMonth = "changed"
	after.Days[0].HijriYear = 1448
	if err := domain.FinalizeCandidateIdentity(&after); err != nil {
		t.Fatal(err)
	}

	diff, err := publication.Diff(&before, after)
	if err != nil {
		t.Fatal(err)
	}
	for _, field := range []string{"hijri_day", "hijri_month", "hijri_year"} {
		if !hasDayChange(diff.Changes, field) {
			t.Errorf("day diff does not explain %s: %#v", field, diff.Changes)
		}
	}
	for _, field := range []string{
		"data_classification", "mosque.name", "mosque.region",
		"source.authority_branch", "source.canonical_url", "source.license_reference",
		"source.attribution", "artifact.filename", "coverage.to",
	} {
		if !hasMetadataChange(diff.MetadataChanges, field) {
			t.Errorf("metadata diff does not explain %s: %#v", field, diff.MetadataChanges)
		}
	}
}

func TestPublicationRequiresApprovalBoundToCandidateAndDiff(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	_, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, domain.ApprovalDecision{})

	if _, err := publication.Publish(request, privateKey); !publication.IsErrorCode(err, "approval_required") {
		t.Fatalf("unapproved Publish() error = %v", err)
	}
	request.Approval = approvalFor(candidate, diff)
	request.Approval.RawSHA256 = strings.Repeat("f", 64)
	if _, err := publication.Publish(request, privateKey); !publication.IsErrorCode(err, "approval_binding_mismatch") {
		t.Fatalf("wrong raw hash Publish() error = %v", err)
	}
	request.Approval = approvalFor(candidate, diff)
	request.Approval.DiffSHA256 = strings.Repeat("e", 64)
	if _, err := publication.Publish(request, privateKey); !publication.IsErrorCode(err, "approval_binding_mismatch") {
		t.Fatalf("wrong diff hash Publish() error = %v", err)
	}
}

func TestSignedSnapshotIsDeterministicAndRejectsTamperOrUnknownKey(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))

	first, err := publication.Publish(request, privateKey)
	if err != nil {
		t.Fatalf("Publish() error = %v", err)
	}
	second, err := publication.Publish(request, privateKey)
	if err != nil {
		t.Fatalf("Publish() second error = %v", err)
	}
	if string(first.JSON) != string(second.JSON) {
		t.Fatal("publication is not byte-deterministic")
	}
	if err := publication.Verify(first.JSON, map[string]ed25519.PublicKey{"test-signing-key": publicKey}); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
	if err := publication.Verify(first.JSON, map[string]ed25519.PublicKey{}); !publication.IsErrorCode(err, "unknown_signing_key") {
		t.Fatalf("unknown key Verify() error = %v", err)
	}
	tampered := []byte(strings.Replace(string(first.JSON), "03:00", "03:01", 1))
	if err := publication.Verify(tampered, map[string]ed25519.PublicKey{"test-signing-key": publicKey}); !publication.IsErrorCode(err, "canonical_hash_mismatch") {
		t.Fatalf("tampered Verify() error = %v", err)
	}
}

func TestVerifyRejectsMalformedUTF8EvenWhenReplacementRuneWasSigned(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.Mosque.Name = "Synthetic \ufffd mosque"
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	result, err := publication.Publish(
		publishRequest(candidate, diff, approvalFor(candidate, diff)),
		privateKey,
	)
	if err != nil {
		t.Fatal(err)
	}
	malformed := bytes.Replace(result.JSON, []byte("\xef\xbf\xbd"), []byte{0xff}, 1)
	if bytes.Equal(malformed, result.JSON) {
		t.Fatal("test fixture does not contain replacement rune")
	}
	if err := publication.Verify(
		malformed,
		map[string]ed25519.PublicKey{"test-signing-key": publicKey},
	); !publication.IsErrorCode(err, "snapshot_decode_failed") {
		t.Fatalf("Verify() malformed UTF-8 error = %v", err)
	}
}

func hasDayChange(changes []publication.DiffChange, field string) bool {
	for _, change := range changes {
		if change.Field == field {
			return true
		}
	}
	return false
}

func hasMetadataChange(changes []publication.MetadataChange, field string) bool {
	for _, change := range changes {
		if change.Field == field {
			return true
		}
	}
	return false
}

func TestAnnualGoldenManualFixturePublishesDeterministically(t *testing.T) {
	t.Parallel()

	start := time.Date(2025, 1, 1, 0, 0, 0, 0, time.UTC)
	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(filepath.Join(workingDirectory, "..", "..", "fixtures", "synthetic", "manual-annual-2025.csv"))
	if err != nil {
		t.Fatal(err)
	}
	artifact, err := manual.CaptureArtifact("manual-annual-2025.csv", "text/csv", start, bytes.NewReader(raw))
	if err != nil {
		t.Fatal(err)
	}
	if artifact.SHA256 != "61d3722203f0c6774c54404575e64c30121f392f1eefdae8115568792f5c8eb5" {
		t.Fatalf("fixture SHA-256 = %s", artifact.SHA256)
	}
	candidate, err := manual.Parse(manual.ParseConfig{
		Mosque: domain.Mosque{ID: "synthetic-annual-mosque", Name: "Synthetic annual mosque", CountryCode: "ZZ", Timezone: "Europe/Ulyanovsk"},
		Source: manual.SourceRecord{
			SourceID: "synthetic-annual-2025", Kind: domain.ProviderKindManualImport,
			AuthorityName:   "Synthetic test fixture — not an authority",
			GeographicScope: manual.GeographicScope{CountryCode: "ZZ", Description: "Synthetic annual fixture only"},
			TimezonePolicy:  manual.TimezonePolicy{Mode: "fixed_iana", IANATimezone: "Europe/Ulyanovsk"},
			Retrieval:       manual.RetrievalPolicy{Mode: "manual_upload", Cadence: "annual", StaleAfterHours: 8760},
			Permission:      manual.Permission{Status: "granted", LicenseReference: "Synthetic fixture"},
			Validation:      manual.ValidationPolicy{ApprovalRequired: true, MaxAutomaticDeltaMinutes: 30, MinimumCoverageDays: 365},
			Status:          "testing",
		},
		ExpectedRawSHA256:  artifact.SHA256,
		DataClassification: domain.DataClassificationSynthetic,
	}, artifact, raw)
	if err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	approval := approvalFor(candidate, diff)
	request := publication.PublishRequest{
		Candidate: candidate, Diff: diff, Approval: approval,
		SnapshotID: "synthetic-annual-2025-v1", GeneratedAt: start,
		SigningKeyID: "ephemeral-test-key",
	}

	first, err := publication.Publish(request, privateKey)
	if err != nil {
		t.Fatal(err)
	}
	second, err := publication.Publish(request, privateKey)
	if err != nil {
		t.Fatal(err)
	}
	if len(first.Snapshot.PrayerDays) != 365 || !bytes.Equal(first.JSON, second.JSON) {
		t.Fatalf("annual publication days=%d deterministic=%v", len(first.Snapshot.PrayerDays), bytes.Equal(first.JSON, second.JSON))
	}
	if err := publication.Verify(first.JSON, map[string]ed25519.PublicKey{"ephemeral-test-key": publicKey}); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
}

func TestValidationFailedCandidateCannotPublish(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.Status = domain.CandidateValidationFailed
	candidate.Validation.Errors = []domain.CandidateDiagnostic{{Code: "date_gap", Path: "days[1].date"}}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	_, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}

	_, err = publication.Publish(publishRequest(candidate, diff, approvalFor(candidate, diff)), privateKey)
	if !publication.IsErrorCode(err, "candidate_invalid") {
		t.Fatalf("Publish() error = %v", err)
	}
}

func TestParserWarningsRequireExplicitApprovalAcknowledgement(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.Validation.Warnings = []domain.CandidateDiagnostic{{Path: "days[0].fajr", Code: "day_to_day_delta", Message: "review"}}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	approval := approvalFor(candidate, diff)
	approval.AcknowledgedWarningCodes = nil
	_, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}

	_, err = publication.Publish(publishRequest(candidate, diff, approval), privateKey)
	if !publication.IsErrorCode(err, "warnings_unacknowledged") {
		t.Fatalf("Publish() error = %v", err)
	}
	approval.AcknowledgedWarningCodes = []string{"day_to_day_delta"}
	if _, err := publication.Publish(publishRequest(candidate, diff, approval), privateKey); err != nil {
		t.Fatalf("acknowledged Publish() error = %v", err)
	}
}

func TestCommittedPublicVerificationFixtureVerifiesInGo(t *testing.T) {
	t.Parallel()

	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	directory := filepath.Join(workingDirectory, "..", "..", "fixtures", "verification")
	keyData, err := os.ReadFile(filepath.Join(directory, "phase1-public-key.json"))
	if err != nil {
		t.Fatal(err)
	}
	var keyFixture struct {
		SigningKeyID       string `json:"signing_key_id"`
		PublicKey          string `json:"public_key_ed25519_base64"`
		PrivateKeyRetained bool   `json:"private_key_retained"`
	}
	if err := json.Unmarshal(keyData, &keyFixture); err != nil {
		t.Fatal(err)
	}
	publicKey, err := base64.StdEncoding.DecodeString(keyFixture.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	if keyFixture.PrivateKeyRetained {
		t.Fatal("verification fixture must not retain a private key")
	}
	snapshot, err := os.ReadFile(filepath.Join(directory, "synthetic-signed-snapshot.json"))
	if err != nil {
		t.Fatal(err)
	}
	if err := publication.Verify(snapshot, map[string]ed25519.PublicKey{keyFixture.SigningKeyID: publicKey}); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
}

func TestCandidateOrDiffMutationCannotReuseApproval(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	approval := approvalFor(candidate, diff)
	_, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}

	mutatedCandidate := candidate
	mutatedCandidate.Days = append([]domain.CandidatePrayerDay(nil), candidate.Days...)
	mutatedCandidate.Days[0].Fajr = "03:02"
	_, err = publication.Publish(publishRequest(mutatedCandidate, diff, approval), privateKey)
	if !publication.IsErrorCode(err, "candidate_binding_mismatch") {
		t.Fatalf("mutated candidate Publish() error = %v", err)
	}
	mutatedDiff := diff
	mutatedDiff.Changes[0].After = "03:02"
	_, err = publication.Publish(publishRequest(candidate, mutatedDiff, approval), privateKey)
	if !publication.IsErrorCode(err, "diff_binding_mismatch") {
		t.Fatalf("mutated diff Publish() error = %v", err)
	}
}

func candidate() domain.CandidateSchedule {
	candidate := domain.CandidateSchedule{
		DataClassification: domain.DataClassificationSynthetic,
		Mosque:             domain.Mosque{ID: "synthetic-mosque", Name: "Synthetic test mosque", CountryCode: "ZZ", Timezone: "Europe/Ulyanovsk"},
		Source: domain.CandidateSource{
			SourceID: "synthetic-annual", Kind: domain.ProviderKindManualImport,
			AuthorityName:   "Synthetic test fixture — not an authority",
			GeographicScope: "Synthetic tests only", PermissionStatus: "granted",
			LicenseReference: "Synthetic fixture", ApprovalRequired: true,
		},
		Artifact:            domain.RawArtifact{Filename: "synthetic.csv", ContentType: "text/csv", CapturedAt: "2025-01-01T00:00:00Z", ByteLength: 100, SHA256: strings.Repeat("a", 64)},
		TranscriptionSHA256: strings.Repeat("c", 64), ParserVersion: "manual-csv/v1",
		Coverage: domain.DateRange{From: "2025-01-01", To: "2025-01-01"},
		Days:     []domain.CandidatePrayerDay{{PrayerDay: domain.PrayerDay{Date: "2025-01-01", Fajr: "03:00", Sunrise: "05:00", Dhuhr: "12:10", Asr: "16:00", Maghrib: "19:00", Isha: "21:00", Flags: []string{"synthetic"}}, Zenith: "12:00", HijriDay: 1, HijriMonth: "synthetic", HijriYear: 1447}},
		Status:   domain.CandidateNeedsReview,
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		panic(err)
	}
	return candidate
}

func approvalFor(candidate domain.CandidateSchedule, diff publication.DiffReport) domain.ApprovalDecision {
	warningCodes := make([]string, 0, len(candidate.Validation.Warnings))
	seenWarnings := make(map[string]struct{}, len(candidate.Validation.Warnings))
	for _, warning := range candidate.Validation.Warnings {
		if _, seen := seenWarnings[warning.Code]; !seen {
			warningCodes = append(warningCodes, warning.Code)
			seenWarnings[warning.Code] = struct{}{}
		}
	}
	return domain.ApprovalDecision{
		ID: "approval-synthetic-2025", Decision: domain.ApprovalApproved, Actor: "test-suite",
		ApprovedAt: "2025-01-01T00:00:00Z", Scope: "Synthetic fixture only",
		CandidateID: candidate.ID, RawSHA256: candidate.Artifact.SHA256,
		TranscriptionSHA256: candidate.TranscriptionSHA256,
		NormalizedSHA256:    candidate.NormalizedSHA256, DiffSHA256: diff.SHA256,
		ParserVersion: candidate.ParserVersion, AcknowledgedWarningCodes: warningCodes,
	}
}

func publishRequest(candidate domain.CandidateSchedule, diff publication.DiffReport, approval domain.ApprovalDecision) publication.PublishRequest {
	return publication.PublishRequest{
		Candidate: candidate, Diff: diff, Approval: approval,
		SnapshotID: "synthetic-2025-snapshot-v1", GeneratedAt: time.Date(2025, 1, 1, 0, 0, 0, 0, time.UTC),
		SigningKeyID: "test-signing-key",
	}
}
