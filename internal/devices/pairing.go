package devices

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base32"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"sort"
	"strings"
	"time"
)

const (
	pairingCodeEntropyBytes = 16
	deviceTokenEntropyBytes = 32
	minimumPairingLifetime  = time.Minute
	maximumPairingLifetime  = 15 * time.Minute
)

var (
	ErrInvalidPairingCommand = errors.New("invalid pairing command")
	ErrInvalidPairingRequest = errors.New("invalid pairing request")
	ErrPairingInvalid        = errors.New("pairing code invalid")
	ErrPairingRateLimited    = errors.New("pairing rate limited")
	ErrDeviceUnauthorized    = errors.New("device unauthorized")
	ErrDeviceNotFound        = errors.New("device not found")
	ErrMosqueNotFound        = errors.New("mosque not found")
	ErrInvalidHeartbeat      = errors.New("invalid device heartbeat")
)

type DeviceInfo struct {
	AppVersion   string   `json:"app_version"`
	OSVersion    string   `json:"os_version"`
	Model        string   `json:"model"`
	Capabilities []string `json:"capabilities,omitempty"`
}

type PairingRateLimits struct {
	Window         time.Duration
	SourceAttempts int
	DeviceAttempts int
	CodeAttempts   int
}

type PairingManagerConfig struct {
	Repository   PairingRepository
	Now          func() time.Time
	Random       io.Reader
	RateLimitKey []byte
	RateLimits   PairingRateLimits
}

type PairingRepository interface {
	CreatePairing(context.Context, PairingRecord) error
	RedeemPairing(context.Context, PairingRedemption) (PairingDecision, error)
	AuthenticateDevice(context.Context, [sha256.Size]byte) (DevicePrincipal, error)
	RevokeDevice(context.Context, DeviceRevocation) error
	RecordHeartbeat(context.Context, DeviceHeartbeat) error
}

type PairingBackend interface {
	Pair(context.Context, PairingAttempt) (PairingProvisioning, error)
	Authenticate(context.Context, string) (DevicePrincipal, error)
	Heartbeat(context.Context, DevicePrincipal, DeviceHeartbeatReport) error
}

type DeviceSyncStatus string

const (
	DeviceSyncStatusOK               DeviceSyncStatus = "ok"
	DeviceSyncStatusOffline          DeviceSyncStatus = "offline"
	DeviceSyncStatusTransientFailure DeviceSyncStatus = "transient_failure"
	DeviceSyncStatusRejectedSnapshot DeviceSyncStatus = "rejected_snapshot"
	DeviceSyncStatusAuthFailure      DeviceSyncStatus = "auth_failure"
	DeviceSyncStatusUnknown          DeviceSyncStatus = "unknown"
)

type DeviceHealth string

const (
	DeviceHealthOK       DeviceHealth = "ok"
	DeviceHealthLow      DeviceHealth = "low"
	DeviceHealthCritical DeviceHealth = "critical"
	DeviceHealthUnknown  DeviceHealth = "unknown"
)

type DeviceBootMode string

const (
	DeviceBootModeManual     DeviceBootMode = "manual"
	DeviceBootModeBestEffort DeviceBootMode = "best_effort"
	DeviceBootModeManaged    DeviceBootMode = "managed"
	DeviceBootModeUnknown    DeviceBootMode = "unknown"
)

type DeviceKioskMode string

const (
	DeviceKioskModeNone       DeviceKioskMode = "none"
	DeviceKioskModeBestEffort DeviceKioskMode = "best_effort"
	DeviceKioskModeManaged    DeviceKioskMode = "managed"
	DeviceKioskModeUnknown    DeviceKioskMode = "unknown"
)

type DeviceHeartbeatReport struct {
	SentAt                time.Time        `json:"sent_at"`
	AppVersion            string           `json:"app_version"`
	OSVersion             string           `json:"os_version"`
	Model                 string           `json:"model"`
	ActiveSnapshotID      string           `json:"active_snapshot_id"`
	SyncStatus            DeviceSyncStatus `json:"sync_status"`
	CoverageDaysRemaining int              `json:"coverage_days_remaining"`
	ClockMismatch         bool             `json:"clock_mismatch"`
	TimezoneMismatch      bool             `json:"timezone_mismatch"`
	StorageHealth         DeviceHealth     `json:"storage_health"`
	MemoryHealth          DeviceHealth     `json:"memory_health"`
	BootMode              DeviceBootMode   `json:"boot_mode"`
	KioskMode             DeviceKioskMode  `json:"kiosk_mode"`
}

