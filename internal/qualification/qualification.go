// Package qualification admits researched public first-party exact tables.
// It verifies captured-artifact, candidate, evidence and catalog bindings; it
// neither fetches sources nor creates an external approval or a signature.
package qualification

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"reflect"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
)

// CatalogBinding comes from the actual imported canonical catalog, not a name
// search result. Region-wide admission requires every catalog city in that
// region so that a single timezone cannot silently span incompatible places.
type CatalogBinding struct {
	Revision       string
	SourceRevision string
	Region         domain.Region
	Cities         []domain.City
}

type EvidenceReview struct {
	Authority          domain.PrayerAuthority           `json:"authority"`
	Scope              domain.GeographicScope           `json:"scope"`
	CatalogRevision    string                           `json:"catalog_revision"`
	Timezone           string                           `json:"timezone"`
	FreshThrough       string                           `json:"fresh_through"`
	Retrieval          domain.PublicSourceRetrieval     `json:"retrieval"`
	TermsAssessment    string                           `json:"terms_assessment"`
	Evidence           []domain.SourceEvidence          `json:"evidence"`
	Comparisons        []domain.SourceValueComparison   `json:"comparisons"`
	WarningResolutions []domain.SourceWarningResolution `json:"warning_resolutions,omitempty"`
	Unknowns           []string                         `json:"unknowns,omitempty"`
}

// Create requires the retained raw bytes, exact parsed candidate, independently
// recorded source comparisons and a trusted catalog projection. Diff identity
// is bound here and recomputed by the publication boundary before signing.
func Create(review EvidenceReview, candidate domain.CandidateSchedule, raw []byte, diffSHA256 string, catalog CatalogBinding, at time.Time) (domain.SourceQualification, error) {
	rawHash := sha256.Sum256(raw)
	if int64(len(raw)) != candidate.Artifact.ByteLength || hex.EncodeToString(rawHash[:]) != candidate.Artifact.SHA256 {
		return domain.SourceQualification{}, fmt.Errorf("qualify public source: captured raw bytes do not match candidate")
	}
	context, err := PublicDisplayContext(review.Scope, catalog, review.Timezone)
	if err != nil || context != candidate.Mosque {
		return domain.SourceQualification{}, fmt.Errorf("qualify public source: candidate display context does not match canonical geographic scope")
	}
	onsetHash, err := domain.PrayerDaysSHA256(candidateOnsets(candidate))
	if err != nil {
		return domain.SourceQualification{}, err
	}
	q := domain.SourceQualification{
		SchemaVersion: domain.SourceQualificationSchema, State: "qualified", DecisionSystem: domain.SourceQualificationDecisionSystem,
		QualifiedAt: at.UTC().Format(time.RFC3339), Authority: review.Authority,
		SourceID: candidate.Source.SourceID, Kind: candidate.Source.Kind, CanonicalURL: candidate.Source.CanonicalURL,
		Scope: review.Scope, CatalogRevision: review.CatalogRevision, Timezone: review.Timezone,
		Coverage: candidate.Coverage, FreshThrough: review.FreshThrough, Artifact: candidate.Artifact, Retrieval: review.Retrieval,
		ParserVersion: candidate.ParserVersion, CandidateID: candidate.ID, NormalizedSHA256: candidate.NormalizedSHA256,
		OnsetSHA256:         onsetHash,
		TranscriptionSHA256: candidate.TranscriptionSHA256, DiffSHA256: diffSHA256,
		ValidationSHA256: validationSHA256(candidate.Validation), ValidatedDays: len(candidate.Days),
		TermsAssessment: review.TermsAssessment, Evidence: append([]domain.SourceEvidence(nil), review.Evidence...),
		Comparisons:        append([]domain.SourceValueComparison(nil), review.Comparisons...),
		WarningResolutions: append([]domain.SourceWarningResolution(nil), review.WarningResolutions...),
		Unknowns:           append([]string(nil), review.Unknowns...),
	}
	fingerprint, err := domain.SourceQualificationSHA256(q)
	if err != nil {
		return domain.SourceQualification{}, fmt.Errorf("qualify public source: fingerprint: %w", err)
	}
	q.SHA256, q.ID = fingerprint, "qualification-"+fingerprint[:32]
	if err := VerifyCandidate(q, candidate, diffSHA256, at); err != nil {
		return domain.SourceQualification{}, err
	}
	if err := VerifyCatalog(q, catalog); err != nil {
		return domain.SourceQualification{}, err
	}
	return q, nil
}

