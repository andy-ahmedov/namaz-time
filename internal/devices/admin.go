package devices

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base32"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"
)

var (
	ErrAdminUnauthorized        = errors.New("admin unauthorized")
	ErrAdminResourceNotFound    = errors.New("admin resource not found")
	ErrAdminIdempotencyConflict = errors.New("admin idempotency conflict")
	ErrInvalidAdminRequest      = errors.New("invalid admin request")
	ErrDeviceAssignmentNotFound = errors.New("device assignment not found")
	ErrRolloutGroupTooLarge     = errors.New("rollout group exceeds transaction bound")
)

const MaxRolloutGroupDevices = 100

type AdminRole string

const (
	AdminRoleServiceAdmin  AdminRole = "service_admin"
	AdminRoleMosqueAdmin   AdminRole = "mosque_admin"
	AdminRoleApprover      AdminRole = "approver"
	AdminRoleViewerSupport AdminRole = "viewer_support"
)

type AdminMembership struct {
	MosqueID string
	Role     AdminRole
}

type AdminPrincipal struct {
	ActorID     string
	Memberships []AdminMembership
}

type FleetDevice struct {
	DeviceID              string           `json:"device_id"`
	MosqueID              string           `json:"mosque_id"`
	Status                string           `json:"status"`
	AppVersion            string           `json:"app_version,omitempty"`
	OSVersion             string           `json:"os_version,omitempty"`
	Model                 string           `json:"model,omitempty"`
	CreatedAt             time.Time        `json:"created_at"`
	PairedAt              *time.Time       `json:"paired_at,omitempty"`
	RevokedAt             *time.Time       `json:"revoked_at,omitempty"`
	SnapshotID            string           `json:"snapshot_id,omitempty"`
	ManifestVersion       int64            `json:"manifest_version,omitempty"`
	LastSeenAt            *time.Time       `json:"last_seen_at,omitempty"`
	ReportedSnapshotID    string           `json:"reported_snapshot_id,omitempty"`
	SyncStatus            DeviceSyncStatus `json:"sync_status,omitempty"`
	CoverageDaysRemaining *int             `json:"coverage_days_remaining,omitempty"`
	ClockMismatch         *bool            `json:"clock_mismatch,omitempty"`
	TimezoneMismatch      *bool            `json:"timezone_mismatch,omitempty"`
	StorageHealth         DeviceHealth     `json:"storage_health,omitempty"`
	MemoryHealth          DeviceHealth     `json:"memory_health,omitempty"`
	BootMode              DeviceBootMode   `json:"boot_mode,omitempty"`
	KioskMode             DeviceKioskMode  `json:"kiosk_mode,omitempty"`
	RolloutGroup          string           `json:"rollout_group,omitempty"`
}

type AdminIssuePairingCommand struct {
	MosqueID       string
	Reason         string
	RequestID      string
	IdempotencyKey string
	ExpiresIn      time.Duration
}

type AdminRevokeDeviceCommand struct {
	MosqueID       string
	DeviceID       string
	Reason         string
	RequestID      string
	IdempotencyKey string
}

type AdminAssignDeviceCommand struct {
	MosqueID          string
	DeviceID          string
	SnapshotID        string
	SnapshotURL       string
	SnapshotSHA256    string
	SigningKeyID      string
	SnapshotMosqueID  string
	SnapshotTimezone  string
	MinimumAppVersion string
	Reason            string
	RequestID         string
	IdempotencyKey    string
}

type AdminAssignmentRetryQuery struct {
	MosqueID          string
	DeviceID          string
	SnapshotID        string
	MinimumAppVersion string
	Reason            string
	IdempotencyKey    string
}

type AdminSetRolloutGroupCommand struct {
	MosqueID       string
	DeviceID       string
	RolloutGroup   string
	Reason         string
	RequestID      string
	IdempotencyKey string
}

type AdminAssignRolloutGroupCommand struct {
	MosqueID          string
	RolloutGroup      string
	SnapshotID        string
	SnapshotURL       string
	SnapshotSHA256    string
	SigningKeyID      string
	SnapshotMosqueID  string
	SnapshotTimezone  string
	MinimumAppVersion string
	Reason            string
	RequestID         string
	IdempotencyKey    string
}

