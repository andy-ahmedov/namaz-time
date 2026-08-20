package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"io"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

func TestPairingManagerGeneratesOneTimeSecretsAndStoresOnlyDigests(t *testing.T) {
	t.Parallel()

	now := time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)
	repository := &recordingPairingRepository{}
	manager, err := NewPairingManager(PairingManagerConfig{
		Repository:   repository,
		Now:          func() time.Time { return now },
		Random:       &incrementingReader{},
		RateLimitKey: bytes.Repeat([]byte{0x6b}, 32),
		RateLimits: PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 10, CodeAttempts: 5,
		},
	})
	if err != nil {
		t.Fatalf("NewPairingManager() error = %v", err)
	}

	issued, err := manager.Issue(t.Context(), IssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001",
		Reason: "install lobby display", RequestID: "request-issue-0001", ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("Issue() error = %v", err)
	}
	if len(issued.Code) != 26 || issued.DeviceID == "" || issued.ExpiresAt != now.Add(10*time.Minute) {
		t.Fatalf("issued pairing = %#v", issued)
	}
	if repository.created.CodeHash != sha256.Sum256([]byte(issued.Code)) {
		t.Fatal("stored pairing code digest does not bind the returned code")
	}
	if strings.Contains(repository.created.String(), issued.Code) {
		t.Fatal("repository record contains the plaintext pairing code")
	}

	repository.redeemDecision = PairingDecision{
		Outcome: PairingOutcomePaired,
		Device: DevicePrincipal{DeviceID: issued.DeviceID, Mosque: MosqueIdentity{
			ID: "mosque-ulyanovsk-0001", Name: "Second Cathedral Mosque", Timezone: "Europe/Ulyanovsk",
		}},
	}
	provisioning, err := manager.Pair(t.Context(), PairingAttempt{
		Code: issued.Code,
		Device: DeviceInfo{
			AppVersion: "1.0.0", OSVersion: "35", Model: "Android TV",
			Capabilities: []string{"leanback", "4k"},
		},
		InstallationPublicKey: "device-public-key",
		SourceAddress:         "192.0.2.10",
		RequestID:             "request-pair-0001",
	})
	if err != nil {
		t.Fatalf("Pair() error = %v", err)
	}
	if provisioning.DeviceID != issued.DeviceID || len(provisioning.Token) != 43 {
		t.Fatalf("provisioning = %#v", provisioning)
	}
	if repository.redeemed.CodeHash != repository.created.CodeHash {
		t.Fatal("redemption did not use the issued code digest")
	}
	if repository.redeemed.TokenHash != sha256.Sum256([]byte(provisioning.Token)) {
		t.Fatal("stored bearer digest does not bind the returned token")
	}
	if repository.redeemed.SourceBucket == ([sha256.Size]byte{}) ||
		repository.redeemed.DeviceBucket == ([sha256.Size]byte{}) ||
		repository.redeemed.CodeBucket == ([sha256.Size]byte{}) {
		t.Fatal("redemption did not derive all privacy-safe rate-limit buckets")
	}
	if strings.Contains(repository.redeemed.String(), provisioning.Token) ||
		strings.Contains(repository.redeemed.String(), issued.Code) {
		t.Fatal("repository redemption contains a plaintext secret")
	}

	repository.authenticated = repository.redeemDecision.Device
	principal, err := manager.Authenticate(t.Context(), provisioning.Token)
	if err != nil {
		t.Fatalf("Authenticate() error = %v", err)
	}
	if principal.DeviceID != issued.DeviceID || repository.authenticatedHash != repository.redeemed.TokenHash {
		t.Fatalf("authenticated principal = %#v", principal)
	}
}

