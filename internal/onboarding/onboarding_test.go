package onboarding

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

func TestInspectQualifiedPublicSourceBindsRawCatalogAndExactOnsets(t *testing.T) {
	m, catalog, raw, at := syntheticImport(t)
	got, err := Inspect(m, catalog, raw, nil, nil, at)
	if err != nil {
		t.Fatal(err)
	}
	if got.Candidate.Source.PermissionStatus != "" || got.Candidate.Source.ApprovalRequired || got.Candidate.ParserVersion != dumrt.ParserVersion || len(got.Candidate.Days) != 1 || got.Candidate.Days[0].Fajr != "04:00" || got.Candidate.Days[0].RecommendedFajr != "04:30" {
		t.Fatalf("incorrect qualified candidate: %+v", got.Candidate)
	}
	if err := qualification.VerifyCandidate(got.Qualification, got.Candidate, got.Diff.SHA256, at); err != nil {
		t.Fatal(err)
	}
	if got.Qualification.Artifact.SHA256 != hash(raw) || got.Qualification.OnsetSHA256 == "" || got.Qualification.Scope.CityID != "city-kazan-synthetic" {
		t.Fatal("missing exact operational bindings")
	}
	again, err := Inspect(m, catalog, raw, nil, nil, at)
	if err != nil {
		t.Fatal(err)
	}
	left, _ := json.Marshal(got)
	right, _ := json.Marshal(again)
	if string(left) != string(right) {
		t.Fatal("import is not deterministic")
	}
}

func TestPublicImportFailsClosedWithoutPartialInspection(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Manifest, *geography.Catalog, *[]byte)
	}{
		{"unknown adapter", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.ParserVersion = "generic-russia/v1" }},
		{"wrong raw hash", func(_ *Manifest, _ *geography.Catalog, raw *[]byte) { *raw = append(*raw, ' ') }},
		{"wrong raw byte length", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Artifact.ByteLength++ }},
		{"catalog fingerprint", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.CatalogContentSHA256 = strings.Repeat("9", 64) }},
		{"missing evidence", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Review.Evidence = nil }},
		{"different published values", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Review.Comparisons[0].Day.Fajr = "04:01" }},
		{"incomplete range", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Coverage.To = "2026-09-09" }},
		{"scope expansion", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Review.Scope.Kind = domain.GeographicScopeRegion
			m.Review.Scope.CityID = ""
		}},
		{"unknown country method", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.SourceKind = domain.ProviderKindCalculationProfile
		}},
		{"missing named locality", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Locality = "" }},
		{"wrong catalog revision", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Review.CatalogRevision = "other-catalog" }},
		{"spurious extraction", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Extraction = &Extraction{SHA256: m.Artifact.SHA256, Method: "invented"}
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			m, c, raw, at := syntheticImport(t)
			tc.mutate(&m, &c, &raw)
			got, err := Inspect(m, c, raw, nil, nil, at)
			if err == nil || got.Qualification.ID != "" || got.Candidate.ID != "" {
				t.Fatalf("invalid import produced usable output: %v", err)
			}
		})
	}
}

func syntheticImport(t *testing.T) (Manifest, geography.Catalog, []byte, time.Time) {
	t.Helper()
	at := time.Date(2026, 9, 8, 6, 0, 0, 0, time.UTC)
	stamp := at.Add(-time.Minute).Format(time.RFC3339)
	raw := []byte("08.09.2026;4:00;4:30;6:00;11:55;12:00;16:00;18:00;20:00\n")
	c := geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{ID: "catalog-synthetic-v1", ImportedCities: 1}, Regions: []domain.Region{{ID: "ru-ta", Name: "Synthetic region", CountryCode: "RU", FederalSubjectCode: "RU-TA"}}, Cities: []domain.City{{ID: "city-kazan-synthetic", Name: "Казань", CountryCode: "RU", RegionID: "ru-ta", Timezone: "Europe/Moscow", GeographicRevision: "2026-09-08", GeographicSource: "https://www.geonames.org/1", GeographicSourceID: "1", GeographicLicense: "CC BY 4.0"}}}
	encoded, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{c.Regions, c.Cities})
	if err != nil {
		t.Fatal(err)
	}
	c.Revision.ContentSHA256 = hash(encoded)
	m := Manifest{SchemaVersion: ManifestSchema, DataClassification: domain.DataClassificationSynthetic, ParserVersion: dumrt.ParserVersion, SourceID: "synthetic-dumrt-shape", SourceKind: domain.ProviderKindOfficialFile, CanonicalURL: "https://authority.example/synthetic.csv", Locality: "Казань", Coverage: domain.DateRange{From: "2026-09-08", To: "2026-09-08"}, Artifact: domain.RawArtifact{Filename: "synthetic.csv", ContentType: "text/csv", CapturedAt: stamp, ByteLength: int64(len(raw)), SHA256: hash(raw)}, CatalogContentSHA256: c.Revision.ContentSHA256, MaxDeltaMinutes: 15,
		Review: qualification.EvidenceReview{Authority: domain.PrayerAuthority{ID: "synthetic-authority", Name: "Synthetic authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"}, Scope: domain.GeographicScope{ID: "synthetic-scope", Kind: domain.GeographicScopeCity, CityID: c.Cities[0].ID, RegionID: c.Regions[0].ID, Description: "Synthetic city only"}, CatalogRevision: c.Revision.ID, Timezone: c.Cities[0].Timezone, FreshThrough: "2026-09-08", Retrieval: domain.PublicSourceRetrieval{URL: "https://authority.example/synthetic.csv", HTTPStatus: 200, ContentType: "text/csv"}, TermsAssessment: "public_transport_no_restriction_observed"}}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		m.Review.Evidence = append(m.Review.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: stamp, SHA256: strings.Repeat("e", 64), Claim: "Synthetic protocol evidence: " + purpose})
	}
	m.Review.Comparisons = []domain.SourceValueComparison{{EvidenceID: "evidence-value_comparison", Day: domain.PrayerDay{Date: "2026-09-08", Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}
	return m, c, raw, at
}

func hash(raw []byte) string { sum := sha256.Sum256(raw); return hex.EncodeToString(sum[:]) }
