package domain

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestSourceQualificationBindsPublicEvidenceWithoutHumanApproval(t *testing.T) {
	q := syntheticQualification(t)
	if err := q.Validate(); err != nil {
		t.Fatal(err)
	}
	encoded, err := json.Marshal(q)
	if err != nil {
		t.Fatal(err)
	}
	for _, fabricated := range []string{"approved_by", "approval_id", "partnership", "external_endorsement"} {
		if strings.Contains(string(encoded), fabricated) {
			t.Fatalf("qualification must not fabricate %s", fabricated)
		}
	}
	repeated, err := SourceQualificationSHA256(q)
	if err != nil || repeated != q.SHA256 {
		t.Fatalf("fingerprint is not stable: %s, %v", repeated, err)
	}
}

func TestSourceQualificationRejectsMissingProofAndTampering(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*SourceQualification)
		rehash bool
	}{
		{"bare status is not evidence", func(q *SourceQualification) { q.Evidence = nil }, true},
		{"unknown owner", func(q *SourceQualification) { q.Authority.EvidenceLabel = "INFERENCE" }, true},
		{"missing owner claim", func(q *SourceQualification) { q.Evidence = q.Evidence[1:] }, true},
		{"missing scope", func(q *SourceQualification) { q.Scope = GeographicScope{} }, true},
		{"city expanded to region", func(q *SourceQualification) { q.Scope.Kind = GeographicScopeRegion }, true},
		{"region without binding", func(q *SourceQualification) { q.Scope.RegionID = "" }, true},
		{"missing catalog", func(q *SourceQualification) { q.CatalogRevision = "" }, true},
		{"generic method", func(q *SourceQualification) { q.Kind = ProviderKindCalculationProfile }, true},
		{"private manual source", func(q *SourceQualification) { q.Kind = ProviderKindManualImport }, true},
		{"missing parser", func(q *SourceQualification) { q.ParserVersion = "" }, true},
		{"noncanonical hash", func(q *SourceQualification) { q.Artifact.SHA256 = strings.Repeat("A", 64) }, true},
		{"zero raw length", func(q *SourceQualification) { q.Artifact.ByteLength = 0 }, true},
		{"blocked transport", func(q *SourceQualification) { q.Retrieval.HTTPStatus = 403 }, true},
		{"credential URL", func(q *SourceQualification) { q.Retrieval.URL = "https://user:password@authority.example/calendar" }, true},
		{"nonpublic URL", func(q *SourceQualification) { q.CanonicalURL = "file:///private/calendar" }, true},
		{"terms not reviewed", func(q *SourceQualification) { q.TermsAssessment = "unknown" }, true},
		{"runtime label invented", func(q *SourceQualification) { q.Evidence[0].Label = "CONFIRMED_RUNTIME" }, true},
		{"duplicate evidence", func(q *SourceQualification) { q.Evidence[1].ID = q.Evidence[0].ID }, true},
		{"unbound sample", func(q *SourceQualification) { q.Comparisons[0].EvidenceID = "missing" }, true},
		{"missing actual comparisons", func(q *SourceQualification) { q.Comparisons = nil }, true},
		{"samples all one date", func(q *SourceQualification) { q.Comparisons[1].Day = q.Comparisons[0].Day }, true},
		{"sample outside coverage", func(q *SourceQualification) { q.Comparisons[0].Day.Date = "2026-08-31" }, true},
		{"sample midnight reversal", func(q *SourceQualification) { q.Comparisons[0].Day.Isha = "00:05" }, true},
		{"incomplete normalization", func(q *SourceQualification) { q.ValidatedDays = 29 }, true},
		{"stale when qualified", func(q *SourceQualification) { q.FreshThrough = "2026-09-07" }, true},
		{"freshness exceeds coverage", func(q *SourceQualification) { q.FreshThrough = "2026-10-01" }, true},
		{"numeric timezone", func(q *SourceQualification) { q.Timezone = "+03:00" }, true},
		{"device timezone", func(q *SourceQualification) { q.Timezone = "Local" }, true},
		{"noncanonical qualification time", func(q *SourceQualification) { q.QualifiedAt = "2026-09-08T13:00:00+03:00" }, true},
		{"capture after qualification", func(q *SourceQualification) { q.Artifact.CapturedAt = "2026-09-09T00:00:00Z" }, true},
		{"evidence after qualification", func(q *SourceQualification) { q.Evidence[0].RetrievedAt = "2026-09-09T00:00:00Z" }, true},
		{"unqualified state", func(q *SourceQualification) { q.State = "researched" }, true},
		{"invented person", func(q *SourceQualification) { q.DecisionSystem = "some-human-approver" }, true},
		{"evidence claim changed", func(q *SourceQualification) { q.Evidence[0].Claim += " altered" }, false},
		{"parser changed", func(q *SourceQualification) { q.ParserVersion = "different/v1" }, false},
		{"raw hash changed", func(q *SourceQualification) { q.Artifact.SHA256 = strings.Repeat("d", 64) }, false},
		{"source changed", func(q *SourceQualification) { q.SourceID += "-other" }, false},
		{"scope changed", func(q *SourceQualification) { q.Scope.CityID += "-other" }, false},
		{"diff changed", func(q *SourceQualification) { q.DiffSHA256 = strings.Repeat("e", 64) }, false},
		{"fingerprint changed", func(q *SourceQualification) { q.SHA256 = strings.Repeat("f", 64) }, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			q := syntheticQualification(t)
			tt.mutate(&q)
			if tt.rehash {
				fingerprintQualification(t, &q)
			}
			if err := q.Validate(); err == nil {
				t.Fatal("invalid qualification accepted")
			}
		})
	}
}

