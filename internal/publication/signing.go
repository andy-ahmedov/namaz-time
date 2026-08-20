package publication

import (
	"bytes"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

var (
	publicationRequestIDPattern = regexp.MustCompile(`^publication-[0-9a-f]{32}$`)
	signingRequestIDPattern     = regexp.MustCompile(`^signing-[0-9a-f]{32}$`)
)

const publicationAttestationDomain = "namaz-time/publication-attestation/v1\x00"

// SigningRequest is public, non-secret material suitable for transfer to an
// isolated Ed25519 signer. The signer signs CanonicalPayloadBase64 verbatim.
type SigningRequest struct {
	SchemaVersion          string          `json:"schema_version"`
	RequestID              string          `json:"request_id"`
	Environment            string          `json:"environment"`
	TrustBundleRevision    uint64          `json:"trust_bundle_revision"`
	TrustBundleSHA256      string          `json:"trust_bundle_sha256"`
	SigningKeyID           string          `json:"signing_key_id"`
	CanonicalSHA256        string          `json:"canonical_sha256"`
	CanonicalPayloadBase64 string          `json:"canonical_payload_base64"`
	CandidateID            string          `json:"candidate_id"`
	RawSHA256              string          `json:"raw_sha256"`
	TranscriptionSHA256    string          `json:"transcription_sha256"`
	NormalizedSHA256       string          `json:"normalized_sha256"`
	DiffSHA256             string          `json:"diff_sha256"`
	ParserVersion          string          `json:"parser_version"`
	ApprovalID             string          `json:"approval_id"`
	ApproverIdentity       string          `json:"approver_identity"`
	ApprovedAt             string          `json:"approved_at"`
	ApprovalScope          string          `json:"approval_scope"`
	PrayerPolicySHA256     string          `json:"prayer_policy_sha256,omitempty"`
	ApprovalReceiptSHA256  string          `json:"approval_receipt_sha256,omitempty"`
	ApprovalTrustRevision  uint64          `json:"approval_trust_revision,omitempty"`
	ApprovalTrustSHA256    string          `json:"approval_trust_sha256,omitempty"`
	ApprovalKeyID          string          `json:"approval_key_id,omitempty"`
	Snapshot               domain.Snapshot `json:"unsigned_snapshot"`
}

type SigningResponse struct {
	SchemaVersion            string `json:"schema_version"`
	RequestID                string `json:"request_id"`
	SigningKeyID             string `json:"signing_key_id"`
	SignatureEd25519Base64   string `json:"signature_ed25519_base64"`
	SignerIdentity           string `json:"signer_identity"`
	SignedAt                 string `json:"signed_at"`
	PublishedAt              string `json:"published_at"`
	PreviousReceiptSHA256    string `json:"previous_receipt_sha256,omitempty"`
	ChainGenesisReason       string `json:"chain_genesis_reason,omitempty"`
	SigningRequestSHA256     string `json:"signing_request_sha256"`
	AttestationEd25519Base64 string `json:"attestation_ed25519_base64"`
}

type publicationAttestation struct {
	SchemaVersion         string `json:"schema_version"`
	Environment           string `json:"environment"`
	RequestID             string `json:"request_id"`
	PublicationRequestID  string `json:"publication_request_id"`
	SigningRequestSHA256  string `json:"signing_request_sha256"`
	SnapshotID            string `json:"snapshot_id"`
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
	TrustBundleRevision   uint64 `json:"trust_bundle_revision"`
	TrustBundleSHA256     string `json:"trust_bundle_sha256"`
	SignerIdentity        string `json:"signer_identity"`
	SignedAt              string `json:"signed_at"`
	PublishedAt           string `json:"published_at"`
	PreviousReceiptSHA256 string `json:"previous_receipt_sha256,omitempty"`
	ChainGenesisReason    string `json:"chain_genesis_reason,omitempty"`
}

func PrepareSigning(request PublishRequest, policy *trust.Policy) (SigningRequest, error) {
	if err := validatePublishRequest(request); err != nil {
		return SigningRequest{}, err
	}
	if policy == nil {
		return SigningRequest{}, newError("prepare signing", "trust_policy_required", errors.New("trust policy is required"))
	}
	if !policy.TransitionValidated() {
		return SigningRequest{}, newError("prepare signing", "trust_transition_required", errors.New("trust bundle transition has not been validated"))
	}
	if request.Candidate.DataClassification == domain.DataClassificationProduction && policy.Environment() != "production" {
		return SigningRequest{}, newError("prepare signing", "trust_environment_mismatch", errors.New("production candidate requires production trust policy"))
	}
	if request.Candidate.DataClassification == domain.DataClassificationProduction && !policy.EnvironmentSeparationValidated() {
		return SigningRequest{}, newError("prepare signing", "trust_environment_separation_required", errors.New("test, staging and production key separation has not been validated"))
	}
	if request.Candidate.DataClassification != domain.DataClassificationProduction && policy.Environment() == "production" {
		return SigningRequest{}, newError("prepare signing", "trust_environment_mismatch", errors.New("synthetic candidate cannot use production trust policy"))
	}
	if _, err := policy.KeyForSigning(request.SigningKeyID, request.GeneratedAt); err != nil {
		return SigningRequest{}, newError("prepare signing", "signing_key_not_active", err)
	}
	snapshot, err := buildSnapshot(request)
	if err != nil {
		return SigningRequest{}, err
	}
	unsigned, err := json.Marshal(snapshot)
	if err != nil {
		return SigningRequest{}, newError("prepare signing", "encode_failed", err)
	}
	canonical, err := canonicalPayload(unsigned)
	if err != nil {
		return SigningRequest{}, newError("prepare signing", "canonicalization_failed", err)
	}
	hash := sha256.Sum256(canonical)
	snapshot.Integrity.CanonicalSHA256 = hex.EncodeToString(hash[:])
	binding := sha256.Sum256([]byte(
		request.Candidate.ID + "\x00" + request.Candidate.Artifact.SHA256 + "\x00" + request.Candidate.TranscriptionSHA256 + "\x00" +
			request.Candidate.NormalizedSHA256 + "\x00" + request.Diff.SHA256 + "\x00" + request.Approval.ID + "\x00" +
			request.Approval.PrayerPolicySHA256 + "\x00" + approvalEvidenceBinding(request.ApprovalEvidence) + "\x00" +
			request.SnapshotID + "\x00" + request.SigningKeyID + "\x00" + hex.EncodeToString(hash[:]),
	))
	var approvalReceiptSHA256, approvalTrustSHA256, approvalKeyID string
	var approvalTrustRevision uint64
	if request.ApprovalEvidence != nil {
		approvalReceiptSHA256 = request.ApprovalEvidence.ReceiptSHA256
		approvalTrustRevision = request.ApprovalEvidence.TrustRevision
		approvalTrustSHA256 = request.ApprovalEvidence.TrustBundleSHA256
		approvalKeyID = request.ApprovalEvidence.ApprovalKeyID
	}
	return SigningRequest{
		SchemaVersion: "1.0", RequestID: "signing-" + hex.EncodeToString(binding[:16]), Environment: policy.Environment(),
		TrustBundleRevision: policy.Revision(), TrustBundleSHA256: policy.SHA256(),
		SigningKeyID: request.SigningKeyID, CanonicalSHA256: hex.EncodeToString(hash[:]),
		CanonicalPayloadBase64: base64.StdEncoding.EncodeToString(canonical),
		CandidateID:            request.Candidate.ID, RawSHA256: request.Candidate.Artifact.SHA256,
		TranscriptionSHA256: request.Candidate.TranscriptionSHA256, NormalizedSHA256: request.Candidate.NormalizedSHA256,
		DiffSHA256: request.Diff.SHA256, ParserVersion: request.Candidate.ParserVersion,
		ApprovalID: request.Approval.ID, ApproverIdentity: request.Approval.Actor, ApprovedAt: request.Approval.ApprovedAt,
		ApprovalScope: request.Approval.Scope, PrayerPolicySHA256: request.Approval.PrayerPolicySHA256,
		ApprovalReceiptSHA256: approvalReceiptSHA256, ApprovalTrustRevision: approvalTrustRevision,
		ApprovalTrustSHA256: approvalTrustSHA256, ApprovalKeyID: approvalKeyID,
		Snapshot: snapshot,
	}, nil
}

func FinalizeSigning(request PublishRequest, prepared SigningRequest, response SigningResponse, policy *trust.Policy, audit AuditMetadata) (Result, AuditReceipt, error) {
	recomputed, err := PrepareSigning(request, policy)
	if err != nil {
		return Result{}, AuditReceipt{}, err
	}
	recomputedJSON, _ := json.Marshal(recomputed)
	preparedJSON, _ := json.Marshal(prepared)
	if !bytes.Equal(recomputedJSON, preparedJSON) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signing_request_binding_mismatch", errors.New("signing request does not match exact publication inputs"))
	}
	if response.SchemaVersion != "1.0" || response.RequestID != prepared.RequestID || response.SigningKeyID != prepared.SigningKeyID {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signer_response_binding_mismatch", errors.New("signer response identity does not match signing request"))
	}
	if response.SignerIdentity == "" || len(response.SignerIdentity) > 240 || strings.TrimSpace(response.SignerIdentity) != response.SignerIdentity {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signer_response_invalid", errors.New("signer identity is required"))
	}
	if strings.EqualFold(strings.TrimSpace(response.SignerIdentity), strings.TrimSpace(request.Approval.Actor)) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "separation_of_duties_required", errors.New("approver and signer identities must differ"))
	}
	signedAt, err := time.Parse(time.RFC3339, response.SignedAt)
	if err != nil || signedAt.UTC().Format(time.RFC3339) != response.SignedAt || signedAt.Before(request.GeneratedAt) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signer_response_invalid", errors.New("signed_at is invalid"))
	}
	if audit.PublishedAt.IsZero() || audit.PublishedAt.Before(signedAt) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "audit_metadata_invalid", errors.New("published_at must not precede signed_at"))
	}
	if response.PublishedAt != audit.PublishedAt.UTC().Format(time.RFC3339) ||
		response.PreviousReceiptSHA256 != audit.PreviousReceiptSHA256 || response.ChainGenesisReason != audit.ChainGenesisReason ||
		!validChainMetadata(response.PreviousReceiptSHA256, response.ChainGenesisReason) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "audit_binding_mismatch", errors.New("signer response does not bind exact publication chain metadata"))
	}
	canonical, err := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	if err != nil || base64.StdEncoding.EncodeToString(canonical) != prepared.CanonicalPayloadBase64 {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signing_request_invalid", errors.New("canonical payload encoding is invalid"))
	}
	hash := sha256.Sum256(canonical)
	if hex.EncodeToString(hash[:]) != prepared.CanonicalSHA256 {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signing_request_invalid", errors.New("canonical hash does not match payload"))
	}
	publicKey, err := policy.KeyForSigning(response.SigningKeyID, request.GeneratedAt)
	if err != nil {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signing_key_not_active", err)
	}
	signature, err := base64.StdEncoding.DecodeString(response.SignatureEd25519Base64)
	if err != nil || len(signature) != ed25519.SignatureSize || base64.StdEncoding.EncodeToString(signature) != response.SignatureEd25519Base64 || !ed25519.Verify(publicKey, canonical, signature) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "signer_response_invalid", errors.New("signature does not verify under active key"))
	}
	attestationPayload, requestSHA256, err := BuildAttestationPayload(prepared, response)
	if err != nil || response.SigningRequestSHA256 != requestSHA256 {
		return Result{}, AuditReceipt{}, newError("finalize signing", "attestation_binding_mismatch", errors.New("signing request hash does not match attestation"))
	}
	attestationSignature, err := base64.StdEncoding.DecodeString(response.AttestationEd25519Base64)
	if err != nil || len(attestationSignature) != ed25519.SignatureSize || base64.StdEncoding.EncodeToString(attestationSignature) != response.AttestationEd25519Base64 || !ed25519.Verify(publicKey, attestationPayload, attestationSignature) {
		return Result{}, AuditReceipt{}, newError("finalize signing", "attestation_invalid", errors.New("publication attestation does not verify under active key"))
	}
	snapshot := prepared.Snapshot
	snapshot.Integrity.SignatureEd25519Base64 = base64.StdEncoding.EncodeToString(signature)
	if err := snapshot.Validate(); err != nil {
		return Result{}, AuditReceipt{}, newError("finalize signing", "snapshot_invalid", err)
	}
	data, err := json.MarshalIndent(snapshot, "", "  ")
	if err != nil {
		return Result{}, AuditReceipt{}, newError("finalize signing", "encode_failed", err)
	}
	result := Result{Snapshot: snapshot, JSON: append(data, '\n')}
	audit.SignerIdentity = response.SignerIdentity
	audit.Environment = policy.Environment()
	audit.SignedAt = signedAt
	audit.TrustBundleRevision = policy.Revision()
	audit.TrustBundleSHA256 = policy.SHA256()
	audit.SigningRequestSHA256 = requestSHA256
	audit.SigningRequestID = prepared.RequestID
	audit.AttestationSignature = base64.StdEncoding.EncodeToString(attestationSignature)
	receipt, err := buildAuditReceipt(request, result, audit)
	if err != nil {
		return Result{}, AuditReceipt{}, err
	}
	return result, receipt, nil
}

