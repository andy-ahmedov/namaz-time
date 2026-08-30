package registry_test

import (
	"errors"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

func TestRegistryResolveUsesFailClosedPrecedence(t *testing.T) {
	tests := []struct {
		name             string
		removePolicyIDs  []string
		removeFallbackID bool
		wantTier         registry.ResolutionTier
		wantErr          error
	}{
		{name: "exact city timetable", wantTier: registry.ResolutionExactCityTimetable},
		{name: "regional official timetable", removePolicyIDs: []string{"city-table-policy"}, wantTier: registry.ResolutionRegionalTimetable},
		{name: "approved regional calculation", removePolicyIDs: []string{"city-table-policy", "region-table-policy"}, wantTier: registry.ResolutionRegionalCalculation},
		{name: "explicit fallback", removePolicyIDs: []string{"city-table-policy", "region-table-policy", "region-calculation-policy"}, wantTier: registry.ResolutionExplicitFallback},
		{name: "unavailable without configured fallback", removePolicyIDs: []string{"city-table-policy", "region-table-policy", "region-calculation-policy"}, removeFallbackID: true, wantErr: registry.ErrPolicyUnavailable},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			dataset := resolutionDataset()
			dataset.Policies = removePolicies(dataset.Policies, test.removePolicyIDs...)
			if test.removeFallbackID {
				dataset.Cities[0].FallbackPolicyID = ""
			}
			catalog, err := registry.New(dataset)
			if err != nil {
				t.Fatalf("New() error = %v", err)
			}

			got, err := catalog.Resolve(registry.ResolveRequest{CityID: "city-1", MosqueID: "mosque-1", Date: "2026-06-01"})
			if !errors.Is(err, test.wantErr) {
				t.Fatalf("Resolve() error = %v, want %v", err, test.wantErr)
			}
			if err == nil && got.Tier != test.wantTier {
				t.Fatalf("Resolve() tier = %q, want %q", got.Tier, test.wantTier)
			}
		})
	}
}

func TestRegistryResolveRejectsAmbiguousPoliciesAtSameTier(t *testing.T) {
	dataset := resolutionDataset()
	duplicate := dataset.Policies[0]
	duplicate.ID = "another-city-table-policy"
	dataset.Policies = append(dataset.Policies, duplicate)
	catalog, err := registry.New(dataset)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}

	_, err = catalog.Resolve(registry.ResolveRequest{CityID: "city-1", MosqueID: "mosque-1", Date: "2026-06-01"})
	if !errors.Is(err, registry.ErrPolicyAmbiguous) {
		t.Fatalf("Resolve() error = %v, want ambiguous policy failure", err)
	}
}

func TestRegistryNewRejectsInvalidDataset(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*registry.Dataset)
	}{
		{
			name: "duplicate city identity",
			mutate: func(dataset *registry.Dataset) {
				dataset.Cities = append(dataset.Cities, dataset.Cities[0])
			},
		},
		{
			name: "numeric offset instead of IANA timezone",
			mutate: func(dataset *registry.Dataset) {
				dataset.Cities[0].Timezone = "+04:00"
			},
		},
		{
			name: "source references unknown authority",
			mutate: func(dataset *registry.Dataset) {
				dataset.Sources[0].AuthorityIDs = []string{"missing-authority"}
			},
		},
		{
			name: "source uses unsupported provider kind",
			mutate: func(dataset *registry.Dataset) {
				dataset.Sources[0].Kind = domain.ProviderKind("aggregator")
			},
		},
		{
			name: "policy lacks approval",
			mutate: func(dataset *registry.Dataset) {
				dataset.Policies[0].ApprovalID = ""
			},
		},
		{
			name: "invalid effective range",
			mutate: func(dataset *registry.Dataset) {
				dataset.Policies[0].Effective = domain.DateRange{From: "2026-12-31", To: "2026-01-01"}
			},
		},
		{
			name: "timetable mosque is outside policy binding",
			mutate: func(dataset *registry.Dataset) {
				dataset.TimeTables[0].MosqueID = "other-mosque"
			},
		},
		{
			name: "override references unknown source",
			mutate: func(dataset *registry.Dataset) {
				dataset.SourceOverrides = []domain.SourceOverride{{
					ID: "override-1", BaseSourceID: "city-source", OverrideSourceID: "missing-source",
					Effective: domain.DateRange{From: "2026-06-01", To: "2026-06-30"}, ApprovalID: "approval-5",
				}}
			},
		},
		{
			name: "timetable override crosses geographic scope",
			mutate: func(dataset *registry.Dataset) {
				dataset.SourceOverrides = []domain.SourceOverride{{
					ID: "override-1", BaseSourceID: "region-table-source", OverrideSourceID: "region-calculation-source",
					Effective: domain.DateRange{From: "2026-06-01", To: "2026-06-30"}, AppliedFields: []string{"dhuhr"}, ApprovalID: "approval-5",
				}}
				dataset.TimeTables[0].SourceOverrideIDs = []string{"override-1"}
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			dataset := resolutionDataset()
			test.mutate(&dataset)
			_, err := registry.New(dataset)
			if !errors.Is(err, registry.ErrInvalidRegistry) {
				t.Fatalf("New() error = %v, want invalid registry", err)
			}
		})
	}
}