// VerifyCandidate rechecks immutable proof against publication inputs without
// re-fetching or replacing raw data. Catalog membership is checked separately
// against the pinned catalog at registry admission.
func VerifyCandidate(q domain.SourceQualification, candidate domain.CandidateSchedule, diffSHA256 string, at time.Time) error {
	if err := q.Validate(); err != nil {
		return fmt.Errorf("verify public qualification: %w", err)
	}
	qualifiedAt, _ := time.Parse(time.RFC3339, q.QualifiedAt)
	location, _ := time.LoadLocation(q.Timezone)
	if at.IsZero() || at.Before(qualifiedAt) || at.In(location).Format(time.DateOnly) > q.FreshThrough {
		return fmt.Errorf("verify public qualification: decision is not current at publication time")
	}
	if q.CandidateID != candidate.ID || q.NormalizedSHA256 != candidate.NormalizedSHA256 || q.Artifact != candidate.Artifact ||
		q.TranscriptionSHA256 != candidate.TranscriptionSHA256 || q.ParserVersion != candidate.ParserVersion || q.DiffSHA256 != diffSHA256 ||
		q.Coverage != candidate.Coverage || q.Timezone != candidate.Mosque.Timezone || candidate.Mosque.CountryCode != "RU" ||
		q.SourceID != candidate.Source.SourceID || q.Kind != candidate.Source.Kind || q.CanonicalURL != candidate.Source.CanonicalURL ||
		q.Authority.Name != candidate.Source.AuthorityName || q.Authority.Branch != candidate.Source.AuthorityBranch ||
		q.Scope.Description != candidate.Source.GeographicScope || len(candidate.Components) != 0 {
		return fmt.Errorf("verify public qualification: source, scope, artifact or candidate binding differs")
	}
	if q.TermsAssessment == "public_transport_attribution_required" && strings.TrimSpace(candidate.Source.Attribution) == "" {
		return fmt.Errorf("verify public qualification: required source attribution is absent")
	}
	if candidate.Status != domain.CandidateNeedsReview || len(candidate.Validation.Errors) != 0 {
		return fmt.Errorf("verify public qualification: candidate has blocking validation or unsupported workflow state")
	}
	normalized, err := domain.CandidateNormalizedSHA256(candidate)
	if err != nil || normalized != q.NormalizedSHA256 || candidate.ID != "candidate-"+normalized[:32] {
		return fmt.Errorf("verify public qualification: normalized candidate fingerprint differs")
	}
	onsetHash, err := domain.PrayerDaysSHA256(candidateOnsets(candidate))
	if err != nil || onsetHash != q.OnsetSHA256 {
		return fmt.Errorf("verify public qualification: materialized onset fingerprint differs")
	}
	recomputed := controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: q.Timezone}, candidate)
	if len(recomputed.Errors) != 0 || validationSHA256(recomputed) != q.ValidationSHA256 || validationSHA256(candidate.Validation) != q.ValidationSHA256 || q.ValidatedDays != len(candidate.Days) {
		return fmt.Errorf("verify public qualification: data validation is invalid, missing or not reproducible")
	}
	warnings := make(map[string]bool)
	for _, warning := range recomputed.Warnings {
		warnings[warning.Code] = true
	}
	for _, resolution := range q.WarningResolutions {
		if !warnings[resolution.Code] {
			return fmt.Errorf("verify public qualification: warning resolution does not correspond to validation")
		}
		for _, evidence := range q.Evidence {
			if evidence.ID == resolution.EvidenceID && evidence.Purpose != "value_comparison" && evidence.Purpose != "time_semantics" && evidence.Purpose != "seasonal_transition" {
				return fmt.Errorf("verify public qualification: warning requires applicable time or value evidence")
			}
		}
		delete(warnings, resolution.Code)
	}
	if len(warnings) != 0 {
		return fmt.Errorf("verify public qualification: validation warnings lack evidence-bound resolutions")
	}
	days := make(map[string]domain.PrayerDay, len(candidate.Days))
	for _, day := range candidate.Days {
		days[day.Date] = domain.PrayerDay{Date: day.Date, Fajr: day.Fajr, Sunrise: day.Sunrise, Dhuhr: day.Dhuhr, Asr: day.Asr, Maghrib: day.Maghrib, Isha: day.Isha}
	}
	for _, comparison := range q.Comparisons {
		if !reflect.DeepEqual(comparison.Day, days[comparison.Day.Date]) {
			return fmt.Errorf("verify public qualification: independently read source values disagree on %s", comparison.Day.Date)
		}
	}
	return nil
}

