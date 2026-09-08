package domain

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/canonicaljson"
)

const SourceQualificationSchema = "namaztime-source-qualification/v1"
const SourceQualificationDecisionSystem = "namaztime:source-qualification/v1"

// SourceEvidence is a researcher-assessed first-party claim bound to captured
// public bytes. A hash verifies the capture's identity, not the truth of a claim.
// Publication separately authenticates NamazTime's qualification decision.
type SourceEvidence struct {
	ID          string `json:"id"`
	Purpose     string `json:"purpose"`
	Label       string `json:"label"`
	URL         string `json:"url"`
	RetrievedAt string `json:"retrieved_at"`
	SHA256      string `json:"sha256"`
	Claim       string `json:"claim"`
}

type PublicSourceRetrieval struct {
	URL          string `json:"url"`
	HTTPStatus   int    `json:"http_status"`
	ContentType  string `json:"content_type"`
	ETag         string `json:"etag,omitempty"`
	LastModified string `json:"last_modified,omitempty"`
}

// SourceValueComparison records independently read source values, not a claim
// that the parser agreed with its own output. References identify the evidence
// artifact/page used for the independent reading.
type SourceValueComparison struct {
	EvidenceID string    `json:"evidence_id"`
	Day        PrayerDay `json:"day"`
}

type SourceWarningResolution struct {
	Code       string `json:"code"`
	EvidenceID string `json:"evidence_id"`
	Reason     string `json:"reason"`
}

// SourceQualification is the public first-party branch, not a human approval
// or external endorsement. All bindings are immutable and covered by its hash.
// The authority is one independently researched organization; its Branch may
// identify an explicitly evidenced subordinate publisher, never a rival.
type SourceQualification struct {
	SchemaVersion       string                    `json:"schema_version"`
	ID                  string                    `json:"qualification_id"`
	State               string                    `json:"state"`
	DecisionSystem      string                    `json:"decision_system"`
	QualifiedAt         string                    `json:"qualified_at"`
	Authority           PrayerAuthority           `json:"authority"`
	SourceID            string                    `json:"source_id"`
	Kind                ProviderKind              `json:"source_kind"`
	CanonicalURL        string                    `json:"canonical_url"`
	Scope               GeographicScope           `json:"scope"`
	CatalogRevision     string                    `json:"catalog_revision"`
	Timezone            string                    `json:"timezone"`
	Coverage            DateRange                 `json:"coverage"`
	FreshThrough        string                    `json:"fresh_through"`
	Artifact            RawArtifact               `json:"artifact"`
	Retrieval           PublicSourceRetrieval     `json:"retrieval"`
	ParserVersion       string                    `json:"parser_version"`
	CandidateID         string                    `json:"candidate_id"`
	NormalizedSHA256    string                    `json:"normalized_sha256"`
	OnsetSHA256         string                    `json:"onset_sha256"`
	TranscriptionSHA256 string                    `json:"transcription_sha256"`
	DiffSHA256          string                    `json:"diff_sha256"`
	ValidationSHA256    string                    `json:"validation_sha256"`
	ValidatedDays       int                       `json:"validated_days"`
	TermsAssessment     string                    `json:"terms_assessment"`
	Evidence            []SourceEvidence          `json:"evidence"`
	Comparisons         []SourceValueComparison   `json:"comparisons"`
	WarningResolutions  []SourceWarningResolution `json:"warning_resolutions,omitempty"`
	Unknowns            []string                  `json:"unknowns,omitempty"`
	SHA256              string                    `json:"sha256"`
}

