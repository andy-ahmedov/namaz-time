package registry

import (
	"errors"
	"reflect"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestProjectCityScheduleChoicesPreservesEveryEligibleChoice(t *testing.T) {
	tests := []struct {
		count             int
		wantStatus        CityScheduleChoiceStatus
		selectionRequired bool
		assessmentStatus  AssessmentStatus
	}{
		{count: 0, wantStatus: CityScheduleChoicesUnavailable, assessmentStatus: AssessmentUnavailable},
		{count: 1, wantStatus: CityScheduleChoicesAvailable, assessmentStatus: AssessmentResolved},
		{count: 2, wantStatus: CityScheduleChoicesAvailable, selectionRequired: true, assessmentStatus: AssessmentAmbiguous},
		{count: 3, wantStatus: CityScheduleChoicesAvailable, selectionRequired: true, assessmentStatus: AssessmentAmbiguous},
		{count: 5, wantStatus: CityScheduleChoicesAvailable, selectionRequired: true, assessmentStatus: AssessmentAmbiguous},
		{count: 8, wantStatus: CityScheduleChoicesAvailable, selectionRequired: true, assessmentStatus: AssessmentAmbiguous},
	}

	for _, test := range tests {
		t.Run(strings.Repeat("choice_", test.count+1), func(t *testing.T) {
			dataset := syntheticChoiceDataset(test.count)
			assessment := assessSyntheticChoices(t, dataset)
			projected, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
				Revision: RevisionRecord{ID: "revision-synthetic-choices-0001", ContentSHA256: strings.Repeat("a", 64)},
				State:    RevisionStateStaged,
				Result:   assessment,
			})
			if err != nil {
				t.Fatalf("ProjectCityScheduleChoices() error = %v", err)
			}
			if projected.Status != test.wantStatus || projected.SelectionRequired != test.selectionRequired ||
				projected.AutomaticResolutionStatus != test.assessmentStatus || len(projected.Choices) != test.count {
				t.Fatalf("projected choice set = %#v", projected)
			}
			seen := make(map[string]bool, len(projected.Choices))
			for _, choice := range projected.Choices {
				if !choice.Selectable || choice.BlockedReason != OptionEligible || choice.PolicyID == "" ||
					choice.Source.ID == "" || choice.Scope.ID == "" || len(choice.Authorities) != 1 ||
					choice.TimeTable == nil || choice.TimeTableID == "" || choice.CalculationProfile != nil || choice.Executable {
					t.Fatalf("incomplete selectable choice = %#v", choice)
				}
				if seen[choice.ID] {
					t.Fatalf("duplicate stable choice ID %q", choice.ID)
				}
				seen[choice.ID] = true
			}
		})
	}
}

func TestProjectCityScheduleChoicesKeepsEveryActiveChoiceExecutableWithoutResolvingAmbiguity(t *testing.T) {
	assessment := assessSyntheticChoices(t, syntheticChoiceDataset(1))
	active, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-active-choice-0001"}, State: RevisionStateActive, Result: assessment,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices(active) error = %v", err)
	}
	if len(active.Choices) != 1 || !active.Choices[0].Executable {
		t.Fatalf("active resolved choice = %#v", active)
	}

	ambiguous := assessSyntheticChoices(t, syntheticChoiceDataset(2))
	activeAmbiguous, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-active-ambiguous-0001"}, State: RevisionStateActive, Result: ambiguous,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices(active ambiguous) error = %v", err)
	}
	if !activeAmbiguous.SelectionRequired || activeAmbiguous.AutomaticResolutionStatus != AssessmentAmbiguous {
		t.Fatalf("multiple executable choices became an automatic selection = %#v", activeAmbiguous)
	}
	for _, choice := range activeAmbiguous.Choices {
		if !choice.Executable {
			t.Fatalf("another eligible choice suppressed an active approved choice = %#v", activeAmbiguous)
		}
	}
}

