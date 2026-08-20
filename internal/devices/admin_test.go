package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"testing"
	"time"
)

func TestAdminFleetManagerEnforcesGlobalAndMosqueRoles(t *testing.T) {
	t.Parallel()

	repository := newRecordingAdminRepository()
	manager := mustAdminFleetManager(t, repository)
	local := AdminPrincipal{
		ActorID:     "actor-mosque-admin-0001",
		Memberships: []AdminMembership{{MosqueID: "mosque-ulyanovsk-0001", Role: AdminRoleMosqueAdmin}},
	}
	service := AdminPrincipal{
		ActorID:     "actor-service-admin-0001",
		Memberships: []AdminMembership{{Role: AdminRoleServiceAdmin}},
	}
	viewer := AdminPrincipal{
		ActorID:     "actor-viewer-support-01",
		Memberships: []AdminMembership{{MosqueID: "mosque-ulyanovsk-0001", Role: AdminRoleViewerSupport}},
	}

	issued, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "install lobby display",
		RequestID: "request-admin-issue-0001", IdempotencyKey: "idem-admin-issue-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if err != nil || len(issued.Code) != 26 {
		t.Fatalf("local IssuePairing() = %#v, %v", issued, err)
	}
	retried, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "install lobby display",
		RequestID: "request-admin-issue-0002", IdempotencyKey: "idem-admin-issue-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if err != nil || retried != issued {
		t.Fatalf("idempotent IssuePairing() = %#v, %v; want %#v", retried, err, issued)
	}
	if repository.pairingCreates != 2 || repository.pairingsByIdempotency != 1 {
		t.Fatalf("idempotent repository calls=%d rows=%d", repository.pairingCreates, repository.pairingsByIdempotency)
	}
	rotated, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository:                   repository,
		Now:                          func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		IdempotencyKey:               bytes.Repeat([]byte{0x70}, sha256.Size),
		CompatibilityIdempotencyKeys: [][]byte{bytes.Repeat([]byte{0x69}, sha256.Size)},
	})
	if err != nil {
		t.Fatalf("NewAdminFleetManager(rotated) error = %v", err)
	}
	rotatedRetry, err := rotated.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "install lobby display",
		RequestID: "request-admin-issue-rotate", IdempotencyKey: "idem-admin-issue-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if err != nil || rotatedRetry != issued {
		t.Fatalf("rotated idempotent IssuePairing() = %#v, %v; want %#v", rotatedRetry, err, issued)
	}
	newKeyIssued, err := rotated.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "new key rollout",
		RequestID: "request-admin-new-key-001", IdempotencyKey: "idem-admin-new-key-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("new-key IssuePairing() error = %v", err)
	}
	stagedOld, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository:                   repository,
		Now:                          func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		IdempotencyKey:               bytes.Repeat([]byte{0x69}, sha256.Size),
		CompatibilityIdempotencyKeys: [][]byte{bytes.Repeat([]byte{0x70}, sha256.Size)},
	})
	if err != nil {
		t.Fatalf("NewAdminFleetManager(staged old) error = %v", err)
	}
	oldNodeRetry, err := stagedOld.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "new key rollout",
		RequestID: "request-admin-old-node-01", IdempotencyKey: "idem-admin-new-key-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if err != nil || oldNodeRetry != newKeyIssued {
		t.Fatalf("old-node idempotent IssuePairing() = %#v, %v; want %#v", oldNodeRetry, err, newKeyIssued)
	}

	_, err = manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "different request",
		RequestID: "request-admin-issue-0003", IdempotencyKey: "idem-admin-issue-0001",
		ExpiresIn: 10 * time.Minute,
	})
	if !errors.Is(err, ErrAdminIdempotencyConflict) {
		t.Fatalf("conflicting IssuePairing() error = %v", err)
	}

	if _, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-kazan-00000001", Reason: "cross mosque",
		RequestID: "request-admin-cross-001", IdempotencyKey: "idem-admin-cross-0001",
		ExpiresIn: 10 * time.Minute,
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("cross-mosque IssuePairing() error = %v", err)
	}
	if _, err := manager.IssuePairing(t.Context(), service, AdminIssuePairingCommand{
		MosqueID: "mosque-kazan-00000001", Reason: "global install",
		RequestID: "request-admin-global-01", IdempotencyKey: "idem-admin-global-0001",
		ExpiresIn: 10 * time.Minute,
	}); err != nil {
		t.Fatalf("service-admin IssuePairing() error = %v", err)
	}

	repository.devices = []FleetDevice{{
		DeviceID: "device-display-0001", MosqueID: "mosque-ulyanovsk-0001", Status: "active",
		CreatedAt: time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC),
	}}
	if devices, err := manager.ListDevices(t.Context(), viewer, "mosque-ulyanovsk-0001"); err != nil || len(devices) != 1 {
		t.Fatalf("viewer ListDevices() = %#v, %v", devices, err)
	}
	if _, err := manager.ListDevices(t.Context(), viewer, "mosque-kazan-00000001"); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("cross-mosque ListDevices() error = %v", err)
	}
	if err := manager.RevokeDevice(t.Context(), viewer, AdminRevokeDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: "device-display-0001", Reason: "viewer cannot revoke",
		RequestID: "request-admin-revoke-01", IdempotencyKey: "idem-admin-revoke-0001",
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("viewer RevokeDevice() error = %v", err)
	}
}