func VerifyCatalog(q domain.SourceQualification, catalog CatalogBinding) error {
	if err := q.Validate(); err != nil {
		return fmt.Errorf("verify qualification catalog: %w", err)
	}
	if q.CatalogRevision != catalog.Revision {
		return fmt.Errorf("verify qualification catalog: pinned catalog revision differs")
	}
	_, err := PublicDisplayContext(q.Scope, catalog, q.Timezone)
	return err
}

// PublicDisplayContext is a geographic display identity, not an invented
// mosque or authority. The legacy transport field is called mosque; the v2
// qualification scope explicitly identifies this public city/region context.
func PublicDisplayContext(scope domain.GeographicScope, catalog CatalogBinding, timezone string) (domain.Mosque, error) {
	if scope.ID == "" || catalog.Revision == "" || catalog.SourceRevision == "" || scope.RegionID != catalog.Region.ID || catalog.Region.CountryCode != "RU" || catalog.Region.Name == "" || len(catalog.Cities) == 0 {
		return domain.Mosque{}, fmt.Errorf("public display context: canonical catalog and region binding are required")
	}
	if (scope.Kind == domain.GeographicScopeCity && (scope.CityID == "" || len(catalog.Cities) != 1)) ||
		(scope.Kind == domain.GeographicScopeRegion && scope.CityID != "") ||
		(scope.Kind != domain.GeographicScopeCity && scope.Kind != domain.GeographicScopeRegion) {
		return domain.Mosque{}, fmt.Errorf("public display context: exact city or explicit region binding is required")
	}
	seen := make(map[string]bool, len(catalog.Cities))
	for _, city := range catalog.Cities {
		if city.ID == "" || city.Name == "" || seen[city.ID] || city.CountryCode != "RU" || city.RegionID != catalog.Region.ID || city.Timezone != timezone || city.GeographicRevision != catalog.SourceRevision ||
			(scope.Kind == domain.GeographicScopeCity && city.ID != scope.CityID) {
			return domain.Mosque{}, fmt.Errorf("public display context: canonical locality, revision or timezone differs from scope")
		}
		seen[city.ID] = true
	}
	scopeHash := sha256.Sum256([]byte(scope.ID))
	context := domain.Mosque{ID: "public-scope-" + hex.EncodeToString(scopeHash[:16]), Name: catalog.Region.Name, CountryCode: "RU", Region: catalog.Region.Name, Timezone: timezone}
	if scope.Kind == domain.GeographicScopeCity {
		context.Name, context.Locality = catalog.Cities[0].Name, catalog.Cities[0].Name
	}
	return context, nil
}

func validationSHA256(report domain.CandidateValidationReport) string {
	encoded, _ := json.Marshal(report) // Report fields cannot fail JSON encoding.
	hash := sha256.Sum256(encoded)
	return hex.EncodeToString(hash[:])
}

func candidateOnsets(candidate domain.CandidateSchedule) []domain.PrayerDay {
	days := make([]domain.PrayerDay, len(candidate.Days))
	for index, day := range candidate.Days {
		days[index] = day.PrayerDay
	}
	return days
}
