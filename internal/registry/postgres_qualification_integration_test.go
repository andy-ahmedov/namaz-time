//go:build integration

package registry_test

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
	. "github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

func TestPostgresQualifiedRegistryRoundTripMigrationAndEvidence(t *testing.T) {
	pool := isolatedQualifiedRegistryPool(t)
	ctx := t.Context()
	migrator := devices.NewPostgresPairingRepository(pool)
	if err := migrator.MigrateUp(ctx); err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, `INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ($1, 'Synthetic legacy registry mosque', 'Europe/Ulyanovsk', 'active', $2, $2)`, testMosqueID, testNow()); err != nil {
		t.Fatal(err)
	}
	store := NewPostgresRevisionStore(pool)
	legacy := executableDataset()
	legacy.Authorities = append(legacy.Authorities, domain.PrayerAuthority{ID: "authority-legacy-secondary", Name: "Synthetic second legacy authority", EvidenceLabel: "CONFIRMED_PUBLIC"})
	legacy.Sources[0].AuthorityIDs = append(legacy.Sources[0].AuthorityIDs, legacy.Authorities[1].ID)
	legacy.Policies[0].AuthorityIDs = append(legacy.Policies[0].AuthorityIDs, legacy.Authorities[1].ID)
	legacyRecord := postgresTestRevision(t, "registry-pg-v1-retained", RegistrySchemaVersion, legacy)
	if err := store.Stage(ctx, legacyRecord, legacy); err != nil {
		t.Fatal(err)
	}
	legacyActivation := ActivationRecord{RevisionID: legacyRecord.ID, ActorID: "synthetic-test", Reason: "legacy receipt baseline", ActivatedAt: testNow(),
		Approvals: []VerifiedApproval{{ID: testApprovalID, MosqueID: testMosqueID, EvidenceSHA256: repeatHex("a"), VerifiedAt: testNow()}},
		Snapshots: []VerifiedSnapshot{{ID: testSnapshotID, MosqueID: testMosqueID, Timezone: "Europe/Ulyanovsk", Effective: legacy.TimeTables[0].Effective,
			PayloadSHA256: repeatHex("b"), SigningKeyID: "synthetic-test-key", VerifiedAt: testNow()}},
	}
	if err := store.Activate(ctx, legacyActivation); err != nil {
		t.Fatal(err)
	}
	if err := migrator.MigrateTo(ctx, 8); err != nil {
		t.Fatalf("legacy-only downgrade: %v", err)
	}
	var oldHash, approvalHash, snapshotHash string
	if err := pool.QueryRow(ctx, `SELECT r.content_sha256, a.evidence_sha256, s.payload_sha256
		FROM registry_revisions r JOIN registry_verified_approvals a ON a.revision_id = r.id
		JOIN registry_verified_snapshots s ON s.revision_id = r.id WHERE r.id = $1`, legacyRecord.ID).Scan(&oldHash, &approvalHash, &snapshotHash); err != nil {
		t.Fatal(err)
	}
	if oldHash != legacyRecord.ContentSHA256 || approvalHash != repeatHex("a") || snapshotHash != repeatHex("b") {
		t.Fatal("legacy-only downgrade rewrote data hashes or verified receipts")
	}
	if err := migrator.MigrateUp(ctx); err != nil {
		t.Fatalf("reapply v9: %v", err)
	}
	assertPostgresDatasetExact(t, store, legacyRecord, legacy)

	qualified := postgresQualifiedDataset(t)
	record := postgresTestRevision(t, "registry-pg-v2-qualified", QualifiedRegistrySchemaVersion, qualified)
	record.ParentRevisionID = legacyRecord.ID
	if err := store.Stage(ctx, record, qualified); err != nil {
		t.Fatalf("stage qualified public revision: %v", err)
	}
	assertPostgresDatasetExact(t, store, record, qualified)
	activation := postgresQualifiedActivation(t, record.ID, legacyRecord.ID, qualified)
	if err := store.Activate(ctx, activation); err != nil {
		t.Fatalf("activate without human approval or fake mosque: %v", err)
	}
	wrongProof := postgresQualifiedActivation(t, record.ID, record.ID, qualified)
	wrongProof.Reason, wrongProof.ActivatedAt = "reject qualification borrowed from another source", testNow().Add(time.Second)
	wrongProof.Snapshots = wrongProof.Snapshots[:1]
	wrongProof.Snapshots[0].QualificationID = qualified.Qualifications[1].ID
	wrongProof.Snapshots[0].QualificationSHA256 = qualified.Qualifications[1].SHA256
	if err := store.Activate(ctx, wrongProof); !errors.Is(err, ErrRevisionInvalid) {
		t.Fatalf("cross-source snapshot qualification reference error = %v", err)
	}
	assertPostgresQualifiedEvidence(t, pool, record.ID, qualified, 1)
	for _, statement := range []string{
		`UPDATE registry_source_qualifications SET proof_json = proof_json`,
		`DELETE FROM registry_source_qualifications`,
		`TRUNCATE registry_source_qualifications CASCADE`,
		`UPDATE registry_verified_snapshots SET qualification_sha256 = qualification_sha256`,
	} {
		if _, err := pool.Exec(ctx, statement); !postgresCode(err, "55000") {
			t.Fatalf("immutable evidence guard: %v", err)
		}
	}
	for name, mutate := range map[string]func(*Dataset, *RevisionRecord){
		"proof_tamper":      func(d *Dataset, _ *RevisionRecord) { d.Qualifications[0].Evidence[0].Claim += " changed" },
		"wrong_catalog":     func(_ *Dataset, r *RevisionRecord) { r.CatalogRevisionID = "wrong-catalog" },
		"wrong_schema":      func(_ *Dataset, r *RevisionRecord) { r.SchemaVersion = RegistrySchemaVersion },
		"missing_reference": func(d *Dataset, _ *RevisionRecord) { d.Sources[0].QualificationID = "" },
		"fake_approval":     func(d *Dataset, _ *RevisionRecord) { d.Policies[0].ApprovalID = "not-an-approval" },
	} {
		t.Run(name, func(t *testing.T) {
			d := postgresQualifiedDataset(t)
			r := postgresTestRevision(t, "invalid-"+name, QualifiedRegistrySchemaVersion, d)
			mutate(&d, &r)
			r.ContentSHA256, _ = DatasetSHA256(d)
			if err := store.Stage(ctx, r, d); !errors.Is(err, ErrRevisionInvalid) {
				t.Fatalf("invalid direct-store staging error = %v", err)
			}
		})
	}
	if err := migrator.MigrateTo(ctx, 8); !postgresCode(err, "55000") {
		t.Fatalf("v2 evidence downgrade must refuse atomically: %v", err)
	}
	if err := migrator.VerifySchema(ctx); err != nil {
		t.Fatalf("refused downgrade changed the migration ledger: %v", err)
	}
	assertPostgresDatasetExact(t, store, record, qualified)
	assertPostgresQualifiedEvidence(t, pool, record.ID, qualified, 1)
	active, _, err := store.Active(ctx)
	if err != nil || active.ID != record.ID {
		t.Fatalf("refused downgrade changed active revision: %v", err)
	}
	legacyActivation.PreviousRevisionID, legacyActivation.Rollback = record.ID, true
	legacyActivation.ActivatedAt = testNow().Add(time.Minute)
	if err := store.Activate(ctx, legacyActivation); err != nil {
		t.Fatalf("explicit pointer rollback to retained v1: %v", err)
	}
	if err := migrator.MigrateTo(ctx, 8); !postgresCode(err, "55000") {
		t.Fatalf("inactive v2 evidence must still prevent destructive downgrade: %v", err)
	}
	assertPostgresDatasetExact(t, store, legacyRecord, legacy)
}

func isolatedQualifiedRegistryPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set; only disposable integration databases are supported")
	}
	admin, err := pgxpool.New(t.Context(), databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256([]byte(t.Name() + time.Now().UTC().String()))
	schema := fmt.Sprintf("namaz_t049_%x", digest[:8])
	identifier := pgx.Identifier{schema}.Sanitize()
	if _, err := admin.Exec(t.Context(), "CREATE SCHEMA "+identifier); err != nil {
		admin.Close()
		t.Fatal(err)
	}
	config, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	config.ConnConfig.RuntimeParams["search_path"] = schema
	pool, err := pgxpool.NewWithConfig(t.Context(), config)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		pool.Close()
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if _, err := admin.Exec(ctx, "DROP SCHEMA "+identifier+" CASCADE"); err != nil {
			t.Errorf("remove isolated generated test schema: %v", err)
		}
		admin.Close()
	})
	return pool
}

func postgresTestRevision(t *testing.T, id string, version int, dataset Dataset) RevisionRecord {
	t.Helper()
	hash, err := DatasetSHA256(dataset)
	if err != nil {
		t.Fatal(err)
	}
	return RevisionRecord{ID: id, SchemaVersion: version, CatalogRevisionID: "catalog-1", ContentSHA256: hash,
		CreatedAt: testNow(), CreatedBy: "synthetic-test", Reason: "synthetic PostgreSQL roundtrip"}
}

