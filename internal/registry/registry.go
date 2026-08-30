// Package registry resolves curated city search results independently from
// prayer authority and publication data.
package registry

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

type Registry struct {
	cities              []domain.City
	regions             []domain.Region
	scopes              []domain.GeographicScope
	authorities         []domain.PrayerAuthority
	sources             []domain.PrayerSource
	policies            []domain.PrayerPolicy
	calculationProfiles []domain.CalculationProfile
	timeTables          []domain.TimeTable
	sourceOverrides     []domain.SourceOverride
}

type Dataset struct {
	Cities              []domain.City
	Regions             []domain.Region
	Scopes              []domain.GeographicScope
	Authorities         []domain.PrayerAuthority
	Sources             []domain.PrayerSource
	Policies            []domain.PrayerPolicy
	CalculationProfiles []domain.CalculationProfile
	TimeTables          []domain.TimeTable
	SourceOverrides     []domain.SourceOverride
}

type ResolveRequest struct {
	CityID   string
	MosqueID string
	Date     string
}

type ResolutionTier string

const (
	ResolutionExactCityTimetable  ResolutionTier = "exact_city_timetable"
	ResolutionRegionalTimetable   ResolutionTier = "regional_official_timetable"
	ResolutionRegionalCalculation ResolutionTier = "approved_regional_calculation_profile"
	ResolutionExplicitFallback    ResolutionTier = "explicitly_configured_fallback"
)

type Resolution struct {
	Tier               ResolutionTier
	City               domain.City
	Region             domain.Region
	Scope              domain.GeographicScope
	Authorities        []domain.PrayerAuthority
	Source             domain.PrayerSource
	Policy             domain.PrayerPolicy
	TimeTable          domain.TimeTable
	CalculationProfile domain.CalculationProfile
	SourceOverrides    []domain.SourceOverride
}

var (
	ErrPolicyUnavailable     = errors.New("prayer policy unavailable")
	ErrPolicyAmbiguous       = errors.New("prayer policy ambiguous")
	ErrInvalidRegistry       = errors.New("invalid city/source registry")
	ErrInvalidResolveRequest = errors.New("invalid city/source resolve request")
)

func New(dataset Dataset) (Registry, error) {
	dataset = cloneDataset(dataset)
	if err := validateDataset(dataset); err != nil {
		return Registry{}, fmt.Errorf("%w: %v", ErrInvalidRegistry, err)
	}
	return Registry{
		cities:              dataset.Cities,
		regions:             dataset.Regions,
		scopes:              dataset.Scopes,
		authorities:         dataset.Authorities,
		sources:             dataset.Sources,
		policies:            dataset.Policies,
		calculationProfiles: dataset.CalculationProfiles,
		timeTables:          dataset.TimeTables,
		sourceOverrides:     dataset.SourceOverrides,
	}, nil
}

func cloneDataset(dataset Dataset) Dataset {
	cloned := Dataset{
		Cities:              append([]domain.City(nil), dataset.Cities...),
		Regions:             append([]domain.Region(nil), dataset.Regions...),
		Scopes:              append([]domain.GeographicScope(nil), dataset.Scopes...),
		Authorities:         append([]domain.PrayerAuthority(nil), dataset.Authorities...),
		Sources:             append([]domain.PrayerSource(nil), dataset.Sources...),
		Policies:            append([]domain.PrayerPolicy(nil), dataset.Policies...),
		CalculationProfiles: append([]domain.CalculationProfile(nil), dataset.CalculationProfiles...),
		TimeTables:          append([]domain.TimeTable(nil), dataset.TimeTables...),
		SourceOverrides:     append([]domain.SourceOverride(nil), dataset.SourceOverrides...),
	}
	for index := range cloned.Cities {
		cloned.Cities[index].Aliases = append([]string(nil), cloned.Cities[index].Aliases...)
	}
	for index := range cloned.Policies {
		cloned.Policies[index].AuthorityIDs = append([]string(nil), cloned.Policies[index].AuthorityIDs...)
		cloned.Policies[index].MosqueIDs = append([]string(nil), cloned.Policies[index].MosqueIDs...)
	}
	for index := range cloned.Sources {
		cloned.Sources[index].AuthorityIDs = append([]string(nil), cloned.Sources[index].AuthorityIDs...)
	}
	for index := range cloned.TimeTables {
		cloned.TimeTables[index].SourceOverrideIDs = append([]string(nil), cloned.TimeTables[index].SourceOverrideIDs...)
	}
	for index := range cloned.SourceOverrides {
		cloned.SourceOverrides[index].AppliedFields = append([]string(nil), cloned.SourceOverrides[index].AppliedFields...)
	}
	return cloned
}

