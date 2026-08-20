package officialpdf_test

import (
	"bytes"
	"encoding/csv"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/providers/officialpdf"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	jsonschema "github.com/santhosh-tekuri/jsonschema/v6"
)

const (
	annualRawSHA256           = "82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21"
	annualTranscriptionSHA256 = "41b44173e99534ffc7d6a1ee7b429f98a95ca863bb29d2971b57e8693dd51538"
	annualNormalizedSHA256    = "867862c453fc167a9c9b17240dfb4c9a5be0822882c3dfbc68e8c454a4c7efb2"
)

func TestPilotAnnualFixturePreservesFullYearProvenanceAndSeasonalRules(t *testing.T) {
	t.Parallel()

	candidate := parseAnnualFixture(t)
	if candidate.Status != domain.CandidateNeedsReview || len(candidate.Validation.Errors) != 0 {
		t.Fatalf("candidate status=%q validation=%#v", candidate.Status, candidate.Validation)
	}
	if candidate.Source.Kind != domain.ProviderKindOfficialFile || candidate.ParserVersion != officialpdf.ParserVersion {
		t.Fatalf("source/parser = %#v / %q", candidate.Source, candidate.ParserVersion)
	}
	if candidate.Artifact.SHA256 != annualRawSHA256 || candidate.Artifact.ByteLength != 35078839 {
		t.Fatalf("artifact = %#v", candidate.Artifact)
	}
	if candidate.TranscriptionSHA256 != annualTranscriptionSHA256 {
		t.Fatalf("transcription SHA-256 = %q", candidate.TranscriptionSHA256)
	}
	if candidate.NormalizedSHA256 != annualNormalizedSHA256 {
		t.Fatalf("normalized SHA-256 = %q", candidate.NormalizedSHA256)
	}
	if candidate.Coverage != (domain.DateRange{From: "2026-01-01", To: "2026-12-31"}) || len(candidate.Days) != 365 {
		t.Fatalf("coverage=%#v days=%d", candidate.Coverage, len(candidate.Days))
	}

	assertDay := func(date string, want domain.CandidatePrayerDay) {
		t.Helper()
		got := dayByDate(t, candidate, date)
		if !reflect.DeepEqual(got, want) {
			t.Fatalf("day %s = %#v, want %#v", date, got, want)
		}
	}
	assertDay("2026-01-01", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-01-01", Fajr: "06:52", Sunrise: "09:05", Dhuhr: "13:00", Asr: "14:48", Maghrib: "16:41", Isha: "18:27"},
		RecommendedFajr: "07:37", Zenith: "12:50", DhuhrCongregation: "13:15",
	})
	assertDay("2026-05-10", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-05-10", Fajr: "01:04", Sunrise: "04:49", Dhuhr: "12:52", Asr: "18:05", Maghrib: "20:43", Isha: "22:07", Flags: []string{"source_summer_calculation_start_isha"}},
		RecommendedFajr: "03:21", Zenith: "12:42", DhuhrCongregation: "13:15",
	})
	assertDay("2026-05-11", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-05-11", Fajr: "02:48", Sunrise: "04:47", Dhuhr: "12:52", Asr: "18:06", Maghrib: "20:45", Isha: "22:09", Flags: []string{"source_summer_calculation_start_fajr"}},
		RecommendedFajr: "03:19", Zenith: "12:42", DhuhrCongregation: "13:15",
	})
	assertDay("2026-08-02", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-08-02", Fajr: "02:58", Sunrise: "04:57", Dhuhr: "13:02", Asr: "18:15", Maghrib: "20:52", Isha: "23:15", Flags: []string{"source_summer_calculation_end_isha"}},
		RecommendedFajr: "03:29", Zenith: "12:52", DhuhrCongregation: "13:15",
	})
	assertDay("2026-08-03", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-08-03", Fajr: "01:10", Sunrise: "04:59", Dhuhr: "13:02", Asr: "18:14", Maghrib: "20:50", Isha: "23:11", Flags: []string{"source_summer_calculation_end_fajr"}},
		RecommendedFajr: "03:31", Zenith: "12:52", DhuhrCongregation: "13:15",
	})
	assertDay("2026-12-31", domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-12-31", Fajr: "06:52", Sunrise: "09:05", Dhuhr: "12:59", Asr: "14:46", Maghrib: "16:39", Isha: "18:26"},
		RecommendedFajr: "07:37", Zenith: "12:49", DhuhrCongregation: "13:15",
	})
	if day := dayByDate(t, candidate, "2026-01-01"); day.HijriDay != 0 || day.HijriMonth != "" || day.HijriYear != 0 {
		t.Fatalf("annual source invented absent Hijri values: %#v", day)
	}
	assertDiagnosticCode(t, candidate.Validation.Warnings, "source_marker_preserved")
	assertDiagnosticCode(t, candidate.Validation.Warnings, "mosque_iqamah_approval_required")
	assertDiagnosticCode(t, candidate.Validation.Warnings, "day_to_day_delta")
}

