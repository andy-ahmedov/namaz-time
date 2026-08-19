package domain

import (
	"crypto/ed25519"
	"encoding/base64"
	"fmt"
	"regexp"
	"strings"
	"time"
	_ "time/tzdata"
)

var (
	localDatePattern = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	localTimePattern = regexp.MustCompile(`^(?:[01]\d|2[0-3]):[0-5]\d$`)
	sha256Pattern    = regexp.MustCompile(`^[a-f0-9]{64}$`)
)

const localDateLayout = "2006-01-02"

// ValidationError is a stable machine-readable domain validation failure.
type ValidationError struct {
	Path    string
	Code    string
	Message string
}

func (e ValidationError) Error() string {
	return fmt.Sprintf("%s: %s: %s", e.Path, e.Code, e.Message)
}

// ValidationErrors preserves deterministic validation order.
type ValidationErrors struct {
	Items []ValidationError
}

func (e *ValidationErrors) Error() string {
	parts := make([]string, 0, len(e.Items))
	for _, item := range e.Items {
		parts = append(parts, item.Error())
	}
	return strings.Join(parts, "; ")
}

// Validate checks the rules that JSON Schema cannot express, as well as the
// provenance and integrity fields required before a snapshot can be staged.
func (s Snapshot) Validate() error {
	var result ValidationErrors
	add := func(path, code, message string) {
		result.Items = append(result.Items, ValidationError{Path: path, Code: code, Message: message})
	}

	validateRequired(&result, "schema_version", s.SchemaVersion)
	if s.SchemaVersion != "" && s.SchemaVersion != "1.0" {
		add("schema_version", "unsupported_value", "must be 1.0")
	}
	validateRequired(&result, "snapshot_id", s.SnapshotID)
	validateRequired(&result, "data_classification", s.DataClassification)
	if s.DataClassification != "" && s.DataClassification != "production" && s.DataClassification != "synthetic" {
		add("data_classification", "unsupported_value", "must be production or synthetic")
	}
	validateRFC3339(&result, "generated_at", s.GeneratedAt)

	validateRequired(&result, "mosque.id", s.Mosque.ID)
	validateRequired(&result, "mosque.name", s.Mosque.Name)
	if s.Mosque.Timezone == "" {
		add("mosque.timezone", "required", "must not be empty")
	} else if s.Mosque.Timezone == "Local" {
		add("mosque.timezone", "invalid_timezone", "must not depend on the runtime local timezone")
	} else if _, err := time.LoadLocation(s.Mosque.Timezone); err != nil {
		add("mosque.timezone", "invalid_timezone", "must be a loadable IANA timezone ID")
	}

	validateSource(&result, s.Source)
	coverageFrom, coverageFromOK := validateDate(&result, "coverage.from", s.Coverage.From)
	coverageTo, coverageToOK := validateDate(&result, "coverage.to", s.Coverage.To)
	if coverageFromOK && coverageToOK && coverageTo.Before(coverageFrom) {
		add("coverage", "invalid_range", "to must not be before from")
	}
	validateCoverageWithinSource(&result, s.Source, coverageFrom, coverageTo, coverageFromOK && coverageToOK)
	validatePrayerDays(&result, s.PrayerDays, coverageFrom, coverageTo, coverageFromOK && coverageToOK)
	validateIntegrity(&result, s.Integrity)

	if len(result.Items) > 0 {
		return &result
	}
	return nil
}

func validateCoverageWithinSource(result *ValidationErrors, source SourceMetadata, coverageFrom, coverageTo time.Time, coverageOK bool) {
	if !coverageOK {
		return
	}
	sourceFrom, fromErr := time.Parse(localDateLayout, source.EffectiveFrom)
	sourceTo, toErr := time.Parse(localDateLayout, source.EffectiveTo)
	if fromErr != nil || toErr != nil {
		return
	}
	if coverageFrom.Before(sourceFrom) || coverageTo.After(sourceTo) {
		result.Items = append(result.Items, ValidationError{
			Path:    "coverage",
			Code:    "outside_source_effective_range",
			Message: "must be contained by source effective range",
		})
	}
}