func syntheticQualification(t *testing.T) SourceQualification {
	t.Helper()
	q := SourceQualification{
		SchemaVersion: SourceQualificationSchema, State: "qualified", DecisionSystem: SourceQualificationDecisionSystem,
		QualifiedAt: "2026-09-08T10:00:00Z",
		Authority:   PrayerAuthority{ID: "authority-synthetic", Name: "Synthetic test authority", Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"},
		SourceID:    "source-synthetic", Kind: ProviderKindOfficialFile, CanonicalURL: "https://authority.example/calendar",
		Scope:           GeographicScope{ID: "scope-synthetic", Kind: GeographicScopeCity, CityID: "city-synthetic", RegionID: "region-synthetic", Description: "Synthetic city only"},
		CatalogRevision: "catalog-synthetic-v1", Timezone: "Europe/Moscow",
		Coverage: DateRange{From: "2026-09-01", To: "2026-09-30"}, FreshThrough: "2026-09-30",
		Artifact:      RawArtifact{Filename: "https://authority.example/calendar.csv", ContentType: "text/csv", CapturedAt: "2026-09-08T09:00:00Z", ByteLength: 1234, SHA256: strings.Repeat("a", 64)},
		Retrieval:     PublicSourceRetrieval{URL: "https://authority.example/calendar.csv", HTTPStatus: 200, ContentType: "text/csv"},
		ParserVersion: "synthetic-test/v1", CandidateID: "candidate-synthetic", NormalizedSHA256: strings.Repeat("b", 64),
		TranscriptionSHA256: strings.Repeat("a", 64), DiffSHA256: strings.Repeat("c", 64), ValidationSHA256: strings.Repeat("d", 64), ValidatedDays: 30,
		OnsetSHA256:     strings.Repeat("b", 64),
		TermsAssessment: "public_transport_no_restriction_observed",
	}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		q.Evidence = append(q.Evidence, SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: "2026-09-08T09:00:00Z", SHA256: strings.Repeat("e", 64), Claim: "Synthetic fixture for " + purpose})
	}
	for _, date := range []string{"2026-09-01", "2026-09-15", "2026-09-30"} {
		q.Comparisons = append(q.Comparisons, SourceValueComparison{EvidenceID: "evidence-value_comparison", Day: PrayerDay{Date: date, Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "15:00", Maghrib: "18:00", Isha: "20:00"}})
	}
	fingerprintQualification(t, &q)
	return q
}

func fingerprintQualification(t *testing.T, q *SourceQualification) {
	t.Helper()
	fingerprint, err := SourceQualificationSHA256(*q)
	if err != nil {
		t.Fatal(err)
	}
	q.SHA256 = fingerprint
	q.ID = "qualification-" + fingerprint[:32]
}