func TestPilotAnnualSourceRecordMatchesContractSchema(t *testing.T) {
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
	sourceDocument, err := jsonschema.UnmarshalJSON(bytes.NewReader(readFile(t, filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026", "source-record.json"))))
	if err != nil {
		t.Fatal(err)
	}
	if err := schema.Validate(sourceDocument); err != nil {
		t.Fatalf("source record schema validation: %v", err)
	}
}

func TestOfficialPDFParserFailsClosedOnRawHeaderAndSourceDrift(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	source, err := officialpdf.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	artifact := annualArtifact()
	transcription := readFile(t, filepath.Join(fixtureDir, "schedule.csv"))
	config := officialpdf.ParseConfig{Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: annualRawSHA256, DataClassification: domain.DataClassificationProduction}

	wrongHash := config
	wrongHash.ExpectedRawSHA256 = strings.Repeat("0", 64)
	if _, err := officialpdf.Parse(wrongHash, artifact, transcription); !officialpdf.IsErrorCode(err, "raw_checksum_mismatch") {
		t.Fatalf("wrong hash error = %v", err)
	}
	driftedHeader := bytes.Replace(transcription, []byte("recommended_fajr"), []byte("fajr_iqamah"), 1)
	if _, err := officialpdf.Parse(config, artifact, driftedHeader); !officialpdf.IsErrorCode(err, "schema_drift") {
		t.Fatalf("header drift error = %v", err)
	}
	badSource := config
	badSource.Source.Kind = domain.ProviderKindManualImport
	if _, err := officialpdf.Parse(badSource, artifact, transcription); !officialpdf.IsErrorCode(err, "unsupported_source") {
		t.Fatalf("source kind bypass error = %v", err)
	}
	badRetrieval := config
	badRetrieval.Source.Retrieval.Mode = "controlled_html_adapter"
	if _, err := officialpdf.Parse(badRetrieval, artifact, transcription); !officialpdf.IsErrorCode(err, "unsupported_source") {
		t.Fatalf("retrieval bypass error = %v", err)
	}
}

func TestOfficialPDFParserValidatesRecommendedFajrAndTransitionMarkers(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	source, err := officialpdf.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	artifact := annualArtifact()
	config := officialpdf.ParseConfig{Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: annualRawSHA256, DataClassification: domain.DataClassificationProduction}
	valid := readFile(t, filepath.Join(fixtureDir, "schedule.csv"))

	badRecommended := bytes.Replace(valid, []byte("2026-01-01,06:52,07:37"), []byte("2026-01-01,06:52,09:06"), 1)
	candidate, err := officialpdf.Parse(config, artifact, badRecommended)
	if err != nil {
		t.Fatal(err)
	}
	if candidate.Status != domain.CandidateValidationFailed {
		t.Fatalf("bad recommended Fajr status=%q validation=%#v", candidate.Status, candidate.Validation)
	}
	assertDiagnosticCode(t, candidate.Validation.Errors, "invalid_recommended_fajr_order")

	missingMarker := bytes.Replace(valid, []byte(",source_summer_calculation_start_isha\n"), []byte(",\n"), 1)
	if _, err := officialpdf.Parse(config, artifact, missingMarker); !officialpdf.IsErrorCode(err, "seasonal_rule_mismatch") {
		t.Fatalf("missing transition marker error = %v", err)
	}
}

func TestOfficialPDFProviderRejectsInvalidPDFMagic(t *testing.T) {
	t.Parallel()

	_, err := officialpdf.CaptureArtifact("calendar.pdf", "application/pdf", time.Unix(0, 0).UTC(), strings.NewReader("not-a-pdf"))
	if !officialpdf.IsErrorCode(err, "invalid_pdf") {
		t.Fatalf("CaptureArtifact() error = %v", err)
	}
}

func TestOfficialPDFCandidateFailsClosedOnGapDuplicateInvalidTimeAndScope(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	source, err := officialpdf.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	artifact := annualArtifact()
	valid := readFile(t, filepath.Join(fixtureDir, "schedule.csv"))
	tests := []struct {
		name   string
		mutate func([]byte, *officialpdf.ParseConfig) []byte
		code   string
	}{
		{name: "duplicate date", code: "duplicate_date", mutate: func(data []byte, _ *officialpdf.ParseConfig) []byte {
			return bytes.Replace(data, []byte("2026-01-02"), []byte("2026-01-01"), 1)
		}},
		{name: "date gap", code: "date_gap", mutate: func(data []byte, _ *officialpdf.ParseConfig) []byte {
			return bytes.Replace(data, []byte("2026-01-02"), []byte("2026-01-03"), 1)
		}},
		{name: "invalid time", code: "invalid_time", mutate: func(data []byte, _ *officialpdf.ParseConfig) []byte {
			return bytes.Replace(data, []byte("2026-01-01,06:52"), []byte("2026-01-01,6:52"), 1)
		}},
		{name: "country scope", code: "source_scope_mismatch", mutate: func(data []byte, config *officialpdf.ParseConfig) []byte {
			config.Mosque.CountryCode = "ZZ"
			return data
		}},
		{name: "timezone scope", code: "source_scope_mismatch", mutate: func(data []byte, config *officialpdf.ParseConfig) []byte {
			config.Mosque.Timezone = "Europe/Moscow"
			return data
		}},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			config := officialpdf.ParseConfig{Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: annualRawSHA256, DataClassification: domain.DataClassificationProduction}
			transcription := test.mutate(append([]byte(nil), valid...), &config)
			candidate, err := officialpdf.Parse(config, artifact, transcription)
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}
			if candidate.Status != domain.CandidateValidationFailed {
				t.Fatalf("status=%q validation=%#v", candidate.Status, candidate.Validation)
			}
			assertDiagnosticCode(t, candidate.Validation.Errors, test.code)
		})
	}
}

