// Package devices implements the device-facing pairing and immutable read path.
package devices

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

const (
	maxPairRequestBytes   = 16 * 1024
	maxHeartbeatBytes     = 16 * 1024
	maxAdminRequestBytes  = 16 * 1024
	maxSnapshotBytes      = 5 * 1024 * 1024
	defaultBackendTimeout = 5 * time.Second
	maximumBackendTimeout = 30 * time.Second
)

type MosqueIdentity struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Timezone string `json:"timezone"`
}

type PairingFixture struct {
	Code     string
	DeviceID string
	Token    string
	Mosque   MosqueIdentity
}

type DeviceAssignment struct {
	DeviceID          string `json:"device_id"`
	ManifestVersion   int64  `json:"manifest_version"`
	SnapshotID        string `json:"snapshot_id"`
	SnapshotURL       string `json:"snapshot_url"`
	SnapshotSHA256    string `json:"snapshot_sha256"`
	SigningKeyID      string `json:"signing_key_id"`
	MinimumAppVersion string `json:"minimum_app_version,omitempty"`
}

type SnapshotArtifact struct {
	ID           string
	Bytes        []byte
	SHA256       string
	SigningKeyID string
}

type ServiceConfig struct {
	PublicBaseURL     string
	PairingFixtures   []PairingFixture
	Assignments       []DeviceAssignment
	Snapshots         []SnapshotArtifact
	TrustedPublicKeys map[string]ed25519.PublicKey
	PairingBackend    PairingBackend
	AdminBackend      AdminFleetBackend
	BackendTimeout    time.Duration
}

type DeviceManifest struct {
	ManifestVersion    int64  `json:"manifest_version"`
	SnapshotID         string `json:"snapshot_id"`
	SnapshotURL        string `json:"snapshot_url"`
	SnapshotSHA256     string `json:"snapshot_sha256"`
	SnapshotByteLength int64  `json:"snapshot_byte_length"`
	SigningKeyID       string `json:"signing_key_id"`
	MinimumAppVersion  string `json:"minimum_app_version,omitempty"`
}

type pairingRecord struct {
	codeHash [sha256.Size]byte
	deviceID string
	token    string
	mosque   MosqueIdentity
	used     bool
}

type snapshotRecord struct {
	id           string
	bytes        []byte
	sha256       string
	signingKeyID string
	mosqueID     string
	timezone     string
}

type Service struct {
	mu             sync.Mutex
	pairings       []*pairingRecord
	assignments    map[string]DeviceAssignment
	snapshots      map[string]snapshotRecord
	tokens         map[string]string
	mosques        map[string]MosqueIdentity
	publicBase     *url.URL
	handler        http.Handler
	pairingBackend PairingBackend
	adminBackend   AdminFleetBackend
	backendTimeout time.Duration
}