type DeviceHeartbeat struct {
	DeviceHeartbeatReport
	DeviceID   string
	MosqueID   string
	ReceivedAt time.Time
}

type PairingRecord struct {
	ID        string
	DeviceID  string
	MosqueID  string
	CodeHash  [sha256.Size]byte
	ExpiresAt time.Time
	CreatedAt time.Time
	ActorID   string
	Reason    string
	RequestID string
	AuditID   string
	AfterHash [sha256.Size]byte
}

func (record PairingRecord) String() string {
	return fmt.Sprintf(
		"PairingRecord{id=%q device=%q mosque=%q expires=%q code_sha256=%s}",
		record.ID, record.DeviceID, record.MosqueID, record.ExpiresAt.UTC().Format(time.RFC3339),
		hex.EncodeToString(record.CodeHash[:]),
	)
}

type PairingRedemption struct {
	CodeHash              [sha256.Size]byte
	TokenHash             [sha256.Size]byte
	SourceBucket          [sha256.Size]byte
	DeviceBucket          [sha256.Size]byte
	CodeBucket            [sha256.Size]byte
	RateLimits            PairingRateLimits
	Device                DeviceInfo
	InstallationPublicKey string
	AttemptedAt           time.Time
	RequestID             string
	AuditID               string
	AfterHash             [sha256.Size]byte
}

func (redemption PairingRedemption) String() string {
	return fmt.Sprintf(
		"PairingRedemption{attempted_at=%q code_sha256=%s token_sha256=%s request=%q}",
		redemption.AttemptedAt.UTC().Format(time.RFC3339),
		hex.EncodeToString(redemption.CodeHash[:]), hex.EncodeToString(redemption.TokenHash[:]),
		redemption.RequestID,
	)
}

type PairingOutcome string

const (
	PairingOutcomePaired      PairingOutcome = "paired"
	PairingOutcomeInvalid     PairingOutcome = "invalid"
	PairingOutcomeExpired     PairingOutcome = "expired"
	PairingOutcomeConsumed    PairingOutcome = "consumed"
	PairingOutcomeRevoked     PairingOutcome = "revoked"
	PairingOutcomeRateLimited PairingOutcome = "rate_limited"
)

type PairingDecision struct {
	Outcome PairingOutcome
	Device  DevicePrincipal
}

type DevicePrincipal struct {
	DeviceID string
	Mosque   MosqueIdentity
}

type IssuePairingCommand struct {
	MosqueID  string
	ActorID   string
	Reason    string
	RequestID string
	ExpiresIn time.Duration
}

type IssuedPairing struct {
	DeviceID  string
	Code      string
	ExpiresAt time.Time
}

type PairingAttempt struct {
	Code                  string
	InstallationPublicKey string
	Device                DeviceInfo
	SourceAddress         string
	RequestID             string
}

type PairingProvisioning struct {
	DeviceID string
	Token    string
	Mosque   MosqueIdentity
}

type RevokeDeviceCommand struct {
	MosqueID  string
	DeviceID  string
	ActorID   string
	Reason    string
	RequestID string
}

type DeviceRevocation struct {
	MosqueID  string
	DeviceID  string
	ActorID   string
	Reason    string
	RequestID string
	RevokedAt time.Time
	AuditID   string
	AfterHash [sha256.Size]byte
}

type PairingManager struct {
	repository PairingRepository
	now        func() time.Time
	random     io.Reader
	rateKey    []byte
	rateLimits PairingRateLimits
}

func (manager *PairingManager) Close() {
	if closer, ok := manager.repository.(interface{ Close() }); ok {
		closer.Close()
	}
}

func (manager *PairingManager) Heartbeat(ctx context.Context, principal DevicePrincipal, report DeviceHeartbeatReport) error {
	if validateDevicePrincipal(principal) != nil || !validHeartbeatReport(report) {
		return ErrInvalidHeartbeat
	}
	receivedAt := manager.now().UTC()
	report.SentAt = report.SentAt.UTC()
	if delta := report.SentAt.Sub(receivedAt); delta > 15*time.Minute || delta < -15*time.Minute {
		report.ClockMismatch = true
	}
	err := manager.repository.RecordHeartbeat(ctx, DeviceHeartbeat{
		DeviceHeartbeatReport: report,
		DeviceID:              principal.DeviceID, MosqueID: principal.Mosque.ID, ReceivedAt: receivedAt,
	})
	if errors.Is(err, ErrDeviceUnauthorized) {
		return ErrDeviceUnauthorized
	}
	if err != nil {
		return fmt.Errorf("record device heartbeat: %w", err)
	}
	return nil
}

