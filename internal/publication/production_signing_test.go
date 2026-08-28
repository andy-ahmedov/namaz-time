package publication_test

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

type protectedSigner struct {
	keyID      string
	privateKey ed25519.PrivateKey
	called     bool
}

func TestProductionPrepareRequiresVerifiableApprovalProof(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)

	missing := request
	missing.ApprovalReceiptBase64 = ""
	if _, err := publication.PrepareSigning(missing, policy); !publication.IsErrorCode(err, "authenticated_approval_required") {
		t.Fatalf("missing approval proof PrepareSigning() error = %v", err)
	}

	tampered := request
	receiptBytes, err := base64.StdEncoding.DecodeString(tampered.ApprovalReceiptBase64)
	if err != nil {
		t.Fatal(err)
	}
	receiptBytes[len(receiptBytes)/2] ^= 1
	tampered.ApprovalReceiptBase64 = base64.StdEncoding.EncodeToString(receiptBytes)
	if _, err := publication.PrepareSigning(tampered, policy); !publication.IsErrorCode(err, "approval_proof_invalid") {
		t.Fatalf("tampered approval proof PrepareSigning() error = %v", err)
	}
}

func (signer *protectedSigner) KeyID() string { return signer.keyID }

func (signer *protectedSigner) Sign(_ context.Context, payload []byte) ([]byte, error) {
	signer.called = true
	return ed25519.Sign(signer.privateKey, payload), nil
}

func TestProductionPublicationUsesProtectedSignerAndCreatesAuditReceipt(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	signer := &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}

	result, receipt, err := publication.PublishWithSigner(context.Background(), request, signer, policy, publication.AuditMetadata{
		SignerIdentity:     "kms://production/schedule-signer",
		PublishedAt:        request.GeneratedAt.Add(time.Minute),
		ChainGenesisReason: "synthetic production workflow test genesis",
	})
	if err != nil {
		t.Fatal(err)
	}
	if !signer.called {
		t.Fatal("protected signer was not called")
	}
	if err := publication.VerifyWithTrust(result.JSON, policy); err != nil {
		t.Fatalf("VerifyWithTrust() error = %v", err)
	}
	if receipt.SnapshotSHA256 == "" || receipt.CanonicalSHA256 == "" || receipt.ReceiptSHA256 == "" || receipt.SignedAt == "" || receipt.TrustBundleRevision != 1 || receipt.TrustBundleSHA256 == "" ||
		receipt.ApprovalID != request.Approval.ID || receipt.SignerIdentity == "" || receipt.SigningKeyID != request.SigningKeyID {
		t.Fatalf("incomplete audit receipt: %#v", receipt)
	}
}