// BuildAttestationPayload returns the second domain-separated message an
// isolated signer must sign after the canonical snapshot payload.
func BuildAttestationPayload(prepared SigningRequest, response SigningResponse) ([]byte, string, error) {
	requestJSON, err := json.Marshal(prepared)
	if err != nil {
		return nil, "", newError("build attestation", "encode_failed", err)
	}
	requestHash := sha256.Sum256(requestJSON)
	requestSHA256 := hex.EncodeToString(requestHash[:])
	snapshotSHA256, err := finalizedSnapshotSHA256(prepared, response)
	if err != nil {
		return nil, "", err
	}
	attestation := publicationAttestation{
		SchemaVersion: "1.0", Environment: prepared.Environment, RequestID: prepared.RequestID,
		PublicationRequestID: publicationRequestID(prepared.CandidateID, prepared.DiffSHA256, prepared.ApprovalID, prepared.Snapshot.SnapshotID, prepared.SigningKeyID),
		SigningRequestSHA256: requestSHA256, SnapshotID: prepared.Snapshot.SnapshotID, SnapshotSHA256: snapshotSHA256,
		CanonicalSHA256: prepared.CanonicalSHA256, SigningKeyID: prepared.SigningKeyID,
		CandidateID: prepared.CandidateID, RawSHA256: prepared.RawSHA256,
		TranscriptionSHA256: prepared.TranscriptionSHA256, NormalizedSHA256: prepared.NormalizedSHA256,
		DiffSHA256: prepared.DiffSHA256, ParserVersion: prepared.ParserVersion,
		ApprovalID: prepared.ApprovalID, ApproverIdentity: prepared.ApproverIdentity, ApprovedAt: prepared.ApprovedAt,
		ApprovalScope: prepared.ApprovalScope, PrayerPolicySHA256: prepared.PrayerPolicySHA256,
		ApprovalReceiptSHA256: prepared.ApprovalReceiptSHA256, ApprovalTrustRevision: prepared.ApprovalTrustRevision,
		ApprovalTrustSHA256: prepared.ApprovalTrustSHA256, ApprovalKeyID: prepared.ApprovalKeyID,
		TrustBundleRevision: prepared.TrustBundleRevision, TrustBundleSHA256: prepared.TrustBundleSHA256,
		SignerIdentity: response.SignerIdentity, SignedAt: response.SignedAt,
		PublishedAt: response.PublishedAt, PreviousReceiptSHA256: response.PreviousReceiptSHA256,
		ChainGenesisReason: response.ChainGenesisReason,
	}
	encoded, err := json.Marshal(attestation)
	if err != nil {
		return nil, "", newError("build attestation", "encode_failed", err)
	}
	return append([]byte(publicationAttestationDomain), encoded...), requestSHA256, nil
}

