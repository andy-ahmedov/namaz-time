package devices

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

const deviceBindingAuditReason = "local TV operator selected an authoritative schedule choice"

func (repository *PostgresPairingRepository) CreateDeviceRegistryBindingRequest(
	ctx context.Context,
	request DeviceRegistryBindingRequest,
) (DeviceRegistryBindingRequest, error) {
	if repository == nil || repository.pool == nil || !validDeviceRegistryBindingRequest(request) {
		return DeviceRegistryBindingRequest{}, ErrInvalidDeviceSetupRequest
	}
	tx, err := repository.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: begin transaction: %w", err)
	}
	defer rollbackTransaction(tx)

	idempotencyHash := sha256.Sum256([]byte(request.DeviceID + "\x00" + request.InteractionID))
	if err := lockAdminIdempotency(ctx, tx, idempotencyHash); err != nil {
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: lock interaction: %w", err)
	}
	stored, found, err := readDeviceRegistryBindingRequest(ctx, tx, request.DeviceID, request.InteractionID)
	if err != nil {
		return DeviceRegistryBindingRequest{}, err
	}
	if found {
		if !sameDeviceRegistryBindingRequest(stored, request) {
			return DeviceRegistryBindingRequest{}, ErrDeviceSetupConflict
		}
		if err := tx.Commit(ctx); err != nil {
			return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: commit retry: %w", err)
		}
		return stored, nil
	}

	var deviceStatus, mosqueStatus string
	if err := tx.QueryRow(ctx, `
		SELECT device.status, mosque.status
		FROM devices device
		JOIN mosques mosque ON mosque.id = device.mosque_id
		WHERE device.id = $1 AND device.mosque_id = $2
		FOR SHARE OF device`, request.DeviceID, request.MosqueID).Scan(
		&deviceStatus, &mosqueStatus,
	); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return DeviceRegistryBindingRequest{}, ErrDeviceUnauthorized
		}
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: authorize device: %w", err)
	}
	if deviceStatus != "active" || mosqueStatus != "active" {
		return DeviceRegistryBindingRequest{}, ErrDeviceUnauthorized
	}
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, registry.PostgresLifecycleAdvisoryLockID); err != nil {
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: lock revision lifecycle: %w", err)
	}
	var staged bool
	if err := tx.QueryRow(ctx, `
		SELECT NOT EXISTS (
			SELECT 1 FROM registry_active_revision active WHERE active.revision_id = revision.id
		)
		FROM registry_revisions revision
		WHERE revision.id = $1`, request.RevisionID).Scan(&staged); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return DeviceRegistryBindingRequest{}, ErrDeviceSetupNotRequestable
		}
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: verify revision state: %w", err)
	}
	if !staged {
		return DeviceRegistryBindingRequest{}, ErrDeviceSetupNotRequestable
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO device_registry_binding_requests (
			id, revision_id, city_id, policy_id, choice_id, mosque_id, device_id,
			local_date, resolution_tier, status, selection_sha256, origin,
			interaction_id, requested_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8::date, $9, $10, $11, $12, $13, $14)`,
		request.ID, request.RevisionID, request.CityID, request.PolicyID, request.ChoiceID,
		request.MosqueID, request.DeviceID, request.Date, request.Tier, request.Status,
		request.SelectionSHA256, request.Origin, request.InteractionID, request.RequestedAt,
	); err != nil {
		var postgresError *pgconn.PgError
		if errors.As(err, &postgresError) && (postgresError.Code == "23503" || postgresError.Code == "23514") {
			return DeviceRegistryBindingRequest{}, ErrDeviceSetupNotRequestable
		}
		if errors.As(err, &postgresError) && postgresError.Code == "23505" {
			return DeviceRegistryBindingRequest{}, ErrDeviceSetupConflict
		}
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: insert request: %w", err)
	}
	afterHash, err := hex.DecodeString(request.SelectionSHA256)
	if err != nil {
		return DeviceRegistryBindingRequest{}, ErrInvalidDeviceSetupRequest
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO audit_events (
			id, occurred_at, actor_type, actor_id, mosque_id, action, entity_type,
			entity_id, after_hash, reason, request_id
		) VALUES ($1, $2, 'device', $3, $4, 'registry.binding_requested_from_tv',
			'device_registry_binding_request', $5, $6, $7, $8)`,
		request.AuditID, request.RequestedAt, request.DeviceID, request.MosqueID,
		request.ID, afterHash, deviceBindingAuditReason, request.InteractionID,
	); err != nil {
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: append audit: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return DeviceRegistryBindingRequest{}, fmt.Errorf("request device registry binding: commit: %w", err)
	}
	return request, nil
}

func readDeviceRegistryBindingRequest(
	ctx context.Context,
	tx pgx.Tx,
	deviceID string,
	interactionID string,
) (DeviceRegistryBindingRequest, bool, error) {
	var stored DeviceRegistryBindingRequest
	err := tx.QueryRow(ctx, `
		SELECT id, revision_id, city_id, policy_id, choice_id, mosque_id, device_id,
		       local_date::text, resolution_tier, status, selection_sha256, origin,
		       interaction_id, requested_at
		FROM device_registry_binding_requests
		WHERE device_id = $1 AND interaction_id = $2`, deviceID, interactionID).Scan(
		&stored.ID, &stored.RevisionID, &stored.CityID, &stored.PolicyID, &stored.ChoiceID,
		&stored.MosqueID, &stored.DeviceID, &stored.Date, &stored.Tier, &stored.Status,
		&stored.SelectionSHA256, &stored.Origin, &stored.InteractionID, &stored.RequestedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeviceRegistryBindingRequest{}, false, nil
	}
	if err != nil {
		return DeviceRegistryBindingRequest{}, false, fmt.Errorf("request device registry binding: read retry: %w", err)
	}
	stored.RequestedAt = stored.RequestedAt.UTC()
	return stored, true, nil
}

func validDeviceRegistryBindingRequest(request DeviceRegistryBindingRequest) bool {
	if !validIdentifier(request.ID) || !validRegistryIdentifier(request.RevisionID) ||
		!validRegistryIdentifier(request.CityID) || !validRegistryIdentifier(request.PolicyID) ||
		!validRegistryIdentifier(request.ChoiceID) ||
		!validIdentifier(request.MosqueID) || !validIdentifier(request.DeviceID) ||
		!validSetupDate(request.Date) || !validIdentifier(request.InteractionID) ||
		request.Status != RegistryBindingPendingReview ||
		request.Origin != DeviceBindingOriginLocalTVOperator ||
		!validSHA256(request.SelectionSHA256) || !validSHA256(request.AuditID) ||
		request.RequestedAt.IsZero() || request.RequestedAt.Location() != time.UTC {
		return false
	}
	return oneOf(request.Tier,
		registry.ResolutionExactCityTimetable,
		registry.ResolutionRegionalTimetable,
		registry.ResolutionRegionalCalculation,
		registry.ResolutionExplicitFallback,
	)
}

func sameDeviceRegistryBindingRequest(left, right DeviceRegistryBindingRequest) bool {
	return left.ID == right.ID && left.RevisionID == right.RevisionID &&
		left.CityID == right.CityID && left.PolicyID == right.PolicyID &&
		left.ChoiceID == right.ChoiceID && left.MosqueID == right.MosqueID &&
		left.DeviceID == right.DeviceID && left.Date == right.Date &&
		left.Tier == right.Tier && left.Status == right.Status &&
		left.SelectionSHA256 == right.SelectionSHA256 && left.Origin == right.Origin &&
		left.InteractionID == right.InteractionID && left.RequestedAt.Equal(right.RequestedAt)
}
