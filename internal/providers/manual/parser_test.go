package manual_test

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	jsonschema "github.com/santhosh-tekuri/jsonschema/v6"
)

const pilotRawSHA256 = "11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c"

func TestPilotFixturePreservesProvenanceAndSourceSemantics(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026-08")
	raw := readFile(t, filepath.Join(fixtureDir, "time-namaz.jpg"))
	artifact, err := manual.CaptureArtifact(
		"time-namaz.jpg",
		"image/jpeg",
		time.Date(2026, 8, 19, 22, 53, 41, 0, time.UTC),
		bytes.NewReader(raw),
	)
	if err != nil {
		t.Fatalf("CaptureArtifact() error = %v", err)
	}
	if artifact.SHA256 != pilotRawSHA256 || artifact.ByteLength != 336797 {
		t.Fatalf("artifact = %#v", artifact)
	}

	source, err := manual.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatalf("DecodeSourceRecord() error = %v", err)
	}
	candidate, err := manual.Parse(
		manual.ParseConfig{
			Mosque: domain.Mosque{
				ID:          "second-cathedral-mosque-ulyanovsk",
				Name:        "Вторая Соборная мечеть Ульяновска",
				CountryCode: "RU",
				Region:      "Ульяновская область",
				Locality:    "Ульяновск, ул. Дзержинского, 18А",
				Timezone:    "Europe/Ulyanovsk",
			},
			Source:             source,
			ExpectedRawSHA256:  pilotRawSHA256,
			DataClassification: domain.DataClassificationProduction,
		},
		artifact,
		readFile(t, filepath.Join(fixtureDir, "schedule.csv")),
	)
	if err != nil {
		t.Fatalf("Parse() error = %v", err)
	}

	if candidate.Status != domain.CandidateNeedsReview {
		t.Fatalf("Status = %q, want needs_review; validation = %#v", candidate.Status, candidate.Validation)
	}
	if candidate.Artifact.SHA256 != pilotRawSHA256 || candidate.ParserVersion != manual.ParserVersion {
		t.Fatalf("candidate provenance = %#v", candidate)
	}
	if candidate.TranscriptionSHA256 != "29c2f62f8eb9da8f984f4e26e81325033bef1499175e24512e569fe534d2f745" {
		t.Fatalf("TranscriptionSHA256 = %q", candidate.TranscriptionSHA256)
	}
	if candidate.Coverage != (domain.DateRange{From: "2026-08-01", To: "2026-08-31"}) {
		t.Fatalf("Coverage = %#v", candidate.Coverage)
	}
	if len(candidate.Days) != 31 {
		t.Fatalf("len(Days) = %d, want 31", len(candidate.Days))
	}
	assertDay := func(index int, want domain.CandidatePrayerDay) {
		t.Helper()
		got := candidate.Days[index]
		if !reflect.DeepEqual(got, want) {
			t.Fatalf("Days[%d] = %#v, want %#v", index, got, want)
		}
	}
	assertDay(1, domain.CandidatePrayerDay{
		PrayerDay: domain.PrayerDay{Date: "2026-08-02", Fajr: "02:58", Sunrise: "04:57", Dhuhr: "13:02", Asr: "18:15", Maghrib: "20:52", Isha: "23:15", Flags: []string{"source_asterisk_isha"}},
		Zenith:    "12:52", DhuhrCongregation: "13:15", HijriDay: 17, HijriMonth: "safar", HijriYear: 1447,
	})
	assertDay(2, domain.CandidatePrayerDay{
		PrayerDay: domain.PrayerDay{Date: "2026-08-03", Fajr: "01:10", Sunrise: "04:59", Dhuhr: "13:02", Asr: "18:14", Maghrib: "20:50", Isha: "23:11", Flags: []string{"source_asterisk_fajr"}},
		Zenith:    "12:52", DhuhrCongregation: "13:15", HijriDay: 18, HijriMonth: "safar", HijriYear: 1447,
	})
	if got := candidate.Days[23].DhuhrCongregation; got != "13:53" {
		t.Fatalf("August 24 congregation = %q, want 13:53", got)
	}
	if got := candidate.Days[30]; got.Zenith != "12:46" || got.Dhuhr != "12:56" {
		t.Fatalf("August 31 = %#v", got)
	}
	assertDiagnosticCode(t, candidate.Validation.Warnings, "mosque_iqamah_approval_required")
	assertDiagnosticCode(t, candidate.Validation.Warnings, "source_marker_preserved")
	assertDiagnosticCode(t, candidate.Validation.Warnings, "day_to_day_delta")
}