// NewPilotRegistry returns the first curated city entry. The coordinates are
// from OpenStreetMap relation 2049867, not from a competitor dataset.
func NewPilotRegistry() Registry {
	const (
		cityID      = "ru-uly-ulyanovsk"
		regionID    = "ru-uly"
		scopeID     = "scope-ulyanovsk-city"
		authorityID = "rdum-ulyanovsk-oblast"
		sourceID    = "effective-ulyanovsk-2026-v1"
		timeTableID = "timetable-ulyanovsk-second-cathedral-2026"
		mosqueID    = "second-cathedral-mosque-ulyanovsk"
	)
	dataset := Dataset{
		Cities: []domain.City{{
			ID:                "ru-uly-ulyanovsk",
			Name:              "Ульяновск",
			Aliases:           []string{"Ulyanovsk"},
			CountryCode:       "RU",
			RegionID:          "ru-uly",
			Latitude:          54.3150278,
			Longitude:         48.4033730,
			Timezone:          "Europe/Ulyanovsk",
			GeographicSource:  "https://www.openstreetmap.org/relation/2049867",
			GeographicLicense: "OpenStreetMap contributors, ODbL 1.0",
		}},
		Regions: []domain.Region{{
			ID: regionID, Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY",
		}},
		Scopes: []domain.GeographicScope{{
			ID: scopeID, Kind: domain.GeographicScopeCity, CityID: cityID, RegionID: regionID,
			Description: "Second Cathedral Mosque of Ulyanovsk; Gregorian 2026",
		}},
		Authorities: []domain.PrayerAuthority{{
			ID: authorityID, Name: "Региональное духовное управление мусульман Ульяновской области в составе ЦДУМ России",
			Branch: "Годовой календарь времени намазов для г. Ульяновска на 2026 год", Website: "https://rdumul.ru/", EvidenceLabel: "CONFIRMED_PUBLIC",
		}, {
			ID: "rdumul-attributed-publisher-unconfirmed", Name: "rdumul.ru-attributed schedule publisher (legal name unconfirmed)",
			Branch: "Ulyanovsk schedule shown in the supplied image", Website: "https://rdumul.ru/", EvidenceLabel: "UNKNOWN",
		}},
		Sources: []domain.PrayerSource{
			{ID: sourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{authorityID, "rdumul-attributed-publisher-unconfirmed"}, GeographicScopeID: scopeID},
			{ID: "official-rdumul-ulyanovsk-2026", Kind: domain.ProviderKindOfficialFile, AuthorityIDs: []string{authorityID}, GeographicScopeID: scopeID, CanonicalURL: "https://rdumul.ru/"},
			{ID: "manual-rdumul-ulsk-2026-08", Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{"rdumul-attributed-publisher-unconfirmed"}, GeographicScopeID: scopeID},
		},
		Policies: []domain.PrayerPolicy{{
			ID: "policy-ulyanovsk-second-cathedral-2026", Kind: domain.PrayerPolicyTimeTable,
			GeographicScopeID: scopeID, AuthorityIDs: []string{authorityID, "rdumul-attributed-publisher-unconfirmed"}, SourceID: sourceID, TimeTableID: timeTableID,
			MosqueIDs: []string{mosqueID}, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			ApprovalID: "approval-second-cathedral-mosque-ulyanovsk-2026-002",
		}},
		TimeTables: []domain.TimeTable{{
			ID: timeTableID, SourceID: sourceID, GeographicScopeID: scopeID, MosqueID: mosqueID,
			Timezone: "Europe/Ulyanovsk", Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			PublishedSnapshotID: "ulyanovsk-second-cathedral-2026-pilot-local-v2",
			SourceOverrideIDs:   []string{"source-override-ulyanovsk-2026-08"},
		}},
		SourceOverrides: []domain.SourceOverride{{
			ID: "source-override-ulyanovsk-2026-08", BaseSourceID: "official-rdumul-ulyanovsk-2026",
			OverrideSourceID: "manual-rdumul-ulsk-2026-08", Effective: domain.DateRange{From: "2026-08-01", To: "2026-08-31"},
			AppliedFields: []string{"fajr", "sunrise", "zenith", "dhuhr", "dhuhr_congregation", "asr", "maghrib", "isha", "hijri_day", "hijri_month", "hijri_year", "flags"},
			ApprovalID:    "approval-second-cathedral-mosque-ulyanovsk-2026-002",
		}},
	}
	registry, err := New(dataset)
	if err != nil {
		panic(err)
	}
	return registry
}