func TestProjectCityScheduleChoicesKeepsVerifiedLegacyDuringPublicOverlap(t *testing.T) {
	for _, reverse := range []bool{false, true} {
		name := "original_order"
		if reverse {
			name = "reversed_order"
		}
		t.Run(name, func(t *testing.T) {
			dataset, approvals, snapshots := mixedAdmissionChoiceFixture(t)
			if reverse {
				slices.Reverse(dataset.Policies)
				slices.Reverse(dataset.Sources)
				slices.Reverse(dataset.Authorities)
				slices.Reverse(dataset.TimeTables)
			}
			store := newFakeRevisionStore()
			service, err := NewPersistentService(PersistentServiceConfig{
				Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow,
			})
			if err != nil {
				t.Fatal(err)
			}
			revision := RevisionRecord{ID: "revision-synthetic-mixed-choices", SchemaVersion: 2, CatalogRevisionID: "catalog-1", CreatedBy: "synthetic-test", Reason: "independent public and mosque-approved choices"}
			if err := service.Stage(t.Context(), revision, dataset); err != nil {
				t.Fatal(err)
			}
			request := ResolveRequest{CityID: testCityID, MosqueID: testMosqueID, Date: "2026-09-08"}
			staged, err := service.AssessRevision(t.Context(), revision.ID, request)
			if err != nil {
				t.Fatal(err)
			}
			stagedChoices, err := ProjectCityScheduleChoices(staged)
			if err != nil || len(stagedChoices.Choices) != 2 || !stagedChoices.SelectionRequired {
				t.Fatalf("staged mixed choices = %+v, %v", stagedChoices, err)
			}
			for _, choice := range stagedChoices.Choices {
				if choice.Executable {
					t.Fatal("staged choice became executable without admission")
				}
			}
			if err := service.Activate(t.Context(), revision.ID, "synthetic-test", "verify both admission branches"); err != nil {
				t.Fatal(err)
			}
			if len(store.activations) != 1 || len(store.activations[0].Approvals) != 1 || len(store.activations[0].Snapshots) != 2 {
				t.Fatal("active mixed revision did not retain separate verified approval and snapshot evidence")
			}
			annual := domain.DateRange{From: "2026-01-01", To: "2026-12-31"}
			for date, _ := time.Parse(time.DateOnly, annual.From); date.Format(time.DateOnly) <= annual.To; date = date.AddDate(0, 0, 1) {
				request.Date = date.Format(time.DateOnly)
				assessment, err := service.AssessRevision(t.Context(), "", request)
				if err != nil {
					t.Fatal(err)
				}
				choices, err := ProjectCityScheduleChoices(assessment)
				if err != nil {
					t.Fatal(err)
				}
				overlap := date.Month() == time.September
				want := 1
				if overlap {
					want = 2
				}
				if len(choices.Choices) != want || choices.SelectionRequired != overlap {
					t.Fatalf("choice gap or expansion on %s: %+v", request.Date, choices)
				}
				for _, choice := range choices.Choices {
					if !choice.Selectable || !choice.Executable || choice.BlockedReason != OptionEligible || choice.Tier != ResolutionExactCityTimetable {
						t.Fatalf("eligible admitted choice %s suppressed on %s: executable=%v", choice.PolicyID, request.Date, choice.Executable)
					}
					if choice.PolicyID == testPolicyID {
						if choice.Qualification != nil || choice.ApprovalID != testApprovalID || choice.Effective != annual ||
							choice.TimeTable == nil || choice.TimeTable.MosqueID != testMosqueID || choice.TimeTable.PublishedSnapshotID != testSnapshotID ||
							!reflect.DeepEqual(choice.SourceOverrides, dataset.SourceOverrides) || len(choice.Authorities) != 2 {
							t.Fatal("legacy approval, original mosque/snapshot, annual range, authority set or override changed")
						}
					} else if choice.PolicyID != testPolicyID+"-public" || choice.Qualification == nil || choice.ApprovalID != "" ||
						!reflect.DeepEqual(*choice.Qualification, dataset.Qualifications[0]) {
						t.Fatal("public proof acquired legacy approval or changed")
					}
				}
				resolved, err := service.Resolve(t.Context(), request)
				if overlap {
					if !errors.Is(err, ErrPolicyAmbiguous) || choices.AutomaticResolutionStatus != AssessmentAmbiguous {
						t.Fatalf("overlap automatically selected an authority on %s: %v", request.Date, err)
					}
				} else if err != nil || resolved.Policy.ID != testPolicyID {
					t.Fatalf("retained annual policy changed outside overlap on %s: %v", request.Date, err)
				}
			}
			for _, date := range []string{"2026-08-31", "2026-09-01", "2026-09-30", "2026-10-01"} {
				assessment, err := service.AssessRevision(t.Context(), "", ResolveRequest{CityID: testCityID, MosqueID: "other-synthetic-mosque", Date: date})
				if err != nil {
					t.Fatal(err)
				}
				choices, err := ProjectCityScheduleChoices(assessment)
				if err != nil {
					t.Fatal(err)
				}
				for _, choice := range choices.Choices {
					if choice.Qualification == nil || choice.PolicyID != testPolicyID+"-public" {
						t.Fatal("legacy approval leaked to another mosque context")
					}
				}
			}
		})
	}
}

