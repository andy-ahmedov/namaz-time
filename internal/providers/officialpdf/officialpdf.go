// Package officialpdf implements the source-specific controlled transcription
// adapter for the RDUM Ulyanovsk 2026 annual PDF. The original PDF remains the
// raw artifact; the parser consumes a strict, human-reviewed CSV transcript and
// can create only an unapproved candidate.
package officialpdf

import (
	"bufio"
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
	"strings"
	"time"
	_ "time/tzdata"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
	"github.com/andy-ahmedov/namaz-time/internal/providers/sourceconfig"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const (
	ParserVersion        = "ulyanovsk-official-pdf-csv/v1"
	CSVHeader            = "date,fajr,recommended_fajr,sunrise,zenith,dhuhr,collective_dhuhr,asr,maghrib,isha,flags"
	maxArtifactBytes     = 48 * 1024 * 1024
	maxTranscriptBytes   = 5 * 1024 * 1024
	maxSourceRecordBytes = 256 * 1024
	expectedSourceID     = "official-rdumul-ulyanovsk-2026"
	expectedTimezone     = "Europe/Ulyanovsk"
	expectedCountryCode  = "RU"
	expectedMosqueID     = "second-cathedral-mosque-ulyanovsk"
	expectedLocality     = "Ульяновск, ул. Дзержинского, 18А"
	expectedFilename     = "calendar_for_the_year_Ulyanovsk.pdf"
	expectedArtifactSHA  = "82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21"
	expectedArtifactLen  = int64(35078839)
)

var (
	expectedCSVHeader = strings.Split(CSVHeader, ",")
	countryCode       = regexp.MustCompile(`^[A-Z]{2}$`)
)

// SourceRecord reuses the contract-aligned source registry representation;
// this adapter applies its own official_file policy.
type SourceRecord = sourceconfig.SourceRecord

type ParseConfig struct {
	Mosque             domain.Mosque
	Source             SourceRecord
	ExpectedRawSHA256  string
	DataClassification domain.DataClassification
}

type Error struct {
	Op   string
	Code string
	Err  error
}

func (e *Error) Error() string { return fmt.Sprintf("%s: %s: %v", e.Op, e.Code, e.Err) }
func (e *Error) Unwrap() error { return e.Err }

func IsErrorCode(err error, code string) bool {
	var providerError *Error
	return errors.As(err, &providerError) && providerError.Code == code
}

func CaptureArtifact(filename, contentType string, capturedAt time.Time, reader io.Reader) (domain.RawArtifact, error) {
	if strings.TrimSpace(filename) == "" || contentType != "application/pdf" || capturedAt.IsZero() {
		return domain.RawArtifact{}, &Error{Op: "capture official PDF", Code: "invalid_metadata", Err: errors.New("PDF filename, application/pdf content type and captured time are required")}
	}
	buffered := bufio.NewReader(reader)
	magic, err := buffered.Peek(5)
	if err != nil || !bytes.Equal(magic, []byte("%PDF-")) {
		return domain.RawArtifact{}, &Error{Op: "capture official PDF", Code: "invalid_pdf", Err: errors.New("raw artifact does not start with the PDF signature")}
	}
	hash := sha256.New()
	count, err := io.Copy(hash, io.LimitReader(buffered, maxArtifactBytes+1))
	if err != nil {
		return domain.RawArtifact{}, &Error{Op: "capture official PDF", Code: "read_failed", Err: err}
	}
	if count > maxArtifactBytes {
		return domain.RawArtifact{}, &Error{Op: "capture official PDF", Code: "artifact_too_large", Err: fmt.Errorf("maximum is %d bytes", maxArtifactBytes)}
	}
	return domain.RawArtifact{
		Filename: filename, ContentType: contentType,
		CapturedAt: capturedAt.UTC().Format(time.RFC3339), ByteLength: count,
		SHA256: hex.EncodeToString(hash.Sum(nil)),
	}, nil
}

func DecodeSourceRecord(data []byte) (SourceRecord, error) {
	if len(data) == 0 || len(data) > maxSourceRecordBytes || !utf8.Valid(data) {
		return SourceRecord{}, &Error{Op: "decode official PDF source record", Code: "schema_drift", Err: errors.New("source record is not valid UTF-8")}
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return SourceRecord{}, &Error{Op: "decode official PDF source record", Code: "schema_drift", Err: err}
	}
	var source SourceRecord
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&source); err != nil {
		return SourceRecord{}, &Error{Op: "decode official PDF source record", Code: "schema_drift", Err: err}
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return SourceRecord{}, &Error{Op: "decode official PDF source record", Code: "schema_drift", Err: errors.New("multiple JSON values")}
	}
	if err := validateSourceRecord(source); err != nil {
		return SourceRecord{}, err
	}
	return source, nil
}

