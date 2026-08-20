package effective_test

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/effective"
	"github.com/andy-ahmedov/namaz-time/internal/providers/manual"
	"github.com/andy-ahmedov/namaz-time/internal/providers/officialpdf"
)

const (
	annualRawSHA  = "82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21"
	monthlyRawSHA = "11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c"
)

func TestComposeAppliesBoundedMonthlyPrecedenceWithCompleteProvenance(t *testing.T) {
	t.Parallel()

	annual, monthly, policy, artifact := inputs(t)
	candidate, err := effective.Compose(effective.ComposeRequest{
		Policy: policy, PolicyBytes: policyBytes(t), PolicyArtifact: artifact, Baseline: annual,
		Overrides: []domain.CandidateSchedule{monthly},
	})
	if err != nil {
		t.Fatal(err)
	}

	if candidate.Status != domain.CandidateNeedsReview || len(candidate.Validation.Errors) != 0 {
		t.Fatalf("status=%q validation=%#v", candidate.Status, candidate.Validation)
	}
	if candidate.Coverage != (domain.DateRange{From: "2026-01-01", To: "2026-12-31"}) || len(candidate.Days) != 365 {
		t.Fatalf("coverage=%#v days=%d", candidate.Coverage, len(candidate.Days))
	}
	if candidate.Artifact.SHA256 != artifact.SHA256 || candidate.Artifact.Filename != "effective-policy.json" {
		t.Fatalf("policy artifact=%#v", candidate.Artifact)
	}
	if candidate.ParserVersion != effective.ParserVersion || candidate.Source.SourceID != "effective-ulyanovsk-2026-v1" {
		t.Fatalf("parser=%q source=%#v", candidate.ParserVersion, candidate.Source)
	}
	if len(candidate.Components) != 2 {
		t.Fatalf("components=%#v", candidate.Components)
	}
	if candidate.Components[0].Role != "baseline" || candidate.Components[0].RawArtifact.SHA256 != annualRawSHA ||
		candidate.Components[1].Role != "override" || candidate.Components[1].RawArtifact.SHA256 != monthlyRawSHA {
		t.Fatalf("component provenance=%#v", candidate.Components)
	}

	for _, monthlyDay := range monthly.Days {
		got := day(t, candidate, monthlyDay.Date)
		annualDay := day(t, annual, monthlyDay.Date)
		monthlyPrayerDay := monthlyDay.PrayerDay
		monthlyPrayerDay.Flags = append([]string{}, monthlyPrayerDay.Flags...)
		for index, flag := range monthlyPrayerDay.Flags {
			if replacement, exists := policy.Overrides[0].ResolvedFlagRewrites[flag]; exists {
				monthlyPrayerDay.Flags[index] = replacement
			}
		}
		if !reflect.DeepEqual(got.PrayerDay, monthlyPrayerDay) || got.Zenith != monthlyDay.Zenith ||
			got.DhuhrCongregation != monthlyDay.DhuhrCongregation || got.HijriDay != monthlyDay.HijriDay ||
			got.HijriMonth != monthlyDay.HijriMonth || got.HijriYear != monthlyDay.HijriYear {
			t.Fatalf("effective %s=%#v, monthly=%#v", monthlyDay.Date, got, monthlyDay)
		}
		if got.RecommendedFajr != annualDay.RecommendedFajr {
			t.Fatalf("effective %s recommended_fajr=%q, annual=%q", monthlyDay.Date, got.RecommendedFajr, annualDay.RecommendedFajr)
		}
	}
	if got, want := day(t, candidate, "2026-07-31"), day(t, annual, "2026-07-31"); !reflect.DeepEqual(got, want) {
		t.Fatalf("July baseline changed: got=%#v want=%#v", got, want)
	}
	if got, want := day(t, candidate, "2026-09-01"), day(t, annual, "2026-09-01"); !reflect.DeepEqual(got, want) {
		t.Fatalf("September baseline changed: got=%#v want=%#v", got, want)
	}

	august24 := day(t, candidate, "2026-08-24")
	if august24.Dhuhr != "12:48" || august24.DhuhrCongregation != "13:53" {
		t.Fatalf("August 24 precedence=%#v", august24)
	}
	august31 := day(t, candidate, "2026-08-31")
	if strings.Contains(strings.Join(august24.Flags, ","), "requires_review") ||
		strings.Contains(strings.Join(august31.Flags, ","), "requires_review") {
		t.Fatalf("resolved source-policy flags leaked: Aug24=%v Aug31=%v", august24.Flags, august31.Flags)
	}
	if !contains(august24.Flags, "source_collective_outlier_preserved_by_monthly_precedence") ||
		!contains(august31.Flags, "source_dhuhr_value_preserved_by_monthly_precedence") {
		t.Fatalf("resolved source-policy evidence missing: Aug24=%v Aug31=%v", august24.Flags, august31.Flags)
	}
	if !diagnostic(candidate.Validation.Warnings, "mosque_iqamah_approval_required") {
		t.Fatalf("D-009 warning missing: %#v", candidate.Validation.Warnings)
	}
}