func TestAdminFleetManagerBindsAssignmentRevocationAndAuthentication(t *testing.T) {
	t.Parallel()

	repository := newRecordingAdminRepository()
	manager := mustAdminFleetManager(t, repository)
	principal := AdminPrincipal{
		ActorID:     "actor-mosque-admin-0001",
		Memberships: []AdminMembership{{MosqueID: "mosque-ulyanovsk-0001", Role: AdminRoleMosqueAdmin}},
	}
	repository.authenticated = principal
	authenticated, err := manager.AuthenticateAdmin(t.Context(), "admin-bearer-token-valid-0001")
	if err != nil || authenticated.ActorID != principal.ActorID || repository.authenticatedHash == ([sha256.Size]byte{}) {
		t.Fatalf("Authenticate() = %#v, %v", authenticated, err)
	}

	assignment, err := manager.AssignDevice(t.Context(), principal, AdminAssignDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: "device-display-0001",
		SnapshotID:     "synthetic-android-verification-v1",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
		SnapshotSHA256: "5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d",
		SigningKeyID:   "phase1-fixture-key-2026-08", SnapshotMosqueID: "mosque-ulyanovsk-0001",
		SnapshotTimezone: "Europe/Ulyanovsk", MinimumAppVersion: "0.2.0-shell",
		Reason: "assign approved verification snapshot", RequestID: "request-admin-assign-01",
		IdempotencyKey: "idem-admin-assign-0001",
	})
	if err != nil || assignment.ManifestVersion != 1 {
		t.Fatalf("AssignDevice() = %#v, %v", assignment, err)
	}
	retried, err := manager.AssignDevice(t.Context(), principal, repository.lastAssignCommand)
	if err != nil || retried != assignment {
		t.Fatalf("idempotent AssignDevice() = %#v, %v; want %#v", retried, err, assignment)
	}
	stored, err := manager.GetDeviceAssignment(t.Context(), "device-display-0001", "mosque-ulyanovsk-0001")
	if err != nil || stored != assignment {
		t.Fatalf("GetDeviceAssignment() = %#v, %v", stored, err)
	}
	if _, err := manager.GetDeviceAssignment(t.Context(), "device-display-0001", "mosque-kazan-00000001"); !errors.Is(err, ErrDeviceAssignmentNotFound) {
		t.Fatalf("cross-mosque GetDeviceAssignment() error = %v", err)
	}

	command := AdminRevokeDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: "device-display-0001", Reason: "device replaced",
		RequestID: "request-admin-revoke-01", IdempotencyKey: "idem-admin-revoke-0001",
	}
	if err := manager.RevokeDevice(t.Context(), principal, command); err != nil {
		t.Fatalf("RevokeDevice() error = %v", err)
	}
	command.RequestID = "request-admin-revoke-02"
	if err := manager.RevokeDevice(t.Context(), principal, command); err != nil {
		t.Fatalf("idempotent RevokeDevice() error = %v", err)
	}
	if repository.revocationRows != 1 {
		t.Fatalf("idempotent revocation rows = %d", repository.revocationRows)
	}
}

