package onboarding

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
	"github.com/andy-ahmedov/namaz-time/internal/providers/saratov"
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

func TestInspectSaratovBindsExactCityMonthAndOnsets(t *testing.T) {
	m, catalog, raw, at := syntheticSaratovImport(t)
	got, err := Inspect(m, catalog, raw, nil, nil, at)
	if err != nil {
		t.Fatal(err)
	}
	if got.Candidate.ParserVersion != saratov.ParserVersion || got.Candidate.Source.Kind != domain.ProviderKindOfficialHTML ||
		got.Candidate.Source.CanonicalURL != "https://dumso.ru/raspisanie" || got.Candidate.Source.Attribution != m.Attribution ||
		got.Qualification.Scope.CityID != "city-saratov-synthetic" || got.Qualification.Scope.Kind != domain.GeographicScopeCity ||
		got.Qualification.Timezone != "Europe/Saratov" || got.Candidate.TranscriptionSHA256 != hash(raw) || len(got.Candidate.Days) != 30 {
		t.Fatal("Saratov import lost its exact source, city, timezone, artifact or month binding")
	}
	for i, day := range got.Candidate.Days {
		want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
			Date: fmt.Sprintf("2026-09-%02d", i+1), Fajr: "04:00", Sunrise: "06:00",
			Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00",
		}}
		if !reflect.DeepEqual(day, want) {
			t.Fatalf("day %d differs or mosque performance times were inferred: %+v", i+1, day)
		}
	}
	if err := qualification.VerifyCandidate(got.Qualification, got.Candidate, got.Diff.SHA256, at); err != nil {
		t.Fatal(err)
	}
}

func TestSaratovImportFailsClosedWithoutPartialInspection(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Manifest, *geography.Catalog, *[]byte)
	}{
		{"neighboring city", func(m *Manifest, c *geography.Catalog, _ *[]byte) {
			m.Locality, c.Cities[0].Name = "Энгельс", "Энгельс"
		}},
		{"catalog alias is not publisher scope", func(m *Manifest, c *geography.Catalog, _ *[]byte) {
			m.Locality, c.Cities[0].Aliases = "Saratov", []string{"Saratov"}
		}},
		{"another city with source-name alias", func(_ *Manifest, c *geography.Catalog, _ *[]byte) {
			c.Cities[0].Name, c.Cities[0].Aliases = "Энгельс", []string{"Саратов"}
		}},
		{"another subject", func(_ *Manifest, c *geography.Catalog, _ *[]byte) { c.Regions[0].FederalSubjectCode = "RU-TA" }},
		{"another timezone", func(m *Manifest, c *geography.Catalog, _ *[]byte) {
			m.Review.Timezone, c.Cities[0].Timezone = "Europe/Moscow", "Europe/Moscow"
		}},
		{"region-wide expansion", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Review.Scope.Kind, m.Review.Scope.CityID = domain.GeographicScopeRegion, ""
		}},
		{"file kind", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.SourceKind = domain.ProviderKindOfficialFile }},
		{"generic calculation", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.SourceKind = domain.ProviderKindCalculationProfile
		}},
		{"mislabeled canonical URL", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.CanonicalURL, m.Review.Retrieval.URL = "https://authority.example/another", "https://authority.example/another"
		}},
		{"separate extraction", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Extraction = &Extraction{SHA256: m.Artifact.SHA256, Method: "invented"}
		}},
		{"unsupported month", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Coverage.To = "2026-10-01" }},
		{"unsupported year", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Coverage = domain.DateRange{From: "2025-09-01", To: "2025-09-30"}
		}},
		{"changed month context", func(m *Manifest, _ *geography.Catalog, raw *[]byte) {
			*raw = []byte(strings.Replace(string(*raw), "Сентябрь 2026", "Сентябрь 2025", 1))
			m.Artifact.SHA256, m.Artifact.ByteLength = hash(*raw), int64(len(*raw))
		}},
		{"missing attribution", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Attribution = "" }},
		{"missing currentness evidence", func(m *Manifest, _ *geography.Catalog, _ *[]byte) {
			m.Review.Evidence = append(m.Review.Evidence[:2], m.Review.Evidence[3:]...)
		}},
		{"independent comparison mismatch", func(m *Manifest, _ *geography.Catalog, _ *[]byte) { m.Review.Comparisons[1].Day.Fajr = "04:01" }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			m, c, raw, at := syntheticSaratovImport(t)
			tc.mutate(&m, &c, &raw)
			// Keep the catalog fingerprint valid, so identity guards cannot
			// pass these cases merely by detecting an unrelated stale hash.
			setSyntheticCatalogHash(t, &c)
			m.CatalogContentSHA256 = c.Revision.ContentSHA256
			got, err := Inspect(m, c, raw, nil, nil, at)
			if err == nil || got.Qualification.ID != "" || got.Candidate.ID != "" || len(got.Candidate.Days) != 0 {
				t.Fatalf("invalid Saratov import produced usable output: %v", err)
			}
		})
	}
}