type AdminRolloutAssignmentRetryQuery struct {
	MosqueID          string
	RolloutGroup      string
	SnapshotID        string
	MinimumAppVersion string
	Reason            string
	IdempotencyKey    string
}

type RolloutAssignmentResult struct {
	RolloutGroup string             `json:"rollout_group"`
	SnapshotID   string             `json:"snapshot_id"`
	DeviceCount  int                `json:"device_count"`
	Assignments  []DeviceAssignment `json:"assignments"`
}

type AdminRepositoryScope struct {
	ActorID     string
	MosqueID    string
	GlobalAdmin bool
}

type AdminPairingMutation struct {
	Scope           AdminRepositoryScope
	Record          PairingRecord
	IdempotencyHash [sha256.Size]byte
	RequestHash     [sha256.Size]byte
}

type AdminRevocationMutation struct {
	Scope           AdminRepositoryScope
	DeviceID        string
	Reason          string
	RequestID       string
	RevokedAt       time.Time
	AuditID         string
	AfterHash       [sha256.Size]byte
	IdempotencyHash [sha256.Size]byte
	RequestHash     [sha256.Size]byte
}

type AdminAssignmentMutation struct {
	Scope           AdminRepositoryScope
	Command         AdminAssignDeviceCommand
	Assignment      DeviceAssignment
	AssignedAt      time.Time
	AuditID         string
	IdempotencyHash [sha256.Size]byte
	RequestHash     [sha256.Size]byte
}

type AdminRolloutGroupMutation struct {
	Scope           AdminRepositoryScope
	Command         AdminSetRolloutGroupCommand
	ChangedAt       time.Time
	AuditID         string
	IdempotencyHash [sha256.Size]byte
	RequestHash     [sha256.Size]byte
}

type AdminRolloutAssignmentMutation struct {
	Scope           AdminRepositoryScope
	Command         AdminAssignRolloutGroupCommand
	AssignedAt      time.Time
	AuditSeed       [sha256.Size]byte
	IdempotencyHash [sha256.Size]byte
	RequestHash     [sha256.Size]byte
}

type AdminFleetRepository interface {
	AuthenticateAdmin(context.Context, [sha256.Size]byte) (AdminPrincipal, error)
	CreateAdminPairing(context.Context, AdminPairingMutation) (PairingRecord, error)
	ListAdminDevices(context.Context, AdminRepositoryScope) ([]FleetDevice, error)
	RevokeAdminDevice(context.Context, AdminRevocationMutation) error
	ReadAdminAssignmentRetry(context.Context, AdminRepositoryScope, [sha256.Size]byte, [sha256.Size]byte) (DeviceAssignment, bool, error)
	AssignAdminDevice(context.Context, AdminAssignmentMutation) (DeviceAssignment, error)
	SetAdminDeviceRolloutGroup(context.Context, AdminRolloutGroupMutation) error
	ReadAdminRolloutAssignmentRetry(context.Context, AdminRepositoryScope, [sha256.Size]byte, [sha256.Size]byte) (RolloutAssignmentResult, bool, error)
	AssignAdminRolloutGroup(context.Context, AdminRolloutAssignmentMutation) (RolloutAssignmentResult, error)
	GetDeviceAssignment(context.Context, string, string) (DeviceAssignment, error)
}

type AdminFleetBackend interface {
	AuthenticateAdmin(context.Context, string) (AdminPrincipal, error)
	AuthorizeAdminScope(AdminPrincipal, string, bool) error
	IssuePairing(context.Context, AdminPrincipal, AdminIssuePairingCommand) (IssuedPairing, error)
	ListDevices(context.Context, AdminPrincipal, string) ([]FleetDevice, error)
	RevokeDevice(context.Context, AdminPrincipal, AdminRevokeDeviceCommand) error
	RetryAssignment(context.Context, AdminPrincipal, AdminAssignmentRetryQuery) (DeviceAssignment, bool, error)
	AssignDevice(context.Context, AdminPrincipal, AdminAssignDeviceCommand) (DeviceAssignment, error)
	SetDeviceRolloutGroup(context.Context, AdminPrincipal, AdminSetRolloutGroupCommand) error
	RetryRolloutAssignment(context.Context, AdminPrincipal, AdminRolloutAssignmentRetryQuery) (RolloutAssignmentResult, bool, error)
	AssignRolloutGroup(context.Context, AdminPrincipal, AdminAssignRolloutGroupCommand) (RolloutAssignmentResult, error)
	GetDeviceAssignment(context.Context, string, string) (DeviceAssignment, error)
}

