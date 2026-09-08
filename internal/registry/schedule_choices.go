package registry

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

type CityScheduleChoiceStatus string

const (
	CityScheduleChoicesAvailable   CityScheduleChoiceStatus = "available"
	CityScheduleChoicesUnavailable CityScheduleChoiceStatus = "unavailable"
)

// CityScheduleChoice is a read-only setup projection of an existing assessed
// policy option. It is not a policy, source, approval, publication, or active
// mosque binding of its own.
type CityScheduleChoice struct {
	ID                   string                      `json:"choice_id"`
	DisplayLabel         string                      `json:"display_label"`
	AuthorityLabel       string                      `json:"authority_label"`
	Tier                 ResolutionTier              `json:"tier"`
	Selectable           bool                        `json:"selectable"`
	Executable           bool                        `json:"executable"`
	BlockedReason        OptionBlockedReason         `json:"blocked_reason"`
	PolicyID             string                      `json:"policy_id"`
	PolicyKind           domain.PrayerPolicyKind     `json:"policy_kind"`
	ApprovalID           string                      `json:"approval_id,omitempty"`
	Qualification        *domain.SourceQualification `json:"qualification,omitempty"`
	Effective            domain.DateRange            `json:"effective"`
	Scope                domain.GeographicScope      `json:"scope"`
	Authorities          []domain.PrayerAuthority    `json:"authorities"`
	Source               domain.PrayerSource         `json:"source"`
	TimeTableID          string                      `json:"timetable_id,omitempty"`
	CalculationProfileID string                      `json:"calculation_profile_id,omitempty"`
	TimeTable            *domain.TimeTable           `json:"timetable,omitempty"`
	CalculationProfile   *domain.CalculationProfile  `json:"calculation_profile,omitempty"`
	SourceOverrides      []domain.SourceOverride     `json:"source_overrides"`
}

// CityScheduleChoiceSet separates discovery of each independent authority's
// eligible most-specific choices from automatic resolution. SelectionRequired never permits
// insertion or display order to choose an authority.
type CityScheduleChoiceSet struct {
	Revision                  RevisionRecord           `json:"revision"`
	RevisionState             RevisionState            `json:"revision_state"`
	Status                    CityScheduleChoiceStatus `json:"status"`
	AutomaticResolutionStatus AssessmentStatus         `json:"automatic_resolution_status"`
	AutomaticResolutionReason AssessmentReason         `json:"automatic_resolution_reason"`
	SelectionRequired         bool                     `json:"selection_required"`
	City                      domain.City              `json:"city"`
	Region                    domain.Region            `json:"region"`
	Date                      string                   `json:"date"`
	Choices                   []CityScheduleChoice     `json:"choices"`
}

// ProjectCityScheduleChoices derives a lossless choice-discovery view from
// PolicyAssessment, the same truth used by the fail-closed resolver and T039.
// It omits blocked options and less-specific options of the same authority;
// independent publishers remain selectable regardless of their relative tiers.
// Omitted options remain in the assessment for operator explainability.
func ProjectCityScheduleChoices(assessment RevisionPolicyAssessment) (CityScheduleChoiceSet, error) {
	if assessment.Revision.ID == "" ||
		(assessment.State != RevisionStateStaged && assessment.State != RevisionStateActive) ||
		assessment.Result.City.ID == "" || assessment.Result.Region.ID == "" ||
		assessment.Result.City.RegionID != assessment.Result.Region.ID {
		return CityScheduleChoiceSet{}, fmt.Errorf("%w: incomplete schedule-choice assessment identity", ErrRevisionInvalid)
	}
	parsedDate, err := time.Parse(time.DateOnly, assessment.Result.Date)
	if err != nil || parsedDate.Format(time.DateOnly) != assessment.Result.Date {
		return CityScheduleChoiceSet{}, fmt.Errorf("%w: invalid schedule-choice local date", ErrRevisionInvalid)
	}

	options := append([]PolicyOption(nil), assessment.Result.Options...)
	sort.Slice(options, func(i, j int) bool {
		leftRank := resolutionTierRank(options[i].Tier)
		rightRank := resolutionTierRank(options[j].Tier)
		if leftRank != rightRank {
			return leftRank < rightRank
		}
		return options[i].Policy.ID < options[j].Policy.ID
	})

	projected := CityScheduleChoiceSet{
		Revision:                  assessment.Revision,
		RevisionState:             assessment.State,
		Status:                    CityScheduleChoicesUnavailable,
		AutomaticResolutionStatus: assessment.Result.Status,
		AutomaticResolutionReason: assessment.Result.Reason,
		City:                      cloneCity(assessment.Result.City),
		Region:                    assessment.Result.Region,
		Date:                      assessment.Result.Date,
		Choices:                   []CityScheduleChoice{},
	}
	for _, option := range options {
		if !option.Selectable {
			continue
		}
		if option.BlockedReason != OptionEligible || option.Policy.ID == "" || option.Scope.ID == "" ||
			option.Source.ID == "" || len(option.Authorities) == 0 {
			return CityScheduleChoiceSet{}, fmt.Errorf("%w: malformed selectable policy option", ErrRevisionInvalid)
		}
		choice, err := projectCityScheduleChoice(assessment.Result.City, option)
		if err != nil {
			return CityScheduleChoiceSet{}, err
		}
		// Active revision admission verifies each legacy approval or public
		// qualification independently. Another eligible choice does not undo
		// that admission; SelectionRequired still forbids automatic resolution.
		choice.Executable = assessment.State == RevisionStateActive
		projected.Choices = append(projected.Choices, choice)
	}

	switch len(projected.Choices) {
	case 0:
		if assessment.Result.Status == AssessmentResolved || assessment.Result.Status == AssessmentAmbiguous {
			return CityScheduleChoiceSet{}, fmt.Errorf("%w: automatic resolution has no selectable choices", ErrRevisionInvalid)
		}
	case 1:
		if assessment.Result.Status != AssessmentResolved {
			return CityScheduleChoiceSet{}, fmt.Errorf("%w: one selectable choice is not resolved", ErrRevisionInvalid)
		}
		projected.Status = CityScheduleChoicesAvailable
	default:
		if assessment.Result.Status != AssessmentAmbiguous {
			return CityScheduleChoiceSet{}, fmt.Errorf("%w: multiple selectable choices are not marked ambiguous", ErrRevisionInvalid)
		}
		projected.Status = CityScheduleChoicesAvailable
		projected.SelectionRequired = true
	}
	return projected, nil
}