func NewPairingManager(config PairingManagerConfig) (*PairingManager, error) {
	if config.Repository == nil {
		return nil, errors.New("configure pairing manager: repository is required")
	}
	if len(config.RateLimitKey) != sha256.Size {
		return nil, errors.New("configure pairing manager: rate-limit key must contain 32 bytes")
	}
	if config.RateLimits.Window < time.Minute || config.RateLimits.Window > time.Hour ||
		config.RateLimits.SourceAttempts < 1 || config.RateLimits.SourceAttempts > 1000 ||
		config.RateLimits.DeviceAttempts < 1 || config.RateLimits.DeviceAttempts > 1000 ||
		config.RateLimits.CodeAttempts < 1 || config.RateLimits.CodeAttempts > 1000 {
		return nil, errors.New("configure pairing manager: rate limits are invalid")
	}
	if config.Now == nil {
		config.Now = time.Now
	}
	if config.Random == nil {
		config.Random = rand.Reader
	}
	return &PairingManager{
		repository: config.Repository,
		now:        config.Now, random: config.Random,
		rateKey: append([]byte(nil), config.RateLimitKey...), rateLimits: config.RateLimits,
	}, nil
}

func (manager *PairingManager) Issue(ctx context.Context, command IssuePairingCommand) (IssuedPairing, error) {
	if !validIdentifier(command.MosqueID) || !validIdentifier(command.ActorID) ||
		!validAuditText(command.Reason, 512) || !validIdentifier(command.RequestID) ||
		command.ExpiresIn < minimumPairingLifetime || command.ExpiresIn > maximumPairingLifetime {
		return IssuedPairing{}, ErrInvalidPairingCommand
	}
	deviceID, err := manager.randomUUID()
	if err != nil {
		return IssuedPairing{}, fmt.Errorf("issue pairing: generate identifier entropy: %w", err)
	}
	pairingID, err := manager.randomUUID()
	if err != nil {
		return IssuedPairing{}, fmt.Errorf("issue pairing: generate identifier entropy: %w", err)
	}
	auditID, err := manager.randomUUID()
	if err != nil {
		return IssuedPairing{}, fmt.Errorf("issue pairing: generate identifier entropy: %w", err)
	}
	codeBytes := make([]byte, pairingCodeEntropyBytes)
	if _, err := io.ReadFull(manager.random, codeBytes); err != nil {
		return IssuedPairing{}, fmt.Errorf("issue pairing: generate secret entropy: %w", err)
	}
	code := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(codeBytes)
	now := manager.now().UTC()
	expiresAt := now.Add(command.ExpiresIn)
	record := PairingRecord{
		ID: pairingID, DeviceID: deviceID, MosqueID: command.MosqueID,
		CodeHash: sha256.Sum256([]byte(code)), ExpiresAt: expiresAt, CreatedAt: now,
		ActorID: command.ActorID, Reason: command.Reason, RequestID: command.RequestID,
		AuditID: auditID,
	}
	record.AfterHash = sha256.Sum256([]byte(strings.Join([]string{
		record.ID, record.DeviceID, record.MosqueID, expiresAt.Format(time.RFC3339Nano),
	}, "\x00")))
	if err := manager.repository.CreatePairing(ctx, record); err != nil {
		return IssuedPairing{}, fmt.Errorf("issue pairing: persist record: %w", err)
	}
	return IssuedPairing{DeviceID: deviceID, Code: code, ExpiresAt: expiresAt}, nil
}