func TestMixedChoiceActivationStillRequiresExactLegacyApprovalAndSnapshot(t *testing.T) {
	for _, test := range []struct {
		name   string
		mutate func(*fakeApprovalVerifier, *fakeSnapshotVerifier)
	}{
		{"missing approval", func(a *fakeApprovalVerifier, _ *fakeSnapshotVerifier) { delete(a.evidence, testApprovalID) }},
		{"wrong approval mosque", func(a *fakeApprovalVerifier, _ *fakeSnapshotVerifier) {
			v := a.evidence[testApprovalID]
			v.MosqueID = "other-synthetic-mosque"
			a.evidence[testApprovalID] = v
		}},
		{"missing legacy snapshot", func(_ *fakeApprovalVerifier, s *fakeSnapshotVerifier) { delete(s.evidence, testSnapshotID) }},
		{"wrong legacy snapshot mosque", func(_ *fakeApprovalVerifier, s *fakeSnapshotVerifier) {
			v := s.evidence[testSnapshotID]
			v.MosqueID = "other-synthetic-mosque"
			s.evidence[testSnapshotID] = v
		}},
	} {
		t.Run(test.name, func(t *testing.T) {
			dataset, approvals, snapshots := mixedAdmissionChoiceFixture(t)
			test.mutate(approvals, snapshots)
			store := newFakeRevisionStore()
			service, err := NewPersistentService(PersistentServiceConfig{Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow})
			if err != nil {
				t.Fatal(err)
			}
			revision := RevisionRecord{ID: "revision-synthetic-invalid-legacy", SchemaVersion: 2, CatalogRevisionID: "catalog-1", CreatedBy: "synthetic-test", Reason: "reject incomplete mixed admission"}
			if err := service.Stage(t.Context(), revision, dataset); err != nil {
				t.Fatal(err)
			}
			if err := service.Activate(t.Context(), revision.ID, "synthetic-test", "must reject invalid legacy evidence"); err == nil || len(store.activations) != 0 {
				t.Fatal("public proof substituted for invalid legacy admission")
			}
		})
	}
}

