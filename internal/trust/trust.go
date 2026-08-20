// Package trust validates the public-only snapshot signing trust bundle.
// Private signing material is deliberately outside this package and process.
package trust

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
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const maxBundleBytes = 256 * 1024

var keyIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`)

type Status string

const (
	StatusScheduled Status = "scheduled"
	StatusActive    Status = "active"
	StatusRetired   Status = "retired"
	StatusRevoked   Status = "revoked"
)

type Bundle struct {
	SchemaVersion string     `json:"schema_version"`
	Revision      uint64     `json:"revision"`
	Environment   string     `json:"environment"`
	GeneratedAt   string     `json:"generated_at"`
	Keys          []KeyEntry `json:"keys"`
}

type KeyEntry struct {
	KeyID                  string `json:"key_id"`
	Algorithm              string `json:"algorithm"`
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

// Policy is an immutable validated view of a public trust bundle.
type Policy struct {
	bundle                         Bundle
	keys                           map[string]trustedKey
	sha256                         string
	generatedAt                    time.Time
	transitionValidated            bool
	environmentSeparationValidated bool
}

func Decode(data []byte) (*Policy, error) {
	if len(data) == 0 || len(data) > maxBundleBytes {
		return nil, errors.New("trust bundle size is invalid")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return nil, fmt.Errorf("trust bundle JSON is ambiguous: %w", err)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var bundle Bundle
	if err := decoder.Decode(&bundle); err != nil {
		return nil, fmt.Errorf("decode trust bundle: %w", err)
	}
	if err := requireEOF(decoder); err != nil {
		return nil, err
	}
	if bundle.SchemaVersion != "1.0" {
		return nil, errors.New("unsupported trust bundle schema version")
	}
	if bundle.Revision == 0 || bundle.Revision > math.MaxInt64 {
		return nil, errors.New("trust bundle revision must be positive")
	}
	if bundle.Environment != "test" && bundle.Environment != "staging" && bundle.Environment != "production" {
		return nil, errors.New("trust bundle environment is invalid")
	}
	bundleGeneratedAt, err := parseTimestamp(bundle.GeneratedAt)
	if err != nil {
		return nil, errors.New("trust bundle generated_at is invalid")
	}
	if len(bundle.Keys) == 0 || len(bundle.Keys) > 32 {
		return nil, errors.New("trust bundle key count is invalid")
	}
	hash := sha256.Sum256(data)
	policy := &Policy{bundle: bundle, keys: make(map[string]trustedKey, len(bundle.Keys)), sha256: hex.EncodeToString(hash[:]), generatedAt: bundleGeneratedAt, transitionValidated: bundle.Revision == 1}
	publicKeys := make(map[string]string, len(bundle.Keys))
	for index, entry := range bundle.Keys {
		key, err := validateKey(entry)
		if err != nil {
			return nil, fmt.Errorf("trust bundle key %d: %w", index, err)
		}
		if _, duplicate := policy.keys[entry.KeyID]; duplicate {
			return nil, fmt.Errorf("duplicate trust key ID %q", entry.KeyID)
		}
		if entry.Status == StatusRetired && key.notAfter != nil && key.notAfter.After(bundleGeneratedAt) {
			return nil, fmt.Errorf("trust bundle key %d: retired not_after exceeds bundle generated_at", index)
		}
		if entry.Status == StatusRevoked {
			revokedAt, _ := parseTimestamp(entry.RevokedAt)
			if revokedAt.After(bundleGeneratedAt) {
				return nil, fmt.Errorf("trust bundle key %d: revoked_at exceeds bundle generated_at", index)
			}
		}
		encoded := base64.StdEncoding.EncodeToString(key.publicKey)
		if existing, duplicate := publicKeys[encoded]; duplicate {
			return nil, fmt.Errorf("trust key IDs %q and %q reuse public key material", existing, entry.KeyID)
		}
		publicKeys[encoded] = entry.KeyID
		policy.keys[entry.KeyID] = key
	}
	return policy, nil
}

func (policy *Policy) Environment() string {
	if policy == nil {
		return ""
	}
	return policy.bundle.Environment
}

func (policy *Policy) Revision() uint64 {
	if policy == nil {
		return 0
	}
	return policy.bundle.Revision
}

func (policy *Policy) SHA256() string {
	if policy == nil {
		return ""
	}
	return policy.sha256
}

func (policy *Policy) TransitionValidated() bool {
	return policy != nil && policy.transitionValidated
}

func (policy *Policy) EnvironmentSeparationValidated() bool {
	return policy != nil && policy.environmentSeparationValidated
}

// ValidateTransition makes trust-bundle lifecycle changes monotonic. It must
// be evaluated against the directly preceding accepted revision before a new
// production bundle is admitted.
func ValidateTransition(previous, current *Policy) error {
	if previous == nil || current == nil {
		return errors.New("previous and current trust policies are required")
	}
	if previous.Environment() != current.Environment() {
		return errors.New("trust bundle environment cannot change across revisions")
	}
	if previous.Revision() == ^uint64(0) || current.Revision() != previous.Revision()+1 {
		return errors.New("trust bundle revision must advance by exactly one")
	}
	if current.generatedAt.Before(previous.generatedAt) {
		return errors.New("trust bundle generated_at cannot move backwards")
	}
	previousMaterial := make(map[string]string, len(previous.keys))
	for keyID, key := range previous.keys {
		encoded := base64.StdEncoding.EncodeToString(key.publicKey)
		previousMaterial[encoded] = keyID
		updated, exists := current.keys[keyID]
		if !exists {
			if key.entry.Status == StatusScheduled || key.entry.Status == StatusActive {
				return fmt.Errorf("live trust key %q cannot be removed", keyID)
			}
			continue
		}
		if !bytes.Equal(key.publicKey, updated.publicKey) || key.entry.Algorithm != updated.entry.Algorithm || key.entry.NotBefore != updated.entry.NotBefore {
			return fmt.Errorf("trust key %q identity or immutable metadata changed", keyID)
		}
		if err := validateStatusTransition(key.entry, updated.entry); err != nil {
			return fmt.Errorf("trust key %q: %w", keyID, err)
		}
	}
	for keyID, key := range current.keys {
		if _, existed := previous.keys[keyID]; existed {
			continue
		}
		if key.entry.Status != StatusScheduled {
			return fmt.Errorf("new trust key %q must first appear as scheduled", keyID)
		}
		encoded := base64.StdEncoding.EncodeToString(key.publicKey)
		if priorID, reused := previousMaterial[encoded]; reused {
			return fmt.Errorf("new trust key %q reuses material from %q", keyID, priorID)
		}
	}
	current.transitionValidated = true
	return nil
}

// ValidateEnvironmentSeparation rejects key ID or public-key reuse between
// environment trust roots. Call it whenever environment bundles are staged.
func ValidateEnvironmentSeparation(policies ...*Policy) error {
	keyIDs := make(map[string]string)
	materials := make(map[string]string)
	environments := make(map[string]*Policy)
	for _, policy := range policies {
		if policy == nil {
			return errors.New("trust policy is required")
		}
		if _, duplicate := environments[policy.Environment()]; duplicate {
			return fmt.Errorf("duplicate %s trust policy", policy.Environment())
		}
		environments[policy.Environment()] = policy
		for keyID, key := range policy.keys {
			if environment, exists := keyIDs[keyID]; exists && environment != policy.Environment() {
				return fmt.Errorf("trust key ID %q is reused across %s and %s", keyID, environment, policy.Environment())
			}
			keyIDs[keyID] = policy.Environment()
			encoded := base64.StdEncoding.EncodeToString(key.publicKey)
			if environment, exists := materials[encoded]; exists && environment != policy.Environment() {
				return fmt.Errorf("public trust material is reused across %s and %s", environment, policy.Environment())
			}
			materials[encoded] = policy.Environment()
		}
	}
	for _, environment := range []string{"test", "staging", "production"} {
		if environments[environment] == nil {
			return fmt.Errorf("%s trust policy is required for environment separation", environment)
		}
	}
	for _, policy := range environments {
		policy.environmentSeparationValidated = true
	}
	return nil
}

func validateStatusTransition(previous, current KeyEntry) error {
	allowed := map[Status]map[Status]bool{
		StatusScheduled: {StatusScheduled: true, StatusActive: true, StatusRevoked: true},
		StatusActive:    {StatusActive: true, StatusRetired: true, StatusRevoked: true},
		StatusRetired:   {StatusRetired: true, StatusRevoked: true},
		StatusRevoked:   {StatusRevoked: true},
	}
	if !allowed[previous.Status][current.Status] {
		return fmt.Errorf("status cannot change from %s to %s", previous.Status, current.Status)
	}
	if previous.NotAfter != "" && current.NotAfter != previous.NotAfter {
		return errors.New("not_after cannot be changed or widened once set")
	}
	if previous.Status == StatusRevoked && (current.NotAfter != previous.NotAfter || current.RevokedAt != previous.RevokedAt || current.RevocationReason != previous.RevocationReason) {
		return errors.New("revocation metadata is immutable")
	}
	return nil
}

func (policy *Policy) KeyForSigning(keyID string, generatedAt time.Time) (ed25519.PublicKey, error) {
	if policy == nil || generatedAt.Before(policy.generatedAt) {
		return nil, errors.New("snapshot generated_at predates the authorizing trust bundle")
	}
	key, err := policy.resolve(keyID, generatedAt)
	if err != nil {
		return nil, err
	}
	if key.entry.Status != StatusActive {
		return nil, fmt.Errorf("signing key %q is %s, not active", keyID, key.entry.Status)
	}
	return append(ed25519.PublicKey(nil), key.publicKey...), nil
}

func (policy *Policy) KeyForVerification(keyID string, generatedAt time.Time) (ed25519.PublicKey, error) {
	key, err := policy.resolve(keyID, generatedAt)
	if err != nil {
		return nil, err
	}
	if key.entry.Status != StatusActive && key.entry.Status != StatusRetired {
		return nil, fmt.Errorf("verification key %q is %s", keyID, key.entry.Status)
	}
	return append(ed25519.PublicKey(nil), key.publicKey...), nil
}

func (policy *Policy) resolve(keyID string, generatedAt time.Time) (trustedKey, error) {
	if policy == nil {
		return trustedKey{}, errors.New("trust policy is required")
	}
	key, exists := policy.keys[keyID]
	if !exists {
		return trustedKey{}, fmt.Errorf("unknown signing key %q", keyID)
	}
	if generatedAt.IsZero() || generatedAt.Before(key.notBefore) || (key.notAfter != nil && generatedAt.After(*key.notAfter)) {
		return trustedKey{}, fmt.Errorf("snapshot generated_at is outside key %q validity window", keyID)
	}
	if key.entry.Status == StatusRevoked {
		return trustedKey{}, fmt.Errorf("signing key %q is revoked", keyID)
	}
	return key, nil
}

func validateKey(entry KeyEntry) (trustedKey, error) {
	if !keyIDPattern.MatchString(entry.KeyID) {
		return trustedKey{}, errors.New("key_id is invalid")
	}
	if entry.Algorithm != "ed25519" {
		return trustedKey{}, errors.New("algorithm must be ed25519")
	}
	decoded, err := base64.StdEncoding.DecodeString(entry.PublicKeyEd25519Base64)
	if err != nil || len(decoded) != ed25519.PublicKeySize {
		return trustedKey{}, errors.New("public key must be canonical base64 for 32 bytes")
	}
	if base64.StdEncoding.EncodeToString(decoded) != entry.PublicKeyEd25519Base64 {
		return trustedKey{}, errors.New("public key base64 is not canonical")
	}
	notBefore, err := parseTimestamp(entry.NotBefore)
	if err != nil {
		return trustedKey{}, errors.New("not_before is invalid")
	}
	var notAfter *time.Time
	if entry.NotAfter != "" {
		parsed, parseErr := parseTimestamp(entry.NotAfter)
		if parseErr != nil || parsed.Before(notBefore) {
			return trustedKey{}, errors.New("not_after is invalid")
		}
		notAfter = &parsed
	}
	switch entry.Status {
	case StatusScheduled, StatusActive:
		if entry.RevokedAt != "" || entry.RevocationReason != "" {
			return trustedKey{}, errors.New("non-revoked key has revocation metadata")
		}
	case StatusRetired:
		if notAfter == nil || entry.RevokedAt != "" || entry.RevocationReason != "" {
			return trustedKey{}, errors.New("retired key requires not_after and no revocation metadata")
		}
	case StatusRevoked:
		if entry.RevokedAt == "" || len(entry.RevocationReason) < 3 || len(entry.RevocationReason) > 240 {
			return trustedKey{}, errors.New("revoked key requires bounded revocation metadata")
		}
		if _, err := parseTimestamp(entry.RevokedAt); err != nil {
			return trustedKey{}, errors.New("revoked_at is invalid")
		}
	default:
		return trustedKey{}, errors.New("key status is invalid")
	}
	return trustedKey{entry: entry, publicKey: ed25519.PublicKey(append([]byte(nil), decoded...)), notBefore: notBefore, notAfter: notAfter}, nil
}

func parseTimestamp(value string) (time.Time, error) {
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil || parsed.Format(time.RFC3339) != value || parsed.Location() != time.UTC {
		return time.Time{}, errors.New("timestamp must be canonical UTC RFC3339")
	}
	return parsed, nil
}

func requireEOF(decoder *json.Decoder) error {
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return errors.New("trust bundle contains multiple JSON values")
		}
		return fmt.Errorf("decode trust bundle trailing data: %w", err)
	}
	return nil
}
