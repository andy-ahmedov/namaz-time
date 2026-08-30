package registry

import (
	"fmt"
	"sort"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

type AssessmentStatus string

const (
	AssessmentResolved    AssessmentStatus = "resolved"
	AssessmentAmbiguous   AssessmentStatus = "ambiguous"
	AssessmentStale       AssessmentStatus = "stale"
	AssessmentUnavailable AssessmentStatus = "unavailable"
)

type AssessmentReason string

const (
	AssessmentReasonResolved            AssessmentReason = "resolved"
	AssessmentReasonSameTierAmbiguous   AssessmentReason = "same_tier_ambiguous"
	AssessmentReasonStaleSource         AssessmentReason = "stale_source"
	AssessmentReasonSourceUnavailable   AssessmentReason = "source_unavailable"
	AssessmentReasonSourceUnapproved    AssessmentReason = "source_not_approved"
	AssessmentReasonScheduleUnavailable AssessmentReason = "schedule_unavailable"
	AssessmentReasonPolicyOutOfRange    AssessmentReason = "policy_out_of_range"
	AssessmentReasonNoPolicy            AssessmentReason = "no_policy"
)

type OptionBlockedReason string

const (
	OptionEligible            OptionBlockedReason = "eligible"
	OptionLowerPrecedence     OptionBlockedReason = "lower_precedence"
	OptionPolicyOutOfRange    OptionBlockedReason = "policy_out_of_range"
	OptionSourceResearchOnly  OptionBlockedReason = "source_research_only"
	OptionSourceStale         OptionBlockedReason = "source_stale"
	OptionSourceNotFresh      OptionBlockedReason = "source_not_fresh_for_date"
	OptionSourceUnavailable   OptionBlockedReason = "source_unavailable"
	OptionScheduleUnavailable OptionBlockedReason = "schedule_unavailable_for_date"
)

type PolicyOption struct {
	Tier               ResolutionTier             `json:"tier"`
	Selectable         bool                       `json:"selectable"`
	BlockedReason      OptionBlockedReason        `json:"blocked_reason"`
	Scope              domain.GeographicScope     `json:"scope"`
	Authorities        []domain.PrayerAuthority   `json:"authorities"`
	Source             domain.PrayerSource        `json:"source"`
	Policy             domain.PrayerPolicy        `json:"policy"`
	TimeTable          *domain.TimeTable          `json:"timetable,omitempty"`
	CalculationProfile *domain.CalculationProfile `json:"calculation_profile,omitempty"`
	SourceOverrides    []domain.SourceOverride    `json:"source_overrides"`
}

type PolicyAssessment struct {
	Status  AssessmentStatus `json:"status"`
	Reason  AssessmentReason `json:"reason"`
	City    domain.City      `json:"city"`
	Region  domain.Region    `json:"region"`
	Date    string           `json:"date"`
	Options []PolicyOption   `json:"options"`
}

func AssessDataset(dataset Dataset, request ResolveRequest) (PolicyAssessment, error) {
	active, err := New(dataset)
	if err != nil {
		return PolicyAssessment{}, fmt.Errorf("assess registry dataset: %w", err)
	}
	return active.Assess(request)
}

func (r Registry) Assess(request ResolveRequest) (PolicyAssessment, error) {
	if request.CityID == "" || request.MosqueID == "" {
		return PolicyAssessment{}, ErrInvalidResolveRequest
	}
	if parsed, err := time.Parse(time.DateOnly, request.Date); err != nil || parsed.Format(time.DateOnly) != request.Date {
		return PolicyAssessment{}, ErrInvalidResolveRequest
	}
	city, ok := findByID(r.cities, request.CityID, func(item domain.City) string { return item.ID })
	if !ok {
		return PolicyAssessment{}, ErrPolicyUnavailable
	}
	region, ok := findByID(r.regions, city.RegionID, func(item domain.Region) string { return item.ID })
	if !ok {
		return PolicyAssessment{}, ErrPolicyUnavailable
	}
	assessment := PolicyAssessment{
		City: cloneCity(city), Region: region, Date: request.Date,
		Options: []PolicyOption{},
	}
	for _, policy := range r.policies {
		if !contains(policy.MosqueIDs, request.MosqueID) {
			continue
		}
		scope, exists := findByID(r.scopes, policy.GeographicScopeID, func(item domain.GeographicScope) string { return item.ID })
		if !exists {
			continue
		}
		tier, matches := assessmentTier(policy, scope, city, region)
		if !matches {
			continue
		}
		assessment.Options = append(assessment.Options, r.assessPolicyOption(policy, scope, tier, request, city))
	}
	sort.Slice(assessment.Options, func(i, j int) bool {
		left, right := assessment.Options[i], assessment.Options[j]
		if resolutionTierRank(left.Tier) != resolutionTierRank(right.Tier) {
			return resolutionTierRank(left.Tier) < resolutionTierRank(right.Tier)
		}
		return left.Policy.ID < right.Policy.ID
	})
	assessment.finalize()
	return assessment, nil
}

func (r Registry) assessPolicyOption(policy domain.PrayerPolicy, scope domain.GeographicScope, tier ResolutionTier, request ResolveRequest, city domain.City) PolicyOption {
	source, _ := findByID(r.sources, policy.SourceID, func(item domain.PrayerSource) string { return item.ID })
	option := PolicyOption{
		Tier: tier, Selectable: false, BlockedReason: OptionEligible, Scope: scope,
		Source: cloneSource(source), Policy: clonePolicy(policy), SourceOverrides: []domain.SourceOverride{},
	}
	for _, authorityID := range policy.AuthorityIDs {
		authority, _ := findByID(r.authorities, authorityID, func(item domain.PrayerAuthority) string { return item.ID })
		option.Authorities = append(option.Authorities, authority)
	}
	if !dateInRange(request.Date, policy.Effective) {
		option.BlockedReason = OptionPolicyOutOfRange
		return option
	}
	if blocked := sourceBlockedReason(source, request.Date); blocked != OptionEligible {
		option.BlockedReason = blocked
		return option
	}
	switch policy.Kind {
	case domain.PrayerPolicyTimeTable:
		timetable, found := findByID(r.timeTables, policy.TimeTableID, func(item domain.TimeTable) string { return item.ID })
		if !found || (timetable.MosqueID != "" && timetable.MosqueID != request.MosqueID) ||
			timetable.Timezone != city.Timezone || !dateInRange(request.Date, timetable.Effective) {
			option.BlockedReason = OptionScheduleUnavailable
			return option
		}
		cloned := cloneTimeTable(timetable)
		option.TimeTable = &cloned
		for _, overrideID := range timetable.SourceOverrideIDs {
			override, found := findByID(r.sourceOverrides, overrideID, func(item domain.SourceOverride) string { return item.ID })
			if !found {
				option.BlockedReason = OptionScheduleUnavailable
				return option
			}
			option.SourceOverrides = append(option.SourceOverrides, cloneSourceOverride(override))
			componentSourceIDs := []string{override.BaseSourceID}
			if dateInRange(request.Date, override.Effective) {
				componentSourceIDs = append(componentSourceIDs, override.OverrideSourceID)
			}
			for _, sourceID := range componentSourceIDs {
				component, exists := findByID(r.sources, sourceID, func(item domain.PrayerSource) string { return item.ID })
				if !exists {
					option.BlockedReason = OptionScheduleUnavailable
					return option
				}
				if blocked := sourceBlockedReason(component, request.Date); blocked != OptionEligible {
					option.BlockedReason = blocked
					return option
				}
			}
		}
	case domain.PrayerPolicyCalculationProfile:
		profile, found := findByID(r.calculationProfiles, policy.CalculationProfileID, func(item domain.CalculationProfile) string { return item.ID })
		if !found || !dateInRange(request.Date, profile.Effective) {
			option.BlockedReason = OptionScheduleUnavailable
			return option
		}
		option.CalculationProfile = &profile
	default:
		option.BlockedReason = OptionScheduleUnavailable
		return option
	}
	option.Selectable = true
	return option
}

func (assessment *PolicyAssessment) finalize() {
	bestRank := int(^uint(0) >> 1)
	bestCount := 0
	for _, option := range assessment.Options {
		if !option.Selectable {
			continue
		}
		rank := resolutionTierRank(option.Tier)
		if rank < bestRank {
			bestRank = rank
			bestCount = 1
		} else if rank == bestRank {
			bestCount++
		}
	}
	if bestCount > 0 {
		for index := range assessment.Options {
			if assessment.Options[index].Selectable && resolutionTierRank(assessment.Options[index].Tier) > bestRank {
				assessment.Options[index].Selectable = false
				assessment.Options[index].BlockedReason = OptionLowerPrecedence
			}
		}
		if bestCount > 1 {
			assessment.Status = AssessmentAmbiguous
			assessment.Reason = AssessmentReasonSameTierAmbiguous
		} else {
			assessment.Status = AssessmentResolved
			assessment.Reason = AssessmentReasonResolved
		}
		return
	}
	assessment.Status = AssessmentUnavailable
	assessment.Reason = AssessmentReasonNoPolicy
	for _, option := range assessment.Options {
		switch option.BlockedReason {
		case OptionSourceStale, OptionSourceNotFresh:
			assessment.Status = AssessmentStale
			assessment.Reason = AssessmentReasonStaleSource
			return
		case OptionSourceUnavailable:
			assessment.Reason = AssessmentReasonSourceUnavailable
		case OptionSourceResearchOnly:
			if assessment.Reason == AssessmentReasonNoPolicy {
				assessment.Reason = AssessmentReasonSourceUnapproved
			}
		case OptionScheduleUnavailable:
			if assessment.Reason == AssessmentReasonNoPolicy {
				assessment.Reason = AssessmentReasonScheduleUnavailable
			}
		case OptionPolicyOutOfRange:
			if assessment.Reason == AssessmentReasonNoPolicy {
				assessment.Reason = AssessmentReasonPolicyOutOfRange
			}
		}
	}
}

func sourceBlockedReason(source domain.PrayerSource, date string) OptionBlockedReason {
	switch source.Status {
	case domain.PrayerSourceApproved:
		if source.FreshThrough == "" || source.FreshThrough < date {
			return OptionSourceNotFresh
		}
		return OptionEligible
	case domain.PrayerSourceStale:
		return OptionSourceStale
	case domain.PrayerSourceUnavailable:
		return OptionSourceUnavailable
	default:
		return OptionSourceResearchOnly
	}
}

func assessmentTier(policy domain.PrayerPolicy, scope domain.GeographicScope, city domain.City, region domain.Region) (ResolutionTier, bool) {
	for _, tier := range []ResolutionTier{ResolutionExactCityTimetable, ResolutionRegionalTimetable, ResolutionRegionalCalculation} {
		if scopeMatchesTier(scope, policy.Kind, tier, city, region) {
			return tier, true
		}
	}
	if city.FallbackPolicyID == policy.ID && scopeMatchesTier(scope, policy.Kind, ResolutionExplicitFallback, city, region) {
		return ResolutionExplicitFallback, true
	}
	return "", false
}

func resolutionTierRank(tier ResolutionTier) int {
	switch tier {
	case ResolutionExactCityTimetable:
		return 0
	case ResolutionRegionalTimetable:
		return 1
	case ResolutionRegionalCalculation:
		return 2
	case ResolutionExplicitFallback:
		return 3
	default:
		return 4
	}
}
