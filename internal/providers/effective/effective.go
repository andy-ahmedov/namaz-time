// Package effective composes already validated candidates under an immutable,
// hash-bound source precedence policy. It cannot approve or publish output.
package effective

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"regexp"
	"sort"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
)

const (
	ParserVersion       = "effective-schedule/v1"
	maxPolicyBytes      = 256 * 1024
	policyFilename      = "effective-policy.json"
	policyContentType   = "application/json"
	maximumOverrideRows = 732
)

var sha256Pattern = regexp.MustCompile(`^[a-f0-9]{64}$`)

type Error struct {
	Op   string
	Code string
	Err  error
}

func (e *Error) Error() string { return fmt.Sprintf("%s: %s: %v", e.Op, e.Code, e.Err) }
func (e *Error) Unwrap() error { return e.Err }

func IsErrorCode(err error, code string) bool {
	var composeError *Error
	return errors.As(err, &composeError) && composeError.Code == code
}

type Policy struct {
	SchemaVersion string                 `json:"schema_version"`
	CompositionID string                 `json:"composition_id"`
	DecidedBy     string                 `json:"decided_by"`
	DecidedAt     string                 `json:"decided_at"`
	Reason        string                 `json:"reason"`
	Baseline      ComponentBinding       `json:"baseline"`
	Overrides     []OverrideBinding      `json:"overrides"`
	OutputSource  domain.CandidateSource `json:"output_source"`
}

type ComponentBinding struct {
	CandidateID         string `json:"candidate_id"`
	RawSHA256           string `json:"raw_sha256"`
	TranscriptionSHA256 string `json:"transcription_sha256"`
	NormalizedSHA256    string `json:"normalized_sha256"`
	ParserVersion       string `json:"parser_version"`
}

type OverrideBinding struct {
	ComponentBinding
	EffectiveFrom        string            `json:"effective_from"`
	EffectiveTo          string            `json:"effective_to"`
	AppliedFields        []string          `json:"applied_fields"`
	ResolvedFlagRewrites map[string]string `json:"resolved_flag_rewrites"`
	Reason               string            `json:"reason"`
}

type ComposeRequest struct {
	Policy         Policy
	PolicyBytes    []byte
	PolicyArtifact domain.RawArtifact
	Baseline       domain.CandidateSchedule
	Overrides      []domain.CandidateSchedule
}

func CapturePolicyArtifact(filename string, capturedAt time.Time, reader io.Reader) (domain.RawArtifact, error) {
	limited := io.LimitReader(reader, maxPolicyBytes+1)
	data, err := io.ReadAll(limited)
	if err != nil {
		return domain.RawArtifact{}, &Error{Op: "capture effective policy", Code: "read_failed", Err: err}
	}
	if len(data) == 0 || len(data) > maxPolicyBytes || filename != policyFilename || capturedAt.IsZero() {
		return domain.RawArtifact{}, &Error{Op: "capture effective policy", Code: "invalid_policy_artifact", Err: errors.New("bounded effective-policy.json bytes and capture time are required")}
	}
	hash := sha256.Sum256(data)
	return domain.RawArtifact{
		Filename: filename, ContentType: policyContentType,
		CapturedAt: capturedAt.UTC().Format(time.RFC3339), ByteLength: int64(len(data)),
		SHA256: hex.EncodeToString(hash[:]),
	}, nil
}

func DecodePolicy(data []byte) (Policy, error) {
	if len(data) == 0 || len(data) > maxPolicyBytes || !utf8.Valid(data) {
		return Policy{}, &Error{Op: "decode effective policy", Code: "schema_drift", Err: errors.New("policy must be bounded valid UTF-8")}
	}
	var policy Policy
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&policy); err != nil {
		return Policy{}, &Error{Op: "decode effective policy", Code: "schema_drift", Err: err}
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return Policy{}, &Error{Op: "decode effective policy", Code: "schema_drift", Err: errors.New("multiple JSON values")}
	}
	if err := validatePolicy(policy); err != nil {
		return Policy{}, err
	}
	return policy, nil
}

