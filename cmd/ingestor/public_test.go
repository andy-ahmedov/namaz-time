package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/onboarding"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

func TestInspectPublicCLIProducesQualifiedInspectionWithoutApproval(t *testing.T) {
	dir := t.TempDir()
	raw := []byte("08.09.2026;4:00;4:30;6:00;11:55;12:00;16:00;18:00;20:00\n")
	hash := func(value []byte) string { sum := sha256.Sum256(value); return hex.EncodeToString(sum[:]) }
	catalog := geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{ID: "synthetic-catalog", ImportedCities: 1}, Regions: []domain.Region{{ID: "ru-ta", Name: "Synthetic region", CountryCode: "RU", FederalSubjectCode: "RU-TA"}}, Cities: []domain.City{{ID: "synthetic-city", Name: "Казань", CountryCode: "RU", RegionID: "ru-ta", Timezone: "Europe/Moscow", GeographicRevision: "2026-09-08", GeographicSource: "https://www.geonames.org/1", GeographicSourceID: "1", GeographicLicense: "CC BY 4.0"}}}
	content, _ := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{catalog.Regions, catalog.Cities})
	catalog.Revision.ContentSHA256 = hash(content)
	stamp := "2026-09-08T06:00:00Z"
	review := qualification.EvidenceReview{Authority: domain.PrayerAuthority{ID: "synthetic-authority", Name: "Synthetic authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"}, Scope: domain.GeographicScope{ID: "synthetic-scope", Kind: domain.GeographicScopeCity, CityID: "synthetic-city", RegionID: "ru-ta", Description: "Synthetic city fixture"}, CatalogRevision: catalog.Revision.ID, Timezone: "Europe/Moscow", FreshThrough: "2026-09-08", Retrieval: domain.PublicSourceRetrieval{URL: "https://authority.example/calendar.csv", HTTPStatus: 200, ContentType: "text/csv"}, TermsAssessment: "public_transport_no_restriction_observed"}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		review.Evidence = append(review.Evidence, domain.SourceEvidence{ID: purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: stamp, SHA256: strings.Repeat("e", 64), Claim: "Synthetic CLI evidence: " + purpose})
	}
	review.Comparisons = []domain.SourceValueComparison{{EvidenceID: "value_comparison", Day: domain.PrayerDay{Date: "2026-09-08", Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}
	manifest := onboarding.Manifest{SchemaVersion: onboarding.ManifestSchema, DataClassification: domain.DataClassificationSynthetic, ParserVersion: dumrt.ParserVersion, SourceID: "synthetic-source", SourceKind: domain.ProviderKindOfficialFile, CanonicalURL: "https://authority.example/calendar.csv", Locality: "Казань", Coverage: domain.DateRange{From: "2026-09-08", To: "2026-09-08"}, Artifact: domain.RawArtifact{Filename: "calendar.csv", ContentType: "text/csv", CapturedAt: stamp, ByteLength: int64(len(raw)), SHA256: hash(raw)}, CatalogContentSHA256: catalog.Revision.ContentSHA256, MaxDeltaMinutes: 15, Review: review}
	for name, value := range map[string]any{"manifest.json": manifest, "catalog.json": catalog} {
		encoded, err := json.Marshal(value)
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, name), encoded, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(filepath.Join(dir, "raw.csv"), raw, 0o600); err != nil {
		t.Fatal(err)
	}
	output := filepath.Join(dir, "inspection.json")
	args := []string{"inspect-public", "--manifest", filepath.Join(dir, "manifest.json"), "--catalog", filepath.Join(dir, "catalog.json"), "--raw", filepath.Join(dir, "raw.csv"), "--at", stamp, "--out", output}
	var stdout, stderr bytes.Buffer
	if code := run(args, &stdout, &stderr); code != 0 {
		t.Fatalf("public inspect exit %d: %s", code, stderr.String())
	}
	encoded, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	var got onboarding.Inspection
	if err := json.Unmarshal(encoded, &got); err != nil {
		t.Fatal(err)
	}
	if err := got.Qualification.Validate(); err != nil {
		t.Fatal(err)
	}
	if got.Candidate.Days[0].Fajr != "04:00" || got.Qualification.SourceID != manifest.SourceID || got.Candidate.Source.ApprovalRequired {
		t.Fatal("CLI lost exact public proof/data or invented approval")
	}
	if code := run(args, &stdout, &stderr); code == 0 {
		t.Fatal("CLI overwrote immutable inspection output")
	}
	after, err := os.ReadFile(output)
	if err != nil || !bytes.Equal(encoded, after) {
		t.Fatal("repeat changed original inspection")
	}
}