func TestProductionPublicationRejectsRawPrivateKeyAndSignerMismatch(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := publication.Publish(request, privateKey); !publication.IsErrorCode(err, "production_signer_required") {
		t.Fatalf("raw-key production Publish() error = %v", err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	signer := &protectedSigner{keyID: "wrong-key", privateKey: privateKey}
	if _, _, err := publication.PublishWithSigner(context.Background(), request, signer, policy, publication.AuditMetadata{
		SignerIdentity: "kms://production/schedule-signer", PublishedAt: request.GeneratedAt,
	}); !publication.IsErrorCode(err, "signer_key_mismatch") {
		t.Fatalf("wrong signer PublishWithSigner() error = %v", err)
	}
}

func TestAirGappedSigningRequestIsRecomputedBeforeFinalize(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		t.Fatal(err)
	}
	canonical, err := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	if err != nil {
		t.Fatal(err)
	}
	response := publication.SigningResponse{
		SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: prepared.SigningKeyID,
		SignatureEd25519Base64: base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical)),
		SignerIdentity:         "kms://production/schedule-signer", SignedAt: "2025-01-01T00:00:30Z",
		PublishedAt: "2025-01-01T00:01:00Z", ChainGenesisReason: "synthetic air-gapped workflow test genesis",
	}
	attestation, requestSHA256, err := publication.BuildAttestationPayload(prepared, response)
	if err != nil {
		t.Fatal(err)
	}
	response.SigningRequestSHA256 = requestSHA256
	response.AttestationEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, attestation))
	result, receipt, err := publication.FinalizeSigning(request, prepared, response, policy, publication.AuditMetadata{
		PublishedAt: time.Date(2025, 1, 1, 0, 1, 0, 0, time.UTC), ChainGenesisReason: "synthetic air-gapped workflow test genesis",
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := publication.VerifyWithTrust(result.JSON, policy); err != nil || receipt.SignerIdentity != response.SignerIdentity {
		t.Fatalf("finalized publication failed: verify=%v receipt=%#v", err, receipt)
	}
	if err := publication.VerifyPublicationEvidence(result.JSON, receipt, policy, nil); err != nil {
		t.Fatalf("VerifyPublicationEvidence() error = %v", err)
	}
	tamperedReceipt := receipt
	tamperedReceipt.DiffSHA256 = strings.Repeat("f", 64)
	tamperedReceipt = rehashReceipt(t, tamperedReceipt)
	if err := publication.VerifyPublicationEvidence(result.JSON, tamperedReceipt, policy, nil); err == nil {
		t.Fatal("tampered audit receipt was accepted")
	}
	tamperedChain := receipt
	tamperedChain.ChainGenesisReason = "attacker reset the publication ledger"
	tamperedChain = rehashReceipt(t, tamperedChain)
	if err := publication.VerifyPublicationEvidence(result.JSON, tamperedChain, policy, nil); err == nil {
		t.Fatal("rehashed but unattested audit chain mutation was accepted")
	}
	var compact bytes.Buffer
	if err := json.Compact(&compact, result.JSON); err != nil {
		t.Fatal(err)
	}
	reformattedReceipt := receipt
	reformattedHash := sha256.Sum256(compact.Bytes())
	reformattedReceipt.SnapshotSHA256 = hex.EncodeToString(reformattedHash[:])
	reformattedReceipt = rehashReceipt(t, reformattedReceipt)
	if err := publication.VerifyPublicationEvidence(compact.Bytes(), reformattedReceipt, policy, nil); err == nil {
		t.Fatal("reformatted snapshot with attacker-rehashed receipt was accepted")
	}

	tamperedRequest := prepared
	tamperedRequest.CanonicalSHA256 = "f" + tamperedRequest.CanonicalSHA256[1:]
	if _, _, err := publication.FinalizeSigning(request, tamperedRequest, response, policy, publication.AuditMetadata{
		PublishedAt: time.Date(2025, 1, 1, 0, 1, 0, 0, time.UTC), ChainGenesisReason: "synthetic tamper test genesis",
	}); !publication.IsErrorCode(err, "signing_request_binding_mismatch") {
		t.Fatalf("tampered request FinalizeSigning() error = %v", err)
	}
}

func rehashReceipt(t *testing.T, receipt publication.AuditReceipt) publication.AuditReceipt {
	t.Helper()
	receipt.ReceiptSHA256 = ""
	encoded, err := json.Marshal(receipt)
	if err != nil {
		t.Fatal(err)
	}
	hash := sha256.Sum256(encoded)
	receipt.ReceiptSHA256 = hex.EncodeToString(hash[:])
	return receipt
}

func TestProductionPublicationEnforcesApprovalBeforeGenerationAndSeparateSigner(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	request.Approval.ApprovedAt = request.GeneratedAt.Add(time.Minute).Format(time.RFC3339)
	if _, err := publication.PrepareSigning(request, policy); !publication.IsErrorCode(err, "approval_time_invalid") {
		t.Fatalf("post-generation approval PrepareSigning() error = %v", err)
	}
	policySHA256 := request.Approval.PrayerPolicySHA256
	request.Approval = approvalFor(candidate, diff)
	request.Approval.PrayerPolicySHA256 = policySHA256
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		t.Fatal(err)
	}
	canonical, _ := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	response := publication.SigningResponse{
		SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: prepared.SigningKeyID,
		SignatureEd25519Base64: base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical)),
		SignerIdentity:         request.Approval.Actor, SignedAt: request.GeneratedAt.Add(time.Second).Format(time.RFC3339),
		PublishedAt: request.GeneratedAt.Add(time.Minute).Format(time.RFC3339), ChainGenesisReason: "synthetic separation test genesis",
	}
	attestation, requestSHA256, err := publication.BuildAttestationPayload(prepared, response)
	if err != nil {
		t.Fatal(err)
	}
	response.SigningRequestSHA256 = requestSHA256
	response.AttestationEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, attestation))
	if _, _, err := publication.FinalizeSigning(request, prepared, response, policy, publication.AuditMetadata{
		PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: "synthetic separation test genesis",
	}); !publication.IsErrorCode(err, "separation_of_duties_required") {
		t.Fatalf("same actor FinalizeSigning() error = %v", err)
	}
}

