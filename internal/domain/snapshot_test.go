package domain

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"sort"
	"testing"

	jsonschema "github.com/santhosh-tekuri/jsonschema/v6"
)

func TestSyntheticSnapshotIsValid(t *testing.T) {
	t.Parallel()

	snapshot := loadSyntheticSnapshot(t)
	if err := snapshot.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}

	if snapshot.Mosque.Timezone != "Europe/Ulyanovsk" {
		t.Fatalf("Mosque.Timezone = %q", snapshot.Mosque.Timezone)
	}
	if snapshot.Source.Kind != ProviderKindManualImport {
		t.Fatalf("Source.Kind = %q", snapshot.Source.Kind)
	}
	if got := len(snapshot.PrayerDays); got != 3 {
		t.Fatalf("len(PrayerDays) = %d, want 3", got)
	}
}

func TestSyntheticSnapshotMatchesJSONSchemaAndDomain(t *testing.T) {
	t.Parallel()

	schema := compileSnapshotSchema(t)
	data := readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json")
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("parse synthetic snapshot for schema validation: %v", err)
	}
	if err := schema.Validate(document); err != nil {
		t.Fatalf("JSON Schema validation error = %v", err)
	}

	snapshot, err := DecodeSnapshot(data)
	if err != nil {
		t.Fatalf("DecodeSnapshot() error = %v", err)
	}
	if err := snapshot.Validate(); err != nil {
		t.Fatalf("domain validation error = %v", err)
	}
}

func TestDecodeSnapshotRejectsMalformedUTF8(t *testing.T) {
	t.Parallel()

	data := readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json")
	data = bytes.Replace(data, []byte("Synthetic"), []byte{'S', 0xff}, 1)
	if _, err := DecodeSnapshot(data); err == nil {
		t.Fatal("DecodeSnapshot() error = nil for malformed UTF-8")
	}
}

func TestDecodeSnapshotRejectsDuplicateObjectMembers(t *testing.T) {
	t.Parallel()

	data := readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json")
	data = bytes.Replace(
		data,
		[]byte(`"snapshot_id": "synthetic-ulsk-demo-2026-08-v1",`),
		[]byte(`"snapshot_id": "attacker-selected-snapshot", "snapshot_id": "synthetic-ulsk-demo-2026-08-v1",`),
		1,
	)
	if _, err := DecodeSnapshot(data); err == nil {
		t.Fatal("DecodeSnapshot() accepted duplicate object members")
	}
}

func TestDecodeSnapshotRejectsSchemaOnlyShapeDrift(t *testing.T) {
	t.Parallel()

	valid := readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json")
	tests := []struct {
		name string
		data []byte
	}{
		{
			name: "required numeric property omitted",
			data: bytes.Replace(valid, []byte(`"priority": 100,`), nil, 1),
		},
		{
			name: "explicit null",
			data: bytes.Replace(valid, []byte(`"subtitle": "Только для проверки интерфейса"`), []byte(`"subtitle": null`), 1),
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			if bytes.Equal(test.data, valid) {
				t.Fatal("test mutation did not apply")
			}
			if _, err := DecodeSnapshot(test.data); err == nil {
				t.Fatal("DecodeSnapshot() error = nil for schema-only drift")
			}
		})
	}
}

