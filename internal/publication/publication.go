package publication

import (
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

type PublishRequest struct {
	Previous           *domain.CandidateSchedule `json:"previous_candidate,omitempty"`
	Candidate          domain.CandidateSchedule  `json:"candidate"`
	Diff               DiffReport                `json:"diff"`
	Approval           domain.ApprovalDecision   `json:"approval"`
	ApprovalEvidence   *ApprovalEvidence         `json:"approval_evidence,omitempty"`
	MosquePrayerPolicy *MosquePrayerPolicy       `json:"mosque_prayer_policy,omitempty"`
	SnapshotID         string                    `json:"snapshot_id"`
	GeneratedAt        time.Time                 `json:"generated_at"`
	SigningKeyID       string                    `json:"signing_key_id"`
}

type ApprovalEvidence struct {
	ReceiptSHA256     string `json:"receipt_sha256"`
	TrustRevision     uint64 `json:"trust_revision"`
	TrustBundleSHA256 string `json:"trust_bundle_sha256"`
	ApprovalKeyID     string `json:"approval_key_id"`
}

type Result struct {
	Snapshot domain.Snapshot
	JSON     []byte
}

// Signer is the only production signing boundary. Implementations keep private
// material in a KMS, HSM or isolated signer and return only an Ed25519
// signature for each exact domain-separated payload supplied here.
type Signer interface {
	KeyID() string
	Sign(context.Context, []byte) ([]byte, error)
}

type AuditMetadata struct {
	Environment           string
	SignerIdentity        string
	SignedAt              time.Time
	PublishedAt           time.Time
	PreviousReceiptSHA256 string
	ChainGenesisReason    string
	TrustBundleRevision   uint64
	TrustBundleSHA256     string
	SigningRequestSHA256  string
	SigningRequestID      string
	AttestationSignature  string
}

type AuditReceipt struct {
	SchemaVersion         string `json:"schema_version"`
	Environment           string `json:"environment"`
	Event                 string `json:"event"`
	RequestID             string `json:"request_id"`
	SnapshotID            string `json:"snapshot_id"`
	SnapshotGeneratedAt   string `json:"snapshot_generated_at"`
	SnapshotSHA256        string `json:"snapshot_sha256"`
	CanonicalSHA256       string `json:"canonical_sha256"`
	SigningKeyID          string `json:"signing_key_id"`
	CandidateID           string `json:"candidate_id"`
	RawSHA256             string `json:"raw_sha256"`
	TranscriptionSHA256   string `json:"transcription_sha256"`
	NormalizedSHA256      string `json:"normalized_sha256"`
	DiffSHA256            string `json:"diff_sha256"`
	ParserVersion         string `json:"parser_version"`
	ApprovalID            string `json:"approval_id"`
	ApproverIdentity      string `json:"approver_identity"`
	ApprovedAt            string `json:"approved_at"`
	ApprovalScope         string `json:"approval_scope"`
	PrayerPolicySHA256    string `json:"prayer_policy_sha256,omitempty"`
	ApprovalReceiptSHA256 string `json:"approval_receipt_sha256,omitempty"`
	ApprovalTrustRevision uint64 `json:"approval_trust_revision,omitempty"`
	ApprovalTrustSHA256   string `json:"approval_trust_sha256,omitempty"`
	ApprovalKeyID         string `json:"approval_key_id,omitempty"`
	SignerIdentity        string `json:"signer_identity"`
	PublishedAt           string `json:"published_at"`
	SignedAt              string `json:"signed_at"`
	TrustBundleRevision   uint64 `json:"trust_bundle_revision"`
	TrustBundleSHA256     string `json:"trust_bundle_sha256"`
	SigningRequestSHA256  string `json:"signing_request_sha256"`
	SigningRequestID      string `json:"signing_request_id"`
	AttestationSignature  string `json:"attestation_ed25519_base64"`
	PreviousReceiptSHA256 string `json:"previous_receipt_sha256,omitempty"`
	ChainGenesisReason    string `json:"chain_genesis_reason,omitempty"`
	ReceiptSHA256         string `json:"receipt_sha256"`
}

func Publish(request PublishRequest, privateKey ed25519.PrivateKey) (Result, error) {
	if err := validatePublishRequest(request); err != nil {
		return Result{}, err
	}
	if request.Candidate.DataClassification == domain.DataClassificationProduction {
		return Result{}, newError("publish snapshot", "production_signer_required", errors.New("production publication requires a protected signer"))
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

// PublishWithSigner publishes through a protected signer and independently
// verifies its output against the public trust policy before returning bytes.
func PublishWithSigner(ctx context.Context, request PublishRequest, signer Signer, policy *trust.Policy, audit AuditMetadata) (Result, AuditReceipt, error) {
	if ctx == nil || signer == nil || policy == nil {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "signer_required", errors.New("context, signer and trust policy are required"))
	}
	if signer.KeyID() != request.SigningKeyID {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "signer_key_mismatch", errors.New("signer key ID does not match publication request"))
	}
	if audit.SignerIdentity == "" || len(audit.SignerIdentity) > 240 || strings.TrimSpace(audit.SignerIdentity) != audit.SignerIdentity || audit.PublishedAt.IsZero() || audit.PublishedAt.Before(request.GeneratedAt) {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "audit_metadata_invalid", errors.New("signer identity and publication time are required"))
	}
	if strings.EqualFold(strings.TrimSpace(audit.SignerIdentity), strings.TrimSpace(request.Approval.Actor)) {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "separation_of_duties_required", errors.New("approver and signer identities must differ"))
	}
	if audit.SignedAt.IsZero() {
		audit.SignedAt = audit.PublishedAt
	}
	if audit.SignedAt.Before(request.GeneratedAt) || audit.PublishedAt.Before(audit.SignedAt) {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "audit_metadata_invalid", errors.New("signing/publication times are invalid"))
	}
	if !validChainMetadata(audit.PreviousReceiptSHA256, audit.ChainGenesisReason) {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "audit_metadata_invalid", errors.New("exactly one valid audit predecessor or genesis reason is required"))
	}
	prepared, err := PrepareSigning(request, policy)
	if err != nil {
		return Result{}, AuditReceipt{}, err
	}
	canonical, err := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	if err != nil {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "signing_request_invalid", err)
	}
	snapshotSignature, err := signer.Sign(ctx, append([]byte(nil), canonical...))
	if err != nil {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "signer_failed", err)
	}
	response := SigningResponse{
		SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: prepared.SigningKeyID,
		SignatureEd25519Base64: base64.StdEncoding.EncodeToString(snapshotSignature),
		SignerIdentity:         audit.SignerIdentity, SignedAt: audit.SignedAt.UTC().Format(time.RFC3339),
		PublishedAt: audit.PublishedAt.UTC().Format(time.RFC3339), PreviousReceiptSHA256: audit.PreviousReceiptSHA256,
		ChainGenesisReason: audit.ChainGenesisReason,
	}
	attestationPayload, requestSHA256, err := BuildAttestationPayload(prepared, response)
	if err != nil {
		return Result{}, AuditReceipt{}, err
	}
	attestationSignature, err := signer.Sign(ctx, attestationPayload)
	if err != nil {
		return Result{}, AuditReceipt{}, newError("publish snapshot", "signer_failed", err)
	}
	response.SigningRequestSHA256 = requestSHA256
	response.AttestationEd25519Base64 = base64.StdEncoding.EncodeToString(attestationSignature)
	return FinalizeSigning(request, prepared, response, policy, audit)
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
	if snapshot.DataClassification == domain.DataClassificationProduction {
		return newError("verify snapshot", "production_trust_policy_required", errors.New("production snapshots require lifecycle-aware trust policy"))
	}
	return nil
}