func NewService(config ServiceConfig) (*Service, error) {
	service := &Service{
		assignments:    make(map[string]DeviceAssignment, len(config.Assignments)),
		snapshots:      make(map[string]snapshotRecord, len(config.Snapshots)),
		tokens:         make(map[string]string, len(config.PairingFixtures)),
		mosques:        make(map[string]MosqueIdentity, len(config.PairingFixtures)),
		pairingBackend: config.PairingBackend,
		adminBackend:   config.AdminBackend,
	}
	if config.BackendTimeout < 0 || config.BackendTimeout > maximumBackendTimeout {
		return nil, errors.New("configure pairing: backend timeout is invalid")
	}
	service.backendTimeout = config.BackendTimeout
	if service.backendTimeout == 0 {
		service.backendTimeout = defaultBackendTimeout
	}
	if config.PairingBackend == nil && config.BackendTimeout != 0 {
		return nil, errors.New("configure pairing: backend timeout requires a persistent backend")
	}
	if config.PairingBackend != nil && len(config.PairingFixtures) > 0 {
		return nil, errors.New("configure pairing: persistent backend and ephemeral fixtures are mutually exclusive")
	}
	if config.PairingBackend != nil && len(config.Assignments) > 0 {
		return nil, errors.New("configure assignment: persistent assignments require the fleet administration store")
	}
	if config.AdminBackend != nil && len(config.Assignments) > 0 {
		return nil, errors.New("configure assignment: admin backend and static assignments are mutually exclusive")
	}
	if config.PublicBaseURL != "" {
		publicBase, err := parsePublicBaseURL(config.PublicBaseURL)
		if err != nil {
			return nil, fmt.Errorf("configure public base URL: %w", err)
		}
		service.publicBase = publicBase
	}
	codeHashes := make(map[[sha256.Size]byte]struct{}, len(config.PairingFixtures))
	credentialHashes := make(map[[sha256.Size]byte]struct{}, len(config.PairingFixtures))
	for _, fixture := range config.PairingFixtures {
		if err := validatePairingFixture(fixture); err != nil {
			return nil, fmt.Errorf("configure pairing fixture %q: %w", fixture.DeviceID, err)
		}
		codeHash := sha256.Sum256([]byte(fixture.Code))
		credentialHash := sha256.Sum256([]byte(fixture.Token))
		if _, exists := service.tokens[fixture.DeviceID]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate device", fixture.DeviceID)
		}
		if _, exists := codeHashes[codeHash]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate pairing code", fixture.DeviceID)
		}
		if _, exists := credentialHashes[credentialHash]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate bearer credential", fixture.DeviceID)
		}
		service.pairings = append(service.pairings, &pairingRecord{
			codeHash: codeHash,
			deviceID: fixture.DeviceID,
			token:    fixture.Token,
			mosque:   fixture.Mosque,
		})
		codeHashes[codeHash] = struct{}{}
		credentialHashes[credentialHash] = struct{}{}
		service.tokens[fixture.DeviceID] = fixture.Token
		service.mosques[fixture.DeviceID] = fixture.Mosque
	}
	for _, artifact := range config.Snapshots {
		snapshot, err := validateSnapshotArtifact(artifact, config.TrustedPublicKeys)
		if err != nil {
			return nil, fmt.Errorf("configure snapshot %q: %w", artifact.ID, err)
		}
		if _, exists := service.snapshots[artifact.ID]; exists {
			return nil, fmt.Errorf("configure snapshot %q: duplicate ID", artifact.ID)
		}
		service.snapshots[artifact.ID] = snapshotRecord{
			id:           artifact.ID,
			bytes:        append([]byte(nil), artifact.Bytes...),
			sha256:       artifact.SHA256,
			signingKeyID: artifact.SigningKeyID,
			mosqueID:     snapshot.Mosque.ID,
			timezone:     snapshot.Mosque.Timezone,
		}
	}
	for _, assignment := range config.Assignments {
		if err := service.validateAssignment(assignment); err != nil {
			return nil, fmt.Errorf("configure assignment %q: %w", assignment.DeviceID, err)
		}
		if _, exists := service.assignments[assignment.DeviceID]; exists {
			return nil, fmt.Errorf("configure assignment %q: duplicate device", assignment.DeviceID)
		}
		service.assignments[assignment.DeviceID] = assignment
	}
	service.handler = service.routes()
	return service, nil
}

func (s *Service) Handler() http.Handler { return s.handler }

func (s *Service) Close() {
	if closer, ok := s.pairingBackend.(interface{ Close() }); ok {
		closer.Close()
	}
}

func (s *Service) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/devices/pair", s.handlePair)
	mux.HandleFunc("GET /v1/devices/{deviceId}/manifest", s.handleManifest)
	mux.HandleFunc("POST /v1/devices/{deviceId}/heartbeat", s.handleHeartbeat)
	mux.HandleFunc("GET /v1/snapshots/{snapshotId}", s.handleSnapshot)
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/devices", s.handleAdminListDevices)
	mux.HandleFunc("POST /v1/admin/mosques/{mosqueId}/pairing-codes", s.handleAdminIssuePairing)
	mux.HandleFunc("POST /v1/admin/mosques/{mosqueId}/devices/{deviceId}/revoke", s.handleAdminRevokeDevice)
	mux.HandleFunc("PUT /v1/admin/mosques/{mosqueId}/devices/{deviceId}/assignment", s.handleAdminAssignDevice)
	mux.HandleFunc("PUT /v1/admin/mosques/{mosqueId}/devices/{deviceId}/rollout-group", s.handleAdminSetRolloutGroup)
	mux.HandleFunc("PUT /v1/admin/mosques/{mosqueId}/rollout-groups/{groupId}/assignment", s.handleAdminAssignRolloutGroup)
	return securityHeaders(mux)
}

type pairRequest struct {
	PairingCode           string     `json:"pairing_code"`
	InstallationPublicKey string     `json:"installation_public_key,omitempty"`
	Device                DeviceInfo `json:"device"`
}