func TestPairingManagerKeepsPublicPairingFailuresIndistinguishable(t *testing.T) {
	t.Parallel()

	for _, outcome := range []PairingOutcome{
		PairingOutcomeInvalid,
		PairingOutcomeExpired,
		PairingOutcomeConsumed,
		PairingOutcomeRevoked,
	} {
		t.Run(string(outcome), func(t *testing.T) {
			repository := &recordingPairingRepository{redeemDecision: PairingDecision{Outcome: outcome}}
			manager := mustPairingManager(t, repository)
			_, err := manager.Pair(t.Context(), validPairingAttemptFixture())
			if !errors.Is(err, ErrPairingInvalid) {
				t.Fatalf("Pair() error = %v, want ErrPairingInvalid", err)
			}
		})
	}

	repository := &recordingPairingRepository{redeemDecision: PairingDecision{Outcome: PairingOutcomeRateLimited}}
	manager := mustPairingManager(t, repository)
	if _, err := manager.Pair(t.Context(), validPairingAttemptFixture()); !errors.Is(err, ErrPairingRateLimited) {
		t.Fatalf("rate-limited Pair() error = %v", err)
	}
}

func TestPairingManagerTreatsInvalidPersistedIdentityAsInfrastructureFailure(t *testing.T) {
	t.Parallel()

	repository := &recordingPairingRepository{authenticated: DevicePrincipal{
		DeviceID: "device-display-0001",
		Mosque: MosqueIdentity{
			ID: "mosque-ulyanovsk-0001", Name: "Second Cathedral Mosque", Timezone: "+04:00",
		},
	}}
	manager := mustPairingManager(t, repository)
	_, err := manager.Authenticate(t.Context(), "valid-length-bearer-token")
	if err == nil || errors.Is(err, ErrDeviceUnauthorized) {
		t.Fatalf("Authenticate() error = %v, want internal persisted-identity failure", err)
	}
}

func TestPairingManagerRejectsInvalidCommandsAndRandomnessFailure(t *testing.T) {
	t.Parallel()

	repository := &recordingPairingRepository{}
	manager := mustPairingManager(t, repository)
	invalidIssues := []IssuePairingCommand{
		{},
		{MosqueID: "short", ActorID: "actor-service-0001", Reason: "reason", RequestID: "request-0001", ExpiresIn: time.Minute},
		{MosqueID: "mosque-ulyanovsk-0001", ActorID: "short", Reason: "reason", RequestID: "request-0001", ExpiresIn: time.Minute},
		{MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001", Reason: "", RequestID: "request-0001", ExpiresIn: time.Minute},
		{MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001", Reason: "reason", RequestID: "request-0001", ExpiresIn: 16 * time.Minute},
	}
	for _, command := range invalidIssues {
		if _, err := manager.Issue(t.Context(), command); !errors.Is(err, ErrInvalidPairingCommand) {
			t.Fatalf("Issue(%#v) error = %v", command, err)
		}
	}

	invalidAttempts := []PairingAttempt{
		{},
		{Code: "123456", Device: DeviceInfo{AppVersion: "1", OSVersion: "1", Model: "TV"}, RequestID: "request-pair-0001"},
		{Code: strings.Repeat("C", 33), Device: DeviceInfo{AppVersion: "1", OSVersion: "1", Model: "TV"}, SourceAddress: "192.0.2.1", RequestID: "request-pair-0001"},
	}
	for _, attempt := range invalidAttempts {
		if _, err := manager.Pair(t.Context(), attempt); !errors.Is(err, ErrInvalidPairingRequest) {
			t.Fatalf("Pair(%#v) error = %v", attempt, err)
		}
	}

	broken, err := NewPairingManager(PairingManagerConfig{
		Repository:   repository,
		Now:          time.Now,
		Random:       io.LimitReader(bytes.NewReader([]byte{1}), 1),
		RateLimitKey: bytes.Repeat([]byte{1}, 32),
		RateLimits:   PairingRateLimits{Window: time.Minute, SourceAttempts: 2, DeviceAttempts: 2, CodeAttempts: 2},
	})
	if err != nil {
		t.Fatalf("NewPairingManager() error = %v", err)
	}
	if _, err := broken.Issue(t.Context(), IssuePairingCommand{
		MosqueID: "mosque-ulyanovsk-0001", ActorID: "actor-service-0001", Reason: "reason",
		RequestID: "request-issue-0001", ExpiresIn: time.Minute,
	}); err == nil || strings.Contains(err.Error(), "pairing code") {
		t.Fatalf("randomness failure = %v", err)
	}
}

func TestPairingManagerRevocationKeepsMosqueScope(t *testing.T) {
	t.Parallel()

	repository := &recordingPairingRepository{}
	manager := mustPairingManager(t, repository)
	command := RevokeDeviceCommand{
		MosqueID: "mosque-ulyanovsk-0001", DeviceID: "device-display-0001",
		ActorID: "actor-service-0001", Reason: "device replaced", RequestID: "request-revoke-0001",
	}
	if err := manager.Revoke(t.Context(), command); err != nil {
		t.Fatalf("Revoke() error = %v", err)
	}
	if repository.revoked.MosqueID != command.MosqueID || repository.revoked.DeviceID != command.DeviceID {
		t.Fatalf("stored revocation = %#v", repository.revoked)
	}
}

func TestPostgresTransportPolicyRejectsUnauthenticatedRemoteTLS(t *testing.T) {
	t.Parallel()

	cases := []struct {
		name    string
		url     string
		wantErr bool
	}{
		{"loopback development may disable TLS", "postgres://user:pass@127.0.0.1:5432/db?sslmode=disable", false},
		{"remote disable", "postgres://user:pass@db.example.test:5432/db?sslmode=disable", true},
		{"remote default prefer fallback", "postgres://user:pass@db.example.test:5432/db", true},
		{"remote encryption without server authentication", "postgres://user:pass@db.example.test:5432/db?sslmode=require", true},
		{"remote verified TLS", "postgres://user:pass@db.example.test:5432/db?sslmode=verify-full", false},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			config, err := pgxpool.ParseConfig(testCase.url)
			if err != nil {
				t.Fatalf("ParseConfig() error = %v", err)
			}
			err = validatePostgresTransport(config)
			if (err != nil) != testCase.wantErr {
				t.Fatalf("validatePostgresTransport() error = %v, wantErr=%v", err, testCase.wantErr)
			}
		})
	}
}