func validateDataset(dataset Dataset) error {
	cities, err := uniqueIndex("city", dataset.Cities, func(item domain.City) string { return item.ID })
	if err != nil {
		return err
	}
	regions, err := uniqueIndex("region", dataset.Regions, func(item domain.Region) string { return item.ID })
	if err != nil {
		return err
	}
	scopes, err := uniqueIndex("geographic scope", dataset.Scopes, func(item domain.GeographicScope) string { return item.ID })
	if err != nil {
		return err
	}
	authorities, err := uniqueIndex("prayer authority", dataset.Authorities, func(item domain.PrayerAuthority) string { return item.ID })
	if err != nil {
		return err
	}
	sources, err := uniqueIndex("prayer source", dataset.Sources, func(item domain.PrayerSource) string { return item.ID })
	if err != nil {
		return err
	}
	policies, err := uniqueIndex("prayer policy", dataset.Policies, func(item domain.PrayerPolicy) string { return item.ID })
	if err != nil {
		return err
	}
	profiles, err := uniqueIndex("calculation profile", dataset.CalculationProfiles, func(item domain.CalculationProfile) string { return item.ID })
	if err != nil {
		return err
	}
	timetables, err := uniqueIndex("timetable", dataset.TimeTables, func(item domain.TimeTable) string { return item.ID })
	if err != nil {
		return err
	}
	overrides, err := uniqueIndex("source override", dataset.SourceOverrides, func(item domain.SourceOverride) string { return item.ID })
	if err != nil {
		return err
	}

	for _, city := range dataset.Cities {
		region, ok := regions[city.RegionID]
		if city.Name == "" || city.CountryCode == "" || !ok || region.CountryCode != city.CountryCode {
			return fmt.Errorf("city %q has incomplete or inconsistent region identity", city.ID)
		}
		if city.Latitude < -90 || city.Latitude > 90 || city.Longitude < -180 || city.Longitude > 180 {
			return fmt.Errorf("city %q has invalid coordinates", city.ID)
		}
		if _, err := time.LoadLocation(city.Timezone); err != nil {
			return fmt.Errorf("city %q has invalid IANA timezone %q", city.ID, city.Timezone)
		}
		if city.GeographicSource == "" {
			return fmt.Errorf("city %q lacks geographic provenance", city.ID)
		}
		if city.FallbackPolicyID != "" {
			if _, ok := policies[city.FallbackPolicyID]; !ok {
				return fmt.Errorf("city %q references unknown fallback policy %q", city.ID, city.FallbackPolicyID)
			}
		}
	}
	for _, region := range dataset.Regions {
		if region.Name == "" || region.CountryCode == "" || region.FederalSubjectCode == "" {
			return fmt.Errorf("region %q has incomplete identity", region.ID)
		}
	}
	for _, scope := range dataset.Scopes {
		switch scope.Kind {
		case domain.GeographicScopeCity:
			city, cityOK := cities[scope.CityID]
			if !cityOK || scope.RegionID == "" || city.RegionID != scope.RegionID {
				return fmt.Errorf("city scope %q has inconsistent geography", scope.ID)
			}
		case domain.GeographicScopeRegion:
			if _, ok := regions[scope.RegionID]; !ok || scope.CityID != "" {
				return fmt.Errorf("region scope %q has inconsistent geography", scope.ID)
			}
		default:
			return fmt.Errorf("scope %q has unsupported kind %q", scope.ID, scope.Kind)
		}
	}
	for _, authority := range dataset.Authorities {
		if authority.Name == "" || !allowedEvidenceLabel(authority.EvidenceLabel) {
			return fmt.Errorf("prayer authority %q lacks a name or valid evidence label", authority.ID)
		}
	}
	for _, source := range dataset.Sources {
		if !allowedProviderKind(source.Kind) {
			return fmt.Errorf("source %q has unsupported provider kind %q", source.ID, source.Kind)
		}
		if err := validateAuthorityReferences("source "+source.ID, source.AuthorityIDs, authorities); err != nil {
			return err
		}
		if _, ok := scopes[source.GeographicScopeID]; !ok {
			return fmt.Errorf("source %q references unknown scope %q", source.ID, source.GeographicScopeID)
		}
	}
	for _, policy := range dataset.Policies {
		if policy.ApprovalID == "" || len(policy.MosqueIDs) == 0 || !validDateRange(policy.Effective) {
			return fmt.Errorf("policy %q lacks approval, mosque binding, or valid effective range", policy.ID)
		}
		if err := validateAuthorityReferences("policy "+policy.ID, policy.AuthorityIDs, authorities); err != nil {
			return err
		}
		scope, scopeOK := scopes[policy.GeographicScopeID]
		source, sourceOK := sources[policy.SourceID]
		if !scopeOK || !sourceOK || !sameStrings(source.AuthorityIDs, policy.AuthorityIDs) || source.GeographicScopeID != scope.ID {
			return fmt.Errorf("policy %q has inconsistent source, authority, or scope", policy.ID)
		}
		switch policy.Kind {
		case domain.PrayerPolicyTimeTable:
			timetable, ok := timetables[policy.TimeTableID]
			if !ok || policy.CalculationProfileID != "" || timetable.SourceID != policy.SourceID || timetable.GeographicScopeID != policy.GeographicScopeID || (timetable.MosqueID != "" && !contains(policy.MosqueIDs, timetable.MosqueID)) {
				return fmt.Errorf("timetable policy %q has inconsistent timetable", policy.ID)
			}
		case domain.PrayerPolicyCalculationProfile:
			profile, ok := profiles[policy.CalculationProfileID]
			if !ok || policy.TimeTableID != "" || profile.SourceID != policy.SourceID || profile.GeographicScopeID != policy.GeographicScopeID {
				return fmt.Errorf("calculation policy %q has inconsistent profile", policy.ID)
			}
		default:
			return fmt.Errorf("policy %q has unsupported kind %q", policy.ID, policy.Kind)
		}
	}
	for _, timetable := range dataset.TimeTables {
		if _, ok := sources[timetable.SourceID]; !ok {
			return fmt.Errorf("timetable %q references unknown source", timetable.ID)
		}
		if _, ok := scopes[timetable.GeographicScopeID]; !ok || !validDateRange(timetable.Effective) || timetable.PublishedSnapshotID == "" {
			return fmt.Errorf("timetable %q has invalid scope, range, or publication", timetable.ID)
		}
		if _, err := time.LoadLocation(timetable.Timezone); err != nil {
			return fmt.Errorf("timetable %q has invalid IANA timezone %q", timetable.ID, timetable.Timezone)
		}
		for _, overrideID := range timetable.SourceOverrideIDs {
			override, ok := overrides[overrideID]
			if !ok {
				return fmt.Errorf("timetable %q references unknown source override %q", timetable.ID, overrideID)
			}
			baseSource, baseOK := sources[override.BaseSourceID]
			overrideSource, overrideOK := sources[override.OverrideSourceID]
			if !baseOK || !overrideOK || baseSource.GeographicScopeID != timetable.GeographicScopeID || overrideSource.GeographicScopeID != timetable.GeographicScopeID || override.Effective.From < timetable.Effective.From || override.Effective.To > timetable.Effective.To {
				return fmt.Errorf("timetable %q has out-of-scope source override %q", timetable.ID, overrideID)
			}
		}
	}
	for _, profile := range dataset.CalculationProfiles {
		if _, ok := sources[profile.SourceID]; !ok || profile.Version == "" || profile.ApprovalID == "" || !validDateRange(profile.Effective) {
			return fmt.Errorf("calculation profile %q lacks source, version, approval, or valid range", profile.ID)
		}
		if _, ok := scopes[profile.GeographicScopeID]; !ok {
			return fmt.Errorf("calculation profile %q references unknown scope", profile.ID)
		}
	}
	for _, override := range dataset.SourceOverrides {
		_, baseOK := sources[override.BaseSourceID]
		_, overrideOK := sources[override.OverrideSourceID]
		if !baseOK || !overrideOK || override.ApprovalID == "" || len(override.AppliedFields) == 0 || !validDateRange(override.Effective) {
			return fmt.Errorf("source override %q lacks valid sources, fields, approval, or range", override.ID)
		}
	}
	return nil
}