func TestSnapshotValidationRejectsMalformedOptionalSections(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		mutate   func(*Snapshot)
		wantPath string
		wantCode string
	}{
		{
			name: "iqamah rule",
			mutate: func(snapshot *Snapshot) {
				snapshot.IqamahRules = []IqamahRule{{
					ID: "rule", Prayer: "invalid", ValidFrom: "2026-08-20", ValidTo: "2026-08-20",
					Weekdays: []int{1}, Value: IqamahValue{Mode: "fixed_time", FixedTime: "13:00"},
				}}
			},
			wantPath: "iqamah_rules[0].prayer",
			wantCode: "unsupported_value",
		},
		{
			name: "iqamah override",
			mutate: func(snapshot *Snapshot) {
				snapshot.IqamahOverrides = []IqamahOverride{{
					Date: "2026-08-20", Prayer: "dhuhr",
					Value: IqamahValue{Mode: "offset_after_adhan", OffsetMinutes: intPointer(241)},
				}}
			},
			wantPath: "iqamah_date_overrides[0].value.offset_minutes",
			wantCode: "out_of_range",
		},
		{
			name: "jumuah",
			mutate: func(snapshot *Snapshot) {
				snapshot.JumuahSessions = []JumuahSession{{
					ID: "j1", Label: "Friday", SalahTime: "25:00",
					ValidFrom: "2026-08-20", ValidTo: "2026-08-20",
				}}
			},
			wantPath: "jumuah_sessions[0].salah_time",
			wantCode: "invalid_time",
		},
		{
			name: "campaign",
			mutate: func(snapshot *Snapshot) {
				snapshot.Campaigns = []Campaign{{
					ID: "c1", Kind: "website", URL: "http://example.org", Title: "Website",
					StartsAt: "2026-08-20T00:00:00Z", EndsAt: "2026-08-21T00:00:00Z", Placement: "always",
				}}
			},
			wantPath: "campaigns[0].url",
			wantCode: "invalid_https_url",
		},
		{
			name: "campaign URL userinfo",
			mutate: func(snapshot *Snapshot) {
				snapshot.Campaigns[0].URL = "https://trusted.example@attacker.example/donate"
			},
			wantPath: "campaigns[0].url",
			wantCode: "invalid_https_url",
		},
		{
			name: "campaign lowercase RFC3339 range",
			mutate: func(snapshot *Snapshot) {
				snapshot.Campaigns = []Campaign{{
					ID: "c1", Kind: "website", URL: "https://example.org", Title: "Website",
					StartsAt: "2026-08-21t00:00:00z", EndsAt: "2026-08-20t00:00:00z", Placement: "always",
				}}
			},
			wantPath: "campaigns[0]",
			wantCode: "invalid_range",
		},
		{
			name: "theme asset",
			mutate: func(snapshot *Snapshot) {
				snapshot.Theme = &Theme{
					ThemeID: "theme", OverlayOpacity: 0.5,
					LandscapeAsset: &AssetReference{AssetID: "asset", SHA256: "bad", MediaType: "image/png", ByteLength: 1, Width: 320, Height: 180},
				}
			},
			wantPath: "theme.landscape_asset.sha256",
			wantCode: "invalid_sha256",
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
			test.mutate(&snapshot)
			assertValidationError(t, snapshot, test.wantPath, test.wantCode)
		})
	}
}

func TestSnapshotCollectionBoundsMatchSchema(t *testing.T) {
	t.Parallel()

	snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
	snapshot.PrayerDays[0].Flags = make([]string, 33)
	for index := range snapshot.PrayerDays[0].Flags {
		snapshot.PrayerDays[0].Flags[index] = fmt.Sprintf("flag-%02d", index)
	}
	data, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatalf("marshal snapshot: %v", err)
	}
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("parse snapshot: %v", err)
	}
	if err := compileSnapshotSchema(t).Validate(document); err == nil {
		t.Fatal("JSON Schema validation error = nil for oversized flags")
	}
	assertValidationError(t, snapshot, "prayer_days[0].flags", "too_many_items")
}

func intPointer(value int) *int { return &value }

func TestSnapshotValidationRejectsInvalidSchedules(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		mutate   func(*Snapshot)
		wantPath string
		wantCode string
	}{
		{
			name: "gap",
			mutate: func(snapshot *Snapshot) {
				snapshot.PrayerDays = append(snapshot.PrayerDays[:1], snapshot.PrayerDays[2:]...)
			},
			wantPath: "prayer_days[1].date",
			wantCode: "date_gap",
		},
		{
			name: "duplicate date",
			mutate: func(snapshot *Snapshot) {
				snapshot.PrayerDays[1].Date = snapshot.PrayerDays[0].Date
			},
			wantPath: "prayer_days[1].date",
			wantCode: "duplicate_date",
		},
		{
			name: "invalid time",
			mutate: func(snapshot *Snapshot) {
				snapshot.PrayerDays[0].Fajr = "3:12"
			},
			wantPath: "prayer_days[0].fajr",
			wantCode: "invalid_time",
		},
		{
			name: "invalid timezone",
			mutate: func(snapshot *Snapshot) {
				snapshot.Mosque.Timezone = "Mars/Olympus_Mons"
			},
			wantPath: "mosque.timezone",
			wantCode: "invalid_timezone",
		},
		{
			name: "device local timezone",
			mutate: func(snapshot *Snapshot) {
				snapshot.Mosque.Timezone = "Local"
			},
			wantPath: "mosque.timezone",
			wantCode: "invalid_timezone",
		},
		{
			name: "missing provenance",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.ParserVersion = ""
			},
			wantPath: "source.parser_version",
			wantCode: "required",
		},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
			test.mutate(&snapshot)
			assertValidationError(t, snapshot, test.wantPath, test.wantCode)
		})
	}
}