func mixedAdmissionChoiceFixture(t *testing.T) (Dataset, *fakeApprovalVerifier, *fakeSnapshotVerifier) {
	t.Helper()
	legacy := executableDataset()
	addApprovedOverride(&legacy, testApprovalID)
	legacy.Authorities = append(legacy.Authorities, domain.PrayerAuthority{ID: "authority-synthetic-legacy-component", Name: "Synthetic retained component publisher", EvidenceLabel: "PROPOSAL"})
	legacy.Sources[0].AuthorityIDs = append(legacy.Sources[0].AuthorityIDs, legacy.Authorities[1].ID)
	legacy.Policies[0].AuthorityIDs = append(legacy.Policies[0].AuthorityIDs, legacy.Authorities[1].ID)
	public := qualifiedDataset(t)
	public.Authorities[0].ID += "-public"
	public.Sources[0].ID += "-public"
	public.Sources[0].AuthorityIDs = []string{public.Authorities[0].ID}
	public.Policies[0].ID += "-public"
	public.Policies[0].AuthorityIDs = public.Sources[0].AuthorityIDs
	public.Policies[0].SourceID = public.Sources[0].ID
	public.TimeTables[0].ID += "-public"
	public.TimeTables[0].SourceID = public.Sources[0].ID
	public.TimeTables[0].PublishedSnapshotID += "-public"
	public.Policies[0].TimeTableID = public.TimeTables[0].ID
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	public.Policies[0].Effective, public.TimeTables[0].Effective = coverage, coverage
	public.Sources[0].FreshThrough = coverage.To
	q := &public.Qualifications[0]
	q.Authority, q.SourceID, q.Coverage, q.FreshThrough, q.ValidatedDays = public.Authorities[0], public.Sources[0].ID, coverage, coverage.To, 30
	comparison := q.Comparisons[0]
	q.Comparisons = nil
	for _, date := range []string{"2026-09-01", "2026-09-08", "2026-09-30"} {
		comparison.Day.Date = date
		q.Comparisons = append(q.Comparisons, comparison)
	}
	sealRegistryProof(t, q)
	public.Sources[0].QualificationID, public.Policies[0].QualificationID = q.ID, q.ID
	snapshots := qualifiedSnapshotVerifier(public)
	publicEvidence := snapshots.evidence[testSnapshotID]
	publicEvidence.ID = public.TimeTables[0].PublishedSnapshotID
	snapshots.evidence[publicEvidence.ID] = publicEvidence
	snapshots.evidence[testSnapshotID] = VerifiedSnapshot{
		ID: testSnapshotID, MosqueID: testMosqueID, Timezone: legacy.Cities[0].Timezone,
		Effective: legacy.Policies[0].Effective, PayloadSHA256: repeatHex("b"), SigningKeyID: "synthetic-production-key", VerifiedAt: testNow(),
	}
	approvals := &fakeApprovalVerifier{evidence: map[string]VerifiedApproval{
		testApprovalID: {ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()},
	}}
	legacy.Authorities = append(legacy.Authorities, public.Authorities...)
	legacy.Sources = append(legacy.Sources, public.Sources...)
	legacy.Policies = append(legacy.Policies, public.Policies...)
	legacy.TimeTables = append(legacy.TimeTables, public.TimeTables...)
	legacy.Qualifications = public.Qualifications
	return legacy, approvals, snapshots
}

func TestProjectCityScheduleChoicesPreservesCalculationProfileIdentity(t *testing.T) {
	dataset := syntheticChoiceDataset(0)
	dataset.Scopes = append(dataset.Scopes, domain.GeographicScope{
		ID: "scope-synthetic-calculation-region", Kind: domain.GeographicScopeRegion,
		RegionID: syntheticChoiceRegionID, Description: "synthetic calculation-profile scope",
	})
	dataset.Authorities = append(dataset.Authorities, domain.PrayerAuthority{
		ID: "authority-synthetic-calculation", Name: "Synthetic calculation organization", EvidenceLabel: "PROPOSAL",
	})
	dataset.Sources = append(dataset.Sources, domain.PrayerSource{
		ID: "source-synthetic-calculation", Kind: domain.ProviderKindCalculationProfile,
		AuthorityIDs:      []string{"authority-synthetic-calculation"},
		GeographicScopeID: "scope-synthetic-calculation-region", Status: domain.PrayerSourceApproved,
		FreshThrough: "2026-12-31",
	})
	dataset.Policies = append(dataset.Policies, domain.PrayerPolicy{
		ID: "policy-synthetic-calculation", Kind: domain.PrayerPolicyCalculationProfile,
		GeographicScopeID: "scope-synthetic-calculation-region", AuthorityIDs: []string{"authority-synthetic-calculation"},
		SourceID: "source-synthetic-calculation", CalculationProfileID: "profile-synthetic-calculation-v1",
		MosqueIDs: []string{syntheticChoiceMosqueID}, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
		ApprovalID: "approval-synthetic-calculation",
	})
	dataset.CalculationProfiles = append(dataset.CalculationProfiles, domain.CalculationProfile{
		ID: "profile-synthetic-calculation-v1", SourceID: "source-synthetic-calculation",
		GeographicScopeID: "scope-synthetic-calculation-region", Version: "synthetic-v1",
		Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, ApprovalID: "approval-synthetic-calculation",
	})

	projected, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-calculation-choice-0001"}, State: RevisionStateStaged,
		Result: assessSyntheticChoices(t, dataset),
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices() error = %v", err)
	}
	if len(projected.Choices) != 1 || projected.Choices[0].Tier != ResolutionRegionalCalculation ||
		projected.Choices[0].PolicyKind != domain.PrayerPolicyCalculationProfile ||
		projected.Choices[0].CalculationProfileID != "profile-synthetic-calculation-v1" ||
		projected.Choices[0].CalculationProfile == nil || projected.Choices[0].TimeTable != nil ||
		projected.Choices[0].TimeTableID != "" {
		t.Fatalf("calculation-profile choice = %#v", projected)
	}
}

