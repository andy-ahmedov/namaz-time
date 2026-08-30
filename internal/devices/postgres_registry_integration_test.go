//go:build integration

package devices

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	ulyanovskCityID      = "city-4adcfc15932f3850d5dd5dbaa17e3a4c"
	ulyanovskMosqueID    = "second-cathedral-mosque-ulyanovsk"
	ulyanovskSnapshotID  = "ulyanovsk-second-cathedral-2026-pilot-local-v2"
	ulyanovskSnapshotSHA = "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b"
)

func TestPostgresUlyanovskAdminSearchResolveAndRollbackPreserveSignedPilot(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 40*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("connect PostgreSQL: %v", err)
	}
	defer pool.Close()
	migrator := NewPostgresPairingRepository(pool)
	if err := migrator.MigrateTo(ctx, 0); err != nil {
		t.Fatalf("reset migrations: %v", err)
	}
	if err := migrator.MigrateUp(ctx); err != nil {
		t.Fatalf("apply migrations: %v", err)
	}
	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = migrator.MigrateTo(cleanupCtx, 0)
	})
	seedMosque(t, pool, ulyanovskMosqueID, "Вторая Соборная мечеть Ульяновска", "Europe/Ulyanovsk")
	adminToken := "ulyanovsk-registry-admin-token-integration-0001"
	seedAdminActor(t, pool, "actor-ulyanovsk-registry-admin", adminToken, AdminRoleMosqueAdmin, ulyanovskMosqueID)

	snapshotBefore := readRegistryRepositoryFile(t, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json")
	assertSHA256(t, snapshotBefore, ulyanovskSnapshotSHA)
	bindings, err := registry.DecodePolicyBindings(readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json"))
	if err != nil {
		t.Fatalf("DecodePolicyBindings() error = %v", err)
	}
	catalog := integrationPilotCatalog(t)
	bindings.RegistryRevision.CatalogRevisionID = catalog.Revision.ID
	bindings.RegistryRevision.CatalogContentSHA256 = catalog.Revision.ContentSHA256
	record, dataset, err := registry.ComposeCatalog(catalog, bindings)
	if err != nil {
		t.Fatalf("ComposeCatalog() error = %v", err)
	}
	verifier, err := registry.NewArtifactReferenceVerifier(integrationArtifactVerifierConfig(t))
	if err != nil {
		t.Fatalf("NewArtifactReferenceVerifier() error = %v", err)
	}
	store := registry.NewPostgresRevisionStore(pool)
	registryService, err := registry.NewPersistentService(registry.PersistentServiceConfig{
		Store: store, ApprovalVerifier: verifier, SnapshotVerifier: verifier,
		Now: func() time.Time { return time.Date(2026, 8, 30, 7, 0, 0, 0, time.UTC) },
	})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}
	if err := registryService.Stage(ctx, record, dataset); err != nil {
		t.Fatalf("Stage(first) error = %v", err)
	}
	if err := registryService.Activate(ctx, record.ID, "operator:t037-integration", "activate persisted Ulyanovsk pilot"); err != nil {
		t.Fatalf("Activate(first) error = %v", err)
	}

	adminManager, err := NewAdminFleetManager(AdminFleetManagerConfig{
		Repository: migrator, Now: func() time.Time { return time.Date(2026, 8, 30, 7, 0, 0, 0, time.UTC) },
		IdempotencyKey: bytes.Repeat([]byte{0x37}, sha256.Size),
	})
	if err != nil {
		t.Fatalf("NewAdminFleetManager() error = %v", err)
	}
	httpService, err := NewService(ServiceConfig{AdminBackend: adminManager, RegistryBackend: registryService})
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(httpService.Handler())
	t.Cleanup(server.Close)

	searchURL := server.URL + "/v1/admin/mosques/" + ulyanovskMosqueID + "/setup/cities?q=" + url.QueryEscape("Ульяновск")
	search := adminRequest(t, http.MethodGet, searchURL, nil, adminToken, "")
	if search.StatusCode != http.StatusOK || !strings.Contains(search.Body, `"city_id":"`+ulyanovskCityID+`"`) ||
		!strings.Contains(search.Body, `"federal_subject_code":"RU-ULY"`) || !strings.Contains(search.Body, `"timezone":"Europe/Ulyanovsk"`) {
		t.Fatalf("persisted Ulyanovsk search = %d %s", search.StatusCode, search.Body)
	}
	kirovURL := server.URL + "/v1/admin/mosques/" + ulyanovskMosqueID + "/setup/cities?q=" + url.QueryEscape("Киров")
	kirov := adminRequest(t, http.MethodGet, kirovURL, nil, adminToken, "")
	if kirov.StatusCode != http.StatusOK || strings.Count(kirov.Body, `"canonical_name":"Киров"`) != 2 {
		t.Fatalf("duplicate Kirov search = %d %s", kirov.StatusCode, kirov.Body)
	}
	if _, unique, err := store.UniqueActiveCity(ctx, "Киров"); err != nil || unique {
		t.Fatalf("UniqueActiveCity(Kirov) unique=%v err=%v", unique, err)
	}
	if _, unique, err := store.UniqueActiveCity(ctx, "unknown-city-name"); err != nil || unique {
		t.Fatalf("UniqueActiveCity(unknown) unique=%v err=%v", unique, err)
	}

	resolveURL := server.URL + "/v1/admin/mosques/" + ulyanovskMosqueID + "/setup/prayer-policy?city_id=" + ulyanovskCityID + "&date=2026-08-30"
	resolved := adminRequest(t, http.MethodGet, resolveURL, nil, adminToken, "")
	if resolved.StatusCode != http.StatusOK || !strings.Contains(resolved.Body, `"source":{"id":"effective-ulyanovsk-2026-v1"`) ||
		!strings.Contains(resolved.Body, `"published_snapshot_id":"`+ulyanovskSnapshotID+`"`) ||
		!strings.Contains(resolved.Body, `"evidence_label":"CONFIRMED_PUBLIC"`) || !strings.Contains(resolved.Body, `"evidence_label":"UNKNOWN"`) {
		t.Fatalf("persisted Ulyanovsk resolution = %d %s", resolved.StatusCode, resolved.Body)
	}

	secondDataset := dataset
	secondDataset.Cities = append([]domain.City(nil), dataset.Cities...)
	secondDataset.Cities[0].Aliases = append(append([]string(nil), dataset.Cities[0].Aliases...), "Ulsk")
	second := record
	second.ID = "registry-ulyanovsk-pilot-2026-v2"
	second.ParentRevisionID = record.ID
	second.CreatedAt = record.CreatedAt.Add(time.Minute)
	second.Reason = "test a reversible catalog alias revision"
	second.ContentSHA256 = ""
	if err := registryService.Stage(ctx, second, secondDataset); err != nil {
		t.Fatalf("Stage(second) error = %v", err)
	}
	if err := registryService.Activate(ctx, second.ID, "operator:t037-integration", "activate successor registry"); err != nil {
		t.Fatalf("Activate(second) error = %v", err)
	}
	if err := registryService.Rollback(ctx, record.ID, "operator:t037-integration", "restore first persisted Ulyanovsk revision"); err != nil {
		t.Fatalf("Rollback(first) error = %v", err)
	}
	resolvedAfterRollback := adminRequest(t, http.MethodGet, resolveURL, nil, adminToken, "")
	if resolvedAfterRollback.StatusCode != http.StatusOK || !strings.Contains(resolvedAfterRollback.Body, `"published_snapshot_id":"`+ulyanovskSnapshotID+`"`) {
		t.Fatalf("resolution after rollback = %d %s", resolvedAfterRollback.StatusCode, resolvedAfterRollback.Body)
	}
	snapshotAfter := readRegistryRepositoryFile(t, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json")
	if !bytes.Equal(snapshotBefore, snapshotAfter) {
		t.Fatal("registry lifecycle changed signed pilot snapshot bytes")
	}
	assertSHA256(t, snapshotAfter, ulyanovskSnapshotSHA)
}

func integrationPilotCatalog(t *testing.T) geography.Catalog {
	t.Helper()
	regions := []domain.Region{
		{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"},
		{ID: "ru-kir", Name: "Кировская область", CountryCode: "RU", FederalSubjectCode: "RU-KIR"},
		{ID: "ru-klu", Name: "Калужская область", CountryCode: "RU", FederalSubjectCode: "RU-KLU"},
	}
	cities := []domain.City{
		{ID: ulyanovskCityID, Name: "Ульяновск", Aliases: []string{"Ulyanovsk", "Синбирск"}, CountryCode: "RU", RegionID: "ru-uly", SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk", Population: 626540, GeographicSource: "https://www.geonames.org/479123", GeographicSourceID: "geonames:479123", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0", SourceModifiedDate: "2022-10-16"},
		{ID: "city-test-kirov-kir", Name: "Киров", CountryCode: "RU", RegionID: "ru-kir", SettlementType: "PPLA", Latitude: 58.60, Longitude: 49.66, Timezone: "Europe/Kirov", GeographicSource: "https://www.geonames.org/548408", GeographicSourceID: "geonames:548408", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0"},
		{ID: "city-test-kirov-klu", Name: "Киров", CountryCode: "RU", RegionID: "ru-klu", SettlementType: "PPLA2", Latitude: 54.07, Longitude: 34.30, Timezone: "Europe/Moscow", GeographicSource: "https://www.geonames.org/548410", GeographicSourceID: "geonames:548410", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0"},
	}
	content, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{Regions: regions, Cities: cities})
	if err != nil {
		t.Fatalf("marshal integration catalog: %v", err)
	}
	digest := sha256.Sum256(content)
	return geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{
		ID: "catalog-geonames-ru-integration-t037", ContentSHA256: hex.EncodeToString(digest[:]), ImportedCities: len(cities),
	}, Regions: regions, Cities: cities}
}

func integrationArtifactVerifierConfig(t *testing.T) registry.ArtifactReferenceVerifierConfig {
	t.Helper()
	return registry.ArtifactReferenceVerifierConfig{
		Now: func() time.Time { return time.Date(2026, 8, 30, 7, 0, 0, 0, time.UTC) },
		Approvals: []registry.ApprovalArtifact{{
			ApprovalID: "approval-second-cathedral-mosque-ulyanovsk-2026-002", MosqueID: ulyanovskMosqueID,
			PrayerPolicy: readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/mosque-prayer-policy.json"),
			Receipt:      readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/approval-receipt.json"),
			TrustBundle:  readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/approver-trust-bundle.json"),
		}},
		Snapshots: []registry.PublishedSnapshotArtifact{{
			SnapshotID:          ulyanovskSnapshotID,
			Snapshot:            readRegistryRepositoryFile(t, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json"),
			Receipt:             readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/publication-receipt.json"),
			TrustBundle:         readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/production-trust-bundle.json"),
			PreviousTrustBundle: readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/production-trust-bundle-revision-2.json"),
			TestTrustBundle:     readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/test-trust-bundle.json"),
			StagingTrustBundle:  readRegistryRepositoryFile(t, "fixtures/pilot/ulyanovsk-2026/pilot-local/staging-trust-bundle.json"),
		}},
	}
}

func readRegistryRepositoryFile(t *testing.T, path string) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("..", "..", filepath.FromSlash(path)))
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return data
}

func assertSHA256(t *testing.T, data []byte, want string) {
	t.Helper()
	digest := sha256.Sum256(data)
	if got := hex.EncodeToString(digest[:]); got != want {
		t.Fatalf("SHA-256 = %s, want %s", got, want)
	}
}