func TestAdminFleetManagerBindsBoundedRolloutCohort(t *testing.T) {
	t.Parallel()

	repository := newRecordingAdminRepository()
	manager := mustAdminFleetManager(t, repository)
	principal := AdminPrincipal{
		ActorID: "actor-mosque-admin-0001",
		Memberships: []AdminMembership{{
			MosqueID: "mosque-ulyanovsk-0001", Role: AdminRoleMosqueAdmin,
		}},
	}
	set := AdminSetRolloutGroupCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: "device-display-0001",
		RolloutGroup: "canary-group-0001", Reason: "select canary",
		RequestID: "request-group-set-0001", IdempotencyKey: "idem-group-set-0001",
	}
	if err := manager.SetDeviceRolloutGroup(t.Context(), principal, set); err != nil {
		t.Fatalf("SetDeviceRolloutGroup() error = %v", err)
	}

	command := AdminAssignRolloutGroupCommand{
		MosqueID: "mosque-ulyanovsk-0001", RolloutGroup: "canary-group-0001",
		SnapshotID:     "synthetic-android-verification-v1",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
		SnapshotSHA256: "5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d",
		SigningKeyID:   "phase1-fixture-key-2026-08", SnapshotMosqueID: "mosque-ulyanovsk-0001",
		SnapshotTimezone: "Europe/Ulyanovsk", MinimumAppVersion: "0.2.0-shell",
		Reason: "canary approved snapshot", RequestID: "request-group-assign-0001",
		IdempotencyKey: "idem-group-assign-0001",
	}
	result, err := manager.AssignRolloutGroup(t.Context(), principal, command)
	if err != nil {
		t.Fatalf("AssignRolloutGroup() error = %v", err)
	}
	if result.RolloutGroup != command.RolloutGroup || result.SnapshotID != command.SnapshotID || result.DeviceCount != 2 {
		t.Fatalf("AssignRolloutGroup() = %#v", result)
	}

	cross := command
	cross.MosqueID = "mosque-kazan-00000001"
	cross.SnapshotMosqueID = cross.MosqueID
	if _, err := manager.AssignRolloutGroup(t.Context(), principal, cross); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("cross-mosque AssignRolloutGroup() error = %v", err)
	}
	invalid := command
	invalid.RolloutGroup = "x"
	if _, err := manager.AssignRolloutGroup(t.Context(), principal, invalid); !errors.Is(err, ErrInvalidAdminRequest) {
		t.Fatalf("invalid AssignRolloutGroup() error = %v", err)
	}
}

func mustAdminFleetManager(t *testing.T, repository AdminFleetRepository) *AdminFleetManager {
	t.Helper()
	manager, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository:     repository,
		Now:            func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		IdempotencyKey: bytes.Repeat([]byte{0x69}, sha256.Size),
	})
	if err != nil {
		t.Fatalf("NewAdminFleetManager() error = %v", err)
	}
	return manager
}

type recordingAdminRepository struct {
	authenticated           AdminPrincipal
	authenticatedHash       [sha256.Size]byte
	devices                 []FleetDevice
	pairingCreates          int
	pairingsByIdempotency   int
	pairings                map[[sha256.Size]byte]adminPairingFixture
	assignments             map[string]DeviceAssignment
	assignmentRequests      map[[sha256.Size]byte]DeviceAssignment
	assignmentRequestHashes map[[sha256.Size]byte][sha256.Size]byte
	revocations             map[[sha256.Size]byte][sha256.Size]byte
	revocationRows          int
	lastAssignCommand       AdminAssignDeviceCommand
	rolloutResult           RolloutAssignmentResult
}

type adminPairingFixture struct {
	record      PairingRecord
	requestHash [sha256.Size]byte
}

func newRecordingAdminRepository() *recordingAdminRepository {
	return &recordingAdminRepository{
		pairings: make(map[[sha256.Size]byte]adminPairingFixture), assignments: make(map[string]DeviceAssignment),
		assignmentRequests: make(map[[sha256.Size]byte]DeviceAssignment), revocations: make(map[[sha256.Size]byte][sha256.Size]byte),
		assignmentRequestHashes: make(map[[sha256.Size]byte][sha256.Size]byte),
		rolloutResult: RolloutAssignmentResult{
			RolloutGroup: "canary-group-0001", SnapshotID: "synthetic-android-verification-v1", DeviceCount: 2,
			Assignments: []DeviceAssignment{
				{DeviceID: "device-display-0001", ManifestVersion: 1, SnapshotID: "synthetic-android-verification-v1"},
				{DeviceID: "device-display-0002", ManifestVersion: 1, SnapshotID: "synthetic-android-verification-v1"},
			},
		},
	}
}

