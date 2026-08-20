// Package manual implements the deterministic manual CSV provider. It creates
// unapproved candidates only; approval and publication live in a separate
// package by design.
package manual

import (
	"bytes"
	"crypto/sha256"
	"encoding/csv"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
	_ "time/tzdata"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const (
	ParserVersion         = "manual-csv/v1"
	CSVHeader             = "date,fajr,sunrise,zenith,dhuhr,collective_dhuhr,asr,maghrib,isha,hijri_day,hijri_month,hijri_year,flags"
	maxRawArtifactBytes   = 20 * 1024 * 1024
	maxTranscriptionBytes = 5 * 1024 * 1024
)

var expectedCSVHeader = strings.Split(CSVHeader, ",")

type Error struct {
	Op   string
	Code string
	Err  error
}

func (e *Error) Error() string {
	return fmt.Sprintf("%s: %s: %v", e.Op, e.Code, e.Err)
}

func (e *Error) Unwrap() error { return e.Err }

func IsErrorCode(err error, code string) bool {
	var providerError *Error
	return errors.As(err, &providerError) && providerError.Code == code
}

type GeographicScope struct {
	CountryCode string   `json:"country_code"`
	RegionCodes []string `json:"region_codes,omitempty"`
	LocalityIDs []string `json:"locality_ids,omitempty"`
	Description string   `json:"description"`
}

type TimezonePolicy struct {
	Mode         string `json:"mode"`
	IANATimezone string `json:"iana_timezone,omitempty"`
}

type RetrievalPolicy struct {
	Mode              string `json:"mode"`
	Cadence           string `json:"cadence"`
	StaleAfterHours   int    `json:"stale_after_hours"`
	HonorETag         bool   `json:"honor_etag"`
	HonorLastModified bool   `json:"honor_last_modified"`
	RateLimitNote     string `json:"rate_limit_note,omitempty"`
}

type Permission struct {
	Status            string `json:"status"`
	LicenseReference  string `json:"license_reference,omitempty"`
	AttributionText   string `json:"attribution_text,omitempty"`
	EvidenceReference string `json:"evidence_reference,omitempty"`
	ExpiresOn         string `json:"expires_on,omitempty"`
}

type ValidationPolicy struct {
	ApprovalRequired         bool   `json:"approval_required"`
	MaxAutomaticDeltaMinutes int    `json:"max_automatic_delta_minutes"`
	MinimumCoverageDays      int    `json:"minimum_coverage_days,omitempty"`
	FixtureSet               string `json:"fixture_set,omitempty"`
}

type SourceRecord struct {
	SourceID        string              `json:"source_id"`
	Kind            domain.ProviderKind `json:"kind"`
	AuthorityName   string              `json:"authority_name"`
	AuthorityBranch string              `json:"authority_branch,omitempty"`
	CanonicalURL    string              `json:"canonical_url,omitempty"`
	Contact         string              `json:"contact,omitempty"`
	GeographicScope GeographicScope     `json:"geographic_scope"`
	TimezonePolicy  TimezonePolicy      `json:"timezone_policy"`
	Retrieval       RetrievalPolicy     `json:"retrieval"`
	Permission      Permission          `json:"permission"`
	Validation      ValidationPolicy    `json:"validation"`
	Status          string              `json:"status"`
}

type ParseConfig struct {
	Mosque             domain.Mosque
	Source             SourceRecord
	ExpectedRawSHA256  string
	DataClassification domain.DataClassification
}

func CaptureArtifact(filename, contentType string, capturedAt time.Time, reader io.Reader) (domain.RawArtifact, error) {
	limited := io.LimitReader(reader, maxRawArtifactBytes+1)
	data, err := io.ReadAll(limited)
	if err != nil {
		return domain.RawArtifact{}, &Error{Op: "capture artifact", Code: "read_failed", Err: err}
	}
	if len(data) > maxRawArtifactBytes {
		return domain.RawArtifact{}, &Error{Op: "capture artifact", Code: "artifact_too_large", Err: fmt.Errorf("maximum is %d bytes", maxRawArtifactBytes)}
	}
	if strings.TrimSpace(filename) == "" || strings.TrimSpace(contentType) == "" || capturedAt.IsZero() {
		return domain.RawArtifact{}, &Error{Op: "capture artifact", Code: "invalid_metadata", Err: errors.New("filename, content type and captured time are required")}
	}
	hash := sha256.Sum256(data)
	return domain.RawArtifact{
		Filename:    filename,
		ContentType: contentType,
		CapturedAt:  capturedAt.UTC().Format(time.RFC3339),
		ByteLength:  int64(len(data)),
		SHA256:      hex.EncodeToString(hash[:]),
	}, nil
}

func DecodeSourceRecord(data []byte) (SourceRecord, error) {
	if !utf8.Valid(data) {
		return SourceRecord{}, &Error{Op: "decode source record", Code: "schema_drift", Err: errors.New("source record is not valid UTF-8")}
	}
	var source SourceRecord
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&source); err != nil {
		return SourceRecord{}, &Error{Op: "decode source record", Code: "schema_drift", Err: err}
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return SourceRecord{}, &Error{Op: "decode source record", Code: "schema_drift", Err: errors.New("multiple JSON values")}
	}
	if err := validateSourceRecord(source); err != nil {
		return SourceRecord{}, err
	}
	return source, nil
}