func TestSnapshotValidationRejectsInvalidMetadata(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		mutate   func(*Snapshot)
		wantPath string
		wantCode string
	}{
		{
			name: "unsupported provider kind",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.Kind = "silent_fallback"
			},
			wantPath: "source.kind",
			wantCode: "unsupported_value",
		},
		{
			name: "coverage outside source effective range",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.EffectiveFrom = "2026-08-20"
			},
			wantPath: "coverage",
			wantCode: "outside_source_effective_range",
		},
		{
			name: "calculation kind without profile reference",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.Kind = ProviderKindCalculationProfile
				snapshot.Source.CalculationProfile = ""
			},
			wantPath: "source.calculation_profile",
			wantCode: "required_for_kind",
		},
		{
			name: "invalid raw hash",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.RawSHA256 = "ABC"
			},
			wantPath: "source.raw_sha256",
			wantCode: "invalid_sha256",
		},
		{
			name: "missing approval id",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.Approval.ID = ""
			},
			wantPath: "source.approval.approval_id",
			wantCode: "required",
		},
		{
			name: "missing approval scope",
			mutate: func(snapshot *Snapshot) {
				snapshot.Source.Approval.Scope = ""
			},
			wantPath: "source.approval.approval_scope",
			wantCode: "required",
		},
		{
			name: "invalid integrity hash",
			mutate: func(snapshot *Snapshot) {
				snapshot.Integrity.CanonicalSHA256 = "ABC"
			},
			wantPath: "integrity.canonical_sha256",
			wantCode: "invalid_sha256",
		},
		{
			name: "invalid signature encoding",
			mutate: func(snapshot *Snapshot) {
				snapshot.Integrity.SignatureEd25519Base64 = "not-base64"
			},
			wantPath: "integrity.signature_ed25519_base64",
			wantCode: "invalid_signature_encoding",
		},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
			test.mutate(&snapshot)
			assertValidationError(t, snapshot, test.wantPath, test.wantCode)
		})
	}
}

func TestInvalidSnapshotFixturesFailDeterministically(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name             string
		filename         string
		schemaMustReject bool
		wantPath         string
		wantCode         string
	}{
		{name: "gap", filename: "gap.json", wantPath: "prayer_days[1].date", wantCode: "date_gap"},
		{name: "duplicate date", filename: "duplicate-date.json", wantPath: "prayer_days[1].date", wantCode: "duplicate_date"},
		{name: "invalid time", filename: "invalid-time.json", schemaMustReject: true, wantPath: "prayer_days[0].fajr", wantCode: "invalid_time"},
		{name: "invalid timezone", filename: "invalid-timezone.json", wantPath: "mosque.timezone", wantCode: "invalid_timezone"},
		{name: "missing provenance", filename: "missing-provenance.json", schemaMustReject: true, wantPath: "source.parser_version", wantCode: "required"},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			data := readRepositoryFile(t, "internal", "domain", "testdata", test.filename)
			document, err := jsonschema.UnmarshalJSON(bytes.NewReader(data))
			if err != nil {
				t.Fatalf("parse fixture: %v", err)
			}
			schemaErr := compileSnapshotSchema(t).Validate(document)
			if test.schemaMustReject && schemaErr == nil {
				t.Fatal("JSON Schema validation error = nil")
			}
			if !test.schemaMustReject && schemaErr != nil {
				t.Fatalf("JSON Schema validation error = %v", schemaErr)
			}

			snapshot, err := DecodeSnapshot(data)
			if err != nil {
				t.Fatalf("DecodeSnapshot() error = %v", err)
			}
			assertValidationError(t, snapshot, test.wantPath, test.wantCode)
		})
	}
}