// Validate verifies the self-contained proof shape and fingerprint. Candidate
// data, current catalog bindings and freshness at use require further checks.
func (q SourceQualification) Validate() error {
	if q.SchemaVersion != SourceQualificationSchema || q.State != "qualified" || q.DecisionSystem != SourceQualificationDecisionSystem {
		return fmt.Errorf("source qualification: explicit qualified NamazTime decision is required")
	}
	for _, field := range []struct{ name, value string }{
		{"source_id", q.SourceID}, {"authority.id", q.Authority.ID}, {"parser_version", q.ParserVersion},
		{"candidate_id", q.CandidateID}, {"catalog_revision", q.CatalogRevision}, {"scope.id", q.Scope.ID},
		{"scope.region_id", q.Scope.RegionID},
	} {
		if !qualificationText(field.value, 128) {
			return fmt.Errorf("source qualification: %s is missing or invalid", field.name)
		}
	}
	if !qualificationText(q.Authority.Name, 240) || q.Authority.EvidenceLabel != "CONFIRMED_PUBLIC" || !publicEvidenceURL(q.Authority.Website) ||
		(q.Authority.Branch != "" && !qualificationText(q.Authority.Branch, 240)) {
		return fmt.Errorf("source qualification: confirmed first-party authority identity is required")
	}
	if !publicEvidenceURL(q.CanonicalURL) || !qualificationText(q.Scope.Description, 1000) {
		return fmt.Errorf("source qualification: canonical public source and scope description are required")
	}
	if (q.Scope.Kind == GeographicScopeCity && !qualificationText(q.Scope.CityID, 128)) ||
		(q.Scope.Kind == GeographicScopeRegion && q.Scope.CityID != "") ||
		(q.Scope.Kind != GeographicScopeCity && q.Scope.Kind != GeographicScopeRegion) {
		return fmt.Errorf("source qualification: exact city or explicit region scope is required")
	}
	// This contract qualifies exact first-party tables. An official calculation
	// policy needs its own sufficient-parameters and multi-season proof contract;
	// accepting only an authority/method string here would introduce a fallback.
	if !stringInSet(string(q.Kind), string(ProviderKindOfficialAPI), string(ProviderKindOfficialFile), string(ProviderKindOfficialHTML), string(ProviderKindMosqueCalendar)) {
		return fmt.Errorf("source qualification: only exact public first-party table kinds are supported")
	}
	location, zoneErr := time.LoadLocation(q.Timezone)
	if zoneErr != nil || !strings.Contains(q.Timezone, "/") || !qualificationText(q.Timezone, 64) {
		return fmt.Errorf("source qualification: named IANA timezone is required")
	}
	qualifiedAt, timeOK := qualificationTimestamp(q.QualifiedAt)
	capturedAt, capturedOK := qualificationTimestamp(q.Artifact.CapturedAt)
	if !timeOK || !capturedOK || capturedAt.After(qualifiedAt) {
		return fmt.Errorf("source qualification: capture must precede canonical UTC qualification time")
	}
	from, fromErr := time.Parse(time.DateOnly, q.Coverage.From)
	to, toErr := time.Parse(time.DateOnly, q.Coverage.To)
	fresh, freshErr := time.Parse(time.DateOnly, q.FreshThrough)
	if fromErr != nil || toErr != nil || freshErr != nil || !localDatePattern.MatchString(q.Coverage.From) ||
		!localDatePattern.MatchString(q.Coverage.To) || !localDatePattern.MatchString(q.FreshThrough) || to.Before(from) ||
		fresh.After(to) || fresh.Before(from) || q.FreshThrough < qualifiedAt.In(location).Format(time.DateOnly) {
		return fmt.Errorf("source qualification: current bounded coverage and freshness are required")
	}
	dayCount := int(to.Sub(from)/(24*time.Hour)) + 1
	if dayCount < 1 || dayCount > maxSnapshotPrayerDays || q.ValidatedDays != dayCount {
		return fmt.Errorf("source qualification: validation must cover every declared day (at most 400)")
	}
	if !qualificationText(q.Artifact.Filename, 2048) || q.Artifact.ByteLength <= 0 ||
		!qualificationText(q.Artifact.ContentType, 240) || q.Retrieval.HTTPStatus != 200 ||
		!publicEvidenceURL(q.Retrieval.URL) || q.Retrieval.ContentType != q.Artifact.ContentType ||
		len(q.Retrieval.ETag) > 1024 || len(q.Retrieval.LastModified) > 128 {
		return fmt.Errorf("source qualification: successful bounded public artifact capture is required")
	}
	for _, hash := range []string{q.Artifact.SHA256, q.TranscriptionSHA256, q.NormalizedSHA256, q.OnsetSHA256, q.DiffSHA256, q.ValidationSHA256, q.SHA256} {
		if !sha256Pattern.MatchString(hash) {
			return fmt.Errorf("source qualification: artifact, normalization, diff, validation and proof hashes are required")
		}
	}
	if q.TermsAssessment != "public_transport_no_restriction_observed" && q.TermsAssessment != "public_transport_attribution_required" {
		return fmt.Errorf("source qualification: permitted public-transport terms assessment is required")
	}
	evidence, err := q.validateEvidence(qualifiedAt)
	if err != nil {
		return err
	}
	if err := q.validateComparisons(evidence, from, to, dayCount); err != nil {
		return err
	}
	seenWarnings := make(map[string]bool)
	if len(q.WarningResolutions) > 64 || len(q.Unknowns) > 64 {
		return fmt.Errorf("source qualification: too many warning resolutions or unknowns")
	}
	for _, resolution := range q.WarningResolutions {
		if !qualificationText(resolution.Code, 128) || seenWarnings[resolution.Code] ||
			!qualificationText(resolution.Reason, 2000) || evidence[resolution.EvidenceID].ID == "" {
			return fmt.Errorf("source qualification: warning resolution must bind unique code and recorded evidence")
		}
		seenWarnings[resolution.Code] = true
	}
	for _, unknown := range q.Unknowns {
		if !qualificationText(unknown, 1000) {
			return fmt.Errorf("source qualification: invalid unresolved-fact note")
		}
	}
	fingerprint, err := SourceQualificationSHA256(q)
	if err != nil || fingerprint != q.SHA256 || q.ID != "qualification-"+fingerprint[:32] {
		return fmt.Errorf("source qualification: proof fingerprint or identity does not match content")
	}
	return nil
}

