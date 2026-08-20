package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
)

func (repository *PostgresPairingRepository) AuthenticateAdmin(ctx context.Context, tokenHash [sha256.Size]byte) (AdminPrincipal, error) {
	rows, err := repository.pool.Query(ctx, `
		SELECT a.id, COALESCE(m.mosque_id, ''), m.role
		FROM admin_credentials c
		JOIN admin_actors a ON a.id = c.actor_id
		JOIN admin_memberships m ON m.actor_id = a.id
		WHERE c.token_hash = $1 AND c.revoked_at IS NULL
		  AND (c.expires_at IS NULL OR c.expires_at > clock_timestamp())
		  AND a.status = 'active'
		ORDER BY m.role, m.mosque_id NULLS FIRST`, tokenHash[:])
	if err != nil {
		return AdminPrincipal{}, fmt.Errorf("authenticate admin: query credential: %w", err)
	}
	defer rows.Close()
	var principal AdminPrincipal
	for rows.Next() {
		var actorID, mosqueID, role string
		if err := rows.Scan(&actorID, &mosqueID, &role); err != nil {
			return AdminPrincipal{}, fmt.Errorf("authenticate admin: scan membership: %w", err)
		}
		if principal.ActorID == "" {
			principal.ActorID = actorID
		} else if principal.ActorID != actorID {
			return AdminPrincipal{}, errors.New("authenticate admin: credential resolved to multiple actors")
		}
		principal.Memberships = append(principal.Memberships, AdminMembership{MosqueID: mosqueID, Role: AdminRole(role)})
	}
	if err := rows.Err(); err != nil {
		return AdminPrincipal{}, fmt.Errorf("authenticate admin: iterate memberships: %w", err)
	}
	if principal.ActorID == "" {
		return AdminPrincipal{}, ErrAdminUnauthorized
	}
	return principal, nil
}

