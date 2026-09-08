package qualification

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
)

func TestCreateQualifiesExactPublicTableAndRejectsUseAfterExpiry(t *testing.T) {
	review, candidate, raw, catalog, at := qualificationFixture(t)
	q, err := Create(review, candidate, raw, strings.Repeat("d", 64), catalog, at)
	if err != nil {
		t.Fatal(err)
	}
	if q.State != "qualified" || q.DecisionSystem != domain.SourceQualificationDecisionSystem || q.Artifact.SHA256 != candidate.Artifact.SHA256 || q.ValidatedDays != 3 {
		t.Fatalf("incomplete qualification: %+v", q)
	}
	if err := VerifyCandidate(q, candidate, strings.Repeat("d", 64), at.Add(time.Hour)); err != nil {
		t.Fatal(err)
	}
	if err := VerifyCandidate(q, candidate, strings.Repeat("d", 64), at.AddDate(0, 0, 3)); err == nil {
		t.Fatal("expired table remained publishable")
	}
	if candidate.Source.PermissionStatus != "" || candidate.Source.ApprovalRequired {
		t.Fatal("public qualification invented legacy permission")
	}
}

func TestCreateRejectsMissingEvidenceRawDriftAndGeographicExpansion(t *testing.T) {
	tests := []struct {
		name   string
		change func(*EvidenceReview, *domain.CandidateSchedule, *[]byte, *CatalogBinding)
	}{
		{"no public evidence", func(r *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) { r.Evidence = nil }},
		{"raw changed", func(_ *EvidenceReview, _ *domain.CandidateSchedule, raw *[]byte, _ *CatalogBinding) {
			*raw = append(*raw, 'x')
		}},
		{"wrong canonical city", func(_ *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, c *CatalogBinding) {
			c.Cities[0].ID = "neighbor"
		}},
		{"catalog revision changed", func(_ *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, c *CatalogBinding) {
			c.Revision += "-new"
		}},
		{"city moved to neighbor region", func(_ *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, c *CatalogBinding) {
			c.Cities[0].RegionID = "neighbor-region"
		}},
		{"city timezone changed", func(_ *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, c *CatalogBinding) {
			c.Cities[0].Timezone = "Asia/Omsk"
		}},
		{"scope expanded", func(r *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) {
			r.Scope.Kind, r.Scope.CityID = domain.GeographicScopeRegion, ""
		}},
		{"new unknown authority", func(r *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) {
			r.Authority.Name = "Another organization"
		}},
		{"wrong independently read time", func(r *EvidenceReview, _ *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) {
			r.Comparisons[0].Day.Fajr = "04:01"
		}},
		{"candidate content changed", func(_ *EvidenceReview, c *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) {
			c.Days[1].Isha = "20:01"
		}},
		{"fabricated validation report", func(_ *EvidenceReview, c *domain.CandidateSchedule, _ *[]byte, _ *CatalogBinding) {
			c.Validation.Warnings = []domain.CandidateDiagnostic{{Code: "made-up"}}
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, c, raw, catalog, at := qualificationFixture(t)
			tt.change(&r, &c, &raw, &catalog)
			if q, err := Create(r, c, raw, strings.Repeat("d", 64), catalog, at); err == nil || q.ID != "" {
				t.Fatal("invalid proof returned a qualification")
			}
		})
	}
}

func TestVerifyCandidateRejectsReboundProofAndUnresolvedWarnings(t *testing.T) {
	r, candidate, raw, catalog, at := qualificationFixture(t)
	q, err := Create(r, candidate, raw, strings.Repeat("d", 64), catalog, at)
	if err != nil {
		t.Fatal(err)
	}
	changed := candidate
	changed.Source.CanonicalURL = "https://another.example/calendar"
	if err := VerifyCandidate(q, changed, strings.Repeat("d", 64), at); err == nil {
		t.Fatal("source rebinding accepted")
	}
	if err := VerifyCandidate(q, candidate, strings.Repeat("e", 64), at); err == nil {
		t.Fatal("different diff accepted")
	}
	if err := VerifyCandidate(q, candidate, strings.Repeat("d", 64), at.Add(-time.Minute)); err == nil {
		t.Fatal("qualification used before its decision time")
	}
	candidate.Days[1].Fajr = "03:00"
	r.Comparisons[1].Day.Fajr = "03:00"
	finalizeQualificationCandidate(t, &candidate)
	if _, err := Create(r, candidate, raw, strings.Repeat("d", 64), catalog, at); err == nil {
		t.Fatal("unresolved day-to-day jump accepted")
	}
	r.WarningResolutions = []domain.SourceWarningResolution{{Code: "day_to_day_delta", EvidenceID: "evidence-value_comparison", Reason: "Synthetic fixture independently reproduces both sides of the recorded transition"}}
	if _, err := Create(r, candidate, raw, strings.Repeat("d", 64), catalog, at); err != nil {
		t.Fatalf("fully bound researched transition rejected: %v", err)
	}
}

