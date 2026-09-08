package domain

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"testing"
	"time"

	jsonschema "github.com/santhosh-tekuri/jsonschema/v6"
)

func TestQualifiedSnapshotHasVersionedPublicProofWithoutHumanApproval(t *testing.T) {
	snapshot := qualifiedSnapshot(t)
	if err := snapshot.Validate(); err != nil {
		t.Fatal(err)
	}
	encoded, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(encoded, []byte(`"approval"`)) || bytes.Contains(encoded, []byte(`"approved_by"`)) {
		t.Fatal("public snapshot fabricated a human approval branch")
	}
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(encoded))
	if err != nil {
		t.Fatal(err)
	}
	if err := compileSnapshotSchema(t).Validate(document); err != nil {
		t.Fatalf("v2 public snapshot does not match JSON Schema: %v", err)
	}
	decoded, err := DecodeSnapshot(encoded)
	if err != nil || decoded.Validate() != nil {
		t.Fatalf("v2 public proof did not round trip: %v", err)
	}
}

func TestQualifiedSnapshotRejectsBranchConfusionAndReboundValues(t *testing.T) {
	for _, tt := range []struct {
		name   string
		mutate func(*Snapshot)
	}{
		{"legacy version with public proof", func(s *Snapshot) { s.SchemaVersion = "1.0" }},
		{"public version without proof", func(s *Snapshot) { s.Source.Qualification = nil }},
		{"both proof branches", func(s *Snapshot) { s.Source.Approval = loadSyntheticSnapshot(t).Source.Approval }},
		{"unbound nonsample row", func(s *Snapshot) { s.PrayerDays[5].Fajr = "04:01" }},
		{"comparison contradicts signed rows", func(s *Snapshot) {
			s.Source.Qualification.Comparisons[0].Day.Fajr = "04:01"
			fingerprintQualification(t, s.Source.Qualification)
		}},
		{"wrong source", func(s *Snapshot) { s.Source.SourceID += "-other" }},
		{"different canonical source", func(s *Snapshot) { s.Source.CanonicalURL += "/other" }},
		{"different parser", func(s *Snapshot) { s.Source.ParserVersion += "+other" }},
		{"different geographic context", func(s *Snapshot) { s.Mosque.ID = "invented-mosque" }},
		{"different timezone", func(s *Snapshot) { s.Mosque.Timezone = "Asia/Omsk" }},
		{"generation before qualification", func(s *Snapshot) { s.GeneratedAt = "2026-09-08T09:00:00Z" }},
		{"publication after freshness", func(s *Snapshot) { s.GeneratedAt = "2026-10-01T00:00:00Z" }},
		{"regional onset as iqamah", func(s *Snapshot) { s.IqamahRules = loadSyntheticSnapshot(t).IqamahRules }},
		{"regional onset as Jumuah", func(s *Snapshot) { s.JumuahSessions = loadSyntheticSnapshot(t).JumuahSessions }},
	} {
		t.Run(tt.name, func(t *testing.T) {
			snapshot := qualifiedSnapshot(t)
			tt.mutate(&snapshot)
			if err := snapshot.Validate(); err == nil {
				t.Fatal("rebound public snapshot accepted")
			}
		})
	}
}

func qualifiedSnapshot(t *testing.T) Snapshot {
	t.Helper()
	q := syntheticQualification(t)
	snapshot := loadSyntheticSnapshot(t)
	snapshot.SchemaVersion, snapshot.GeneratedAt = "2.0", q.QualifiedAt
	scopeHash := sha256.Sum256([]byte(q.Scope.ID))
	snapshot.Mosque = Mosque{ID: "public-scope-" + hex.EncodeToString(scopeHash[:16]), Name: "Synthetic city", CountryCode: "RU", Timezone: q.Timezone}
	snapshot.Source = SourceMetadata{SourceID: q.SourceID, Kind: q.Kind, AuthorityName: q.Authority.Name, GeographicScope: q.Scope.Description, CanonicalURL: q.CanonicalURL, RetrievedAt: q.Artifact.CapturedAt, EffectiveFrom: q.Coverage.From, EffectiveTo: q.Coverage.To, RawSHA256: q.Artifact.SHA256, ParserVersion: q.ParserVersion}
	snapshot.Coverage, snapshot.PrayerDays = q.Coverage, nil
	snapshot.IqamahRules, snapshot.IqamahOverrides, snapshot.JumuahSessions, snapshot.Campaigns, snapshot.Theme = nil, nil, nil, nil, nil
	for date := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC); date.Month() == 9; date = date.AddDate(0, 0, 1) {
		day := q.Comparisons[0].Day
		day.Date = date.Format(time.DateOnly)
		snapshot.PrayerDays = append(snapshot.PrayerDays, day)
	}
	var err error
	q.OnsetSHA256, err = PrayerDaysSHA256(snapshot.PrayerDays)
	if err != nil {
		t.Fatal(err)
	}
	fingerprintQualification(t, &q)
	snapshot.Source.Qualification = &q
	return snapshot
}
