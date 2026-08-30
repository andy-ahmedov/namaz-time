// Package devices implements the device-facing pairing and immutable read path.
package devices

import (
	"bytes"
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
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
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
	ID              string
	Bytes           []byte
	SHA256          string
	SigningKeyID    string
	Receipt         *publication.AuditReceipt
	PreviousReceipt *publication.AuditReceipt
}

type ServiceConfig struct {
	PublicBaseURL               string
	PairingFixtures             []PairingFixture
	Assignments                 []DeviceAssignment
	Snapshots                   []SnapshotArtifact
	TrustedPublicKeys           map[string]ed25519.PublicKey
	TrustPolicy                 *trust.Policy
	PublicationLedgerHeadSHA256 string
	PairingBackend              PairingBackend
	AdminBackend                AdminFleetBackend
	RegistryBackend             AdminRegistryBackend
	BackendTimeout              time.Duration
	Now                         func() time.Time
}

type AdminRegistryBackend interface {
	SearchCities(context.Context, string) ([]registry.CitySearchResult, error)
	Resolve(context.Context, registry.ResolveRequest) (registry.Resolution, error)
	AssessRevision(context.Context, string, registry.ResolveRequest) (registry.RevisionPolicyAssessment, error)
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
	mu              sync.Mutex
	pairings        []*pairingRecord
	assignments     map[string]DeviceAssignment
	snapshots       map[string]snapshotRecord
	tokens          map[string]string
	mosques         map[string]MosqueIdentity
	publicBase      *url.URL
	handler         http.Handler
	pairingBackend  PairingBackend
	adminBackend    AdminFleetBackend
	registryBackend AdminRegistryBackend
	backendTimeout  time.Duration
	now             func() time.Time
}

