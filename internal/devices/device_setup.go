package devices

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

var (
	ErrInvalidDeviceSetupRequest = errors.New("invalid device setup request")
	ErrDeviceSetupUnavailable    = errors.New("device setup unavailable")
	ErrDeviceSetupNotRequestable = errors.New("device setup choice is not requestable")
	ErrDeviceSetupConflict       = errors.New("device setup request conflict")
)

type DeviceBindingOrigin string

const DeviceBindingOriginLocalTVOperator DeviceBindingOrigin = "local_tv_operator"

type DeviceScheduleChoiceCommand struct {
	CityID        string
	ChoiceID      string
	Date          string
	InteractionID string
}

type DeviceRegistryBindingRequest struct {
	ID              string                  `json:"id"`
	RevisionID      string                  `json:"revision_id"`
	CityID          string                  `json:"city_id"`
	PolicyID        string                  `json:"policy_id"`
	ChoiceID        string                  `json:"choice_id"`
	MosqueID        string                  `json:"mosque_id"`
	DeviceID        string                  `json:"device_id"`
	Date            string                  `json:"date"`
	Tier            registry.ResolutionTier `json:"resolution_tier"`
	Status          RegistryBindingStatus   `json:"status"`
	SelectionSHA256 string                  `json:"selection_sha256"`
	Origin          DeviceBindingOrigin     `json:"origin"`
	InteractionID   string                  `json:"interaction_id"`
	RequestedAt     time.Time               `json:"requested_at"`
	AuditID         string                  `json:"-"`
}

type DeviceSetupRegistry interface {
	SearchCities(context.Context, string) ([]registry.CitySearchResult, error)
	SearchRevisionCities(context.Context, string, string) ([]registry.CitySearchResult, error)
	AssessRevision(context.Context, string, registry.ResolveRequest) (registry.RevisionPolicyAssessment, error)
}

type DeviceSetupRepository interface {
	CreateDeviceRegistryBindingRequest(context.Context, DeviceRegistryBindingRequest) (DeviceRegistryBindingRequest, error)
}

type DeviceSetupManagerConfig struct {
	Registry         DeviceSetupRegistry
	Repository       DeviceSetupRepository
	SetupRevisionIDs map[string]string
	Now              func() time.Time
}

type DeviceSetupManager struct {
	registry         DeviceSetupRegistry
	repository       DeviceSetupRepository
	setupRevisionIDs map[string]string
	now              func() time.Time
}

func NewDeviceSetupManager(config DeviceSetupManagerConfig) (*DeviceSetupManager, error) {
	if config.Registry == nil {
		return nil, errors.New("configure device setup: registry is required")
	}
	revisions := make(map[string]string, len(config.SetupRevisionIDs))
	for mosqueID, revisionID := range config.SetupRevisionIDs {
		if !validIdentifier(mosqueID) || !validRegistryIdentifier(revisionID) {
			return nil, errors.New("configure device setup: mosque or revision identifier is invalid")
		}
		revisions[mosqueID] = revisionID
	}
	now := config.Now
	if now == nil {
		now = time.Now
	}
	return &DeviceSetupManager{
		registry: config.Registry, repository: config.Repository,
		setupRevisionIDs: revisions, now: now,
	}, nil
}

func (manager *DeviceSetupManager) SearchCities(
	ctx context.Context,
	principal DevicePrincipal,
	query string,
) ([]registry.CitySearchResult, error) {
	if manager == nil || manager.registry == nil {
		return nil, ErrDeviceSetupUnavailable
	}
	if validateDevicePrincipal(principal) != nil || !validSetupQuery(query) {
		return nil, ErrInvalidDeviceSetupRequest
	}
	revisionID := manager.setupRevisionIDs[principal.Mosque.ID]
	var cities []registry.CitySearchResult
	var err error
	if revisionID == "" {
		cities, err = manager.registry.SearchCities(ctx, query)
	} else {
		cities, err = manager.registry.SearchRevisionCities(ctx, revisionID, query)
	}
	if err != nil {
		return nil, fmt.Errorf("search device setup cities: %w", err)
	}
	return append([]registry.CitySearchResult(nil), cities...), nil
}

func (manager *DeviceSetupManager) ScheduleChoices(
	ctx context.Context,
	principal DevicePrincipal,
	cityID string,
	date string,
) (registry.CityScheduleChoiceSet, error) {
	if manager == nil || manager.registry == nil {
		return registry.CityScheduleChoiceSet{}, ErrDeviceSetupUnavailable
	}
	if validateDevicePrincipal(principal) != nil || !validRegistryIdentifier(cityID) || !validSetupDate(date) {
		return registry.CityScheduleChoiceSet{}, ErrInvalidDeviceSetupRequest
	}
	revisionID := manager.setupRevisionIDs[principal.Mosque.ID]
	assessment, err := manager.registry.AssessRevision(ctx, revisionID, registry.ResolveRequest{
		CityID: cityID, MosqueID: principal.Mosque.ID, Date: date,
	})
	if err != nil {
		return registry.CityScheduleChoiceSet{}, fmt.Errorf("assess device schedule choices: %w", err)
	}
	projected, err := registry.ProjectCityScheduleChoices(assessment)
	if err != nil {
		return registry.CityScheduleChoiceSet{}, fmt.Errorf("project device schedule choices: %w", err)
	}
	return projected, nil
}