func projectCityScheduleChoice(city domain.City, option PolicyOption) (CityScheduleChoice, error) {
	authorities := append([]domain.PrayerAuthority(nil), option.Authorities...)
	sort.Slice(authorities, func(i, j int) bool { return authorities[i].ID < authorities[j].ID })
	authorityNames := make([]string, 0, len(authorities))
	for _, authority := range authorities {
		if authority.ID == "" || strings.TrimSpace(authority.Name) == "" {
			return CityScheduleChoice{}, fmt.Errorf("%w: schedule choice has unnamed authority", ErrRevisionInvalid)
		}
		authorityNames = append(authorityNames, authority.Name)
	}
	authorityLabel := strings.Join(authorityNames, " / ")
	choice := CityScheduleChoice{
		ID:           stableCityScheduleChoiceID(city.ID, option.Policy.ID),
		DisplayLabel: city.Name + " (" + authorityLabel + ")", AuthorityLabel: authorityLabel,
		Tier: option.Tier, Selectable: true, BlockedReason: OptionEligible,
		PolicyID: option.Policy.ID, PolicyKind: option.Policy.Kind, ApprovalID: option.Policy.ApprovalID,
		Effective: option.Policy.Effective, Scope: option.Scope, Authorities: authorities,
		Source: cloneSource(option.Source), TimeTableID: option.Policy.TimeTableID,
		CalculationProfileID: option.Policy.CalculationProfileID,
		SourceOverrides:      make([]domain.SourceOverride, 0, len(option.SourceOverrides)),
	}
	if option.Policy.QualificationID != "" {
		q := option.Qualification
		if q == nil || q.ID != option.Policy.QualificationID || q.ID != option.Source.QualificationID || q.Validate() != nil || option.Policy.ApprovalID != "" {
			return CityScheduleChoice{}, fmt.Errorf("%w: qualified choice lacks exact proof", ErrRevisionInvalid)
		}
		cloned := cloneQualification(*q)
		choice.Qualification = &cloned
	} else if option.Qualification != nil {
		return CityScheduleChoice{}, fmt.Errorf("%w: legacy choice contains a public qualification", ErrRevisionInvalid)
	}
	if option.TimeTable != nil {
		cloned := cloneTimeTable(*option.TimeTable)
		choice.TimeTable = &cloned
	}
	if option.CalculationProfile != nil {
		cloned := *option.CalculationProfile
		choice.CalculationProfile = &cloned
	}
	for _, override := range option.SourceOverrides {
		choice.SourceOverrides = append(choice.SourceOverrides, cloneSourceOverride(override))
	}
	return choice, nil
}

func stableCityScheduleChoiceID(cityID, policyID string) string {
	digest := sha256.Sum256([]byte("namaztime-city-schedule-choice/v1\x00" + cityID + "\x00" + policyID))
	return "schedule-choice-" + hex.EncodeToString(digest[:])
}