func NewService(config ServiceConfig) (*Service, error) {
	service := &Service{
		assignments:     make(map[string]DeviceAssignment, len(config.Assignments)),
		snapshots:       make(map[string]snapshotRecord, len(config.Snapshots)),
		tokens:          make(map[string]string, len(config.PairingFixtures)),
		mosques:         make(map[string]MosqueIdentity, len(config.PairingFixtures)),
		pairingBackend:  config.PairingBackend,
		adminBackend:    config.AdminBackend,
		registryBackend: config.RegistryBackend,
		now:             config.Now,
	}
	if service.now == nil {
		service.now = time.Now
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
	if config.RegistryBackend != nil && config.AdminBackend == nil {
		return nil, errors.New("configure registry: admin backend is required")
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
	productionReceiptPredecessors := make(map[string]string)
	for _, artifact := range config.Snapshots {
		snapshot, err := validateSnapshotArtifact(artifact, config.TrustedPublicKeys, config.TrustPolicy)
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
		if snapshot.DataClassification == domain.DataClassificationProduction && artifact.Receipt != nil {
			productionReceiptPredecessors[artifact.Receipt.ReceiptSHA256] = artifact.Receipt.PreviousReceiptSHA256
			if artifact.PreviousReceipt != nil {
				productionReceiptPredecessors[artifact.PreviousReceipt.ReceiptSHA256] = artifact.PreviousReceipt.PreviousReceiptSHA256
			}
		}
	}
	if err := validatePublicationLedgerAnchor(productionReceiptPredecessors, config.PublicationLedgerHeadSHA256); err != nil {
		return nil, err
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

func validatePublicationLedgerAnchor(predecessors map[string]string, head string) error {
	if len(predecessors) > 0 {
		if !validSHA256(head) {
			return errors.New("production snapshot registry requires a valid publication ledger head")
		}
		if _, exists := predecessors[head]; !exists {
			return errors.New("production snapshot registry does not contain the anchored publication ledger head")
		}
		reachable := make(map[string]struct{}, len(predecessors))
		for cursor := head; cursor != ""; cursor = predecessors[cursor] {
			if _, loop := reachable[cursor]; loop {
				return errors.New("production publication receipt ledger contains a cycle")
			}
			reachable[cursor] = struct{}{}
			if _, known := predecessors[cursor]; !known {
				break
			}
		}
		for receiptSHA256 := range predecessors {
			if _, exists := reachable[receiptSHA256]; !exists {
				return errors.New("production snapshot registry contains a receipt outside the anchored ledger ancestry")
			}
		}
	} else if head != "" {
		return errors.New("publication ledger head is configured without production snapshots")
	}
	return nil
}

func (s *Service) Handler() http.Handler { return s.handler }

func (s *Service) Close() {
	if closer, ok := s.pairingBackend.(interface{ Close() }); ok {
		closer.Close()
	}
	if closer, ok := s.registryBackend.(interface{ Close() }); ok {
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
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/devices/{deviceId}/support-bundle", s.handleAdminDeviceSupportBundle)
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/setup/cities", s.handleAdminCitySearch)
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy", s.handleAdminPrayerPolicy)
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/setup/schedule-choices", s.handleAdminCityScheduleChoices)
	mux.HandleFunc("GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy-options", s.handleAdminPrayerPolicyOptions)
	mux.HandleFunc("POST /v1/admin/mosques/{mosqueId}/setup/prayer-policy-binding-requests", s.handleAdminRegistryBindingRequest)
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
	var input pairRequest
	if !decodeRequestJSON(writer, request, maxPairRequestBytes, &input) || !validPairRequest(input) {
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
	writer.Header().Set("Date", s.now().UTC().Format(http.TimeFormat))
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
	var input heartbeatRequest
	if !decodeRequestJSON(writer, request, maxHeartbeatBytes, &input) || !input.complete() {
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

type adminRegistryBindingRequest struct {
	RevisionID string `json:"revision_id"`
	CityID     string `json:"city_id"`
	PolicyID   string `json:"policy_id"`
	Date       string `json:"date"`
	Reason     string `json:"reason"`
}

type adminCityCandidate struct {
	CityID             string   `json:"city_id"`
	CanonicalName      string   `json:"canonical_name"`
	Aliases            []string `json:"aliases"`
	FederalSubjectCode string   `json:"federal_subject_code"`
	FederalSubjectName string   `json:"federal_subject_name"`
	SettlementType     string   `json:"settlement_type"`
	Timezone           string   `json:"timezone"`
	Latitude           float64  `json:"latitude"`
	Longitude          float64  `json:"longitude"`
	GeographicSourceID string   `json:"geographic_source_id"`
	GeographicRevision string   `json:"geographic_revision"`
	GeographicLicense  string   `json:"geographic_license"`
}

type adminAuthorityResolution struct {
	ID            string `json:"id"`
	Name          string `json:"name"`
	Branch        string `json:"branch,omitempty"`
	Website       string `json:"website,omitempty"`
	EvidenceLabel string `json:"evidence_label"`
}

type adminPrayerPolicyResponse struct {
	SchemaVersion      string                     `json:"schema_version"`
	Status             string                     `json:"status"`
	Tier               registry.ResolutionTier    `json:"tier"`
	City               adminCityCandidate         `json:"city"`
	Scope              domain.GeographicScope     `json:"scope"`
	Authorities        []adminAuthorityResolution `json:"authorities"`
	Source             domain.PrayerSource        `json:"source"`
	Policy             domain.PrayerPolicy        `json:"policy"`
	TimeTable          *domain.TimeTable          `json:"timetable,omitempty"`
	CalculationProfile *domain.CalculationProfile `json:"calculation_profile,omitempty"`
	SourceOverrides    []domain.SourceOverride    `json:"source_overrides"`
}

type adminPrayerPolicyOptionsResponse struct {
	SchemaVersion  string                    `json:"schema_version"`
	Revision       registry.RevisionRecord   `json:"revision"`
	RevisionState  registry.RevisionState    `json:"revision_state"`
	Status         registry.AssessmentStatus `json:"status"`
	Reason         registry.AssessmentReason `json:"reason"`
	Date           string                    `json:"date"`
	City           adminCityCandidate        `json:"city"`
	Options        []registry.PolicyOption   `json:"options"`
	AllowedActions []string                  `json:"allowed_actions"`
}

type adminCityScheduleChoicesResponse struct {
	SchemaVersion             string                            `json:"schema_version"`
	Revision                  registry.RevisionRecord           `json:"revision"`
	RevisionState             registry.RevisionState            `json:"revision_state"`
	Status                    registry.CityScheduleChoiceStatus `json:"status"`
	AutomaticResolutionStatus registry.AssessmentStatus         `json:"automatic_resolution_status"`
	AutomaticResolutionReason registry.AssessmentReason         `json:"automatic_resolution_reason"`
	SelectionRequired         bool                              `json:"selection_required"`
	Date                      string                            `json:"date"`
	City                      adminCityCandidate                `json:"city"`
	Choices                   []registry.CityScheduleChoice     `json:"choices"`
	AllowedActions            []string                          `json:"allowed_actions"`
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
	writeJSON(writer, http.StatusCreated, adminIssuePairingResponse(issued))
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

func (s *Service) handleAdminCitySearch(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	if s.registryBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	query, ok := exactQueryValue(request, "q", 200)
	if !ok || len(request.URL.Query()) != 1 {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	if err := s.adminBackend.AuthorizeAdminScope(principal, request.PathValue("mosqueId"), false); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	results, err := s.registryBackend.SearchCities(backendContext, query)
	if err != nil {
		writeRegistryOperationError(writer, err)
		return
	}
	candidates := make([]adminCityCandidate, 0, len(results))
	for _, result := range results {
		candidates = append(candidates, projectAdminCity(result))
	}
	writeJSON(writer, http.StatusOK, struct {
		SchemaVersion string               `json:"schema_version"`
		Query         string               `json:"query"`
		Candidates    []adminCityCandidate `json:"candidates"`
	}{SchemaVersion: "city-search/v1", Query: query, Candidates: candidates})
}

func (s *Service) handleAdminPrayerPolicy(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	if s.registryBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	cityID, cityOK := exactQueryValue(request, "city_id", 160)
	date, dateOK := exactQueryValue(request, "date", len(time.DateOnly))
	parsedDate, dateErr := time.Parse(time.DateOnly, date)
	if !cityOK || !dateOK || len(request.URL.Query()) != 2 || dateErr != nil || parsedDate.Format(time.DateOnly) != date {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	mosqueID := request.PathValue("mosqueId")
	if err := s.adminBackend.AuthorizeAdminScope(principal, mosqueID, false); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	resolved, err := s.registryBackend.Resolve(backendContext, registry.ResolveRequest{CityID: cityID, MosqueID: mosqueID, Date: date})
	if err != nil {
		writeRegistryOperationError(writer, err)
		return
	}
	authorities := make([]adminAuthorityResolution, 0, len(resolved.Authorities))
	for _, authority := range resolved.Authorities {
		authorities = append(authorities, adminAuthorityResolution{
			ID: authority.ID, Name: authority.Name, Branch: authority.Branch,
			Website: authority.Website, EvidenceLabel: authority.EvidenceLabel,
		})
	}
	var timetable *domain.TimeTable
	var calculationProfile *domain.CalculationProfile
	if resolved.Policy.Kind == domain.PrayerPolicyTimeTable {
		timetable = &resolved.TimeTable
	} else {
		calculationProfile = &resolved.CalculationProfile
	}
	overrides := append([]domain.SourceOverride(nil), resolved.SourceOverrides...)
	if overrides == nil {
		overrides = []domain.SourceOverride{}
	}
	writeJSON(writer, http.StatusOK, adminPrayerPolicyResponse{
		SchemaVersion: "prayer-policy-resolution/v1", Status: "resolved", Tier: resolved.Tier,
		City:  projectAdminCity(registry.CitySearchResult{City: resolved.City, Region: resolved.Region}),
		Scope: resolved.Scope, Authorities: authorities, Source: resolved.Source,
		Policy: resolved.Policy, TimeTable: timetable, CalculationProfile: calculationProfile,
		SourceOverrides: overrides,
	})
}

func (s *Service) handleAdminCityScheduleChoices(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	if s.registryBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	cityID, cityOK := exactQueryValue(request, "city_id", 160)
	date, dateOK := exactQueryValue(request, "date", len(time.DateOnly))
	parsedDate, dateErr := time.Parse(time.DateOnly, date)
	revisionID := ""
	revisionValues, hasRevision := request.URL.Query()["revision_id"]
	if hasRevision {
		var revisionOK bool
		revisionID, revisionOK = exactQueryValue(request, "revision_id", 160)
		if !revisionOK {
			writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
			return
		}
	}
	wantQueryValues := 2
	if hasRevision {
		wantQueryValues = 3
	}
	if !cityOK || !dateOK || len(request.URL.Query()) != wantQueryValues || len(revisionValues) > 1 ||
		dateErr != nil || parsedDate.Format(time.DateOnly) != date {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	mosqueID := request.PathValue("mosqueId")
	if err := s.adminBackend.AuthorizeAdminScope(principal, mosqueID, false); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	assessment, err := s.registryBackend.AssessRevision(backendContext, revisionID, registry.ResolveRequest{
		CityID: cityID, MosqueID: mosqueID, Date: date,
	})
	if err != nil {
		writeRegistryOperationError(writer, err)
		return
	}
	projected, err := registry.ProjectCityScheduleChoices(assessment)
	if err != nil {
		writeRegistryOperationError(writer, err)
		return
	}
	choices := append([]registry.CityScheduleChoice(nil), projected.Choices...)
	if choices == nil {
		choices = []registry.CityScheduleChoice{}
	}
	actions := []string{}
	if projected.RevisionState == registry.RevisionStateStaged && len(choices) > 0 {
		actions = append(actions, "request_binding")
	}
	writeJSON(writer, http.StatusOK, adminCityScheduleChoicesResponse{
		SchemaVersion: "city-schedule-choices/v1", Revision: projected.Revision,
		RevisionState: projected.RevisionState, Status: projected.Status,
		AutomaticResolutionStatus: projected.AutomaticResolutionStatus,
		AutomaticResolutionReason: projected.AutomaticResolutionReason,
		SelectionRequired:         projected.SelectionRequired, Date: projected.Date,
		City:    projectAdminCity(registry.CitySearchResult{City: projected.City, Region: projected.Region}),
		Choices: choices, AllowedActions: actions,
	})
}

func (s *Service) handleAdminPrayerPolicyOptions(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	if s.registryBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	revisionID, revisionOK := exactQueryValue(request, "revision_id", 160)
	cityID, cityOK := exactQueryValue(request, "city_id", 160)
	date, dateOK := exactQueryValue(request, "date", len(time.DateOnly))
	parsedDate, dateErr := time.Parse(time.DateOnly, date)
	if !revisionOK || !cityOK || !dateOK || len(request.URL.Query()) != 3 ||
		dateErr != nil || parsedDate.Format(time.DateOnly) != date {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	mosqueID := request.PathValue("mosqueId")
	if err := s.adminBackend.AuthorizeAdminScope(principal, mosqueID, false); err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	assessment, err := s.registryBackend.AssessRevision(backendContext, revisionID, registry.ResolveRequest{
		CityID: cityID, MosqueID: mosqueID, Date: date,
	})
	if err != nil {
		writeRegistryOperationError(writer, err)
		return
	}
	options := append([]registry.PolicyOption(nil), assessment.Result.Options...)
	if options == nil {
		options = []registry.PolicyOption{}
	}
	actions := []string{}
	if assessment.State == registry.RevisionStateStaged {
		for _, option := range options {
			if option.Selectable {
				actions = append(actions, "request_binding")
				break
			}
		}
	}
	writeJSON(writer, http.StatusOK, adminPrayerPolicyOptionsResponse{
		SchemaVersion: "prayer-policy-options/v1", Revision: assessment.Revision,
		RevisionState: assessment.State, Status: assessment.Result.Status, Reason: assessment.Result.Reason,
		Date:    assessment.Result.Date,
		City:    projectAdminCity(registry.CitySearchResult{City: assessment.Result.City, Region: assessment.Result.Region}),
		Options: options, AllowedActions: actions,
	})
}

func (s *Service) handleAdminRegistryBindingRequest(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	backend, ok := s.adminBackend.(AdminRegistryBindingBackend)
	if !ok || s.registryBackend == nil {
		writeAPIError(writer, http.StatusNotFound, "admin_resource_not_found", false)
		return
	}
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	var input adminRegistryBindingRequest
	if !decodeAdminJSON(writer, request, &input) {
		return
	}
	if !validIdempotencyKey(request.Header.Get("Idempotency-Key")) {
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
	created, err := backend.RequestRegistryBinding(backendContext, principal, AdminRegistryBindingCommand{
		MosqueID: request.PathValue("mosqueId"), RevisionID: input.RevisionID,
		CityID: input.CityID, PolicyID: input.PolicyID, Date: input.Date, Reason: input.Reason,
		RequestID: requestID, IdempotencyKey: request.Header.Get("Idempotency-Key"),
	})
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writeJSON(writer, http.StatusCreated, struct {
		SchemaVersion string                 `json:"schema_version"`
		Request       RegistryBindingRequest `json:"request"`
	}{SchemaVersion: "registry-binding-request/v1", Request: created})
}

func projectAdminCity(result registry.CitySearchResult) adminCityCandidate {
	aliases := append([]string(nil), result.City.Aliases...)
	if aliases == nil {
		aliases = []string{}
	}
	return adminCityCandidate{
		CityID: result.City.ID, CanonicalName: result.City.Name, Aliases: aliases,
		FederalSubjectCode: result.Region.FederalSubjectCode, FederalSubjectName: result.Region.Name,
		SettlementType: result.City.SettlementType, Timezone: result.City.Timezone,
		Latitude: result.City.Latitude, Longitude: result.City.Longitude,
		GeographicSourceID: result.City.GeographicSourceID, GeographicRevision: result.City.GeographicRevision,
		GeographicLicense: result.City.GeographicLicense,
	}
}

func exactQueryValue(request *http.Request, name string, maximumRunes int) (string, bool) {
	query := request.URL.Query()
	values, exists := query[name]
	if !exists || len(values) != 1 {
		return "", false
	}
	value := values[0]
	if value == "" || strings.TrimSpace(value) != value || len([]rune(value)) > maximumRunes || strings.ContainsAny(value, "\x00\r\n") {
		return "", false
	}
	return value, true
}

func writeRegistryOperationError(writer http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, registry.ErrInvalidResolveRequest), errors.Is(err, registry.ErrRevisionInvalid):
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
	case errors.Is(err, registry.ErrPolicyAmbiguous):
		writeAPIError(writer, http.StatusConflict, "prayer_policy_ambiguous", false)
	case errors.Is(err, registry.ErrPolicyUnavailable), errors.Is(err, registry.ErrRevisionUnavailable):
		writeAPIError(writer, http.StatusConflict, "prayer_policy_unavailable", false)
	default:
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
	}
}

func (s *Service) handleAdminDeviceSupportBundle(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	principal, ok := s.authenticateAdminRequest(writer, request)
	if !ok {
		return
	}
	backendContext, cancel := context.WithTimeout(request.Context(), s.backendTimeout)
	defer cancel()
	bundle, err := s.adminBackend.GetDeviceSupportBundle(
		backendContext, principal, request.PathValue("mosqueId"), request.PathValue("deviceId"),
	)
	if err != nil {
		writeAdminOperationError(writer, err)
		return
	}
	writeJSON(writer, http.StatusOK, bundle)
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
	if !decodeRequestJSON(writer, request, maxAdminRequestBytes, target) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return false
	}
	return true
}

func decodeRequestJSON(writer http.ResponseWriter, request *http.Request, maximumBytes int64, target any) bool {
	mediaType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		return false
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maximumBytes)
	body, err := io.ReadAll(request.Body)
	if err != nil || strictjson.RejectDuplicateObjectMembers(body) != nil {
		return false
	}
	decoder := json.NewDecoder(bytes.NewReader(body))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil || decodeEOF(decoder) != nil {
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
	case errors.Is(err, ErrRegistryBindingNotSelectable):
		writeAPIError(writer, http.StatusConflict, "registry_binding_not_selectable", false)
	case errors.Is(err, ErrRegistryWorkflowUnavailable):
		writeAPIError(writer, http.StatusServiceUnavailable, "registry_workflow_unavailable", true)
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

func validateSnapshotArtifact(artifact SnapshotArtifact, trustedPublicKeys map[string]ed25519.PublicKey, trustPolicy *trust.Policy) (domain.Snapshot, error) {
	if !validIdentifier(artifact.ID) || len(artifact.Bytes) == 0 || len(artifact.Bytes) > maxSnapshotBytes || !validSHA256(artifact.SHA256) || len(artifact.SigningKeyID) < 1 || len(artifact.SigningKeyID) > 128 {
		return domain.Snapshot{}, errors.New("snapshot metadata is invalid")
	}
	actual := sha256.Sum256(artifact.Bytes)
	if hex.EncodeToString(actual[:]) != artifact.SHA256 {
		return domain.Snapshot{}, errors.New("snapshot SHA-256 does not match bytes")
	}
	if len(trustedPublicKeys) > 0 && trustPolicy != nil {
		return domain.Snapshot{}, errors.New("legacy public keys and trust policy are mutually exclusive")
	}
	snapshot, err := domain.DecodeSnapshot(artifact.Bytes)
	if err != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot contract decoding failed: %w", err)
	}
	if artifact.PreviousReceipt != nil && artifact.Receipt == nil {
		return domain.Snapshot{}, errors.New("publication predecessor cannot be configured without its receipt")
	}
	if snapshot.DataClassification == domain.DataClassificationProduction && (trustPolicy == nil || artifact.Receipt == nil) {
		return domain.Snapshot{}, errors.New("production snapshot requires a trust policy and authenticated publication receipt")
	}
	var authenticityError error
	if trustPolicy != nil {
		authenticityError = publication.VerifyWithTrust(artifact.Bytes, trustPolicy)
	} else {
		authenticityError = publication.Verify(artifact.Bytes, trustedPublicKeys)
	}
	if authenticityError != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot authenticity validation failed: %w", authenticityError)
	}
	if snapshot.SnapshotID != artifact.ID || snapshot.Integrity.SigningKeyID != artifact.SigningKeyID {
		return domain.Snapshot{}, errors.New("snapshot payload identity does not match artifact metadata")
	}
	if snapshot.DataClassification == domain.DataClassificationProduction {
		if err := publication.VerifyPublicationEvidence(artifact.Bytes, *artifact.Receipt, trustPolicy, artifact.PreviousReceipt); err != nil {
			return domain.Snapshot{}, fmt.Errorf("production publication evidence validation failed: %w", err)
		}
	} else if artifact.Receipt != nil {
		if trustPolicy == nil {
			return domain.Snapshot{}, errors.New("snapshot receipt requires a lifecycle-aware trust policy")
		}
		if err := publication.VerifyPublicationEvidence(artifact.Bytes, *artifact.Receipt, trustPolicy, artifact.PreviousReceipt); err != nil {
			return domain.Snapshot{}, fmt.Errorf("publication evidence validation failed: %w", err)
		}
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
