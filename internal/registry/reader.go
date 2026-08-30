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
