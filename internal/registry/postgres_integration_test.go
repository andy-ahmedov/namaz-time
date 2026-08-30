//go:build integration

package registry_test

import (
	"context"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	. "github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	testCityID     = "city-test-ulyanovsk"
	testRegionID   = "ru-uly"
	testScopeID    = "scope-test-ulyanovsk"
	testAuthority  = "authority-test"
	testSourceID   = "source-test"
	testPolicyID   = "policy-test"
	testTimetable  = "timetable-test"
	testMosqueID   = "mosque-test-ulyanovsk"
	testApprovalID = "approval-test"
	testSnapshotID = "snapshot-test"
)

func TestPostgresRegistryRevisionActivationSearchAndRollback(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("connect PostgreSQL: %v", err)
	}
	defer pool.Close()
	migrator := devices.NewPostgresPairingRepository(pool)
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
	if _, err := pool.Exec(ctx, `
		INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ($1, 'Registry integration mosque', 'Europe/Ulyanovsk', 'active', clock_timestamp(), clock_timestamp())`, testMosqueID); err != nil {
		t.Fatalf("seed mosque: %v", err)
	}

	store := NewPostgresRevisionStore(pool)
	approvals := &fakeApprovalVerifier{evidence: map[string]VerifiedApproval{
		testApprovalID: {ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()},
	}}
	snapshots := &fakeSnapshotVerifier{evidence: map[string]VerifiedSnapshot{
		testSnapshotID: {
			ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk",
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PayloadSHA256: repeatHex("b"),
			SigningKeyID: "production-key-test", VerifiedAt: testNow(),
		},
	}}
	service, err := NewPersistentService(PersistentServiceConfig{Store: store, ApprovalVerifier: approvals, SnapshotVerifier: snapshots, Now: testNow})
	if err != nil {
		t.Fatalf("NewPersistentService() error = %v", err)
	}

	firstDataset := executableDatasetWithDuplicateKirov()
	first := RevisionRecord{ID: "revision-pg-1", SchemaVersion: RegistrySchemaVersion, CatalogRevisionID: "catalog-pg-1", CreatedBy: "actor-pg-1", Reason: "first PostgreSQL registry"}
	if err := service.Stage(ctx, first, firstDataset); err != nil {
		t.Fatalf("Stage(first) error = %v", err)
	}
	if err := service.Activate(ctx, first.ID, "actor-pg-1", "activate first PostgreSQL registry"); err != nil {
		t.Fatalf("Activate(first) error = %v", err)
	}
	results, err := store.SearchActiveCities(ctx, "  КИРОВ  ")
	if err != nil {
		t.Fatalf("SearchActiveCities() error = %v", err)
	}
	if len(results) != 2 || results[0].Region.FederalSubjectCode != "RU-KIR" || results[1].Region.FederalSubjectCode != "RU-KLU" {
		t.Fatalf("duplicate results = %#v", results)
	}
	if unique, ok, err := store.UniqueActiveCity(ctx, "Киров"); err != nil || ok || unique.City.ID != "" {
		t.Fatalf("UniqueActiveCity(duplicate) = %#v, %v, %v", unique, ok, err)
	}
	if unique, ok, err := store.UniqueActiveCity(ctx, "Ulyanovsk"); err != nil || !ok || unique.City.ID != testCityID {
		t.Fatalf("UniqueActiveCity(alias) = %#v, %v, %v", unique, ok, err)
	}
	loadedRecord, loadedDataset, err := store.Load(ctx, first.ID)
	if err != nil || loadedRecord.ContentSHA256 == "" {
		t.Fatalf("Load(first) = %#v, %v", loadedRecord, err)
	}
	wantHash, _ := DatasetSHA256(firstDataset)
	gotHash, _ := DatasetSHA256(loadedDataset)
	if wantHash != gotHash || gotHash != loadedRecord.ContentSHA256 {
		t.Fatalf("persisted dataset hashes = want %s got %s record %s", wantHash, gotHash, loadedRecord.ContentSHA256)
	}

	secondDataset := executableDatasetWithDuplicateKirov()
	secondDataset.Cities[0].Aliases = append(secondDataset.Cities[0].Aliases, "Ulsk")
	second := RevisionRecord{ID: "revision-pg-2", SchemaVersion: RegistrySchemaVersion, ParentRevisionID: first.ID, CatalogRevisionID: "catalog-pg-2", CreatedBy: "actor-pg-2", Reason: "second PostgreSQL registry"}
	if err := service.Stage(ctx, second, secondDataset); err != nil {
		t.Fatalf("Stage(second) error = %v", err)
	}
	if err := service.Activate(ctx, second.ID, "actor-pg-2", "activate second PostgreSQL registry"); err != nil {
		t.Fatalf("Activate(second) error = %v", err)
	}
	if err := service.Rollback(ctx, first.ID, "actor-pg-rollback", "rollback registry revision"); err != nil {
		t.Fatalf("Rollback() error = %v", err)
	}
	active, err := service.ActiveRevision(ctx)
	if err != nil || active.ID != first.ID {
		t.Fatalf("active after rollback = %#v, %v", active, err)
	}
	var auditCount, approvalEvidenceCount, snapshotEvidenceCount int
	if err := pool.QueryRow(ctx, `
		SELECT
			(SELECT count(*) FROM registry_audit_events),
			(SELECT count(*) FROM registry_verified_approvals),
			(SELECT count(*) FROM registry_verified_snapshots)`).Scan(&auditCount, &approvalEvidenceCount, &snapshotEvidenceCount); err != nil {
		t.Fatalf("read registry audit/evidence: %v", err)
	}
	if auditCount != 5 || approvalEvidenceCount != 3 || snapshotEvidenceCount != 3 {
		t.Fatalf("audit/evidence counts = %d/%d/%d", auditCount, approvalEvidenceCount, snapshotEvidenceCount)
	}
	assertRegistryAppendOnly(t, pool)

	if err := migrator.MigrateTo(ctx, devices.PairingSchemaVersion-1); err != nil {
		t.Fatalf("rollback registry migration: %v", err)
	}
	var registryRemoved, fleetPreserved bool
	if err := pool.QueryRow(ctx, `
		SELECT to_regclass('registry_revisions') IS NULL,
		       to_regclass('mosques') IS NOT NULL`).Scan(&registryRemoved, &fleetPreserved); err != nil {
		t.Fatalf("inspect migration rollback: %v", err)
	}
	if !registryRemoved || !fleetPreserved {
		t.Fatalf("migration rollback registry_removed=%v fleet_preserved=%v", registryRemoved, fleetPreserved)
	}
	if err := migrator.MigrateUp(ctx); err != nil {
		t.Fatalf("reapply registry migration: %v", err)
	}
}