// Opt-in, offline reproduction of independently captured public bytes. This
// calls only the dispatcher/parser, not qualification or publication; the full
// raw tables and independently extracted reference stay outside Git.
func TestRetainedSaratovDispatcherMatchesIndependentPrint(t *testing.T) {
	dir := os.Getenv("T049_SARATOV_IMPORT_ARTIFACT_DIR")
	if dir == "" {
		t.Skip("retained Saratov import captures not configured")
	}
	read := func(name, expectedSHA string) []byte {
		t.Helper()
		raw, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Fatal(err)
		}
		if hash(raw) != expectedSHA {
			t.Fatalf("%s retained capture hash mismatch", name)
		}
		return raw
	}
	raw := read("saratov-raspisanie.html", "6d556e59ee203e519e3c43f42171fb90de0cd4c7872c472eb4af36be3b0169d6")
	read("saratov-print.html", "4f94b2a5dfcab1e406c4d870732da65b021af27d90b9f26bcf26461a18933564")
	read("saratov-page2348.json", "b6c08e8d648189ac9988aa279f836852d18ad79522b94bbc82463dd3d315c3f5")
	reference := read("independent-comparison.json", "c46ad2e66c24a4b3ab9d193aa773314e18090e343cc1fc4349109ce4b62e6940")
	var compared struct {
		DateCount    int                `json:"date_count"`
		MatrixSHA256 string             `json:"matrix_sha256"`
		Days         []domain.PrayerDay `json:"days"`
	}
	if err := json.Unmarshal(reference, &compared); err != nil {
		t.Fatal(err)
	}
	if compared.DateCount != 30 || len(compared.Days) != 30 || compared.MatrixSHA256 != "275824c832aaaeb838189232769eeb2c185cdd3a5c15ba16d486c835e9163b3f" {
		t.Fatal("independent reference does not cover the complete reviewed month")
	}
	city := domain.City{ID: "city-b186fd91bf7761113bf427894d18f605", Name: "Саратов", RegionID: "ru-sar", CountryCode: "RU", Timezone: "Europe/Saratov"}
	binding := qualification.CatalogBinding{Region: domain.Region{ID: "ru-sar", CountryCode: "RU", FederalSubjectCode: "RU-SAR"}, Cities: []domain.City{city}}
	m := Manifest{ParserVersion: saratov.ParserVersion, SourceKind: domain.ProviderKindOfficialHTML, CanonicalURL: "https://dumso.ru/raspisanie", Locality: city.Name,
		Coverage: domain.DateRange{From: "2026-09-01", To: "2026-09-30"}, Artifact: domain.RawArtifact{SHA256: hash(raw)},
		Review: qualification.EvidenceReview{Scope: domain.GeographicScope{Kind: domain.GeographicScopeCity, CityID: city.ID, RegionID: city.RegionID}, Timezone: city.Timezone}}
	got, transcriptionSHA, err := parse(m, binding, raw, nil)
	if err != nil || len(got) != 30 || transcriptionSHA != hash(raw) {
		t.Fatalf("retained native dispatch: days=%d err=%v", len(got), err)
	}
	for i, day := range got {
		if !reflect.DeepEqual(day, domain.CandidatePrayerDay{PrayerDay: compared.Days[i]}) {
			t.Fatalf("date %s differs from independently extracted print or inferred fields were added", day.Date)
		}
	}
	m.Coverage.From = "2026-09-08"
	part, _, err := parse(m, binding, raw, nil)
	if err != nil || !reflect.DeepEqual(part, got[7:]) {
		t.Fatalf("retained bounded dispatch differs: %v", err)
	}
	t.Log("fresh main source: all 30 dates × 6 fields match independently extracted print; no qualification performed")
}