func Compose(request ComposeRequest) (domain.CandidateSchedule, error) {
	if err := validatePolicy(request.Policy); err != nil {
		return domain.CandidateSchedule{}, err
	}
	decodedPolicy, err := DecodePolicy(request.PolicyBytes)
	if err != nil {
		return domain.CandidateSchedule{}, err
	}
	decodedJSON, _ := json.Marshal(decodedPolicy)
	requestJSON, _ := json.Marshal(request.Policy)
	if !bytes.Equal(decodedJSON, requestJSON) {
		return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "policy_binding_mismatch", Err: errors.New("decoded policy bytes do not match the requested policy")}
	}
	if err := validatePolicyArtifact(request.PolicyArtifact, request.PolicyBytes); err != nil {
		return domain.CandidateSchedule{}, err
	}
	if err := validateComponent(request.Baseline, request.Policy.Baseline); err != nil {
		return domain.CandidateSchedule{}, err
	}
	if len(request.Overrides) != len(request.Policy.Overrides) {
		return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "component_binding_mismatch", Err: errors.New("override count does not match policy")}
	}

	days := append([]domain.CandidatePrayerDay(nil), request.Baseline.Days...)
	components := []domain.CandidateSourceComponent{componentProvenance(
		"baseline", request.Baseline, request.Baseline.Coverage, baselineFields(),
	)}
	seenDates := make(map[string]struct{})
	for index, binding := range request.Policy.Overrides {
		override := request.Overrides[index]
		if err := validateComponent(override, binding.ComponentBinding); err != nil {
			return domain.CandidateSchedule{}, err
		}
		if override.Mosque != request.Baseline.Mosque || override.DataClassification != request.Baseline.DataClassification {
			return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "component_scope_mismatch", Err: errors.New("all components must have identical mosque and classification")}
		}
		if override.Coverage.From != binding.EffectiveFrom || override.Coverage.To != binding.EffectiveTo || len(override.Days) > maximumOverrideRows {
			return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "override_coverage_mismatch", Err: errors.New("override rows must exactly cover the policy range")}
		}
		for _, overrideDay := range override.Days {
			if _, duplicate := seenDates[overrideDay.Date]; duplicate {
				return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "overlapping_overrides", Err: fmt.Errorf("date %s has multiple overrides", overrideDay.Date)}
			}
			seenDates[overrideDay.Date] = struct{}{}
			position := sort.Search(len(days), func(position int) bool { return days[position].Date >= overrideDay.Date })
			if position >= len(days) || days[position].Date != overrideDay.Date {
				return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "override_coverage_mismatch", Err: fmt.Errorf("date %s is outside baseline", overrideDay.Date)}
			}
			merged, mergeErr := applyFields(days[position], overrideDay, binding)
			if mergeErr != nil {
				return domain.CandidateSchedule{}, mergeErr
			}
			days[position] = merged
		}
		components = append(components, componentProvenance(
			"override", override, domain.DateRange{From: binding.EffectiveFrom, To: binding.EffectiveTo}, binding.AppliedFields,
		))
	}

	transcription, err := json.Marshal(days)
	if err != nil {
		return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "normalization_failed", Err: err}
	}
	transcriptionHash := sha256.Sum256(transcription)
	candidate := domain.CandidateSchedule{
		DataClassification: request.Baseline.DataClassification,
		Mosque:             request.Baseline.Mosque, Source: request.Policy.OutputSource,
		Artifact: request.PolicyArtifact, TranscriptionSHA256: hex.EncodeToString(transcriptionHash[:]),
		ParserVersion: ParserVersion, Coverage: request.Baseline.Coverage,
		Days: days, Components: components, Status: domain.CandidateNeedsReview,
	}
	candidate.Validation = controlled.ValidateCandidate(controlled.ValidationConfig{
		SourceCountryCode: candidate.Mosque.CountryCode,
		SourceTimezone:    candidate.Mosque.Timezone,
		SourceStatus:      "active",
	}, candidate)
	if len(candidate.Validation.Errors) > 0 {
		candidate.Status = domain.CandidateValidationFailed
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		return domain.CandidateSchedule{}, &Error{Op: "compose effective schedule", Code: "normalization_failed", Err: err}
	}
	return candidate, nil
}