func EncodeSigningRequest(request SigningRequest) ([]byte, error) {
	data, err := json.MarshalIndent(request, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("encode signing request: %w", err)
	}
	return append(data, '\n'), nil
}

func EncodeAuditReceipt(receipt AuditReceipt) ([]byte, error) {
	data, err := json.MarshalIndent(receipt, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("encode audit receipt: %w", err)
	}
	return append(data, '\n'), nil
}

func VerifyPublicationEvidence(snapshot []byte, receipt AuditReceipt, policy *trust.Policy, previous *AuditReceipt) error {
	if err := VerifyPublicationAdmission(snapshot, receipt, policy); err != nil {
		return err
	}
	if err := VerifyAuditReceipt(receipt, previous); err != nil {
		return err
	}
	if previous != nil {
		if err := VerifyAuditReceiptHead(*previous, policy); err != nil {
			return fmt.Errorf("verify previous receipt authenticity: %w", err)
		}
	}
	return nil
}

// VerifyPublicationAdmission authenticates one snapshot and its signed
// provenance receipt. Chain continuity remains a separate release-ledger gate.
func VerifyPublicationAdmission(snapshot []byte, receipt AuditReceipt, policy *trust.Policy) error {
	if err := VerifyWithTrust(snapshot, policy); err != nil {
		return fmt.Errorf("verify publication snapshot: %w", err)
	}
	if err := VerifyAuditReceiptHead(receipt, policy); err != nil {
		return err
	}
	if receipt.Environment != policy.Environment() {
		return errors.New("audit receipt environment does not match the current trust policy")
	}
	hash := sha256.Sum256(snapshot)
	if hex.EncodeToString(hash[:]) != receipt.SnapshotSHA256 {
		return errors.New("audit receipt snapshot hash does not match bytes")
	}
	var envelope domain.Snapshot
	if err := json.Unmarshal(snapshot, &envelope); err != nil {
		return fmt.Errorf("decode publication snapshot: %w", err)
	}
	if receipt.SnapshotID != envelope.SnapshotID || receipt.SnapshotGeneratedAt != envelope.GeneratedAt || receipt.SigningKeyID != envelope.Integrity.SigningKeyID || receipt.CanonicalSHA256 != envelope.Integrity.CanonicalSHA256 ||
		receipt.ApprovalID != envelope.Source.Approval.ID || receipt.ApproverIdentity != envelope.Source.Approval.ApprovedBy || receipt.ApprovedAt != envelope.Source.Approval.ApprovedAt || receipt.ApprovalScope != envelope.Source.Approval.Scope {
		return errors.New("audit receipt does not bind snapshot identity, integrity and approval")
	}
	return nil
}

