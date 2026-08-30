package registry

import (
	"context"
	"errors"
	"fmt"
)

type ActiveRevisionStore interface {
	RevisionStore
	SearchActiveCities(context.Context, string) ([]CitySearchResult, error)
}

type ActiveReader struct {
	store ActiveRevisionStore
}

type RevisionState string

const (
	RevisionStateStaged RevisionState = "staged"
	RevisionStateActive RevisionState = "active"
)

type RevisionPolicyAssessment struct {
	Revision RevisionRecord   `json:"revision"`
	State    RevisionState    `json:"revision_state"`
	Result   PolicyAssessment `json:"result"`
}

func NewActiveReader(store ActiveRevisionStore) (*ActiveReader, error) {
	if store == nil {
		return nil, errors.New("create active registry reader: store is required")
	}
	return &ActiveReader{store: store}, nil
}

func (reader *ActiveReader) SearchCities(ctx context.Context, query string) ([]CitySearchResult, error) {
	if reader == nil || reader.store == nil {
		return nil, ErrRevisionUnavailable
	}
	return reader.store.SearchActiveCities(ctx, query)
}

func (reader *ActiveReader) SearchRevisionCities(
	ctx context.Context,
	revisionID string,
	query string,
) ([]CitySearchResult, error) {
	if reader == nil || reader.store == nil || !validAuditText(revisionID, 160) {
		return nil, ErrRevisionUnavailable
	}
	searcher, ok := reader.store.(interface {
		SearchRevisionCities(context.Context, string, string) ([]CitySearchResult, error)
	})
	if !ok {
		return nil, ErrRevisionUnavailable
	}
	return searcher.SearchRevisionCities(ctx, revisionID, query)
}

func (reader *ActiveReader) Resolve(ctx context.Context, request ResolveRequest) (Resolution, error) {
	if reader == nil || reader.store == nil {
		return Resolution{}, ErrRevisionUnavailable
	}
	_, dataset, err := reader.store.Active(ctx)
	if err != nil {
		return Resolution{}, err
	}
	active, err := New(dataset)
	if err != nil {
		return Resolution{}, fmt.Errorf("load active registry: %w", err)
	}
	return active.Resolve(request)
}

func (reader *ActiveReader) AssessRevision(ctx context.Context, revisionID string, request ResolveRequest) (RevisionPolicyAssessment, error) {
	if reader == nil || reader.store == nil {
		return RevisionPolicyAssessment{}, ErrRevisionUnavailable
	}
	return assessRevision(ctx, reader.store, revisionID, request)
}

func assessRevision(
	ctx context.Context,
	store RevisionStore,
	revisionID string,
	request ResolveRequest,
) (RevisionPolicyAssessment, error) {
	var record RevisionRecord
	var dataset Dataset
	var err error
	state := RevisionStateStaged
	if revisionID == "" {
		record, dataset, err = store.Active(ctx)
		state = RevisionStateActive
	} else {
		if !validAuditText(revisionID, 160) {
			return RevisionPolicyAssessment{}, ErrRevisionInvalid
		}
		record, dataset, err = store.Load(ctx, revisionID)
	}
	if err != nil {
		return RevisionPolicyAssessment{}, err
	}
	if revisionID != "" {
		active, _, activeErr := store.Active(ctx)
		if activeErr == nil && active.ID == record.ID {
			state = RevisionStateActive
		} else if activeErr != nil && !errors.Is(activeErr, ErrRevisionUnavailable) {
			return RevisionPolicyAssessment{}, fmt.Errorf("read active registry state: %w", activeErr)
		}
	}
	assessment, err := AssessDataset(dataset, request)
	if err != nil {
		return RevisionPolicyAssessment{}, err
	}
	return RevisionPolicyAssessment{Revision: record, State: state, Result: assessment}, nil
}

func (reader *ActiveReader) ActiveRevision(ctx context.Context) (RevisionRecord, error) {
	if reader == nil || reader.store == nil {
		return RevisionRecord{}, ErrRevisionUnavailable
	}
	record, _, err := reader.store.Active(ctx)
	return record, err
}

func (reader *ActiveReader) Close() {
	if reader == nil || reader.store == nil {
		return
	}
	if closer, ok := reader.store.(interface{ Close() }); ok {
		closer.Close()
	}
}