func TestDecodeSourceRecordRejectsDuplicateMembers(t *testing.T) {
	t.Parallel()

	data := readFile(t, filepath.Join(repositoryRoot(t), "fixtures", "pilot", "ulyanovsk-2026-08", "source-record.json"))
	duplicate := bytes.Replace(data, []byte(`"source_id":`), []byte(`"source_id":"shadowed","source_id":`), 1)

	if _, err := manual.DecodeSourceRecord(duplicate); !manual.IsErrorCode(err, "schema_drift") {
		t.Fatalf("DecodeSourceRecord() error = %v", err)
	}
}

func TestPilotSourceRecordMatchesContractSchema(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	schemaData := readFile(t, filepath.Join(root, "contracts", "source-record.schema.json"))
	schemaDocument, err := jsonschema.UnmarshalJSON(bytes.NewReader(schemaData))
	if err != nil {
		t.Fatal(err)
	}
	compiler := jsonschema.NewCompiler()
	compiler.AssertFormat()
	const schemaURL = "https://example.invalid/schemas/source-record.schema.json"
	if err := compiler.AddResource(schemaURL, schemaDocument); err != nil {
		t.Fatal(err)
	}
	schema, err := compiler.Compile(schemaURL)
	if err != nil {
		t.Fatal(err)
	}
	sourceData := readFile(t, filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026-08", "source-record.json"))
	sourceDocument, err := jsonschema.UnmarshalJSON(bytes.NewReader(sourceData))
	if err != nil {
		t.Fatal(err)
	}
	if err := schema.Validate(sourceDocument); err != nil {
		t.Fatalf("source record schema validation: %v", err)
	}
}

func TestParserFailsClosedOnChecksumAndHeaderDrift(t *testing.T) {
	t.Parallel()

	artifact, err := manual.CaptureArtifact("source.csv", "text/csv", time.Unix(0, 0).UTC(), strings.NewReader("raw"))
	if err != nil {
		t.Fatal(err)
	}
	config := syntheticConfig()
	valid := syntheticCSVForRange(t, "2025-01-01", 2)

	if _, err := manual.Parse(config, artifact, valid); !manual.IsErrorCode(err, "raw_checksum_mismatch") {
		t.Fatalf("checksum Parse() error = %v", err)
	}
	config.ExpectedRawSHA256 = artifact.SHA256
	drifted := bytes.Replace(valid, []byte("collective_dhuhr"), []byte("iqamah"), 1)
	if _, err := manual.Parse(config, artifact, drifted); !manual.IsErrorCode(err, "schema_drift") {
		t.Fatalf("schema drift Parse() error = %v", err)
	}
}

func TestParseRejectsUnvalidatedSourceRecordBypass(t *testing.T) {
	t.Parallel()

	transcription := syntheticCSVForRange(t, "2025-01-01", 2)
	artifact, err := manual.CaptureArtifact(
		"fixture.csv",
		"text/csv",
		time.Unix(0, 0).UTC(),
		bytes.NewReader(transcription),
	)
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name   string
		mutate func(*manual.ParseConfig)
	}{
		{
			name: "false official kind",
			mutate: func(config *manual.ParseConfig) {
				config.Source.Kind = domain.ProviderKindOfficialAPI
			},
		},
		{
			name: "non manual retrieval",
			mutate: func(config *manual.ParseConfig) {
				config.Source.Retrieval.Mode = "api_poll"
			},
		},
		{
			name: "approval disabled",
			mutate: func(config *manual.ParseConfig) {
				config.Source.Validation.ApprovalRequired = false
			},
		},
		{
			name: "retired source",
			mutate: func(config *manual.ParseConfig) {
				config.Source.Status = "retired"
			},
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			config := syntheticConfig()
			config.ExpectedRawSHA256 = artifact.SHA256
			test.mutate(&config)
			if _, err := manual.Parse(config, artifact, transcription); err == nil {
				t.Fatal("Parse() error = nil; unvalidated source bypass accepted")
			}
		})
	}
}