func VerifyAuditReceipt(receipt AuditReceipt, previous *AuditReceipt) error {
	if err := verifyAuditReceiptSelf(receipt); err != nil {
		return err
	}
	if previous == nil {
		if receipt.PreviousReceiptSHA256 != "" || receipt.ChainGenesisReason == "" {
			return errors.New("audit receipt predecessor is required unless this is explicit genesis")
		}
		return nil
	}
	if receipt.ChainGenesisReason != "" || receipt.PreviousReceiptSHA256 != previous.ReceiptSHA256 {
		return errors.New("audit receipt chain does not match previous receipt")
	}
	return verifyAuditReceiptSelf(*previous)
}

// VerifyAuditReceiptHead authenticates the signer-attested fields and the
// receipt's own hash without claiming that an omitted predecessor was checked.
func VerifyAuditReceiptHead(receipt AuditReceipt, policy *trust.Policy) error {
	if policy == nil {
		return errors.New("audit receipt trust policy is required")
	}
	if receipt.Environment != policy.Environment() {
		return errors.New("audit receipt environment does not match trust policy")
	}
	if err := verifyAuditReceiptSelf(receipt); err != nil {
		return err
	}
	generatedAt, err := time.Parse(time.RFC3339, receipt.SnapshotGeneratedAt)
	if err != nil {
		return errors.New("audit receipt snapshot generation time is invalid")
	}
	publicKey, err := policy.KeyForVerification(receipt.SigningKeyID, generatedAt)
	if err != nil {
		return fmt.Errorf("audit receipt trust policy rejected key: %w", err)
	}
	attestation := publicationAttestation{
		SchemaVersion: "1.0", Environment: receipt.Environment, RequestID: receipt.SigningRequestID,
		PublicationRequestID: receipt.RequestID, SigningRequestSHA256: receipt.SigningRequestSHA256,
		SnapshotID: receipt.SnapshotID, SnapshotSHA256: receipt.SnapshotSHA256,
		CanonicalSHA256: receipt.CanonicalSHA256, SigningKeyID: receipt.SigningKeyID,
		CandidateID: receipt.CandidateID, RawSHA256: receipt.RawSHA256, TranscriptionSHA256: receipt.TranscriptionSHA256,
		NormalizedSHA256: receipt.NormalizedSHA256, DiffSHA256: receipt.DiffSHA256, ParserVersion: receipt.ParserVersion,
		ApprovalID: receipt.ApprovalID, ApproverIdentity: receipt.ApproverIdentity, ApprovedAt: receipt.ApprovedAt, ApprovalScope: receipt.ApprovalScope,
		PrayerPolicySHA256: receipt.PrayerPolicySHA256, ApprovalReceiptSHA256: receipt.ApprovalReceiptSHA256,
		ApprovalTrustRevision: receipt.ApprovalTrustRevision, ApprovalTrustSHA256: receipt.ApprovalTrustSHA256,
		ApprovalKeyID:       receipt.ApprovalKeyID,
		TrustBundleRevision: receipt.TrustBundleRevision, TrustBundleSHA256: receipt.TrustBundleSHA256,
		SignerIdentity: receipt.SignerIdentity, SignedAt: receipt.SignedAt,
		PublishedAt: receipt.PublishedAt, PreviousReceiptSHA256: receipt.PreviousReceiptSHA256,
		ChainGenesisReason: receipt.ChainGenesisReason,
	}
	encoded, err := json.Marshal(attestation)
	if err != nil {
		return fmt.Errorf("encode audit attestation: %w", err)
	}
	signature, err := base64.StdEncoding.DecodeString(receipt.AttestationSignature)
	if err != nil || len(signature) != ed25519.SignatureSize || base64.StdEncoding.EncodeToString(signature) != receipt.AttestationSignature || !ed25519.Verify(publicKey, append([]byte(publicationAttestationDomain), encoded...), signature) {
		return errors.New("audit receipt signer attestation is invalid")
	}
	return nil
}

