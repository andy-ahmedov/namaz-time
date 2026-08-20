//go:build integration

package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

func TestPostgresAdminFleetLifecycleAndIsolation(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("connect PostgreSQL: %v", err)
	}
	defer pool.Close()
	repository := NewPostgresPairingRepository(pool)
	if err := repository.MigrateTo(ctx, 0); err != nil {
		t.Fatalf("reset migrations: %v", err)
	}
	if err := repository.MigrateUp(ctx); err != nil {
		t.Fatalf("apply migrations: %v", err)
	}
	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = repository.MigrateTo(cleanupCtx, 0)
	})

	seedMosque(t, pool, "mosque-ulyanovsk-0001", "Second Cathedral Mosque", "Europe/Ulyanovsk")
	seedMosque(t, pool, "mosque-kazan-00000001", "Synthetic Kazan Mosque", "Europe/Moscow")
	serviceToken := "service-admin-token-integration-0001"
	localToken := "local-admin-token-integration-000001"
	viewerToken := "viewer-token-integration-0000000001"
	seedAdminActor(t, pool, "actor-service-admin-0001", serviceToken, AdminRoleServiceAdmin, "")
	seedAdminActor(t, pool, "actor-mosque-admin-0001", localToken, AdminRoleMosqueAdmin, "mosque-ulyanovsk-0001")
	seedAdminActor(t, pool, "actor-viewer-support-01", viewerToken, AdminRoleViewerSupport, "mosque-ulyanovsk-0001")
	assertAdminPlaintextAbsent(t, pool, serviceToken, localToken, viewerToken)

	manager, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository:     repository,
		Now:            func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		IdempotencyKey: bytes.Repeat([]byte{0x49}, sha256.Size),
	})
	if err != nil {
		t.Fatalf("NewAdminFleetManager() error = %v", err)
	}
	local, err := manager.AuthenticateAdmin(t.Context(), localToken)
	if err != nil {
		t.Fatalf("AuthenticateAdmin(local) error = %v", err)
	}
	service, err := manager.AuthenticateAdmin(t.Context(), serviceToken)
	if err != nil {
		t.Fatalf("AuthenticateAdmin(service) error = %v", err)
	}
	viewer, err := manager.AuthenticateAdmin(t.Context(), viewerToken)
	if err != nil {
		t.Fatalf("AuthenticateAdmin(viewer) error = %v", err)
	}
	if _, err := manager.AuthenticateAdmin(t.Context(), "unknown-admin-token-integration"); !errors.Is(err, ErrAdminUnauthorized) {
		t.Fatalf("AuthenticateAdmin(unknown) error = %v", err)
	}
	forgedLocal := local
	forgedLocal.Memberships = []AdminMembership{{MosqueID: "mosque-kazan-00000001", Role: AdminRoleMosqueAdmin}}
	if _, err := manager.IssuePairing(t.Context(), forgedLocal, AdminIssuePairingCommand{
		MosqueID: "mosque-kazan-00000001", Reason: "forged in-memory scope",
		RequestID: "request-admin-forged-001", IdempotencyKey: "idem-admin-forged-0001", ExpiresIn: 10 * time.Minute,
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("repository accepted forged local scope: %v", err)
	}
	if _, err := manager.AssignRolloutGroup(t.Context(), forgedLocal, AdminAssignRolloutGroupCommand{
		MosqueID: "mosque-kazan-00000001", RolloutGroup: "canary-group-0001",
		SnapshotID:     "synthetic-rollout-verification-v2",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-rollout-verification-v2",
		SnapshotSHA256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		SigningKeyID:   "phase1-fixture-key-2026-08", SnapshotMosqueID: "mosque-kazan-00000001",
		SnapshotTimezone: "Europe/Moscow", Reason: "forged rollout scope",
		RequestID: "request-admin-forged-rollout", IdempotencyKey: "idem-admin-forged-rollout",
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("repository accepted forged rollout scope: %v", err)
	}
	forgedViewer := viewer
	forgedViewer.Memberships = []AdminMembership{{MosqueID: "mosque-ulyanovsk-0001", Role: AdminRoleMosqueAdmin}}
	if _, err := manager.IssuePairing(t.Context(), forgedViewer, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "forged viewer write",
		RequestID: "request-admin-forged-002", IdempotencyKey: "idem-admin-forged-0002", ExpiresIn: 10 * time.Minute,
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("repository accepted forged viewer role: %v", err)
	}
	if _, err := pool.Exec(t.Context(), `UPDATE admin_actors SET status = 'suspended' WHERE id = $1`, local.ActorID); err != nil {
		t.Fatalf("suspend local admin: %v", err)
	}
	if _, err := manager.AuthenticateAdmin(t.Context(), localToken); !errors.Is(err, ErrAdminUnauthorized) {
		t.Fatalf("AuthenticateAdmin(suspended) error = %v", err)
	}
	if _, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "stale suspended principal",
		RequestID: "request-admin-suspend-01", IdempotencyKey: "idem-admin-suspend-0001", ExpiresIn: 10 * time.Minute,
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("stale suspended principal IssuePairing() error = %v", err)
	}
	if _, err := pool.Exec(t.Context(), `UPDATE admin_actors SET status = 'active' WHERE id = $1`, local.ActorID); err != nil {
		t.Fatalf("reactivate local admin: %v", err)
	}

	command := AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "install lobby display",
		RequestID: "request-admin-issue-0001", IdempotencyKey: "idem-admin-issue-0001", ExpiresIn: 10 * time.Minute,
	}
	issued, err := manager.IssuePairing(t.Context(), local, command)
	if err != nil {
		t.Fatalf("IssuePairing(local) error = %v", err)
	}
	command.RequestID = "request-admin-issue-0002"
	retried, err := manager.IssuePairing(t.Context(), local, command)
	if err != nil || retried != issued {
		t.Fatalf("idempotent IssuePairing() = %#v, %v; want %#v", retried, err, issued)
	}
	command.Reason = "conflicting retry"
	if _, err := manager.IssuePairing(t.Context(), local, command); !errors.Is(err, ErrAdminIdempotencyConflict) {
		t.Fatalf("conflicting IssuePairing() error = %v", err)
	}
	expiredCommand := AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "expired retry proof",
		RequestID: "request-admin-expired-01", IdempotencyKey: "idem-admin-expired-0001", ExpiresIn: 10 * time.Minute,
	}
	expiredIdempotencyHash := hashAdminRequest("idempotency", local.ActorID, "issue_pairing", expiredCommand.IdempotencyKey)
	expiredRequestHash := hashAdminRequest("issue_pairing", expiredCommand.MosqueID, expiredCommand.Reason, expiredCommand.ExpiresIn.String())
	if _, err := pool.Exec(t.Context(), `
		INSERT INTO admin_requests (
			idempotency_hash, request_hash, actor_id, mosque_id, operation, resource_id,
			created_at, expires_at
		) VALUES ($1, $2, $3, $4, 'issue_pairing', 'expired-resource-0001',
		          '2026-08-18T12:00:00Z', '2026-08-19T12:00:00Z')`,
		expiredIdempotencyHash[:], expiredRequestHash[:], local.ActorID, expiredCommand.MosqueID,
	); err != nil {
		t.Fatalf("seed expired idempotency evidence: %v", err)
	}
	if _, err := manager.IssuePairing(t.Context(), local, expiredCommand); !errors.Is(err, ErrAdminIdempotencyConflict) {
		t.Fatalf("expired IssuePairing() error = %v", err)
	}
	if _, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-kazan-00000001", Reason: "cross scope", RequestID: "request-admin-cross-001",
		IdempotencyKey: "idem-admin-cross-0001", ExpiresIn: 10 * time.Minute,
	}); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("cross-mosque IssuePairing() error = %v", err)
	}
	if _, err := manager.IssuePairing(t.Context(), service, AdminIssuePairingCommand{
		MosqueID: "mosque-kazan-00000001", Reason: "global scope", RequestID: "request-admin-global-01",
		IdempotencyKey: "idem-admin-global-0001", ExpiresIn: 10 * time.Minute,
	}); err != nil {
		t.Fatalf("service-admin IssuePairing() error = %v", err)
	}

	t.Run("concurrent identical issue has one resource and response", func(t *testing.T) {
		base := AdminIssuePairingCommand{
			MosqueID: "mosque-ulyanovsk-0001", Reason: "concurrent admin retry",
			IdempotencyKey: "idem-admin-concurrent-01", ExpiresIn: 10 * time.Minute,
		}
		var wait sync.WaitGroup
		results := make(chan IssuedPairing, 8)
		errorsFound := make(chan error, 8)
		for index := 0; index < 8; index++ {
			wait.Add(1)
			go func(index int) {
				defer wait.Done()
				command := base
				command.RequestID = fmt.Sprintf("request-admin-concurrent-%02d", index)
				result, issueErr := manager.IssuePairing(t.Context(), local, command)
				if issueErr != nil {
					errorsFound <- issueErr
					return
				}
				results <- result
			}(index)
		}
		wait.Wait()
		close(results)
		close(errorsFound)
		for issueErr := range errorsFound {
			t.Fatalf("concurrent IssuePairing() error = %v", issueErr)
		}
		var first IssuedPairing
		for result := range results {
			if first.DeviceID == "" {
				first = result
			} else if result != first {
				t.Fatalf("concurrent result = %#v, want %#v", result, first)
			}
		}
		var devicesCount, auditCount, requestCount int
		if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM devices WHERE id = $1`, first.DeviceID).Scan(&devicesCount); err != nil {
			t.Fatalf("count concurrent devices: %v", err)
		}
		if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM audit_events WHERE entity_id IN (SELECT id FROM pairing_codes WHERE device_id = $1)`, first.DeviceID).Scan(&auditCount); err != nil {
			t.Fatalf("count concurrent audit: %v", err)
		}
		if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM admin_requests WHERE resource_id IN (SELECT id FROM pairing_codes WHERE device_id = $1)`, first.DeviceID).Scan(&requestCount); err != nil {
			t.Fatalf("count concurrent requests: %v", err)
		}
		if devicesCount != 1 || auditCount != 1 || requestCount != 1 {
			t.Fatalf("concurrent rows: devices=%d audit=%d requests=%d", devicesCount, auditCount, requestCount)
		}
	})

	pairingManager := newIntegrationPairingManager(
		t, repository,
		func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		bytes.Repeat([]byte{0x51}, sha256.Size),
		PairingRateLimits{Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 20, CodeAttempts: 20},
	)
	paired, err := pairingManager.Pair(t.Context(), PairingAttempt{
		Code: issued.Code, Device: DeviceInfo{AppVersion: "1.0.0", OSVersion: "35", Model: "Lobby TV"},
		SourceAddress: "192.0.2.80", RequestID: "request-admin-pair-0001",
	})
	if err != nil {
		t.Fatalf("Pair(admin-issued code) error = %v", err)
	}
	if err := pairingManager.Heartbeat(t.Context(), DevicePrincipal{DeviceID: paired.DeviceID, Mosque: paired.Mosque}, DeviceHeartbeatReport{
		SentAt:     time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC),
		AppVersion: "1.1.0", OSVersion: "36", Model: "Lobby TV",
		ActiveSnapshotID: "snapshot-device-reported-01", SyncStatus: DeviceSyncStatusOK,
		CoverageDaysRemaining: 30, StorageHealth: DeviceHealthOK, MemoryHealth: DeviceHealthOK,
		BootMode: DeviceBootModeBestEffort, KioskMode: DeviceKioskModeNone,
	}); err != nil {
		t.Fatalf("Heartbeat(admin fleet projection) error = %v", err)
	}

	devices, err := manager.ListDevices(t.Context(), viewer, "mosque-ulyanovsk-0001")
	foundIssued := false
	for _, device := range devices {
		if device.DeviceID == issued.DeviceID {
			foundIssued = device.LastSeenAt != nil && device.ReportedSnapshotID == "snapshot-device-reported-01" &&
				device.SyncStatus == DeviceSyncStatusOK && device.CoverageDaysRemaining != nil &&
				*device.CoverageDaysRemaining == 30
		}
	}
	if err != nil || len(devices) != 2 || !foundIssued {
		t.Fatalf("viewer ListDevices() = %#v, %v", devices, err)
	}
	if _, err := manager.ListDevices(t.Context(), viewer, "mosque-kazan-00000001"); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("cross-mosque ListDevices() error = %v", err)
	}
	if _, err := manager.ListDevices(t.Context(), service, "mosque-does-not-exist"); !errors.Is(err, ErrAdminResourceNotFound) {
		t.Fatalf("missing-mosque ListDevices() error = %v", err)
	}

	assignmentCommand := AdminAssignDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: issued.DeviceID,
		SnapshotID:     "synthetic-android-verification-v1",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
		SnapshotSHA256: "5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d",
		SigningKeyID:   "phase1-fixture-key-2026-08", SnapshotMosqueID: "mosque-ulyanovsk-0001",
		SnapshotTimezone: "Europe/Ulyanovsk", Reason: "assign verified snapshot",
		RequestID: "request-admin-assign-01", IdempotencyKey: "idem-admin-assign-0001",
	}
	assignment, err := manager.AssignDevice(t.Context(), local, assignmentCommand)
	if err != nil || assignment.ManifestVersion != 1 {
		t.Fatalf("AssignDevice() = %#v, %v", assignment, err)
	}
	assignmentCommand.RequestID = "request-admin-assign-02"
	retriedAssignment, err := manager.AssignDevice(t.Context(), local, assignmentCommand)
	if err != nil || retriedAssignment != assignment {
		t.Fatalf("idempotent AssignDevice() = %#v, %v", retriedAssignment, err)
	}
	secondAssignmentCommand := assignmentCommand
	secondAssignmentCommand.MinimumAppVersion = "0.3.0-shell"
	secondAssignmentCommand.Reason = "raise minimum application version"
	secondAssignmentCommand.RequestID = "request-admin-assign-03"
	secondAssignmentCommand.IdempotencyKey = "idem-admin-assign-0002"
	secondAssignment, err := manager.AssignDevice(t.Context(), local, secondAssignmentCommand)
	if err != nil || secondAssignment.ManifestVersion != 2 {
		t.Fatalf("second AssignDevice() = %#v, %v", secondAssignment, err)
	}
	assertAssignmentAuditChain(t, pool, issued.DeviceID, assignment, secondAssignment)
	assignmentCommand.RequestID = "request-admin-assign-04"
	historicalRetry, err := manager.AssignDevice(t.Context(), local, assignmentCommand)
	if err != nil || historicalRetry != assignment {
		t.Fatalf("historical idempotent AssignDevice() = %#v, %v; want %#v", historicalRetry, err, assignment)
	}
	if _, err := manager.GetDeviceAssignment(t.Context(), issued.DeviceID, "mosque-kazan-00000001"); !errors.Is(err, ErrDeviceAssignmentNotFound) {
		t.Fatalf("cross-mosque GetDeviceAssignment() error = %v", err)
	}

	canaryPeer, err := manager.IssuePairing(t.Context(), local, AdminIssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", Reason: "prepare second canary",
		RequestID: "request-canary-peer-0001", IdempotencyKey: "idem-canary-peer-0001", ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("IssuePairing(canary peer) error = %v", err)
	}
	for index, deviceID := range []string{issued.DeviceID, canaryPeer.DeviceID} {
		if err := manager.SetDeviceRolloutGroup(t.Context(), local, AdminSetRolloutGroupCommand{
			MosqueID: "mosque-ulyanovsk-0001", DeviceID: deviceID, RolloutGroup: "canary-group-0001",
			Reason: "select bounded canary", RequestID: fmt.Sprintf("request-canary-set-%04d", index),
			IdempotencyKey: fmt.Sprintf("idem-canary-set-%04d", index),
		}); err != nil {
			t.Fatalf("SetDeviceRolloutGroup(%s) error = %v", deviceID, err)
		}
	}
	rolloutCommand := AdminAssignRolloutGroupCommand{
		MosqueID: "mosque-ulyanovsk-0001", RolloutGroup: "canary-group-0001",
		SnapshotID:     "synthetic-rollout-verification-v2",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-rollout-verification-v2",
		SnapshotSHA256: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		SigningKeyID:   "phase1-fixture-key-2026-08", SnapshotMosqueID: "mosque-ulyanovsk-0001",
		SnapshotTimezone: "Europe/Ulyanovsk", Reason: "canary verified snapshot",
		RequestID: "request-canary-assign-0001", IdempotencyKey: "idem-canary-assign-0001",
	}
	rollout, err := manager.AssignRolloutGroup(t.Context(), local, rolloutCommand)
	if err != nil || rollout.DeviceCount != 2 || rollout.Assignments[0].DeviceID > rollout.Assignments[1].DeviceID {
		t.Fatalf("AssignRolloutGroup() = %#v, %v", rollout, err)
	}
	rolloutCommand.RequestID = "request-canary-assign-0002"
	retriedRollout, err := manager.AssignRolloutGroup(t.Context(), local, rolloutCommand)
	if err != nil || !rolloutResultsEqual(retriedRollout, rollout) {
		t.Fatalf("idempotent AssignRolloutGroup() = %#v, %v; want %#v", retriedRollout, err, rollout)
	}
	rollbackCommand := rolloutCommand
	rollbackCommand.SnapshotID = assignment.SnapshotID
	rollbackCommand.SnapshotURL = assignment.SnapshotURL
	rollbackCommand.SnapshotSHA256 = assignment.SnapshotSHA256
	rollbackCommand.SigningKeyID = assignment.SigningKeyID
	rollbackCommand.Reason = "rollback canary to last-known-good"
	rollbackCommand.RequestID = "request-canary-rollback-01"
	rollbackCommand.IdempotencyKey = "idem-canary-rollback-0001"
	rollback, err := manager.AssignRolloutGroup(t.Context(), local, rollbackCommand)
	if err != nil || rollback.DeviceCount != 2 {
		t.Fatalf("AssignRolloutGroup(rollback) = %#v, %v", rollback, err)
	}
	versions := make(map[string]int64)
	for _, rolledBack := range rollback.Assignments {
		versions[rolledBack.DeviceID] = rolledBack.ManifestVersion
	}
	if versions[issued.DeviceID] != 4 || versions[canaryPeer.DeviceID] != 2 {
		t.Fatalf("rollback manifest versions = %#v", versions)
	}
	rolloutIssued := rolloutAssignmentForDevice(t, rollout, issued.DeviceID)
	rollbackIssued := rolloutAssignmentForDevice(t, rollback, issued.DeviceID)
	assertRolloutAuditTransition(t, pool, issued.DeviceID, "canary verified snapshot", secondAssignment, rolloutIssued)
	assertRolloutAuditTransition(t, pool, issued.DeviceID, "rollback canary to last-known-good", rolloutIssued, rollbackIssued)
	concurrentCommands := []AdminAssignRolloutGroupCommand{rolloutCommand, rolloutCommand}
	for index := range concurrentCommands {
		concurrentCommands[index].SnapshotID = fmt.Sprintf("synthetic-concurrent-rollout-%02d", index)
		concurrentCommands[index].SnapshotURL = "https://api.example.invalid/v1/snapshots/" + concurrentCommands[index].SnapshotID
		concurrentCommands[index].SnapshotSHA256 = strings.Repeat(fmt.Sprint(index+1), 64)
		concurrentCommands[index].Reason = fmt.Sprintf("concurrent canary %d", index)
		concurrentCommands[index].RequestID = fmt.Sprintf("request-concurrent-rollout-%02d", index)
		concurrentCommands[index].IdempotencyKey = fmt.Sprintf("idem-concurrent-rollout-%02d", index)
	}
	var rolloutWait sync.WaitGroup
	concurrentResults := make(chan RolloutAssignmentResult, len(concurrentCommands))
	concurrentErrors := make(chan error, len(concurrentCommands))
	for _, concurrentCommand := range concurrentCommands {
		rolloutWait.Add(1)
		go func(command AdminAssignRolloutGroupCommand) {
			defer rolloutWait.Done()
			result, assignErr := manager.AssignRolloutGroup(t.Context(), local, command)
			if assignErr != nil {
				concurrentErrors <- assignErr
				return
			}
			concurrentResults <- result
		}(concurrentCommand)
	}
	rolloutWait.Wait()
	close(concurrentResults)
	close(concurrentErrors)
	for assignErr := range concurrentErrors {
		t.Fatalf("concurrent AssignRolloutGroup() error = %v", assignErr)
	}
	if len(concurrentResults) != len(concurrentCommands) {
		t.Fatalf("concurrent rollout results = %d", len(concurrentResults))
	}
	var finalIssuedVersion, finalPeerVersion int64
	if err := pool.QueryRow(t.Context(), `
		SELECT manifest_version FROM device_assignments WHERE device_id = $1`, issued.DeviceID).Scan(&finalIssuedVersion); err != nil {
		t.Fatalf("read concurrent issued version: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT manifest_version FROM device_assignments WHERE device_id = $1`, canaryPeer.DeviceID).Scan(&finalPeerVersion); err != nil {
		t.Fatalf("read concurrent peer version: %v", err)
	}
	if finalIssuedVersion != 6 || finalPeerVersion != 4 {
		t.Fatalf("concurrent rollout versions = %d/%d", finalIssuedVersion, finalPeerVersion)
	}
	listedAfterRollout, err := manager.ListDevices(t.Context(), viewer, "mosque-ulyanovsk-0001")
	if err != nil {
		t.Fatalf("ListDevices(after rollout) error = %v", err)
	}
	var grouped int
	for _, device := range listedAfterRollout {
		if device.RolloutGroup == "canary-group-0001" {
			grouped++
		}
	}
	if grouped != 2 {
		t.Fatalf("canary devices in fleet projection = %d", grouped)
	}

	if _, err := pool.Exec(t.Context(), `
		INSERT INTO devices (id, mosque_id, status, rollout_group, created_at)
		SELECT 'device-overflow-' || lpad(value::text, 4, '0'),
		       'mosque-ulyanovsk-0001', 'pending', 'overflow-group-01', clock_timestamp()
		FROM generate_series(1, 101) AS value`); err != nil {
		t.Fatalf("seed oversized rollout group: %v", err)
	}
	overflow := rolloutCommand
	overflow.RolloutGroup = "overflow-group-01"
	overflow.RequestID = "request-overflow-assign-01"
	overflow.IdempotencyKey = "idem-overflow-assign-0001"
	if _, err := manager.AssignRolloutGroup(t.Context(), local, overflow); !errors.Is(err, ErrRolloutGroupTooLarge) {
		t.Fatalf("oversized AssignRolloutGroup() error = %v", err)
	}
	var overflowAssignments int
	if err := pool.QueryRow(t.Context(), `
		SELECT count(*) FROM device_assignments a
		JOIN devices d ON d.id = a.device_id
		WHERE d.rollout_group = 'overflow-group-01'`).Scan(&overflowAssignments); err != nil {
		t.Fatalf("count oversized rollout assignments: %v", err)
	}
	if overflowAssignments != 0 {
		t.Fatalf("oversized rollout partially assigned %d devices", overflowAssignments)
	}

	revoke := AdminRevokeDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: issued.DeviceID, Reason: "device replaced",
		RequestID: "request-admin-revoke-01", IdempotencyKey: "idem-admin-revoke-0001",
	}
	if err := manager.RevokeDevice(t.Context(), local, revoke); err != nil {
		t.Fatalf("RevokeDevice() error = %v", err)
	}
	revoke.RequestID = "request-admin-revoke-02"
	if err := manager.RevokeDevice(t.Context(), local, revoke); err != nil {
		t.Fatalf("idempotent RevokeDevice() error = %v", err)
	}
	assertAdminAuditEvidence(t, pool, local.ActorID, map[string]string{
		"device.pairing_issued": "install lobby display",
		"device.assigned":       "assign verified snapshot",
		"device.revoked":        "device replaced",
	})
	var rolloutGroupAuditCount int
	if err := pool.QueryRow(t.Context(), `
		SELECT count(*) FROM audit_events
		WHERE actor_id = $1 AND action = 'device.rollout_group_changed'
		  AND reason = 'select bounded canary'`, local.ActorID).Scan(&rolloutGroupAuditCount); err != nil {
		t.Fatalf("inspect rollout group audit: %v", err)
	}
	if rolloutGroupAuditCount != 2 {
		t.Fatalf("rollout group audit count = %d", rolloutGroupAuditCount)
	}
	assertAdminRequestsAreAppendOnly(t, pool)
}

