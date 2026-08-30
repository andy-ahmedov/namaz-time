package devices

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

func TestDeviceSetupManagerDiscoversEveryConfiguredChoiceAndRecordsExplicitProposal(t *testing.T) {
	t.Parallel()

	const (
		mosqueID   = "mosque-device-setup-0001"
		deviceID   = "device-setup-display-0001"
		cityID     = "city-device-setup-0001"
		revisionID = "revision-device-setup-0001"
	)
	assessment := syntheticDeviceSetupAssessment(mosqueID, cityID, revisionID, 8)
	registryBackend := &recordingDeviceSetupRegistry{assessment: assessment}
	repository := &recordingDeviceSetupRepository{}
	manager, err := NewDeviceSetupManager(DeviceSetupManagerConfig{
		Registry:         registryBackend,
		Repository:       repository,
		SetupRevisionIDs: map[string]string{mosqueID: revisionID},
		Now:              func() time.Time { return time.Date(2026, 8, 30, 12, 0, 0, 0, time.UTC) },
	})
	if err != nil {
		t.Fatalf("NewDeviceSetupManager() error = %v", err)
	}
	principal := DevicePrincipal{
		DeviceID: deviceID,
		Mosque: MosqueIdentity{
			ID: mosqueID, Name: "Synthetic setup mosque", Timezone: "Europe/Moscow",
		},
	}

	choices, err := manager.ScheduleChoices(t.Context(), principal, cityID, "2026-08-30")
	if err != nil {
		t.Fatalf("ScheduleChoices() error = %v", err)
	}
	if len(choices.Choices) != 8 || !choices.SelectionRequired || choices.Revision.ID != revisionID {
		t.Fatalf("ScheduleChoices() = %#v", choices)
	}
	if registryBackend.assessmentID != revisionID || registryBackend.request != (registry.ResolveRequest{
		CityID: cityID, MosqueID: mosqueID, Date: "2026-08-30",
	}) {
		t.Fatalf("registry assessment = %q %#v", registryBackend.assessmentID, registryBackend.request)
	}
	for _, choice := range choices.Choices {
		if !choice.Selectable || choice.Executable {
			t.Fatalf("staged choice unexpectedly executable = %#v", choice)
		}
	}

	selected := choices.Choices[6]
	created, err := manager.RequestScheduleChoice(t.Context(), principal, DeviceScheduleChoiceCommand{
		CityID: cityID, ChoiceID: selected.ID, Date: "2026-08-30",
		InteractionID: "interaction-tv-selection-0001",
	})
	if err != nil {
		t.Fatalf("RequestScheduleChoice() error = %v", err)
	}
	if created.Status != RegistryBindingPendingReview || created.DeviceID != deviceID ||
		created.MosqueID != mosqueID || created.RevisionID != revisionID ||
		created.CityID != cityID || created.PolicyID != selected.PolicyID ||
		created.ChoiceID != selected.ID || created.Origin != DeviceBindingOriginLocalTVOperator ||
		created.InteractionID != "interaction-tv-selection-0001" ||
		created.RequestedAt != time.Date(2026, 8, 30, 12, 0, 0, 0, time.UTC) {
		t.Fatalf("RequestScheduleChoice() = %#v", created)
	}
	if repository.creates != 1 || repository.last.DeviceID != deviceID ||
		repository.last.SelectionSHA256 == "" {
		t.Fatalf("persisted proposal = %#v; creates=%d", repository.last, repository.creates)
	}

	retried, err := manager.RequestScheduleChoice(t.Context(), principal, DeviceScheduleChoiceCommand{
		CityID: cityID, ChoiceID: selected.ID, Date: "2026-08-30",
		InteractionID: "interaction-tv-selection-0001",
	})
	if err != nil || retried != created || repository.creates != 2 {
		t.Fatalf("idempotent RequestScheduleChoice() = %#v, %v; creates=%d", retried, err, repository.creates)
	}
}