func executableDatasetWithDuplicateKirov() Dataset {
	dataset := executableDataset()
	dataset.Regions = append(dataset.Regions,
		domain.Region{ID: "ru-kir", Name: "Кировская область", CountryCode: "RU", FederalSubjectCode: "RU-KIR"},
		domain.Region{ID: "ru-klu", Name: "Калужская область", CountryCode: "RU", FederalSubjectCode: "RU-KLU"},
	)
	dataset.Cities = append(dataset.Cities,
		domain.City{ID: "city-kirov-kir", Name: "Киров", Aliases: []string{"Kirov"}, CountryCode: "RU", RegionID: "ru-kir", SettlementType: "PPLA", Latitude: 58.59809, Longitude: 49.65783, Timezone: "Europe/Kirov", GeographicSource: "https://www.geonames.org/548408", GeographicSourceID: "geonames:548408", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0"},
		domain.City{ID: "city-kirov-klu", Name: "Киров", Aliases: []string{"Kirov"}, CountryCode: "RU", RegionID: "ru-klu", SettlementType: "PPLA2", Latitude: 54.06889, Longitude: 34.29891, Timezone: "Europe/Moscow", GeographicSource: "https://www.geonames.org/548410", GeographicSourceID: "geonames:548410", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0"},
	)
	return dataset
}

func executableDataset() Dataset {
	return Dataset{
		Cities: []domain.City{{
			ID: testCityID, Name: "Ульяновск", Aliases: []string{"Ulyanovsk"}, CountryCode: "RU", RegionID: testRegionID,
			SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk",
			GeographicSource: "https://www.geonames.org/479123", GeographicSourceID: "geonames:479123",
			GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
		}},
		Regions:     []domain.Region{{ID: testRegionID, Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}},
		Scopes:      []domain.GeographicScope{{ID: testScopeID, Kind: domain.GeographicScopeCity, CityID: testCityID, RegionID: testRegionID, Description: "test scope"}},
		Authorities: []domain.PrayerAuthority{{ID: testAuthority, Name: "Test authority", EvidenceLabel: "CONFIRMED_PUBLIC"}},
		Sources: []domain.PrayerSource{{
			ID: testSourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{testAuthority}, GeographicScopeID: testScopeID,
			Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
		}},
		Policies: []domain.PrayerPolicy{{
			ID: testPolicyID, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: testScopeID, AuthorityIDs: []string{testAuthority},
			SourceID: testSourceID, TimeTableID: testTimetable, MosqueIDs: []string{testMosqueID},
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, ApprovalID: testApprovalID,
		}},
		TimeTables: []domain.TimeTable{{
			ID: testTimetable, SourceID: testSourceID, GeographicScopeID: testScopeID, MosqueID: testMosqueID,
			Timezone: "Europe/Ulyanovsk", Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PublishedSnapshotID: testSnapshotID,
		}},
	}
}

func testNow() time.Time { return time.Date(2026, 8, 30, 6, 0, 0, 0, time.UTC) }

func repeatHex(character string) string {
	result := ""
	for range 64 {
		result += character
	}
	return result
}

type fakeApprovalVerifier struct {
	evidence map[string]VerifiedApproval
}

func (verifier *fakeApprovalVerifier) VerifyApproval(_ context.Context, approvalID string) (VerifiedApproval, error) {
	value, ok := verifier.evidence[approvalID]
	if !ok {
		return VerifiedApproval{}, ErrVerifiedReferenceMissing
	}
	return value, nil
}

type fakeSnapshotVerifier struct {
	evidence map[string]VerifiedSnapshot
}

func (verifier *fakeSnapshotVerifier) VerifySnapshot(_ context.Context, snapshotID string) (VerifiedSnapshot, error) {
	value, ok := verifier.evidence[snapshotID]
	if !ok {
		return VerifiedSnapshot{}, ErrVerifiedReferenceMissing
	}
	return value, nil
}

func assertRegistryAppendOnly(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()
	statements := []string{
		`UPDATE registry_cities SET canonical_name = 'tampered' WHERE revision_id = 'revision-pg-1'`,
		`DELETE FROM registry_audit_events`,
		`TRUNCATE registry_revisions CASCADE`,
	}
	for _, statement := range statements {
		if _, err := pool.Exec(t.Context(), statement); !postgresCode(err, "55000") {
			t.Fatalf("append-only statement %q error = %v", statement, err)
		}
	}
}

func postgresCode(err error, code string) bool {
	var postgresError *pgconn.PgError
	return errors.As(err, &postgresError) && postgresError.Code == code
}