func rolloutResultsEqual(left, right RolloutAssignmentResult) bool {
	if left.RolloutGroup != right.RolloutGroup || left.SnapshotID != right.SnapshotID ||
		left.DeviceCount != right.DeviceCount || len(left.Assignments) != len(right.Assignments) {
		return false
	}
	for index := range left.Assignments {
		if left.Assignments[index] != right.Assignments[index] {
			return false
		}
	}
	return true
}

func rolloutAssignmentForDevice(t *testing.T, result RolloutAssignmentResult, deviceID string) DeviceAssignment {
	t.Helper()
	for _, assignment := range result.Assignments {
		if assignment.DeviceID == deviceID {
			return assignment
		}
	}
	t.Fatalf("rollout result has no assignment for %s", deviceID)
	return DeviceAssignment{}
}

func assertRolloutAuditTransition(
	t *testing.T,
	pool *pgxpool.Pool,
	deviceID, reason string,
	before, after DeviceAssignment,
) {
	t.Helper()
	var beforeHash, afterHash []byte
	if err := pool.QueryRow(t.Context(), `
		SELECT before_hash, after_hash FROM audit_events
		WHERE action = 'device.assigned' AND entity_id = $1 AND reason = $2`, deviceID, reason).Scan(
		&beforeHash, &afterHash,
	); err != nil {
		t.Fatalf("read rollout audit transition %s: %v", reason, err)
	}
	wantBefore := hashDeviceAssignment(before, "mosque-ulyanovsk-0001")
	wantAfter := hashDeviceAssignment(after, "mosque-ulyanovsk-0001")
	if !bytes.Equal(beforeHash, wantBefore[:]) || !bytes.Equal(afterHash, wantAfter[:]) {
		t.Fatalf("rollout audit %s = %x/%x, want %x/%x", reason, beforeHash, afterHash, wantBefore, wantAfter)
	}
}