func postgresQualifiedDataset(t *testing.T) Dataset {
	t.Helper()
	dataset := executableDataset()
	dataset.Authorities, dataset.Sources, dataset.Policies, dataset.TimeTables = nil, nil, nil, nil
	stamp := testNow().Add(-time.Minute).Format(time.RFC3339)
	coverage := domain.DateRange{From: "2026-08-30", To: "2026-08-30"}
	for _, suffix := range []string{"alpha", "beta"} {
		authority := domain.PrayerAuthority{ID: "authority-qualified-" + suffix, Name: "Synthetic " + suffix + " authority", Website: "https://" + suffix + ".example", EvidenceLabel: "CONFIRMED_PUBLIC"}
		source := domain.PrayerSource{ID: "source-qualified-" + suffix, Kind: domain.ProviderKindOfficialFile, AuthorityIDs: []string{authority.ID},
			GeographicScopeID: testScopeID, CanonicalURL: authority.Website + "/calendar", Status: domain.PrayerSourceQualified, FreshThrough: coverage.To}
		q := domain.SourceQualification{SchemaVersion: domain.SourceQualificationSchema, State: "qualified", DecisionSystem: domain.SourceQualificationDecisionSystem,
			QualifiedAt: stamp, Authority: authority, SourceID: source.ID, Kind: source.Kind, CanonicalURL: source.CanonicalURL, Scope: dataset.Scopes[0], CatalogRevision: "catalog-1",
			Timezone: "Europe/Ulyanovsk", Coverage: coverage, FreshThrough: coverage.To,
			Artifact:      domain.RawArtifact{Filename: "synthetic-" + suffix + ".csv", ContentType: "text/csv", ByteLength: 12, SHA256: repeatHex("a"), CapturedAt: stamp},
			Retrieval:     domain.PublicSourceRetrieval{URL: source.CanonicalURL, HTTPStatus: 200, ContentType: "text/csv", ETag: `"synthetic"`},
			ParserVersion: "synthetic-pg/v1", CandidateID: "candidate-" + suffix, NormalizedSHA256: repeatHex("b"), OnsetSHA256: repeatHex("c"), TranscriptionSHA256: repeatHex("d"),
			DiffSHA256: repeatHex("e"), ValidationSHA256: repeatHex("f"), ValidatedDays: 1, TermsAssessment: "public_transport_no_restriction_observed", Unknowns: []string{"Synthetic cadence not published"}}
		for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
			q.Evidence = append(q.Evidence, domain.SourceEvidence{ID: purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: authority.Website + "/" + purpose, RetrievedAt: stamp,
				SHA256: repeatHex("e"), Claim: "Synthetic PostgreSQL protocol evidence: " + purpose})
		}
		q.Comparisons = []domain.SourceValueComparison{{EvidenceID: "value_comparison", Day: domain.PrayerDay{Date: coverage.From, Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00"}}}
		q.WarningResolutions = []domain.SourceWarningResolution{{Code: "synthetic_test_warning", EvidenceID: "time_semantics", Reason: "Synthetic preserved nested proof field"}}
		hash, err := domain.SourceQualificationSHA256(q)
		if err != nil {
			t.Fatal(err)
		}
		q.SHA256, q.ID = hash, "qualification-"+hash[:32]
		if err := q.Validate(); err != nil {
			t.Fatal(err)
		}
		binding := qualification.CatalogBinding{Revision: q.CatalogRevision, SourceRevision: dataset.Cities[0].GeographicRevision, Region: dataset.Regions[0], Cities: dataset.Cities}
		publicContext, err := qualification.PublicDisplayContext(q.Scope, binding, q.Timezone)
		if err != nil {
			t.Fatal(err)
		}
		source.QualificationID = q.ID
		dataset.Authorities = append(dataset.Authorities, authority)
		dataset.Sources = append(dataset.Sources, source)
		dataset.Qualifications = append(dataset.Qualifications, q)
		dataset.TimeTables = append(dataset.TimeTables, domain.TimeTable{ID: "timetable-" + suffix, SourceID: source.ID, GeographicScopeID: testScopeID, MosqueID: publicContext.ID,
			Timezone: q.Timezone, Effective: coverage, PublishedSnapshotID: "snapshot-qualified-" + suffix})
		dataset.Policies = append(dataset.Policies, domain.PrayerPolicy{ID: "policy-" + suffix, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: testScopeID,
			AuthorityIDs: []string{authority.ID}, SourceID: source.ID, TimeTableID: "timetable-" + suffix, Effective: coverage, QualificationID: q.ID})
	}
	if _, err := New(dataset); err != nil {
		t.Fatal(err)
	}
	return dataset
}

func postgresQualifiedActivation(t *testing.T, revisionID, previousID string, dataset Dataset) ActivationRecord {
	t.Helper()
	activation := ActivationRecord{RevisionID: revisionID, PreviousRevisionID: previousID, ActorID: "synthetic-test", Reason: "synthetic qualified publication evidence", ActivatedAt: testNow()}
	for i, table := range dataset.TimeTables {
		q := dataset.Qualifications[i]
		binding := qualification.CatalogBinding{Revision: q.CatalogRevision, SourceRevision: dataset.Cities[0].GeographicRevision, Region: dataset.Regions[0], Cities: dataset.Cities}
		publicContext, err := qualification.PublicDisplayContext(q.Scope, binding, q.Timezone)
		if err != nil {
			t.Fatal(err)
		}
		activation.Snapshots = append(activation.Snapshots, VerifiedSnapshot{ID: table.PublishedSnapshotID, MosqueID: table.MosqueID, Timezone: table.Timezone, Effective: table.Effective,
			PayloadSHA256: repeatHex("b"), SigningKeyID: "synthetic-test-key", VerifiedAt: testNow(), QualificationID: q.ID, QualificationSHA256: q.SHA256, PublicContext: &publicContext})
	}
	return activation
}

func assertPostgresDatasetExact(t *testing.T, store *PostgresRevisionStore, record RevisionRecord, want Dataset) {
	t.Helper()
	loaded, got, err := store.Load(t.Context(), record.ID)
	if err != nil {
		t.Fatal(err)
	}
	hash, err := DatasetSHA256(got)
	if err != nil || hash != record.ContentSHA256 || loaded.ContentSHA256 != hash || loaded.SchemaVersion != record.SchemaVersion {
		t.Fatalf("dataset/record identity lost: %s %v", hash, err)
	}
	// Array order within proof fields is signed content; jsonb storage must not
	// drop nested comparison/evidence/warning/unknown fields or invent approval.
	for _, expected := range want.Qualifications {
		found := false
		for _, actual := range got.Qualifications {
			if actual.ID == expected.ID {
				found = reflect.DeepEqual(expected, actual)
			}
		}
		if !found {
			t.Fatal("complete immutable qualification proof differs after decoding")
		}
	}
	if len(want.Qualifications) != len(got.Qualifications) {
		t.Fatal("qualification count changed")
	}
}

func assertPostgresQualifiedEvidence(t *testing.T, pool *pgxpool.Pool, revisionID string, dataset Dataset, legacyMosqueCount int) {
	t.Helper()
	var proofs, snapshots, approvals, mosques, policyMosques int
	if err := pool.QueryRow(t.Context(), `SELECT
		(SELECT count(*) FROM registry_source_qualifications WHERE revision_id=$1),
		(SELECT count(*) FROM registry_verified_snapshots WHERE revision_id=$1 AND mosque_id IS NULL AND public_context_id IS NOT NULL AND qualification_id IS NOT NULL),
		(SELECT count(*) FROM registry_verified_approvals WHERE revision_id=$1),
		(SELECT count(*) FROM mosques),
		(SELECT count(*) FROM registry_policy_mosques WHERE revision_id=$1)`, revisionID).Scan(&proofs, &snapshots, &approvals, &mosques, &policyMosques); err != nil {
		t.Fatal(err)
	}
	if proofs != len(dataset.Qualifications) || snapshots != len(dataset.TimeTables) || approvals != 0 || mosques != legacyMosqueCount || policyMosques != 0 {
		t.Fatalf("public/legacy branches mixed: proofs=%d snapshots=%d approvals=%d mosques=%d policy_mosques=%d", proofs, snapshots, approvals, mosques, policyMosques)
	}
	for _, q := range dataset.Qualifications {
		var raw []byte
		var hash, sourceID, scopeID, authorityID string
		if err := pool.QueryRow(t.Context(), `SELECT proof_json, qualification_sha256, source_id, scope_id, authority_id
			FROM registry_source_qualifications WHERE revision_id=$1 AND id=$2`, revisionID, q.ID).Scan(&raw, &hash, &sourceID, &scopeID, &authorityID); err != nil {
			t.Fatal(err)
		}
		var decoded domain.SourceQualification
		if err := json.Unmarshal(raw, &decoded); err != nil || !reflect.DeepEqual(decoded, q) || hash != q.SHA256 || sourceID != q.SourceID || scopeID != q.Scope.ID || authorityID != q.Authority.ID {
			t.Fatal("normalized qualification references or full proof JSON changed")
		}
		if strings.Contains(string(raw), "approved_by") {
			t.Fatal("invented human approver in public proof")
		}
	}
}