func (manager *PairingManager) Pair(ctx context.Context, attempt PairingAttempt) (PairingProvisioning, error) {
	if !validPairingAttempt(attempt) {
		return PairingProvisioning{}, ErrInvalidPairingRequest
	}
	tokenBytes := make([]byte, deviceTokenEntropyBytes)
	if _, err := io.ReadFull(manager.random, tokenBytes); err != nil {
		return PairingProvisioning{}, fmt.Errorf("redeem pairing: generate credential entropy: %w", err)
	}
	auditID, err := manager.randomUUID()
	if err != nil {
		return PairingProvisioning{}, fmt.Errorf("redeem pairing: generate identifier entropy: %w", err)
	}
	token := base64.RawURLEncoding.EncodeToString(tokenBytes)
	codeHash := sha256.Sum256([]byte(attempt.Code))
	tokenHash := sha256.Sum256([]byte(token))
	now := manager.now().UTC()
	redemption := PairingRedemption{
		CodeHash: codeHash, TokenHash: tokenHash,
		SourceBucket: manager.rateBucket("source", attempt.SourceAddress),
		DeviceBucket: manager.rateBucket("device", deviceFingerprint(attempt)),
		CodeBucket:   manager.rateBucket("code", attempt.Code),
		RateLimits:   manager.rateLimits, Device: cloneDeviceInfo(attempt.Device),
		InstallationPublicKey: attempt.InstallationPublicKey,
		AttemptedAt:           now, RequestID: attempt.RequestID, AuditID: auditID,
	}
	redemption.AfterHash = sha256.Sum256([]byte(strings.Join([]string{
		hex.EncodeToString(tokenHash[:]), attempt.Device.AppVersion, attempt.Device.OSVersion,
		attempt.Device.Model, now.Format(time.RFC3339Nano),
	}, "\x00")))
	decision, err := manager.repository.RedeemPairing(ctx, redemption)
	if err != nil {
		return PairingProvisioning{}, fmt.Errorf("redeem pairing: persist transaction: %w", err)
	}
	switch decision.Outcome {
	case PairingOutcomePaired:
		if !validIdentifier(decision.Device.DeviceID) || validateMosqueIdentity(decision.Device.Mosque) != nil {
			return PairingProvisioning{}, errors.New("redeem pairing: repository returned invalid device identity")
		}
		return PairingProvisioning{DeviceID: decision.Device.DeviceID, Token: token, Mosque: decision.Device.Mosque}, nil
	case PairingOutcomeRateLimited:
		return PairingProvisioning{}, ErrPairingRateLimited
	case PairingOutcomeInvalid, PairingOutcomeExpired, PairingOutcomeConsumed, PairingOutcomeRevoked:
		return PairingProvisioning{}, ErrPairingInvalid
	default:
		return PairingProvisioning{}, errors.New("redeem pairing: repository returned invalid outcome")
	}
}

func (manager *PairingManager) Authenticate(ctx context.Context, token string) (DevicePrincipal, error) {
	if len(token) < 16 || len(token) > 4096 {
		return DevicePrincipal{}, ErrDeviceUnauthorized
	}
	principal, err := manager.repository.AuthenticateDevice(ctx, sha256.Sum256([]byte(token)))
	if err != nil {
		if errors.Is(err, ErrDeviceUnauthorized) {
			return DevicePrincipal{}, ErrDeviceUnauthorized
		}
		return DevicePrincipal{}, fmt.Errorf("authenticate device: repository lookup: %w", err)
	}
	if !validIdentifier(principal.DeviceID) || validateMosqueIdentity(principal.Mosque) != nil {
		return DevicePrincipal{}, errors.New("authenticate device: repository returned invalid device identity")
	}
	return principal, nil
}

func (manager *PairingManager) Revoke(ctx context.Context, command RevokeDeviceCommand) error {
	if !validIdentifier(command.MosqueID) || !validIdentifier(command.DeviceID) ||
		!validIdentifier(command.ActorID) || !validAuditText(command.Reason, 512) ||
		!validIdentifier(command.RequestID) {
		return ErrInvalidPairingCommand
	}
	auditID, err := manager.randomUUID()
	if err != nil {
		return fmt.Errorf("revoke device: generate identifier entropy: %w", err)
	}
	now := manager.now().UTC()
	revocation := DeviceRevocation{
		MosqueID: command.MosqueID, DeviceID: command.DeviceID, ActorID: command.ActorID,
		Reason: command.Reason, RequestID: command.RequestID, RevokedAt: now, AuditID: auditID,
	}
	revocation.AfterHash = sha256.Sum256([]byte(strings.Join([]string{
		command.DeviceID, command.MosqueID, "revoked", now.Format(time.RFC3339Nano),
	}, "\x00")))
	if err := manager.repository.RevokeDevice(ctx, revocation); err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return ErrDeviceNotFound
		}
		return fmt.Errorf("revoke device: persist transaction: %w", err)
	}
	return nil
}

func (manager *PairingManager) rateBucket(kind, value string) [sha256.Size]byte {
	digest := hmac.New(sha256.New, manager.rateKey)
	_, _ = digest.Write([]byte("namaz-time/pairing-rate/v1\x00" + kind + "\x00" + value))
	var result [sha256.Size]byte
	copy(result[:], digest.Sum(nil))
	return result
}

