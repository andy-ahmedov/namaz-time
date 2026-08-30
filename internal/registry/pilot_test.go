package registry

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
)

const (
	pilotCityID         = "city-4adcfc15932f3850d5dd5dbaa17e3a4c"
	pilotSnapshotID     = "ulyanovsk-second-cathedral-2026-pilot-local-v2"
	pilotSnapshotSHA256 = "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b"
	pilotApprovalID     = "approval-second-cathedral-mosque-ulyanovsk-2026-002"
	pilotMosqueID       = "second-cathedral-mosque-ulyanovsk"
)

func TestPilotBindingsComposeCanonicalCatalogWithoutChangingSnapshot(t *testing.T) {
	bindings := loadPilotBindings(t)
	catalog := pilotTestCatalog(t)
	bindings.RegistryRevision.CatalogRevisionID = catalog.Revision.ID
	bindings.RegistryRevision.CatalogContentSHA256 = catalog.Revision.ContentSHA256

	record, dataset, err := ComposeCatalog(catalog, bindings)
	if err != nil {
		t.Fatalf("ComposeCatalog() error = %v", err)
	}
	if record.SchemaVersion != RegistrySchemaVersion || record.CatalogRevisionID != catalog.Revision.ID {
		t.Fatalf("revision = %#v", record)
	}
	if len(dataset.Cities) != 1 || dataset.Cities[0].ID != pilotCityID || dataset.Cities[0].Timezone != "Europe/Ulyanovsk" {
		t.Fatalf("composed city = %#v", dataset.Cities)
	}
	if dataset.Cities[0].FallbackPolicyID != "" {
		t.Fatalf("geographic city acquired fallback policy %q", dataset.Cities[0].FallbackPolicyID)
	}
	bindings.Sources[0].AuthorityIDs[0] = "tampered-after-compose"
	bindings.Policies[0].MosqueIDs[0] = "tampered-after-compose"
	if dataset.Sources[0].AuthorityIDs[0] == "tampered-after-compose" || dataset.Policies[0].MosqueIDs[0] == "tampered-after-compose" {
		t.Fatal("composed dataset retained mutable policy binding slices")
	}

	verifier := loadPilotArtifactVerifier(t)
	service, err := NewPersistentService(PersistentServiceConfig{
		Store: newFakeRevisionStore(), ApprovalVerifier: verifier, SnapshotVerifier: verifier,
		Now: func() time.Time { return time.Date(2026, 8, 30, 7, 0, 0, 0, time.UTC) },
	})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}
	if err := service.Stage(t.Context(), record, dataset); err != nil {
		t.Fatalf("Stage() error = %v", err)
	}
	if err := service.Activate(t.Context(), record.ID, "operator:t037-test", "activate verified Ulyanovsk pilot"); err != nil {
		t.Fatalf("Activate() error = %v", err)
	}
	got, err := service.Resolve(t.Context(), ResolveRequest{CityID: pilotCityID, MosqueID: pilotMosqueID, Date: "2026-08-30"})
	if err != nil {
		t.Fatalf("Resolve() error = %v", err)
	}
	if got.Region.FederalSubjectCode != "RU-ULY" || got.TimeTable.PublishedSnapshotID != pilotSnapshotID || got.Source.ID != "effective-ulyanovsk-2026-v1" {
		t.Fatalf("persistable Ulyanovsk resolution = %#v", got)
	}
	if got.Authorities[0].EvidenceLabel != "CONFIRMED_PUBLIC" || got.Authorities[1].EvidenceLabel != "UNKNOWN" {
		t.Fatalf("authority evidence = %#v", got.Authorities)
	}
	assessment, err := AssessDataset(dataset, ResolveRequest{CityID: pilotCityID, MosqueID: pilotMosqueID, Date: "2026-08-30"})
	if err != nil {
		t.Fatalf("AssessDataset() error = %v", err)
	}
	choices, err := ProjectCityScheduleChoices(RevisionPolicyAssessment{
		Revision: record, State: RevisionStateActive, Result: assessment,
	})
	if err != nil {
		t.Fatalf("ProjectCityScheduleChoices() error = %v", err)
	}
	if choices.Status != CityScheduleChoicesAvailable || choices.SelectionRequired || len(choices.Choices) != 1 ||
		!choices.Choices[0].Selectable || !choices.Choices[0].Executable ||
		choices.Choices[0].PolicyID != "policy-ulyanovsk-second-cathedral-2026" ||
		choices.Choices[0].TimeTable == nil || choices.Choices[0].TimeTable.PublishedSnapshotID != pilotSnapshotID {
		t.Fatalf("Ulyanovsk schedule choices = %#v", choices)
	}
	assertPilotSnapshotUnchanged(t)
}

func TestArtifactVerifierRejectsTamperedPilotSnapshot(t *testing.T) {
	config := pilotArtifactVerifierConfig(t)
	config.Snapshots[0].Snapshot = append([]byte(nil), config.Snapshots[0].Snapshot...)
	config.Snapshots[0].Snapshot[len(config.Snapshots[0].Snapshot)/2] ^= 1
	verifier, err := NewArtifactReferenceVerifier(config)
	if err != nil {
		t.Fatalf("NewArtifactReferenceVerifier() error = %v", err)
	}
	if _, err := verifier.VerifySnapshot(context.Background(), pilotSnapshotID); err == nil {
		t.Fatal("VerifySnapshot() accepted tampered signed bytes")
	}
}