func Parse(config ParseConfig, artifact domain.RawArtifact, transcription []byte) (domain.CandidateSchedule, error) {
	if err := validateSourceRecord(config.Source); err != nil {
		return domain.CandidateSchedule{}, err
	}
	if config.Mosque.ID != expectedMosqueID || config.Mosque.Locality != expectedLocality {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "source_scope_mismatch", Err: errors.New("adapter is bound to the configured Ulyanovsk pilot mosque")}
	}
	if config.Source.Status != "testing" && config.Source.Status != "active" {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "source_not_enabled", Err: errors.New("source status must be testing or active")}
	}
	if config.ExpectedRawSHA256 != expectedArtifactSHA || artifact.SHA256 != config.ExpectedRawSHA256 {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "raw_checksum_mismatch", Err: errors.New("artifact and configured checksum must match the adapter's pinned source")}
	}
	if artifact.Filename != expectedFilename || artifact.ContentType != "application/pdf" || artifact.ByteLength != expectedArtifactLen {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "invalid_artifact", Err: errors.New("captured PDF filename, content type or byte length does not match the pinned source")}
	}
	if capturedAt, err := time.Parse(time.RFC3339, artifact.CapturedAt); err != nil || capturedAt.IsZero() {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "invalid_artifact", Err: errors.New("captured PDF requires a valid RFC3339 capture time")}
	}
	if len(transcription) > maxTranscriptBytes {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "transcription_too_large", Err: fmt.Errorf("maximum is %d bytes", maxTranscriptBytes)}
	}
	records, err := decodeCSV(transcription)
	if err != nil {
		return domain.CandidateSchedule{}, err
	}
	days := make([]domain.CandidatePrayerDay, 0, len(records)-1)
	for index, record := range records[1:] {
		day, err := parseDay(record)
		if err != nil {
			return domain.CandidateSchedule{}, &Error{Op: fmt.Sprintf("parse official PDF row %d", index+2), Code: "invalid_field", Err: err}
		}
		days = append(days, day)
	}
	if err := validateAnnualScopeAndMarkers(days); err != nil {
		return domain.CandidateSchedule{}, err
	}
	transcriptionHash := sha256.Sum256(transcription)
	candidate := domain.CandidateSchedule{
		DataClassification: config.DataClassification,
		Mosque:             config.Mosque,
		Source: domain.CandidateSource{
			SourceID: config.Source.SourceID, Kind: config.Source.Kind,
			AuthorityName: config.Source.AuthorityName, AuthorityBranch: config.Source.AuthorityBranch,
			GeographicScope: config.Source.GeographicScope.Description, CanonicalURL: config.Source.CanonicalURL,
			PermissionStatus: config.Source.Permission.Status, LicenseReference: config.Source.Permission.LicenseReference,
			Attribution: config.Source.Permission.AttributionText, ApprovalRequired: config.Source.Validation.ApprovalRequired,
			MaxDeltaMinutes: config.Source.Validation.MaxAutomaticDeltaMinutes, MinimumCoverageDays: config.Source.Validation.MinimumCoverageDays,
		},
		Artifact: artifact, TranscriptionSHA256: hex.EncodeToString(transcriptionHash[:]),
		ParserVersion: ParserVersion, Days: days, Status: domain.CandidateNeedsReview,
		Coverage: domain.DateRange{From: days[0].Date, To: days[len(days)-1].Date},
	}
	candidate.Validation = controlled.ValidateCandidate(controlled.ValidationConfig{
		SourceCountryCode: config.Source.GeographicScope.CountryCode,
		SourceTimezone:    config.Source.TimezonePolicy.IANATimezone,
		SourceStatus:      config.Source.Status,
	}, candidate)
	if len(candidate.Validation.Errors) > 0 {
		candidate.Status = domain.CandidateValidationFailed
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		return domain.CandidateSchedule{}, &Error{Op: "parse official PDF transcription", Code: "normalization_failed", Err: err}
	}
	return candidate, nil
}

func decodeCSV(data []byte) ([][]string, error) {
	if !utf8.Valid(data) {
		return nil, &Error{Op: "decode official PDF transcription", Code: "schema_drift", Err: errors.New("transcription is not valid UTF-8")}
	}
	reader := csv.NewReader(bytes.NewReader(data))
	reader.FieldsPerRecord = len(expectedCSVHeader)
	records, err := reader.ReadAll()
	if err != nil {
		return nil, &Error{Op: "decode official PDF transcription", Code: "schema_drift", Err: err}
	}
	if len(records) < 2 {
		return nil, &Error{Op: "decode official PDF transcription", Code: "empty_schedule", Err: errors.New("header and rows are required")}
	}
	for index, expected := range expectedCSVHeader {
		if records[0][index] != expected {
			return nil, &Error{Op: "decode official PDF transcription", Code: "schema_drift", Err: fmt.Errorf("column %d is %q, want %q", index+1, records[0][index], expected)}
		}
	}
	return records, nil
}