func verifyAuditReceiptSelf(receipt AuditReceipt) error {
	if receipt.SchemaVersion != "1.0" || receipt.Event != "snapshot.published" || (receipt.Environment != "test" && receipt.Environment != "staging" && receipt.Environment != "production") || !publicationRequestIDPattern.MatchString(receipt.RequestID) ||
		len(receipt.SnapshotID) < 1 || len(receipt.SnapshotID) > 128 || len(receipt.SigningKeyID) < 1 || len(receipt.SigningKeyID) > 128 ||
		len(receipt.CandidateID) < 1 || len(receipt.CandidateID) > 128 || len(receipt.ParserVersion) < 1 || len(receipt.ParserVersion) > 128 ||
		len(receipt.ApprovalID) < 1 || len(receipt.ApprovalID) > 128 || len(receipt.ApproverIdentity) < 1 || len(receipt.ApproverIdentity) > 240 ||
		len(receipt.SignerIdentity) < 1 || len(receipt.SignerIdentity) > 240 || strings.EqualFold(strings.TrimSpace(receipt.ApproverIdentity), strings.TrimSpace(receipt.SignerIdentity)) || receipt.TrustBundleRevision == 0 ||
		!validSHA256(receipt.SigningRequestSHA256) || !signingRequestIDPattern.MatchString(receipt.SigningRequestID) || len(receipt.ApprovalScope) < 1 || len(receipt.ApprovalScope) > 500 {
		return errors.New("audit receipt required metadata is missing")
	}
	if receipt.Environment == "production" && (!validSHA256(receipt.PrayerPolicySHA256) || !validSHA256(receipt.ApprovalReceiptSHA256) || receipt.ApprovalTrustRevision == 0 || !validSHA256(receipt.ApprovalTrustSHA256) || receipt.ApprovalKeyID == "") {
		return errors.New("production audit receipt authenticated approval evidence is missing")
	}
	for _, hash := range []string{receipt.SnapshotSHA256, receipt.CanonicalSHA256, receipt.RawSHA256, receipt.TranscriptionSHA256, receipt.NormalizedSHA256, receipt.DiffSHA256, receipt.TrustBundleSHA256, receipt.ReceiptSHA256} {
		if !validSHA256(hash) {
			return errors.New("audit receipt contains invalid SHA-256")
		}
	}
	approvedAt, approvedErr := time.Parse(time.RFC3339, receipt.ApprovedAt)
	signedAt, signedErr := time.Parse(time.RFC3339, receipt.SignedAt)
	publishedAt, publishedErr := time.Parse(time.RFC3339, receipt.PublishedAt)
	if approvedErr != nil || signedErr != nil || publishedErr != nil ||
		approvedAt.UTC().Format(time.RFC3339) != receipt.ApprovedAt || signedAt.UTC().Format(time.RFC3339) != receipt.SignedAt || publishedAt.UTC().Format(time.RFC3339) != receipt.PublishedAt ||
		signedAt.Before(approvedAt) || publishedAt.Before(signedAt) {
		return errors.New("audit receipt timestamp ordering is invalid")
	}
	if !validChainMetadata(receipt.PreviousReceiptSHA256, receipt.ChainGenesisReason) {
		return errors.New("audit receipt must have exactly one predecessor or genesis reason")
	}
	expected := receipt.ReceiptSHA256
	receipt.ReceiptSHA256 = ""
	encoded, err := json.Marshal(receipt)
	if err != nil {
		return fmt.Errorf("encode audit receipt: %w", err)
	}
	hash := sha256.Sum256(encoded)
	if hex.EncodeToString(hash[:]) != expected {
		return errors.New("audit receipt hash does not match content")
	}
	return nil
}

func approvalEvidenceBinding(evidence *ApprovalEvidence) string {
	if evidence == nil {
		return ""
	}
	return fmt.Sprintf("%s\x00%d\x00%s\x00%s", evidence.ReceiptSHA256, evidence.TrustRevision, evidence.TrustBundleSHA256, evidence.ApprovalKeyID)
}

func finalizedSnapshotSHA256(prepared SigningRequest, response SigningResponse) (string, error) {
	snapshot := prepared.Snapshot
	snapshot.Integrity.SignatureEd25519Base64 = response.SignatureEd25519Base64
	encoded, err := json.MarshalIndent(snapshot, "", "  ")
	if err != nil {
		return "", newError("build attestation", "encode_failed", err)
	}
	hash := sha256.Sum256(append(encoded, '\n'))
	return hex.EncodeToString(hash[:]), nil
}
