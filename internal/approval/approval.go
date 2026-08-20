// Package approval authenticates human schedule approvals independently from
// snapshot signing. Approval keys never authorize a snapshot signature, and
// snapshot signing keys never manufacture a religious approval.
package approval

import (
	"bytes"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const (
	maxDocumentBytes = 256 * 1024
	receiptDomain    = "namaz-time/approval-receipt/v1\x00"
)

var keyIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`)

type Status string

const (
	StatusScheduled Status = "scheduled"
	StatusActive    Status = "active"
	StatusRetired   Status = "retired"
	StatusRevoked   Status = "revoked"
)

type TrustBundle struct {
	SchemaVersion string     `json:"schema_version"`
	Revision      uint64     `json:"revision"`
	Environment   string     `json:"environment"`
	GeneratedAt   string     `json:"generated_at"`
	Keys          []KeyEntry `json:"keys"`
}

type KeyEntry struct {
	KeyID                  string `json:"key_id"`
	Algorithm              string `json:"algorithm"`
	ApproverIdentity       string `json:"approver_identity"`
	PublicKeyEd25519Base64 string `json:"public_key_ed25519_base64"`
	Status                 Status `json:"status"`
	NotBefore              string `json:"not_before"`
	NotAfter               string `json:"not_after,omitempty"`
	RevokedAt              string `json:"revoked_at,omitempty"`
	RevocationReason       string `json:"revocation_reason,omitempty"`
}

type trustedKey struct {
	entry     KeyEntry
	publicKey ed25519.PublicKey
	notBefore time.Time
	notAfter  *time.Time
}

type Policy struct {
	bundle              TrustBundle
	keys                map[string]trustedKey
	sha256              string
	generatedAt         time.Time
	transitionValidated bool
}

type Receipt struct {
	SchemaVersion      string                  `json:"schema_version"`
	Approval           domain.ApprovalDecision `json:"approval"`
	PrayerPolicySHA256 string                  `json:"prayer_policy_sha256"`
	ApprovalKeyID      string                  `json:"approval_key_id"`
	SignatureBase64    string                  `json:"signature_ed25519_base64"`
}

type Evidence struct {
	KeyID             string
	ReceiptSHA256     string
	TrustBundleSHA256 string
	TrustRevision     uint64
}

func DecodeTrustBundle(data []byte) (*Policy, error) {
	if len(data) == 0 || len(data) > maxDocumentBytes {
		return nil, errors.New("approval trust bundle size is invalid")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return nil, fmt.Errorf("approval trust bundle JSON is ambiguous: %w", err)
	}
	var bundle TrustBundle
	if err := decodeStrict(data, &bundle); err != nil {
		return nil, fmt.Errorf("decode approval trust bundle: %w", err)
	}
	if bundle.SchemaVersion != "1.0" || bundle.Revision == 0 || bundle.Revision > math.MaxInt64 {
		return nil, errors.New("approval trust bundle version or revision is invalid")
	}
	if bundle.Environment != "production" && bundle.Environment != "staging" && bundle.Environment != "test" {
		return nil, errors.New("approval trust bundle environment is invalid")
	}
	generatedAt, err := canonicalTime(bundle.GeneratedAt)
	if err != nil {
		return nil, errors.New("approval trust bundle generated_at is invalid")
	}
	if len(bundle.Keys) == 0 || len(bundle.Keys) > 32 {
		return nil, errors.New("approval trust bundle key count is invalid")
	}
	hash := sha256.Sum256(data)
	policy := &Policy{bundle: bundle, keys: make(map[string]trustedKey, len(bundle.Keys)), sha256: hex.EncodeToString(hash[:]), generatedAt: generatedAt, transitionValidated: bundle.Revision == 1}
	materials := make(map[string]string, len(bundle.Keys))
	for index, entry := range bundle.Keys {
		key, validateErr := validateKey(entry, generatedAt)
		if validateErr != nil {
			return nil, fmt.Errorf("approval trust key %d: %w", index, validateErr)
		}
		if _, exists := policy.keys[entry.KeyID]; exists {
			return nil, fmt.Errorf("duplicate approval key ID %q", entry.KeyID)
		}
		material := base64.StdEncoding.EncodeToString(key.publicKey)
		if existing, exists := materials[material]; exists {
			return nil, fmt.Errorf("approval keys %q and %q reuse key material", existing, entry.KeyID)
		}
		materials[material] = entry.KeyID
		policy.keys[entry.KeyID] = key
	}
	return policy, nil
}

// DecodeTrustBundleChain validates a current approval bundle against the
// directly preceding accepted revision. Revision 1 is the only genesis and
// must not be supplied with a predecessor.
func DecodeTrustBundleChain(currentData, previousData []byte) (*Policy, error) {
	current, err := DecodeTrustBundle(currentData)
	if err != nil {
		return nil, err
	}
	if current.Revision() == 1 {
		if len(previousData) != 0 {
			return nil, errors.New("approval trust genesis cannot have a predecessor")
		}
		return current, nil
	}
	if len(previousData) == 0 {
		return nil, errors.New("approval trust direct predecessor is required")
	}
	previous, err := DecodeTrustBundle(previousData)
	if err != nil {
		return nil, fmt.Errorf("decode previous approval trust bundle: %w", err)
	}
	if err := ValidateTransition(previous, current); err != nil {
		return nil, err
	}
	return current, nil
}

func (policy *Policy) SHA256() string {
	if policy == nil {
		return ""
	}
	return policy.sha256
}

func (policy *Policy) Revision() uint64 {
	if policy == nil {
		return 0
	}
	return policy.bundle.Revision
}

func (policy *Policy) Environment() string {
	if policy == nil {
		return ""
	}
	return policy.bundle.Environment
}

func (policy *Policy) TransitionValidated() bool {
	return policy != nil && policy.transitionValidated
}

// ValidateTransition prevents approval-key rebinding, lifecycle rollback and
// revocation resurrection across directly adjacent trust revisions.
func ValidateTransition(previous, current *Policy) error {
	if previous == nil || current == nil {
		return errors.New("previous and current approval trust policies are required")
	}
	if previous.Environment() != current.Environment() || current.Revision() != previous.Revision()+1 {
		return errors.New("approval trust revision must advance by exactly one in the same environment")
	}
	if current.generatedAt.Before(previous.generatedAt) {
		return errors.New("approval trust generated_at cannot move backwards")
	}
	for keyID, oldKey := range previous.keys {
		newKey, exists := current.keys[keyID]
		if !exists {
			if oldKey.entry.Status == StatusScheduled || oldKey.entry.Status == StatusActive {
				return fmt.Errorf("live approval key %q cannot be removed", keyID)
			}
			continue
		}
		if !bytes.Equal(oldKey.publicKey, newKey.publicKey) || oldKey.entry.Algorithm != newKey.entry.Algorithm || oldKey.entry.ApproverIdentity != newKey.entry.ApproverIdentity || oldKey.entry.NotBefore != newKey.entry.NotBefore {
			return fmt.Errorf("approval key %q identity or immutable metadata changed", keyID)
		}
		if oldKey.entry.NotAfter != "" && newKey.entry.NotAfter != oldKey.entry.NotAfter {
			return fmt.Errorf("approval key %q not_after changed after being set", keyID)
		}
		allowed := map[Status]map[Status]bool{
			StatusScheduled: {StatusScheduled: true, StatusActive: true, StatusRevoked: true},
			StatusActive:    {StatusActive: true, StatusRetired: true, StatusRevoked: true},
			StatusRetired:   {StatusRetired: true, StatusRevoked: true},
			StatusRevoked:   {StatusRevoked: true},
		}
		if !allowed[oldKey.entry.Status][newKey.entry.Status] {
			return fmt.Errorf("approval key %q status cannot change from %s to %s", keyID, oldKey.entry.Status, newKey.entry.Status)
		}
		if oldKey.entry.Status == StatusRevoked && (oldKey.entry.RevokedAt != newKey.entry.RevokedAt || oldKey.entry.RevocationReason != newKey.entry.RevocationReason) {
			return fmt.Errorf("approval key %q revocation metadata changed", keyID)
		}
	}
	for keyID, key := range current.keys {
		if _, exists := previous.keys[keyID]; !exists && key.entry.Status != StatusScheduled {
			return fmt.Errorf("new approval key %q must first appear as scheduled", keyID)
		}
	}
	current.transitionValidated = true
	return nil
}

func Sign(decision domain.ApprovalDecision, prayerPolicySHA256, keyID string, privateKey ed25519.PrivateKey) (Receipt, error) {
	if len(privateKey) != ed25519.PrivateKeySize {
		return Receipt{}, errors.New("approval private key is invalid")
	}
	receipt := Receipt{
		SchemaVersion: "1.0", Approval: decision, PrayerPolicySHA256: prayerPolicySHA256,
		ApprovalKeyID: keyID,
	}
	payload, err := signingPayload(receipt)
	if err != nil {
		return Receipt{}, err
	}
	receipt.SignatureBase64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, payload))
	return receipt, nil
}

func EncodeReceipt(receipt Receipt) ([]byte, error) {
	if err := validateReceiptFields(receipt); err != nil {
		return nil, err
	}
	data, err := json.MarshalIndent(receipt, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("encode approval receipt: %w", err)
	}
	return append(data, '\n'), nil
}

func Verify(data []byte, policy *Policy, expectedPrayerPolicySHA256 string) (domain.ApprovalDecision, Evidence, error) {
	if policy == nil {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval trust policy is required")
	}
	if !policy.TransitionValidated() {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval trust transition has not been validated")
	}
	if len(data) == 0 || len(data) > maxDocumentBytes {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval receipt size is invalid")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return domain.ApprovalDecision{}, Evidence{}, fmt.Errorf("approval receipt JSON is ambiguous: %w", err)
	}
	var receipt Receipt
	if err := decodeStrict(data, &receipt); err != nil {
		return domain.ApprovalDecision{}, Evidence{}, fmt.Errorf("decode approval receipt: %w", err)
	}
	if err := validateReceiptFields(receipt); err != nil {
		return domain.ApprovalDecision{}, Evidence{}, err
	}
	if receipt.PrayerPolicySHA256 != expectedPrayerPolicySHA256 {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval receipt prayer policy binding does not match")
	}
	key, exists := policy.keys[receipt.ApprovalKeyID]
	if !exists {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval key is not trusted")
	}
	if key.entry.Status != StatusActive {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval key is not active")
	}
	if key.entry.ApproverIdentity != receipt.Approval.Actor {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval identity does not match trusted key")
	}
	approvedAt, _ := canonicalTime(receipt.Approval.ApprovedAt)
	if approvedAt.Before(key.notBefore) || (key.notAfter != nil && approvedAt.After(*key.notAfter)) {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval time is outside key validity")
	}
	signature, err := base64.StdEncoding.DecodeString(receipt.SignatureBase64)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval signature encoding is invalid")
	}
	payload, err := signingPayload(receipt)
	if err != nil {
		return domain.ApprovalDecision{}, Evidence{}, err
	}
	if !ed25519.Verify(key.publicKey, payload, signature) {
		return domain.ApprovalDecision{}, Evidence{}, errors.New("approval signature is invalid")
	}
	hash := sha256.Sum256(data)
	return receipt.Approval, Evidence{
		KeyID: receipt.ApprovalKeyID, ReceiptSHA256: hex.EncodeToString(hash[:]),
		TrustBundleSHA256: policy.SHA256(), TrustRevision: policy.Revision(),
	}, nil
}

func signingPayload(receipt Receipt) ([]byte, error) {
	receipt.SignatureBase64 = ""
	data, err := json.Marshal(receipt)
	if err != nil {
		return nil, fmt.Errorf("encode approval signing payload: %w", err)
	}
	return append([]byte(receiptDomain), data...), nil
}

func validateReceiptFields(receipt Receipt) error {
	if receipt.SchemaVersion != "1.0" || !keyIDPattern.MatchString(receipt.ApprovalKeyID) || !validSHA256(receipt.PrayerPolicySHA256) {
		return errors.New("approval receipt metadata is invalid")
	}
	decision := receipt.Approval
	if decision.Decision != domain.ApprovalApproved || decision.ID == "" || decision.Actor == "" || strings.TrimSpace(decision.Actor) != decision.Actor || len(decision.Actor) > 240 || decision.Scope == "" || len(decision.Scope) > 500 {
		return errors.New("approval decision is incomplete")
	}
	if _, err := canonicalTime(decision.ApprovedAt); err != nil {
		return errors.New("approval decision time is invalid")
	}
	if decision.CandidateID == "" || !validSHA256(decision.RawSHA256) || !validSHA256(decision.TranscriptionSHA256) || !validSHA256(decision.NormalizedSHA256) || !validSHA256(decision.DiffSHA256) || decision.ParserVersion == "" {
		return errors.New("approval decision provenance binding is invalid")
	}
	warnings := append([]string(nil), decision.AcknowledgedWarningCodes...)
	if !sort.StringsAreSorted(warnings) {
		return errors.New("acknowledged warning codes must be sorted")
	}
	for index, warning := range warnings {
		if warning == "" || strings.TrimSpace(warning) != warning || (index > 0 && warnings[index-1] == warning) {
			return errors.New("acknowledged warning codes must be non-empty and unique")
		}
	}
	if receipt.SignatureBase64 == "" {
		return errors.New("approval signature is required")
	}
	return nil
}

func validateKey(entry KeyEntry, generatedAt time.Time) (trustedKey, error) {
	if !keyIDPattern.MatchString(entry.KeyID) || entry.Algorithm != "ed25519" || entry.ApproverIdentity == "" || strings.TrimSpace(entry.ApproverIdentity) != entry.ApproverIdentity || len(entry.ApproverIdentity) > 240 {
		return trustedKey{}, errors.New("identity metadata is invalid")
	}
	publicKey, err := base64.StdEncoding.DecodeString(entry.PublicKeyEd25519Base64)
	if err != nil || len(publicKey) != ed25519.PublicKeySize || base64.StdEncoding.EncodeToString(publicKey) != entry.PublicKeyEd25519Base64 {
		return trustedKey{}, errors.New("public key is invalid")
	}
	notBefore, err := canonicalTime(entry.NotBefore)
	if err != nil || notBefore.After(generatedAt) {
		return trustedKey{}, errors.New("not_before is invalid")
	}
	var notAfter *time.Time
	if entry.NotAfter != "" {
		parsed, parseErr := canonicalTime(entry.NotAfter)
		if parseErr != nil || parsed.Before(notBefore) {
			return trustedKey{}, errors.New("not_after is invalid")
		}
		notAfter = &parsed
	}
	switch entry.Status {
	case StatusScheduled, StatusActive:
		if entry.RevokedAt != "" || entry.RevocationReason != "" {
			return trustedKey{}, errors.New("active key has revocation metadata")
		}
	case StatusRetired:
		if notAfter == nil || entry.RevokedAt != "" || entry.RevocationReason != "" {
			return trustedKey{}, errors.New("retired key metadata is invalid")
		}
	case StatusRevoked:
		if entry.RevokedAt == "" || entry.RevocationReason == "" {
			return trustedKey{}, errors.New("revoked key metadata is incomplete")
		}
		revokedAt, parseErr := canonicalTime(entry.RevokedAt)
		if parseErr != nil || revokedAt.After(generatedAt) {
			return trustedKey{}, errors.New("revoked_at is invalid")
		}
	default:
		return trustedKey{}, errors.New("key status is invalid")
	}
	return trustedKey{entry: entry, publicKey: ed25519.PublicKey(publicKey), notBefore: notBefore, notAfter: notAfter}, nil
}

func canonicalTime(value string) (time.Time, error) {
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil || parsed.UTC().Format(time.RFC3339) != value {
		return time.Time{}, errors.New("timestamp must be canonical UTC whole-second RFC3339")
	}
	return parsed, nil
}

func validSHA256(value string) bool {
	if len(value) != sha256.Size*2 || strings.ToLower(value) != value {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func decodeStrict(data []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}