func (repository *recordingAdminRepository) ReadAdminAssignmentRetry(
	_ context.Context,
	_ AdminRepositoryScope,
	idempotencyHash, requestHash [sha256.Size]byte,
) (DeviceAssignment, bool, error) {
	assignment, found := repository.assignmentRequests[idempotencyHash]
	if !found {
		return DeviceAssignment{}, false, nil
	}
	if repository.assignmentRequestHashes[idempotencyHash] != requestHash {
		return DeviceAssignment{}, false, ErrAdminIdempotencyConflict
	}
	return assignment, true, nil
}

func (repository *recordingAdminRepository) AuthenticateAdmin(_ context.Context, tokenHash [sha256.Size]byte) (AdminPrincipal, error) {
	repository.authenticatedHash = tokenHash
	return repository.authenticated, nil
}

func (repository *recordingAdminRepository) CreateAdminPairing(_ context.Context, mutation AdminPairingMutation) (PairingRecord, error) {
	repository.pairingCreates++
	if existing, found := repository.pairings[mutation.IdempotencyHash]; found {
		if existing.requestHash != mutation.RequestHash {
			return PairingRecord{}, ErrAdminIdempotencyConflict
		}
		return existing.record, nil
	}
	repository.pairings[mutation.IdempotencyHash] = adminPairingFixture{record: mutation.Record, requestHash: mutation.RequestHash}
	repository.pairingsByIdempotency++
	return mutation.Record, nil
}

func (repository *recordingAdminRepository) ListAdminDevices(_ context.Context, _ AdminRepositoryScope) ([]FleetDevice, error) {
	return append([]FleetDevice(nil), repository.devices...), nil
}

func (repository *recordingAdminRepository) RevokeAdminDevice(_ context.Context, mutation AdminRevocationMutation) error {
	if requestHash, found := repository.revocations[mutation.IdempotencyHash]; found {
		if requestHash != mutation.RequestHash {
			return ErrAdminIdempotencyConflict
		}
		return nil
	}
	repository.revocations[mutation.IdempotencyHash] = mutation.RequestHash
	repository.revocationRows++
	return nil
}

func (repository *recordingAdminRepository) AssignAdminDevice(_ context.Context, mutation AdminAssignmentMutation) (DeviceAssignment, error) {
	repository.lastAssignCommand = mutation.Command
	if existing, found := repository.assignmentRequests[mutation.IdempotencyHash]; found {
		return existing, nil
	}
	assignment := mutation.Assignment
	if current, found := repository.assignments[assignment.DeviceID]; found {
		assignment.ManifestVersion = current.ManifestVersion + 1
	} else {
		assignment.ManifestVersion = 1
	}
	repository.assignments[assignment.DeviceID] = assignment
	repository.assignmentRequests[mutation.IdempotencyHash] = assignment
	repository.assignmentRequestHashes[mutation.IdempotencyHash] = mutation.RequestHash
	return assignment, nil
}

func (repository *recordingAdminRepository) GetDeviceAssignment(_ context.Context, deviceID, mosqueID string) (DeviceAssignment, error) {
	assignment, found := repository.assignments[deviceID]
	if !found || mosqueID != "mosque-ulyanovsk-0001" {
		return DeviceAssignment{}, ErrDeviceAssignmentNotFound
	}
	return assignment, nil
}

func (repository *recordingAdminRepository) SetAdminDeviceRolloutGroup(_ context.Context, _ AdminRolloutGroupMutation) error {
	return nil
}

func (repository *recordingAdminRepository) ReadAdminRolloutAssignmentRetry(
	_ context.Context,
	_ AdminRepositoryScope,
	_, _ [sha256.Size]byte,
) (RolloutAssignmentResult, bool, error) {
	return RolloutAssignmentResult{}, false, nil
}

func (repository *recordingAdminRepository) AssignAdminRolloutGroup(
	_ context.Context,
	_ AdminRolloutAssignmentMutation,
) (RolloutAssignmentResult, error) {
	return repository.rolloutResult, nil
}