func assertAdminRequestsAreAppendOnly(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()
	if _, err := pool.Exec(t.Context(), `UPDATE admin_requests SET operation = 'revoke_device'`); err == nil {
		t.Fatal("admin_requests accepted an UPDATE")
	}
	if _, err := pool.Exec(t.Context(), `DELETE FROM admin_requests`); err == nil {
		t.Fatal("admin_requests accepted a DELETE")
	}
	if _, err := pool.Exec(t.Context(), `TRUNCATE admin_requests`); err == nil {
		t.Fatal("admin_requests accepted a TRUNCATE")
	}
}

func seedAdminActor(t *testing.T, pool *pgxpool.Pool, actorID, token string, role AdminRole, mosqueID string) {
	t.Helper()
	tokenHash := sha256.Sum256([]byte(token))
	tx, err := pool.Begin(t.Context())
	if err != nil {
		t.Fatalf("begin admin actor seed: %v", err)
	}
	defer func() { _ = tx.Rollback(t.Context()) }()
	if _, err := tx.Exec(t.Context(), `
		INSERT INTO admin_actors (id, display_name, status, created_at)
		VALUES ($1, $1, 'active', '2026-08-20T12:00:00Z')`, actorID); err != nil {
		t.Fatalf("seed admin actor %s: %v", actorID, err)
	}
	if _, err := tx.Exec(t.Context(), `
		INSERT INTO admin_credentials (id, actor_id, token_hash, created_at)
		VALUES ($1 || '-credential', $1, $2, '2026-08-20T12:00:00Z')`, actorID, tokenHash[:]); err != nil {
		t.Fatalf("seed admin credential %s: %v", actorID, err)
	}
	if _, err := tx.Exec(t.Context(), `
		INSERT INTO admin_memberships (actor_id, mosque_id, role, created_at)
		VALUES ($1, NULLIF($2, ''), $3, '2026-08-20T12:00:00Z')`, actorID, mosqueID, role); err != nil {
		t.Fatalf("seed admin actor %s: %v", actorID, err)
	}
	if err := tx.Commit(t.Context()); err != nil {
		t.Fatalf("commit admin actor seed %s: %v", actorID, err)
	}
}

