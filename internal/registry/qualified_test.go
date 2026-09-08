package registry

import (
	"encoding/json"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

func TestQualifiedPolicyBindingsRoundTripAndExactCatalog(t *testing.T) {
	d := qualifiedDataset(t)
	content, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{d.Regions, d.Cities})
	if err != nil {
		t.Fatal(err)
	}
	catalog := geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{ID: "catalog-1", ContentSHA256: sha256Hex(content), ImportedCities: len(d.Cities)}, Regions: d.Regions, Cities: d.Cities}
	bindings := PolicyBindings{SchemaVersion: "namaztime-policy-bindings/v2", RegistryRevision: PolicyBindingsRevision{ID: "qualified-bindings", SchemaVersion: 2, CatalogRevisionID: catalog.Revision.ID, CatalogContentSHA256: catalog.Revision.ContentSHA256, CreatedAt: testNow(), CreatedBy: "system-test", Reason: "synthetic qualified source"}, Qualifications: d.Qualifications, Scopes: d.Scopes, Authorities: d.Authorities, Sources: d.Sources, Policies: d.Policies, TimeTables: d.TimeTables}
	encoded, err := json.Marshal(bindings)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := DecodePolicyBindings(encoded)
	if err != nil {
		t.Fatal(err)
	}
	record, composed, err := ComposeCatalog(catalog, decoded)
	if err != nil {
		t.Fatal(err)
	}
	if record.SchemaVersion != 2 || len(composed.Qualifications) != 1 || composed.Qualifications[0].SHA256 != d.Qualifications[0].SHA256 {
		t.Fatal("public proof lost during composition")
	}
	decoded.Qualifications[0].Evidence[0].Claim = "mutated"
	if composed.Qualifications[0].Evidence[0].Claim == "mutated" {
		t.Fatal("composed proof retains mutable caller data")
	}
	for _, tc := range []struct {
		name   string
		mutate func(*PolicyBindings)
	}{
		{"legacy document", func(b *PolicyBindings) { b.SchemaVersion = "namaztime-policy-bindings/v1" }},
		{"legacy revision", func(b *PolicyBindings) { b.RegistryRevision.SchemaVersion = 1 }},
		{"missing qualification", func(b *PolicyBindings) { b.Qualifications = nil }},
		{"wrong catalog hash", func(b *PolicyBindings) { b.RegistryRevision.CatalogContentSHA256 = repeatHex("9") }},
		{"qualified catalog substitution", func(b *PolicyBindings) {
			b.Qualifications[0].CatalogRevision = "another-catalog"
			sealRegistryProof(t, &b.Qualifications[0])
			b.Sources[0].QualificationID = b.Qualifications[0].ID
			b.Policies[0].QualificationID = b.Qualifications[0].ID
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var b PolicyBindings
			if err := json.Unmarshal(encoded, &b); err != nil {
				t.Fatal(err)
			}
			tc.mutate(&b)
			if _, _, err := ComposeCatalog(catalog, b); err == nil {
				t.Fatal("invalid qualified bindings accepted")
			}
		})
	}
}

func TestQualifiedRegistryActivationBindsSignedProofWithoutApprovalVerifier(t *testing.T) {
	d := qualifiedDataset(t)
	store := newFakeRevisionStore()
	snapshots := qualifiedSnapshotVerifier(d)
	service, err := NewPersistentService(PersistentServiceConfig{Store: store, SnapshotVerifier: snapshots, Now: testNow})
	if err != nil {
		t.Fatal(err)
	}
	revision := RevisionRecord{ID: "qualified-revision", SchemaVersion: 2, CatalogRevisionID: "catalog-1", CreatedBy: "system-test", Reason: "synthetic first-party qualification test"}
	if err := service.Stage(t.Context(), revision, d); err != nil {
		t.Fatal(err)
	}
	if err := service.Activate(t.Context(), revision.ID, "system-test", "activate signed qualified synthetic source"); err != nil {
		t.Fatal(err)
	}
	activation := store.activations[0]
	if len(activation.Approvals) != 0 || len(activation.Snapshots) != 1 || activation.Snapshots[0].QualificationSHA256 != d.Qualifications[0].SHA256 {
		t.Fatalf("incorrect public admission evidence: %+v", activation)
	}
	// A signed artifact carrying another valid qualification cannot back this
	// revision, even if every old snapshot field happens to match.
	value := snapshots.evidence[testSnapshotID]
	value.QualificationSHA256 = repeatHex("9")
	snapshots.evidence[testSnapshotID] = value
	if err := service.Activate(t.Context(), revision.ID, "system-test", "attempt swapped qualification"); !errors.Is(err, ErrVerifiedReferenceMismatch) {
		t.Fatalf("swapped signed proof accepted: %v", err)
	}
	if len(store.activations) != 1 {
		t.Fatal("failed admission changed active state")
	}
}