func TestComposeFailsClosedWhenPolicyBindingOrRangeChanges(t *testing.T) {
	t.Parallel()

	annual, monthly, _, artifact := inputs(t)
	tests := []struct {
		name   string
		mutate func(*effective.ComposeRequest)
		code   string
	}{
		{"baseline raw hash", func(request *effective.ComposeRequest) { request.Baseline.Artifact.SHA256 = strings.Repeat("0", 64) }, "component_binding_mismatch"},
		{"override candidate", func(request *effective.ComposeRequest) { request.Overrides[0].ID = "candidate-wrong" }, "component_binding_mismatch"},
		{"override range gap", func(request *effective.ComposeRequest) { request.Policy.Overrides[0].EffectiveFrom = "2026-08-02" }, "override_coverage_mismatch"},
		{"unknown applied field", func(request *effective.ComposeRequest) {
			request.Policy.Overrides[0].AppliedFields = append(request.Policy.Overrides[0].AppliedFields, "unknown")
		}, "invalid_policy"},
		{"unresolved flag rewrite", func(request *effective.ComposeRequest) {
			delete(request.Policy.Overrides[0].ResolvedFlagRewrites, "requires_review_collective_outlier")
		}, "unresolved_review_flag"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			freshPolicy, err := effective.DecodePolicy(policyBytes(t))
			if err != nil {
				t.Fatal(err)
			}
			request := effective.ComposeRequest{
				Policy: freshPolicy, PolicyBytes: policyBytes(t), PolicyArtifact: artifact, Baseline: annual,
				Overrides: []domain.CandidateSchedule{monthly},
			}
			test.mutate(&request)
			bindPolicy(t, &request)
			if _, err := effective.Compose(request); !effective.IsErrorCode(err, test.code) {
				t.Fatalf("Compose() error=%v, want %q", err, test.code)
			}
		})
	}
}

