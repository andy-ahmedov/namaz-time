package approval_test

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"reflect"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/approval"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestSignedReceiptAuthenticatesExactDecisionAndPrayerPolicy(t *testing.T) {
	t.Parallel()

	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	const (
		identity   = "approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly"
		keyID      = "ulyanovsk-approver-akhmedov-2026-01"
		policyHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	)
	policy, err := approval.DecodeTrustBundle([]byte(fmt.Sprintf(`{
  "schema_version":"1.0",
  "revision":1,
  "environment":"production",
  "generated_at":"2026-08-20T14:00:00Z",
  "keys":[{
    "key_id":%q,
    "algorithm":"ed25519",
    "approver_identity":%q,
    "public_key_ed25519_base64":%q,
    "status":"active",
    "not_before":"2026-08-20T14:00:00Z"
  }]
}`, keyID, identity, base64.StdEncoding.EncodeToString(publicKey))))
	if err != nil {
		t.Fatal(err)
	}
	decision := approvalDecision(identity)
	receipt, err := approval.Sign(decision, policyHash, keyID, privateKey)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := approval.EncodeReceipt(receipt)
	if err != nil {
		t.Fatal(err)
	}
	verified, evidence, err := approval.Verify(encoded, policy, policyHash)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(verified, decision) || evidence.KeyID != keyID || evidence.ReceiptSHA256 == "" || evidence.TrustBundleSHA256 == "" {
		t.Fatalf("verified = %#v evidence = %#v", verified, evidence)
	}

	tampered := strings.Replace(string(encoded), policyHash, strings.Repeat("b", 64), 1)
	if _, _, err := approval.Verify([]byte(tampered), policy, strings.Repeat("b", 64)); err == nil {
		t.Fatal("tampered prayer policy was accepted")
	}
}

func TestApprovalTrustRejectsRevokedKeyAndIdentityRebinding(t *testing.T) {
	t.Parallel()

	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	const keyID = "pilot-approver-key"
	bundle := func(status, identity string) *approval.Policy {
		t.Helper()
		revocation := ""
		if status == "revoked" {
			revocation = `,"revoked_at":"2026-08-20T14:00:00Z","revocation_reason":"test revocation"`
		}
		policy, decodeErr := approval.DecodeTrustBundle([]byte(fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":"production","generated_at":"2026-08-20T14:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","approver_identity":%q,"public_key_ed25519_base64":%q,"status":%q,"not_before":"2026-08-20T14:00:00Z"%s}]}`,
			keyID, identity, base64.StdEncoding.EncodeToString(publicKey), status, revocation)))
		if decodeErr != nil {
			t.Fatal(decodeErr)
		}
		return policy
	}
	decision := approvalDecision("approver:pilot:one")
	receipt, err := approval.Sign(decision, strings.Repeat("a", 64), keyID, privateKey)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := approval.EncodeReceipt(receipt)
	if err != nil {
		t.Fatal(err)
	}
	if _, _, err := approval.Verify(encoded, bundle("revoked", decision.Actor), strings.Repeat("a", 64)); err == nil {
		t.Fatal("revoked approver key was accepted")
	}
	if _, _, err := approval.Verify(encoded, bundle("active", "approver:pilot:other"), strings.Repeat("a", 64)); err == nil {
		t.Fatal("approver identity rebinding was accepted")
	}
}

func TestApprovalTrustTransitionPreventsKeyRebindingAndRevocationResurrection(t *testing.T) {
	t.Parallel()

	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	otherPublicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	bundle := func(revision int, generatedAt, status string, key ed25519.PublicKey, revocation string) *approval.Policy {
		t.Helper()
		document := fmt.Sprintf(`{"schema_version":"1.0","revision":%d,"environment":"production","generated_at":%q,"keys":[{"key_id":"approver-key","algorithm":"ed25519","approver_identity":"approver:pilot:one","public_key_ed25519_base64":%q,"status":%q,"not_before":"2026-08-20T14:00:00Z"%s}]}`,
			revision, generatedAt, base64.StdEncoding.EncodeToString(key), status, revocation)
		policy, decodeErr := approval.DecodeTrustBundle([]byte(document))
		if decodeErr != nil {
			t.Fatal(decodeErr)
		}
		return policy
	}
	active := bundle(1, "2026-08-20T14:00:00Z", "active", publicKey, "")
	revoked := bundle(2, "2026-08-20T15:00:00Z", "revoked", publicKey, `,"revoked_at":"2026-08-20T14:30:00Z","revocation_reason":"compromised"`)
	if err := approval.ValidateTransition(active, revoked); err != nil {
		t.Fatalf("valid revocation transition rejected: %v", err)
	}
	resurrected := bundle(3, "2026-08-20T16:00:00Z", "active", publicKey, "")
	if err := approval.ValidateTransition(revoked, resurrected); err == nil {
		t.Fatal("revoked approver key was resurrected")
	}
	rebound := bundle(2, "2026-08-20T15:00:00Z", "active", otherPublicKey, "")
	if err := approval.ValidateTransition(active, rebound); err == nil {
		t.Fatal("approval key ID was rebound to new public material")
	}
}

func TestDecodeTrustBundleChainRequiresDirectPredecessorAfterGenesis(t *testing.T) {
	t.Parallel()

	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	material := base64.StdEncoding.EncodeToString(publicKey)
	previous := []byte(fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":"production","generated_at":"2026-08-20T14:00:00Z","keys":[{"key_id":"approver-2026-01","algorithm":"ed25519","approver_identity":"approver:pilot","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-08-20T14:00:00Z"}]}`, material))
	current := []byte(fmt.Sprintf(`{"schema_version":"1.0","revision":2,"environment":"production","generated_at":"2026-08-20T15:00:00Z","keys":[{"key_id":"approver-2026-01","algorithm":"ed25519","approver_identity":"approver:pilot","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-08-20T14:00:00Z"}]}`, material))

	if _, err := approval.DecodeTrustBundleChain(current, nil); err == nil {
		t.Fatal("revision 2 accepted without its direct predecessor")
	}
	policy, err := approval.DecodeTrustBundleChain(current, previous)
	if err != nil {
		t.Fatal(err)
	}
	if !policy.TransitionValidated() || policy.Revision() != 2 {
		t.Fatalf("transition was not validated: revision=%d validated=%t", policy.Revision(), policy.TransitionValidated())
	}
	if _, err := approval.DecodeTrustBundleChain(previous, previous); err == nil {
		t.Fatal("genesis revision accepted an unexpected predecessor")
	}
}

func approvalDecision(identity string) domain.ApprovalDecision {
	return domain.ApprovalDecision{
		ID: "approval-pilot-2026-001", Decision: domain.ApprovalApproved,
		Actor: identity, ApprovedAt: "2026-08-20T14:10:00Z",
		Scope:       "Second Cathedral Mosque, Ulyanovsk, 2026",
		CandidateID: "candidate-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		RawSHA256:   strings.Repeat("1", 64), TranscriptionSHA256: strings.Repeat("2", 64),
		NormalizedSHA256: strings.Repeat("3", 64), DiffSHA256: strings.Repeat("4", 64),
		ParserVersion:            "effective-schedule/v1",
		AcknowledgedWarningCodes: []string{"day_to_day_delta", "source_marker_preserved"},
	}
}