func TestProviderKindsMatchJSONSchemas(t *testing.T) {
	t.Parallel()

	want := []string{
		string(ProviderKindOfficialAPI),
		string(ProviderKindOfficialFile),
		string(ProviderKindOfficialHTML),
		string(ProviderKindMosqueCalendar),
		string(ProviderKindCalculationProfile),
		string(ProviderKindManualImport),
	}
	sort.Strings(want)

	tests := []struct {
		name string
		path []string
	}{
		{name: "snapshot", path: []string{"$defs", "source", "properties", "kind", "enum"}},
		{name: "source record", path: []string{"properties", "kind", "enum"}},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			filename := "prayer-snapshot.schema.json"
			if test.name == "source record" {
				filename = "source-record.schema.json"
			}
			var document map[string]any
			if err := json.Unmarshal(readRepositoryFile(t, "contracts", filename), &document); err != nil {
				t.Fatalf("decode schema: %v", err)
			}
			got := enumAtPath(t, document, test.path...)
			sort.Strings(got)
			if !reflect.DeepEqual(got, want) {
				t.Fatalf("provider kinds = %v, want %v", got, want)
			}
		})
	}
}

func TestDomainAcceptsJSONSchemaDateTimeVariants(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		value string
	}{
		{name: "lowercase separators", value: "2026-08-19t10:00:00z"},
		{name: "leap second", value: "2026-12-31T23:59:60Z"},
		{name: "leap second with offset", value: "2027-01-01T02:59:60+03:00"},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
			snapshot.GeneratedAt = test.value
			data, err := json.Marshal(snapshot)
			if err != nil {
				t.Fatalf("marshal snapshot: %v", err)
			}
			document, err := jsonschema.UnmarshalJSON(bytes.NewReader(data))
			if err != nil {
				t.Fatalf("parse snapshot: %v", err)
			}
			if err := compileSnapshotSchema(t).Validate(document); err != nil {
				t.Fatalf("JSON Schema rejected date-time %q: %v", test.value, err)
			}
			if err := snapshot.Validate(); err != nil {
				t.Fatalf("domain rejected schema-valid date-time %q: %v", test.value, err)
			}
		})
	}
}

func TestDomainRejectsJSONSchemaInvalidLeapSecond(t *testing.T) {
	t.Parallel()

	snapshot := cloneSnapshot(t, loadSyntheticSnapshot(t))
	snapshot.GeneratedAt = "2026-12-31T12:00:60Z"
	data, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatalf("marshal snapshot: %v", err)
	}
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("parse snapshot: %v", err)
	}
	if err := compileSnapshotSchema(t).Validate(document); err == nil {
		t.Fatal("JSON Schema validation error = nil")
	}
	assertValidationError(t, snapshot, "generated_at", "invalid_datetime")
}

func TestConditionalProvenanceRejectedBySchemaAndDomain(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		mutate   func(map[string]any)
		wantPath string
		wantCode string
	}{
		{
			name: "missing approval id",
			mutate: func(document map[string]any) {
				delete(nestedObject(t, document, "source", "approval"), "approval_id")
			},
			wantPath: "source.approval.approval_id",
			wantCode: "required",
		},
		{
			name: "missing approval scope",
			mutate: func(document map[string]any) {
				delete(nestedObject(t, document, "source", "approval"), "approval_scope")
			},
			wantPath: "source.approval.approval_scope",
			wantCode: "required",
		},
		{
			name: "calculation kind without profile reference",
			mutate: func(document map[string]any) {
				source := nestedObject(t, document, "source")
				source["kind"] = string(ProviderKindCalculationProfile)
				delete(source, "calculation_profile")
			},
			wantPath: "source.calculation_profile",
			wantCode: "required_for_kind",
		},
	}

	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			var document map[string]any
			if err := json.Unmarshal(readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json"), &document); err != nil {
				t.Fatalf("decode synthetic snapshot: %v", err)
			}
			test.mutate(document)
			if err := compileSnapshotSchema(t).Validate(document); err == nil {
				t.Fatal("JSON Schema validation error = nil")
			}
			data, err := json.Marshal(document)
			if err != nil {
				t.Fatalf("marshal mutated snapshot: %v", err)
			}
			snapshot, err := DecodeSnapshot(data)
			if err != nil {
				t.Fatalf("DecodeSnapshot() error = %v", err)
			}
			assertValidationError(t, snapshot, test.wantPath, test.wantCode)
		})
	}
}