func TestArtifactVerifierHonorsCanceledContext(t *testing.T) {
	verifier := loadPilotArtifactVerifier(t)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := verifier.VerifyApproval(ctx, pilotApprovalID); !errors.Is(err, context.Canceled) {
		t.Fatalf("VerifyApproval() error = %v, want context canceled", err)
	}
	if _, err := verifier.VerifySnapshot(ctx, pilotSnapshotID); !errors.Is(err, context.Canceled) {
		t.Fatalf("VerifySnapshot() error = %v, want context canceled", err)
	}
}

func TestDecodePolicyBindingsRejectsNonCanonicalUTC(t *testing.T) {
	bindings := loadPilotBindings(t)
	bindings.RegistryRevision.CreatedAt = time.Date(2026, 8, 30, 9, 0, 0, 0, time.FixedZone("UTC+3", 3*60*60))
	data, err := json.Marshal(bindings)
	if err != nil {
		t.Fatalf("marshal policy bindings: %v", err)
	}
	if _, err := DecodePolicyBindings(data); !errors.Is(err, ErrRevisionInvalid) {
		t.Fatalf("DecodePolicyBindings() error = %v, want invalid revision", err)
	}
}

func loadPilotBindings(t *testing.T) PolicyBindings {
	t.Helper()
	data := readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json")
	bindings, err := DecodePolicyBindings(data)
	if err != nil {
		t.Fatalf("DecodePolicyBindings() error = %v", err)
	}
	return bindings
}

func loadPilotArtifactVerifier(t *testing.T) *ArtifactReferenceVerifier {
	t.Helper()
	verifier, err := NewArtifactReferenceVerifier(pilotArtifactVerifierConfig(t))
	if err != nil {
		t.Fatalf("NewArtifactReferenceVerifier() error = %v", err)
	}
	return verifier
}

func pilotArtifactVerifierConfig(t *testing.T) ArtifactReferenceVerifierConfig {
	t.Helper()
	return ArtifactReferenceVerifierConfig{
		Now: func() time.Time { return time.Date(2026, 8, 30, 7, 0, 0, 0, time.UTC) },
		Approvals: []ApprovalArtifact{{
			ApprovalID:   pilotApprovalID,
			MosqueID:     pilotMosqueID,
			PrayerPolicy: readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/mosque-prayer-policy.json"),
			Receipt:      readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/approval-receipt.json"),
			TrustBundle:  readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/approver-trust-bundle.json"),
		}},
		Snapshots: []PublishedSnapshotArtifact{{
			SnapshotID:          pilotSnapshotID,
			Snapshot:            readRepositoryFile(t, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json"),
			Receipt:             readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/publication-receipt.json"),
			TrustBundle:         readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/production-trust-bundle.json"),
			PreviousTrustBundle: readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/production-trust-bundle-revision-2.json"),
			TestTrustBundle:     readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/test-trust-bundle.json"),
			StagingTrustBundle:  readRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/staging-trust-bundle.json"),
		}},
	}
}

func pilotTestCatalog(t *testing.T) geography.Catalog {
	t.Helper()
	regions := []domain.Region{{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}}
	cities := []domain.City{{
		ID: pilotCityID, Name: "Ульяновск", Aliases: []string{"Ulyanovsk", "Синбирск"}, CountryCode: "RU", RegionID: "ru-uly",
		SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk", Population: 626540,
		GeographicSource: "https://www.geonames.org/479123", GeographicSourceID: "geonames:479123",
		GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0", SourceModifiedDate: "2022-10-16",
	}}
	content, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{Regions: regions, Cities: cities})
	if err != nil {
		t.Fatalf("marshal test catalog: %v", err)
	}
	digest := sha256.Sum256(content)
	return geography.Catalog{
		SchemaVersion: "namaztime-city-catalog/v1",
		Revision:      geography.Revision{ID: "catalog-geonames-ru-test-ulyanovsk", ContentSHA256: hex.EncodeToString(digest[:]), ImportedCities: 1},
		Regions:       regions, Cities: cities,
	}
}

func assertPilotSnapshotUnchanged(t *testing.T) {
	t.Helper()
	data := readRepositoryFile(t, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json")
	digest := sha256.Sum256(data)
	if hex.EncodeToString(digest[:]) != pilotSnapshotSHA256 {
		t.Fatalf("pilot snapshot SHA-256 = %s", hex.EncodeToString(digest[:]))
	}
	var snapshot domain.Snapshot
	if err := json.Unmarshal(data, &snapshot); err != nil {
		t.Fatalf("decode pilot snapshot: %v", err)
	}
	if snapshot.SnapshotID != pilotSnapshotID || snapshot.Mosque.ID != pilotMosqueID || snapshot.Integrity.SigningKeyID != "pilot-local-schedule-2026-02" || snapshot.Integrity.SignatureEd25519Base64 == "" {
		t.Fatalf("pilot signed identity changed: %#v", snapshot)
	}
}

func readRepositoryFile(t *testing.T, path string) []byte {
	t.Helper()
	root := filepath.Join("..", "..")
	data, err := os.ReadFile(filepath.Join(root, filepath.FromSlash(path)))
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return data
}