func TestOfficialPDFParserBindsPilotMosqueAndExactArtifactProvenance(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	source, err := officialpdf.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	transcription := readFile(t, filepath.Join(fixtureDir, "schedule.csv"))
	base := officialpdf.ParseConfig{Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: annualRawSHA256, DataClassification: domain.DataClassificationProduction}

	tests := []struct {
		name           string
		mutateConfig   func(*officialpdf.ParseConfig)
		mutateArtifact func(*domain.RawArtifact)
		code           string
	}{
		{name: "different mosque", code: "source_scope_mismatch", mutateConfig: func(config *officialpdf.ParseConfig) {
			config.Mosque.ID = "another-ulyanovsk-mosque"
		}},
		{name: "different locality", code: "source_scope_mismatch", mutateConfig: func(config *officialpdf.ParseConfig) {
			config.Mosque.Locality = "Ульяновск"
		}},
		{name: "wrong filename", code: "invalid_artifact", mutateArtifact: func(artifact *domain.RawArtifact) {
			artifact.Filename = "calendar-copy.pdf"
		}},
		{name: "wrong byte length", code: "invalid_artifact", mutateArtifact: func(artifact *domain.RawArtifact) {
			artifact.ByteLength--
		}},
		{name: "missing capture time", code: "invalid_artifact", mutateArtifact: func(artifact *domain.RawArtifact) {
			artifact.CapturedAt = ""
		}},
		{name: "invalid capture time", code: "invalid_artifact", mutateArtifact: func(artifact *domain.RawArtifact) {
			artifact.CapturedAt = "2026-08-20"
		}},
		{name: "configured hash is not pinned source", code: "raw_checksum_mismatch", mutateConfig: func(config *officialpdf.ParseConfig) {
			config.ExpectedRawSHA256 = strings.Repeat("0", 64)
		}, mutateArtifact: func(artifact *domain.RawArtifact) {
			artifact.SHA256 = strings.Repeat("0", 64)
		}},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			config := base
			artifact := annualArtifact()
			if test.mutateConfig != nil {
				test.mutateConfig(&config)
			}
			if test.mutateArtifact != nil {
				test.mutateArtifact(&artifact)
			}
			if _, err := officialpdf.Parse(config, artifact, transcription); !officialpdf.IsErrorCode(err, test.code) {
				t.Fatalf("Parse() error = %v, want code %q", err, test.code)
			}
		})
	}
}

