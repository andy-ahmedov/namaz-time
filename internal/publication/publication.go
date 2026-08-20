package publication

import (
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

type PublishRequest struct {
	Previous     *domain.CandidateSchedule
	Candidate    domain.CandidateSchedule
	Diff         DiffReport
	Approval     domain.ApprovalDecision
	SnapshotID   string
	GeneratedAt  time.Time
	SigningKeyID string
}

type Result struct {
	Snapshot domain.Snapshot
	JSON     []byte
}

func Publish(request PublishRequest, privateKey ed25519.PrivateKey) (Result, error) {
	if err := validatePublishRequest(request); err != nil {
		return Result{}, err
	}
	if len(privateKey) != ed25519.PrivateKeySize {
		return Result{}, newError("publish snapshot", "invalid_private_key", fmt.Errorf("must be %d bytes", ed25519.PrivateKeySize))
	}
	snapshot, err := buildSnapshot(request)
	if err != nil {
		return Result{}, err
	}
	unsignedJSON, err := json.Marshal(snapshot)
	if err != nil {
		return Result{}, newError("publish snapshot", "encode_failed", err)
	}
	canonical, err := canonicalPayload(unsignedJSON)
	if err != nil {
		return Result{}, newError("publish snapshot", "canonicalization_failed", err)
	}
	hash := sha256.Sum256(canonical)
	snapshot.Integrity.CanonicalSHA256 = hex.EncodeToString(hash[:])
	snapshot.Integrity.SignatureEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical))
	if err := snapshot.Validate(); err != nil {
		return Result{}, newError("publish snapshot", "snapshot_invalid", err)
	}
	data, err := json.MarshalIndent(snapshot, "", "  ")
	if err != nil {
		return Result{}, newError("publish snapshot", "encode_failed", err)
	}
	data = append(data, '\n')
	return Result{Snapshot: snapshot, JSON: data}, nil
}

func Verify(data []byte, publicKeys map[string]ed25519.PublicKey) error {
	if !utf8.Valid(data) {
		return newError("verify snapshot", "snapshot_decode_failed", errors.New("snapshot is not valid UTF-8"))
	}
	var envelope struct {
		Integrity domain.IntegrityMetadata `json:"integrity"`
	}
	if err := json.Unmarshal(data, &envelope); err != nil {
		return newError("verify snapshot", "snapshot_decode_failed", err)
	}
	publicKey, exists := publicKeys[envelope.Integrity.SigningKeyID]
	if !exists {
		return newError("verify snapshot", "unknown_signing_key", errors.New("signing key ID is not trusted"))
	}
	if len(publicKey) != ed25519.PublicKeySize {
		return newError("verify snapshot", "invalid_public_key", fmt.Errorf("must be %d bytes", ed25519.PublicKeySize))
	}
	canonical, err := canonicalPayload(data)
	if err != nil {
		return newError("verify snapshot", "canonicalization_failed", err)
	}
	hash := sha256.Sum256(canonical)
	if hex.EncodeToString(hash[:]) != envelope.Integrity.CanonicalSHA256 {
		return newError("verify snapshot", "canonical_hash_mismatch", errors.New("canonical payload hash does not match envelope"))
	}
	signature, err := base64.StdEncoding.DecodeString(envelope.Integrity.SignatureEd25519Base64)
	if err != nil {
		return newError("verify snapshot", "signature_encoding_invalid", err)
	}
	if !ed25519.Verify(publicKey, canonical, signature) {
		return newError("verify snapshot", "signature_invalid", errors.New("Ed25519 verification failed"))
	}
	snapshot, err := domain.DecodeSnapshot(data)
	if err != nil {
		return newError("verify snapshot", "snapshot_decode_failed", err)
	}
	if err := snapshot.Validate(); err != nil {
		return newError("verify snapshot", "snapshot_invalid", err)
	}
	return nil
}