// Clocks and qualification evidence are synthetic; only the reviewed public
// markup and explicit city/month/footnote labels match the source format.
func syntheticSaratovImport(t *testing.T) (Manifest, geography.Catalog, []byte, time.Time) {
	t.Helper()
	m, c, _, at := syntheticImport(t)
	c.Regions[0].ID, c.Regions[0].FederalSubjectCode = "ru-sar", "RU-SAR"
	c.Cities[0].ID, c.Cities[0].Name, c.Cities[0].RegionID, c.Cities[0].Timezone = "city-saratov-synthetic", "Саратов", "ru-sar", "Europe/Saratov"
	setSyntheticCatalogHash(t, &c)
	m.ParserVersion, m.SourceID, m.SourceKind = saratov.ParserVersion, "synthetic-saratov-shape", domain.ProviderKindOfficialHTML
	m.CanonicalURL, m.Locality = "https://dumso.ru/raspisanie", "Саратов"
	m.Coverage = domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	m.Attribution = "Synthetic test attribution: https://dumso.ru/raspisanie"
	m.CatalogContentSHA256 = c.Revision.ContentSHA256
	m.Review.Scope.CityID, m.Review.Scope.RegionID, m.Review.Timezone = c.Cities[0].ID, c.Regions[0].ID, c.Cities[0].Timezone
	m.Review.FreshThrough, m.Review.TermsAssessment = m.Coverage.To, "public_transport_attribution_required"
	m.Review.Retrieval.URL, m.Review.Retrieval.ContentType = m.CanonicalURL, "text/html; charset=UTF-8"
	m.Review.Comparisons = nil
	for _, day := range []string{"2026-09-01", "2026-09-08", "2026-09-30"} {
		m.Review.Comparisons = append(m.Review.Comparisons, domain.SourceValueComparison{EvidenceID: "evidence-value_comparison", Day: domain.PrayerDay{Date: day, Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}})
	}
	var rows strings.Builder
	weekdays := [...]string{"Воскрес", "Понедел", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"}
	for day := 1; day <= 30; day++ {
		date := time.Date(2026, time.September, day, 0, 0, 0, 0, time.UTC)
		fmt.Fprintf(&rows, `<tr><td>%d</td><td>%s</td><td>%d</td><td>4:00</td><td>6.00</td><td>12.00</td><td>16.00</td><td>18.00</td><td>20.00</td></tr>`, day, weekdays[date.Weekday()], (day+18)%30+1)
	}
	raw := []byte(`<!DOCTYPE html><html><head><title>Synthetic fixture</title><link rel="canonical" href="https://dumso.ru/raspisanie"><link rel="alternate" type="application/json" href="https://dumso.ru/wp-json/wp/v2/pages/2348"></head><body><div id="wrap"><div id="menu"><h1>«Духовное управление мусульман Саратовской области»</h1></div><div id="extras"><center><a href="/raspisanie" title="Расписание намазов в г. Саратове">на месяц</a></center><div id="calendar_wrap"><table id="wp-calendar" class="wp-calendar-table"><caption>Сентябрь 2026</caption></table></div></div><div id="contentwide"><div class="post"><h2>Расписание намазов на сентябрь</h2><p align="center"><strong>«Поистине молитва для верующих предписана в определенное время»</strong><br>(Коран: сура 4, аят 103)</p><div class="print_btn"><a href="https://dumso.ru/print/namaz-time.html" title="Версия для печати">Версия для печати</a></div><table class="namaz_time"><tbody><tr><td><strong>сентябрь</strong></td><td>день<br>недели</td><td><strong>Раби аль-авваль/</strong><br><strong>Раби аль-ахир</strong></td><td><strong>Фажр</strong><br>утрен.<br>намаз</td><td><strong>Восход</strong><br><strong>солнца</strong></td><td><strong>Зухр</strong><br>уля<br>намаз</td><td><strong>Аср</strong><br>икенде<br>намаз</td><td><strong>Магриб</strong><br>ахшам</td><td><strong>Ийша</strong><br>ясых</td></tr>` + rows.String() + `</tbody></table><p>&nbsp;</p><ul><li>Азан на зухр намаз в соборной мечети 13:15</li><li>Фажр намаз в мечети через 45 минут после наступления времени.</li></ul></div></div></div></body></html>`)
	m.Artifact.Filename, m.Artifact.ContentType, m.Artifact.ByteLength, m.Artifact.SHA256 = "synthetic-saratov.html", m.Review.Retrieval.ContentType, int64(len(raw)), hash(raw)
	return m, c, raw, at
}

func setSyntheticCatalogHash(t *testing.T, c *geography.Catalog) {
	t.Helper()
	encoded, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{c.Regions, c.Cities})
	if err != nil {
		t.Fatal(err)
	}
	c.Revision.ContentSHA256 = hash(encoded)
}