func TestAugustReconciliationRecordsOnlyObservedSourceDisagreements(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	annual := parseAnnualFixture(t)
	monthly := parseMonthlyFixture(t, root)

	type mismatch struct{ date, field, monthly, annual string }
	var got []mismatch
	for _, monthlyDay := range monthly.Days {
		annualDay := dayByDate(t, annual, monthlyDay.Date)
		fields := []struct {
			name            string
			monthly, annual string
		}{
			{"fajr", monthlyDay.Fajr, annualDay.Fajr},
			{"sunrise", monthlyDay.Sunrise, annualDay.Sunrise},
			{"zenith", monthlyDay.Zenith, annualDay.Zenith},
			{"dhuhr", monthlyDay.Dhuhr, annualDay.Dhuhr},
			{"dhuhr_congregation", monthlyDay.DhuhrCongregation, annualDay.DhuhrCongregation},
			{"asr", monthlyDay.Asr, annualDay.Asr},
			{"maghrib", monthlyDay.Maghrib, annualDay.Maghrib},
			{"isha", monthlyDay.Isha, annualDay.Isha},
		}
		for _, field := range fields {
			if field.monthly != field.annual {
				got = append(got, mismatch{monthlyDay.Date, field.name, field.monthly, field.annual})
			}
		}
	}

	records, err := csv.NewReader(bytes.NewReader(readFile(t, filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026", "august-reconciliation.csv")))).ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	var want []mismatch
	for _, record := range records[1:] {
		if len(record) != 5 || record[4] != "resolved_monthly_photo_precedence" {
			t.Fatalf("invalid reconciliation row %#v", record)
		}
		want = append(want, mismatch{record[0], record[1], record[2], record[3]})
	}
	sort.Slice(got, func(i, j int) bool { return got[i].date+got[i].field < got[j].date+got[j].field })
	sort.Slice(want, func(i, j int) bool { return want[i].date+want[i].field < want[j].date+want[j].field })
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("source mismatches = %#v, want recorded %#v", got, want)
	}
	if len(got) != 12 {
		t.Fatalf("mismatch count = %d, want 12", len(got))
	}
}

func TestAnnualPilotCandidateCannotPublishWithoutNamedApproval(t *testing.T) {
	t.Parallel()

	candidate := parseAnnualFixture(t)
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	_, err = publication.Publish(publication.PublishRequest{
		Candidate: candidate, Diff: diff,
		SnapshotID: "ulyanovsk-2026-unapproved", GeneratedAt: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
		SigningKeyID: "must-not-be-used",
	}, nil)
	if !publication.IsErrorCode(err, "approval_required") {
		t.Fatalf("Publish() error = %v", err)
	}
}

func parseAnnualFixture(t *testing.T) domain.CandidateSchedule {
	t.Helper()
	root := repositoryRoot(t)
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	source, err := officialpdf.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	file, err := os.Open(filepath.Join(fixtureDir, "calendar_for_the_year_Ulyanovsk.pdf"))
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	artifact, err := officialpdf.CaptureArtifact("calendar_for_the_year_Ulyanovsk.pdf", "application/pdf", time.Date(2026, 8, 20, 9, 35, 29, 0, time.UTC), file)
	if err != nil {
		t.Fatal(err)
	}
	candidate, err := officialpdf.Parse(officialpdf.ParseConfig{
		Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: annualRawSHA256,
		DataClassification: domain.DataClassificationProduction,
	}, artifact, readFile(t, filepath.Join(fixtureDir, "schedule.csv")))
	if err != nil {
		t.Fatal(err)
	}
	return candidate
}

func parseMonthlyFixture(t *testing.T, root string) domain.CandidateSchedule {
	t.Helper()
	fixtureDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026-08")
	raw := readFile(t, filepath.Join(fixtureDir, "time-namaz.jpg"))
	artifact, err := manual.CaptureArtifact("time-namaz.jpg", "image/jpeg", time.Date(2026, 8, 19, 22, 53, 41, 0, time.UTC), bytes.NewReader(raw))
	if err != nil {
		t.Fatal(err)
	}
	source, err := manual.DecodeSourceRecord(readFile(t, filepath.Join(fixtureDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	candidate, err := manual.Parse(manual.ParseConfig{
		Mosque: pilotMosque(), Source: source, ExpectedRawSHA256: artifact.SHA256,
		DataClassification: domain.DataClassificationProduction,
	}, artifact, readFile(t, filepath.Join(fixtureDir, "schedule.csv")))
	if err != nil {
		t.Fatal(err)
	}
	return candidate
}

func pilotMosque() domain.Mosque {
	return domain.Mosque{
		ID: "second-cathedral-mosque-ulyanovsk", Name: "Вторая Соборная мечеть Ульяновска",
		CountryCode: "RU", Region: "Ульяновская область", Locality: "Ульяновск, ул. Дзержинского, 18А",
		Timezone: "Europe/Ulyanovsk",
	}
}

func annualArtifact() domain.RawArtifact {
	return domain.RawArtifact{
		Filename: "calendar_for_the_year_Ulyanovsk.pdf", ContentType: "application/pdf",
		CapturedAt: "2026-08-20T09:35:29Z", ByteLength: 35078839, SHA256: annualRawSHA256,
	}
}

func dayByDate(t *testing.T, candidate domain.CandidateSchedule, date string) domain.CandidatePrayerDay {
	t.Helper()
	for _, day := range candidate.Days {
		if day.Date == date {
			return day
		}
	}
	t.Fatalf("candidate does not contain %s", date)
	return domain.CandidatePrayerDay{}
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