func (q SourceQualification) validateEvidence(qualifiedAt time.Time) (map[string]SourceEvidence, error) {
	if len(q.Evidence) < 6 || len(q.Evidence) > 64 {
		return nil, fmt.Errorf("source qualification: bounded first-party evidence is required")
	}
	evidence := make(map[string]SourceEvidence, len(q.Evidence))
	purposes := make(map[string]bool)
	for _, item := range q.Evidence {
		stamp, stampOK := qualificationTimestamp(item.RetrievedAt)
		if !qualificationText(item.ID, 128) || evidence[item.ID].ID != "" || item.Label != "CONFIRMED_PUBLIC" ||
			!publicEvidenceURL(item.URL) || !sha256Pattern.MatchString(item.SHA256) || !qualificationText(item.Claim, 4000) ||
			!stampOK || stamp.After(qualifiedAt) || !stringInSet(item.Purpose, "ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison", "seasonal_transition", "authority_chain") {
			return nil, fmt.Errorf("source qualification: invalid or duplicate first-party evidence")
		}
		evidence[item.ID], purposes[item.Purpose] = item, true
	}
	for _, required := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		if !purposes[required] {
			return nil, fmt.Errorf("source qualification: %s evidence is required", required)
		}
	}
	return evidence, nil
}

func (q SourceQualification) validateComparisons(evidence map[string]SourceEvidence, from, to time.Time, dayCount int) error {
	if len(q.Comparisons) < min(3, dayCount) || len(q.Comparisons) > maxSnapshotPrayerDays {
		return fmt.Errorf("source qualification: independently read dated value comparisons are required")
	}
	dates, quarters := make(map[string]bool), make(map[int]bool)
	for _, comparison := range q.Comparisons {
		day := comparison.Day
		parsed, err := time.Parse(time.DateOnly, day.Date)
		if err != nil || !localDatePattern.MatchString(day.Date) || parsed.Before(from) || parsed.After(to) || dates[day.Date] ||
			evidence[comparison.EvidenceID].Purpose != "value_comparison" {
			return fmt.Errorf("source qualification: comparison must bind unique covered date and value evidence")
		}
		previous := ""
		for _, value := range []string{day.Fajr, day.Sunrise, day.Dhuhr, day.Asr, day.Maghrib, day.Isha} {
			if !localTimePattern.MatchString(value) || (previous != "" && value <= previous) {
				return fmt.Errorf("source qualification: compared onset values must be canonical and same-day ordered")
			}
			previous = value
		}
		if day.Duha != "" || day.MiddleOfNight != "" || day.LastThirdOfNight != "" || len(day.Flags) != 0 {
			return fmt.Errorf("source qualification: comparisons contain only six independently read onset fields")
		}
		dates[day.Date], quarters[parsed.Year()*4+(int(parsed.Month())-1)/3] = true, true
	}
	// Include every season touched by the requested interval. Full-year proof
	// cannot reuse three dates from a single month; partial coverage stays partial.
	for date := from; !date.After(to); date = date.AddDate(0, 0, 1) {
		if !quarters[date.Year()*4+(int(date.Month())-1)/3] {
			return fmt.Errorf("source qualification: comparisons omit a covered calendar quarter")
		}
	}
	return nil
}

func qualificationTimestamp(value string) (time.Time, bool) {
	parsed, err := time.Parse(time.RFC3339, value)
	return parsed, err == nil && parsed.UTC().Format(time.RFC3339) == value
}

func qualificationText(value string, maximum int) bool {
	return utf8.ValidString(value) && len([]rune(value)) > 0 && len([]rune(value)) <= maximum && strings.TrimSpace(value) == value &&
		strings.IndexFunc(value, unicode.IsControl) == -1
}

func publicEvidenceURL(value string) bool {
	if !qualificationText(value, 2048) {
		return false
	}
	parsed, err := url.ParseRequestURI(value)
	return err == nil && parsed.Scheme == "https" && parsed.Hostname() != "" && parsed.User == nil && parsed.Fragment == ""
}

func SourceQualificationSHA256(q SourceQualification) (string, error) {
	encoded, err := json.Marshal(q)
	if err != nil {
		return "", fmt.Errorf("encode source qualification: %w", err)
	}
	canonical, err := canonicaljson.WithoutRootMembers(encoded, "qualification_id", "sha256")
	if err != nil {
		return "", fmt.Errorf("canonicalize source qualification: %w", err)
	}
	hash := sha256.Sum256(canonical)
	return hex.EncodeToString(hash[:]), nil
}

// PrayerDaysSHA256 binds every materialized onset row using the same sorted-key
// JSON encoding as snapshots. It is independently reproducible by TV clients.
func PrayerDaysSHA256(days []PrayerDay) (string, error) {
	encoded, err := json.Marshal(days)
	if err != nil {
		return "", fmt.Errorf("encode onset days: %w", err)
	}
	canonical, err := canonicaljson.Encode(encoded)
	if err != nil {
		return "", fmt.Errorf("canonicalize onset days: %w", err)
	}
	hash := sha256.Sum256(canonical)
	return hex.EncodeToString(hash[:]), nil
}