func (repository *PostgresPairingRepository) CreateAdminPairing(ctx context.Context, mutation AdminPairingMutation) (PairingRecord, error) {
	if mutation.Record.MosqueID != mutation.Scope.MosqueID || mutation.Record.ActorID != mutation.Scope.ActorID {
		return PairingRecord{}, ErrAdminResourceNotFound
	}
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return PairingRecord{}, fmt.Errorf("admin issue pairing: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if err := authorizeAdminScope(ctx, tx, mutation.Scope, true); err != nil {
		return PairingRecord{}, err
	}
	if err := lockAdminIdempotency(ctx, tx, mutation.IdempotencyHash); err != nil {
		return PairingRecord{}, fmt.Errorf("admin issue pairing: lock idempotency: %w", err)
	}
	existingResource, _, found, err := readAdminRequest(ctx, tx, mutation.Scope, "issue_pairing", mutation.IdempotencyHash, mutation.RequestHash)
	if err != nil {
		return PairingRecord{}, err
	}
	if found {
		record, err := readPairingRecord(ctx, tx, existingResource)
		if err != nil {
			return PairingRecord{}, err
		}
		if err := tx.Commit(ctx); err != nil {
			return PairingRecord{}, fmt.Errorf("admin issue pairing: commit idempotent read: %w", err)
		}
		return record, nil
	}

	var mosqueStatus string
	var identity MosqueIdentity
	if err := tx.QueryRow(ctx, `
		SELECT id, name, timezone_id, status FROM mosques WHERE id = $1`, mutation.Scope.MosqueID).Scan(
		&identity.ID, &identity.Name, &identity.Timezone, &mosqueStatus,
	); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return PairingRecord{}, ErrAdminResourceNotFound
		}
		return PairingRecord{}, fmt.Errorf("admin issue pairing: read mosque: %w", err)
	}
	if mosqueStatus != "active" || validateMosqueIdentity(identity) != nil {
		return PairingRecord{}, ErrAdminResourceNotFound
	}
	record := mutation.Record
	if _, err := tx.Exec(ctx, `
		INSERT INTO devices (id, mosque_id, status, created_at)
		VALUES ($1, $2, 'pending', $3)`, record.DeviceID, record.MosqueID, record.CreatedAt); err != nil {
		return PairingRecord{}, mapCredentialWriteError("admin issue pairing: insert device", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO pairing_codes (
			id, device_id, mosque_id, code_hash, expires_at, issued_by_actor_id, created_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
		record.ID, record.DeviceID, record.MosqueID, record.CodeHash[:], record.ExpiresAt, record.ActorID, record.CreatedAt,
	); err != nil {
		return PairingRecord{}, mapCredentialWriteError("admin issue pairing: insert code", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, after_hash, reason, request_id
		) VALUES ($1, $2, 'admin', $3, $4, 'device.pairing_issued', 'pairing_code', $5, $6, $7, $8)`,
		record.AuditID, record.CreatedAt, record.ActorID, record.MosqueID, record.ID,
		record.AfterHash[:], record.Reason, record.RequestID,
	); err != nil {
		return PairingRecord{}, fmt.Errorf("admin issue pairing: append audit: %w", err)
	}
	if err := insertAdminRequest(ctx, tx, mutation.Scope, "issue_pairing", record.ID, mutation.IdempotencyHash, mutation.RequestHash, nil); err != nil {
		return PairingRecord{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return PairingRecord{}, fmt.Errorf("admin issue pairing: commit: %w", err)
	}
	return record, nil
}

func (repository *PostgresPairingRepository) ListAdminDevices(ctx context.Context, scope AdminRepositoryScope) ([]FleetDevice, error) {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return nil, fmt.Errorf("admin list devices: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if err := authorizeAdminScope(ctx, tx, scope, false); err != nil {
		return nil, err
	}
	var mosqueActive bool
	if err := tx.QueryRow(ctx, `
		SELECT status = 'active' FROM mosques WHERE id = $1`, scope.MosqueID).Scan(&mosqueActive); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrAdminResourceNotFound
		}
		return nil, fmt.Errorf("admin list devices: lock mosque: %w", err)
	}
	if !mosqueActive {
		return nil, ErrAdminResourceNotFound
	}
	rows, err := tx.Query(ctx, `
		SELECT d.id, d.mosque_id, d.status, COALESCE(d.app_version, ''), COALESCE(d.os_version, ''),
		       COALESCE(d.model, ''), d.created_at, d.paired_at, d.revoked_at,
		       COALESCE(a.snapshot_id, ''), COALESCE(a.manifest_version, 0)
		FROM devices d
		LEFT JOIN device_assignments a ON a.device_id = d.id AND a.mosque_id = d.mosque_id
		WHERE d.mosque_id = $1
		ORDER BY d.created_at DESC, d.id`, scope.MosqueID)
	if err != nil {
		return nil, fmt.Errorf("admin list devices: query: %w", err)
	}
	defer rows.Close()
	var devices []FleetDevice
	for rows.Next() {
		var device FleetDevice
		if err := rows.Scan(
			&device.DeviceID, &device.MosqueID, &device.Status, &device.AppVersion, &device.OSVersion,
			&device.Model, &device.CreatedAt, &device.PairedAt, &device.RevokedAt,
			&device.SnapshotID, &device.ManifestVersion,
		); err != nil {
			return nil, fmt.Errorf("admin list devices: scan: %w", err)
		}
		devices = append(devices, device)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("admin list devices: iterate: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("admin list devices: commit read: %w", err)
	}
	return devices, nil
}

func (repository *PostgresPairingRepository) RevokeAdminDevice(ctx context.Context, mutation AdminRevocationMutation) error {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("admin revoke device: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if err := authorizeAdminScope(ctx, tx, mutation.Scope, true); err != nil {
		return err
	}
	if err := lockAdminIdempotency(ctx, tx, mutation.IdempotencyHash); err != nil {
		return fmt.Errorf("admin revoke device: lock idempotency: %w", err)
	}
	_, _, found, err := readAdminRequest(ctx, tx, mutation.Scope, "revoke_device", mutation.IdempotencyHash, mutation.RequestHash)
	if err != nil {
		return err
	}
	if found {
		return commitTransaction(ctx, tx, "admin revoke device: commit idempotent read")
	}
	var status string
	var tokenHash []byte
	if err := tx.QueryRow(ctx, `
		SELECT status, token_hash FROM devices
		WHERE id = $1 AND mosque_id = $2 FOR UPDATE`, mutation.DeviceID, mutation.Scope.MosqueID).Scan(&status, &tokenHash); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrAdminResourceNotFound
		}
		return fmt.Errorf("admin revoke device: lock device: %w", err)
	}
	if status == "revoked" {
		return ErrAdminResourceNotFound
	}
	beforeHash := sha256.Sum256([]byte(status + "\x00" + fmt.Sprintf("%x", tokenHash)))
	if _, err := tx.Exec(ctx, `
		UPDATE devices SET status = 'revoked', token_hash = NULL, revoked_at = $3
		WHERE id = $1 AND mosque_id = $2`, mutation.DeviceID, mutation.Scope.MosqueID, mutation.RevokedAt); err != nil {
		return fmt.Errorf("admin revoke device: update: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		UPDATE pairing_codes SET revoked_at = COALESCE(revoked_at, $3)
		WHERE device_id = $1 AND mosque_id = $2 AND uses_count = 0`,
		mutation.DeviceID, mutation.Scope.MosqueID, mutation.RevokedAt,
	); err != nil {
		return fmt.Errorf("admin revoke device: close pairing codes: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, before_hash, after_hash, reason, request_id
		) VALUES ($1, $2, 'admin', $3, $4, 'device.revoked', 'device', $5, $6, $7, $8, $9)`,
		mutation.AuditID, mutation.RevokedAt, mutation.Scope.ActorID, mutation.Scope.MosqueID,
		mutation.DeviceID, beforeHash[:], mutation.AfterHash[:], mutation.Reason, mutation.RequestID,
	); err != nil {
		return fmt.Errorf("admin revoke device: append audit: %w", err)
	}
	if err := insertAdminRequest(ctx, tx, mutation.Scope, "revoke_device", mutation.DeviceID, mutation.IdempotencyHash, mutation.RequestHash, nil); err != nil {
		return err
	}
	return commitTransaction(ctx, tx, "admin revoke device: commit")
}

func (repository *PostgresPairingRepository) AssignAdminDevice(ctx context.Context, mutation AdminAssignmentMutation) (DeviceAssignment, error) {
	if mutation.Command.MosqueID != mutation.Scope.MosqueID ||
		mutation.Command.DeviceID != mutation.Assignment.DeviceID {
		return DeviceAssignment{}, ErrAdminResourceNotFound
	}
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if err := authorizeAdminScope(ctx, tx, mutation.Scope, true); err != nil {
		return DeviceAssignment{}, err
	}
	if err := lockAdminIdempotency(ctx, tx, mutation.IdempotencyHash); err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: lock idempotency: %w", err)
	}
	existingResource, response, found, err := readAdminRequest(ctx, tx, mutation.Scope, "assign_device", mutation.IdempotencyHash, mutation.RequestHash)
	if err != nil {
		return DeviceAssignment{}, err
	}
	if found {
		var assignment DeviceAssignment
		if len(response) == 0 || json.Unmarshal(response, &assignment) != nil || assignment.DeviceID != existingResource {
			return DeviceAssignment{}, errors.New("admin assignment idempotency response is invalid")
		}
		if err := tx.Commit(ctx); err != nil {
			return DeviceAssignment{}, fmt.Errorf("admin assign device: commit idempotent read: %w", err)
		}
		return assignment, nil
	}
	var deviceStatus, timezoneID string
	if err := tx.QueryRow(ctx, `
		SELECT d.status, m.timezone_id
		FROM devices d JOIN mosques m ON m.id = d.mosque_id
		WHERE d.id = $1 AND d.mosque_id = $2 AND m.status = 'active'
		FOR UPDATE OF d`, mutation.Assignment.DeviceID, mutation.Scope.MosqueID).Scan(&deviceStatus, &timezoneID); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return DeviceAssignment{}, ErrAdminResourceNotFound
		}
		return DeviceAssignment{}, fmt.Errorf("admin assign device: lock device: %w", err)
	}
	if deviceStatus == "revoked" || mutation.Command.SnapshotMosqueID != mutation.Scope.MosqueID || mutation.Command.SnapshotTimezone != timezoneID {
		return DeviceAssignment{}, ErrAdminResourceNotFound
	}
	var previous *DeviceAssignment
	current, err := readDeviceAssignment(ctx, tx, mutation.Assignment.DeviceID, mutation.Scope.MosqueID)
	if err == nil {
		previous = &current
	} else if !errors.Is(err, ErrDeviceAssignmentNotFound) {
		return DeviceAssignment{}, err
	}
	assignment := mutation.Assignment
	assignment.ManifestVersion = 1
	if previous != nil {
		assignment.ManifestVersion = previous.ManifestVersion + 1
	}
	assignmentTag, err := tx.Exec(ctx, `
		INSERT INTO device_assignments (
			device_id, mosque_id, manifest_version, snapshot_id, snapshot_url,
			snapshot_sha256, signing_key_id, minimum_app_version, assigned_by_actor_id, assigned_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, NULLIF($8, ''), $9, $10)
		ON CONFLICT (device_id) DO UPDATE SET
			manifest_version = EXCLUDED.manifest_version,
			snapshot_id = EXCLUDED.snapshot_id,
			snapshot_url = EXCLUDED.snapshot_url,
			snapshot_sha256 = EXCLUDED.snapshot_sha256,
			signing_key_id = EXCLUDED.signing_key_id,
			minimum_app_version = EXCLUDED.minimum_app_version,
			assigned_by_actor_id = EXCLUDED.assigned_by_actor_id,
			assigned_at = EXCLUDED.assigned_at
		WHERE device_assignments.mosque_id = EXCLUDED.mosque_id`,
		assignment.DeviceID, mutation.Scope.MosqueID, assignment.ManifestVersion,
		assignment.SnapshotID, assignment.SnapshotURL, assignment.SnapshotSHA256,
		assignment.SigningKeyID, assignment.MinimumAppVersion, mutation.Scope.ActorID, mutation.AssignedAt,
	)
	if err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: upsert assignment: %w", err)
	}
	if assignmentTag.RowsAffected() != 1 {
		return DeviceAssignment{}, ErrAdminResourceNotFound
	}
	var beforeHash any
	if previous != nil {
		digest := hashDeviceAssignment(*previous, mutation.Scope.MosqueID)
		beforeHash = digest[:]
	}
	afterHash := hashDeviceAssignment(assignment, mutation.Scope.MosqueID)
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, before_hash, after_hash, reason, request_id
		) VALUES ($1, $2, 'admin', $3, $4, 'device.assigned', 'device', $5, $6, $7, $8, $9)`,
		mutation.AuditID, mutation.AssignedAt, mutation.Scope.ActorID, mutation.Scope.MosqueID,
		assignment.DeviceID, beforeHash, afterHash[:], mutation.Command.Reason, mutation.Command.RequestID,
	); err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: append audit: %w", err)
	}
	response, err = json.Marshal(assignment)
	if err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: encode idempotency response: %w", err)
	}
	if err := insertAdminRequest(ctx, tx, mutation.Scope, "assign_device", assignment.DeviceID, mutation.IdempotencyHash, mutation.RequestHash, response); err != nil {
		return DeviceAssignment{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return DeviceAssignment{}, fmt.Errorf("admin assign device: commit: %w", err)
	}
	return assignment, nil
}