func qualificationFixture(t *testing.T) (EvidenceReview, domain.CandidateSchedule, []byte, CatalogBinding, time.Time) {
	t.Helper()
	raw := []byte("synthetic retained artifact; not real prayer values")
	hash := sha256.Sum256(raw)
	catalog := CatalogBinding{
		Revision:       "catalog-synthetic-v1",
		SourceRevision: "2026-09-08",
		Region:         domain.Region{ID: "region-synthetic", Name: "Synthetic region", CountryCode: "RU", FederalSubjectCode: "RU-TEST"},
		Cities:         []domain.City{{ID: "city-synthetic", Name: "Synthetic city", RegionID: "region-synthetic", CountryCode: "RU", Timezone: "Europe/Moscow", GeographicRevision: "2026-09-08"}},
	}
	review := EvidenceReview{
		Authority:       domain.PrayerAuthority{ID: "authority-synthetic", Name: "Synthetic authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"},
		Scope:           domain.GeographicScope{ID: "scope-synthetic", Kind: domain.GeographicScopeCity, CityID: "city-synthetic", RegionID: "region-synthetic", Description: "Synthetic city only"},
		CatalogRevision: catalog.Revision, Timezone: "Europe/Moscow", FreshThrough: "2026-09-03",
		Retrieval:       domain.PublicSourceRetrieval{URL: "https://authority.example/calendar", HTTPStatus: 200, ContentType: "text/plain"},
		TermsAssessment: "public_transport_no_restriction_observed",
	}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		review.Evidence = append(review.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: "2026-09-01T08:00:00Z", SHA256: strings.Repeat("e", 64), Claim: "Synthetic evidence for " + purpose})
	}
	// Context is constructed independently here so the initial red test reaches
	// the missing qualification behavior, not an unimplemented fixture helper.
	scopeHash := sha256.Sum256([]byte(review.Scope.ID))
	candidate := domain.CandidateSchedule{
		DataClassification:  domain.DataClassificationSynthetic,
		Mosque:              domain.Mosque{ID: "public-scope-" + hex.EncodeToString(scopeHash[:16]), Name: catalog.Cities[0].Name, CountryCode: "RU", Region: catalog.Region.Name, Locality: catalog.Cities[0].Name, Timezone: "Europe/Moscow"},
		Source:              domain.CandidateSource{SourceID: "source-synthetic", Kind: domain.ProviderKindOfficialFile, AuthorityName: review.Authority.Name, GeographicScope: review.Scope.Description, CanonicalURL: review.Retrieval.URL, MinimumCoverageDays: 3, MaxDeltaMinutes: 15},
		Artifact:            domain.RawArtifact{Filename: review.Retrieval.URL, ContentType: "text/plain", CapturedAt: "2026-09-01T08:00:00Z", ByteLength: int64(len(raw)), SHA256: hex.EncodeToString(hash[:])},
		TranscriptionSHA256: hex.EncodeToString(hash[:]), ParserVersion: "synthetic-test/v1", Coverage: domain.DateRange{From: "2026-09-01", To: "2026-09-03"},
	}
	for _, date := range []string{"2026-09-01", "2026-09-02", "2026-09-03"} {
		day := domain.PrayerDay{Date: date, Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "15:00", Maghrib: "18:00", Isha: "20:00"}
		candidate.Days = append(candidate.Days, domain.CandidatePrayerDay{PrayerDay: day})
		review.Comparisons = append(review.Comparisons, domain.SourceValueComparison{EvidenceID: "evidence-value_comparison", Day: day})
	}
	finalizeQualificationCandidate(t, &candidate)
	return review, candidate, raw, catalog, time.Date(2026, 9, 1, 10, 0, 0, 0, time.UTC)
}

func finalizeQualificationCandidate(t *testing.T, candidate *domain.CandidateSchedule) {
	t.Helper()
	candidate.Validation = controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: candidate.Mosque.Timezone}, *candidate)
	candidate.Status = domain.CandidateNeedsReview
	if len(candidate.Validation.Errors) != 0 {
		candidate.Status = domain.CandidateValidationFailed
	}
	if err := domain.FinalizeCandidateIdentity(candidate); err != nil {
		t.Fatal(err)
	}
}
