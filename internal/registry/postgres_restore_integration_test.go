//go:build integration

package registry_test

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
	. "github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	qualifiedRestoreRevision = "registry-restore-qualified-v2"
	legacyRestoreRevision    = "registry-restore-retained-v1"
)

// Invoked only by the disposable backup/restore harness before pg_dump. This
// creates synthetic records; it is not an operational source-onboarding job.
func TestPostgresQualificationBackupSeed(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_QUALIFIED_SEED_URL")
	if databaseURL == "" {
		t.Skip("qualified backup seed is opt-in and only for the disposable restore drill")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 20*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	var database string
	if err := pool.QueryRow(ctx, `SELECT current_database()`).Scan(&database); err != nil || database != "namaz_time_backup_source" {
		t.Fatal("qualified seed refuses databases outside the named disposable restore drill")
	}
	if err := devices.NewPostgresPairingRepository(pool).VerifySchema(ctx); err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, `INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ($1, 'Synthetic legacy backup mosque', 'Europe/Ulyanovsk', 'active', $2, $2)`, testMosqueID, testNow()); err != nil {
		t.Fatal(err)
	}
	store := NewPostgresRevisionStore(pool)
	legacy := executableDataset()
	legacyRecord := postgresTestRevision(t, legacyRestoreRevision, RegistrySchemaVersion, legacy)
	if err := store.Stage(ctx, legacyRecord, legacy); err != nil {
		t.Fatal(err)
	}
	legacyActivation := ActivationRecord{RevisionID: legacyRecord.ID, ActorID: "synthetic-test", Reason: "retained legacy backup evidence", ActivatedAt: testNow(),
		Approvals: []VerifiedApproval{{ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()}},
		Snapshots: []VerifiedSnapshot{{ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk", Effective: legacy.TimeTables[0].Effective,
			PayloadSHA256: repeatHex("b"), SigningKeyID: "synthetic-test-key", VerifiedAt: testNow()}},
	}
	if err := store.Activate(ctx, legacyActivation); err != nil {
		t.Fatal(err)
	}
	dataset := postgresQualifiedDataset(t)
	record := postgresTestRevision(t, qualifiedRestoreRevision, QualifiedRegistrySchemaVersion, dataset)
	record.ParentRevisionID = legacyRecord.ID
	if err := store.Stage(ctx, record, dataset); err != nil {
		t.Fatal(err)
	}
	if err := store.Activate(ctx, postgresQualifiedActivation(t, record.ID, legacyRecord.ID, dataset)); err != nil {
		t.Fatal(err)
	}
	assertPostgresDatasetExact(t, store, legacyRecord, legacy)
	assertPostgresDatasetExact(t, store, record, dataset)
	assertPostgresQualifiedEvidence(t, pool, record.ID, dataset, 2)
	t.Logf("seeded exact registry datasets legacy_sha256=%s qualified_sha256=%s", legacyRecord.ContentSHA256, record.ContentSHA256)
}

func TestPostgresQualifiedBackupRestorePreservesProofAndLegacyHashes(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_RESTORE_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_RESTORE_URL is not set")
	}
	runtimeURL := os.Getenv("NAMAZ_TEST_POSTGRES_RESTORE_RUNTIME_URL")
	if runtimeURL == "" {
		t.Fatal("restored read-only runtime role is required")
	}
	ctx, cancel := context.WithTimeout(t.Context(), 20*time.Second)
	defer cancel()
	owner, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	defer owner.Close()
	runtime, err := pgxpool.New(ctx, runtimeURL)
	if err != nil {
		t.Fatal(err)
	}
	defer runtime.Close()
	if err := devices.NewPostgresPairingRepository(runtime).VerifySchema(ctx); err != nil {
		t.Fatal(err)
	}
	dataset, legacy := postgresQualifiedDataset(t), executableDataset()
	record := postgresTestRevision(t, qualifiedRestoreRevision, QualifiedRegistrySchemaVersion, dataset)
	legacyRecord := postgresTestRevision(t, legacyRestoreRevision, RegistrySchemaVersion, legacy)
	store := NewPostgresRevisionStore(runtime)
	assertPostgresDatasetExact(t, store, record, dataset)
	assertPostgresDatasetExact(t, store, legacyRecord, legacy)
	assertPostgresQualifiedEvidence(t, runtime, record.ID, dataset, 2)
	active, _, err := store.Active(ctx)
	if err != nil || active.ID != record.ID || active.ContentSHA256 != record.ContentSHA256 {
		t.Fatalf("restored active qualified revision differs: %v", err)
	}
	var approvalHash, snapshotHash string
	if err := runtime.QueryRow(ctx, `SELECT a.evidence_sha256, s.payload_sha256
		FROM registry_verified_approvals a JOIN registry_verified_snapshots s ON a.event_id = s.event_id
		WHERE a.revision_id=$1`, legacyRecord.ID).Scan(&approvalHash, &snapshotHash); err != nil {
		t.Fatal(err)
	}
	if approvalHash != repeatHex("a") || snapshotHash != repeatHex("b") {
		t.Fatal("restore changed verified legacy receipt hashes")
	}
	var canSelect, canInsert, canUpdate, canDelete, canTruncate bool
	if err := runtime.QueryRow(ctx, `SELECT
		has_table_privilege(current_user, 'registry_source_qualifications', 'SELECT'),
		has_table_privilege(current_user, 'registry_source_qualifications', 'INSERT'),
		has_table_privilege(current_user, 'registry_source_qualifications', 'UPDATE'),
		has_table_privilege(current_user, 'registry_source_qualifications', 'DELETE'),
		has_table_privilege(current_user, 'registry_source_qualifications', 'TRUNCATE')`).Scan(&canSelect, &canInsert, &canUpdate, &canDelete, &canTruncate); err != nil {
		t.Fatal(err)
	}
	if !canSelect || canInsert || canUpdate || canDelete || canTruncate {
		t.Fatal("restored qualification runtime privileges are not read-only")
	}
	for _, statement := range []string{
		`UPDATE registry_source_qualifications SET proof_json=proof_json`,
		`DELETE FROM registry_source_qualifications`,
		`TRUNCATE registry_source_qualifications CASCADE`,
		`UPDATE registry_verified_snapshots SET qualification_sha256=qualification_sha256`,
	} {
		if _, err := owner.Exec(ctx, statement); !postgresCode(err, "55000") {
			t.Fatalf("restored append-only proof/snapshot guard: %v", err)
		}
	}
	if err := devices.NewPostgresPairingRepository(owner).MigrateTo(ctx, 8); !postgresCode(err, "55000") {
		t.Fatalf("restored evidence downgrade did not refuse: %v", err)
	}
	assertPostgresDatasetExact(t, store, record, dataset)
	t.Logf("restored exact registry datasets legacy_sha256=%s qualified_sha256=%s", legacyRecord.ContentSHA256, record.ContentSHA256)
}
