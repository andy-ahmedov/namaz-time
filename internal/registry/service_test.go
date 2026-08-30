package registry

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const (
	testCityID     = "city-test-ulyanovsk"
	testRegionID   = "ru-uly"
	testScopeID    = "scope-test-ulyanovsk"
	testAuthority  = "authority-test"
	testSourceID   = "source-test"
	testPolicyID   = "policy-test"
	testTimetable  = "timetable-test"
	testMosqueID   = "mosque-test-ulyanovsk"
	testApprovalID = "approval-test"
	testSnapshotID = "snapshot-test"
)

func TestServiceActivatesOnlyVerifiedExecutableRevision(t *testing.T) {
	store := newFakeRevisionStore()
	approvals := &fakeApprovalVerifier{evidence: map[string]VerifiedApproval{
		testApprovalID: {ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()},
	}}
	snapshots := &fakeSnapshotVerifier{evidence: map[string]VerifiedSnapshot{
		testSnapshotID: {
			ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk",
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PayloadSHA256: repeatHex("b"),
			SigningKeyID: "production-key-test", VerifiedAt: testNow(),
		},
	}}
	service, err := NewPersistentService(PersistentServiceConfig{
		Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow,
	})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}
	revision := RevisionRecord{ID: "revision-1", SchemaVersion: RegistrySchemaVersion, CatalogRevisionID: "catalog-1", CreatedBy: "actor-1", Reason: "approved pilot registry"}
	if err := service.Stage(t.Context(), revision, executableDataset()); err != nil {
		t.Fatalf("Stage() error = %v", err)
	}
	if err := service.Activate(t.Context(), revision.ID, "actor-approver", "activate verified pilot"); err != nil {
		t.Fatalf("Activate() error = %v", err)
	}
	active, err := service.ActiveRevision(t.Context())
	if err != nil || active.ID != revision.ID || active.ContentSHA256 == "" {
		t.Fatalf("ActiveRevision() = %#v, %v", active, err)
	}
	resolved, err := service.Resolve(t.Context(), ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil || resolved.TimeTable.PublishedSnapshotID != testSnapshotID || resolved.Source.Status != domain.PrayerSourceApproved {
		t.Fatalf("Resolve() = %#v, %v", resolved, err)
	}
	activation := store.activations[len(store.activations)-1]
	if len(activation.Approvals) != 1 || len(activation.Snapshots) != 1 || activation.Reason != "activate verified pilot" {
		t.Fatalf("activation evidence = %#v", activation)
	}
}

func TestServiceRejectsUnsupportedRevisionSchema(t *testing.T) {
	store := newFakeRevisionStore()
	service, err := NewPersistentService(PersistentServiceConfig{
		Store: store, ApprovalVerifier: &fakeApprovalVerifier{}, SnapshotVerifier: &fakeSnapshotVerifier{}, Now: testNow,
	})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}
	revision := RevisionRecord{
		ID: "revision-unsupported", SchemaVersion: RegistrySchemaVersion + 1,
		CatalogRevisionID: "catalog-1", CreatedBy: "actor-1", Reason: "unsupported schema",
	}
	if err := service.Stage(t.Context(), revision, executableDataset()); !errors.Is(err, ErrRevisionInvalid) {
		t.Fatalf("Stage() error = %v, want invalid revision", err)
	}
	if len(store.revisions) != 0 {
		t.Fatal("unsupported registry schema was persisted")
	}
}