func validateSource(result *ValidationErrors, source SourceMetadata) {
	validateRequired(result, "source.source_id", source.SourceID)
	if source.Kind == "" {
		result.Items = append(result.Items, ValidationError{"source.kind", "required", "must not be empty"})
	} else if !source.Kind.valid() {
		result.Items = append(result.Items, ValidationError{"source.kind", "unsupported_value", "must be an allowed provider kind"})
	}
	if source.Kind == ProviderKindCalculationProfile && strings.TrimSpace(source.CalculationProfile) == "" {
		result.Items = append(result.Items, ValidationError{"source.calculation_profile", "required_for_kind", "must identify the frozen calculation profile"})
	}
	validateRequired(result, "source.authority_name", source.AuthorityName)
	validateRequired(result, "source.geographic_scope", source.GeographicScope)
	validateRFC3339(result, "source.retrieved_at", source.RetrievedAt)
	sourceFrom, sourceFromOK := validateDate(result, "source.effective_from", source.EffectiveFrom)
	sourceTo, sourceToOK := validateDate(result, "source.effective_to", source.EffectiveTo)
	if sourceFromOK && sourceToOK && sourceTo.Before(sourceFrom) {
		result.Items = append(result.Items, ValidationError{"source", "invalid_range", "effective_to must not be before effective_from"})
	}
	validateSHA256(result, "source.raw_sha256", source.RawSHA256)
	validateRequired(result, "source.parser_version", source.ParserVersion)
	validateRequired(result, "source.approval.status", source.Approval.Status)
	if source.Approval.Status != "" && source.Approval.Status != "approved" {
		result.Items = append(result.Items, ValidationError{"source.approval.status", "unsupported_value", "must be approved"})
	}
	validateRequired(result, "source.approval.approval_id", source.Approval.ID)
	validateRequired(result, "source.approval.approved_by", source.Approval.ApprovedBy)
	validateRFC3339(result, "source.approval.approved_at", source.Approval.ApprovedAt)
	validateRequired(result, "source.approval.approval_scope", source.Approval.Scope)
}

func validatePrayerDays(result *ValidationErrors, days []PrayerDay, coverageFrom, coverageTo time.Time, coverageOK bool) {
	if len(days) == 0 {
		result.Items = append(result.Items, ValidationError{"prayer_days", "required", "must contain at least one day"})
		return
	}

	seen := make(map[string]struct{}, len(days))
	parsedDates := make([]time.Time, len(days))
	validDates := make([]bool, len(days))
	for index, day := range days {
		datePath := fmt.Sprintf("prayer_days[%d].date", index)
		parsedDates[index], validDates[index] = validateDate(result, datePath, day.Date)
		if _, duplicate := seen[day.Date]; duplicate && day.Date != "" {
			result.Items = append(result.Items, ValidationError{datePath, "duplicate_date", "date must be unique"})
		} else {
			seen[day.Date] = struct{}{}
		}
		validatePrayerTimes(result, index, day)
	}

	if coverageOK {
		for index := range days {
			if !validDates[index] {
				continue
			}
			expected := coverageFrom.AddDate(0, 0, index)
			if !parsedDates[index].Equal(expected) {
				result.Items = append(result.Items, ValidationError{
					Path:    fmt.Sprintf("prayer_days[%d].date", index),
					Code:    "date_gap",
					Message: fmt.Sprintf("must be %s for continuous coverage", expected.Format(localDateLayout)),
				})
			}
		}
		lastIndex := len(days) - 1
		if validDates[lastIndex] && !parsedDates[lastIndex].Equal(coverageTo) {
			result.Items = append(result.Items, ValidationError{"prayer_days", "coverage_mismatch", "must contain exactly one row for every coverage date"})
		}
	}
}