func loadSyntheticSnapshot(t *testing.T) Snapshot {
	t.Helper()

	data := readRepositoryFile(t, "examples", "synthetic-prayer-snapshot.json")

	snapshot, err := DecodeSnapshot(data)
	if err != nil {
		t.Fatalf("DecodeSnapshot() error = %v", err)
	}
	return snapshot
}

func compileSnapshotSchema(t *testing.T) *jsonschema.Schema {
	t.Helper()

	schemaData := readRepositoryFile(t, "contracts", "prayer-snapshot.schema.json")
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(schemaData))
	if err != nil {
		t.Fatalf("parse snapshot schema: %v", err)
	}
	compiler := jsonschema.NewCompiler()
	compiler.AssertFormat()
	const schemaURL = "https://example.invalid/schemas/prayer-snapshot.schema.json"
	if err := compiler.AddResource(schemaURL, document); err != nil {
		t.Fatalf("add snapshot schema: %v", err)
	}
	schema, err := compiler.Compile(schemaURL)
	if err != nil {
		t.Fatalf("compile snapshot schema: %v", err)
	}
	return schema
}

func readRepositoryFile(t *testing.T, pathParts ...string) []byte {
	t.Helper()

	_, filename, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("locate test source")
	}
	root := filepath.Join(filepath.Dir(filename), "..", "..")
	data, err := os.ReadFile(filepath.Join(append([]string{root}, pathParts...)...))
	if err != nil {
		t.Fatalf("read repository file: %v", err)
	}
	return data
}

func cloneSnapshot(t *testing.T, snapshot Snapshot) Snapshot {
	t.Helper()

	data, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatalf("marshal snapshot clone: %v", err)
	}
	clone, err := DecodeSnapshot(data)
	if err != nil {
		t.Fatalf("decode snapshot clone: %v", err)
	}
	return clone
}

func assertValidationError(t *testing.T, snapshot Snapshot, wantPath, wantCode string) {
	t.Helper()

	err := snapshot.Validate()
	if err == nil {
		t.Fatal("Validate() error = nil")
	}

	var validationErrors *ValidationErrors
	if !errors.As(err, &validationErrors) {
		t.Fatalf("Validate() error type = %T, want *ValidationErrors", err)
	}
	if len(validationErrors.Items) == 0 {
		t.Fatal("Validate() returned empty ValidationErrors")
	}
	first := validationErrors.Items[0]
	if first.Path != wantPath || first.Code != wantCode {
		t.Fatalf("first validation error = %s/%s, want %s/%s (all: %v)", first.Path, first.Code, wantPath, wantCode, err)
	}
}

func enumAtPath(t *testing.T, document map[string]any, path ...string) []string {
	t.Helper()

	var current any = document
	for _, segment := range path {
		object, ok := current.(map[string]any)
		if !ok {
			t.Fatalf("schema path %q is not an object", segment)
		}
		current, ok = object[segment]
		if !ok {
			t.Fatalf("schema path %q is missing", segment)
		}
	}
	values, ok := current.([]any)
	if !ok {
		t.Fatal("schema enum is not an array")
	}
	result := make([]string, len(values))
	for index, value := range values {
		text, ok := value.(string)
		if !ok {
			t.Fatalf("schema enum item %d is not a string", index)
		}
		result[index] = text
	}
	return result
}

func nestedObject(t *testing.T, document map[string]any, path ...string) map[string]any {
	t.Helper()

	current := document
	for _, segment := range path {
		next, ok := current[segment].(map[string]any)
		if !ok {
			t.Fatalf("document path %q is not an object", segment)
		}
		current = next
	}
	return current
}