func Parse(config ParseConfig, artifact domain.RawArtifact, transcription []byte) (domain.CandidateSchedule, error) {
	// Parse is a public trust boundary. Callers may construct ParseConfig
	// directly instead of using DecodeSourceRecord, so provenance policy must be
	// revalidated here before any candidate can be created.
	if err := validateSourceRecord(config.Source); err != nil {
		return domain.CandidateSchedule{}, err
	}
	if config.Source.Status != "testing" && config.Source.Status != "active" {
		return domain.CandidateSchedule{}, &Error{
			Op: "parse manual CSV", Code: "source_not_enabled",
			Err: errors.New("source status must be testing or active"),
		}
	}
	if artifact.SHA256 != config.ExpectedRawSHA256 {
		return domain.CandidateSchedule{}, &Error{
			Op: "parse manual CSV", Code: "raw_checksum_mismatch",
			Err: fmt.Errorf("artifact %s does not match configured %s", artifact.SHA256, config.ExpectedRawSHA256),
		}
	}
	if len(transcription) > maxTranscriptionBytes {
		return domain.CandidateSchedule{}, &Error{Op: "parse manual CSV", Code: "transcription_too_large", Err: fmt.Errorf("maximum is %d bytes", maxTranscriptionBytes)}
	}
	records, err := decodeCSV(transcription)
	if err != nil {
		return domain.CandidateSchedule{}, err
	}
	days := make([]domain.CandidatePrayerDay, 0, len(records)-1)
	for rowIndex, record := range records[1:] {
		day, err := parseDay(record)
		if err != nil {
			return domain.CandidateSchedule{}, &Error{Op: fmt.Sprintf("parse manual CSV row %d", rowIndex+2), Code: "invalid_field", Err: err}
		}
		days = append(days, day)
	}
	transcriptionHash := sha256.Sum256(transcription)
	candidate := domain.CandidateSchedule{
		DataClassification: config.DataClassification,
		Mosque:             config.Mosque,
		Source: domain.CandidateSource{
			SourceID:            config.Source.SourceID,
			Kind:                config.Source.Kind,
			AuthorityName:       config.Source.AuthorityName,
			AuthorityBranch:     config.Source.AuthorityBranch,
			GeographicScope:     config.Source.GeographicScope.Description,
			CanonicalURL:        config.Source.CanonicalURL,
			PermissionStatus:    config.Source.Permission.Status,
			LicenseReference:    config.Source.Permission.LicenseReference,
			Attribution:         config.Source.Permission.AttributionText,
			ApprovalRequired:    config.Source.Validation.ApprovalRequired,
			MaxDeltaMinutes:     config.Source.Validation.MaxAutomaticDeltaMinutes,
			MinimumCoverageDays: config.Source.Validation.MinimumCoverageDays,
		},
		Artifact:            artifact,
		TranscriptionSHA256: hex.EncodeToString(transcriptionHash[:]),
		ParserVersion:       ParserVersion,
		Days:                days,
		Status:              domain.CandidateNeedsReview,
	}
	if len(days) > 0 {
		candidate.Coverage = domain.DateRange{From: days[0].Date, To: days[len(days)-1].Date}
	}
	candidate.Validation = validateCandidate(config, candidate)
	if len(candidate.Validation.Errors) > 0 {
		candidate.Status = domain.CandidateValidationFailed
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		return domain.CandidateSchedule{}, &Error{Op: "parse manual CSV", Code: "normalization_failed", Err: err}
	}
	return candidate, nil
}

func decodeCSV(data []byte) ([][]string, error) {
	reader := csv.NewReader(bytes.NewReader(data))
	reader.FieldsPerRecord = len(expectedCSVHeader)
	reader.ReuseRecord = false
	records, err := reader.ReadAll()
	if err != nil {
		return nil, &Error{Op: "decode manual CSV", Code: "schema_drift", Err: err}
	}
	if len(records) < 2 {
		return nil, &Error{Op: "decode manual CSV", Code: "empty_schedule", Err: errors.New("header and at least one row are required")}
	}
	for index, expected := range expectedCSVHeader {
		if records[0][index] != expected {
			return nil, &Error{Op: "decode manual CSV", Code: "schema_drift", Err: fmt.Errorf("column %d is %q, want %q", index+1, records[0][index], expected)}
		}
	}
	return records, nil
}

