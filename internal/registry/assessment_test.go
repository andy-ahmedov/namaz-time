package registry

import (
	"errors"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestIndependentRegionalAuthorityIsNotHiddenByAnotherCityAuthority(t *testing.T) {
	dataset := executableDataset()
	other := domain.PrayerAuthority{ID: "authority-independent", Name: "Independent synthetic authority", EvidenceLabel: "CONFIRMED_PUBLIC"}
	dataset.Authorities = append(dataset.Authorities, other)
	scope := domain.GeographicScope{ID: "scope-independent-region", Kind: domain.GeographicScopeRegion, RegionID: testRegionID}
	dataset.Scopes = append(dataset.Scopes, scope)
	source := dataset.Sources[0]
	source.ID, source.GeographicScopeID, source.AuthorityIDs = "source-independent", scope.ID, []string{other.ID}
	dataset.Sources = append(dataset.Sources, source)
	table := dataset.TimeTables[0]
	table.ID, table.SourceID, table.GeographicScopeID = "table-independent", source.ID, scope.ID
	dataset.TimeTables = append(dataset.TimeTables, table)
	policy := dataset.Policies[0]
	policy.ID, policy.SourceID, policy.GeographicScopeID = "policy-independent", source.ID, scope.ID
	policy.TimeTableID, policy.AuthorityIDs = table.ID, []string{other.ID}
	dataset.Policies = append(dataset.Policies, policy)
	request := ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"}
	active, err := New(dataset)
	if err != nil {
		t.Fatal(err)
	}
	assessment, err := active.Assess(request)
	if err != nil || assessment.Status != AssessmentAmbiguous || assessment.Reason != "multiple_authorities" {
		t.Fatalf("independent authorities must require selection: status=%s reason=%s error=%v", assessment.Status, assessment.Reason, err)
	}
	for _, option := range assessment.Options {
		if !option.Selectable || option.BlockedReason != OptionEligible {
			t.Fatalf("independent authority was suppressed: %#v", option)
		}
	}
	if _, err := active.Resolve(request); !errors.Is(err, ErrPolicyAmbiguous) {
		t.Fatalf("automatic resolver chose a religious authority: %v", err)
	}
	choices, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-independent-synthetic"}, State: RevisionStateStaged, Result: assessment,
	})
	if err != nil || len(choices.Choices) != 2 || !choices.SelectionRequired {
		t.Fatalf("independent choice projection: %#v, %v", choices, err)
	}
}

func TestResolveDoesNotTreatAbsentSourceStatusAsApproval(t *testing.T) {
	dataset := executableDataset()
	dataset.Sources[0].Status = ""
	dataset.Sources[0].FreshThrough = ""
	active, err := New(dataset)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := active.Resolve(ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"}); !errors.Is(err, ErrPolicyUnavailable) {
		t.Fatalf("unknown source implicitly became approved: %v", err)
	}
}

func TestAssessDatasetExplainsAmbiguousSelectablePolicies(t *testing.T) {
	dataset := executableDataset()
	parallelAuthority := dataset.Authorities[0]
	parallelAuthority.ID = "authority-test-parallel"
	parallelAuthority.Name = "Parallel test authority"
	dataset.Authorities = append(dataset.Authorities, parallelAuthority)
	parallelSource := dataset.Sources[0]
	parallelSource.ID = "source-test-parallel"
	parallelSource.AuthorityIDs = []string{parallelAuthority.ID}
	dataset.Sources = append(dataset.Sources, parallelSource)
	parallelTable := dataset.TimeTables[0]
	parallelTable.ID = "timetable-test-parallel"
	parallelTable.SourceID = parallelSource.ID
	dataset.TimeTables = append(dataset.TimeTables, parallelTable)
	duplicate := dataset.Policies[0]
	duplicate.ID = "policy-test-parallel-authority"
	duplicate.AuthorityIDs = []string{parallelAuthority.ID}
	duplicate.SourceID = parallelSource.ID
	duplicate.TimeTableID = parallelTable.ID
	dataset.Policies = append(dataset.Policies, duplicate)

	assessment, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil {
		t.Fatalf("AssessDataset() error = %v", err)
	}
	if assessment.Status != AssessmentAmbiguous || assessment.Reason != AssessmentReasonSameTierAmbiguous || len(assessment.Options) != 2 {
		t.Fatalf("ambiguous assessment = %#v", assessment)
	}
	for _, option := range assessment.Options {
		if !option.Selectable || option.BlockedReason != OptionEligible || option.Tier != ResolutionExactCityTimetable {
			t.Fatalf("ambiguous option = %#v", option)
		}
	}
	if assessment.Options[0].Authorities[0].ID == assessment.Options[1].Authorities[0].ID {
		t.Fatalf("parallel authority identities collapsed: %#v", assessment.Options)
	}
}