func TestRegistryResolveRejectsMalformedLocalDate(t *testing.T) {
	catalog, err := registry.New(resolutionDataset())
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}

	_, err = catalog.Resolve(registry.ResolveRequest{CityID: "city-1", MosqueID: "mosque-1", Date: "2026-02-30"})
	if !errors.Is(err, registry.ErrInvalidResolveRequest) {
		t.Fatalf("Resolve() error = %v, want invalid request", err)
	}
}

func TestRegistryOwnsValidatedDatasetCopy(t *testing.T) {
	dataset := resolutionDataset()
	catalog, err := registry.New(dataset)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}
	dataset.Cities[0].Name = "mutated"
	dataset.Policies[0].MosqueIDs[0] = "other-mosque"

	if got := catalog.SearchCities("City"); len(got) != 1 || got[0].ID != "city-1" {
		t.Fatalf("SearchCities() after caller mutation = %#v, want immutable validated city", got)
	}
	if _, err := catalog.Resolve(registry.ResolveRequest{CityID: "city-1", MosqueID: "mosque-1", Date: "2026-06-01"}); err != nil {
		t.Fatalf("Resolve() after caller mutation error = %v, want immutable validated policy", err)
	}
}

func TestRegistryDoesNotExposeMutableInternalSlices(t *testing.T) {
	catalog, err := registry.New(resolutionDataset())
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}
	cities := catalog.SearchCities("City alias")
	cities[0].Aliases[0] = "corrupted"
	resolution, err := catalog.Resolve(registry.ResolveRequest{
		CityID: "city-1", MosqueID: "mosque-1", Date: "2026-08-30",
	})
	if err != nil {
		t.Fatalf("first Resolve() error = %v", err)
	}
	resolution.Policy.MosqueIDs[0] = "other-mosque"

	if got := catalog.SearchCities("City alias"); len(got) != 1 {
		t.Fatalf("SearchCities() after returned value mutation = %#v, want intact alias", got)
	}
	got, err := catalog.Resolve(registry.ResolveRequest{
		CityID: "city-1", MosqueID: "mosque-1", Date: "2026-08-30",
	})
	if err != nil || got.Policy.MosqueIDs[0] != "mosque-1" {
		t.Fatalf("second Resolve() = %#v, %v; want immutable policy", got, err)
	}
}