func allowedProviderKind(kind domain.ProviderKind) bool {
	switch kind {
	case domain.ProviderKindOfficialAPI,
		domain.ProviderKindOfficialFile,
		domain.ProviderKindOfficialHTML,
		domain.ProviderKindMosqueCalendar,
		domain.ProviderKindCalculationProfile,
		domain.ProviderKindManualImport:
		return true
	default:
		return false
	}
}

func uniqueIndex[T any](kind string, values []T, id func(T) string) (map[string]T, error) {
	result := make(map[string]T, len(values))
	for _, value := range values {
		valueID := id(value)
		if valueID == "" {
			return nil, fmt.Errorf("%s has empty ID", kind)
		}
		if _, exists := result[valueID]; exists {
			return nil, fmt.Errorf("duplicate %s ID %q", kind, valueID)
		}
		result[valueID] = value
	}
	return result, nil
}

func validDateRange(value domain.DateRange) bool {
	from, fromErr := time.Parse("2006-01-02", value.From)
	to, toErr := time.Parse("2006-01-02", value.To)
	return fromErr == nil && toErr == nil && !from.After(to)
}

func (r Registry) SearchCities(query string) []domain.City {
	normalized := normalizeSearch(query)
	if normalized == "" {
		return nil
	}
	var matches []domain.City
	for _, city := range r.cities {
		if normalizeSearch(city.Name) == normalized || containsNormalized(city.Aliases, normalized) {
			matches = append(matches, cloneCity(city))
		}
	}
	return matches
}