func TestQualifiedRegistryActivationAndStagingFailClosed(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Dataset, *RevisionRecord, *fakeSnapshotVerifier)
		late   bool
	}{
		{"legacy schema cannot carry public qualification", func(_ *Dataset, r *RevisionRecord, _ *fakeSnapshotVerifier) { r.SchemaVersion = 1 }, false},
		{"catalog revision substitution", func(_ *Dataset, r *RevisionRecord, _ *fakeSnapshotVerifier) { r.CatalogRevisionID = "other-catalog" }, false},
		{"missing authenticated proof", func(_ *Dataset, _ *RevisionRecord, s *fakeSnapshotVerifier) {
			v := s.evidence[testSnapshotID]
			v.QualificationID = ""
			s.evidence[testSnapshotID] = v
		}, false},
		{"signed noncanonical display name", func(_ *Dataset, _ *RevisionRecord, s *fakeSnapshotVerifier) {
			v := s.evidence[testSnapshotID]
			c := *v.PublicContext
			c.Name = "another-city"
			v.PublicContext = &c
			s.evidence[testSnapshotID] = v
		}, false},
		{"signed noncanonical locality", func(_ *Dataset, _ *RevisionRecord, s *fakeSnapshotVerifier) {
			v := s.evidence[testSnapshotID]
			c := *v.PublicContext
			c.Locality = "another-city"
			v.PublicContext = &c
			s.evidence[testSnapshotID] = v
		}, false},
		{"missing signed public context", func(_ *Dataset, _ *RevisionRecord, s *fakeSnapshotVerifier) {
			v := s.evidence[testSnapshotID]
			v.PublicContext = nil
			s.evidence[testSnapshotID] = v
		}, false},
		{"stale source", func(d *Dataset, _ *RevisionRecord, _ *fakeSnapshotVerifier) {
			d.Sources[0].Status = domain.PrayerSourceStale
		}, false},
		{"short freshness", func(d *Dataset, _ *RevisionRecord, _ *fakeSnapshotVerifier) { d.Sources[0].FreshThrough = "2026-08-29" }, false},
		{"expired at activation", func(_ *Dataset, _ *RevisionRecord, _ *fakeSnapshotVerifier) {}, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			d := qualifiedDataset(t)
			r := RevisionRecord{ID: "qualified-bad", SchemaVersion: 2, CatalogRevisionID: "catalog-1", CreatedBy: "system-test", Reason: "invalid synthetic admission"}
			store := newFakeRevisionStore()
			snapshots := qualifiedSnapshotVerifier(d)
			tc.mutate(&d, &r, snapshots)
			now := testNow
			if tc.late {
				now = func() time.Time { return testNow().AddDate(0, 0, 1) }
			}
			service, err := NewPersistentService(PersistentServiceConfig{Store: store, SnapshotVerifier: snapshots, Now: now})
			if err != nil {
				t.Fatal(err)
			}
			err = service.Stage(t.Context(), r, d)
			if err == nil {
				err = service.Activate(t.Context(), r.ID, "system-test", "reject invalid proof")
			}
			if err == nil || len(store.activations) != 0 {
				t.Fatal("invalid public admission reached activation")
			}
		})
	}
}