func TestAssessDatasetExplainsBlockedSourceAndScheduleStates(t *testing.T) {
	tests := []struct {
		name       string
		mutate     func(*Dataset)
		wantStatus AssessmentStatus
		wantReason AssessmentReason
		wantBlock  OptionBlockedReason
	}{
		{
			name: "stale source status",
			mutate: func(dataset *Dataset) {
				dataset.Sources[0].Status = domain.PrayerSourceStale
			},
			wantStatus: AssessmentStale,
			wantReason: AssessmentReasonStaleSource,
			wantBlock:  OptionSourceStale,
		},
		{
			name: "approved source freshness expired",
			mutate: func(dataset *Dataset) {
				dataset.Sources[0].FreshThrough = "2026-08-29"
			},
			wantStatus: AssessmentStale,
			wantReason: AssessmentReasonStaleSource,
			wantBlock:  OptionSourceNotFresh,
		},
		{
			name: "research-only source",
			mutate: func(dataset *Dataset) {
				dataset.Sources[0].Status = domain.PrayerSourceResearchOnly
				dataset.Sources[0].FreshThrough = ""
			},
			wantStatus: AssessmentUnavailable,
			wantReason: AssessmentReasonSourceUnapproved,
			wantBlock:  OptionSourceResearchOnly,
		},
		{
			name: "source unavailable",
			mutate: func(dataset *Dataset) {
				dataset.Sources[0].Status = domain.PrayerSourceUnavailable
			},
			wantStatus: AssessmentUnavailable,
			wantReason: AssessmentReasonSourceUnavailable,
			wantBlock:  OptionSourceUnavailable,
		},
		{
			name: "policy outside effective range",
			mutate: func(dataset *Dataset) {
				dataset.Policies[0].Effective = domain.DateRange{From: "2026-01-01", To: "2026-06-30"}
			},
			wantStatus: AssessmentUnavailable,
			wantReason: AssessmentReasonPolicyOutOfRange,
			wantBlock:  OptionPolicyOutOfRange,
		},
		{
			name: "schedule unavailable for date",
			mutate: func(dataset *Dataset) {
				dataset.TimeTables[0].Effective = domain.DateRange{From: "2026-01-01", To: "2026-06-30"}
			},
			wantStatus: AssessmentUnavailable,
			wantReason: AssessmentReasonScheduleUnavailable,
			wantBlock:  OptionScheduleUnavailable,
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			dataset := executableDataset()
			test.mutate(&dataset)
			assessment, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
			if err != nil {
				t.Fatalf("AssessDataset() error = %v", err)
			}
			if assessment.Status != test.wantStatus || assessment.Reason != test.wantReason || len(assessment.Options) != 1 || assessment.Options[0].Selectable || assessment.Options[0].BlockedReason != test.wantBlock {
				t.Fatalf("blocked assessment = %#v", assessment)
			}
		})
	}
}

func TestAssessDatasetMarksEligibleLowerTierAsNonSelectable(t *testing.T) {
	dataset := executableDataset()
	regionalScope := domain.GeographicScope{
		ID: "scope-test-regional", Kind: domain.GeographicScopeRegion,
		RegionID: testRegionID, Description: "regional test scope",
	}
	dataset.Scopes = append(dataset.Scopes, regionalScope)
	regionalSource := dataset.Sources[0]
	regionalSource.ID = "source-test-regional"
	regionalSource.GeographicScopeID = regionalScope.ID
	dataset.Sources = append(dataset.Sources, regionalSource)
	regionalTable := dataset.TimeTables[0]
	regionalTable.ID = "timetable-test-regional"
	regionalTable.SourceID = regionalSource.ID
	regionalTable.GeographicScopeID = regionalScope.ID
	regionalTable.MosqueID = ""
	dataset.TimeTables = append(dataset.TimeTables, regionalTable)
	regionalPolicy := dataset.Policies[0]
	regionalPolicy.ID = "policy-test-regional"
	regionalPolicy.GeographicScopeID = regionalScope.ID
	regionalPolicy.SourceID = regionalSource.ID
	regionalPolicy.TimeTableID = regionalTable.ID
	dataset.Policies = append(dataset.Policies, regionalPolicy)

	assessment, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil {
		t.Fatalf("AssessDataset() error = %v", err)
	}
	if len(assessment.Options) != 2 || assessment.Status != AssessmentResolved ||
		assessment.Options[0].Tier != ResolutionExactCityTimetable || !assessment.Options[0].Selectable ||
		assessment.Options[1].Tier != ResolutionRegionalTimetable || assessment.Options[1].Selectable ||
		assessment.Options[1].BlockedReason != OptionLowerPrecedence {
		t.Fatalf("precedence assessment = %#v", assessment)
	}
}

func TestAssessDatasetChecksOverrideSourceOnlyInsideOverrideRange(t *testing.T) {
	dataset := executableDataset()
	overrideSource := dataset.Sources[0]
	overrideSource.ID = "source-test-august-override"
	overrideSource.Status = domain.PrayerSourceStale
	dataset.Sources = append(dataset.Sources, overrideSource)
	override := domain.SourceOverride{
		ID: "override-test-august", BaseSourceID: dataset.Sources[0].ID,
		OverrideSourceID: overrideSource.ID, Effective: domain.DateRange{From: "2026-08-01", To: "2026-08-31"},
		AppliedFields: []string{"dhuhr"}, ApprovalID: testApprovalID,
	}
	dataset.SourceOverrides = []domain.SourceOverride{override}
	dataset.TimeTables[0].SourceOverrideIDs = []string{override.ID}

	outside, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-07-31"})
	if err != nil || outside.Status != AssessmentResolved || !outside.Options[0].Selectable {
		t.Fatalf("outside override range assessment = %#v, %v", outside, err)
	}
	inside, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil || inside.Status != AssessmentStale || inside.Options[0].BlockedReason != OptionSourceStale {
		t.Fatalf("inside override range assessment = %#v, %v", inside, err)
	}
}

func TestAssessDatasetExplainsMissingPolicyWithoutFallback(t *testing.T) {
	dataset := executableDataset()
	dataset.Policies = nil
	dataset.TimeTables = nil

	assessment, err := AssessDataset(dataset, ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-08-30"})
	if err != nil {
		t.Fatalf("AssessDataset() error = %v", err)
	}
	if assessment.Status != AssessmentUnavailable || assessment.Reason != AssessmentReasonNoPolicy || len(assessment.Options) != 0 {
		t.Fatalf("missing policy assessment = %#v", assessment)
	}
}