func TestDeviceSetupManagerFailsClosedForImplicitOrActiveSelection(t *testing.T) {
	t.Parallel()

	const (
		mosqueID = "mosque-device-setup-0002"
		deviceID = "device-setup-display-0002"
		cityID   = "city-device-setup-0002"
	)
	assessment := syntheticDeviceSetupAssessment(mosqueID, cityID, "revision-active-setup-0002", 1)
	assessment.State = registry.RevisionStateActive
	registryBackend := &recordingDeviceSetupRegistry{assessment: assessment}
	manager, err := NewDeviceSetupManager(DeviceSetupManagerConfig{
		Registry:   registryBackend,
		Repository: &recordingDeviceSetupRepository{},
	})
	if err != nil {
		t.Fatalf("NewDeviceSetupManager() error = %v", err)
	}
	principal := DevicePrincipal{
		DeviceID: deviceID,
		Mosque:   MosqueIdentity{ID: mosqueID, Name: "Synthetic setup mosque", Timezone: "Europe/Moscow"},
	}
	choices, err := manager.ScheduleChoices(t.Context(), principal, cityID, "2026-08-30")
	if err != nil || len(choices.Choices) != 1 || !choices.Choices[0].Executable ||
		registryBackend.assessmentID != "" {
		t.Fatalf("active ScheduleChoices() = %#v, %v; revision=%q", choices, err, registryBackend.assessmentID)
	}

	_, err = manager.RequestScheduleChoice(t.Context(), principal, DeviceScheduleChoiceCommand{
		CityID: cityID, Date: "2026-08-30", InteractionID: "interaction-no-choice-0002",
	})
	if !errors.Is(err, ErrInvalidDeviceSetupRequest) {
		t.Fatalf("implicit first choice error = %v", err)
	}
	_, err = manager.RequestScheduleChoice(t.Context(), principal, DeviceScheduleChoiceCommand{
		CityID: cityID, ChoiceID: choices.Choices[0].ID, Date: "2026-08-30",
		InteractionID: "interaction-active-choice-0002",
	})
	if !errors.Is(err, ErrDeviceSetupNotRequestable) {
		t.Fatalf("active choice request error = %v", err)
	}
}

func TestDeviceSetupManagerSearchUsesCanonicalCatalogWithoutMosqueInput(t *testing.T) {
	t.Parallel()

	registryBackend := &recordingDeviceSetupRegistry{cities: []registry.CitySearchResult{
		{City: domain.City{ID: "city-kirov-001", Name: "Киров", RegionID: "region-kirov-001"}, Region: domain.Region{ID: "region-kirov-001", FederalSubjectCode: "RU-KIR"}},
		{City: domain.City{ID: "city-kirov-002", Name: "Киров", RegionID: "region-kirov-002"}, Region: domain.Region{ID: "region-kirov-002", FederalSubjectCode: "RU-KLU"}},
	}}
	manager, err := NewDeviceSetupManager(DeviceSetupManagerConfig{Registry: registryBackend})
	if err != nil {
		t.Fatalf("NewDeviceSetupManager() error = %v", err)
	}
	principal := DevicePrincipal{
		DeviceID: "device-setup-display-0003",
		Mosque:   MosqueIdentity{ID: "mosque-device-setup-0003", Name: "Synthetic setup mosque", Timezone: "Europe/Moscow"},
	}
	cities, err := manager.SearchCities(t.Context(), principal, "Киров")
	if err != nil || len(cities) != 2 || cities[0].City.ID == cities[1].City.ID {
		t.Fatalf("SearchCities() = %#v, %v", cities, err)
	}
	if registryBackend.query != "Киров" {
		t.Fatalf("SearchCities query = %q", registryBackend.query)
	}
}

type recordingDeviceSetupRegistry struct {
	cities       []registry.CitySearchResult
	query        string
	assessment   registry.RevisionPolicyAssessment
	assessmentID string
	request      registry.ResolveRequest
}

func (backend *recordingDeviceSetupRegistry) SearchCities(_ context.Context, query string) ([]registry.CitySearchResult, error) {
	backend.query = query
	return append([]registry.CitySearchResult(nil), backend.cities...), nil
}

func (backend *recordingDeviceSetupRegistry) SearchRevisionCities(
	_ context.Context,
	revisionID string,
	query string,
) ([]registry.CitySearchResult, error) {
	backend.assessmentID = revisionID
	backend.query = query
	return append([]registry.CitySearchResult(nil), backend.cities...), nil
}