func TestProjectCityScheduleChoicesUsesNeutralDeterministicOrderWithoutResolvingAmbiguity(t *testing.T) {
	dataset := syntheticChoiceDataset(8)
	firstAssessment := assessSyntheticChoices(t, dataset)
	first, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-order-first-0001"}, State: RevisionStateStaged, Result: firstAssessment,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices(first) error = %v", err)
	}

	slices.Reverse(dataset.Authorities)
	slices.Reverse(dataset.Sources)
	slices.Reverse(dataset.Policies)
	slices.Reverse(dataset.TimeTables)
	secondAssessment := assessSyntheticChoices(t, dataset)
	second, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-order-second-0001"}, State: RevisionStateStaged, Result: secondAssessment,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices(second) error = %v", err)
	}

	if !reflect.DeepEqual(choiceIDs(first.Choices), choiceIDs(second.Choices)) {
		t.Fatalf("choice order changed: first=%v second=%v", choiceIDs(first.Choices), choiceIDs(second.Choices))
	}
	if !first.SelectionRequired || !second.SelectionRequired || first.AutomaticResolutionStatus != AssessmentAmbiguous {
		t.Fatalf("ambiguous projection was treated as an automatic selection: first=%#v second=%#v", first, second)
	}
	catalog, err := New(dataset)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}
	_, err = catalog.Resolve(syntheticChoiceRequest())
	if !errors.Is(err, ErrPolicyAmbiguous) {
		t.Fatalf("Resolve() error = %v, want fail-closed authority ambiguity", err)
	}
}

func TestProjectCityScheduleChoicesExcludesBlockedAndLowerPrecedenceOptionsButKeepsT039Assessment(t *testing.T) {
	dataset := syntheticChoiceDataset(3)
	dataset.Sources[1].Status = domain.PrayerSourceStale
	dataset.TimeTables[2].Effective = domain.DateRange{From: "2026-01-01", To: "2026-06-30"}
	addSyntheticRegionalChoice(&dataset)
	// Lower precedence is meaningful only within the same evidenced authority.
	dataset.Sources[len(dataset.Sources)-1].AuthorityIDs = append([]string(nil), dataset.Sources[0].AuthorityIDs...)
	dataset.Policies[len(dataset.Policies)-1].AuthorityIDs = append([]string(nil), dataset.Policies[0].AuthorityIDs...)

	assessment := assessSyntheticChoices(t, dataset)
	projected, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-blocked-choices-0001"}, State: RevisionStateStaged, Result: assessment,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices() error = %v", err)
	}
	if len(assessment.Options) != 4 {
		t.Fatalf("T039 assessment options = %d, want all 4", len(assessment.Options))
	}
	blocked := map[OptionBlockedReason]int{}
	for _, option := range assessment.Options {
		blocked[option.BlockedReason]++
	}
	if blocked[OptionSourceStale] != 1 || blocked[OptionScheduleUnavailable] != 1 || blocked[OptionLowerPrecedence] != 1 {
		t.Fatalf("T039 blocked reasons = %#v", blocked)
	}
	if len(projected.Choices) != 1 || projected.Choices[0].PolicyID != "policy-synthetic-00" || projected.SelectionRequired {
		t.Fatalf("T040 eligible projection = %#v", projected)
	}

	unavailableDataset := syntheticChoiceDataset(1)
	unavailableDataset.TimeTables[0].Effective = domain.DateRange{From: "2026-01-01", To: "2026-06-30"}
	unavailable, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-unavailable-choice-0001"}, State: RevisionStateStaged,
		Result: assessSyntheticChoices(t, unavailableDataset),
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices(unavailable) error = %v", err)
	}
	if unavailable.Status != CityScheduleChoicesUnavailable || len(unavailable.Choices) != 0 ||
		unavailable.AutomaticResolutionReason != AssessmentReasonScheduleUnavailable {
		t.Fatalf("unavailable choice projection = %#v", unavailable)
	}
}