func qualifiedSnapshotVerifier(d Dataset) *fakeSnapshotVerifier {
	context, err := qualification.PublicDisplayContext(d.Qualifications[0].Scope, deriveQualificationCatalog(d, d.Qualifications[0]), d.Qualifications[0].Timezone)
	if err != nil {
		panic(err)
	} // Only validated synthetic fixtures call this helper.
	return &fakeSnapshotVerifier{evidence: map[string]VerifiedSnapshot{testSnapshotID: {ID: testSnapshotID, MosqueID: d.TimeTables[0].MosqueID, Timezone: d.TimeTables[0].Timezone, Effective: d.TimeTables[0].Effective, PayloadSHA256: repeatHex("b"), SigningKeyID: "synthetic-production-key", VerifiedAt: testNow(), QualificationID: d.Qualifications[0].ID, QualificationSHA256: d.Qualifications[0].SHA256, PublicContext: &context}}}
}

func TestQualifiedIndependentAuthoritiesActivateAndRequireExplicitChoice(t *testing.T) {
	d := qualifiedDataset(t)
	other := qualifiedDataset(t)
	other.Authorities[0].ID += "-independent"
	other.Authorities[0].Name += " B"
	other.Sources[0].ID += "-independent"
	other.Sources[0].AuthorityIDs = []string{other.Authorities[0].ID}
	other.Policies[0].ID += "-independent"
	other.Policies[0].SourceID = other.Sources[0].ID
	other.Policies[0].AuthorityIDs = other.Sources[0].AuthorityIDs
	other.TimeTables[0].ID += "-independent"
	other.TimeTables[0].SourceID = other.Sources[0].ID
	other.TimeTables[0].PublishedSnapshotID += "-independent"
	other.Policies[0].TimeTableID = other.TimeTables[0].ID
	other.Qualifications[0].SourceID = other.Sources[0].ID
	other.Qualifications[0].Authority = other.Authorities[0]
	sealRegistryProof(t, &other.Qualifications[0])
	other.Sources[0].QualificationID = other.Qualifications[0].ID
	other.Policies[0].QualificationID = other.Qualifications[0].ID
	d.Authorities = append(d.Authorities, other.Authorities...)
	d.Sources = append(d.Sources, other.Sources...)
	d.Policies = append(d.Policies, other.Policies...)
	d.TimeTables = append(d.TimeTables, other.TimeTables...)
	d.Qualifications = append(d.Qualifications, other.Qualifications...)
	snapshots := qualifiedSnapshotVerifier(d)
	b := qualifiedSnapshotVerifier(other).evidence[testSnapshotID]
	b.ID = other.TimeTables[0].PublishedSnapshotID
	snapshots.evidence[b.ID] = b
	store := newFakeRevisionStore()
	service, err := NewPersistentService(PersistentServiceConfig{Store: store, SnapshotVerifier: snapshots, Now: testNow})
	if err != nil {
		t.Fatal(err)
	}
	r := RevisionRecord{ID: "qualified-multiple", SchemaVersion: 2, CatalogRevisionID: "catalog-1", CreatedBy: "system-test", Reason: "independent synthetic authorities"}
	if err := service.Stage(t.Context(), r, d); err != nil {
		t.Fatal(err)
	}
	if err := service.Activate(t.Context(), r.ID, "system-test", "independent verified publications"); err != nil {
		t.Fatal(err)
	}
	request := ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"}
	if _, err := service.Resolve(t.Context(), request); !errors.Is(err, ErrPolicyAmbiguous) {
		t.Fatalf("silently selected an authority: %v", err)
	}
	assessment, err := service.AssessRevision(t.Context(), r.ID, request)
	if err != nil {
		t.Fatal(err)
	}
	choices, err := ProjectCityScheduleChoices(assessment)
	if err != nil {
		t.Fatal(err)
	}
	if !choices.SelectionRequired || len(choices.Choices) != 2 {
		t.Fatalf("lost independent choices: %+v", choices)
	}
	for _, choice := range choices.Choices {
		if !choice.Executable || choice.ApprovalID != "" || choice.Qualification == nil || choice.Qualification.ID != choice.Source.QualificationID {
			t.Fatalf("incomplete qualified choice: %+v", choice)
		}
	}
	choices.Choices[0].Qualification.Evidence[0].Claim = "mutated caller projection"
	again, err := service.AssessRevision(t.Context(), r.ID, request)
	if err != nil {
		t.Fatal(err)
	}
	if again.Result.Options[0].Qualification.Evidence[0].Claim == "mutated caller projection" {
		t.Fatal("projection shared mutable proof")
	}
}