func VerifyWithTrust(data []byte, policy *trust.Policy) error {
	if policy == nil || !policy.TransitionValidated() {
		return newError("verify snapshot", "trust_transition_required", errors.New("trust bundle transition has not been validated"))
	}
	if !utf8.Valid(data) {
		return newError("verify snapshot", "snapshot_decode_failed", errors.New("snapshot is not valid UTF-8"))
	}
	var envelope struct {
		GeneratedAt string                   `json:"generated_at"`
		Integrity   domain.IntegrityMetadata `json:"integrity"`
	}
	if err := json.Unmarshal(data, &envelope); err != nil {
		return newError("verify snapshot", "snapshot_decode_failed", err)
	}
	generatedAt, err := time.Parse(time.RFC3339, envelope.GeneratedAt)
	if err != nil {
		return newError("verify snapshot", "snapshot_generated_at_invalid", errors.New("generated_at must be RFC3339"))
	}
	publicKey, err := policy.KeyForVerification(envelope.Integrity.SigningKeyID, generatedAt)
	if err != nil {
		return newError("verify snapshot", "trust_policy_rejected", err)
	}
	if err := verifyWithPublicKey(data, envelope.Integrity, publicKey); err != nil {
		return err
	}
	snapshot, err := domain.DecodeSnapshot(data)
	if err != nil {
		return newError("verify snapshot", "snapshot_decode_failed", err)
	}
	if err := snapshot.Validate(); err != nil {
		return newError("verify snapshot", "snapshot_invalid", err)
	}
	if snapshot.DataClassification == domain.DataClassificationProduction && policy.Environment() != "production" {
		return newError("verify snapshot", "trust_environment_mismatch", errors.New("production snapshot requires production trust policy"))
	}
	if snapshot.DataClassification == domain.DataClassificationProduction && !policy.EnvironmentSeparationValidated() {
		return newError("verify snapshot", "trust_environment_separation_required", errors.New("test, staging and production key separation has not been validated"))
	}
	if snapshot.DataClassification != domain.DataClassificationProduction && policy.Environment() == "production" {
		return newError("verify snapshot", "trust_environment_mismatch", errors.New("synthetic snapshot cannot use production trust policy"))
	}
	return nil
}