func (manager *AdminFleetManager) AuthorizeAdminScope(principal AdminPrincipal, mosqueID string, write bool) error {
	if !validIdentifier(mosqueID) {
		return ErrInvalidAdminRequest
	}
	var allowed bool
	if write {
		_, allowed = manager.writeScope(principal, mosqueID)
	} else {
		_, allowed = manager.readScope(principal, mosqueID)
	}
	if !allowed {
		return ErrAdminResourceNotFound
	}
	return nil
}

type AdminFleetManagerConfig struct {
	Repository                   AdminFleetRepository
	Now                          func() time.Time
	IdempotencyKey               []byte
	CompatibilityIdempotencyKeys [][]byte
}

type AdminFleetManager struct {
	repository      AdminFleetRepository
	now             func() time.Time
	idempotencyKeys [][]byte
}

func NewAdminFleetManager(config AdminFleetManagerConfig) (*AdminFleetManager, error) {
	if config.Repository == nil {
		return nil, errors.New("configure admin fleet manager: repository is required")
	}
	if len(config.IdempotencyKey) != sha256.Size {
		return nil, errors.New("configure admin fleet manager: idempotency key must contain 32 bytes")
	}
	if len(config.CompatibilityIdempotencyKeys) > 8 {
		return nil, errors.New("configure admin fleet manager: at most eight compatibility idempotency keys are allowed")
	}
	keys := make([][]byte, 0, 1+len(config.CompatibilityIdempotencyKeys))
	keys = append(keys, append([]byte(nil), config.IdempotencyKey...))
	for _, key := range config.CompatibilityIdempotencyKeys {
		if len(key) != sha256.Size {
			return nil, errors.New("configure admin fleet manager: compatibility idempotency key must contain 32 bytes")
		}
		keys = append(keys, append([]byte(nil), key...))
	}
	if config.Now == nil {
		config.Now = time.Now
	}
	return &AdminFleetManager{
		repository: config.Repository, now: config.Now,
		idempotencyKeys: keys,
	}, nil
}

func (manager *AdminFleetManager) AuthenticateAdmin(ctx context.Context, token string) (AdminPrincipal, error) {
	if len(token) < 24 || len(token) > 4096 {
		return AdminPrincipal{}, ErrAdminUnauthorized
	}
	principal, err := manager.repository.AuthenticateAdmin(ctx, sha256.Sum256([]byte(token)))
	if err != nil {
		if errors.Is(err, ErrAdminUnauthorized) {
			return AdminPrincipal{}, ErrAdminUnauthorized
		}
		return AdminPrincipal{}, fmt.Errorf("authenticate admin: repository lookup: %w", err)
	}
	if err := validateAdminPrincipal(principal); err != nil {
		return AdminPrincipal{}, errors.New("authenticate admin: repository returned invalid principal")
	}
	return cloneAdminPrincipal(principal), nil
}