func TestPublicationReceiptChainRequiresAuthenticatedDirectPredecessor(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	firstRequest := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, firstRequest.SigningKeyID, publicKey)
	signer := &protectedSigner{keyID: firstRequest.SigningKeyID, privateKey: privateKey}
	_, firstReceipt, err := publication.PublishWithSigner(context.Background(), firstRequest, signer, policy, publication.AuditMetadata{
		SignerIdentity: "kms://production/schedule-signer", PublishedAt: firstRequest.GeneratedAt.Add(time.Minute),
		ChainGenesisReason: "initial production ledger",
	})
	if err != nil {
		t.Fatal(err)
	}
	secondRequest := firstRequest
	secondRequest.SnapshotID = "production-snapshot-0002"
	secondRequest.GeneratedAt = firstRequest.GeneratedAt.Add(2 * time.Minute)
	secondResult, secondReceipt, err := publication.PublishWithSigner(context.Background(), secondRequest, signer, policy, publication.AuditMetadata{
		SignerIdentity: "kms://production/schedule-signer", PublishedAt: secondRequest.GeneratedAt.Add(time.Minute),
		PreviousReceiptSHA256: firstReceipt.ReceiptSHA256,
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := publication.VerifyPublicationEvidence(secondResult.JSON, secondReceipt, policy, &firstReceipt); err != nil {
		t.Fatalf("valid receipt chain rejected: %v", err)
	}
	wrong := firstReceipt
	wrong.ReceiptSHA256 = strings.Repeat("a", 64)
	if err := publication.VerifyPublicationEvidence(secondResult.JSON, secondReceipt, policy, &wrong); err == nil {
		t.Fatal("wrong direct predecessor was accepted")
	}
}

func TestRetiredKeyRollbackArtifactRemainsAdmissibleAfterTrustRotation(t *testing.T) {
	t.Parallel()

	candidate := candidate()
	candidate.DataClassification = domain.DataClassificationProduction
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, candidate)
	if err != nil {
		t.Fatal(err)
	}
	request := publishRequest(candidate, diff, approvalFor(candidate, diff))
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	previousPolicy := productionPolicy(t, request.SigningKeyID, publicKey)
	result, receipt, err := publication.PublishWithSigner(
		context.Background(),
		request,
		&protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey},
		previousPolicy,
		publication.AuditMetadata{
			SignerIdentity: "kms://production/schedule-signer", PublishedAt: request.GeneratedAt.Add(time.Minute),
			ChainGenesisReason: "rotation rollback fixture genesis",
		},
	)
	if err != nil {
		t.Fatal(err)
	}
	currentDocument := fmt.Sprintf(`{"schema_version":"1.0","revision":2,"environment":"production","generated_at":"2025-01-02T00:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"retired","not_before":"2024-01-01T00:00:00Z","not_after":%q}]}`,
		request.SigningKeyID, base64.StdEncoding.EncodeToString(publicKey), request.GeneratedAt.Format(time.RFC3339))
	currentPolicy, err := trust.Decode([]byte(currentDocument))
	if err != nil {
		t.Fatal(err)
	}
	if err := trust.ValidateTransition(previousPolicy, currentPolicy); err != nil {
		t.Fatal(err)
	}
	if err := trust.ValidateEnvironmentSeparation(
		environmentPolicy(t, "test", "test-rotation-key", ed25519.PublicKey(bytes.Repeat([]byte{0x61}, ed25519.PublicKeySize))),
		environmentPolicy(t, "staging", "staging-rotation-key", ed25519.PublicKey(bytes.Repeat([]byte{0x62}, ed25519.PublicKeySize))),
		currentPolicy,
	); err != nil {
		t.Fatal(err)
	}
	if err := publication.VerifyPublicationEvidence(result.JSON, receipt, currentPolicy, nil); err != nil {
		t.Fatalf("historical rollback artifact rejected after valid retirement: %v", err)
	}
}

func productionPolicy(t *testing.T, keyID string, publicKey ed25519.PublicKey) *trust.Policy {
	t.Helper()
	document := fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":"production","generated_at":"2024-01-01T00:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2024-01-01T00:00:00Z"}]}`, keyID, base64.StdEncoding.EncodeToString(publicKey))
	policy, err := trust.Decode([]byte(document))
	if err != nil {
		t.Fatal(err)
	}
	testPolicy := environmentPolicy(t, "test", "test-schedule-key", ed25519.PublicKey(bytes.Repeat([]byte{0x41}, ed25519.PublicKeySize)))
	stagingPolicy := environmentPolicy(t, "staging", "staging-schedule-key", ed25519.PublicKey(bytes.Repeat([]byte{0x42}, ed25519.PublicKeySize)))
	if err := trust.ValidateEnvironmentSeparation(testPolicy, stagingPolicy, policy); err != nil {
		t.Fatal(err)
	}
	return policy
}

func environmentPolicy(t *testing.T, environment, keyID string, publicKey ed25519.PublicKey) *trust.Policy {
	t.Helper()
	document := fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":%q,"generated_at":"2024-01-01T00:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2024-01-01T00:00:00Z"}]}`, environment, keyID, base64.StdEncoding.EncodeToString(publicKey))
	policy, err := trust.Decode([]byte(document))
	if err != nil {
		t.Fatal(err)
	}
	return policy
}