func TestServiceFailsClosedBeforeActivation(t *testing.T) {
	tests := []struct {
		name    string
		mutate  func(*Dataset, *fakeApprovalVerifier, *fakeSnapshotVerifier)
		wantErr error
	}{
		{
			name: "research source",
			mutate: func(dataset *Dataset, _ *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				dataset.Sources[0].Status = domain.PrayerSourceResearchOnly
			},
			wantErr: ErrRevisionNotExecutable,
		},
		{
			name: "stale source",
			mutate: func(dataset *Dataset, _ *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				dataset.Sources[0].FreshThrough = "2026-08-29"
			},
			wantErr: ErrRevisionNotExecutable,
		},
		{
			name: "missing approval proof",
			mutate: func(_ *Dataset, approvals *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				delete(approvals.evidence, testApprovalID)
			},
			wantErr: ErrVerifiedReferenceMissing,
		},
		{
			name: "snapshot mosque mismatch",
			mutate: func(_ *Dataset, _ *fakeApprovalVerifier, snapshots *fakeSnapshotVerifier) {
				value := snapshots.evidence[testSnapshotID]
				value.MosqueID = "other-mosque"
				snapshots.evidence[testSnapshotID] = value
			},
			wantErr: ErrVerifiedReferenceMismatch,
		},
		{
			name: "missing override approval proof",
			mutate: func(dataset *Dataset, _ *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				addApprovedOverride(dataset, "approval-override-unverified")
			},
			wantErr: ErrVerifiedReferenceMissing,
		},
		{
			name: "unavailable override component",
			mutate: func(dataset *Dataset, approvals *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				addApprovedOverride(dataset, "approval-override")
				dataset.Sources[len(dataset.Sources)-1].Status = domain.PrayerSourceUnavailable
				approvals.evidence["approval-override"] = VerifiedApproval{
					ID: "approval-override", MosqueID: testMosqueID, EvidenceSHA256: repeatHex("c"), VerifiedAt: testNow(),
				}
			},
			wantErr: ErrRevisionNotExecutable,
		},
		{
			name: "same tier ambiguity",
			mutate: func(dataset *Dataset, _ *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
				duplicate := dataset.Policies[0]
				duplicate.ID = "policy-test-duplicate"
				dataset.Policies = append(dataset.Policies, duplicate)
			},
			wantErr: ErrPolicyAmbiguous,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			store := newFakeRevisionStore()
			approvals := &fakeApprovalVerifier{evidence: map[string]VerifiedApproval{
				testApprovalID: {ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()},
			}}
			snapshots := &fakeSnapshotVerifier{evidence: map[string]VerifiedSnapshot{
				testSnapshotID: {
					ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk",
					Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PayloadSHA256: repeatHex("b"),
					SigningKeyID: "production-key-test", VerifiedAt: testNow(),
				},
			}}
			dataset := executableDataset()
			test.mutate(&dataset, approvals, snapshots)
			service, err := NewPersistentService(PersistentServiceConfig{Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow})
			if err != nil {
				t.Fatalf("NewPersistentService() error = %v", err)
			}
			if err := service.Stage(t.Context(), RevisionRecord{ID: "revision-blocked", SchemaVersion: RegistrySchemaVersion, CatalogRevisionID: "catalog-1", CreatedBy: "actor-1", Reason: "test blocked revision"}, dataset); err != nil {
				t.Fatalf("Stage() error = %v", err)
			}
			err = service.Activate(t.Context(), "revision-blocked", "actor-approver", "must fail closed")
			if !errors.Is(err, test.wantErr) {
				t.Fatalf("Activate() error = %v, want %v", err, test.wantErr)
			}
			if _, err := service.ActiveRevision(t.Context()); !errors.Is(err, ErrRevisionUnavailable) {
				t.Fatalf("blocked activation changed active revision: %v", err)
			}
		})
	}
}

