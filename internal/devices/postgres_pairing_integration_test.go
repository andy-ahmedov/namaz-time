//go:build integration

package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

func TestPostgresPairingMigrationUpgradesVersionOneToCurrent(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	pool, err := pgxpool.New(t.Context(), databaseURL)
	if err != nil {
		t.Fatalf("connect PostgreSQL: %v", err)
	}
	t.Cleanup(pool.Close)
	repository := NewPostgresPairingRepository(pool)
	if err := repository.MigrateTo(t.Context(), 0); err != nil {
		t.Fatalf("reset migrations: %v", err)
	}
	tx, err := pool.Begin(t.Context())
	if err != nil {
		t.Fatalf("begin v1 seed: %v", err)
	}
	defer func() { _ = tx.Rollback(t.Context()) }()
	if _, err := tx.Exec(t.Context(), `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version bigint PRIMARY KEY,
			applied_at timestamptz NOT NULL
		)`); err != nil {
		t.Fatalf("create v1 ledger: %v", err)
	}
	if _, err := tx.Exec(t.Context(), `DELETE FROM schema_migrations`); err != nil {
		t.Fatalf("clear v1 ledger: %v", err)
	}
	v1, err := pairingMigrations.ReadFile("migrations/000001_fleet_pairing.up.sql")
	if err != nil {
		t.Fatalf("read v1 migration: %v", err)
	}
	if _, err := tx.Exec(t.Context(), string(v1), pgx.QueryExecModeSimpleProtocol); err != nil {
		t.Fatalf("apply v1 fixture: %v", err)
	}
	if _, err := tx.Exec(t.Context(), `INSERT INTO schema_migrations (version, applied_at) VALUES (1, clock_timestamp())`); err != nil {
		t.Fatalf("record v1 fixture: %v", err)
	}
	if err := tx.Commit(t.Context()); err != nil {
		t.Fatalf("commit v1 fixture: %v", err)
	}
	if err := repository.MigrateTo(t.Context(), 4); err != nil {
		t.Fatalf("upgrade v1 to v4: %v", err)
	}
	if _, err := pool.Exec(t.Context(), `
		INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ('mosque-migration-0001', 'Migration fixture', 'Europe/Moscow', 'active', clock_timestamp(), clock_timestamp());
		INSERT INTO admin_actors (id, display_name, status, created_at)
		VALUES ('actor-migration-0001', 'Migration fixture', 'active', clock_timestamp());
		INSERT INTO admin_requests (
			idempotency_hash, request_hash, actor_id, mosque_id, operation, resource_id,
			response, created_at, expires_at
		) VALUES
			(decode(repeat('41', 32), 'hex'), decode(repeat('42', 32), 'hex'),
			 'actor-migration-0001', 'mosque-migration-0001', 'assign_device',
			 'device-migration-0001', NULL, clock_timestamp(), clock_timestamp() + interval '1 day'),
			(decode(repeat('43', 32), 'hex'), decode(repeat('44', 32), 'hex'),
			 'actor-migration-0001', 'mosque-migration-0001', 'assign_device',
			 'rollout-migration-01',
			 '{"rollout_group":"rollout-migration-01","snapshot_id":"snapshot-migration-01","device_count":0,"assignments":[]}'::jsonb,
			 clock_timestamp(), clock_timestamp() + interval '1 day')`, pgx.QueryExecModeSimpleProtocol); err != nil {
		t.Fatalf("seed v4 admin request provenance: %v", err)
	}
	if err := repository.MigrateUp(t.Context()); err != nil {
		t.Fatalf("upgrade v4 to current: %v", err)
	}
	var versions int
	var adminTable, healthTable, rolloutColumn bool
	if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM schema_migrations`).Scan(&versions); err != nil {
		t.Fatalf("count upgraded versions: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `SELECT to_regclass('admin_actors') IS NOT NULL`).Scan(&adminTable); err != nil {
		t.Fatalf("inspect v2 table: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `SELECT to_regclass('device_health') IS NOT NULL`).Scan(&healthTable); err != nil {
		t.Fatalf("inspect v3 table: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT EXISTS (
			SELECT 1 FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'rollout_group'
		)`).Scan(&rolloutColumn); err != nil {
		t.Fatalf("inspect v4 column: %v", err)
	}
	if versions != PairingSchemaVersion || !adminTable || !healthTable || !rolloutColumn {
		t.Fatalf("upgraded schema: versions=%d admin_table=%v health_table=%v rollout_column=%v", versions, adminTable, healthTable, rolloutColumn)
	}
	var setGroupOperations, assignGroupOperations int
	if err := pool.QueryRow(t.Context(), `
		SELECT
			count(*) FILTER (WHERE operation = 'set_rollout_group'),
			count(*) FILTER (WHERE operation = 'assign_rollout_group')
		FROM admin_requests WHERE actor_id = 'actor-migration-0001'`).Scan(
		&setGroupOperations, &assignGroupOperations,
	); err != nil {
		t.Fatalf("inspect migrated admin request provenance: %v", err)
	}
	if setGroupOperations != 1 || assignGroupOperations != 1 {
		t.Fatalf("migrated operations: set=%d assign=%d", setGroupOperations, assignGroupOperations)
	}
	if err := repository.VerifySchema(t.Context()); err != nil {
		t.Fatalf("VerifySchema(current) error = %v", err)
	}
	if err := repository.MigrateTo(t.Context(), 3); err != nil {
		t.Fatalf("rollback current schema to v3: %v", err)
	}
	var v3Versions int
	var preservedHealth, removedRollout bool
	if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM schema_migrations`).Scan(&v3Versions); err != nil {
		t.Fatalf("count v3 rollback versions: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT to_regclass('device_health') IS NOT NULL,
		       NOT EXISTS (
			   SELECT 1 FROM information_schema.columns
			   WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'rollout_group'
		       )`).Scan(&preservedHealth, &removedRollout); err != nil {
		t.Fatalf("inspect v4 to v3 rollback: %v", err)
	}
	if v3Versions != 3 || !preservedHealth || !removedRollout {
		t.Fatalf("v4 to v3 rollback: versions=%d health=%v rollout_removed=%v", v3Versions, preservedHealth, removedRollout)
	}
	if err := repository.MigrateUp(t.Context()); err != nil {
		t.Fatalf("reapply current schema after v3 rollback: %v", err)
	}
	if err := repository.MigrateTo(t.Context(), 2); err != nil {
		t.Fatalf("rollback current schema to v2: %v", err)
	}
	var v2Versions int
	var preservedAdmin, removedHealth bool
	if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM schema_migrations`).Scan(&v2Versions); err != nil {
		t.Fatalf("count v2 rollback versions: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT to_regclass('admin_actors') IS NOT NULL,
		       to_regclass('device_health') IS NULL`).Scan(&preservedAdmin, &removedHealth); err != nil {
		t.Fatalf("inspect v3 to v2 rollback: %v", err)
	}
	if v2Versions != 2 || !preservedAdmin || !removedHealth {
		t.Fatalf("v3 to v2 rollback: versions=%d admin=%v health_removed=%v", v2Versions, preservedAdmin, removedHealth)
	}
	if err := repository.MigrateUp(t.Context()); err != nil {
		t.Fatalf("reapply current schema after v2 rollback: %v", err)
	}
	if err := repository.MigrateTo(t.Context(), 1); err != nil {
		t.Fatalf("rollback upgraded schema to v1: %v", err)
	}
	var remainingVersions int
	var pairingTable, removedAdminTable, removedHealthTable bool
	if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM schema_migrations`).Scan(&remainingVersions); err != nil {
		t.Fatalf("count rolled-back versions: %v", err)
	}
	if err := pool.QueryRow(t.Context(), `
		SELECT to_regclass('pairing_codes') IS NOT NULL,
		       to_regclass('admin_actors') IS NULL,
		       to_regclass('device_health') IS NULL`).Scan(&pairingTable, &removedAdminTable, &removedHealthTable); err != nil {
		t.Fatalf("inspect current to v1 rollback: %v", err)
	}
	if remainingVersions != 1 || !pairingTable || !removedAdminTable || !removedHealthTable {
		t.Fatalf("targeted rollback: versions=%d pairing=%v admin_removed=%v health_removed=%v", remainingVersions, pairingTable, removedAdminTable, removedHealthTable)
	}
	if err := repository.VerifySchema(t.Context()); err == nil {
		t.Fatal("VerifySchema(v1) accepted an outdated schema")
	}
	if err := repository.MigrateTo(t.Context(), 0); err != nil {
		t.Fatalf("cleanup upgraded schema: %v", err)
	}
}

func TestPostgresPairingMigrationRefusesFutureSchema(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	pool, err := pgxpool.New(t.Context(), databaseURL)
	if err != nil {
		t.Fatalf("connect PostgreSQL: %v", err)
	}
	t.Cleanup(pool.Close)
	repository := NewPostgresPairingRepository(pool)
	if err := repository.MigrateTo(t.Context(), 0); err != nil {
		t.Fatalf("reset migrations: %v", err)
	}
	if err := repository.MigrateUp(t.Context()); err != nil {
		t.Fatalf("apply migrations: %v", err)
	}
	t.Cleanup(func() {
		cleanupCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_, _ = pool.Exec(cleanupCtx, `DELETE FROM schema_migrations WHERE version > $1`, PairingSchemaVersion)
		_ = repository.MigrateTo(cleanupCtx, 0)
	})
	if _, err := pool.Exec(t.Context(), `INSERT INTO schema_migrations (version, applied_at) VALUES ($1, clock_timestamp())`, PairingSchemaVersion+1); err != nil {
		t.Fatalf("seed future migration: %v", err)
	}
	if err := repository.MigrateUp(t.Context()); err == nil {
		t.Fatal("MigrateUp() accepted a future schema version")
	}
	if err := repository.MigrateTo(t.Context(), 1); err == nil {
		t.Fatal("MigrateTo() attempted rollback while a future schema version existed")
	}
	var mosqueTableExists bool
	if err := pool.QueryRow(t.Context(), `SELECT to_regclass('mosques') IS NOT NULL`).Scan(&mosqueTableExists); err != nil {
		t.Fatalf("inspect schema after refused rollback: %v", err)
	}
	if !mosqueTableExists {
		t.Fatal("future-schema rollback guard did not preserve current tables")
	}
}

func TestPostgresPairingLifecycle(t *testing.T) {
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
	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping PostgreSQL: %v", err)
	}
	repository := NewPostgresPairingRepository(pool)
	if err := repository.MigrateTo(ctx, 0); err != nil {
		t.Fatalf("reset migrations: %v", err)
	}
	if err := repository.MigrateUp(ctx); err != nil {
		t.Fatalf("apply migrations: %v", err)
	}
	if err := repository.MigrateUp(ctx); err != nil {
		t.Fatalf("reapply idempotent migrations: %v", err)
	}
	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = repository.MigrateTo(cleanupCtx, 0)
	})

	now := newMutableClock(time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC))
	rateKey := bytes.Repeat([]byte{0x4c}, sha256.Size)
	seedMosque(t, pool, "mosque-ulyanovsk-0001", "Second Cathedral Mosque", "Europe/Ulyanovsk")
	seedMosque(t, pool, "mosque-kazan-00000001", "Synthetic Kazan Mosque", "Europe/Moscow")
	seedMosque(t, pool, "mosque-suspend-000001", "Suspension Test Mosque", "Europe/Ulyanovsk")
	seedMosque(t, pool, "mosque-invalid-zone-01", "Invalid Zone Mosque", "+04:00")
	assertDatabaseRejectsCrossMosquePairingRecord(t, pool)

	manager := newIntegrationPairingManager(t, repository, now.Now, rateKey, PairingRateLimits{
		Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 20, CodeAttempts: 20,
	})
	if _, err := manager.Issue(t.Context(), IssuePairingCommand{
		MosqueID: "mosque-invalid-zone-01", ActorID: "actor-service-0001",
		Reason: "invalid identity proof", RequestID: "request-invalid-zone", ExpiresIn: 10 * time.Minute,
	}); err == nil {
		t.Fatal("Issue() accepted a persisted mosque with a numeric-offset timezone")
	}
	var invalidMosqueDevices int
	if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM devices WHERE mosque_id = 'mosque-invalid-zone-01'`).Scan(&invalidMosqueDevices); err != nil {
		t.Fatalf("count invalid-mosque devices: %v", err)
	}
	if invalidMosqueDevices != 0 {
		t.Fatalf("invalid mosque left %d device rows", invalidMosqueDevices)
	}
	suspended, err := manager.Issue(t.Context(), IssuePairingCommand{
		MosqueID: "mosque-suspend-000001", ActorID: "actor-service-0001",
		Reason: "suspension proof", RequestID: "request-suspend-issue", ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("Issue() before suspension error = %v", err)
	}
	if _, err := pool.Exec(t.Context(), `
		UPDATE mosques SET status = 'suspended', updated_at = $2 WHERE id = $1`,
		"mosque-suspend-000001", now.Now()); err != nil {
		t.Fatalf("suspend mosque: %v", err)
	}
	_, err = manager.Pair(t.Context(), PairingAttempt{
		Code: suspended.Code, Device: DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Suspended TV"},
		SourceAddress: "192.0.2.5", RequestID: "request-suspend-pair",
	})
	if !errors.Is(err, ErrPairingInvalid) {
		t.Fatalf("Pair() after mosque suspension error = %v", err)
	}
	var suspendedUses int
	var suspendedDeviceStatus string
	if err := pool.QueryRow(t.Context(), `
		SELECT pc.uses_count, d.status
		FROM pairing_codes pc JOIN devices d ON d.id = pc.device_id
		WHERE d.id = $1`, suspended.DeviceID).Scan(&suspendedUses, &suspendedDeviceStatus); err != nil {
		t.Fatalf("read suspended pairing state: %v", err)
	}
	if suspendedUses != 0 || suspendedDeviceStatus != "pending" {
		t.Fatalf("suspended mosque mutated pairing: uses=%d device=%s", suspendedUses, suspendedDeviceStatus)
	}
	issued, err := manager.Issue(t.Context(), IssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001",
		Reason: "install lobby display", RequestID: "request-issue-0001", ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("Issue() error = %v", err)
	}
	assertPlaintextSecretAbsent(t, pool, issued.Code)

	paired, err := manager.Pair(t.Context(), PairingAttempt{
		Code:                  issued.Code,
		Device:                DeviceInfo{AppVersion: "1.0.0", OSVersion: "35", Model: "Android TV", Capabilities: []string{"4k"}},
		InstallationPublicKey: "device-public-key",
		SourceAddress:         "192.0.2.10", RequestID: "request-pair-0001",
	})
	if err != nil {
		t.Fatalf("Pair() error = %v", err)
	}
	if paired.DeviceID != issued.DeviceID || paired.Mosque.ID != "mosque-ulyanovsk-0001" {
		t.Fatalf("paired device = %#v", paired)
	}
	assertPlaintextSecretAbsent(t, pool, paired.Token)
	assertStoredDigests(t, pool, issued.DeviceID, issued.Code, paired.Token)

	pool.Close()
	restartedPool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("reconnect PostgreSQL: %v", err)
	}
	defer restartedPool.Close()
	repository = NewPostgresPairingRepository(restartedPool)
	manager = newIntegrationPairingManager(t, repository, now.Now, rateKey, PairingRateLimits{
		Window: 10 * time.Minute, SourceAttempts: 100, DeviceAttempts: 100, CodeAttempts: 100,
	})
	principal, err := manager.Authenticate(t.Context(), paired.Token)
	if err != nil || principal.DeviceID != issued.DeviceID {
		t.Fatalf("Authenticate() after restart = %#v, %v", principal, err)
	}
	heartbeat := DeviceHeartbeatReport{
		SentAt: now.Now(), AppVersion: "1.1.0", OSVersion: "36", Model: "Android TV Updated",
		ActiveSnapshotID: "snapshot-reported-0001", SyncStatus: DeviceSyncStatusOK,
		CoverageDaysRemaining: 27, StorageHealth: DeviceHealthOK, MemoryHealth: DeviceHealthLow,
		BootMode: DeviceBootModeBestEffort, KioskMode: DeviceKioskModeNone,
	}
	if err := manager.Heartbeat(t.Context(), principal, heartbeat); err != nil {
		t.Fatalf("Heartbeat(first) error = %v", err)
	}
	heartbeat.SyncStatus = DeviceSyncStatusTransientFailure
	heartbeat.CoverageDaysRemaining = 26
	if err := manager.Heartbeat(t.Context(), principal, heartbeat); err != nil {
		t.Fatalf("Heartbeat(second) error = %v", err)
	}
	var healthRows, coverage int
	var lastSeen time.Time
	var syncStatus string
	if err := restartedPool.QueryRow(t.Context(), `
		SELECT count(*), max(sync_status), max(coverage_days_remaining)
		FROM device_health WHERE device_id = $1`, issued.DeviceID).Scan(&healthRows, &syncStatus, &coverage); err != nil {
		t.Fatalf("read latest-only heartbeat: %v", err)
	}
	if err := restartedPool.QueryRow(t.Context(), `SELECT last_seen_at FROM devices WHERE id = $1`, issued.DeviceID).Scan(&lastSeen); err != nil {
		t.Fatalf("read server last-seen: %v", err)
	}
	if healthRows != 1 || syncStatus != string(DeviceSyncStatusTransientFailure) || coverage != 26 || !lastSeen.Equal(now.Now()) {
		t.Fatalf("heartbeat state: rows=%d status=%s coverage=%d last_seen=%s", healthRows, syncStatus, coverage, lastSeen)
	}
	forgedPrincipal := principal
	forgedPrincipal.Mosque.ID = "mosque-kazan-00000001"
	if err := manager.Heartbeat(t.Context(), forgedPrincipal, heartbeat); !errors.Is(err, ErrDeviceUnauthorized) {
		t.Fatalf("cross-mosque Heartbeat() error = %v", err)
	}
	if _, err := restartedPool.Exec(t.Context(), `
		UPDATE mosques SET status = 'suspended'
		WHERE id = 'mosque-ulyanovsk-0001'`); err != nil {
		t.Fatalf("suspend heartbeat mosque: %v", err)
	}
	now.Advance(time.Minute)
	if err := manager.Heartbeat(t.Context(), principal, heartbeat); !errors.Is(err, ErrDeviceUnauthorized) {
		t.Fatalf("suspended-mosque stale-principal Heartbeat() error = %v", err)
	}
	var suspendedLastSeen time.Time
	if err := restartedPool.QueryRow(t.Context(), `
		SELECT last_seen_at FROM devices WHERE id = $1`, issued.DeviceID).Scan(&suspendedLastSeen); err != nil {
		t.Fatalf("read suspended-mosque last-seen: %v", err)
	}
	if !suspendedLastSeen.Equal(lastSeen) {
		t.Fatalf("suspended mosque changed last-seen: got %s want %s", suspendedLastSeen, lastSeen)
	}
	if _, err := restartedPool.Exec(t.Context(), `
		UPDATE mosques SET status = 'active'
		WHERE id = 'mosque-ulyanovsk-0001'`); err != nil {
		t.Fatalf("reactivate heartbeat mosque: %v", err)
	}
	_, err = manager.Pair(t.Context(), PairingAttempt{
		Code: issued.Code, Device: DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "TV"},
		SourceAddress: "192.0.2.11", RequestID: "request-reuse-0001",
	})
	if !errors.Is(err, ErrPairingInvalid) {
		t.Fatalf("reused Pair() error = %v", err)
	}

	t.Run("lock wait obeys caller deadline without consuming code", func(t *testing.T) {
		blocked, err := manager.Issue(t.Context(), IssuePairingCommand{
			MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001",
			Reason: "deadline proof", RequestID: "request-issue-deadline", ExpiresIn: 10 * time.Minute,
		})
		if err != nil {
			t.Fatalf("Issue() error = %v", err)
		}
		lockTx, err := restartedPool.Begin(t.Context())
		if err != nil {
			t.Fatalf("begin lock transaction: %v", err)
		}
		defer func() { _ = lockTx.Rollback(t.Context()) }()
		codeHash := sha256.Sum256([]byte(blocked.Code))
		if _, err := lockTx.Exec(t.Context(), `SELECT id FROM pairing_codes WHERE code_hash = $1 FOR UPDATE`, codeHash[:]); err != nil {
			t.Fatalf("lock pairing code: %v", err)
		}

		deadlineContext, cancel := context.WithTimeout(t.Context(), 50*time.Millisecond)
		started := time.Now()
		_, err = manager.Pair(deadlineContext, PairingAttempt{
			Code: blocked.Code, Device: DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Blocked TV"},
			SourceAddress: "192.0.2.12", RequestID: "request-pair-deadline",
		})
		cancel()
		if err == nil || time.Since(started) >= time.Second {
			t.Fatalf("deadline Pair() error = %v after %s", err, time.Since(started))
		}
		if err := lockTx.Rollback(t.Context()); err != nil {
			t.Fatalf("release pairing lock: %v", err)
		}
		var uses int
		var status string
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT pc.uses_count, d.status
			FROM pairing_codes pc JOIN devices d ON d.id = pc.device_id
			WHERE pc.code_hash = $1`, codeHash[:]).Scan(&uses, &status); err != nil {
			t.Fatalf("read deadline pairing state: %v", err)
		}
		if uses != 0 || status != "pending" {
			t.Fatalf("deadline mutated pairing: uses=%d status=%s", uses, status)
		}
	})

	t.Run("concurrent redemption has one winner", func(t *testing.T) {
		concurrent, err := manager.Issue(t.Context(), IssuePairingCommand{
			MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001",
			Reason: "concurrency proof", RequestID: "request-issue-concurrent", ExpiresIn: 10 * time.Minute,
		})
		if err != nil {
			t.Fatalf("Issue() error = %v", err)
		}
		var winners atomic.Int32
		var invalid atomic.Int32
		var unexpected atomic.Value
		var wait sync.WaitGroup
		for index := 0; index < 8; index++ {
			wait.Add(1)
			go func(index int) {
				defer wait.Done()
				_, pairErr := manager.Pair(t.Context(), PairingAttempt{
					Code:          concurrent.Code,
					Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: fmt.Sprintf("TV-%d", index)},
					SourceAddress: fmt.Sprintf("198.51.100.%d", index+1),
					RequestID:     fmt.Sprintf("request-concurrent-%02d", index),
				})
				switch {
				case pairErr == nil:
					winners.Add(1)
				case errors.Is(pairErr, ErrPairingInvalid):
					invalid.Add(1)
				default:
					unexpected.Store(pairErr)
				}
			}(index)
		}
		wait.Wait()
		if value := unexpected.Load(); value != nil {
			t.Fatalf("unexpected concurrent error: %v", value)
		}
		if winners.Load() != 1 || invalid.Load() != 7 {
			t.Fatalf("concurrent outcomes: winners=%d invalid=%d", winners.Load(), invalid.Load())
		}
	})

	t.Run("expiry fails closed", func(t *testing.T) {
		expiring, err := manager.Issue(t.Context(), IssuePairingCommand{
			MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001",
			Reason: "expiry proof", RequestID: "request-issue-expiry", ExpiresIn: time.Minute,
		})
		if err != nil {
			t.Fatalf("Issue() error = %v", err)
		}
		now.Advance(time.Minute)
		_, err = manager.Pair(t.Context(), PairingAttempt{
			Code: expiring.Code, Device: DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "TV"},
			SourceAddress: "203.0.113.10", RequestID: "request-expired-0001",
		})
		if !errors.Is(err, ErrPairingInvalid) {
			t.Fatalf("expired Pair() error = %v", err)
		}
	})

	t.Run("rate buckets persist and do not expose code state", func(t *testing.T) {
		limitedManager := newIntegrationPairingManager(t, repository, now.Now, rateKey, PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 2, DeviceAttempts: 100, CodeAttempts: 100,
		})
		for attempt := 0; attempt < 2; attempt++ {
			_, pairErr := limitedManager.Pair(t.Context(), PairingAttempt{
				Code:          "INVALID-CODE-00000000000000",
				Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: fmt.Sprintf("Guess-%d", attempt)},
				SourceAddress: "203.0.113.20", RequestID: fmt.Sprintf("request-rate-%04d", attempt),
			})
			if !errors.Is(pairErr, ErrPairingInvalid) {
				t.Fatalf("invalid attempt %d error = %v", attempt, pairErr)
			}
		}
		_, err := limitedManager.Pair(t.Context(), PairingAttempt{
			Code: "ANOTHER-INVALID-CODE-0000000", Device: DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Other"},
			SourceAddress: "203.0.113.20", RequestID: "request-rate-0002",
		})
		if !errors.Is(err, ErrPairingRateLimited) {
			t.Fatalf("rate-limited Pair() error = %v", err)
		}
		var variableBucketsBefore int
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT count(*) FROM pairing_rate_buckets WHERE bucket_kind IN ('code', 'device')`).Scan(&variableBucketsBefore); err != nil {
			t.Fatalf("count variable buckets before limited flood: %v", err)
		}
		for index := 0; index < 10; index++ {
			_, pairErr := limitedManager.Pair(t.Context(), PairingAttempt{
				Code:          fmt.Sprintf("FLOOD-INVALID-%012d", index),
				Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: fmt.Sprintf("Flood-%d", index)},
				SourceAddress: "203.0.113.20", RequestID: fmt.Sprintf("request-flood-%04d", index),
			})
			if !errors.Is(pairErr, ErrPairingRateLimited) {
				t.Fatalf("limited flood attempt %d error = %v", index, pairErr)
			}
		}
		var variableBucketsAfter int
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT count(*) FROM pairing_rate_buckets WHERE bucket_kind IN ('code', 'device')`).Scan(&variableBucketsAfter); err != nil {
			t.Fatalf("count variable buckets after limited flood: %v", err)
		}
		if variableBucketsAfter != variableBucketsBefore {
			t.Fatalf("source-limited flood grew variable buckets: before=%d after=%d", variableBucketsBefore, variableBucketsAfter)
		}
	})

	t.Run("source device and code buckets limit independently", func(t *testing.T) {
		cases := []struct {
			name       string
			limits     PairingRateLimits
			attemptFor func(int) PairingAttempt
		}{
			{
				name:   "source",
				limits: PairingRateLimits{Window: 10 * time.Minute, SourceAttempts: 2, DeviceAttempts: 100, CodeAttempts: 100},
				attemptFor: func(index int) PairingAttempt {
					return PairingAttempt{
						Code:          fmt.Sprintf("SOURCE-INVALID-%010d", index),
						Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: fmt.Sprintf("Source-TV-%d", index)},
						SourceAddress: "203.0.113.30", RequestID: fmt.Sprintf("request-source-%04d", index),
					}
				},
			},
			{
				name:   "device",
				limits: PairingRateLimits{Window: 10 * time.Minute, SourceAttempts: 100, DeviceAttempts: 2, CodeAttempts: 100},
				attemptFor: func(index int) PairingAttempt {
					return PairingAttempt{
						Code:          fmt.Sprintf("DEVICE-INVALID-%010d", index),
						Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Stable Device"},
						SourceAddress: fmt.Sprintf("203.0.113.%d", 40+index), RequestID: fmt.Sprintf("request-device-%04d", index),
					}
				},
			},
			{
				name:   "code",
				limits: PairingRateLimits{Window: 10 * time.Minute, SourceAttempts: 100, DeviceAttempts: 100, CodeAttempts: 2},
				attemptFor: func(index int) PairingAttempt {
					return PairingAttempt{
						Code:          "CODE-INVALID-00000000000000",
						Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: fmt.Sprintf("Code-TV-%d", index)},
						SourceAddress: fmt.Sprintf("198.51.100.%d", 40+index), RequestID: fmt.Sprintf("request-code-%04d", index),
					}
				},
			},
		}
		for _, testCase := range cases {
			t.Run(testCase.name, func(t *testing.T) {
				limitedManager := newIntegrationPairingManager(t, repository, now.Now, rateKey, testCase.limits)
				for index := 0; index < 3; index++ {
					_, pairErr := limitedManager.Pair(t.Context(), testCase.attemptFor(index))
					if index < 2 && !errors.Is(pairErr, ErrPairingInvalid) {
						t.Fatalf("attempt %d error = %v, want invalid", index, pairErr)
					}
					if index == 2 && !errors.Is(pairErr, ErrPairingRateLimited) {
						t.Fatalf("attempt %d error = %v, want rate limited", index, pairErr)
					}
				}
			})
		}
	})

	t.Run("expired rate buckets are pruned in bounded batches", func(t *testing.T) {
		cleanupManager := newIntegrationPairingManager(t, repository, now.Now, rateKey, PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 100, DeviceAttempts: 100, CodeAttempts: 100,
		})
		oldSource := "192.0.2.200"
		_, err := cleanupManager.Pair(t.Context(), PairingAttempt{
			Code:          "CLEANUP-INVALID-CODE-0000000",
			Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Cleanup Old"},
			SourceAddress: oldSource, RequestID: "request-cleanup-old",
		})
		if !errors.Is(err, ErrPairingInvalid) {
			t.Fatalf("old bucket attempt error = %v", err)
		}
		oldBucket := cleanupManager.rateBucket("source", oldSource)
		now.Advance(21 * time.Minute)
		_, err = cleanupManager.Pair(t.Context(), PairingAttempt{
			Code:          "CLEANUP-INVALID-CODE-0000001",
			Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Cleanup New"},
			SourceAddress: "192.0.2.201", RequestID: "request-cleanup-new",
		})
		if !errors.Is(err, ErrPairingInvalid) {
			t.Fatalf("new bucket attempt error = %v", err)
		}
		var oldExists bool
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT EXISTS (
				SELECT 1 FROM pairing_rate_buckets
				WHERE bucket_kind = 'source' AND bucket_hash = $1
			)`, oldBucket[:]).Scan(&oldExists); err != nil {
			t.Fatalf("inspect expired bucket: %v", err)
		}
		if oldExists {
			t.Fatal("expired rate bucket was not pruned")
		}
	})

	t.Run("backward clock movement retains the active rate window", func(t *testing.T) {
		clockManager := newIntegrationPairingManager(t, repository, now.Now, rateKey, PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 100, DeviceAttempts: 100, CodeAttempts: 100,
		})
		attempt := PairingAttempt{
			Code:          "CLOCK-INVALID-CODE-00000000",
			Device:        DeviceInfo{AppVersion: "1", OSVersion: "35", Model: "Clock TV"},
			SourceAddress: "192.0.2.210", RequestID: "request-clock-forward",
		}
		_, err := clockManager.Pair(t.Context(), attempt)
		if !errors.Is(err, ErrPairingInvalid) {
			t.Fatalf("initial clock attempt error = %v", err)
		}
		bucket := clockManager.rateBucket("source", attempt.SourceAddress)
		var originalWindow, originalUpdated time.Time
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT window_started_at, updated_at FROM pairing_rate_buckets
			WHERE bucket_kind = 'source' AND bucket_hash = $1`, bucket[:]).Scan(&originalWindow, &originalUpdated); err != nil {
			t.Fatalf("read original clock bucket: %v", err)
		}
		now.Advance(-5 * time.Minute)
		attempt.RequestID = "request-clock-backward"
		_, err = clockManager.Pair(t.Context(), attempt)
		if !errors.Is(err, ErrPairingInvalid) {
			t.Fatalf("backward-clock attempt error = %v", err)
		}
		var retainedWindow, retainedUpdated time.Time
		if err := restartedPool.QueryRow(t.Context(), `
			SELECT window_started_at, updated_at FROM pairing_rate_buckets
			WHERE bucket_kind = 'source' AND bucket_hash = $1`, bucket[:]).Scan(&retainedWindow, &retainedUpdated); err != nil {
			t.Fatalf("read retained clock bucket: %v", err)
		}
		if !retainedWindow.Equal(originalWindow) || retainedUpdated.Before(originalUpdated) {
			t.Fatalf("clock rollback changed bucket backward: window=%s updated=%s", retainedWindow, retainedUpdated)
		}
	})

	t.Run("revocation is mosque scoped and durable", func(t *testing.T) {
		err := manager.Revoke(t.Context(), RevokeDeviceCommand{
			MosqueID: "mosque-kazan-00000001", DeviceID: issued.DeviceID,
			ActorID: "actor-service-0001", Reason: "cross-mosque attempt", RequestID: "request-revoke-wrong",
		})
		if !errors.Is(err, ErrDeviceNotFound) {
			t.Fatalf("cross-mosque Revoke() error = %v", err)
		}
		if _, err := manager.Authenticate(t.Context(), paired.Token); err != nil {
			t.Fatalf("cross-mosque revoke changed credential: %v", err)
		}
		if err := manager.Revoke(t.Context(), RevokeDeviceCommand{
			MosqueID: "mosque-ulyanovsk-0001", DeviceID: issued.DeviceID,
			ActorID: "actor-service-0001", Reason: "device replaced", RequestID: "request-revoke-right",
		}); err != nil {
			t.Fatalf("Revoke() error = %v", err)
		}
		if _, err := manager.Authenticate(t.Context(), paired.Token); !errors.Is(err, ErrDeviceUnauthorized) {
			t.Fatalf("revoked Authenticate() error = %v", err)
		}
		if err := manager.Heartbeat(t.Context(), principal, heartbeat); !errors.Is(err, ErrDeviceUnauthorized) {
			t.Fatalf("revoked stale-principal Heartbeat() error = %v", err)
		}
	})

	assertAuditIsAppendOnly(t, restartedPool)
	assertAuditActions(t, restartedPool, map[string]int{
		"device.pairing_issued": 4,
		"device.paired":         2,
		"device.revoked":        1,
	})
}

func newIntegrationPairingManager(
	t *testing.T,
	repository PairingRepository,
	now func() time.Time,
	rateKey []byte,
	limits PairingRateLimits,
) *PairingManager {
	t.Helper()
	manager, err := NewPairingManager(PairingManagerConfig{
		Repository: repository, Now: now, RateLimitKey: rateKey, RateLimits: limits,
	})
	if err != nil {
		t.Fatalf("NewPairingManager() error = %v", err)
	}
	return manager
}

func seedMosque(t *testing.T, pool *pgxpool.Pool, id, name, timezone string) {
	t.Helper()
	_, err := pool.Exec(t.Context(), `
		INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ($1, $2, $3, 'active', '2026-08-20T12:00:00Z', '2026-08-20T12:00:00Z')`,
		id, name, timezone,
	)
	if err != nil {
		t.Fatalf("seed mosque %s: %v", id, err)
	}
}

func assertPlaintextSecretAbsent(t *testing.T, pool *pgxpool.Pool, secret string) {
	t.Helper()
	var found bool
	err := pool.QueryRow(t.Context(), `
		SELECT EXISTS (
			SELECT 1 FROM pairing_codes WHERE code_hash::text LIKE '%' || $1 || '%'
			UNION ALL
			SELECT 1 FROM devices WHERE COALESCE(token_hash::text, '') LIKE '%' || $1 || '%'
		)`, secret).Scan(&found)
	if err != nil {
		t.Fatalf("inspect secret storage: %v", err)
	}
	if found {
		t.Fatal("plaintext secret was found in persistent credential columns")
	}
}

func assertStoredDigests(t *testing.T, pool *pgxpool.Pool, deviceID, code, token string) {
	t.Helper()
	var codeHash, tokenHash []byte
	err := pool.QueryRow(t.Context(), `
		SELECT pc.code_hash, d.token_hash
		FROM pairing_codes pc JOIN devices d ON d.id = pc.device_id
		WHERE d.id = $1`, deviceID).Scan(&codeHash, &tokenHash)
	if err != nil {
		t.Fatalf("read stored digests: %v", err)
	}
	wantCode := sha256.Sum256([]byte(code))
	wantToken := sha256.Sum256([]byte(token))
	if !bytes.Equal(codeHash, wantCode[:]) || !bytes.Equal(tokenHash, wantToken[:]) {
		t.Fatal("stored secret digests do not match issued values")
	}
}

func assertDatabaseRejectsCrossMosquePairingRecord(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()
	tx, err := pool.Begin(t.Context())
	if err != nil {
		t.Fatalf("begin cross-mosque constraint transaction: %v", err)
	}
	defer func() { _ = tx.Rollback(context.Background()) }()
	now := time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)
	if _, err := tx.Exec(t.Context(), `
		INSERT INTO devices (id, mosque_id, status, created_at)
		VALUES ('device-cross-scope-0001', 'mosque-ulyanovsk-0001', 'pending', $1)`, now); err != nil {
		t.Fatalf("insert cross-scope device fixture: %v", err)
	}
	codeHash := sha256.Sum256([]byte("CROSS-SCOPE-CODE"))
	_, err = tx.Exec(t.Context(), `
		INSERT INTO pairing_codes (
			id, device_id, mosque_id, code_hash, expires_at, issued_by_actor_id, created_at
		) VALUES (
			'pairing-cross-scope-0001', 'device-cross-scope-0001', 'mosque-kazan-00000001',
			$1, $2, 'actor-service-0001', $3
		)`, codeHash[:], now.Add(10*time.Minute), now)
	if err == nil {
		t.Fatal("database accepted a pairing code whose mosque differs from its device")
	}
}

func assertAuditIsAppendOnly(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()
	if _, err := pool.Exec(t.Context(), `UPDATE audit_events SET action = 'tampered'`); err == nil {
		t.Fatal("audit_events accepted an UPDATE")
	}
	if _, err := pool.Exec(t.Context(), `DELETE FROM audit_events`); err == nil {
		t.Fatal("audit_events accepted a DELETE")
	}
	if _, err := pool.Exec(t.Context(), `TRUNCATE audit_events`); err == nil {
		t.Fatal("audit_events accepted a TRUNCATE")
	}
}

func assertAuditActions(t *testing.T, pool *pgxpool.Pool, minimum map[string]int) {
	t.Helper()
	for action, want := range minimum {
		var count int
		if err := pool.QueryRow(t.Context(), `SELECT count(*) FROM audit_events WHERE action = $1`, action).Scan(&count); err != nil {
			t.Fatalf("count audit action %s: %v", action, err)
		}
		if count < want {
			t.Fatalf("audit action %s count = %d, want at least %d", action, count, want)
		}
	}
}

type mutableClock struct {
	mu  sync.Mutex
	now time.Time
}

func newMutableClock(now time.Time) *mutableClock { return &mutableClock{now: now} }

func (clock *mutableClock) Now() time.Time {
	clock.mu.Lock()
	defer clock.mu.Unlock()
	return clock.now
}

func (clock *mutableClock) Advance(duration time.Duration) {
	clock.mu.Lock()
	defer clock.mu.Unlock()
	clock.now = clock.now.Add(duration)
}