func (repository *PostgresPairingRepository) ReadAdminAssignmentRetry(
	ctx context.Context,
	scope AdminRepositoryScope,
	idempotencyHash, requestHash [sha256.Size]byte,
) (DeviceAssignment, bool, error) {
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceAssignment{}, false, fmt.Errorf("admin retry assignment: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)
	if err := authorizeAdminScope(ctx, tx, scope, true); err != nil {
		return DeviceAssignment{}, false, err
	}
	resourceID, response, found, err := readAdminRequest(ctx, tx, scope, "assign_device", idempotencyHash, requestHash)
	if err != nil {
		return DeviceAssignment{}, false, err
	}
	if !found {
		return DeviceAssignment{}, false, commitTransaction(ctx, tx, "admin retry assignment: commit miss")
	}
	var assignment DeviceAssignment
	if len(response) == 0 || json.Unmarshal(response, &assignment) != nil || assignment.DeviceID != resourceID {
		return DeviceAssignment{}, false, errors.New("admin retry assignment: stored response is invalid")
	}
	if err := tx.Commit(ctx); err != nil {
		return DeviceAssignment{}, false, fmt.Errorf("admin retry assignment: commit: %w", err)
	}
	return assignment, true, nil
}

func hashDeviceAssignment(assignment DeviceAssignment, mosqueID string) [sha256.Size]byte {
	return hashAdminRequest(
		"device_assignment", assignment.DeviceID, mosqueID,
		fmt.Sprint(assignment.ManifestVersion), assignment.SnapshotID,
		assignment.SnapshotURL, assignment.SnapshotSHA256,
		assignment.SigningKeyID, assignment.MinimumAppVersion,
	)
}

func (repository *PostgresPairingRepository) GetDeviceAssignment(ctx context.Context, deviceID, mosqueID string) (DeviceAssignment, error) {
	return readDeviceAssignment(ctx, repository.pool, deviceID, mosqueID)
}

type assignmentQuerier interface {
	QueryRow(context.Context, string, ...any) pgx.Row
}

func readDeviceAssignment(ctx context.Context, querier assignmentQuerier, deviceID, mosqueID string) (DeviceAssignment, error) {
	var assignment DeviceAssignment
	err := querier.QueryRow(ctx, `
		SELECT device_id, manifest_version, snapshot_id, snapshot_url, snapshot_sha256,
		       signing_key_id, COALESCE(minimum_app_version, '')
		FROM device_assignments WHERE device_id = $1 AND mosque_id = $2`, deviceID, mosqueID).Scan(
		&assignment.DeviceID, &assignment.ManifestVersion, &assignment.SnapshotID, &assignment.SnapshotURL,
		&assignment.SnapshotSHA256, &assignment.SigningKeyID, &assignment.MinimumAppVersion,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeviceAssignment{}, ErrDeviceAssignmentNotFound
	}
	if err != nil {
		return DeviceAssignment{}, fmt.Errorf("read device assignment: %w", err)
	}
	return assignment, nil
}

func readPairingRecord(ctx context.Context, tx pgx.Tx, pairingID string) (PairingRecord, error) {
	var record PairingRecord
	var codeHash []byte
	err := tx.QueryRow(ctx, `
		SELECT pc.id, pc.device_id, pc.mosque_id, pc.code_hash, pc.expires_at,
		       pc.created_at, pc.issued_by_actor_id
		FROM pairing_codes pc WHERE pc.id = $1`, pairingID).Scan(
		&record.ID, &record.DeviceID, &record.MosqueID, &codeHash,
		&record.ExpiresAt, &record.CreatedAt, &record.ActorID,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return PairingRecord{}, errors.New("admin idempotency resource is missing")
	}
	if err != nil {
		return PairingRecord{}, fmt.Errorf("read idempotent pairing: %w", err)
	}
	if len(codeHash) != sha256.Size {
		return PairingRecord{}, errors.New("read idempotent pairing: code hash is invalid")
	}
	copy(record.CodeHash[:], codeHash)
	record.ExpiresAt = record.ExpiresAt.UTC()
	record.CreatedAt = record.CreatedAt.UTC()
	return record, nil
}

func authorizeAdminScope(ctx context.Context, tx pgx.Tx, scope AdminRepositoryScope, write bool) error {
	roles := []string{string(AdminRoleMosqueAdmin)}
	if !write {
		roles = append(roles, string(AdminRoleApprover), string(AdminRoleViewerSupport))
	}
	var role string
	var mosqueID *string
	err := tx.QueryRow(ctx, `
		SELECT m.role, m.mosque_id
		FROM admin_actors a
		JOIN admin_memberships m ON m.actor_id = a.id
		WHERE a.id = $1 AND a.status = 'active'
		  AND (
			($3 AND m.role = 'service_admin' AND m.mosque_id IS NULL) OR
			(NOT $3 AND m.mosque_id = $2 AND m.role = ANY($4::text[]))
		  )
		LIMIT 1`, scope.ActorID, scope.MosqueID, scope.GlobalAdmin, roles).Scan(&role, &mosqueID)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrAdminResourceNotFound
	}
	if err != nil {
		return fmt.Errorf("authorize admin scope: %w", err)
	}
	return nil
}

