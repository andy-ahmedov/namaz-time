package publication

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestVerifyRejectsCorrectlySignedMalformedOptionalSections(t *testing.T) {
	t.Parallel()

	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name   string
		mutate func(*domain.Snapshot)
	}{
		{
			name: "iqamah rule",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.IqamahRules[0].Prayer = "invalid"
			},
		},
		{
			name: "iqamah override",
			mutate: func(snapshot *domain.Snapshot) {
				value := 241
				snapshot.IqamahOverrides = []domain.IqamahOverride{{
					Date: "2026-08-20", Prayer: "dhuhr",
					Value: domain.IqamahValue{Mode: "offset_after_adhan", OffsetMinutes: &value},
				}}
			},
		},
		{
			name: "jumuah session",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.JumuahSessions[0].SalahTime = "25:00"
			},
		},
		{
			name: "campaign",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.Campaigns[0].URL = "http://example.org"
			},
		},
		{
			name: "campaign lowercase reversed range",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.Campaigns[0].StartsAt = "2026-08-21t00:00:00z"
				snapshot.Campaigns[0].EndsAt = "2026-08-20t00:00:00z"
			},
		},
		{
			name: "campaign leap-second reversed range",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.Campaigns[0].StartsAt = "2026-12-31T23:59:60Z"
				snapshot.Campaigns[0].EndsAt = "2026-12-31T23:59:59Z"
			},
		},
		{
			name: "campaign fractional precision reversed range",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.Campaigns[0].StartsAt = "2026-08-20T00:00:00.0000000002Z"
				snapshot.Campaigns[0].EndsAt = "2026-08-20T00:00:00.0000000001Z"
			},
		},
		{
			name: "theme asset",
			mutate: func(snapshot *domain.Snapshot) {
				snapshot.Theme.LandscapeAsset = &domain.AssetReference{
					AssetID: "asset", SHA256: "bad", MediaType: "image/png",
					ByteLength: 1, Width: 320, Height: 180,
				}
			},
		},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			snapshot := verificationBaseSnapshot(t)
			test.mutate(&snapshot)
			data := signSnapshotForVerificationTest(t, snapshot, privateKey)
			if err := Verify(data, map[string]ed25519.PublicKey{"test-key": publicKey}); !IsErrorCode(err, "snapshot_invalid") {
				t.Fatalf("Verify() error = %v", err)
			}
		})
	}
}

func TestVerifyRejectsCorrectlySignedBaseFieldSchemaDrift(t *testing.T) {
	t.Parallel()

	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name   string
		mutate func(*domain.Snapshot)
	}{
		{name: "short snapshot ID", mutate: func(snapshot *domain.Snapshot) { snapshot.SnapshotID = "x" }},
		{name: "invalid offset hour", mutate: func(snapshot *domain.Snapshot) { snapshot.GeneratedAt = "2026-08-20T00:00:00+24:00" }},
		{name: "invalid offset minute", mutate: func(snapshot *domain.Snapshot) { snapshot.Source.RetrievedAt = "2026-08-20T00:00:00+00:60" }},
		{name: "invalid country code", mutate: func(snapshot *domain.Snapshot) { snapshot.Mosque.CountryCode = "USA" }},
		{name: "long authority", mutate: func(snapshot *domain.Snapshot) { snapshot.Source.AuthorityName = stringOfLength(241) }},
		{name: "invalid canonical URI", mutate: func(snapshot *domain.Snapshot) { snapshot.Source.CanonicalURL = "://bad" }},
		{name: "duplicate prayer flag", mutate: func(snapshot *domain.Snapshot) { snapshot.PrayerDays[0].Flags = []string{"same", "same"} }},
		{name: "long approval scope", mutate: func(snapshot *domain.Snapshot) { snapshot.Source.Approval.Scope = stringOfLength(1001) }},
	}
	for _, test := range tests {
		test := test
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			snapshot := verificationBaseSnapshot(t)
			test.mutate(&snapshot)
			data := signSnapshotForVerificationTest(t, snapshot, privateKey)
			if err := Verify(data, map[string]ed25519.PublicKey{"test-key": publicKey}); !IsErrorCode(err, "snapshot_invalid") {
				t.Fatalf("Verify() error = %v", err)
			}
		})
	}
}

func stringOfLength(length int) string {
	value := make([]byte, length)
	for index := range value {
		value[index] = 'x'
	}
	return string(value)
}

func verificationBaseSnapshot(t *testing.T) domain.Snapshot {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("..", "..", "examples", "synthetic-prayer-snapshot.json"))
	if err != nil {
		t.Fatal(err)
	}
	snapshot, err := domain.DecodeSnapshot(data)
	if err != nil {
		t.Fatal(err)
	}
	return snapshot
}

func signSnapshotForVerificationTest(t *testing.T, snapshot domain.Snapshot, privateKey ed25519.PrivateKey) []byte {
	t.Helper()
	snapshot.Integrity = domain.IntegrityMetadata{
		CanonicalSHA256:        "0000000000000000000000000000000000000000000000000000000000000000",
		SigningKeyID:           "test-key",
		SignatureEd25519Base64: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
	}
	unsigned, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatal(err)
	}
	canonical, err := canonicalPayload(unsigned)
	if err != nil {
		t.Fatal(err)
	}
	hash := sha256.Sum256(canonical)
	snapshot.Integrity.CanonicalSHA256 = hex.EncodeToString(hash[:])
	snapshot.Integrity.SignatureEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical))
	data, err := json.Marshal(snapshot)
	if err != nil {
		t.Fatal(err)
	}
	return data
}