func (r Registry) Resolve(request ResolveRequest) (Resolution, error) {
	if request.CityID == "" || request.MosqueID == "" {
		return Resolution{}, ErrInvalidResolveRequest
	}
	if _, err := time.Parse("2006-01-02", request.Date); err != nil {
		return Resolution{}, ErrInvalidResolveRequest
	}
	city, ok := findByID(r.cities, request.CityID, func(item domain.City) string { return item.ID })
	if !ok {
		return Resolution{}, ErrPolicyUnavailable
	}
	region, ok := findByID(r.regions, city.RegionID, func(item domain.Region) string { return item.ID })
	if !ok {
		return Resolution{}, ErrPolicyUnavailable
	}
	for _, tier := range []ResolutionTier{
		ResolutionExactCityTimetable,
		ResolutionRegionalTimetable,
		ResolutionRegionalCalculation,
	} {
		var eligible []Resolution
		for _, policy := range r.policies {
			if resolution, ok := r.resolvePolicy(policy, tier, request, city, region); ok {
				eligible = append(eligible, resolution)
			}
		}
		if len(eligible) > 1 {
			return Resolution{}, ErrPolicyAmbiguous
		}
		if len(eligible) == 1 {
			return eligible[0], nil
		}
	}
	if city.FallbackPolicyID != "" {
		if policy, exists := findByID(r.policies, city.FallbackPolicyID, func(item domain.PrayerPolicy) string { return item.ID }); exists {
			if resolution, eligible := r.resolvePolicy(policy, ResolutionExplicitFallback, request, city, region); eligible {
				return resolution, nil
			}
		}
	}
	return Resolution{}, ErrPolicyUnavailable
}