func TestQualifiedPublicDatasetScopesWithoutInventingMosqueApproval(t *testing.T) {
	dataset := qualifiedDataset(t)
	r, err := New(dataset)
	if err != nil {
		t.Fatal(err)
	}
	for _, mosque := range []string{testMosqueID, "other-real-device-mosque"} {
		got, err := r.Assess(ResolveRequest{CityID: testCityID, MosqueID: mosque, Date: "2026-08-30"})
		if err != nil || got.Status != AssessmentResolved || len(got.Options) != 1 || !got.Options[0].Selectable {
			t.Fatalf("qualified geographic choice for %s = %+v, %v", mosque, got, err)
		}
	}
	encoded, _ := json.Marshal(dataset.Policies[0])
	if strings.Contains(string(encoded), "approval_id") {
		t.Fatal("public policy invented approval")
	}
	// Registry owns the exact qualified evidence; mutation cannot broaden it.
	dataset.Qualifications[0].Scope.CityID = "other-city"
	got, err := r.Assess(ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil || got.Status != AssessmentResolved {
		t.Fatalf("caller mutation affected registry: %v", err)
	}
}

func TestQualifiedRegistryRejectsUnboundProofs(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Dataset)
	}{
		{"missing proof", func(d *Dataset) { d.Qualifications = nil }},
		{"missing source reference", func(d *Dataset) { d.Sources[0].QualificationID = "" }},
		{"missing policy reference", func(d *Dataset) { d.Policies[0].QualificationID = "" }},
		{"fake human approval", func(d *Dataset) { d.Policies[0].ApprovalID = testApprovalID }},
		{"fake mosque binding", func(d *Dataset) { d.Policies[0].MosqueIDs = []string{testMosqueID} }},
		{"altered authority", func(d *Dataset) { d.Authorities[0].Name = "different publisher" }},
		{"altered scope", func(d *Dataset) { d.Scopes[0].Kind = domain.GeographicScopeRegion; d.Scopes[0].CityID = "" }},
		{"altered canonical URL", func(d *Dataset) { d.Sources[0].CanonicalURL += "/other" }},
		{"promoted approval status", func(d *Dataset) { d.Sources[0].Status = domain.PrayerSourceApproved }},
		{"expanded policy range", func(d *Dataset) { d.Policies[0].Effective.From = "2026-08-29" }},
		{"expanded table range", func(d *Dataset) { d.TimeTables[0].Effective.To = "2026-08-31" }},
		{"altered context", func(d *Dataset) { d.TimeTables[0].MosqueID = testMosqueID }},
		{"time zone substitution", func(d *Dataset) { d.TimeTables[0].Timezone = "Europe/Moscow" }},
		{"proof hash tampered", func(d *Dataset) { d.Qualifications[0].Evidence[0].Claim += "changed" }},
		{"unreferenced qualification", func(d *Dataset) {
			q := d.Qualifications[0]
			q.SourceID += "-orphan"
			sealRegistryProof(t, &q)
			d.Qualifications = append(d.Qualifications, q)
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			d := qualifiedDataset(t)
			tc.mutate(&d)
			if _, err := New(d); err == nil {
				t.Fatal("invalid qualified dataset accepted")
			}
		})
	}
}

func TestQualifiedRegionValidatesEveryCatalogLocality(t *testing.T) {
	d := qualifiedDataset(t)
	d.Scopes[0].Kind, d.Scopes[0].CityID = domain.GeographicScopeRegion, ""
	d.Qualifications[0].Scope = d.Scopes[0]
	sealRegistryProof(t, &d.Qualifications[0])
	d.Sources[0].QualificationID, d.Policies[0].QualificationID = d.Qualifications[0].ID, d.Qualifications[0].ID
	context, err := qualification.PublicDisplayContext(d.Scopes[0], qualification.CatalogBinding{Revision: "catalog-1", SourceRevision: d.Cities[0].GeographicRevision, Region: d.Regions[0], Cities: d.Cities}, d.Cities[0].Timezone)
	if err != nil {
		t.Fatal(err)
	}
	d.TimeTables[0].MosqueID = context.ID
	if _, err := New(d); err != nil {
		t.Fatalf("explicit regional proof: %v", err)
	}
	other := d.Cities[0]
	other.ID = "another-region-locality"
	other.Timezone = "Europe/Moscow"
	d.Cities = append(d.Cities, other)
	if _, err := New(d); err == nil {
		t.Fatal("region policy silently spans incompatible canonical timezones")
	}
}