func TestServiceRollbackReverifiesAndRestoresPriorRevision(t *testing.T) {
	store := newFakeRevisionStore()
	approvals := &fakeApprovalVerifier{evidence: map[string]VerifiedApproval{
		testApprovalID: {ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()},
	}}
	snapshots := &fakeSnapshotVerifier{evidence: map[string]VerifiedSnapshot{
		testSnapshotID: {
			ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk",
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PayloadSHA256: repeatHex("b"),
			SigningKeyID: "production-key-test", VerifiedAt: testNow(),
		},
	}}
	service, err := NewPersistentService(PersistentServiceConfig{Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}
	first := RevisionRecord{ID: "revision-1", SchemaVersion: RegistrySchemaVersion, CatalogRevisionID: "catalog-1", CreatedBy: "actor-1", Reason: "first"}
	if err := service.Stage(t.Context(), first, executableDataset()); err != nil {
		t.Fatalf("Stage(first) error = %v", err)
	}
	if err := service.Activate(t.Context(), first.ID, "actor-1", "activate first"); err != nil {
		t.Fatalf("Activate(first) error = %v", err)
	}
	secondDataset := executableDataset()
	secondDataset.Cities[0].Aliases = append(secondDataset.Cities[0].Aliases, "Ulsk")
	second := RevisionRecord{ID: "revision-2", SchemaVersion: RegistrySchemaVersion, ParentRevisionID: first.ID, CatalogRevisionID: "catalog-2", CreatedBy: "actor-2", Reason: "second"}
	if err := service.Stage(t.Context(), second, secondDataset); err != nil {
		t.Fatalf("Stage(second) error = %v", err)
	}
	if err := service.Activate(t.Context(), second.ID, "actor-2", "activate second"); err != nil {
		t.Fatalf("Activate(second) error = %v", err)
	}
	if err := service.Rollback(t.Context(), first.ID, "actor-rollback", "restore reviewed registry"); err != nil {
		t.Fatalf("Rollback() error = %v", err)
	}
	active, err := service.ActiveRevision(t.Context())
	if err != nil || active.ID != first.ID {
		t.Fatalf("active after rollback = %#v, %v", active, err)
	}
	if got := len(store.activations); got != 3 || !store.activations[2].Rollback {
		t.Fatalf("activation audit = %#v", store.activations)
	}
	if approvals.calls < 3 || snapshots.calls < 3 {
		t.Fatalf("rollback did not reverify references: approval=%d snapshot=%d", approvals.calls, snapshots.calls)
	}
}

func executableDataset() Dataset {
	return Dataset{
		Cities: []domain.City{{
			ID: testCityID, Name: "Ульяновск", Aliases: []string{"Ulyanovsk"}, CountryCode: "RU", RegionID: testRegionID,
			SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk",
			GeographicSource: "https://www.geonames.org/479123", GeographicSourceID: "geonames:479123",
			GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
		}},
		Regions:     []domain.Region{{ID: testRegionID, Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}},
		Scopes:      []domain.GeographicScope{{ID: testScopeID, Kind: domain.GeographicScopeCity, CityID: testCityID, RegionID: testRegionID, Description: "test scope"}},
		Authorities: []domain.PrayerAuthority{{ID: testAuthority, Name: "Test authority", EvidenceLabel: "CONFIRMED_PUBLIC"}},
		Sources: []domain.PrayerSource{{
			ID: testSourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{testAuthority}, GeographicScopeID: testScopeID,
			Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
		}},
		Policies: []domain.PrayerPolicy{{
			ID: testPolicyID, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: testScopeID, AuthorityIDs: []string{testAuthority},
			SourceID: testSourceID, TimeTableID: testTimetable, MosqueIDs: []string{testMosqueID},
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, ApprovalID: testApprovalID,
		}},
		TimeTables: []domain.TimeTable{{
			ID: testTimetable, SourceID: testSourceID, GeographicScopeID: testScopeID, MosqueID: testMosqueID,
			Timezone: "Europe/Ulyanovsk", Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PublishedSnapshotID: testSnapshotID,
		}},
	}
}

func addApprovedOverride(dataset *Dataset, approvalID string) {
	dataset.Sources = append(dataset.Sources,
		domain.PrayerSource{
			ID: "source-base", Kind: domain.ProviderKindOfficialFile, AuthorityIDs: []string{testAuthority},
			GeographicScopeID: testScopeID, Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
		},
		domain.PrayerSource{
			ID: "source-override", Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{testAuthority},
			GeographicScopeID: testScopeID, Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
		},
	)
	dataset.SourceOverrides = append(dataset.SourceOverrides, domain.SourceOverride{
		ID: "override-test", BaseSourceID: "source-base", OverrideSourceID: "source-override",
		Effective: domain.DateRange{From: "2026-08-01", To: "2026-08-31"}, AppliedFields: []string{"dhuhr"}, ApprovalID: approvalID,
	})
	dataset.TimeTables[0].SourceOverrideIDs = append(dataset.TimeTables[0].SourceOverrideIDs, "override-test")
}

func testNow() time.Time { return time.Date(2026, 8, 30, 6, 0, 0, 0, time.UTC) }

func repeatHex(character string) string {
	result := ""
	for range 64 {
		result += character
	}
	return result
}

type fakeRevisionStore struct {
	revisions   map[string]storedFakeRevision
	activeID    string
	activations []ActivationRecord
}

type storedFakeRevision struct {
	record  RevisionRecord
	dataset Dataset
}

func newFakeRevisionStore() *fakeRevisionStore {
	return &fakeRevisionStore{revisions: make(map[string]storedFakeRevision)}
}

func (store *fakeRevisionStore) Stage(_ context.Context, record RevisionRecord, dataset Dataset) error {
	if _, duplicate := store.revisions[record.ID]; duplicate {
		return ErrRevisionConflict
	}
	if record.ParentRevisionID != "" {
		if _, exists := store.revisions[record.ParentRevisionID]; !exists {
			return ErrRevisionUnavailable
		}
	}
	store.revisions[record.ID] = storedFakeRevision{record: record, dataset: dataset}
	return nil
}

func (store *fakeRevisionStore) Load(_ context.Context, revisionID string) (RevisionRecord, Dataset, error) {
	value, ok := store.revisions[revisionID]
	if !ok {
		return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
	}
	return value.record, value.dataset, nil
}

func (store *fakeRevisionStore) Activate(_ context.Context, activation ActivationRecord) error {
	if _, exists := store.revisions[activation.RevisionID]; !exists {
		return ErrRevisionUnavailable
	}
	store.activeID = activation.RevisionID
	store.activations = append(store.activations, activation)
	return nil
}

func (store *fakeRevisionStore) Active(ctx context.Context) (RevisionRecord, Dataset, error) {
	if store.activeID == "" {
		return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
	}
	return store.Load(ctx, store.activeID)
}

type fakeApprovalVerifier struct {
	evidence map[string]VerifiedApproval
	calls    int
}

func (verifier *fakeApprovalVerifier) VerifyApproval(_ context.Context, approvalID string) (VerifiedApproval, error) {
	verifier.calls++
	value, ok := verifier.evidence[approvalID]
	if !ok {
		return VerifiedApproval{}, ErrVerifiedReferenceMissing
	}
	return value, nil
}

type fakeSnapshotVerifier struct {
	evidence map[string]VerifiedSnapshot
	calls    int
}

func (verifier *fakeSnapshotVerifier) VerifySnapshot(_ context.Context, snapshotID string) (VerifiedSnapshot, error) {
	verifier.calls++
	value, ok := verifier.evidence[snapshotID]
	if !ok {
		return VerifiedSnapshot{}, ErrVerifiedReferenceMissing
	}
	return value, nil
}