func validatePolicyArtifact(artifact domain.RawArtifact, data []byte) error {
	hash := sha256.Sum256(data)
	if artifact.Filename != policyFilename || artifact.ContentType != policyContentType ||
		artifact.ByteLength != int64(len(data)) || artifact.SHA256 != hex.EncodeToString(hash[:]) {
		return &Error{Op: "compose effective schedule", Code: "policy_binding_mismatch", Err: errors.New("policy artifact metadata does not bind exact bytes")}
	}
	if capturedAt, err := time.Parse(time.RFC3339, artifact.CapturedAt); err != nil || capturedAt.IsZero() {
		return &Error{Op: "compose effective schedule", Code: "policy_binding_mismatch", Err: errors.New("policy artifact capture time is invalid")}
	}
	return nil
}

func validatePolicy(policy Policy) error {
	invalid := func(message string) error {
		return &Error{Op: "validate effective policy", Code: "invalid_policy", Err: errors.New(message)}
	}
	if policy.SchemaVersion != "1.0" || len(policy.CompositionID) < 8 || len(policy.CompositionID) > 128 ||
		strings.TrimSpace(policy.DecidedBy) == "" || len(policy.DecidedBy) > 240 ||
		strings.TrimSpace(policy.Reason) == "" || len(policy.Reason) > 2000 {
		return invalid("schema, bounded identity, decision actor and reason are required")
	}
	if decidedAt, err := time.Parse(time.RFC3339, policy.DecidedAt); err != nil || decidedAt.IsZero() {
		return invalid("decided_at must be RFC3339")
	}
	if err := validateBinding(policy.Baseline); err != nil {
		return invalid(err.Error())
	}
	if len(policy.Overrides) == 0 || len(policy.Overrides) > 32 {
		return invalid("one to 32 bounded overrides are required")
	}
	seenCandidates := map[string]struct{}{policy.Baseline.CandidateID: {}}
	for _, override := range policy.Overrides {
		if err := validateBinding(override.ComponentBinding); err != nil {
			return invalid(err.Error())
		}
		if _, duplicate := seenCandidates[override.CandidateID]; duplicate {
			return invalid("component candidate IDs must be unique")
		}
		seenCandidates[override.CandidateID] = struct{}{}
		from, fromErr := time.Parse("2006-01-02", override.EffectiveFrom)
		to, toErr := time.Parse("2006-01-02", override.EffectiveTo)
		if fromErr != nil || toErr != nil || to.Before(from) || strings.TrimSpace(override.Reason) == "" || len(override.Reason) > 2000 {
			return invalid("override range and reason are invalid")
		}
		seenFields := make(map[string]struct{}, len(override.AppliedFields))
		for _, field := range override.AppliedFields {
			if _, allowed := allowedFields[field]; !allowed {
				return invalid("override contains an unsupported applied field")
			}
			if _, duplicate := seenFields[field]; duplicate {
				return invalid("override applied fields must be unique")
			}
			seenFields[field] = struct{}{}
		}
		if len(seenFields) == 0 {
			return invalid("override must apply at least one field")
		}
		if len(override.ResolvedFlagRewrites) > 32 {
			return invalid("too many resolved flag rewrites")
		}
		for before, after := range override.ResolvedFlagRewrites {
			if !strings.HasPrefix(before, "requires_review_") || !strings.HasPrefix(after, "source_") || len(before) > 128 || len(after) > 128 {
				return invalid("resolved flag rewrites must map bounded requires_review_ flags to source_ evidence flags")
			}
		}
	}
	output := policy.OutputSource
	if output.SourceID == "" || output.AuthorityName == "" || output.GeographicScope == "" ||
		output.Kind != domain.ProviderKindManualImport || output.PermissionStatus != "granted" ||
		!output.ApprovalRequired || output.MinimumCoverageDays < 1 || output.MinimumCoverageDays > 732 ||
		output.MaxDeltaMinutes < 0 || output.MaxDeltaMinutes > 180 {
		return invalid("output source must be a granted approval-required bounded manual composition")
	}
	return nil
}

func validateBinding(binding ComponentBinding) error {
	if binding.CandidateID == "" || binding.ParserVersion == "" ||
		!sha256Pattern.MatchString(binding.RawSHA256) || !sha256Pattern.MatchString(binding.TranscriptionSHA256) ||
		!sha256Pattern.MatchString(binding.NormalizedSHA256) {
		return errors.New("component binding requires candidate, raw, transcription, normalized and parser identities")
	}
	return nil
}