func (manager *AdminFleetManager) IssuePairing(ctx context.Context, principal AdminPrincipal, command AdminIssuePairingCommand) (IssuedPairing, error) {
	if !validIdentifier(command.MosqueID) || !validAuditText(command.Reason, 512) ||
		!validIdentifier(command.RequestID) || !validIdempotencyKey(command.IdempotencyKey) ||
		command.ExpiresIn < minimumPairingLifetime || command.ExpiresIn > maximumPairingLifetime {
		return IssuedPairing{}, ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, command.MosqueID)
	if !allowed {
		return IssuedPairing{}, ErrAdminResourceNotFound
	}
	idempotencyHash := hashAdminRequest("idempotency", principal.ActorID, "issue_pairing", command.IdempotencyKey)
	requestHash := hashAdminRequest("issue_pairing", command.MosqueID, command.Reason, command.ExpiresIn.String())
	codeBytes := manager.digest(0, "pairing_code", principal.ActorID, command.MosqueID, command.IdempotencyKey)
	code := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(codeBytes[:pairingCodeEntropyBytes])
	now := manager.now().UTC()
	record := PairingRecord{
		ID:       deterministicUUID(manager.digest(0, "pairing_id", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
		DeviceID: deterministicUUID(manager.digest(0, "device_id", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
		MosqueID: command.MosqueID, CodeHash: sha256.Sum256([]byte(code)),
		ExpiresAt: now.Add(command.ExpiresIn), CreatedAt: now, ActorID: principal.ActorID,
		Reason: command.Reason, RequestID: command.RequestID,
		AuditID: deterministicUUID(manager.digest(0, "audit_issue", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
	}
	record.AfterHash = hashAdminRequest(
		record.ID, record.DeviceID, record.MosqueID, fmt.Sprintf("%x", record.CodeHash),
		record.ExpiresAt.Format(time.RFC3339Nano),
	)
	stored, err := manager.repository.CreateAdminPairing(ctx, AdminPairingMutation{
		Scope: scope, Record: record, IdempotencyHash: idempotencyHash, RequestHash: requestHash,
	})
	if err != nil {
		return IssuedPairing{}, mapAdminRepositoryError("issue pairing", err)
	}
	if stored.MosqueID != command.MosqueID || !validIdentifier(stored.DeviceID) ||
		!stored.ExpiresAt.After(stored.CreatedAt) {
		return IssuedPairing{}, errors.New("issue pairing: repository returned invalid idempotent result")
	}
	responseCode := code
	if stored.CodeHash != record.CodeHash {
		responseCode = ""
		for keyIndex := 1; keyIndex < len(manager.idempotencyKeys); keyIndex++ {
			candidateBytes := manager.digest(keyIndex, "pairing_code", principal.ActorID, command.MosqueID, command.IdempotencyKey)
			candidate := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(candidateBytes[:pairingCodeEntropyBytes])
			if sha256.Sum256([]byte(candidate)) == stored.CodeHash {
				responseCode = candidate
				break
			}
		}
		if responseCode == "" {
			return IssuedPairing{}, errors.New("issue pairing: idempotency key ring cannot reproduce stored code")
		}
	}
	return IssuedPairing{DeviceID: stored.DeviceID, Code: responseCode, ExpiresAt: stored.ExpiresAt}, nil
}

func (manager *AdminFleetManager) ListDevices(ctx context.Context, principal AdminPrincipal, mosqueID string) ([]FleetDevice, error) {
	if !validIdentifier(mosqueID) {
		return nil, ErrInvalidAdminRequest
	}
	scope, allowed := manager.readScope(principal, mosqueID)
	if !allowed {
		return nil, ErrAdminResourceNotFound
	}
	devices, err := manager.repository.ListAdminDevices(ctx, scope)
	if err != nil {
		return nil, mapAdminRepositoryError("list devices", err)
	}
	return append([]FleetDevice(nil), devices...), nil
}

func (manager *AdminFleetManager) RevokeDevice(ctx context.Context, principal AdminPrincipal, command AdminRevokeDeviceCommand) error {
	if !validIdentifier(command.MosqueID) || !validIdentifier(command.DeviceID) ||
		!validAuditText(command.Reason, 512) || !validIdentifier(command.RequestID) ||
		!validIdempotencyKey(command.IdempotencyKey) {
		return ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, command.MosqueID)
	if !allowed {
		return ErrAdminResourceNotFound
	}
	now := manager.now().UTC()
	idempotencyHash := hashAdminRequest("idempotency", principal.ActorID, "revoke_device", command.IdempotencyKey)
	requestHash := hashAdminRequest("revoke_device", command.MosqueID, command.DeviceID, command.Reason)
	mutation := AdminRevocationMutation{
		Scope: scope, DeviceID: command.DeviceID, Reason: command.Reason, RequestID: command.RequestID,
		RevokedAt: now, AuditID: deterministicUUID(manager.digest(0, "audit_revoke", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
		IdempotencyHash: idempotencyHash, RequestHash: requestHash,
	}
	mutation.AfterHash = hashAdminRequest(command.DeviceID, command.MosqueID, "revoked", now.Format(time.RFC3339Nano))
	if err := manager.repository.RevokeAdminDevice(ctx, mutation); err != nil {
		return mapAdminRepositoryError("revoke device", err)
	}
	return nil
}

func (manager *AdminFleetManager) AssignDevice(ctx context.Context, principal AdminPrincipal, command AdminAssignDeviceCommand) (DeviceAssignment, error) {
	parsedSnapshotURL, urlErr := url.Parse(command.SnapshotURL)
	snapshotZone, zoneErr := time.LoadLocation(command.SnapshotTimezone)
	if !validIdentifier(command.MosqueID) || !validIdentifier(command.DeviceID) ||
		!validIdentifier(command.SnapshotID) || !validSHA256(command.SnapshotSHA256) ||
		len(command.SigningKeyID) < 1 || len(command.SigningKeyID) > 128 ||
		command.SnapshotMosqueID != command.MosqueID || len(command.SnapshotTimezone) < 3 ||
		len(command.SnapshotTimezone) > 64 || len(command.MinimumAppVersion) > 64 ||
		zoneErr != nil || snapshotZone.String() != command.SnapshotTimezone ||
		strings.HasPrefix(command.SnapshotTimezone, "+") || strings.HasPrefix(command.SnapshotTimezone, "-") ||
		urlErr != nil || parsedSnapshotURL.Scheme != "https" || parsedSnapshotURL.Host == "" ||
		parsedSnapshotURL.User != nil || parsedSnapshotURL.RawQuery != "" || parsedSnapshotURL.Fragment != "" ||
		len(command.SnapshotURL) > 2048 ||
		!validAuditText(command.Reason, 512) || !validIdentifier(command.RequestID) ||
		!validIdempotencyKey(command.IdempotencyKey) {
		return DeviceAssignment{}, ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, command.MosqueID)
	if !allowed {
		return DeviceAssignment{}, ErrAdminResourceNotFound
	}
	idempotencyHash := hashAdminRequest("idempotency", principal.ActorID, "assign_device", command.IdempotencyKey)
	requestHash := hashAdminAssignmentRequest(command.MosqueID, command.DeviceID, command.SnapshotID, command.MinimumAppVersion, command.Reason)
	assignment := DeviceAssignment{
		DeviceID: command.DeviceID, SnapshotID: command.SnapshotID, SnapshotURL: command.SnapshotURL,
		SnapshotSHA256: command.SnapshotSHA256, SigningKeyID: command.SigningKeyID,
		MinimumAppVersion: command.MinimumAppVersion,
	}
	now := manager.now().UTC()
	mutation := AdminAssignmentMutation{
		Scope: scope, Command: command, Assignment: assignment, AssignedAt: now,
		AuditID:         deterministicUUID(manager.digest(0, "audit_assign", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
		IdempotencyHash: idempotencyHash, RequestHash: requestHash,
	}
	stored, err := manager.repository.AssignAdminDevice(ctx, mutation)
	if err != nil {
		return DeviceAssignment{}, mapAdminRepositoryError("assign device", err)
	}
	if stored.DeviceID != command.DeviceID || stored.SnapshotID != command.SnapshotID ||
		stored.SnapshotSHA256 != command.SnapshotSHA256 || stored.ManifestVersion < 1 {
		return DeviceAssignment{}, errors.New("assign device: repository returned invalid assignment")
	}
	return stored, nil
}

// RetryAssignment resolves append-only idempotency evidence before the caller
// consults the mutable in-process artifact registry. This keeps an exact retry
// reproducible for the documented window even after deployment configuration
// changes or removes the artifact.
func (manager *AdminFleetManager) RetryAssignment(ctx context.Context, principal AdminPrincipal, query AdminAssignmentRetryQuery) (DeviceAssignment, bool, error) {
	if !validIdentifier(query.MosqueID) || !validIdentifier(query.DeviceID) ||
		!validIdentifier(query.SnapshotID) || len(query.MinimumAppVersion) > 64 ||
		!validAuditText(query.Reason, 512) || !validIdempotencyKey(query.IdempotencyKey) {
		return DeviceAssignment{}, false, ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, query.MosqueID)
	if !allowed {
		return DeviceAssignment{}, false, ErrAdminResourceNotFound
	}
	idempotencyHash := hashAdminRequest("idempotency", principal.ActorID, "assign_device", query.IdempotencyKey)
	requestHash := hashAdminAssignmentRequest(query.MosqueID, query.DeviceID, query.SnapshotID, query.MinimumAppVersion, query.Reason)
	assignment, found, err := manager.repository.ReadAdminAssignmentRetry(ctx, scope, idempotencyHash, requestHash)
	if err != nil {
		return DeviceAssignment{}, false, mapAdminRepositoryError("retry assignment", err)
	}
	return assignment, found, nil
}

func hashAdminAssignmentRequest(mosqueID, deviceID, snapshotID, minimumAppVersion, reason string) [sha256.Size]byte {
	return hashAdminRequest("assign_device", mosqueID, deviceID, snapshotID, minimumAppVersion, reason)
}

func (manager *AdminFleetManager) SetDeviceRolloutGroup(
	ctx context.Context,
	principal AdminPrincipal,
	command AdminSetRolloutGroupCommand,
) error {
	if !validIdentifier(command.MosqueID) || !validIdentifier(command.DeviceID) ||
		(command.RolloutGroup != "" && !validRolloutGroup(command.RolloutGroup)) ||
		!validAuditText(command.Reason, 512) || !validIdentifier(command.RequestID) ||
		!validIdempotencyKey(command.IdempotencyKey) {
		return ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, command.MosqueID)
	if !allowed {
		return ErrAdminResourceNotFound
	}
	mutation := AdminRolloutGroupMutation{
		Scope: scope, Command: command, ChangedAt: manager.now().UTC(),
		AuditID:         deterministicUUID(manager.digest(0, "audit_rollout_group", principal.ActorID, command.MosqueID, command.IdempotencyKey)),
		IdempotencyHash: hashAdminRequest("idempotency", principal.ActorID, "set_rollout_group", command.IdempotencyKey),
		RequestHash: hashAdminRequest(
			"set_rollout_group", command.MosqueID, command.DeviceID, command.RolloutGroup, command.Reason,
		),
	}
	if err := manager.repository.SetAdminDeviceRolloutGroup(ctx, mutation); err != nil {
		return mapAdminRepositoryError("set device rollout group", err)
	}
	return nil
}

func (manager *AdminFleetManager) AssignRolloutGroup(
	ctx context.Context,
	principal AdminPrincipal,
	command AdminAssignRolloutGroupCommand,
) (RolloutAssignmentResult, error) {
	if !validRolloutGroup(command.RolloutGroup) || !validRolloutAssignmentCommand(command) {
		return RolloutAssignmentResult{}, ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, command.MosqueID)
	if !allowed {
		return RolloutAssignmentResult{}, ErrAdminResourceNotFound
	}
	mutation := AdminRolloutAssignmentMutation{
		Scope: scope, Command: command, AssignedAt: manager.now().UTC(),
		AuditSeed:       manager.digest(0, "audit_rollout_assign", principal.ActorID, command.MosqueID, command.IdempotencyKey),
		IdempotencyHash: hashAdminRequest("idempotency", principal.ActorID, "assign_rollout_group", command.IdempotencyKey),
		RequestHash: hashAdminRolloutAssignmentRequest(
			command.MosqueID, command.RolloutGroup, command.SnapshotID, command.MinimumAppVersion, command.Reason,
		),
	}
	result, err := manager.repository.AssignAdminRolloutGroup(ctx, mutation)
	if err != nil {
		return RolloutAssignmentResult{}, mapAdminRepositoryError("assign rollout group", err)
	}
	if result.RolloutGroup != command.RolloutGroup || result.SnapshotID != command.SnapshotID ||
		result.DeviceCount < 1 || result.DeviceCount > MaxRolloutGroupDevices ||
		len(result.Assignments) != result.DeviceCount {
		return RolloutAssignmentResult{}, errors.New("assign rollout group: repository returned invalid result")
	}
	return result, nil
}

func (manager *AdminFleetManager) RetryRolloutAssignment(
	ctx context.Context,
	principal AdminPrincipal,
	query AdminRolloutAssignmentRetryQuery,
) (RolloutAssignmentResult, bool, error) {
	if !validIdentifier(query.MosqueID) || !validRolloutGroup(query.RolloutGroup) ||
		!validIdentifier(query.SnapshotID) || len(query.MinimumAppVersion) > 64 ||
		!validAuditText(query.Reason, 512) || !validIdempotencyKey(query.IdempotencyKey) {
		return RolloutAssignmentResult{}, false, ErrInvalidAdminRequest
	}
	scope, allowed := manager.writeScope(principal, query.MosqueID)
	if !allowed {
		return RolloutAssignmentResult{}, false, ErrAdminResourceNotFound
	}
	idempotencyHash := hashAdminRequest("idempotency", principal.ActorID, "assign_rollout_group", query.IdempotencyKey)
	requestHash := hashAdminRolloutAssignmentRequest(
		query.MosqueID, query.RolloutGroup, query.SnapshotID, query.MinimumAppVersion, query.Reason,
	)
	result, found, err := manager.repository.ReadAdminRolloutAssignmentRetry(ctx, scope, idempotencyHash, requestHash)
	if err != nil {
		return RolloutAssignmentResult{}, false, mapAdminRepositoryError("retry rollout assignment", err)
	}
	return result, found, nil
}

func validRolloutAssignmentCommand(command AdminAssignRolloutGroupCommand) bool {
	parsedSnapshotURL, urlErr := url.Parse(command.SnapshotURL)
	snapshotZone, zoneErr := time.LoadLocation(command.SnapshotTimezone)
	return validIdentifier(command.MosqueID) && validIdentifier(command.SnapshotID) &&
		validSHA256(command.SnapshotSHA256) && len(command.SigningKeyID) >= 1 &&
		len(command.SigningKeyID) <= 128 && command.SnapshotMosqueID == command.MosqueID &&
		len(command.SnapshotTimezone) >= 3 && len(command.SnapshotTimezone) <= 64 &&
		len(command.MinimumAppVersion) <= 64 && zoneErr == nil &&
		snapshotZone.String() == command.SnapshotTimezone &&
		!strings.HasPrefix(command.SnapshotTimezone, "+") && !strings.HasPrefix(command.SnapshotTimezone, "-") &&
		urlErr == nil && parsedSnapshotURL.Scheme == "https" && parsedSnapshotURL.Host != "" &&
		parsedSnapshotURL.User == nil && parsedSnapshotURL.RawQuery == "" && parsedSnapshotURL.Fragment == "" &&
		len(command.SnapshotURL) <= 2048 && validAuditText(command.Reason, 512) &&
		validIdentifier(command.RequestID) && validIdempotencyKey(command.IdempotencyKey)
}

func validRolloutGroup(group string) bool {
	if len(group) < 8 || len(group) > 64 || !asciiLetterOrDigit(group[0]) {
		return false
	}
	for index := 1; index < len(group); index++ {
		if !asciiLetterOrDigit(group[index]) && group[index] != '.' && group[index] != '_' && group[index] != '-' {
			return false
		}
	}
	return true
}

func asciiLetterOrDigit(value byte) bool {
	return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value >= '0' && value <= '9'
}

func hashAdminRolloutAssignmentRequest(mosqueID, group, snapshotID, minimumAppVersion, reason string) [sha256.Size]byte {
	return hashAdminRequest("assign_rollout_group", mosqueID, group, snapshotID, minimumAppVersion, reason)
}

func (manager *AdminFleetManager) GetDeviceAssignment(ctx context.Context, deviceID, mosqueID string) (DeviceAssignment, error) {
	if !validIdentifier(deviceID) || !validIdentifier(mosqueID) {
		return DeviceAssignment{}, ErrDeviceAssignmentNotFound
	}
	assignment, err := manager.repository.GetDeviceAssignment(ctx, deviceID, mosqueID)
	if err != nil {
		if errors.Is(err, ErrDeviceAssignmentNotFound) {
			return DeviceAssignment{}, ErrDeviceAssignmentNotFound
		}
		return DeviceAssignment{}, fmt.Errorf("get device assignment: repository lookup: %w", err)
	}
	return assignment, nil
}

func (manager *AdminFleetManager) readScope(principal AdminPrincipal, mosqueID string) (AdminRepositoryScope, bool) {
	if validateAdminPrincipal(principal) != nil {
		return AdminRepositoryScope{}, false
	}
	for _, membership := range principal.Memberships {
		if membership.Role == AdminRoleServiceAdmin {
			return AdminRepositoryScope{ActorID: principal.ActorID, MosqueID: mosqueID, GlobalAdmin: true}, true
		}
		if membership.MosqueID == mosqueID && (membership.Role == AdminRoleMosqueAdmin ||
			membership.Role == AdminRoleViewerSupport || membership.Role == AdminRoleApprover) {
			return AdminRepositoryScope{ActorID: principal.ActorID, MosqueID: mosqueID}, true
		}
	}
	return AdminRepositoryScope{}, false
}

func (manager *AdminFleetManager) writeScope(principal AdminPrincipal, mosqueID string) (AdminRepositoryScope, bool) {
	if validateAdminPrincipal(principal) != nil {
		return AdminRepositoryScope{}, false
	}
	for _, membership := range principal.Memberships {
		if membership.Role == AdminRoleServiceAdmin {
			return AdminRepositoryScope{ActorID: principal.ActorID, MosqueID: mosqueID, GlobalAdmin: true}, true
		}
		if membership.MosqueID == mosqueID && membership.Role == AdminRoleMosqueAdmin {
			return AdminRepositoryScope{ActorID: principal.ActorID, MosqueID: mosqueID}, true
		}
	}
	return AdminRepositoryScope{}, false
}

func (manager *AdminFleetManager) digest(keyIndex int, parts ...string) [sha256.Size]byte {
	digest := hmac.New(sha256.New, manager.idempotencyKeys[keyIndex])
	_, _ = digest.Write([]byte("namaz-time/admin-idempotency/v1\x00" + strings.Join(parts, "\x00")))
	var result [sha256.Size]byte
	copy(result[:], digest.Sum(nil))
	return result
}

func hashAdminRequest(parts ...string) [sha256.Size]byte {
	return sha256.Sum256([]byte("namaz-time/admin-request/v1\x00" + strings.Join(parts, "\x00")))
}

func deterministicUUID(digest [sha256.Size]byte) string {
	bytes := digest[:16]
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x", bytes[0:4], bytes[4:6], bytes[6:8], bytes[8:10], bytes[10:16])
}

func validIdempotencyKey(value string) bool {
	return len(value) >= 8 && len(value) <= 128 && value == strings.TrimSpace(value) && !strings.ContainsRune(value, '\x00')
}

func validateAdminPrincipal(principal AdminPrincipal) error {
	if !validIdentifier(principal.ActorID) || len(principal.Memberships) < 1 || len(principal.Memberships) > 256 {
		return errors.New("admin principal is invalid")
	}
	seen := make(map[string]struct{}, len(principal.Memberships))
	for _, membership := range principal.Memberships {
		valid := false
		switch membership.Role {
		case AdminRoleServiceAdmin:
			valid = membership.MosqueID == ""
		case AdminRoleMosqueAdmin, AdminRoleApprover, AdminRoleViewerSupport:
			valid = validIdentifier(membership.MosqueID)
		}
		key := string(membership.Role) + "\x00" + membership.MosqueID
		if !valid {
			return errors.New("admin membership is invalid")
		}
		if _, duplicate := seen[key]; duplicate {
			return errors.New("admin membership is duplicated")
		}
		seen[key] = struct{}{}
	}
	return nil
}

func cloneAdminPrincipal(principal AdminPrincipal) AdminPrincipal {
	return AdminPrincipal{ActorID: principal.ActorID, Memberships: append([]AdminMembership(nil), principal.Memberships...)}
}

func mapAdminRepositoryError(operation string, err error) error {
	switch {
	case errors.Is(err, ErrAdminIdempotencyConflict):
		return ErrAdminIdempotencyConflict
	case errors.Is(err, ErrAdminResourceNotFound), errors.Is(err, ErrDeviceNotFound), errors.Is(err, ErrMosqueNotFound):
		return ErrAdminResourceNotFound
	default:
		return fmt.Errorf("%s: persist transaction: %w", operation, err)
	}
}
