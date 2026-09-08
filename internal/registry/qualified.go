package registry

import (
	"fmt"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

const QualifiedRegistrySchemaVersion = 2

func cloneQualification(q domain.SourceQualification) domain.SourceQualification {
	q.Evidence = append([]domain.SourceEvidence(nil), q.Evidence...)
	q.Comparisons = append([]domain.SourceValueComparison(nil), q.Comparisons...)
	for i := range q.Comparisons {
		q.Comparisons[i].Day.Flags = append([]string(nil), q.Comparisons[i].Day.Flags...)
	}
	q.WarningResolutions = append([]domain.SourceWarningResolution(nil), q.WarningResolutions...)
	q.Unknowns = append([]string(nil), q.Unknowns...)
	return q
}

// deriveQualificationCatalog inspects the full revision, not a caller-selected
// regional subset. Every canonical locality in an explicit regional scope is
// checked by VerifyCatalog; a timezone or source-revision mismatch fails closed.
func deriveQualificationCatalog(dataset Dataset, q domain.SourceQualification) qualification.CatalogBinding {
	binding := qualification.CatalogBinding{Revision: q.CatalogRevision}
	for _, region := range dataset.Regions {
		if region.ID == q.Scope.RegionID {
			binding.Region = region
			break
		}
	}
	for _, city := range dataset.Cities {
		if city.RegionID != q.Scope.RegionID || (q.Scope.Kind == domain.GeographicScopeCity && city.ID != q.Scope.CityID) {
			continue
		}
		if len(binding.Cities) == 0 {
			binding.SourceRevision = city.GeographicRevision
		}
		binding.Cities = append(binding.Cities, city)
	}
	return binding
}

func validateQualifiedDataset(dataset Dataset, sources map[string]domain.PrayerSource, scopes map[string]domain.GeographicScope, authorities map[string]domain.PrayerAuthority, timetables map[string]domain.TimeTable) error {
	proofs, err := uniqueIndex("source qualification", dataset.Qualifications, func(q domain.SourceQualification) string { return q.ID })
	if err != nil {
		return err
	}
	contexts := make(map[string]string, len(proofs))
	for _, q := range dataset.Qualifications {
		source, exists := sources[q.SourceID]
		if !exists || source.QualificationID != q.ID || q.Scope != scopes[source.GeographicScopeID] ||
			q.Authority != authorities[q.Authority.ID] || !sameStrings(source.AuthorityIDs, []string{q.Authority.ID}) ||
			source.Kind != q.Kind || source.CanonicalURL != q.CanonicalURL ||
			!validDate(source.FreshThrough) || source.FreshThrough > q.FreshThrough {
			return fmt.Errorf("qualification %q has inconsistent source, authority, scope, or freshness", q.ID)
		}
		binding := deriveQualificationCatalog(dataset, q)
		if err := qualification.VerifyCatalog(q, binding); err != nil {
			return err
		}
		context, err := qualification.PublicDisplayContext(q.Scope, binding, q.Timezone)
		if err != nil {
			return err
		}
		contexts[q.ID] = context.ID
	}
	for _, source := range dataset.Sources {
		if source.QualificationID == "" {
			if source.Status == domain.PrayerSourceQualified {
				return fmt.Errorf("source %q lacks its qualification", source.ID)
			}
			continue
		}
		q, exists := proofs[source.QualificationID]
		if !exists || q.SourceID != source.ID || (source.Status != domain.PrayerSourceQualified && source.Status != domain.PrayerSourceStale && source.Status != domain.PrayerSourceUnavailable) {
			return fmt.Errorf("source %q has invalid qualification or conflicting approval status", source.ID)
		}
	}
	for _, policy := range dataset.Policies {
		source := sources[policy.SourceID]
		if policy.QualificationID == "" && source.QualificationID == "" {
			continue
		}
		q, exists := proofs[policy.QualificationID]
		table, tableExists := timetables[policy.TimeTableID]
		if !exists || source.QualificationID != q.ID || policy.ApprovalID != "" || len(policy.MosqueIDs) != 0 ||
			policy.Kind != domain.PrayerPolicyTimeTable || policy.CalculationProfileID != "" || policy.Effective != q.Coverage ||
			!tableExists || table.MosqueID != contexts[q.ID] || table.Timezone != q.Timezone || table.Effective != q.Coverage || len(table.SourceOverrideIDs) != 0 {
			return fmt.Errorf("policy %q does not match its qualified public timetable; human approval and local overrides are separate", policy.ID)
		}
	}
	return nil
}

func validateRevisionQualification(record RevisionRecord, dataset Dataset) error {
	expectedVersion := RegistrySchemaVersion
	if len(dataset.Qualifications) > 0 {
		expectedVersion = QualifiedRegistrySchemaVersion
	}
	if record.SchemaVersion != expectedVersion {
		return fmt.Errorf("%w: registry schema does not match admission branches", ErrRevisionInvalid)
	}
	for _, q := range dataset.Qualifications {
		if q.CatalogRevision != record.CatalogRevisionID {
			return fmt.Errorf("%w: qualification %q targets another catalog revision", ErrRevisionInvalid, q.ID)
		}
	}
	return nil
}