func syntheticImport(t *testing.T) (Manifest, geography.Catalog, []byte, time.Time) {
	t.Helper()
	at := time.Date(2026, 9, 8, 6, 0, 0, 0, time.UTC)
	stamp := at.Add(-time.Minute).Format(time.RFC3339)
	raw := []byte("08.09.2026;4:00;4:30;6:00;11:55;12:00;16:00;18:00;20:00\n")
	c := geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{ID: "catalog-synthetic-v1", ImportedCities: 1}, Regions: []domain.Region{{ID: "ru-ta", Name: "Synthetic region", CountryCode: "RU", FederalSubjectCode: "RU-TA"}}, Cities: []domain.City{{ID: "city-kazan-synthetic", Name: "Казань", CountryCode: "RU", RegionID: "ru-ta", Timezone: "Europe/Moscow", GeographicRevision: "2026-09-08", GeographicSource: "https://www.geonames.org/1", GeographicSourceID: "1", GeographicLicense: "CC BY 4.0"}}}
	setSyntheticCatalogHash(t, &c)
	m := Manifest{SchemaVersion: ManifestSchema, DataClassification: domain.DataClassificationSynthetic, ParserVersion: dumrt.ParserVersion, SourceID: "synthetic-dumrt-shape", SourceKind: domain.ProviderKindOfficialFile, CanonicalURL: "https://authority.example/synthetic.csv", Locality: "Казань", Coverage: domain.DateRange{From: "2026-09-08", To: "2026-09-08"}, Artifact: domain.RawArtifact{Filename: "synthetic.csv", ContentType: "text/csv", CapturedAt: stamp, ByteLength: int64(len(raw)), SHA256: hash(raw)}, CatalogContentSHA256: c.Revision.ContentSHA256, MaxDeltaMinutes: 15,
		Review: qualification.EvidenceReview{Authority: domain.PrayerAuthority{ID: "synthetic-authority", Name: "Synthetic authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"}, Scope: domain.GeographicScope{ID: "synthetic-scope", Kind: domain.GeographicScopeCity, CityID: c.Cities[0].ID, RegionID: c.Regions[0].ID, Description: "Synthetic city only"}, CatalogRevision: c.Revision.ID, Timezone: c.Cities[0].Timezone, FreshThrough: "2026-09-08", Retrieval: domain.PublicSourceRetrieval{URL: "https://authority.example/synthetic.csv", HTTPStatus: 200, ContentType: "text/csv"}, TermsAssessment: "public_transport_no_restriction_observed"}}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		m.Review.Evidence = append(m.Review.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: stamp, SHA256: strings.Repeat("e", 64), Claim: "Synthetic protocol evidence: " + purpose})
	}
	m.Review.Comparisons = []domain.SourceValueComparison{{EvidenceID: "evidence-value_comparison", Day: domain.PrayerDay{Date: "2026-09-08", Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}
	return m, c, raw, at
}

func hash(raw []byte) string { sum := sha256.Sum256(raw); return hex.EncodeToString(sum[:]) }