func bindPolicy(t *testing.T, request *effective.ComposeRequest) {
	t.Helper()
	data, err := json.MarshalIndent(request.Policy, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	data = append(data, '\n')
	artifact, err := effective.CapturePolicyArtifact(
		"effective-policy.json", time.Date(2026, 8, 20, 11, 31, 33, 0, time.UTC), bytes.NewReader(data),
	)
	if err != nil {
		t.Fatal(err)
	}
	request.PolicyBytes = data
	request.PolicyArtifact = artifact
}

func TestPolicyDecodeIsStrictAndCaptureBindsExactBytes(t *testing.T) {
	t.Parallel()

	root := repositoryRoot(t)
	data := read(t, filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026", "effective-policy.json"))
	policy, err := effective.DecodePolicy(data)
	if err != nil {
		t.Fatal(err)
	}
	if policy.CompositionID != "ulyanovsk-2026-pdf-with-august-photo-v1" || len(policy.Overrides) != 1 {
		t.Fatalf("policy=%#v", policy)
	}
	artifact, err := effective.CapturePolicyArtifact(
		"effective-policy.json", time.Date(2026, 8, 20, 11, 31, 33, 0, time.UTC), bytes.NewReader(data),
	)
	if err != nil {
		t.Fatal(err)
	}
	if artifact.ByteLength != int64(len(data)) || len(artifact.SHA256) != 64 || artifact.ContentType != "application/json" {
		t.Fatalf("artifact=%#v", artifact)
	}
	unknown := bytes.Replace(data, []byte(`"reason":`), []byte(`"unexpected":true,"reason":`), 1)
	if _, err := effective.DecodePolicy(unknown); !effective.IsErrorCode(err, "schema_drift") {
		t.Fatalf("DecodePolicy() error=%v", err)
	}
}

func inputs(t *testing.T) (domain.CandidateSchedule, domain.CandidateSchedule, effective.Policy, domain.RawArtifact) {
	t.Helper()
	root := repositoryRoot(t)
	annualDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026")
	monthlyDir := filepath.Join(root, "fixtures", "pilot", "ulyanovsk-2026-08")

	annualSource, err := officialpdf.DecodeSourceRecord(read(t, filepath.Join(annualDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	annualFile, err := os.Open(filepath.Join(annualDir, "calendar_for_the_year_Ulyanovsk.pdf"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = annualFile.Close() })
	annualArtifact, err := officialpdf.CaptureArtifact(
		"calendar_for_the_year_Ulyanovsk.pdf", "application/pdf",
		time.Date(2026, 8, 20, 9, 35, 29, 0, time.UTC), annualFile,
	)
	if err != nil {
		t.Fatal(err)
	}
	annual, err := officialpdf.Parse(officialpdf.ParseConfig{
		Mosque: pilotMosque(), Source: annualSource, ExpectedRawSHA256: annualRawSHA,
		DataClassification: domain.DataClassificationProduction,
	}, annualArtifact, read(t, filepath.Join(annualDir, "schedule.csv")))
	if err != nil {
		t.Fatal(err)
	}

	monthlySource, err := manual.DecodeSourceRecord(read(t, filepath.Join(monthlyDir, "source-record.json")))
	if err != nil {
		t.Fatal(err)
	}
	monthlyBytes := read(t, filepath.Join(monthlyDir, "time-namaz.jpg"))
	monthlyArtifact, err := manual.CaptureArtifact(
		"time-namaz.jpg", "image/jpeg", time.Date(2026, 8, 19, 22, 53, 41, 0, time.UTC), bytes.NewReader(monthlyBytes),
	)
	if err != nil {
		t.Fatal(err)
	}
	monthly, err := manual.Parse(manual.ParseConfig{
		Mosque: pilotMosque(), Source: monthlySource, ExpectedRawSHA256: monthlyRawSHA,
		DataClassification: domain.DataClassificationProduction,
	}, monthlyArtifact, read(t, filepath.Join(monthlyDir, "schedule.csv")))
	if err != nil {
		t.Fatal(err)
	}

	policyBytes := read(t, filepath.Join(annualDir, "effective-policy.json"))
	policy, err := effective.DecodePolicy(policyBytes)
	if err != nil {
		t.Fatal(err)
	}
	policyArtifact, err := effective.CapturePolicyArtifact(
		"effective-policy.json", time.Date(2026, 8, 20, 11, 31, 33, 0, time.UTC), bytes.NewReader(policyBytes),
	)
	if err != nil {
		t.Fatal(err)
	}
	return annual, monthly, policy, policyArtifact
}

func policyBytes(t *testing.T) []byte {
	t.Helper()
	return read(t, filepath.Join(repositoryRoot(t), "fixtures", "pilot", "ulyanovsk-2026", "effective-policy.json"))
}

func pilotMosque() domain.Mosque {
	return domain.Mosque{
		ID: "second-cathedral-mosque-ulyanovsk", Name: "Вторая Соборная мечеть Ульяновска",
		CountryCode: "RU", Region: "Ульяновская область", Locality: "Ульяновск, ул. Дзержинского, 18А",
		Timezone: "Europe/Ulyanovsk",
	}
}

func day(t *testing.T, candidate domain.CandidateSchedule, date string) domain.CandidatePrayerDay {
	t.Helper()
	for _, value := range candidate.Days {
		if value.Date == date {
			return value
		}
	}
	t.Fatalf("missing day %s", date)
	return domain.CandidatePrayerDay{}
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

func diagnostic(values []domain.CandidateDiagnostic, code string) bool {
	for _, value := range values {
		if value.Code == code {
			return true
		}
	}
	return false
}

func repositoryRoot(t *testing.T) string {
	t.Helper()
	workingDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	return filepath.Clean(filepath.Join(workingDirectory, "..", "..", ".."))
}

func read(t *testing.T, path string) []byte {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return data
}