type pairResponse struct {
	DeviceID    string         `json:"device_id"`
	DeviceToken string         `json:"device_token"`
	Mosque      MosqueIdentity `json:"mosque"`
	ManifestURL string         `json:"manifest_url"`
}

type heartbeatRequest struct {
	SentAt                *time.Time        `json:"sent_at"`
	AppVersion            *string           `json:"app_version"`
	OSVersion             *string           `json:"os_version"`
	Model                 *string           `json:"model"`
	ActiveSnapshotID      *string           `json:"active_snapshot_id"`
	SyncStatus            *DeviceSyncStatus `json:"sync_status"`
	CoverageDaysRemaining *int              `json:"coverage_days_remaining"`
	ClockMismatch         *bool             `json:"clock_mismatch"`
	TimezoneMismatch      *bool             `json:"timezone_mismatch"`
	StorageHealth         *DeviceHealth     `json:"storage_health"`
	MemoryHealth          *DeviceHealth     `json:"memory_health"`
	BootMode              *DeviceBootMode   `json:"boot_mode"`
	KioskMode             *DeviceKioskMode  `json:"kiosk_mode"`
}

func (s *Service) handlePair(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	mediaType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maxPairRequestBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	var input pairRequest
	if err := decoder.Decode(&input); err != nil || decodeEOF(decoder) != nil || !validPairRequest(input) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	if s.pairingBackend != nil {
		requestID, err := newRequestID()
		if err != nil {
			writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
			return
		}
		backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
		defer cancel()
		provisioning, err := s.pairingBackend.Pair(backendContext, PairingAttempt{
			Code: input.PairingCode, InstallationPublicKey: input.InstallationPublicKey,
			Device: cloneDeviceInfo(input.Device), SourceAddress: requestSourceAddress(request.RemoteAddr),
			RequestID: requestID,
		})
		switch {
		case err == nil:
			writeJSON(writer, http.StatusOK, pairResponse{
				DeviceID: provisioning.DeviceID, DeviceToken: provisioning.Token, Mosque: provisioning.Mosque,
				ManifestURL: "/v1/devices/" + url.PathEscape(provisioning.DeviceID) + "/manifest",
			})
		case errors.Is(err, ErrPairingInvalid):
			writeAPIError(writer, http.StatusBadRequest, "pairing_code_invalid", false)
		case errors.Is(err, ErrPairingRateLimited):
			writeAPIError(writer, http.StatusTooManyRequests, "pairing_rate_limited", true)
		case errors.Is(err, ErrInvalidPairingRequest):
			writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		default:
			writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		}
		return
	}
	providedHash := sha256.Sum256([]byte(input.PairingCode))
	s.mu.Lock()
	var matched *pairingRecord
	for _, fixture := range s.pairings {
		if subtle.ConstantTimeCompare(providedHash[:], fixture.codeHash[:]) == 1 && !fixture.used {
			matched = fixture
		}
	}
	if matched != nil {
		matched.used = true
	}
	s.mu.Unlock()
	if matched == nil {
		writeAPIError(writer, http.StatusBadRequest, "pairing_code_invalid", false)
		return
	}
	writeJSON(writer, http.StatusOK, pairResponse{
		DeviceID: matched.deviceID, DeviceToken: matched.token, Mosque: matched.mosque,
		ManifestURL: "/v1/devices/" + url.PathEscape(matched.deviceID) + "/manifest",
	})
}

func (s *Service) handleManifest(writer http.ResponseWriter, request *http.Request) {
	deviceID := request.PathValue("deviceId")
	principal, authErr := s.authenticatedDevice(request)
	if authErr != nil {
		writeDeviceAuthenticationError(writer, authErr)
		return
	}
	if subtle.ConstantTimeCompare([]byte(principal.DeviceID), []byte(deviceID)) != 1 {
		writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
		return
	}
	assignment, exists, assignmentErr := s.assignmentForDevice(request, principal)
	if assignmentErr != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	if !exists {
		writeAPIError(writer, http.StatusNotFound, "manifest_not_found", false)
		return
	}
	artifact, exists := s.snapshots[assignment.SnapshotID]
	if !exists || artifact.mosqueID != principal.Mosque.ID || artifact.timezone != principal.Mosque.Timezone {
		writeAPIError(writer, http.StatusNotFound, "manifest_not_found", false)
		return
	}
	manifest := DeviceManifest{
		ManifestVersion: assignment.ManifestVersion, SnapshotID: assignment.SnapshotID,
		SnapshotURL: assignment.SnapshotURL, SnapshotSHA256: assignment.SnapshotSHA256,
		SnapshotByteLength: int64(len(artifact.bytes)), SigningKeyID: assignment.SigningKeyID,
		MinimumAppVersion: assignment.MinimumAppVersion,
	}
	body, err := json.Marshal(manifest)
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	etagHash := sha256.Sum256(body)
	etag := `"` + hex.EncodeToString(etagHash[:]) + `"`
	writer.Header().Set("Cache-Control", "private, max-age=0, must-revalidate")
	writer.Header().Set("ETag", etag)
	if request.Header.Get("If-None-Match") == etag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	writeJSONBytes(writer, http.StatusOK, body)
}