func (backend *recordingDeviceSetupRegistry) AssessRevision(
	_ context.Context,
	revisionID string,
	request registry.ResolveRequest,
) (registry.RevisionPolicyAssessment, error) {
	backend.assessmentID = revisionID
	backend.request = request
	return backend.assessment, nil
}

type recordingDeviceSetupRepository struct {
	creates int
	last    DeviceRegistryBindingRequest
}

func (repository *recordingDeviceSetupRepository) CreateDeviceRegistryBindingRequest(
	_ context.Context,
	request DeviceRegistryBindingRequest,
) (DeviceRegistryBindingRequest, error) {
	repository.creates++
	if repository.last.InteractionID == request.InteractionID {
		if repository.last.SelectionSHA256 != request.SelectionSHA256 {
			return DeviceRegistryBindingRequest{}, ErrDeviceSetupConflict
		}
		return repository.last, nil
	}
	repository.last = request
	return request, nil
}

func syntheticDeviceSetupAssessment(
	mosqueID string,
	cityID string,
	revisionID string,
	choiceCount int,
) registry.RevisionPolicyAssessment {
	city := domain.City{
		ID: cityID, Name: "Синтетический город", CountryCode: "RU",
		RegionID: "region-device-setup", SettlementType: "PPL", Latitude: 55, Longitude: 49,
		Timezone: "Europe/Moscow", GeographicSourceID: "synthetic:device-setup",
		GeographicRevision: "fixture-v1", GeographicLicense: "synthetic-test-only",
	}
	region := domain.Region{
		ID: "region-device-setup", Name: "Синтетический субъект",
		CountryCode: "RU", FederalSubjectCode: "RU-XX",
	}
	options := make([]registry.PolicyOption, 0, choiceCount)
	for index := range choiceCount {
		suffix := fmt.Sprintf("%02d", index)
		authorityID := "authority-device-setup-" + suffix
		sourceID := "source-device-setup-" + suffix
		policyID := "policy-device-setup-" + suffix
		timetableID := "timetable-device-setup-" + suffix
		scope := domain.GeographicScope{
			ID: "scope-device-setup", Kind: domain.GeographicScopeCity,
			CityID: cityID, RegionID: region.ID, Description: "synthetic device setup fixture",
		}
		timetable := domain.TimeTable{
			ID: timetableID, SourceID: sourceID, GeographicScopeID: scope.ID, MosqueID: mosqueID,
			Timezone: city.Timezone, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			PublishedSnapshotID: "snapshot-device-setup-" + suffix,
		}
		options = append(options, registry.PolicyOption{
			Tier: registry.ResolutionExactCityTimetable, Selectable: true,
			BlockedReason: registry.OptionEligible, Scope: scope,
			Authorities: []domain.PrayerAuthority{{
				ID: authorityID, Name: "Synthetic authority " + suffix, EvidenceLabel: "PROPOSAL",
			}},
			Source: domain.PrayerSource{
				ID: sourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{authorityID},
				GeographicScopeID: scope.ID, CanonicalURL: "https://example.invalid/" + suffix,
				Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
			},
			Policy: domain.PrayerPolicy{
				ID: policyID, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: scope.ID,
				AuthorityIDs: []string{authorityID}, SourceID: sourceID, TimeTableID: timetableID,
				MosqueIDs: []string{mosqueID}, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
				ApprovalID: "approval-device-setup-" + suffix,
			},
			TimeTable: &timetable, SourceOverrides: []domain.SourceOverride{},
		})
	}
	status := registry.AssessmentUnavailable
	reason := registry.AssessmentReasonNoPolicy
	if choiceCount == 1 {
		status = registry.AssessmentResolved
		reason = registry.AssessmentReasonResolved
	} else if choiceCount > 1 {
		status = registry.AssessmentAmbiguous
		reason = registry.AssessmentReasonSameTierAmbiguous
	}
	return registry.RevisionPolicyAssessment{
		Revision: registry.RevisionRecord{ID: revisionID, ContentSHA256: strings.Repeat("d", 64)},
		State:    registry.RevisionStateStaged,
		Result: registry.PolicyAssessment{
			Status: status, Reason: reason, City: city, Region: region,
			Date: "2026-08-30", Options: options,
		},
	}
}