func verifyWithPublicKey(data []byte, integrity domain.IntegrityMetadata, publicKey ed25519.PublicKey) error {
	if len(publicKey) != ed25519.PublicKeySize {
		return newError("verify snapshot", "invalid_public_key", fmt.Errorf("must be %d bytes", ed25519.PublicKeySize))
	}
	canonical, err := canonicalPayload(data)
	if err != nil {
		return newError("verify snapshot", "canonicalization_failed", err)
	}
	hash := sha256.Sum256(canonical)
	if hex.EncodeToString(hash[:]) != integrity.CanonicalSHA256 {
		return newError("verify snapshot", "canonical_hash_mismatch", errors.New("canonical payload hash does not match envelope"))
	}
	signature, err := base64.StdEncoding.DecodeString(integrity.SignatureEd25519Base64)
	if err != nil {
		return newError("verify snapshot", "signature_encoding_invalid", err)
	}
	if !ed25519.Verify(publicKey, canonical, signature) {
		return newError("verify snapshot", "signature_invalid", errors.New("Ed25519 verification failed"))
	}
	return nil
}

func buildAuditReceipt(request PublishRequest, result Result, audit AuditMetadata) (AuditReceipt, error) {
	snapshotHash := sha256.Sum256(result.JSON)
	receipt := AuditReceipt{
		SchemaVersion: "1.0", Environment: audit.Environment, Event: "snapshot.published",
		RequestID:  publicationRequestID(request.Candidate.ID, request.Diff.SHA256, request.Approval.ID, request.SnapshotID, request.SigningKeyID),
		SnapshotID: request.SnapshotID, SnapshotGeneratedAt: result.Snapshot.GeneratedAt, SnapshotSHA256: hex.EncodeToString(snapshotHash[:]), CanonicalSHA256: result.Snapshot.Integrity.CanonicalSHA256,
		SigningKeyID: request.SigningKeyID, CandidateID: request.Candidate.ID, RawSHA256: request.Candidate.Artifact.SHA256,
		TranscriptionSHA256: request.Candidate.TranscriptionSHA256, NormalizedSHA256: request.Candidate.NormalizedSHA256,
		DiffSHA256: request.Diff.SHA256, ParserVersion: request.Candidate.ParserVersion,
		ApprovalID: request.Approval.ID, ApproverIdentity: request.Approval.Actor, ApprovedAt: request.Approval.ApprovedAt, ApprovalScope: request.Approval.Scope,
		PrayerPolicySHA256: request.Approval.PrayerPolicySHA256,
		SignerIdentity:     audit.SignerIdentity, SignedAt: audit.SignedAt.UTC().Format(time.RFC3339), PublishedAt: audit.PublishedAt.UTC().Format(time.RFC3339), PreviousReceiptSHA256: audit.PreviousReceiptSHA256,
		TrustBundleRevision: audit.TrustBundleRevision, TrustBundleSHA256: audit.TrustBundleSHA256,
		SigningRequestSHA256: audit.SigningRequestSHA256, AttestationSignature: audit.AttestationSignature,
		SigningRequestID:   audit.SigningRequestID,
		ChainGenesisReason: audit.ChainGenesisReason,
	}
	if request.ApprovalEvidence != nil {
		receipt.ApprovalReceiptSHA256 = request.ApprovalEvidence.ReceiptSHA256
		receipt.ApprovalTrustRevision = request.ApprovalEvidence.TrustRevision
		receipt.ApprovalTrustSHA256 = request.ApprovalEvidence.TrustBundleSHA256
		receipt.ApprovalKeyID = request.ApprovalEvidence.ApprovalKeyID
	}
	encoded, err := json.Marshal(receipt)
	if err != nil {
		return AuditReceipt{}, newError("publish snapshot", "audit_receipt_failed", err)
	}
	hash := sha256.Sum256(encoded)
	receipt.ReceiptSHA256 = hex.EncodeToString(hash[:])
	return receipt, nil
}