func TestProjectCityScheduleChoicesUsesCanonicalAuthorityNamesWithoutCollapsingEqualLabels(t *testing.T) {
	dataset := syntheticChoiceDataset(2)
	dataset.Authorities[0].Name = "Полное синтетическое название организации"
	dataset.Authorities[1].Name = "Полное синтетическое название организации"

	projected, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: RevisionRecord{ID: "revision-label-choices-0001"}, State: RevisionStateStaged,
		Result: assessSyntheticChoices(t, dataset),
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices() error = %v", err)
	}
	if len(projected.Choices) != 2 || projected.Choices[0].ID == projected.Choices[1].ID ||
		projected.Choices[0].PolicyID == projected.Choices[1].PolicyID {
		t.Fatalf("same-label choices collapsed = %#v", projected.Choices)
	}
	for _, choice := range projected.Choices {
		if choice.AuthorityLabel != "Полное синтетическое название организации" ||
			choice.DisplayLabel != "Синтетический город (Полное синтетическое название организации)" {
			t.Fatalf("authority label invented or mixed with source = %#v", choice)
		}
	}
}

func TestProjectCityScheduleChoicesKeepsSameNameCitiesGeographicallyDistinct(t *testing.T) {
	dataset := syntheticChoiceDataset(1)
	dataset.Regions = append(dataset.Regions, domain.Region{
		ID: "region-synthetic-second", Name: "Второй синтетический субъект", CountryCode: "RU", FederalSubjectCode: "RU-YY",
	})
	dataset.Cities = append(dataset.Cities, domain.City{
		ID: "city-synthetic-second", Name: "Синтетический город", Aliases: []string{"Synthetic City Second"},
		CountryCode: "RU", RegionID: "region-synthetic-second", SettlementType: "PPL", Latitude: 56, Longitude: 50,
		Timezone: "Europe/Samara", GeographicSource: "https://example.invalid/geography/second",
		GeographicSourceID: "synthetic:second", GeographicRevision: "fixture-v1", GeographicLicense: "synthetic-test-only",
	})
	catalog, err := New(dataset)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}
	if matches := catalog.SearchCities("Синтетический город"); len(matches) != 2 || matches[0].ID == matches[1].ID {
		t.Fatalf("same-name search = %#v", matches)
	}
	if matches := catalog.SearchCities("Synthetic City"); len(matches) != 1 || matches[0].ID != syntheticChoiceCityID {
		t.Fatalf("alias search = %#v", matches)
	}
	secondAssessment, err := catalog.Assess(ResolveRequest{CityID: "city-synthetic-second", MosqueID: syntheticChoiceMosqueID, Date: "2026-08-30"})
	if err != nil || secondAssessment.Status != AssessmentUnavailable || len(secondAssessment.Options) != 0 {
		t.Fatalf("second same-name city assessment = %#v, %v", secondAssessment, err)
	}
}

const (
	syntheticChoiceCityID   = "city-synthetic-choice"
	syntheticChoiceRegionID = "region-synthetic-choice"
	syntheticChoiceScopeID  = "scope-synthetic-choice"
	syntheticChoiceMosqueID = "mosque-synthetic-choice"
)

