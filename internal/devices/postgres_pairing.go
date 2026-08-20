package devices

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/*.sql
var pairingMigrations embed.FS

// PairingSchemaVersion is the exact PostgreSQL schema version required by the
// current API binary.
const PairingSchemaVersion = 4

const postgresRollbackTimeout = 2 * time.Second

type PostgresPairingRepository struct {
	pool *pgxpool.Pool
}

func NewPostgresPairingRepository(pool *pgxpool.Pool) *PostgresPairingRepository {
	return &PostgresPairingRepository{pool: pool}
}

func OpenPostgresPairingRepository(ctx context.Context, databaseURL string) (*PostgresPairingRepository, error) {
	if strings.TrimSpace(databaseURL) == "" {
		return nil, errors.New("open PostgreSQL pairing repository: database URL is required")
	}
	config, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, errors.New("open PostgreSQL pairing repository: parse database URL")
	}
	if err := validatePostgresTransport(config); err != nil {
		return nil, fmt.Errorf("open PostgreSQL pairing repository: %w", err)
	}
	config.MaxConns = 10
	config.MinConns = 1
	config.MaxConnLifetime = 30 * time.Minute
	config.MaxConnIdleTime = 5 * time.Minute
	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, errors.New("open PostgreSQL pairing repository: create pool")
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, errors.New("open PostgreSQL pairing repository: ping database")
	}
	return NewPostgresPairingRepository(pool), nil
}

func validatePostgresTransport(config *pgxpool.Config) error {
	if config == nil || config.ConnConfig == nil {
		return errors.New("PostgreSQL connection config is missing")
	}
	if err := validatePostgresEndpointTransport(
		config.ConnConfig.Host, config.ConnConfig.TLSConfig,
	); err != nil {
		return err
	}
	for _, fallback := range config.ConnConfig.Fallbacks {
		if err := validatePostgresEndpointTransport(fallback.Host, fallback.TLSConfig); err != nil {
			return err
		}
	}
	return nil
}

func validatePostgresEndpointTransport(host string, tlsConfig *tls.Config) error {
	if postgresEndpointIsLocal(host) {
		return nil
	}
	if tlsConfig == nil {
		return errors.New("remote PostgreSQL endpoint requires authenticated TLS")
	}
	if tlsConfig.InsecureSkipVerify && tlsConfig.VerifyPeerCertificate == nil && tlsConfig.VerifyConnection == nil {
		return errors.New("remote PostgreSQL endpoint requires server certificate verification")
	}
	return nil
}

func postgresEndpointIsLocal(host string) bool {
	if strings.HasPrefix(host, "/") || strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(strings.Trim(host, "[]"))
	return ip != nil && ip.IsLoopback()
}

func (repository *PostgresPairingRepository) Close() {
	if repository != nil && repository.pool != nil {
		repository.pool.Close()
	}
}

func (repository *PostgresPairingRepository) MigrateUp(ctx context.Context) error {
	return repository.MigrateTo(ctx, PairingSchemaVersion)
}

// MigrateTo moves the schema to an explicit known version. Production callers
// use this from the short-lived migration command, never from the API process.
func (repository *PostgresPairingRepository) MigrateTo(ctx context.Context, targetVersion int) error {
	if repository == nil || repository.pool == nil {
		return errors.New("migrate pairing schema: repository is not configured")
	}
	if targetVersion < 0 || targetVersion > PairingSchemaVersion {
		return fmt.Errorf("migrate pairing schema: target version %d is unknown", targetVersion)
	}
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("migrate pairing schema: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(70149822411011)`); err != nil {
		return fmt.Errorf("migrate pairing schema: acquire lock: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version bigint PRIMARY KEY,
			applied_at timestamptz NOT NULL
		)`); err != nil {
		return fmt.Errorf("migrate pairing schema: create migration ledger: %w", err)
	}
	if err := requireKnownPairingSchemaVersions(ctx, tx, "migrate pairing schema"); err != nil {
		return err
	}
	currentVersion, err := currentPairingSchemaVersion(ctx, tx, "migrate pairing schema")
	if err != nil {
		return err
	}
	for version := currentVersion + 1; version <= targetVersion; version++ {
		var applied bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE version = $1)`, version).Scan(&applied); err != nil {
			return fmt.Errorf("migrate pairing schema: inspect version %d: %w", version, err)
		}
		if applied {
			continue
		}
		migration, err := pairingMigrations.ReadFile(pairingMigrationPath(version, "up"))
		if err != nil {
			return fmt.Errorf("migrate pairing schema: read embedded migration %d: %w", version, err)
		}
		if _, err := tx.Exec(ctx, string(migration), pgx.QueryExecModeSimpleProtocol); err != nil {
			return fmt.Errorf("migrate pairing schema: apply version %d: %w", version, err)
		}
		if _, err := tx.Exec(ctx, `INSERT INTO schema_migrations (version, applied_at) VALUES ($1, clock_timestamp())`, version); err != nil {
			return fmt.Errorf("migrate pairing schema: record version %d: %w", version, err)
		}
	}
	for version := currentVersion; version > targetVersion; version-- {
		var applied bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE version = $1)`, version).Scan(&applied); err != nil {
			return fmt.Errorf("migrate pairing schema: inspect version %d for rollback: %w", version, err)
		}
		if !applied {
			continue
		}
		migration, err := pairingMigrations.ReadFile(pairingMigrationPath(version, "down"))
		if err != nil {
			return fmt.Errorf("migrate pairing schema: read embedded migration %d down: %w", version, err)
		}
		if _, err := tx.Exec(ctx, string(migration), pgx.QueryExecModeSimpleProtocol); err != nil {
			return fmt.Errorf("migrate pairing schema: apply version %d down: %w", version, err)
		}
		if _, err := tx.Exec(ctx, `DELETE FROM schema_migrations WHERE version = $1`, version); err != nil {
			return fmt.Errorf("migrate pairing schema: clear version %d: %w", version, err)
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("migrate pairing schema: commit: %w", err)
	}
	return nil
}

// VerifySchema checks the migration ledger without acquiring DDL privileges or
// changing database state. The long-running API calls this through its runtime
// role and fails closed unless the exact current schema is present.
func (repository *PostgresPairingRepository) VerifySchema(ctx context.Context) error {
	if repository == nil || repository.pool == nil {
		return errors.New("verify pairing schema: repository is not configured")
	}
	rows, err := repository.pool.Query(ctx, `SELECT version FROM schema_migrations ORDER BY version`)
	if err != nil {
		return fmt.Errorf("verify pairing schema: read migration ledger: %w", err)
	}
	defer rows.Close()
	expected := int64(1)
	for rows.Next() {
		var version int64
		if err := rows.Scan(&version); err != nil {
			return fmt.Errorf("verify pairing schema: scan migration ledger: %w", err)
		}
		if version != expected || version > PairingSchemaVersion {
			return errors.New("verify pairing schema: database schema version is newer or unknown")
		}
		expected++
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("verify pairing schema: read migration ledger: %w", err)
	}
	if expected-1 != PairingSchemaVersion {
		return fmt.Errorf("verify pairing schema: database schema is version %d, require %d", expected-1, PairingSchemaVersion)
	}
	return nil
}

func (repository *PostgresPairingRepository) CreatePairing(ctx context.Context, record PairingRecord) error {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("create pairing: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	var mosqueStatus string
	var mosqueIdentity MosqueIdentity
	if err := tx.QueryRow(ctx, `
		SELECT id, name, timezone_id, status
		FROM mosques WHERE id = $1`, record.MosqueID).Scan(
		&mosqueIdentity.ID, &mosqueIdentity.Name, &mosqueIdentity.Timezone, &mosqueStatus,
	); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrMosqueNotFound
		}
		return fmt.Errorf("create pairing: read mosque: %w", err)
	}
	if mosqueStatus != "active" {
		return ErrMosqueNotFound
	}
	if err := validateMosqueIdentity(mosqueIdentity); err != nil {
		return errors.New("create pairing: mosque identity is invalid")
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO devices (id, mosque_id, status, created_at)
		VALUES ($1, $2, 'pending', $3)`, record.DeviceID, record.MosqueID, record.CreatedAt); err != nil {
		return mapCredentialWriteError("create pairing: insert device", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO pairing_codes (
			id, device_id, mosque_id, code_hash, expires_at, max_uses, uses_count,
			issued_by_actor_id, created_at
		) VALUES ($1, $2, $3, $4, $5, 1, 0, $6, $7)`,
		record.ID, record.DeviceID, record.MosqueID, record.CodeHash[:], record.ExpiresAt,
		record.ActorID, record.CreatedAt,
	); err != nil {
		return mapCredentialWriteError("create pairing: insert code", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, after_hash, reason, request_id
		) VALUES ($1, $2, 'admin', $3, $4, 'device.pairing_issued', 'pairing_code', $5, $6, $7, $8)`,
		record.AuditID, record.CreatedAt, record.ActorID, record.MosqueID, record.ID,
		record.AfterHash[:], record.Reason, record.RequestID,
	); err != nil {
		return fmt.Errorf("create pairing: append audit: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("create pairing: commit: %w", err)
	}
	return nil
}

func (repository *PostgresPairingRepository) RedeemPairing(ctx context.Context, redemption PairingRedemption) (PairingDecision, error) {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	retentionThreshold := redemption.AttemptedAt.Add(-2 * redemption.RateLimits.Window)
	if _, err := tx.Exec(ctx, `
		DELETE FROM pairing_rate_buckets
		WHERE ctid IN (
			SELECT ctid FROM pairing_rate_buckets
			WHERE updated_at < $1
			ORDER BY updated_at
			LIMIT 100
		)`, retentionThreshold); err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: prune expired rate buckets: %w", err)
	}
	buckets := []struct {
		kind  string
		hash  [sha256.Size]byte
		limit int
	}{
		{kind: "source", hash: redemption.SourceBucket, limit: redemption.RateLimits.SourceAttempts},
		{kind: "device", hash: redemption.DeviceBucket, limit: redemption.RateLimits.DeviceAttempts},
		{kind: "code", hash: redemption.CodeBucket, limit: redemption.RateLimits.CodeAttempts},
	}
	for _, bucket := range buckets {
		attempts, err := bumpPairingRateBucket(ctx, tx, bucket.kind, bucket.hash, redemption.AttemptedAt, redemption.RateLimits.Window)
		if err != nil {
			return PairingDecision{}, err
		}
		if attempts > bucket.limit {
			return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeRateLimited})
		}
	}

	var pairingID, deviceID, mosqueID, mosqueName, timezoneID, mosqueStatus, deviceStatus string
	var expiresAt time.Time
	var usesCount, maxUses int
	var consumedAt, codeRevokedAt *time.Time
	err = tx.QueryRow(ctx, `
		SELECT pc.id, d.id, m.id, m.name, m.timezone_id, m.status, d.status,
		       pc.expires_at, pc.uses_count, pc.max_uses, pc.consumed_at, pc.revoked_at
		FROM pairing_codes pc
		JOIN devices d ON d.id = pc.device_id AND d.mosque_id = pc.mosque_id
		JOIN mosques m ON m.id = pc.mosque_id
		WHERE pc.code_hash = $1
		FOR UPDATE OF pc, d`, redemption.CodeHash[:]).Scan(
		&pairingID, &deviceID, &mosqueID, &mosqueName, &timezoneID, &mosqueStatus, &deviceStatus,
		&expiresAt, &usesCount, &maxUses, &consumedAt, &codeRevokedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeInvalid})
	}
	if err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: lock code: %w", err)
	}
	mosqueIdentity := MosqueIdentity{ID: mosqueID, Name: mosqueName, Timezone: timezoneID}
	if err := validateMosqueIdentity(mosqueIdentity); err != nil {
		return PairingDecision{}, errors.New("redeem pairing: mosque identity is invalid")
	}
	if mosqueStatus != "active" {
		return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeInvalid})
	}
	if codeRevokedAt != nil || deviceStatus == "revoked" {
		return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeRevoked})
	}
	if usesCount >= maxUses || consumedAt != nil || deviceStatus != "pending" {
		return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeConsumed})
	}
	if !redemption.AttemptedAt.Before(expiresAt) {
		return commitPairingDecision(ctx, tx, PairingDecision{Outcome: PairingOutcomeExpired})
	}
	capabilityValues := redemption.Device.Capabilities
	if capabilityValues == nil {
		capabilityValues = []string{}
	}
	capabilities, err := json.Marshal(capabilityValues)
	if err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: encode capabilities: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		UPDATE pairing_codes
		SET uses_count = 1, consumed_at = $2
		WHERE id = $1`, pairingID, redemption.AttemptedAt); err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: consume code: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		UPDATE devices
		SET status = 'active', token_hash = $2, installation_public_key = NULLIF($3, ''),
		    app_version = $4, os_version = $5, model = $6, capabilities = $7::jsonb,
		    paired_at = $8
		WHERE id = $1`, deviceID, redemption.TokenHash[:], redemption.InstallationPublicKey,
		redemption.Device.AppVersion, redemption.Device.OSVersion, redemption.Device.Model,
		string(capabilities), redemption.AttemptedAt,
	); err != nil {
		return PairingDecision{}, mapCredentialWriteError("redeem pairing: activate device", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, after_hash, reason, request_id
		) VALUES ($1, $2, 'device', $3, $4, 'device.paired', 'device', $3, $5, 'pairing code redeemed', $6)`,
		redemption.AuditID, redemption.AttemptedAt, deviceID, mosqueID,
		redemption.AfterHash[:], redemption.RequestID,
	); err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: append audit: %w", err)
	}
	decision := PairingDecision{
		Outcome: PairingOutcomePaired,
		Device:  DevicePrincipal{DeviceID: deviceID, Mosque: mosqueIdentity},
	}
	return commitPairingDecision(ctx, tx, decision)
}

func (repository *PostgresPairingRepository) AuthenticateDevice(ctx context.Context, tokenHash [sha256.Size]byte) (DevicePrincipal, error) {
	var principal DevicePrincipal
	err := repository.pool.QueryRow(ctx, `
		SELECT d.id, m.id, m.name, m.timezone_id
		FROM devices d
		JOIN mosques m ON m.id = d.mosque_id
		WHERE d.token_hash = $1 AND d.status = 'active' AND m.status = 'active'`, tokenHash[:]).Scan(
		&principal.DeviceID, &principal.Mosque.ID, &principal.Mosque.Name, &principal.Mosque.Timezone,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DevicePrincipal{}, ErrDeviceUnauthorized
	}
	if err != nil {
		return DevicePrincipal{}, fmt.Errorf("authenticate device: query credential: %w", err)
	}
	return principal, nil
}

func (repository *PostgresPairingRepository) RevokeDevice(ctx context.Context, revocation DeviceRevocation) error {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("revoke device: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	var status string
	var tokenHash []byte
	err = tx.QueryRow(ctx, `
		SELECT status, token_hash FROM devices
		WHERE id = $1 AND mosque_id = $2
		FOR UPDATE`, revocation.DeviceID, revocation.MosqueID).Scan(&status, &tokenHash)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrDeviceNotFound
	}
	if err != nil {
		return fmt.Errorf("revoke device: lock device: %w", err)
	}
	if status == "revoked" {
		return commitTransaction(ctx, tx, "revoke device: commit unchanged")
	}
	beforeHash := sha256.Sum256([]byte(status + "\x00" + fmt.Sprintf("%x", tokenHash)))
	if _, err := tx.Exec(ctx, `
		UPDATE devices
		SET status = 'revoked', token_hash = NULL, revoked_at = $3
		WHERE id = $1 AND mosque_id = $2`, revocation.DeviceID, revocation.MosqueID, revocation.RevokedAt); err != nil {
		return fmt.Errorf("revoke device: update device: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		UPDATE pairing_codes
		SET revoked_at = COALESCE(revoked_at, $3)
		WHERE device_id = $1 AND mosque_id = $2 AND uses_count = 0`,
		revocation.DeviceID, revocation.MosqueID, revocation.RevokedAt,
	); err != nil {
		return fmt.Errorf("revoke device: close pairing codes: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, before_hash, after_hash, reason, request_id
		) VALUES ($1, $2, 'admin', $3, $4, 'device.revoked', 'device', $5, $6, $7, $8, $9)`,
		revocation.AuditID, revocation.RevokedAt, revocation.ActorID, revocation.MosqueID,
		revocation.DeviceID, beforeHash[:], revocation.AfterHash[:], revocation.Reason, revocation.RequestID,
	); err != nil {
		return fmt.Errorf("revoke device: append audit: %w", err)
	}
	return commitTransaction(ctx, tx, "revoke device: commit")
}

func (repository *PostgresPairingRepository) RecordHeartbeat(ctx context.Context, heartbeat DeviceHeartbeat) error {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("record heartbeat: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	var deviceStatus, mosqueStatus string
	var lastSeenAt *time.Time
	if err := tx.QueryRow(ctx, `
		SELECT d.status, m.status, d.last_seen_at
		FROM devices d
		JOIN mosques m ON m.id = d.mosque_id
		WHERE d.id = $1 AND d.mosque_id = $2
		FOR UPDATE OF d`, heartbeat.DeviceID, heartbeat.MosqueID).Scan(
		&deviceStatus, &mosqueStatus, &lastSeenAt,
	); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrDeviceUnauthorized
		}
		return fmt.Errorf("record heartbeat: lock device: %w", err)
	}
	if deviceStatus != "active" || mosqueStatus != "active" {
		return ErrDeviceUnauthorized
	}
	if lastSeenAt != nil && heartbeat.ReceivedAt.Before(*lastSeenAt) {
		return commitTransaction(ctx, tx, "record heartbeat: commit stale server time")
	}
	if _, err := tx.Exec(ctx, `
		UPDATE devices SET last_seen_at = $3, app_version = $4,
		       os_version = NULLIF($5, ''), model = NULLIF($6, '')
		WHERE id = $1 AND mosque_id = $2`,
		heartbeat.DeviceID, heartbeat.MosqueID, heartbeat.ReceivedAt,
		heartbeat.AppVersion, heartbeat.OSVersion, heartbeat.Model,
	); err != nil {
		return fmt.Errorf("record heartbeat: update device: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO device_health (
			device_id, mosque_id, reported_at, received_at, reported_snapshot_id,
			sync_status, coverage_days_remaining, clock_mismatch, timezone_mismatch,
			storage_health, memory_health, boot_mode, kiosk_mode
		) VALUES ($1, $2, $3, $4, NULLIF($5, ''), $6, $7, $8, $9, $10, $11, $12, $13)
		ON CONFLICT (device_id) DO UPDATE SET
			mosque_id = EXCLUDED.mosque_id,
			reported_at = EXCLUDED.reported_at,
			received_at = EXCLUDED.received_at,
			reported_snapshot_id = EXCLUDED.reported_snapshot_id,
			sync_status = EXCLUDED.sync_status,
			coverage_days_remaining = EXCLUDED.coverage_days_remaining,
			clock_mismatch = EXCLUDED.clock_mismatch,
			timezone_mismatch = EXCLUDED.timezone_mismatch,
			storage_health = EXCLUDED.storage_health,
			memory_health = EXCLUDED.memory_health,
			boot_mode = EXCLUDED.boot_mode,
			kiosk_mode = EXCLUDED.kiosk_mode
		WHERE device_health.mosque_id = EXCLUDED.mosque_id
		  AND device_health.received_at <= EXCLUDED.received_at`,
		heartbeat.DeviceID, heartbeat.MosqueID, heartbeat.SentAt, heartbeat.ReceivedAt,
		heartbeat.ActiveSnapshotID, heartbeat.SyncStatus, heartbeat.CoverageDaysRemaining,
		heartbeat.ClockMismatch, heartbeat.TimezoneMismatch, heartbeat.StorageHealth,
		heartbeat.MemoryHealth, heartbeat.BootMode, heartbeat.KioskMode,
	); err != nil {
		return fmt.Errorf("record heartbeat: upsert latest health: %w", err)
	}
	return commitTransaction(ctx, tx, "record heartbeat: commit")
}

func bumpPairingRateBucket(
	ctx context.Context,
	tx pgx.Tx,
	kind string,
	hash [sha256.Size]byte,
	now time.Time,
	window time.Duration,
) (int, error) {
	var attempts int
	windowThreshold := now.Add(-window)
	err := tx.QueryRow(ctx, `
		INSERT INTO pairing_rate_buckets (
			bucket_kind, bucket_hash, window_started_at, attempts, updated_at
		) VALUES ($1, $2, $3, 1, $3)
		ON CONFLICT (bucket_kind, bucket_hash) DO UPDATE SET
			window_started_at = CASE
				WHEN pairing_rate_buckets.window_started_at <= $4 THEN EXCLUDED.window_started_at
				ELSE pairing_rate_buckets.window_started_at
			END,
			attempts = CASE
				WHEN pairing_rate_buckets.window_started_at <= $4 THEN 1
				ELSE pairing_rate_buckets.attempts + 1
			END,
			updated_at = GREATEST(pairing_rate_buckets.updated_at, EXCLUDED.updated_at)
		RETURNING attempts`, kind, hash[:], now, windowThreshold).Scan(&attempts)
	if err != nil {
		return 0, fmt.Errorf("redeem pairing: update %s rate bucket: %w", kind, err)
	}
	return attempts, nil
}

func commitPairingDecision(ctx context.Context, tx pgx.Tx, decision PairingDecision) (PairingDecision, error) {
	if err := tx.Commit(ctx); err != nil {
		return PairingDecision{}, fmt.Errorf("redeem pairing: commit outcome: %w", err)
	}
	return decision, nil
}

func commitTransaction(ctx context.Context, tx pgx.Tx, operation string) error {
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("%s: %w", operation, err)
	}
	return nil
}

func rollbackTransaction(tx pgx.Tx) {
	ctx, cancel := context.WithTimeout(context.Background(), postgresRollbackTimeout)
	defer cancel()
	_ = tx.Rollback(ctx)
}

func mapCredentialWriteError(operation string, err error) error {
	var postgresError *pgconn.PgError
	if errors.As(err, &postgresError) && postgresError.Code == "23505" {
		return fmt.Errorf("%s: generated credential collision", operation)
	}
	return fmt.Errorf("%s: %w", operation, err)
}

func requireKnownPairingSchemaVersions(ctx context.Context, tx pgx.Tx, operation string) error {
	rows, err := tx.Query(ctx, `SELECT version FROM schema_migrations ORDER BY version`)
	if err != nil {
		return fmt.Errorf("%s: inspect migration ledger: %w", operation, err)
	}
	defer rows.Close()
	expected := int64(1)
	for rows.Next() {
		var version int64
		if err := rows.Scan(&version); err != nil {
			return fmt.Errorf("%s: inspect migration version: %w", operation, err)
		}
		if version != expected || version > PairingSchemaVersion {
			return fmt.Errorf("%s: database schema version is newer or unknown", operation)
		}
		expected++
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("%s: inspect migration ledger: %w", operation, err)
	}
	return nil
}

func currentPairingSchemaVersion(ctx context.Context, tx pgx.Tx, operation string) (int, error) {
	rows, err := tx.Query(ctx, `SELECT version FROM schema_migrations ORDER BY version`)
	if err != nil {
		return 0, fmt.Errorf("%s: inspect migration ledger: %w", operation, err)
	}
	defer rows.Close()
	expected := int64(1)
	for rows.Next() {
		var version int64
		if err := rows.Scan(&version); err != nil {
			return 0, fmt.Errorf("%s: inspect migration version: %w", operation, err)
		}
		if version != expected || version > PairingSchemaVersion {
			return 0, fmt.Errorf("%s: database schema version is newer or unknown", operation)
		}
		expected++
	}
	if err := rows.Err(); err != nil {
		return 0, fmt.Errorf("%s: inspect migration ledger: %w", operation, err)
	}
	return int(expected - 1), nil
}

func pairingMigrationPath(version int, direction string) string {
	name := "fleet_pairing"
	if version == 2 {
		name = "fleet_admin"
	} else if version == 3 {
		name = "device_health"
	} else if version == 4 {
		name = "rollout_groups"
	}
	return fmt.Sprintf("migrations/%06d_%s.%s.sql", version, name, direction)
}
