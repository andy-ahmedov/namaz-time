package setupbundle

import (
	"context"
	"fmt"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

// admissionStore is a private one-shot staging area. It does not authenticate
// anything: all admission decisions are made by PersistentService and the
// concrete ArtifactReferenceVerifier. No interface injection is exported.
type admissionStore struct {
	record     registry.RevisionRecord
	dataset    registry.Dataset
	activation *registry.ActivationRecord
}

func (s *admissionStore) Stage(ctx context.Context, r registry.RevisionRecord, d registry.Dataset) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if s.record.ID != "" {
		return registry.ErrRevisionConflict
	}
	s.record, s.dataset = r, d
	return nil
}
func (s *admissionStore) Load(ctx context.Context, id string) (registry.RevisionRecord, registry.Dataset, error) {
	if err := ctx.Err(); err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, err
	}
	if s.record.ID == "" || s.record.ID != id {
		return registry.RevisionRecord{}, registry.Dataset{}, registry.ErrRevisionUnavailable
	}
	return s.record, s.dataset, nil
}
func (s *admissionStore) Activate(ctx context.Context, a registry.ActivationRecord) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if s.activation != nil || a.RevisionID != s.record.ID || a.PreviousRevisionID != "" || a.Rollback {
		return registry.ErrRevisionConflict
	}
	s.activation = &a
	return nil
}
func (s *admissionStore) Active(ctx context.Context) (registry.RevisionRecord, registry.Dataset, error) {
	if s.activation == nil {
		return registry.RevisionRecord{}, registry.Dataset{}, registry.ErrRevisionUnavailable
	}
	return s.Load(ctx, s.activation.RevisionID)
}

func admit(ctx context.Context, in inputs, config Config, now time.Time) (registry.ActivationRecord, error) {
	verifier, err := registry.NewArtifactReferenceVerifier(in.artifacts)
	if err != nil {
		return registry.ActivationRecord{}, err
	}
	store := &admissionStore{}
	service, err := registry.NewPersistentService(registry.PersistentServiceConfig{Store: store, ApprovalVerifier: verifier, SnapshotVerifier: verifier, Now: func() time.Time { return now }})
	if err != nil {
		return registry.ActivationRecord{}, err
	}
	if err = service.Stage(ctx, in.record, in.dataset); err != nil {
		return registry.ActivationRecord{}, fmt.Errorf("stage local registry: %w", err)
	}
	if err = service.Activate(ctx, in.record.ID, config.ActorID, config.Reason); err != nil {
		return registry.ActivationRecord{}, fmt.Errorf("admit local registry: %w", err)
	}
	if store.activation == nil {
		return registry.ActivationRecord{}, fmt.Errorf("local registry was not admitted")
	}
	a := *store.activation
	if len(a.Approvals) != len(in.artifacts.Approvals) || len(a.Snapshots) != len(in.artifacts.Snapshots) {
		return registry.ActivationRecord{}, fmt.Errorf("artifact manifest contains unused approval or snapshot references")
	}
	return a, nil
}