func validatePrayerTimes(result *ValidationErrors, index int, day PrayerDay) {
	required := []struct {
		name  string
		value string
	}{
		{"fajr", day.Fajr},
		{"sunrise", day.Sunrise},
		{"dhuhr", day.Dhuhr},
		{"asr", day.Asr},
		{"maghrib", day.Maghrib},
		{"isha", day.Isha},
	}
	for _, prayer := range required {
		validateTime(result, fmt.Sprintf("prayer_days[%d].%s", index, prayer.name), prayer.value, true)
	}
	optional := []struct {
		name  string
		value string
	}{
		{"duha", day.Duha},
		{"middle_of_night", day.MiddleOfNight},
		{"last_third_of_night", day.LastThirdOfNight},
	}
	for _, prayer := range optional {
		validateTime(result, fmt.Sprintf("prayer_days[%d].%s", index, prayer.name), prayer.value, false)
	}
}

func validateIntegrity(result *ValidationErrors, integrity IntegrityMetadata) {
	validateSHA256(result, "integrity.canonical_sha256", integrity.CanonicalSHA256)
	validateRequired(result, "integrity.signing_key_id", integrity.SigningKeyID)
	if integrity.SignatureEd25519Base64 == "" {
		result.Items = append(result.Items, ValidationError{"integrity.signature_ed25519_base64", "required", "must not be empty"})
		return
	}
	signature, err := base64.StdEncoding.DecodeString(integrity.SignatureEd25519Base64)
	if err != nil || len(signature) != ed25519.SignatureSize {
		result.Items = append(result.Items, ValidationError{"integrity.signature_ed25519_base64", "invalid_signature_encoding", "must encode a 64-byte Ed25519 signature"})
	}
}

func validateRequired(result *ValidationErrors, path, value string) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
	}
}

func validateRFC3339(result *ValidationErrors, path, value string) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return
	}
	if !isRFC3339DateTime(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_datetime", "must be RFC 3339"})
	}
}

func isRFC3339DateTime(value string) bool {
	normalized := []byte(value)
	if len(normalized) > 10 && normalized[10] == 't' {
		normalized[10] = 'T'
	}
	if len(normalized) > 0 && normalized[len(normalized)-1] == 'z' {
		normalized[len(normalized)-1] = 'Z'
	}
	if _, err := time.Parse(time.RFC3339, string(normalized)); err == nil {
		return true
	}
	if len(normalized) >= 19 && normalized[16] == ':' && string(normalized[17:19]) == "60" {
		normalized[17] = '5'
		normalized[18] = '9'
		parsed, err := time.Parse(time.RFC3339, string(normalized))
		return err == nil && parsed.UTC().Hour() == 23 && parsed.UTC().Minute() == 59
	}
	return false
}

func validateDate(result *ValidationErrors, path, value string) (time.Time, bool) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return time.Time{}, false
	}
	parsed, err := time.Parse(localDateLayout, value)
	if err != nil || !localDatePattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_date", "must be YYYY-MM-DD"})
		return time.Time{}, false
	}
	return parsed, true
}

func validateTime(result *ValidationErrors, path, value string, required bool) {
	if value == "" {
		if required {
			result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		}
		return
	}
	if !localTimePattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_time", "must be HH:MM from 00:00 through 23:59"})
	}
}

func validateSHA256(result *ValidationErrors, path, value string) {
	if value == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return
	}
	if !sha256Pattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_sha256", "must be 64 lowercase hexadecimal characters"})
	}
}

func (kind ProviderKind) valid() bool {
	switch kind {
	case ProviderKindOfficialAPI,
		ProviderKindOfficialFile,
		ProviderKindOfficialHTML,
		ProviderKindMosqueCalendar,
		ProviderKindCalculationProfile,
		ProviderKindManualImport:
		return true
	default:
		return false
	}
}