func publicationRequestID(candidateID, diffSHA256, approvalID, snapshotID, signingKeyID string) string {
	requestBinding := sha256.Sum256([]byte(candidateID + "\x00" + diffSHA256 + "\x00" + approvalID + "\x00" + snapshotID + "\x00" + signingKeyID))
	return "publication-" + hex.EncodeToString(requestBinding[:16])
}

func validSHA256(value string) bool {
	if len(value) != sha256.Size*2 {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func validChainMetadata(previousSHA256, genesisReason string) bool {
	if (previousSHA256 == "") == (genesisReason == "") {
		return false
	}
	if previousSHA256 != "" {
		return validSHA256(previousSHA256)
	}
	return len(genesisReason) >= 1 && len(genesisReason) <= 240 && strings.TrimSpace(genesisReason) == genesisReason
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
	if approval.Decision != domain.ApprovalApproved || approval.ID == "" || approval.Actor == "" || strings.TrimSpace(approval.Actor) != approval.Actor || approval.ApprovedAt == "" || approval.Scope == "" {
		return newError("publish snapshot", "approval_required", errors.New("an explicit approved decision with actor, time and scope is required"))
	}
	approvedAt, err := time.Parse(time.RFC3339, approval.ApprovedAt)
	if err != nil || approvedAt.UTC().Format(time.RFC3339) != approval.ApprovedAt || (!request.GeneratedAt.IsZero() && request.GeneratedAt.Before(approvedAt)) {
		return newError("publish snapshot", "approval_time_invalid", errors.New("approval time must be canonical UTC and not after snapshot generation"))
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
	if err := validateMosquePrayerPolicyBinding(request); err != nil {
		return err
	}
	if request.Candidate.DataClassification == domain.DataClassificationProduction {
		if request.MosquePrayerPolicy == nil || request.ApprovalEvidence == nil || !validSHA256(request.ApprovalEvidence.ReceiptSHA256) || request.ApprovalEvidence.TrustRevision == 0 || !validSHA256(request.ApprovalEvidence.TrustBundleSHA256) || request.ApprovalEvidence.ApprovalKeyID == "" {
			return newError("publish snapshot", "authenticated_approval_required", errors.New("production publication requires authenticated approval evidence"))
		}
	} else if request.ApprovalEvidence != nil {
		return newError("publish snapshot", "approval_evidence_invalid", errors.New("synthetic publication cannot carry production approval evidence"))
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
		if request.MosquePrayerPolicy != nil && request.MosquePrayerPolicy.DhuhrAdhanSource == DhuhrAdhanFromCongregation {
			if day.DhuhrCongregation == "" {
				return domain.Snapshot{}, newError("publish snapshot", "prayer_policy_invalid", fmt.Errorf("day %s has no approved Dhuhr congregation value", day.Date))
			}
			prayerDays[index].Dhuhr = day.DhuhrCongregation
		}
	}
	var iqamahRules []domain.IqamahRule
	var jumuahSessions []domain.JumuahSession
	if request.MosquePrayerPolicy != nil {
		iqamahRules = append([]domain.IqamahRule(nil), request.MosquePrayerPolicy.IqamahRules...)
		jumuahSessions = append([]domain.JumuahSession(nil), request.MosquePrayerPolicy.JumuahSessions...)
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
		Coverage:       candidate.Coverage,
		PrayerDays:     prayerDays,
		IqamahRules:    iqamahRules,
		JumuahSessions: jumuahSessions,
		Integrity: domain.IntegrityMetadata{
			CanonicalSHA256:        "0000000000000000000000000000000000000000000000000000000000000000",
			SigningKeyID:           request.SigningKeyID,
			SignatureEd25519Base64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
		},
	}, nil
}