func validatePublishRequest(request PublishRequest) error {
	if request.Candidate.Status == domain.CandidateValidationFailed || len(request.Candidate.Validation.Errors) > 0 {
		return newError("publish snapshot", "candidate_invalid", errors.New("candidate contains blocking validation errors"))
	}
	if request.Candidate.Status != domain.CandidateNeedsReview {
		return newError("publish snapshot", "candidate_invalid", errors.New("candidate status must be needs_review"))
	}
	if request.Candidate.Source.PermissionStatus != "granted" {
		return newError("publish snapshot", "permission_not_granted", errors.New("source permission is not granted"))
	}
	if !request.Candidate.Source.ApprovalRequired {
		return newError("publish snapshot", "unsafe_source_policy", errors.New("manual source must require approval"))
	}
	approval := request.Approval
	if approval.Decision != domain.ApprovalApproved || approval.ID == "" || approval.Actor == "" || approval.ApprovedAt == "" || approval.Scope == "" {
		return newError("publish snapshot", "approval_required", errors.New("an explicit approved decision with actor, time and scope is required"))
	}
	acknowledgedWarnings := make(map[string]struct{}, len(approval.AcknowledgedWarningCodes))
	for _, code := range approval.AcknowledgedWarningCodes {
		acknowledgedWarnings[code] = struct{}{}
	}
	for _, warning := range request.Candidate.Validation.Warnings {
		if _, acknowledged := acknowledgedWarnings[warning.Code]; !acknowledged {
			return newError("publish snapshot", "warnings_unacknowledged", fmt.Errorf("warning %s is not acknowledged", warning.Code))
		}
	}
	if approval.CandidateID != request.Candidate.ID ||
		approval.RawSHA256 != request.Candidate.Artifact.SHA256 ||
		approval.TranscriptionSHA256 != request.Candidate.TranscriptionSHA256 ||
		approval.NormalizedSHA256 != request.Candidate.NormalizedSHA256 ||
		approval.DiffSHA256 != request.Diff.SHA256 ||
		approval.ParserVersion != request.Candidate.ParserVersion ||
		request.Diff.CandidateID != request.Candidate.ID ||
		request.Diff.CandidateRawSHA256 != request.Candidate.Artifact.SHA256 ||
		request.Diff.CandidateNormalizedSHA256 != request.Candidate.NormalizedSHA256 {
		return newError("publish snapshot", "approval_binding_mismatch", errors.New("approval/diff does not bind to exact candidate inputs"))
	}
	normalizedSHA256, err := domain.CandidateNormalizedSHA256(request.Candidate)
	if err != nil || normalizedSHA256 != request.Candidate.NormalizedSHA256 {
		return newError("publish snapshot", "candidate_binding_mismatch", errors.New("normalized candidate fingerprint does not match content"))
	}
	recomputedDiff, err := Diff(request.Previous, request.Candidate)
	if err != nil {
		return newError("publish snapshot", "diff_invalid", err)
	}
	if recomputedDiff.SHA256 != request.Diff.SHA256 || diffHash(request.Diff) != request.Diff.SHA256 {
		return newError("publish snapshot", "diff_binding_mismatch", errors.New("diff content does not match the candidate"))
	}
	if request.SnapshotID == "" || request.GeneratedAt.IsZero() || request.SigningKeyID == "" {
		return newError("publish snapshot", "publication_metadata_required", errors.New("snapshot ID, generated time and signing key ID are required"))
	}
	return nil
}

func buildSnapshot(request PublishRequest) (domain.Snapshot, error) {
	candidate := request.Candidate
	prayerDays := make([]domain.PrayerDay, len(candidate.Days))
	for index, day := range candidate.Days {
		prayerDays[index] = day.PrayerDay
	}
	return domain.Snapshot{
		SchemaVersion:      "1.0",
		SnapshotID:         request.SnapshotID,
		DataClassification: candidate.DataClassification,
		GeneratedAt:        request.GeneratedAt.UTC().Format(time.RFC3339),
		Mosque:             candidate.Mosque,
		Source: domain.SourceMetadata{
			SourceID:         candidate.Source.SourceID,
			Kind:             candidate.Source.Kind,
			AuthorityName:    candidate.Source.AuthorityName,
			AuthorityBranch:  candidate.Source.AuthorityBranch,
			GeographicScope:  candidate.Source.GeographicScope,
			CanonicalURL:     candidate.Source.CanonicalURL,
			RetrievedAt:      candidate.Artifact.CapturedAt,
			EffectiveFrom:    candidate.Coverage.From,
			EffectiveTo:      candidate.Coverage.To,
			RawSHA256:        candidate.Artifact.SHA256,
			ParserVersion:    candidate.ParserVersion,
			LicenseReference: candidate.Source.LicenseReference,
			Attribution:      candidate.Source.Attribution,
			Approval: domain.Approval{
				Status: "approved", ID: request.Approval.ID, ApprovedBy: request.Approval.Actor,
				ApprovedAt: request.Approval.ApprovedAt, Scope: request.Approval.Scope, Note: request.Approval.Reason,
			},
		},
		Coverage:   candidate.Coverage,
		PrayerDays: prayerDays,
		Integrity: domain.IntegrityMetadata{
			CanonicalSHA256:        "0000000000000000000000000000000000000000000000000000000000000000",
			SigningKeyID:           request.SigningKeyID,
			SignatureEd25519Base64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
		},
	}, nil
}