func validateComponent(candidate domain.CandidateSchedule, binding ComponentBinding) error {
	if candidate.Status != domain.CandidateNeedsReview || len(candidate.Validation.Errors) != 0 {
		return &Error{Op: "compose effective schedule", Code: "component_invalid", Err: errors.New("components must be valid unapproved candidates")}
	}
	recomputed, err := domain.CandidateNormalizedSHA256(candidate)
	if err != nil || recomputed != candidate.NormalizedSHA256 {
		return &Error{Op: "compose effective schedule", Code: "component_binding_mismatch", Err: errors.New("component normalized fingerprint does not match content")}
	}
	if candidate.ID != binding.CandidateID || candidate.Artifact.SHA256 != binding.RawSHA256 ||
		candidate.TranscriptionSHA256 != binding.TranscriptionSHA256 || candidate.NormalizedSHA256 != binding.NormalizedSHA256 ||
		candidate.ParserVersion != binding.ParserVersion {
		return &Error{Op: "compose effective schedule", Code: "component_binding_mismatch", Err: errors.New("component does not match policy hashes")}
	}
	return nil
}

func componentProvenance(role string, candidate domain.CandidateSchedule, effective domain.DateRange, fields []string) domain.CandidateSourceComponent {
	return domain.CandidateSourceComponent{
		Role: role, CandidateID: candidate.ID, Source: candidate.Source, RawArtifact: candidate.Artifact,
		TranscriptionSHA256: candidate.TranscriptionSHA256, NormalizedSHA256: candidate.NormalizedSHA256,
		ParserVersion: candidate.ParserVersion, Effective: effective, AppliedFields: append([]string(nil), fields...),
	}
}

func applyFields(baseline, override domain.CandidatePrayerDay, binding OverrideBinding) (domain.CandidatePrayerDay, error) {
	result := baseline
	for _, field := range binding.AppliedFields {
		switch field {
		case "fajr":
			result.Fajr = override.Fajr
		case "recommended_fajr":
			result.RecommendedFajr = override.RecommendedFajr
		case "sunrise":
			result.Sunrise = override.Sunrise
		case "zenith":
			result.Zenith = override.Zenith
		case "dhuhr":
			result.Dhuhr = override.Dhuhr
		case "dhuhr_congregation":
			result.DhuhrCongregation = override.DhuhrCongregation
		case "asr":
			result.Asr = override.Asr
		case "maghrib":
			result.Maghrib = override.Maghrib
		case "isha":
			result.Isha = override.Isha
		case "duha":
			result.Duha = override.Duha
		case "middle_of_night":
			result.MiddleOfNight = override.MiddleOfNight
		case "last_third_of_night":
			result.LastThirdOfNight = override.LastThirdOfNight
		case "hijri_day":
			result.HijriDay = override.HijriDay
		case "hijri_month":
			result.HijriMonth = override.HijriMonth
		case "hijri_year":
			result.HijriYear = override.HijriYear
		case "flags":
			result.Flags = make([]string, len(override.Flags))
			for index, flag := range override.Flags {
				if replacement, exists := binding.ResolvedFlagRewrites[flag]; exists {
					flag = replacement
				}
				if strings.HasPrefix(flag, "requires_review_") {
					return domain.CandidatePrayerDay{}, &Error{Op: "compose effective schedule", Code: "unresolved_review_flag", Err: fmt.Errorf("date %s retains unresolved flag %s", override.Date, flag)}
				}
				result.Flags[index] = flag
			}
		}
	}
	return result, nil
}

var allowedFields = map[string]struct{}{
	"fajr": {}, "recommended_fajr": {}, "sunrise": {}, "zenith": {}, "dhuhr": {},
	"dhuhr_congregation": {}, "asr": {}, "maghrib": {}, "isha": {}, "duha": {},
	"middle_of_night": {}, "last_third_of_night": {}, "hijri_day": {}, "hijri_month": {},
	"hijri_year": {}, "flags": {},
}

func baselineFields() []string {
	return []string{
		"fajr", "recommended_fajr", "sunrise", "zenith", "dhuhr", "dhuhr_congregation",
		"asr", "maghrib", "isha", "duha", "middle_of_night", "last_third_of_night",
		"hijri_day", "hijri_month", "hijri_year", "flags",
	}
}
