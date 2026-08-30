//go:build integration

package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

func TestPostgresBackupRestorePreservesCurrentFleetState(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_RESTORE_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_RESTORE_URL is not set")
	}
	runtimeDatabaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_RESTORE_RUNTIME_URL")
	if runtimeDatabaseURL == "" {
		t.Fatal("NAMAZ_TEST_POSTGRES_RESTORE_RUNTIME_URL is not set")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 20*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("connect restored PostgreSQL: %v", err)
	}
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping restored PostgreSQL: %v", err)
	}

	runtimePool, err := pgxpool.New(ctx, runtimeDatabaseURL)
	if err != nil {
		t.Fatalf("connect restored runtime PostgreSQL role: %v", err)
	}
	defer runtimePool.Close()
	if err := runtimePool.Ping(ctx); err != nil {
		t.Fatalf("ping restored runtime PostgreSQL role: %v", err)
	}

	repository := NewPostgresPairingRepository(runtimePool)
	if err := repository.VerifySchema(ctx); err != nil {
		t.Fatalf("verify restored schema: %v", err)
	}
	pairingManager, err := NewPairingManager(PairingManagerConfig{
		Repository:   repository,
		RateLimitKey: bytes.Repeat([]byte{0x51}, sha256.Size),
		RateLimits: PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 10, CodeAttempts: 5,
		},
	})
	if err != nil {
		t.Fatalf("configure restored pairing manager: %v", err)
	}
	device, err := pairingManager.Authenticate(ctx, "restore-device-token-integration-0001")
	if err != nil {
		t.Fatalf("authenticate restored device: %v", err)
	}
	if device.DeviceID != "device-restore-0001" || device.Mosque.ID != "mosque-restore-0001" {
		t.Fatalf("restored device identity = %#v", device)
	}

	adminManager, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository:     repository,
		IdempotencyKey: bytes.Repeat([]byte{0x52}, sha256.Size),
		Now:            func() time.Time { return time.Date(2026, 8, 20, 12, 30, 0, 0, time.UTC) },
	})
	if err != nil {
		t.Fatalf("configure restored admin manager: %v", err)
	}
	admin, err := adminManager.AuthenticateAdmin(ctx, "restore-admin-token-integration-0001")
	if err != nil {
		t.Fatalf("authenticate restored admin: %v", err)
	}
	bundle, err := adminManager.GetDeviceSupportBundle(ctx, admin, "mosque-restore-0001", "device-restore-0001")
	if err != nil {
		t.Fatalf("read restored support bundle: %v", err)
	}
	if bundle.Assignment == nil || bundle.Assignment.ManifestVersion != 7 ||
		bundle.Assignment.SnapshotID != "snapshot-restore-0001" {
		t.Fatalf("restored assignment = %#v", bundle.Assignment)
	}
	if bundle.Health == nil || bundle.Health.SyncStatus != DeviceSyncStatusOK ||
		bundle.Health.CoverageDaysRemaining != 180 || bundle.Device.RolloutGroup != "canary.restore" {
		t.Fatalf("restored current state = device %#v health %#v", bundle.Device, bundle.Health)
	}

	var migrationRows, pairingRows, auditRows, requestRows int
	if err := pool.QueryRow(ctx, `
		SELECT
			(SELECT count(*) FROM schema_migrations),
			(SELECT count(*) FROM pairing_codes),
			(SELECT count(*) FROM audit_events),
			(SELECT count(*) FROM admin_requests)`).Scan(
		&migrationRows, &pairingRows, &auditRows, &requestRows,
	); err != nil {
		t.Fatalf("count restored durable rows: %v", err)
	}
	if migrationRows != PairingSchemaVersion || pairingRows != 1 || auditRows != 1 || requestRows != 1 {
		t.Fatalf(
			"restored row counts: migrations=%d pairing=%d audit=%d requests=%d",
			migrationRows, pairingRows, auditRows, requestRows,
		)
	}
	var runtimeCanUpdateAudit, runtimeCanReadRegistry, runtimeCanInsertRegistry, runtimeCanUpdateRegistry, runtimeCanCreateSchemaObject bool
	if err := runtimePool.QueryRow(ctx, `
		SELECT
			has_table_privilege(current_user, 'audit_events', 'UPDATE'),
			has_table_privilege(current_user, 'registry_active_revision', 'SELECT'),
			has_table_privilege(current_user, 'registry_active_revision', 'INSERT'),
			has_table_privilege(current_user, 'registry_active_revision', 'UPDATE'),
			has_schema_privilege(current_user, 'public', 'CREATE')`).Scan(
		&runtimeCanUpdateAudit, &runtimeCanReadRegistry, &runtimeCanInsertRegistry, &runtimeCanUpdateRegistry, &runtimeCanCreateSchemaObject,
	); err != nil {
		t.Fatalf("inspect restored runtime privileges: %v", err)
	}
	if runtimeCanUpdateAudit || !runtimeCanReadRegistry || runtimeCanInsertRegistry || runtimeCanUpdateRegistry || runtimeCanCreateSchemaObject {
		t.Fatalf(
			"restored runtime privileges: update_audit=%v registry_select=%v registry_insert=%v registry_update=%v create_schema_object=%v",
			runtimeCanUpdateAudit, runtimeCanReadRegistry, runtimeCanInsertRegistry, runtimeCanUpdateRegistry, runtimeCanCreateSchemaObject,
		)
	}

	assertRestoredAppendOnlyGuard(t, ctx, pool, `UPDATE audit_events SET reason = reason`)
	assertRestoredAppendOnlyGuard(t, ctx, pool, `DELETE FROM audit_events`)
	assertRestoredAppendOnlyGuard(t, ctx, pool, `TRUNCATE audit_events`)
	assertRestoredAppendOnlyGuard(t, ctx, pool, `UPDATE admin_requests SET operation = operation`)
	assertRestoredAppendOnlyGuard(t, ctx, pool, `DELETE FROM admin_requests`)
	assertRestoredAppendOnlyGuard(t, ctx, pool, `TRUNCATE admin_requests`)
}

func assertRestoredAppendOnlyGuard(t *testing.T, ctx context.Context, pool *pgxpool.Pool, statement string) {
	t.Helper()
	_, err := pool.Exec(ctx, statement)
	if err == nil {
		t.Fatalf("restored append-only guard accepted %q", statement)
	}
	var postgresError *pgconn.PgError
	if !errors.As(err, &postgresError) || postgresError.Code != "55000" {
		t.Fatalf("restored append-only guard %q error = %v; want SQLSTATE 55000", statement, err)
	}
}
