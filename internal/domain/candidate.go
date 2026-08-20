package domain

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
)

// CandidateStatus deliberately has no approved or published value. Approval
// and publication are separate records and cannot be inferred by a provider.
type CandidateStatus string

const (
	CandidateValidationFailed CandidateStatus = "validation_failed"
	CandidateNeedsReview      CandidateStatus = "needs_review"
)

type RawArtifact struct {
	Filename    string `json:"filename"`
	ContentType string `json:"content_type"`
	CapturedAt  string `json:"captured_at"`
	ByteLength  int64  `json:"byte_length"`
	SHA256      string `json:"sha256"`
}

type CandidateSource struct {
	SourceID            string       `json:"source_id"`
	Kind                ProviderKind `json:"kind"`
	AuthorityName       string       `json:"authority_name"`
	AuthorityBranch     string       `json:"authority_branch,omitempty"`
	GeographicScope     string       `json:"geographic_scope"`
	CanonicalURL        string       `json:"canonical_url,omitempty"`
	PermissionStatus    string       `json:"permission_status"`
	LicenseReference    string       `json:"license_reference,omitempty"`
	Attribution         string       `json:"attribution,omitempty"`
	ApprovalRequired    bool         `json:"approval_required"`
	MaxDeltaMinutes     int          `json:"max_delta_minutes"`
	MinimumCoverageDays int          `json:"minimum_coverage_days"`
}

type CandidatePrayerDay struct {
	PrayerDay
	RecommendedFajr   string `json:"recommended_fajr,omitempty"`
	Zenith            string `json:"zenith,omitempty"`
	DhuhrCongregation string `json:"dhuhr_congregation,omitempty"`
	HijriDay          int    `json:"hijri_day,omitempty"`
	HijriMonth        string `json:"hijri_month,omitempty"`
	HijriYear         int    `json:"hijri_year,omitempty"`
}

type CandidateDiagnostic struct {
	Path    string `json:"path"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

type CandidateValidationReport struct {
	Errors   []CandidateDiagnostic `json:"errors"`
	Warnings []CandidateDiagnostic `json:"warnings"`
}

type CandidateSchedule struct {
	ID                  string                     `json:"candidate_id"`
	DataClassification  DataClassification         `json:"data_classification"`
	Mosque              Mosque                     `json:"mosque"`
	Source              CandidateSource            `json:"source"`
	Artifact            RawArtifact                `json:"artifact"`
	TranscriptionSHA256 string                     `json:"transcription_sha256"`
	NormalizedSHA256    string                     `json:"normalized_sha256"`
	ParserVersion       string                     `json:"parser_version"`
	Coverage            DateRange                  `json:"coverage"`
	Days                []CandidatePrayerDay       `json:"days"`
	Components          []CandidateSourceComponent `json:"source_components,omitempty"`
	Status              CandidateStatus            `json:"status"`
	Validation          CandidateValidationReport  `json:"validation"`
}

// CandidateSourceComponent preserves every immutable input that contributed
// fields to a composed candidate. The effective policy artifact remains the
// candidate's primary Artifact; these component bindings make the derived
// schedule traceable to each retained raw source without mutating any input.
type CandidateSourceComponent struct {
	Role                string          `json:"role"`
	CandidateID         string          `json:"candidate_id"`
	Source              CandidateSource `json:"source"`
	RawArtifact         RawArtifact     `json:"raw_artifact"`
	TranscriptionSHA256 string          `json:"transcription_sha256"`
	NormalizedSHA256    string          `json:"normalized_sha256"`
	ParserVersion       string          `json:"parser_version"`
	Effective           DateRange       `json:"effective"`
	AppliedFields       []string        `json:"applied_fields"`
}

type ApprovalDecisionValue string

const (
	ApprovalApproved ApprovalDecisionValue = "approved"
	ApprovalRejected ApprovalDecisionValue = "rejected"
)

// ApprovalDecision binds a human decision to the immutable candidate inputs
// and deterministic diff. Changing any one of them invalidates the decision.
type ApprovalDecision struct {
	ID                       string                `json:"approval_id"`
	Decision                 ApprovalDecisionValue `json:"decision"`
	Actor                    string                `json:"actor"`
	ApprovedAt               string                `json:"approved_at"`
	Scope                    string                `json:"scope"`
	Reason                   string                `json:"reason,omitempty"`
	CandidateID              string                `json:"candidate_id"`
	RawSHA256                string                `json:"raw_sha256"`
	TranscriptionSHA256      string                `json:"transcription_sha256"`
	NormalizedSHA256         string                `json:"normalized_sha256"`
	DiffSHA256               string                `json:"diff_sha256"`
	ParserVersion            string                `json:"parser_version"`
	PrayerPolicySHA256       string                `json:"prayer_policy_sha256,omitempty"`
	AcknowledgedWarningCodes []string              `json:"acknowledged_warning_codes,omitempty"`
}

// CandidateNormalizedSHA256 fingerprints every normalized field that may
// affect publication. Workflow status, diagnostics, IDs and the fingerprint
// itself are deliberately excluded.
func CandidateNormalizedSHA256(candidate CandidateSchedule) (string, error) {
	projection := struct {
		DataClassification  DataClassification
		Mosque              Mosque
		Source              CandidateSource
		Artifact            RawArtifact
		TranscriptionSHA256 string
		ParserVersion       string
		Coverage            DateRange
		Days                []CandidatePrayerDay
		Components          []CandidateSourceComponent `json:"Components,omitempty"`
	}{
		candidate.DataClassification,
		candidate.Mosque,
		candidate.Source,
		candidate.Artifact,
		candidate.TranscriptionSHA256,
		candidate.ParserVersion,
		candidate.Coverage,
		candidate.Days,
		candidate.Components,
	}
	data, err := json.Marshal(projection)
	if err != nil {
		return "", err
	}
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:]), nil
}

func FinalizeCandidateIdentity(candidate *CandidateSchedule) error {
	fingerprint, err := CandidateNormalizedSHA256(*candidate)
	if err != nil {
		return err
	}
	candidate.NormalizedSHA256 = fingerprint
	candidate.ID = "candidate-" + fingerprint[:32]
	return nil
}