func (manager *PairingManager) randomUUID() (string, error) {
	bytes := make([]byte, 16)
	if _, err := io.ReadFull(manager.random, bytes); err != nil {
		return "", err
	}
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x", bytes[0:4], bytes[4:6], bytes[6:8], bytes[8:10], bytes[10:16]), nil
}

func validPairingAttempt(attempt PairingAttempt) bool {
	return len(attempt.Code) >= 6 && len(attempt.Code) <= 32 &&
		attempt.SourceAddress != "" && len(attempt.SourceAddress) <= 256 &&
		validIdentifier(attempt.RequestID) && len(attempt.InstallationPublicKey) <= 4096 &&
		validDeviceInfo(attempt.Device)
}

func validDeviceInfo(info DeviceInfo) bool {
	if info.AppVersion == "" || len(info.AppVersion) > 64 || info.OSVersion == "" ||
		len(info.OSVersion) > 128 || info.Model == "" || len(info.Model) > 240 ||
		len(info.Capabilities) > 128 {
		return false
	}
	seen := make(map[string]struct{}, len(info.Capabilities))
	for _, capability := range info.Capabilities {
		if capability == "" || len(capability) > 128 {
			return false
		}
		if _, duplicate := seen[capability]; duplicate {
			return false
		}
		seen[capability] = struct{}{}
	}
	return true
}

func validateDevicePrincipal(principal DevicePrincipal) error {
	if !validIdentifier(principal.DeviceID) {
		return errors.New("device principal ID is invalid")
	}
	return validateMosqueIdentity(principal.Mosque)
}

func validHeartbeatReport(report DeviceHeartbeatReport) bool {
	if report.SentAt.IsZero() ||
		report.AppVersion == "" || len(report.AppVersion) > 64 ||
		len(report.OSVersion) > 128 || len(report.Model) > 240 ||
		(report.ActiveSnapshotID != "" && !validIdentifier(report.ActiveSnapshotID)) ||
		report.CoverageDaysRemaining < 0 || report.CoverageDaysRemaining > 732 {
		return false
	}
	if !oneOf(report.SyncStatus,
		DeviceSyncStatusOK, DeviceSyncStatusOffline, DeviceSyncStatusTransientFailure,
		DeviceSyncStatusRejectedSnapshot, DeviceSyncStatusAuthFailure, DeviceSyncStatusUnknown,
	) || !oneOf(report.StorageHealth, DeviceHealthOK, DeviceHealthLow, DeviceHealthCritical, DeviceHealthUnknown) ||
		!oneOf(report.MemoryHealth, DeviceHealthOK, DeviceHealthLow, DeviceHealthCritical, DeviceHealthUnknown) ||
		!oneOf(report.BootMode, DeviceBootModeManual, DeviceBootModeBestEffort, DeviceBootModeManaged, DeviceBootModeUnknown) ||
		!oneOf(report.KioskMode, DeviceKioskModeNone, DeviceKioskModeBestEffort, DeviceKioskModeManaged, DeviceKioskModeUnknown) {
		return false
	}
	return true
}

func oneOf[T comparable](value T, allowed ...T) bool {
	for _, candidate := range allowed {
		if value == candidate {
			return true
		}
	}
	return false
}

func validAuditText(value string, maximum int) bool {
	return value != "" && value == strings.TrimSpace(value) && len(value) <= maximum && !strings.ContainsRune(value, '\x00')
}

func validateMosqueIdentity(identity MosqueIdentity) error {
	return validatePairingFixture(PairingFixture{
		Code: "123456", DeviceID: "validation-device", Token: "validation-token-0000", Mosque: identity,
	})
}

func cloneDeviceInfo(info DeviceInfo) DeviceInfo {
	return DeviceInfo{
		AppVersion: info.AppVersion, OSVersion: info.OSVersion, Model: info.Model,
		Capabilities: append([]string(nil), info.Capabilities...),
	}
}

func deviceFingerprint(attempt PairingAttempt) string {
	device := cloneDeviceInfo(attempt.Device)
	sort.Strings(device.Capabilities)
	encoded, _ := json.Marshal(struct {
		Device                DeviceInfo `json:"device"`
		InstallationPublicKey string     `json:"installation_public_key"`
	}{Device: device, InstallationPublicKey: attempt.InstallationPublicKey})
	return string(encoded)
}
