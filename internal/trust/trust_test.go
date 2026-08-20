package trust_test

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

func TestDecodeAndResolveLifecycle(t *testing.T) {
	t.Parallel()

	encodedKeys := make([]string, 4)
	for index := range encodedKeys {
		publicKey, _, err := ed25519.GenerateKey(rand.Reader)
		if err != nil {
			t.Fatal(err)
		}
		encodedKeys[index] = base64.StdEncoding.EncodeToString(publicKey)
	}
	bundle := []byte(fmt.Sprintf(`{
  "schema_version":"1.0",
	"revision":4,
  "environment":"production",
  "generated_at":"2026-08-20T00:00:00Z",
  "keys":[
    {"key_id":"scheduled-2027","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"scheduled","not_before":"2027-01-01T00:00:00Z"},
    {"key_id":"active-2026","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-01-01T00:00:00Z","not_after":"2026-12-31T23:59:59Z"},
    {"key_id":"retired-2025","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"retired","not_before":"2025-01-01T00:00:00Z","not_after":"2025-12-31T23:59:59Z"},
    {"key_id":"revoked-2024","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"revoked","not_before":"2024-01-01T00:00:00Z","revoked_at":"2026-08-19T00:00:00Z","revocation_reason":"compromise drill"}
  ]
}`, encodedKeys[0], encodedKeys[1], encodedKeys[2], encodedKeys[3]))

	policy, err := trust.Decode(bundle)
	if err != nil {
		t.Fatal(err)
	}
	activeAt := time.Date(2026, 8, 20, 1, 0, 0, 0, time.UTC)
	if _, err := policy.KeyForSigning("active-2026", activeAt); err != nil {
		t.Fatalf("active signing key: %v", err)
	}
	if _, err := policy.KeyForVerification("retired-2025", time.Date(2025, 8, 20, 0, 0, 0, 0, time.UTC)); err != nil {
		t.Fatalf("retired historical key: %v", err)
	}
	for name, resolve := range map[string]func() error{
		"predates bundle": func() error {
			_, err := policy.KeyForSigning("active-2026", time.Date(2026, 8, 19, 0, 0, 0, 0, time.UTC))
			return err
		},
		"scheduled cannot sign": func() error {
			_, err := policy.KeyForSigning("scheduled-2027", time.Date(2027, 1, 2, 0, 0, 0, 0, time.UTC))
			return err
		},
		"retired cannot sign": func() error { _, err := policy.KeyForSigning("retired-2025", activeAt); return err },
		"revoked cannot verify": func() error {
			_, err := policy.KeyForVerification("revoked-2024", time.Date(2024, 6, 1, 0, 0, 0, 0, time.UTC))
			return err
		},
		"future signature": func() error {
			_, err := policy.KeyForVerification("active-2026", time.Date(2027, 1, 1, 0, 0, 0, 0, time.UTC))
			return err
		},
	} {
		if err := resolve(); err == nil {
			t.Errorf("%s unexpectedly succeeded", name)
		}
	}
}

func TestDecodeFailsClosedOnAmbiguityOrPrivateMaterial(t *testing.T) {
	t.Parallel()

	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	encoded := base64.StdEncoding.EncodeToString(publicKey)
	valid := fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":"test","generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":"key-1","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-01-01T00:00:00Z"}]}`, encoded)
	tests := map[string]string{
		"unknown field":          strings.Replace(valid, `"environment":"test"`, `"environment":"test","private_key":"forbidden"`, 1),
		"duplicate key id":       strings.Replace(valid, `]}`, `,{"key_id":"key-1","algorithm":"ed25519","public_key_ed25519_base64":"`+encoded+`","status":"active","not_before":"2026-01-01T00:00:00Z"}]}`, 1),
		"retired no end":         strings.Replace(valid, `"status":"active"`, `"status":"retired"`, 1),
		"revoked no metadata":    strings.Replace(valid, `"status":"active"`, `"status":"revoked"`, 1),
		"invalid environment":    strings.Replace(valid, `"environment":"test"`, `"environment":"prod"`, 1),
		"duplicate JSON member":  strings.Replace(valid, `"environment":"test"`, `"environment":"test","environment":"production"`, 1),
		"revision above Android": strings.Replace(valid, `"revision":1`, `"revision":9223372036854775808`, 1),
	}
	for name, document := range tests {
		name, document := name, document
		t.Run(name, func(t *testing.T) {
			t.Parallel()
			if _, err := trust.Decode([]byte(document)); err == nil {
				t.Fatal("Decode() unexpectedly succeeded")
			}
		})
	}
}

