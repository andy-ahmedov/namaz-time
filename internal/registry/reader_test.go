package registry

import (
	"context"
	"testing"
)

type assessmentStore struct {
	*fakeRevisionStore
}

func (store assessmentStore) SearchActiveCities(context.Context, string) ([]CitySearchResult, error) {
	return nil, nil
}

func TestActiveReaderAssessesNamedStagedAndActiveRevisions(t *testing.T) {
	store := newFakeRevisionStore()
	dataset := executableDataset()
	record := RevisionRecord{
		ID: "revision-assessment", SchemaVersion: RegistrySchemaVersion, CatalogRevisionID: "catalog-assessment",
		ContentSHA256: repeatHex("a"), CreatedAt: testNow(), CreatedBy: "actor-test", Reason: "assessment",
	}
	store.revisions[record.ID] = storedFakeRevision{record: record, dataset: dataset}
	reader, err := NewActiveReader(assessmentStore{fakeRevisionStore: store})
	if err != nil {
		t.Fatalf("NewActiveReader() error = %v", err)
	}
	request := ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"}
	staged, err := reader.AssessRevision(t.Context(), record.ID, request)
	if err != nil || staged.State != RevisionStateStaged || staged.Result.Status != AssessmentResolved {
		t.Fatalf("staged assessment = %#v, %v", staged, err)
	}
	store.activeID = record.ID
	active, err := reader.AssessRevision(t.Context(), "", request)
	if err != nil || active.State != RevisionStateActive || active.Revision.ID != record.ID {
		t.Fatalf("active assessment = %#v, %v", active, err)
	}
}