func assertAdminPlaintextAbsent(t *testing.T, pool *pgxpool.Pool, secrets ...string) {
	t.Helper()
	for _, secret := range secrets {
		var found bool
		if err := pool.QueryRow(t.Context(), `
			SELECT EXISTS (SELECT 1 FROM admin_credentials WHERE token_hash::text LIKE '%' || $1 || '%')`, secret).Scan(&found); err != nil {
			t.Fatalf("inspect admin credential storage: %v", err)
		}
		if found {
			t.Fatal("plaintext admin credential was found in PostgreSQL")
		}
	}
}

func assertAdminAuditEvidence(t *testing.T, pool *pgxpool.Pool, actorID string, actions map[string]string) {
	t.Helper()
	for action, reason := range actions {
		var count int
		if err := pool.QueryRow(t.Context(), `
			SELECT count(*) FROM audit_events
			WHERE actor_id = $1 AND action = $2 AND reason = $3 AND request_id <> ''`, actorID, action, reason).Scan(&count); err != nil {
			t.Fatalf("inspect admin audit %s: %v", action, err)
		}
		if count != 1 {
			t.Fatalf("admin audit %s count = %d, want 1", action, count)
		}
	}
}

func assertAssignmentAuditChain(
	t *testing.T,
	pool *pgxpool.Pool,
	deviceID string,
	first, second DeviceAssignment,
) {
	t.Helper()
	var firstBefore, firstAfter, secondBefore, secondAfter []byte
	if err := pool.QueryRow(t.Context(), `
		SELECT before_hash, after_hash FROM audit_events
		WHERE action = 'device.assigned' AND entity_id = $1 AND reason = 'assign verified snapshot'`, deviceID).Scan(
		&firstBefore, &firstAfter,
	); err != nil {
		t.Fatalf("read first assignment audit: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT before_hash, after_hash FROM audit_events
		WHERE action = 'device.assigned' AND entity_id = $1 AND reason = 'raise minimum application version'`, deviceID).Scan(
		&secondBefore, &secondAfter,
	); err != nil {
		t.Fatalf("read second assignment audit: %v", err)
	}
	firstHash := hashDeviceAssignment(first, "mosque-ulyanovsk-0001")
	secondHash := hashDeviceAssignment(second, "mosque-ulyanovsk-0001")
	if firstBefore != nil || !bytes.Equal(firstAfter, firstHash[:]) ||
		!bytes.Equal(firstAfter, secondBefore) || !bytes.Equal(secondAfter, secondHash[:]) {
		t.Fatalf("assignment audit chain mismatch: first_before=%x first_after=%x second_before=%x second_after=%x", firstBefore, firstAfter, secondBefore, secondAfter)
	}
}