func parseDay(record []string) (domain.CandidatePrayerDay, error) {
	flags := []string(nil)
	if record[10] != "" {
		flags = strings.Split(record[10], ";")
	}
	return domain.CandidatePrayerDay{
		PrayerDay: domain.PrayerDay{
			Date: record[0], Fajr: record[1], Sunrise: record[3], Dhuhr: record[5],
			Asr: record[7], Maghrib: record[8], Isha: record[9], Flags: flags,
		},
		RecommendedFajr: record[2], Zenith: record[4], DhuhrCongregation: record[6],
	}, nil
}

func validateAnnualScopeAndMarkers(days []domain.CandidatePrayerDay) error {
	if len(days) != 365 || days[0].Date != "2026-01-01" || days[len(days)-1].Date != "2026-12-31" {
		return &Error{Op: "validate official PDF transcription", Code: "annual_scope_mismatch", Err: errors.New("adapter requires every day of Gregorian 2026")}
	}
	expected := map[string]string{
		"2026-05-10": "source_summer_calculation_start_isha",
		"2026-05-11": "source_summer_calculation_start_fajr",
		"2026-08-02": "source_summer_calculation_end_isha",
		"2026-08-03": "source_summer_calculation_end_fajr",
	}
	for _, day := range days {
		want := expected[day.Date]
		found := ""
		for _, flag := range day.Flags {
			if strings.HasPrefix(flag, "source_summer_calculation_") {
				if found != "" {
					return &Error{Op: "validate official PDF transcription", Code: "seasonal_rule_mismatch", Err: fmt.Errorf("%s has multiple summer transition markers", day.Date)}
				}
				found = flag
			}
		}
		if found != want {
			return &Error{Op: "validate official PDF transcription", Code: "seasonal_rule_mismatch", Err: fmt.Errorf("%s marker is %q, want %q", day.Date, found, want)}
		}
	}
	return nil
}

func validateSourceRecord(source SourceRecord) error {
	if source.SourceID != expectedSourceID || source.AuthorityName == "" || source.GeographicScope.Description == "" {
		return &Error{Op: "validate official PDF source record", Code: "invalid_source_record", Err: errors.New("expected source identity, authority and scope are required")}
	}
	if source.Kind != domain.ProviderKindOfficialFile || source.Retrieval.Mode != "manual_upload" {
		return &Error{Op: "validate official PDF source record", Code: "unsupported_source", Err: errors.New("adapter requires official_file/manual_upload")}
	}
	if source.GeographicScope.CountryCode != expectedCountryCode || !countryCode.MatchString(source.GeographicScope.CountryCode) || !contains(source.GeographicScope.LocalityIDs, "ulyanovsk") {
		return &Error{Op: "validate official PDF source record", Code: "invalid_source_scope", Err: errors.New("adapter is scoped only to Ulyanovsk, RU")}
	}
	if source.TimezonePolicy.Mode != "fixed_iana" || source.TimezonePolicy.IANATimezone != expectedTimezone {
		return &Error{Op: "validate official PDF source record", Code: "invalid_timezone_policy", Err: errors.New("adapter requires Europe/Ulyanovsk")}
	}
	if _, err := time.LoadLocation(source.TimezonePolicy.IANATimezone); err != nil {
		return &Error{Op: "validate official PDF source record", Code: "invalid_timezone_policy", Err: err}
	}
	if source.Retrieval.Cadence != "annual" || source.Retrieval.StaleAfterHours < 1 || source.Retrieval.StaleAfterHours > 17520 {
		return &Error{Op: "validate official PDF source record", Code: "invalid_retrieval_policy", Err: errors.New("annual cadence and bounded stale threshold are required")}
	}
	if source.Permission.Status != "granted" || !source.Validation.ApprovalRequired || source.Validation.MinimumCoverageDays != 365 || source.Validation.MaxAutomaticDeltaMinutes < 0 || source.Validation.MaxAutomaticDeltaMinutes > 180 {
		return &Error{Op: "validate official PDF source record", Code: "invalid_source_record", Err: errors.New("granted project use, approval and 365-day validation are required")}
	}
	if source.Status != "testing" && source.Status != "active" && source.Status != "draft" && source.Status != "suspended" && source.Status != "retired" {
		return &Error{Op: "validate official PDF source record", Code: "invalid_source_record", Err: errors.New("invalid source status")}
	}
	parsed, err := url.ParseRequestURI(source.CanonicalURL)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" {
		return &Error{Op: "validate official PDF source record", Code: "invalid_canonical_url", Err: errors.New("canonical URL must be absolute HTTPS")}
	}
	return nil
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}