func (s *Service) handleHeartbeat(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, authErr := s.authenticatedDevice(request)
	if authErr != nil {
		writeDeviceAuthenticationError(writer, authErr)
		return
	}
	if subtle.ConstantTimeCompare([]byte(principal.DeviceID), []byte(request.PathValue("deviceId"))) != 1 {
		writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
		return
	}
	mediaType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maxHeartbeatBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	var input heartbeatRequest
	if err := decoder.Decode(&input); err != nil || decodeEOF(decoder) != nil || !input.complete() {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	report := input.report()
	if s.pairingBackend != nil {
		backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
		defer cancel()
		err := s.pairingBackend.Heartbeat(backendContext, principal, report)
		switch {
		case err == nil:
		case errors.Is(err, ErrInvalidHeartbeat):
			writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
			return
		case errors.Is(err, ErrDeviceUnauthorized):
			writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
			return
		default:
			writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
			return
		}
	}
	writer.WriteHeader(http.StatusNoContent)
}

func (input heartbeatRequest) complete() bool {
	return input.SentAt != nil && input.AppVersion != nil && input.OSVersion != nil &&
		input.Model != nil && input.ActiveSnapshotID != nil && input.SyncStatus != nil &&
		input.CoverageDaysRemaining != nil && input.ClockMismatch != nil &&
		input.TimezoneMismatch != nil && input.StorageHealth != nil &&
		input.MemoryHealth != nil && input.BootMode != nil && input.KioskMode != nil
}

func (input heartbeatRequest) report() DeviceHeartbeatReport {
	return DeviceHeartbeatReport{
		SentAt: *input.SentAt, AppVersion: *input.AppVersion, OSVersion: *input.OSVersion,
		Model: *input.Model, ActiveSnapshotID: *input.ActiveSnapshotID,
		SyncStatus: *input.SyncStatus, CoverageDaysRemaining: *input.CoverageDaysRemaining,
		ClockMismatch: *input.ClockMismatch, TimezoneMismatch: *input.TimezoneMismatch,
		StorageHealth: *input.StorageHealth, MemoryHealth: *input.MemoryHealth,
		BootMode: *input.BootMode, KioskMode: *input.KioskMode,
	}
}

func (s *Service) handleSnapshot(writer http.ResponseWriter, request *http.Request) {
	snapshotID := request.PathValue("snapshotId")
	principal, authErr := s.authenticatedDevice(request)
	if authErr != nil {
		writeDeviceAuthenticationError(writer, authErr)
		return
	}
	assignment, assigned, assignmentErr := s.assignmentForDevice(request, principal)
	if assignmentErr != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	if !assigned || assignment.SnapshotID != snapshotID {
		writeAPIError(writer, http.StatusNotFound, "snapshot_not_found", false)
		return
	}
	artifact, exists := s.snapshots[snapshotID]
	if !exists || artifact.mosqueID != principal.Mosque.ID || artifact.timezone != principal.Mosque.Timezone {
		writeAPIError(writer, http.StatusNotFound, "snapshot_not_found", false)
		return
	}
	etag := `"` + artifact.sha256 + `"`
	writer.Header().Set("Cache-Control", "private, max-age=86400, immutable")
	writer.Header().Set("ETag", etag)
	if request.Header.Get("If-None-Match") == etag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	hashBytes, _ := hex.DecodeString(artifact.sha256)
	writer.Header().Set("Content-Type", "application/json")
	writer.Header().Set("Content-Length", fmt.Sprintf("%d", len(artifact.bytes)))
	writer.Header().Set("Digest", "sha-256="+base64.StdEncoding.EncodeToString(hashBytes))
	writer.WriteHeader(http.StatusOK)
	_, _ = writer.Write(artifact.bytes)
}

type adminIssuePairingRequest struct {
	ExpiresInSeconds int64  `json:"expires_in_seconds"`
	Reason           string `json:"reason"`
}

type adminIssuePairingResponse struct {
	DeviceID  string    `json:"device_id"`
	Code      string    `json:"pairing_code"`
	ExpiresAt time.Time `json:"expires_at"`
}

type adminReasonRequest struct {
	Reason string `json:"reason"`
}

type adminAssignmentRequest struct {
	SnapshotID        string `json:"snapshot_id"`
	MinimumAppVersion string `json:"minimum_app_version,omitempty"`
	Reason            string `json:"reason"`
}

type adminRolloutGroupRequest struct {
	RolloutGroup *string `json:"rollout_group"`
	Reason       string  `json:"reason"`
}

func (s *Service) handleAdminIssuePairing(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminIssuePairingRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if !validIdempotencyKey(request.Header.Get("Idempotency-Key")) ||
		!validAuditText(input.Reason, 512) || input.ExpiresInSeconds < 60 || input.ExpiresInSeconds > 900 {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	requestID, err := newRequestID()
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	issued, err := s.adminBackend.IssuePairing(backendContext, principal, AdminIssuePairingCommand{
		MosqueID: request.PathValue("mosqueId"), Reason: input.Reason, RequestID: requestID,
		IdempotencyKey: request.Header.Get("Idempotency-Key"),
		ExpiresIn:      time.Duration(input.ExpiresInSeconds) * time.Second,
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writeJSON(writer, http.StatusCreated, adminIssuePairingResponse{
		DeviceID: issued.DeviceID, Code: issued.Code, ExpiresAt: issued.ExpiresAt,
	})
}

func (s *Service) handleAdminListDevices(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	devices, err := s.adminBackend.ListDevices(backendContext, principal, request.PathValue("mosqueId"))
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	if devices == nil {
		devices = []FleetDevice{}
	}
	writeJSON(writer, http.StatusOK, struct {
		Devices []FleetDevice `json:"devices"`
	}{Devices: devices})
}

func (s *Service) handleAdminRevokeDevice(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminReasonRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if !validIdempotencyKey(request.Header.Get("Idempotency-Key")) || !validAuditText(input.Reason, 512) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	requestID, err := newRequestID()
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	err = s.adminBackend.RevokeDevice(backendContext, principal, AdminRevokeDeviceCommand{
		MosqueID: request.PathValue("mosqueId"), DeviceID: request.PathValue("deviceId"),
		Reason: input.Reason, RequestID: requestID, IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writer.WriteHeader(http.StatusNoContent)
}

func (s *Service) handleAdminAssignDevice(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminAssignmentRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if !validIdempotencyKey(request.Header.Get("Idempotency-Key")) || !validAuditText(input.Reason, 512) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	if err := s.adminBackend.AuthorizeAdminScope(principal, request.PathValue("mosqueId"), true); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	retried, found, err := s.adminBackend.RetryAssignment(backendContext, principal, AdminAssignmentRetryQuery{
		MosqueID: request.PathValue("mosqueId"), DeviceID: request.PathValue("deviceId"),
		SnapshotID: input.SnapshotID, MinimumAppVersion: input.MinimumAppVersion,
		Reason: input.Reason, IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	cancel()
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	if found {
		writeJSON(writer, http.StatusOK, retried)
		return
	}
	artifact, exists := s.snapshots[input.SnapshotID]
	if !exists || artifact.mosqueID != request.PathValue("mosqueId") || s.publicBase == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	requestID, err := newRequestID()
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	snapshotURL := s.publicBase.ResolveReference(&url.URL{Path: "/v1/snapshots/" + url.PathEscape(artifact.id)}).String()
	backendContext, cancel = context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	assignment, err := s.adminBackend.AssignDevice(backendContext, principal, AdminAssignDeviceCommand{
		MosqueID: request.PathValue("mosqueId"), DeviceID: request.PathValue("deviceId"),
		SnapshotID: artifact.id, SnapshotURL: snapshotURL, SnapshotSHA256: artifact.sha256,
		SigningKeyID: artifact.signingKeyID, SnapshotMosqueID: artifact.mosqueID,
		SnapshotTimezone: artifact.timezone, MinimumAppVersion: input.MinimumAppVersion,
		Reason: input.Reason, RequestID: requestID, IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writeJSON(writer, http.StatusOK, assignment)
}

func (s *Service) handleAdminSetRolloutGroup(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminRolloutGroupRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if input.RolloutGroup == nil || !validIdempotencyKey(request.Header.Get("Idempotency-Key")) ||
		!validAuditText(input.Reason, 512) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	requestID, err := newRequestID()
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	err = s.adminBackend.SetDeviceRolloutGroup(backendContext, principal, AdminSetRolloutGroupCommand{
		MosqueID: request.PathValue("mosqueId"), DeviceID: request.PathValue("deviceId"),
		RolloutGroup: *input.RolloutGroup, Reason: input.Reason, RequestID: requestID,
		IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writer.WriteHeader(http.StatusNoContent)
}

func (s *Service) handleAdminAssignRolloutGroup(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminAssignmentRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if !validIdempotencyKey(request.Header.Get("Idempotency-Key")) || !validAuditText(input.Reason, 512) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	mosqueID := request.PathValue("mosqueId")
	rolloutGroup := request.PathValue("groupId")
	if err := s.adminBackend.AuthorizeAdminScope(principal, mosqueID, true); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	retried, found, err := s.adminBackend.RetryRolloutAssignment(
		backendContext,
		principal,
		AdminRolloutAssignmentRetryQuery{
			MosqueID: mosqueID, RolloutGroup: rolloutGroup, SnapshotID: input.SnapshotID,
			MinimumAppVersion: input.MinimumAppVersion, Reason: input.Reason,
			IdempotencyKey: request.Header.Get("Idempotency-Key"),
		},
	)
	cancel()
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	if found {
		writeJSON(writer, http.StatusOK, retried)
		return
	}
	artifact, exists := s.snapshots[input.SnapshotID]
	if !exists || artifact.mosqueID != mosqueID || s.publicBase == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	requestID, err := newRequestID()
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	snapshotURL := s.publicBase.ResolveReference(&url.URL{Path: "/v1/snapshots/" + url.PathEscape(artifact.id)}).String()
	backendContext, cancel = context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	result, err := s.adminBackend.AssignRolloutGroup(backendContext, principal, AdminAssignRolloutGroupCommand{
		MosqueID: mosqueID, RolloutGroup: rolloutGroup,
		SnapshotID: artifact.id, SnapshotURL: snapshotURL, SnapshotSHA256: artifact.sha256,
		SigningKeyID: artifact.signingKeyID, SnapshotMosqueID: artifact.mosqueID,
		SnapshotTimezone: artifact.timezone, MinimumAppVersion: input.MinimumAppVersion,
		Reason: input.Reason, RequestID: requestID, IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writeJSON(writer, http.StatusOK, result)
}

func (s *Service) authenticateAdminRequest(writer http.ResponseWriter, request *http.Request) (AdminPrincipal, bool) {
	if s.adminBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return AdminPrincipal{}, false
	}
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") || len(header) == len("Bearer ") {
		writeAPIError(writer, http.StatusUnauthorized, "admin_unauthorized", false)
		return AdminPrincipal{}, false
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	principal, err := s.adminBackend.AuthenticateAdmin(backendContext, header[len("Bearer "):])
	if err == nil {
		return principal, true
	}
	if errors.Is(err, ErrAdminUnauthorized) {
		writeAPIError(writer, http.StatusUnauthorized, "admin_unauthorized", false)
		return AdminPrincipal{}, false
	}
	writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
	return AdminPrincipal{}, false
}

func decodeAdminJSON(writer http.ResponseWriter, request *http.Request, target any) bool {
	mediaType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return false
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maxAdminRequestBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil || decodeEOF(decoder) != nil {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return false
	}
	return true
}

func writeAdminOperationError(writer http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrInvalidAdminRequest):
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
	case errors.Is(err, ErrAdminIdempotencyConflict):
		writeAPIError(writer, http.StatusConflict, "idempotency_conflict", false)
	case errors.Is(err, ErrRolloutGroupTooLarge):
		writeAPIError(writer, http.StatusConflict, "rollout_group_too_large", false)
	case errors.Is(err, ErrAdminResourceNotFound):
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
	default:
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
	}
}

func (s *Service) assignmentForDevice(request *http.Request, principal DevicePrincipal) (DeviceAssignment, bool, error) {
	if s.adminBackend == nil {
		assignment, exists := s.assignments[principal.DeviceID]
		return assignment, exists, nil
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	assignment, err := s.adminBackend.GetDeviceAssignment(backendContext, principal.DeviceID, principal.Mosque.ID)
	if errors.Is(err, ErrDeviceAssignmentNotFound) {
		return DeviceAssignment{}, false, nil
	}
	if err != nil {
		return DeviceAssignment{}, false, err
	}
	artifact, exists := s.snapshots[assignment.SnapshotID]
	if !exists || assignment.DeviceID != principal.DeviceID || artifact.mosqueID != principal.Mosque.ID ||
		artifact.timezone != principal.Mosque.Timezone || s.validateAssignmentMetadata(assignment, artifact) != nil {
		return DeviceAssignment{}, false, errors.New("persistent assignment is invalid")
	}
	return assignment, true, nil
}

func (s *Service) authenticatedDevice(request *http.Request) (DevicePrincipal, error) {
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") || len(header) == len("Bearer ") {
		return DevicePrincipal{}, ErrDeviceUnauthorized
	}
	provided := header[len("Bearer "):]
	if s.pairingBackend != nil {
		backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
		defer cancel()
		return s.pairingBackend.Authenticate(backendContext, provided)
	}
	for deviceID, token := range s.tokens {
		if len(provided) == len(token) && subtle.ConstantTimeCompare([]byte(provided), []byte(token)) == 1 {
			return DevicePrincipal{DeviceID: deviceID, Mosque: s.mosques[deviceID]}, nil
		}
	}
	return DevicePrincipal{}, ErrDeviceUnauthorized
}

func writeDeviceAuthenticationError(writer http.ResponseWriter, err error) {
	if errors.Is(err, ErrDeviceUnauthorized) {
		writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
		return
	}
	writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
}

func (s *Service) validateAssignment(assignment DeviceAssignment) error {
	if _, exists := s.tokens[assignment.DeviceID]; !exists {
		return errors.New("device has no pairing credential")
	}
	artifact, exists := s.snapshots[assignment.SnapshotID]
	if !exists {
		return errors.New("snapshot does not exist")
	}
	pairedMosque := s.mosques[assignment.DeviceID]
	if artifact.mosqueID != pairedMosque.ID || artifact.timezone != pairedMosque.Timezone {
		return errors.New("snapshot mosque identity does not match paired device mosque")
	}
	return s.validateAssignmentMetadata(assignment, artifact)
}

func (s *Service) validateAssignmentMetadata(assignment DeviceAssignment, artifact snapshotRecord) error {
	if !validIdentifier(assignment.DeviceID) || assignment.ManifestVersion < 1 || !validIdentifier(assignment.SnapshotID) || !validSHA256(assignment.SnapshotSHA256) || len(assignment.SigningKeyID) < 1 || len(assignment.SigningKeyID) > 128 || len(assignment.MinimumAppVersion) > 64 {
		return errors.New("required assignment metadata is invalid")
	}
	if s.publicBase == nil {
		return errors.New("public base URL is required when assignments exist")
	}
	parsed, err := url.Parse(assignment.SnapshotURL)
	wantPath := "/v1/snapshots/" + url.PathEscape(assignment.SnapshotID)
	if err != nil || !sameOrigin(parsed, s.publicBase) || parsed.User != nil || parsed.Fragment != "" || parsed.RawQuery != "" || parsed.EscapedPath() != wantPath {
		return errors.New("snapshot URL must use the public API origin and canonical snapshot path")
	}
	if artifact.sha256 != assignment.SnapshotSHA256 || artifact.signingKeyID != assignment.SigningKeyID {
		return errors.New("assignment does not match immutable snapshot identity")
	}
	return nil
}

func validatePairingFixture(fixture PairingFixture) error {
	if len(fixture.Code) < 6 || len(fixture.Code) > 32 || !validIdentifier(fixture.DeviceID) || len(fixture.Token) < 16 || len(fixture.Token) > 4096 {
		return errors.New("code, device ID or token is invalid")
	}
	if len(fixture.Mosque.ID) < 1 || len(fixture.Mosque.ID) > 128 || len(fixture.Mosque.Name) < 1 || len(fixture.Mosque.Name) > 240 || len(fixture.Mosque.Timezone) < 3 || len(fixture.Mosque.Timezone) > 64 {
		return errors.New("mosque identity is incomplete")
	}
	zone, err := time.LoadLocation(fixture.Mosque.Timezone)
	if err != nil || zone.String() != fixture.Mosque.Timezone || strings.HasPrefix(fixture.Mosque.Timezone, "+") || strings.HasPrefix(fixture.Mosque.Timezone, "-") {
		return errors.New("mosque timezone must be a named IANA zone")
	}
	return nil
}

func validateSnapshotArtifact(artifact SnapshotArtifact, trustedPublicKeys map[string]ed25519.PublicKey) (domain.Snapshot, error) {
	if !validIdentifier(artifact.ID) || len(artifact.Bytes) == 0 || len(artifact.Bytes) > maxSnapshotBytes || !validSHA256(artifact.SHA256) || len(artifact.SigningKeyID) < 1 || len(artifact.SigningKeyID) > 128 {
		return domain.Snapshot{}, errors.New("snapshot metadata is invalid")
	}
	actual := sha256.Sum256(artifact.Bytes)
	if hex.EncodeToString(actual[:]) != artifact.SHA256 {
		return domain.Snapshot{}, errors.New("snapshot SHA-256 does not match bytes")
	}
	if err := publication.Verify(artifact.Bytes, trustedPublicKeys); err != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot authenticity validation failed: %w", err)
	}
	snapshot, err := domain.DecodeSnapshot(artifact.Bytes)
	if err != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot contract decoding failed: %w", err)
	}
	if snapshot.SnapshotID != artifact.ID || snapshot.Integrity.SigningKeyID != artifact.SigningKeyID {
		return domain.Snapshot{}, errors.New("snapshot payload identity does not match artifact metadata")
	}
	return snapshot, nil
}

func parsePublicBaseURL(value string) (*url.URL, error) {
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil || parsed.Fragment != "" || parsed.RawQuery != "" || (parsed.EscapedPath() != "" && parsed.EscapedPath() != "/") {
		return nil, errors.New("must be an HTTPS origin without path, query, user info or fragment")
	}
	return parsed, nil
}

func sameOrigin(left, right *url.URL) bool {
	return strings.EqualFold(left.Scheme, right.Scheme) && strings.EqualFold(left.Hostname(), right.Hostname()) && effectivePort(left) == effectivePort(right)
}

func effectivePort(value *url.URL) string {
	if value.Port() != "" {
		return value.Port()
	}
	return "443"
}

func validPairRequest(input pairRequest) bool {
	if len(input.PairingCode) < 6 || len(input.PairingCode) > 32 || len(input.InstallationPublicKey) > 4096 || !validDeviceInfo(input.Device) {
		return false
	}
	return true
}

func newRequestID() (string, error) {
	value := make([]byte, 16)
	if _, err := io.ReadFull(rand.Reader, value); err != nil {
		return "", err
	}
	return "request-" + hex.EncodeToString(value), nil
}

func requestSourceAddress(remoteAddress string) string {
	host, _, err := net.SplitHostPort(remoteAddress)
	if err == nil && net.ParseIP(host) != nil {
		return host
	}
	if parsed := net.ParseIP(remoteAddress); parsed != nil {
		return parsed.String()
	}
	return "unresolved-source"
}

func validIdentifier(value string) bool { return len(value) >= 8 && len(value) <= 128 }

func validSHA256(value string) bool {
	if len(value) != sha256.Size*2 || strings.ToLower(value) != value {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func decodeEOF(decoder *json.Decoder) error {
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}

func writeAPIError(writer http.ResponseWriter, status int, code string, retryable bool) {
	writeJSON(writer, status, struct {
		Error struct {
			Code      string `json:"code"`
			Message   string `json:"message"`
			Retryable bool   `json:"retryable"`
		} `json:"error"`
	}{Error: struct {
		Code      string `json:"code"`
		Message   string `json:"message"`
		Retryable bool   `json:"retryable"`
	}{Code: code, Message: "Request could not be completed", Retryable: retryable}})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	body, err := json.Marshal(value)
	if err != nil {
		http.Error(writer, "internal error", http.StatusInternalServerError)
		return
	}
	writeJSONBytes(writer, status, body)
}

func writeJSONBytes(writer http.ResponseWriter, status int, body []byte) {
	writer.Header().Set("Content-Type", "application/json")
	writer.Header().Set("Content-Length", fmt.Sprintf("%d", len(body)))
	writer.WriteHeader(status)
	_, _ = writer.Write(body)
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("X-Content-Type-Options", "nosniff")
		writer.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(writer, request)
	})
}