func (manager *DeviceSetupManager) RequestScheduleChoice(
	ctx context.Context,
	principal DevicePrincipal,
	command DeviceScheduleChoiceCommand,
) (DeviceRegistryBindingRequest, error) {
	if validateDevicePrincipal(principal) != nil || !validRegistryIdentifier(command.CityID) ||
		!validRegistryIdentifier(command.ChoiceID) || !validIdentifier(command.InteractionID) ||
		!validSetupDate(command.Date) {
		return DeviceRegistryBindingRequest{}, ErrInvalidDeviceSetupRequest
	}
	if manager == nil || manager.registry == nil || manager.repository == nil {
		return DeviceRegistryBindingRequest{}, ErrDeviceSetupUnavailable
	}
	choices, err := manager.ScheduleChoices(ctx, principal, command.CityID, command.Date)
	if err != nil {
		return DeviceRegistryBindingRequest{}, err
	}
	if choices.RevisionState != registry.RevisionStateStaged {
		return DeviceRegistryBindingRequest{}, ErrDeviceSetupNotRequestable
	}
	var selected *registry.CityScheduleChoice
	for index := range choices.Choices {
		if choices.Choices[index].ID == command.ChoiceID {
			selected = &choices.Choices[index]
			break
		}
	}
	if selected == nil || !selected.Selectable || selected.Executable ||
		selected.BlockedReason != registry.OptionEligible {
		return DeviceRegistryBindingRequest{}, ErrDeviceSetupNotRequestable
	}
	selectionDigest := sha256.Sum256([]byte(strings.Join([]string{
		"namaz-time/device-registry-binding/v1", choices.Revision.ID,
		choices.Revision.ContentSHA256, principal.DeviceID, principal.Mosque.ID,
		command.CityID, selected.PolicyID, selected.ID, command.Date, string(selected.Tier),
	}, "\x00")))
	selectionSHA256 := hex.EncodeToString(selectionDigest[:])
	requestIDHash := sha256.Sum256([]byte(strings.Join([]string{
		"namaz-time/device-registry-binding-request/v1", principal.DeviceID, command.InteractionID,
	}, "\x00")))
	auditIDHash := sha256.Sum256([]byte(strings.Join([]string{
		"namaz-time/device-registry-binding-audit/v1", principal.DeviceID, command.InteractionID,
	}, "\x00")))
	request := DeviceRegistryBindingRequest{
		ID:         "device-binding-request-" + hex.EncodeToString(requestIDHash[:]),
		RevisionID: choices.Revision.ID, CityID: command.CityID,
		PolicyID: selected.PolicyID, ChoiceID: selected.ID,
		MosqueID: principal.Mosque.ID, DeviceID: principal.DeviceID, Date: command.Date,
		Tier: selected.Tier, Status: RegistryBindingPendingReview,
		SelectionSHA256: selectionSHA256, Origin: DeviceBindingOriginLocalTVOperator,
		InteractionID: command.InteractionID, RequestedAt: manager.now().UTC(),
		AuditID: hex.EncodeToString(auditIDHash[:]),
	}
	stored, err := manager.repository.CreateDeviceRegistryBindingRequest(ctx, request)
	if err != nil {
		if errors.Is(err, ErrDeviceUnauthorized) || errors.Is(err, ErrDeviceSetupConflict) ||
			errors.Is(err, ErrDeviceSetupNotRequestable) {
			return DeviceRegistryBindingRequest{}, err
		}
		return DeviceRegistryBindingRequest{}, fmt.Errorf("persist device schedule choice: %w", err)
	}
	if !validDeviceRegistryBindingResult(stored, request) {
		return DeviceRegistryBindingRequest{}, errors.New("persist device schedule choice: repository returned invalid result")
	}
	return stored, nil
}

func validDeviceRegistryBindingResult(result, expected DeviceRegistryBindingRequest) bool {
	return result.ID == expected.ID && result.RevisionID == expected.RevisionID &&
		result.CityID == expected.CityID && result.PolicyID == expected.PolicyID &&
		result.ChoiceID == expected.ChoiceID && result.MosqueID == expected.MosqueID &&
		result.DeviceID == expected.DeviceID && result.Date == expected.Date &&
		result.Tier == expected.Tier && result.Status == RegistryBindingPendingReview &&
		result.SelectionSHA256 == expected.SelectionSHA256 &&
		result.Origin == DeviceBindingOriginLocalTVOperator &&
		result.InteractionID == expected.InteractionID && result.RequestedAt == expected.RequestedAt
}

func validSetupQuery(query string) bool {
	return query != "" && strings.TrimSpace(query) == query && len([]rune(query)) <= 200 &&
		!strings.ContainsAny(query, "\x00\r\n")
}

func validSetupDate(value string) bool {
	parsed, err := time.Parse(time.DateOnly, value)
	return err == nil && parsed.Format(time.DateOnly) == value
}

func validRegistryIdentifier(value string) bool {
	return len(value) >= 1 && len(value) <= 160 && strings.TrimSpace(value) == value &&
		!strings.ContainsRune(value, '\x00')
}
