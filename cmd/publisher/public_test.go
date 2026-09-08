package main

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

func TestPublisherAssemblesQualifiedProductionWithoutHumanApproval(t *testing.T) {
	dir := t.TempDir()
	inspection := publicInspection(t)
	path := writeJSONFile(t, dir, "inspection.json", inspection)
	out := filepath.Join(dir, "request.json")
	args := []string{"assemble", "-inspection", path, "-snapshot-id", "synthetic-qualified-production", "-generated-at", "2026-09-08T06:02:00Z", "-signing-key-id", "synthetic-production-key", "-out", out}
	if err := run(args, io.Discard); err != nil {
		t.Fatal(err)
	}
	var request publication.PublishRequest
	if err := readStrictJSON(out, &request); err != nil {
		t.Fatal(err)
	}
	if request.Qualification == nil || request.Qualification.ID != inspection.Qualification.ID || request.Approval.ID != "" || request.ApprovalEvidence != nil || request.MosquePrayerPolicy != nil {
		t.Fatal("public assemble lost qualification or fabricated approval")
	}
	if err := qualification.VerifyCandidate(*request.Qualification, request.Candidate, request.Diff.SHA256, request.GeneratedAt); err != nil {
		t.Fatal(err)
	}
	// Any supplied legacy approval/prayer-policy input conflicts with the branch.
	for _, flag := range []string{"-approval", "-approval-receipt", "-approval-trust-bundle", "-previous-approval-trust-bundle", "-prayer-policy"} {
		conflicting := append(append([]string(nil), args...), flag, path)
		if err := run(conflicting, io.Discard); err == nil || !strings.Contains(err.Error(), "conflict") {
			t.Fatalf("%s should fail at admission before writing: %v", flag, err)
		}
	}
	inspection.Candidate.Days[0].Fajr = "04:01"
	badPath := writeJSONFile(t, dir, "modified-inspection.json", inspection)
	args[2] = badPath
	args[len(args)-1] = filepath.Join(dir, "invalid-request.json")
	if err := run(args, io.Discard); err == nil {
		t.Fatal("tampered public candidate assembled")
	}
}

func publicInspection(t *testing.T) inspectionInput {
	t.Helper()
	stamp := "2026-09-08T06:00:00Z"
	at := time.Date(2026, 9, 8, 6, 1, 0, 0, time.UTC)
	raw := []byte("synthetic publication CLI source")
	sum := sha256.Sum256(raw)
	rawSHA := hex.EncodeToString(sum[:])
	catalog := qualification.CatalogBinding{Revision: "synthetic-catalog", SourceRevision: "2026-09-08", Region: domain.Region{ID: "synthetic-region", Name: "Synthetic region", CountryCode: "RU"}, Cities: []domain.City{{ID: "synthetic-city", Name: "Synthetic city", CountryCode: "RU", RegionID: "synthetic-region", Timezone: "Europe/Moscow", GeographicRevision: "2026-09-08"}}}
	review := qualification.EvidenceReview{Authority: domain.PrayerAuthority{ID: "synthetic-authority", Name: "Synthetic authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"}, Scope: domain.GeographicScope{ID: "synthetic-scope", Kind: domain.GeographicScopeCity, CityID: "synthetic-city", RegionID: "synthetic-region", Description: "Synthetic city only"}, CatalogRevision: catalog.Revision, Timezone: "Europe/Moscow", FreshThrough: "2026-09-08", Retrieval: domain.PublicSourceRetrieval{URL: "https://authority.example/calendar", HTTPStatus: 200, ContentType: "text/csv"}, TermsAssessment: "public_transport_no_restriction_observed"}
	context, err := qualification.PublicDisplayContext(review.Scope, catalog, review.Timezone)
	if err != nil {
		t.Fatal(err)
	}
	c := domain.CandidateSchedule{DataClassification: domain.DataClassificationProduction, Mosque: context, Source: domain.CandidateSource{SourceID: "synthetic-source", Kind: domain.ProviderKindOfficialFile, AuthorityName: review.Authority.Name, GeographicScope: review.Scope.Description, CanonicalURL: review.Retrieval.URL, MaxDeltaMinutes: 15, MinimumCoverageDays: 1}, Artifact: domain.RawArtifact{Filename: "synthetic.csv", ContentType: "text/csv", CapturedAt: stamp, ByteLength: int64(len(raw)), SHA256: rawSHA}, TranscriptionSHA256: rawSHA, ParserVersion: "synthetic-publication-cli/v1", Coverage: domain.DateRange{From: "2026-09-08", To: "2026-09-08"}, Days: []domain.CandidatePrayerDay{{PrayerDay: domain.PrayerDay{Date: "2026-09-08", Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}, Status: domain.CandidateNeedsReview}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		review.Evidence = append(review.Evidence, domain.SourceEvidence{ID: purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: stamp, SHA256: strings.Repeat("e", 64), Claim: "Synthetic CLI evidence: " + purpose})
	}
	review.Comparisons = []domain.SourceValueComparison{{EvidenceID: "value_comparison", Day: c.Days[0].PrayerDay}}
	c.Validation = controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: c.Mosque.Timezone}, c)
	if err := domain.FinalizeCandidateIdentity(&c); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, c)
	if err != nil {
		t.Fatal(err)
	}
	q, err := qualification.Create(review, c, raw, diff.SHA256, catalog, at)
	if err != nil {
		t.Fatal(err)
	}
	return inspectionInput{Candidate: c, Diff: diff, Qualification: &q}
}