func mustPairingManager(t *testing.T, repository PairingRepository) *PairingManager {
	t.Helper()
	manager, err := NewPairingManager(PairingManagerConfig{
		Repository:   repository,
		Now:          func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
		Random:       &incrementingReader{},
		RateLimitKey: bytes.Repeat([]byte{0x6b}, 32),
		RateLimits:   PairingRateLimits{Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 10, CodeAttempts: 5},
	})
	if err != nil {
		t.Fatalf("NewPairingManager() error = %v", err)
	}
	return manager
}

func validPairingAttemptFixture() PairingAttempt {
	return PairingAttempt{
		Code:          "ABCDEFGHIJKLMNOPQRSTUVWX26",
		Device:        DeviceInfo{AppVersion: "1.0.0", OSVersion: "35", Model: "Android TV"},
		SourceAddress: "192.0.2.1", RequestID: "request-pair-0001",
	}
}

type recordingPairingRepository struct {
	created           PairingRecord
	redeemed          PairingRedemption
	redeemDecision    PairingDecision
	authenticatedHash [sha256.Size]byte
	authenticated     DevicePrincipal
	revoked           DeviceRevocation
}

func (repository *recordingPairingRepository) CreatePairing(_ context.Context, record PairingRecord) error {
	repository.created = record
	return nil
}

func (repository *recordingPairingRepository) RedeemPairing(_ context.Context, redemption PairingRedemption) (PairingDecision, error) {
	repository.redeemed = redemption
	return repository.redeemDecision, nil
}

func (repository *recordingPairingRepository) AuthenticateDevice(_ context.Context, tokenHash [sha256.Size]byte) (DevicePrincipal, error) {
	repository.authenticatedHash = tokenHash
	return repository.authenticated, nil
}

func (repository *recordingPairingRepository) RevokeDevice(_ context.Context, revocation DeviceRevocation) error {
	repository.revoked = revocation
	return nil
}

type incrementingReader struct{ next byte }

func (reader *incrementingReader) Read(target []byte) (int, error) {
	for index := range target {
		reader.next++
		target[index] = reader.next
	}
	return len(target), nil
}