func TestValidateTransitionPreventsRollbackRebindingAndResurrection(t *testing.T) {
	t.Parallel()

	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	replacement, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	base := func(revision uint64, status, key string) *trust.Policy {
		document := fmt.Sprintf(`{"schema_version":"1.0","revision":%d,"environment":"production","generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":"prod-2026","algorithm":"ed25519","public_key_ed25519_base64":%q,"status":%q,"not_before":"2026-01-01T00:00:00Z"%s}]}`,
			revision, key, status, map[string]string{
				"active":  ``,
				"retired": `,"not_after":"2026-08-19T23:59:59Z"`,
				"revoked": `,"revoked_at":"2026-08-20T00:00:00Z","revocation_reason":"compromised key material"`,
			}[status])
		policy, decodeErr := trust.Decode([]byte(document))
		if decodeErr != nil {
			t.Fatalf("Decode(): %v", decodeErr)
		}
		return policy
	}
	encoded := base64.StdEncoding.EncodeToString(publicKey)
	if err := trust.ValidateTransition(base(1, "active", encoded), base(2, "retired", encoded)); err != nil {
		t.Fatalf("valid active-to-retired transition: %v", err)
	}
	invalid := map[string][2]*trust.Policy{
		"revision rollback": {base(2, "active", encoded), base(1, "active", encoded)},
		"key rebinding":     {base(1, "active", encoded), base(2, "active", base64.StdEncoding.EncodeToString(replacement))},
		"resurrection":      {base(1, "revoked", encoded), base(2, "active", encoded)},
	}
	for name, policies := range invalid {
		name, policies := name, policies
		t.Run(name, func(t *testing.T) {
			t.Parallel()
			if err := trust.ValidateTransition(policies[0], policies[1]); err == nil {
				t.Fatal("ValidateTransition() unexpectedly succeeded")
			}
		})
	}
}

func TestValidateEnvironmentSeparationRejectsReusedPublicMaterial(t *testing.T) {
	t.Parallel()
	publicKey, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	encoded := base64.StdEncoding.EncodeToString(publicKey)
	decode := func(environment, keyID string) *trust.Policy {
		policy, decodeErr := trust.Decode([]byte(fmt.Sprintf(`{"schema_version":"1.0","revision":1,"environment":%q,"generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":"active","not_before":"2026-01-01T00:00:00Z"}]}`, environment, keyID, encoded)))
		if decodeErr != nil {
			t.Fatal(decodeErr)
		}
		return policy
	}
	if err := trust.ValidateEnvironmentSeparation(decode("test", "test-key"), decode("production", "prod-key")); err == nil {
		t.Fatal("ValidateEnvironmentSeparation() unexpectedly accepted reused public material")
	}
}

func TestValidateTransitionRequiresNewKeyScheduledPreflight(t *testing.T) {
	t.Parallel()
	first, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	second, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	entry := func(keyID, encoded, status string) string {
		return fmt.Sprintf(`{"key_id":%q,"algorithm":"ed25519","public_key_ed25519_base64":%q,"status":%q,"not_before":"2026-01-01T00:00:00Z"}`, keyID, encoded, status)
	}
	decode := func(revision int, entries string) *trust.Policy {
		policy, decodeErr := trust.Decode([]byte(fmt.Sprintf(`{"schema_version":"1.0","revision":%d,"environment":"production","generated_at":"2026-08-20T00:00:00Z","keys":[%s]}`, revision, entries)))
		if decodeErr != nil {
			t.Fatal(decodeErr)
		}
		return policy
	}
	firstEncoded := base64.StdEncoding.EncodeToString(first)
	secondEncoded := base64.StdEncoding.EncodeToString(second)
	previous := decode(1, entry("prod-old", firstEncoded, "active"))
	current := decode(2, entry("prod-old", firstEncoded, "active")+","+entry("prod-new", secondEncoded, "active"))
	if err := trust.ValidateTransition(previous, current); err == nil {
		t.Fatal("new active key skipped required scheduled revision")
	}
}