func syntheticChoiceDataset(count int) Dataset {
	effective := domain.DateRange{From: "2026-01-01", To: "2026-12-31"}
	dataset := Dataset{
		Cities: []domain.City{{
			ID: syntheticChoiceCityID, Name: "Синтетический город", Aliases: []string{"Synthetic City"},
			CountryCode: "RU", RegionID: syntheticChoiceRegionID, SettlementType: "PPL", Latitude: 55, Longitude: 49,
			Timezone: "Europe/Moscow", GeographicSource: "https://example.invalid/geography/choice",
			GeographicSourceID: "synthetic:choice", GeographicRevision: "fixture-v1", GeographicLicense: "synthetic-test-only",
		}},
		Regions: []domain.Region{{
			ID: syntheticChoiceRegionID, Name: "Синтетический субъект", CountryCode: "RU", FederalSubjectCode: "RU-XX",
		}},
		Scopes: []domain.GeographicScope{{
			ID: syntheticChoiceScopeID, Kind: domain.GeographicScopeCity, CityID: syntheticChoiceCityID,
			RegionID: syntheticChoiceRegionID, Description: "synthetic city scope for choice tests",
		}},
	}
	for index := range count {
		suffix := twoDigits(index)
		authorityID := "authority-synthetic-" + suffix
		sourceID := "source-synthetic-" + suffix
		policyID := "policy-synthetic-" + suffix
		timetableID := "timetable-synthetic-" + suffix
		dataset.Authorities = append(dataset.Authorities, domain.PrayerAuthority{
			ID: authorityID, Name: "Synthetic canonical organization " + suffix, EvidenceLabel: "PROPOSAL",
		})
		dataset.Sources = append(dataset.Sources, domain.PrayerSource{
			ID: sourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{authorityID},
			GeographicScopeID: syntheticChoiceScopeID, CanonicalURL: "https://example.invalid/source/" + suffix,
			Status: domain.PrayerSourceApproved, FreshThrough: effective.To,
		})
		dataset.Policies = append(dataset.Policies, domain.PrayerPolicy{
			ID: policyID, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: syntheticChoiceScopeID,
			AuthorityIDs: []string{authorityID}, SourceID: sourceID, TimeTableID: timetableID,
			MosqueIDs: []string{syntheticChoiceMosqueID}, Effective: effective, ApprovalID: "approval-synthetic-" + suffix,
		})
		dataset.TimeTables = append(dataset.TimeTables, domain.TimeTable{
			ID: timetableID, SourceID: sourceID, GeographicScopeID: syntheticChoiceScopeID,
			MosqueID: syntheticChoiceMosqueID, Timezone: "Europe/Moscow", Effective: effective,
			PublishedSnapshotID: "snapshot-synthetic-" + suffix,
		})
	}
	return dataset
}

func addSyntheticRegionalChoice(dataset *Dataset) {
	effective := domain.DateRange{From: "2026-01-01", To: "2026-12-31"}
	dataset.Scopes = append(dataset.Scopes, domain.GeographicScope{
		ID: "scope-synthetic-region", Kind: domain.GeographicScopeRegion,
		RegionID: syntheticChoiceRegionID, Description: "synthetic regional scope for precedence test",
	})
	dataset.Authorities = append(dataset.Authorities, domain.PrayerAuthority{
		ID: "authority-synthetic-regional", Name: "Synthetic regional organization", EvidenceLabel: "PROPOSAL",
	})
	dataset.Sources = append(dataset.Sources, domain.PrayerSource{
		ID: "source-synthetic-regional", Kind: domain.ProviderKindOfficialFile,
		AuthorityIDs: []string{"authority-synthetic-regional"}, GeographicScopeID: "scope-synthetic-region",
		Status: domain.PrayerSourceApproved, FreshThrough: effective.To,
	})
	dataset.Policies = append(dataset.Policies, domain.PrayerPolicy{
		ID: "policy-synthetic-regional", Kind: domain.PrayerPolicyTimeTable,
		GeographicScopeID: "scope-synthetic-region", AuthorityIDs: []string{"authority-synthetic-regional"},
		SourceID: "source-synthetic-regional", TimeTableID: "timetable-synthetic-regional",
		MosqueIDs: []string{syntheticChoiceMosqueID}, Effective: effective, ApprovalID: "approval-synthetic-regional",
	})
	dataset.TimeTables = append(dataset.TimeTables, domain.TimeTable{
		ID: "timetable-synthetic-regional", SourceID: "source-synthetic-regional",
		GeographicScopeID: "scope-synthetic-region", MosqueID: syntheticChoiceMosqueID,
		Timezone: "Europe/Moscow", Effective: effective, PublishedSnapshotID: "snapshot-synthetic-regional",
	})
}

func assessSyntheticChoices(t *testing.T, dataset Dataset) PolicyAssessment {
	t.Helper()
	assessment, err := AssessDataset(dataset, syntheticChoiceRequest())
	if err != nil {
		t.Fatalf("AssessDataset() error = %v", err)
	}
	return assessment
}

func syntheticChoiceRequest() ResolveRequest {
	return ResolveRequest{CityID: syntheticChoiceCityID, MosqueID: syntheticChoiceMosqueID, Date: "2026-08-30"}
}

func choiceIDs(choices []CityScheduleChoice) []string {
	ids := make([]string, 0, len(choices))
	for _, choice := range choices {
		ids = append(ids, choice.ID)
	}
	return ids
}

func twoDigits(value int) string {
	if value < 10 {
		return "0" + string(rune('0'+value))
	}
	return string(rune('0'+value/10)) + string(rune('0'+value%10))
}