func (r Registry) resolvePolicy(policy domain.PrayerPolicy, tier ResolutionTier, request ResolveRequest, city domain.City, region domain.Region) (Resolution, bool) {
	if policy.ApprovalID == "" || !contains(policy.MosqueIDs, request.MosqueID) || !dateInRange(request.Date, policy.Effective) {
		return Resolution{}, false
	}
	scope, exists := findByID(r.scopes, policy.GeographicScopeID, func(item domain.GeographicScope) string { return item.ID })
	if !exists || !scopeMatchesTier(scope, policy.Kind, tier, city, region) {
		return Resolution{}, false
	}
	source, sourceOK := findByID(r.sources, policy.SourceID, func(item domain.PrayerSource) string { return item.ID })
	if !sourceOK || !sameStrings(source.AuthorityIDs, policy.AuthorityIDs) || source.GeographicScopeID != scope.ID ||
		(source.Status != "" && source.Status != domain.PrayerSourceApproved) || (source.FreshThrough != "" && request.Date > source.FreshThrough) {
		return Resolution{}, false
	}
	authorities := make([]domain.PrayerAuthority, 0, len(policy.AuthorityIDs))
	for _, authorityID := range policy.AuthorityIDs {
		authority, found := findByID(r.authorities, authorityID, func(item domain.PrayerAuthority) string { return item.ID })
		if !found {
			return Resolution{}, false
		}
		authorities = append(authorities, authority)
	}
	resolution := Resolution{
		Tier: tier, City: cloneCity(city), Region: region, Scope: scope,
		Authorities: authorities, Source: cloneSource(source), Policy: clonePolicy(policy),
	}
	switch policy.Kind {
	case domain.PrayerPolicyTimeTable:
		timetable, found := findByID(r.timeTables, policy.TimeTableID, func(item domain.TimeTable) string { return item.ID })
		if !found || (timetable.MosqueID != "" && timetable.MosqueID != request.MosqueID) || timetable.SourceID != policy.SourceID || timetable.GeographicScopeID != scope.ID || timetable.Timezone != city.Timezone || !dateInRange(request.Date, timetable.Effective) || timetable.PublishedSnapshotID == "" {
			return Resolution{}, false
		}
		resolution.TimeTable = cloneTimeTable(timetable)
		for _, overrideID := range timetable.SourceOverrideIDs {
			override, found := findByID(r.sourceOverrides, overrideID, func(item domain.SourceOverride) string { return item.ID })
			if !found {
				return Resolution{}, false
			}
			resolution.SourceOverrides = append(resolution.SourceOverrides, cloneSourceOverride(override))
		}
	case domain.PrayerPolicyCalculationProfile:
		profile, found := findByID(r.calculationProfiles, policy.CalculationProfileID, func(item domain.CalculationProfile) string { return item.ID })
		if !found || profile.SourceID != policy.SourceID || profile.GeographicScopeID != scope.ID || profile.ApprovalID == "" || !dateInRange(request.Date, profile.Effective) {
			return Resolution{}, false
		}
		resolution.CalculationProfile = profile
	default:
		return Resolution{}, false
	}
	return resolution, true
}