func resolutionDataset() registry.Dataset {
	effective := domain.DateRange{From: "2026-01-01", To: "2026-12-31"}
	return registry.Dataset{
		Cities: []domain.City{{
			ID: "city-1", Name: "City", Aliases: []string{"City alias"}, CountryCode: "RU", RegionID: "region-1",
			Latitude: 55, Longitude: 49, Timezone: "Europe/Moscow", GeographicSource: "https://example.test/city",
			FallbackPolicyID: "fallback-policy",
		}},
		Regions: []domain.Region{{ID: "region-1", Name: "Region", CountryCode: "RU", FederalSubjectCode: "RU-XX"}},
		Scopes: []domain.GeographicScope{
			{ID: "city-scope", Kind: domain.GeographicScopeCity, CityID: "city-1", RegionID: "region-1"},
			{ID: "region-scope", Kind: domain.GeographicScopeRegion, RegionID: "region-1"},
		},
		Authorities: []domain.PrayerAuthority{{ID: "authority-1", Name: "Authority", EvidenceLabel: "CONFIRMED_PUBLIC"}},
		Sources: []domain.PrayerSource{
			{ID: "city-source", Kind: domain.ProviderKindOfficialFile, AuthorityIDs: []string{"authority-1"}, GeographicScopeID: "city-scope"},
			{ID: "region-table-source", Kind: domain.ProviderKindOfficialFile, AuthorityIDs: []string{"authority-1"}, GeographicScopeID: "region-scope"},
			{ID: "region-calculation-source", Kind: domain.ProviderKindCalculationProfile, AuthorityIDs: []string{"authority-1"}, GeographicScopeID: "region-scope"},
			{ID: "fallback-source", Kind: domain.ProviderKindCalculationProfile, AuthorityIDs: []string{"authority-1"}, GeographicScopeID: "city-scope"},
		},
		Policies: []domain.PrayerPolicy{
			{ID: "city-table-policy", Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: "city-scope", AuthorityIDs: []string{"authority-1"}, SourceID: "city-source", TimeTableID: "city-table", MosqueIDs: []string{"mosque-1"}, Effective: effective, ApprovalID: "approval-1"},
			{ID: "region-table-policy", Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: "region-scope", AuthorityIDs: []string{"authority-1"}, SourceID: "region-table-source", TimeTableID: "region-table", MosqueIDs: []string{"mosque-1"}, Effective: effective, ApprovalID: "approval-2"},
			{ID: "region-calculation-policy", Kind: domain.PrayerPolicyCalculationProfile, GeographicScopeID: "region-scope", AuthorityIDs: []string{"authority-1"}, SourceID: "region-calculation-source", CalculationProfileID: "region-profile", MosqueIDs: []string{"mosque-1"}, Effective: effective, ApprovalID: "approval-3"},
			{ID: "fallback-policy", Kind: domain.PrayerPolicyCalculationProfile, GeographicScopeID: "city-scope", AuthorityIDs: []string{"authority-1"}, SourceID: "fallback-source", CalculationProfileID: "fallback-profile", MosqueIDs: []string{"mosque-1"}, Effective: effective, ApprovalID: "approval-4"},
		},
		CalculationProfiles: []domain.CalculationProfile{
			{ID: "region-profile", SourceID: "region-calculation-source", GeographicScopeID: "region-scope", Version: "v1", Effective: effective, ApprovalID: "approval-3"},
			{ID: "fallback-profile", SourceID: "fallback-source", GeographicScopeID: "city-scope", Version: "v1", Effective: effective, ApprovalID: "approval-4"},
		},
		TimeTables: []domain.TimeTable{
			{ID: "city-table", SourceID: "city-source", GeographicScopeID: "city-scope", MosqueID: "mosque-1", Timezone: "Europe/Moscow", Effective: effective, PublishedSnapshotID: "snapshot-city"},
			{ID: "region-table", SourceID: "region-table-source", GeographicScopeID: "region-scope", Timezone: "Europe/Moscow", Effective: effective, PublishedSnapshotID: "snapshot-region"},
		},
	}
}

func removePolicies(policies []domain.PrayerPolicy, ids ...string) []domain.PrayerPolicy {
	removed := make(map[string]bool, len(ids))
	for _, id := range ids {
		removed[id] = true
	}
	kept := make([]domain.PrayerPolicy, 0, len(policies))
	for _, policy := range policies {
		if !removed[policy.ID] {
			kept = append(kept, policy)
		}
	}
	return kept
}