func qualifiedDataset(t *testing.T) Dataset {
	t.Helper()
	d := executableDataset()
	d.Authorities[0].Name, d.Authorities[0].Website = "Synthetic qualified authority", "https://authority.example"
	d.Sources[0].Kind, d.Sources[0].Status, d.Sources[0].CanonicalURL = domain.ProviderKindOfficialFile, domain.PrayerSourceQualified, "https://authority.example/calendar"
	d.Sources[0].FreshThrough = "2026-08-30"
	d.Policies[0].ApprovalID, d.Policies[0].MosqueIDs = "", nil
	d.Policies[0].Effective = domain.DateRange{From: "2026-08-30", To: "2026-08-30"}
	d.TimeTables[0].Effective = d.Policies[0].Effective
	stamp := testNow().Add(-time.Minute).Format(time.RFC3339)
	q := domain.SourceQualification{
		SchemaVersion: domain.SourceQualificationSchema, State: "qualified", DecisionSystem: domain.SourceQualificationDecisionSystem,
		QualifiedAt: stamp, Authority: d.Authorities[0], SourceID: d.Sources[0].ID, Kind: d.Sources[0].Kind, CanonicalURL: d.Sources[0].CanonicalURL,
		Scope: d.Scopes[0], CatalogRevision: "catalog-1", Timezone: d.Cities[0].Timezone, Coverage: d.Policies[0].Effective, FreshThrough: d.Sources[0].FreshThrough,
		Artifact:      domain.RawArtifact{Filename: "synthetic-calendar", ContentType: "text/csv", ByteLength: 12, SHA256: repeatHex("a"), CapturedAt: stamp},
		Retrieval:     domain.PublicSourceRetrieval{URL: d.Sources[0].CanonicalURL, HTTPStatus: 200, ContentType: "text/csv"},
		ParserVersion: "synthetic-registry-test/v1", CandidateID: "candidate-synthetic-registry", NormalizedSHA256: repeatHex("b"), OnsetSHA256: repeatHex("c"), TranscriptionSHA256: repeatHex("d"), DiffSHA256: repeatHex("e"), ValidationSHA256: repeatHex("f"), ValidatedDays: 1, TermsAssessment: "public_transport_no_restriction_observed",
	}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		q.Evidence = append(q.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: stamp, SHA256: repeatHex("e"), Claim: "Synthetic protocol test evidence for " + purpose})
	}
	q.Comparisons = []domain.SourceValueComparison{{EvidenceID: "evidence-value_comparison", Day: domain.PrayerDay{Date: "2026-08-30", Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}
	sealRegistryProof(t, &q)
	if err := q.Validate(); err != nil {
		t.Fatal(err)
	}
	d.Qualifications = []domain.SourceQualification{q}
	d.Sources[0].QualificationID, d.Policies[0].QualificationID = q.ID, q.ID
	context, err := qualification.PublicDisplayContext(q.Scope, qualification.CatalogBinding{Revision: q.CatalogRevision, SourceRevision: d.Cities[0].GeographicRevision, Region: d.Regions[0], Cities: d.Cities}, q.Timezone)
	if err != nil {
		t.Fatal(err)
	}
	d.TimeTables[0].MosqueID = context.ID
	return d
}

func sealRegistryProof(t *testing.T, q *domain.SourceQualification) {
	t.Helper()
	hash, err := domain.SourceQualificationSHA256(*q)
	if err != nil {
		t.Fatal(err)
	}
	q.SHA256, q.ID = hash, "qualification-"+hash[:32]
}