func cloneCity(city domain.City) domain.City {
	city.Aliases = append([]string(nil), city.Aliases...)
	return city
}

func clonePolicy(policy domain.PrayerPolicy) domain.PrayerPolicy {
	policy.AuthorityIDs = append([]string(nil), policy.AuthorityIDs...)
	policy.MosqueIDs = append([]string(nil), policy.MosqueIDs...)
	return policy
}

func cloneSource(source domain.PrayerSource) domain.PrayerSource {
	source.AuthorityIDs = append([]string(nil), source.AuthorityIDs...)
	return source
}

func cloneTimeTable(timetable domain.TimeTable) domain.TimeTable {
	timetable.SourceOverrideIDs = append([]string(nil), timetable.SourceOverrideIDs...)
	return timetable
}

func cloneSourceOverride(override domain.SourceOverride) domain.SourceOverride {
	override.AppliedFields = append([]string(nil), override.AppliedFields...)
	return override
}

func allowedEvidenceLabel(label string) bool {
	switch label {
	case "CONFIRMED_PUBLIC", "CONFIRMED_STATIC", "CONFIRMED_RUNTIME", "INFERENCE", "PROPOSAL", "UNKNOWN":
		return true
	default:
		return false
	}
}

func validateAuthorityReferences(label string, authorityIDs []string, authorities map[string]domain.PrayerAuthority) error {
	if len(authorityIDs) == 0 {
		return fmt.Errorf("%s has no authority references", label)
	}
	seen := make(map[string]bool, len(authorityIDs))
	for _, authorityID := range authorityIDs {
		if seen[authorityID] {
			return fmt.Errorf("%s repeats authority %q", label, authorityID)
		}
		seen[authorityID] = true
		if _, ok := authorities[authorityID]; !ok {
			return fmt.Errorf("%s references unknown authority %q", label, authorityID)
		}
	}
	return nil
}

func sameStrings(left, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	counts := make(map[string]int, len(left))
	for _, value := range left {
		counts[value]++
	}
	for _, value := range right {
		counts[value]--
	}
	for _, count := range counts {
		if count != 0 {
			return false
		}
	}
	return true
}

func scopeMatchesTier(scope domain.GeographicScope, kind domain.PrayerPolicyKind, tier ResolutionTier, city domain.City, region domain.Region) bool {
	switch tier {
	case ResolutionExactCityTimetable:
		return scope.Kind == domain.GeographicScopeCity && scope.CityID == city.ID && kind == domain.PrayerPolicyTimeTable
	case ResolutionRegionalTimetable:
		return scope.Kind == domain.GeographicScopeRegion && scope.RegionID == region.ID && kind == domain.PrayerPolicyTimeTable
	case ResolutionRegionalCalculation:
		return scope.Kind == domain.GeographicScopeRegion && scope.RegionID == region.ID && kind == domain.PrayerPolicyCalculationProfile
	case ResolutionExplicitFallback:
		return (scope.CityID == city.ID || scope.RegionID == region.ID) && (kind == domain.PrayerPolicyTimeTable || kind == domain.PrayerPolicyCalculationProfile)
	default:
		return false
	}
}

func dateInRange(date string, dateRange domain.DateRange) bool {
	return dateRange.From <= date && date <= dateRange.To
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

func findByID[T any](values []T, wanted string, id func(T) string) (T, bool) {
	for _, value := range values {
		if id(value) == wanted {
			return value, true
		}
	}
	var zero T
	return zero, false
}

func containsNormalized(values []string, wanted string) bool {
	for _, value := range values {
		if normalizeSearch(value) == wanted {
			return true
		}
	}
	return false
}

func normalizeSearch(value string) string {
	return strings.ToLower(strings.Join(strings.Fields(value), " "))
}