func lockAdminIdempotency(ctx context.Context, tx pgx.Tx, hash [sha256.Size]byte) error {
	key := int64(binary.BigEndian.Uint64(hash[:8]))
	_, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, key)
	return err
}

func readAdminRequest(
	ctx context.Context,
	tx pgx.Tx,
	scope AdminRepositoryScope,
	operation string,
	idempotencyHash, requestHash [sha256.Size]byte,
) (string, []byte, bool, error) {
	var storedRequestHash []byte
	var response []byte
	var actorID, mosqueID, storedOperation, resourceID string
	var active bool
	err := tx.QueryRow(ctx, `
		SELECT request_hash, actor_id, mosque_id, operation, resource_id, response,
		       expires_at > clock_timestamp()
		FROM admin_requests WHERE idempotency_hash = $1`, idempotencyHash[:]).Scan(
		&storedRequestHash, &actorID, &mosqueID, &storedOperation, &resourceID, &response, &active,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", nil, false, nil
	}
	if err != nil {
		return "", nil, false, fmt.Errorf("read admin idempotency request: %w", err)
	}
	if !bytes.Equal(storedRequestHash, requestHash[:]) || actorID != scope.ActorID ||
		mosqueID != scope.MosqueID || storedOperation != operation {
		return "", nil, false, ErrAdminIdempotencyConflict
	}
	if !active {
		return "", nil, false, ErrAdminIdempotencyConflict
	}
	return resourceID, response, true, nil
}

func insertAdminRequest(
	ctx context.Context,
	tx pgx.Tx,
	scope AdminRepositoryScope,
	operation, resourceID string,
	idempotencyHash, requestHash [sha256.Size]byte,
	response []byte,
) error {
	if _, err := tx.Exec(ctx, `
		INSERT INTO admin_requests (
			idempotency_hash, request_hash, actor_id, mosque_id, operation, resource_id, response, created_at, expires_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, clock_timestamp(), clock_timestamp() + interval '24 hours')`,
		idempotencyHash[:], requestHash[:], scope.ActorID, scope.MosqueID, operation, resourceID,
		response,
	); err != nil {
		return fmt.Errorf("record admin idempotency request: %w", err)
	}
	return nil
}