func TestCandidateValidationBlocksGapDuplicateInvalidTimeAndScope(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		csv  string
		code string
	}{
		{name: "duplicate", csv: strings.Replace(string(syntheticCSVForRange(t, "2025-01-01", 3)), "2025-01-02", "2025-01-01", 1), code: "duplicate_date"},
		{name: "gap", csv: strings.Replace(string(syntheticCSVForRange(t, "2025-01-01", 3)), "2025-01-02", "2025-01-04", 1), code: "date_gap"},
		{name: "invalid time", csv: strings.Replace(string(syntheticCSVForRange(t, "2025-01-01", 2)), "03:00", "3:00", 1), code: "invalid_time"},
		{name: "wrong timezone", csv: string(syntheticCSVForRange(t, "2025-01-01", 2)), code: "invalid_timezone"},
		{name: "wrong source scope", csv: string(syntheticCSVForRange(t, "2025-01-01", 2)), code: "source_scope_mismatch"},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			artifact, err := manual.CaptureArtifact("fixture.csv", "text/csv", time.Unix(0, 0).UTC(), strings.NewReader("raw"))
			if err != nil {
				t.Fatal(err)
			}
			config := syntheticConfig()
			config.ExpectedRawSHA256 = artifact.SHA256
			if test.name == "wrong timezone" {
				config.Mosque.Timezone = "+04:00"
			}
			if test.name == "wrong source scope" {
				config.Mosque.CountryCode = "RU"
			}
			candidate, err := manual.Parse(config, artifact, []byte(test.csv))
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}
			if candidate.Status != domain.CandidateValidationFailed {
				t.Fatalf("Status = %q, validation = %#v", candidate.Status, candidate.Validation)
			}
			assertDiagnosticCode(t, candidate.Validation.Errors, test.code)
		})
	}
}

func TestAnnualSyntheticFixturesValidate365And366Days(t *testing.T) {
	t.Parallel()

	for _, test := range []struct {
		year int
		days int
	}{{2025, 365}, {2024, 366}} {
		test := test
		t.Run(fmt.Sprint(test.year), func(t *testing.T) {
			t.Parallel()
			csv := syntheticCSVForRange(t, fmt.Sprintf("%d-01-01", test.year), test.days)
			artifact, err := manual.CaptureArtifact("synthetic.csv", "text/csv", time.Unix(0, 0).UTC(), bytes.NewReader(csv))
			if err != nil {
				t.Fatal(err)
			}
			config := syntheticConfig()
			config.ExpectedRawSHA256 = artifact.SHA256
			candidate, err := manual.Parse(config, artifact, csv)
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}
			if candidate.Status != domain.CandidateNeedsReview || len(candidate.Days) != test.days || len(candidate.Validation.Errors) != 0 {
				t.Fatalf("candidate = status %q days %d report %#v", candidate.Status, len(candidate.Days), candidate.Validation)
			}
		})
	}
}

func syntheticConfig() manual.ParseConfig {
	return manual.ParseConfig{
		Mosque: domain.Mosque{ID: "synthetic-mosque", Name: "Synthetic test mosque", CountryCode: "ZZ", Timezone: "Europe/Ulyanovsk"},
		Source: manual.SourceRecord{
			SourceID: "synthetic-annual", Kind: domain.ProviderKindManualImport,
			AuthorityName:   "Synthetic test fixture — not an authority",
			GeographicScope: manual.GeographicScope{CountryCode: "ZZ", Description: "Synthetic tests only"},
			TimezonePolicy:  manual.TimezonePolicy{Mode: "fixed_iana", IANATimezone: "Europe/Ulyanovsk"},
			Retrieval:       manual.RetrievalPolicy{Mode: "manual_upload", Cadence: "annual", StaleAfterHours: 8760},
			Permission:      manual.Permission{Status: "granted", LicenseReference: "Synthetic fixture"},
			Validation:      manual.ValidationPolicy{ApprovalRequired: true, MaxAutomaticDeltaMinutes: 30, MinimumCoverageDays: 1},
			Status:          "testing",
		},
		DataClassification: domain.DataClassificationSynthetic,
	}
}

func syntheticCSVForRange(t *testing.T, first string, count int) []byte {
	t.Helper()
	start, err := time.Parse("2006-01-02", first)
	if err != nil {
		t.Fatal(err)
	}
	var value strings.Builder
	value.WriteString(manual.CSVHeader + "\n")
	for day := 0; day < count; day++ {
		date := start.AddDate(0, 0, day).Format("2006-01-02")
		fmt.Fprintf(&value, "%s,03:00,05:00,12:00,12:10,,16:00,19:00,21:00,1,synthetic,1447,synthetic\n", date)
	}
	return []byte(value.String())
}

func assertDiagnosticCode(t *testing.T, diagnostics []domain.CandidateDiagnostic, code string) {
	t.Helper()
	for _, diagnostic := range diagnostics {
		if diagnostic.Code == code {
			return
		}
	}
	t.Fatalf("diagnostics %#v do not contain %q", diagnostics, code)
}

func repositoryRoot(t *testing.T) string {
	t.Helper()
	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	return filepath.Clean(filepath.Join(workingDirectory, "..", "..", ".."))
}

func readFile(t *testing.T, path string) []byte {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return data
}