func parseDay(record []string) (domain.CandidatePrayerDay, error) {
	hijriDay, err := strconv.Atoi(record[9])
	if err != nil || hijriDay < 1 || hijriDay > 30 {
		return domain.CandidatePrayerDay{}, fmt.Errorf("hijri_day must be 1..30")
	}
	hijriYear, err := strconv.Atoi(record[11])
	if err != nil || hijriYear < 1 {
		return domain.CandidatePrayerDay{}, fmt.Errorf("hijri_year must be positive")
	}
	flags := []string(nil)
	if record[12] != "" {
		flags = strings.Split(record[12], ";")
	}
	return domain.CandidatePrayerDay{
		PrayerDay: domain.PrayerDay{
			Date: record[0], Fajr: record[1], Sunrise: record[2], Dhuhr: record[4],
			Asr: record[6], Maghrib: record[7], Isha: record[8], Flags: flags,
		},
		Zenith: record[3], DhuhrCongregation: record[5], HijriDay: hijriDay,
		HijriMonth: record[10], HijriYear: hijriYear,
	}, nil
}

func validateSourceRecord(source SourceRecord) error {
	if source.SourceID == "" || source.AuthorityName == "" || source.GeographicScope.CountryCode == "" || source.GeographicScope.Description == "" {
		return &Error{Op: "validate source record", Code: "invalid_source_record", Err: errors.New("source identity, authority and scope are required")}
	}
	if source.Kind != domain.ProviderKindManualImport || source.Retrieval.Mode != "manual_upload" {
		return &Error{Op: "validate source record", Code: "unsupported_source", Err: errors.New("manual provider requires manual_import/manual_upload")}
	}
	if !regexpCountryCode.MatchString(source.GeographicScope.CountryCode) {
		return &Error{Op: "validate source record", Code: "invalid_source_scope", Err: errors.New("country code must be two uppercase letters")}
	}
	if source.TimezonePolicy.Mode != "fixed_iana" || source.TimezonePolicy.IANATimezone == "" {
		return &Error{Op: "validate source record", Code: "invalid_timezone_policy", Err: errors.New("fixed_iana with an IANA timezone is required")}
	}
	if _, err := time.LoadLocation(source.TimezonePolicy.IANATimezone); err != nil || strings.HasPrefix(source.TimezonePolicy.IANATimezone, "+") || strings.HasPrefix(source.TimezonePolicy.IANATimezone, "-") {
		return &Error{Op: "validate source record", Code: "invalid_timezone_policy", Err: errors.New("timezone must be a named loadable IANA zone")}
	}
	if !inSet(source.Retrieval.Cadence, "on_change", "daily", "weekly", "monthly", "annual", "manual") || source.Retrieval.StaleAfterHours < 1 || source.Retrieval.StaleAfterHours > 17520 {
		return &Error{Op: "validate source record", Code: "invalid_retrieval_policy", Err: errors.New("cadence and positive stale_after_hours are required")}
	}
	if !inSet(source.Permission.Status, "unknown", "requested", "granted", "restricted", "denied") || !inSet(source.Status, "draft", "testing", "active", "suspended", "retired") || !source.Validation.ApprovalRequired {
		return &Error{Op: "validate source record", Code: "invalid_source_record", Err: errors.New("permission, status and approval_required=true are required")}
	}
	if source.Validation.MaxAutomaticDeltaMinutes < 0 || source.Validation.MaxAutomaticDeltaMinutes > 180 || source.Validation.MinimumCoverageDays < 1 || source.Validation.MinimumCoverageDays > 732 {
		return &Error{Op: "validate source record", Code: "invalid_validation_policy", Err: errors.New("delta and coverage bounds are invalid")}
	}
	if source.CanonicalURL != "" {
		parsed, err := url.ParseRequestURI(source.CanonicalURL)
		if err != nil || parsed.Scheme != "https" || parsed.Host == "" {
			return &Error{Op: "validate source record", Code: "invalid_canonical_url", Err: errors.New("canonical URL must be absolute HTTPS")}
		}
	}
	return nil
}

func inSet(value string, allowed ...string) bool {
	for _, candidate := range allowed {
		if value == candidate {
			return true
		}
	}
	return false
}

var regexpCountryCode = regexp.MustCompile(`^[A-Z]{2}$`)
