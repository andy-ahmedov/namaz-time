//go:build integration

package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/jackc/pgx/v5/pgxpool"
)

func TestRuntimeLoadsRestartSafePostgresPairingWithoutLiteralSecrets(t *testing.T) {
	databaseURL := os.Getenv("NAMAZ_TEST_POSTGRES_URL")
	if databaseURL == "" {
		t.Skip("NAMAZ_TEST_POSTGRES_URL is not set")
	}
	rateKey := bytes.Repeat([]byte{0x71}, sha256.Size)
	adminIdempotencyKey := bytes.Repeat([]byte{0x72}, sha256.Size)
	t.Setenv("NAMAZ_PAIR_RATE_KEY_TEST", base64.StdEncoding.EncodeToString(rateKey))
	t.Setenv("NAMAZ_ADMIN_IDEMPOTENCY_KEY_TEST", base64.StdEncoding.EncodeToString(adminIdempotencyKey))

	directory := t.TempDir()
	config := map[string]any{
		"public_base_url":  "https://api.example.invalid",
		"pairing_backend":  "postgres",
		"registry_backend": "postgres",
		"device_setup_revision_ids": map[string]string{
			"mosque-runtime-0001": "revision-runtime-staged-0001",
		},
		"database_url_env":                "NAMAZ_DATABASE_URL_TEST",
		"pairing_rate_limit_key_env":      "NAMAZ_PAIR_RATE_KEY_TEST",
		"admin_idempotency_key_env":       "NAMAZ_ADMIN_IDEMPOTENCY_KEY_TEST",
		"pairing_backend_timeout_seconds": 5,
		"pairing_rate_limits": map[string]any{
			"window_seconds": 600, "source_attempts": 20, "device_attempts": 10, "code_attempts": 5,
		},
		"trusted_public_key_files": []string{},
		"snapshots":                []map[string]any{},
		"assignments":              []map[string]any{},
	}
	configBytes, err := json.Marshal(config)
	if err != nil {
		t.Fatalf("encode production config: %v", err)
	}
	configPath := filepath.Join(directory, "api-config.json")
	if err := os.WriteFile(configPath, configBytes, 0o600); err != nil {
		t.Fatalf("write production config: %v", err)
	}

	migrationPool, err := pgxpool.New(t.Context(), databaseURL)
	if err != nil {
		t.Fatalf("connect migration fixture pool: %v", err)
	}
	migrationRepository := devices.NewPostgresPairingRepository(migrationPool)
	if err := migrationRepository.MigrateTo(t.Context(), 0); err != nil {
		migrationPool.Close()
		t.Fatalf("reset runtime schema: %v", err)
	}
	if err := migrationRepository.MigrateUp(t.Context()); err != nil {
		migrationPool.Close()
		t.Fatalf("migrate runtime schema: %v", err)
	}
	runtimeDatabaseURL := createLeastPrivilegeRuntimeRole(t, migrationPool, databaseURL)
	migrationPool.Close()
	t.Setenv("NAMAZ_DATABASE_URL_TEST", runtimeDatabaseURL)

	service, err := loadRuntimeService(configPath)
	if err != nil {
		t.Fatalf("loadRuntimeService() error = %v", err)
	}

	ctx, cancel := context.WithTimeout(t.Context(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatalf("connect fixture pool: %v", err)
	}
	if _, err := pool.Exec(ctx, `
		INSERT INTO mosques (id, name, timezone_id, status, created_at, updated_at)
		VALUES ('mosque-runtime-0001', 'Runtime Mosque', 'Europe/Ulyanovsk', 'active', $1, $1)`,
		time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)); err != nil {
		t.Fatalf("seed runtime mosque: %v", err)
	}
	adminToken := "runtime-admin-token-integration-0001"
	adminTokenHash := sha256.Sum256([]byte(adminToken))
	if _, err := pool.Exec(ctx, `
		INSERT INTO admin_actors (id, display_name, status, created_at)
		VALUES ('actor-runtime-admin-0001', 'Runtime Admin', 'active', $1)`,
		time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)); err != nil {
		t.Fatalf("seed runtime admin actor: %v", err)
	}
	if _, err := pool.Exec(ctx, `
		INSERT INTO admin_credentials (id, actor_id, token_hash, created_at)
		VALUES ('credential-runtime-admin-01', 'actor-runtime-admin-0001', $1, $2)`,
		adminTokenHash[:], time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)); err != nil {
		t.Fatalf("seed runtime admin credential: %v", err)
	}
	if _, err := pool.Exec(ctx, `
		INSERT INTO admin_memberships (actor_id, mosque_id, role, created_at)
		VALUES ('actor-runtime-admin-0001', 'mosque-runtime-0001', 'mosque_admin', $1)`,
		time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)); err != nil {
		t.Fatalf("seed runtime admin membership: %v", err)
	}
	stagedDataset := registry.Dataset{
		Cities: []domain.City{{
			ID: "city-runtime-ulyanovsk", Name: "Ульяновск", CountryCode: "RU", RegionID: "region-runtime-uly",
			SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk",
			GeographicSource: "https://www.geonames.org/479123", GeographicSourceID: "geonames:479123",
			GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
		}},
		Regions: []domain.Region{{ID: "region-runtime-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}},
		Scopes: []domain.GeographicScope{{
			ID: "scope-runtime-uly", Kind: domain.GeographicScopeCity,
			CityID: "city-runtime-ulyanovsk", RegionID: "region-runtime-uly", Description: "runtime staged city",
		}},
		Authorities: []domain.PrayerAuthority{{ID: "authority-runtime-uly", Name: "Runtime authority", EvidenceLabel: "CONFIRMED_PUBLIC"}},
		Sources: []domain.PrayerSource{{
			ID: "source-runtime-uly", Kind: domain.ProviderKindManualImport,
			AuthorityIDs: []string{"authority-runtime-uly"}, GeographicScopeID: "scope-runtime-uly",
			Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
		}},
		Policies: []domain.PrayerPolicy{{
			ID: "policy-runtime-uly", Kind: domain.PrayerPolicyTimeTable,
			GeographicScopeID: "scope-runtime-uly", AuthorityIDs: []string{"authority-runtime-uly"},
			SourceID: "source-runtime-uly", TimeTableID: "timetable-runtime-uly",
			MosqueIDs: []string{"mosque-runtime-0001"}, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			ApprovalID: "approval-runtime-uly",
		}},
		TimeTables: []domain.TimeTable{{
			ID: "timetable-runtime-uly", SourceID: "source-runtime-uly", GeographicScopeID: "scope-runtime-uly",
			MosqueID: "mosque-runtime-0001", Timezone: "Europe/Ulyanovsk",
			Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, PublishedSnapshotID: "snapshot-runtime-uly",
		}},
	}
	stagedSHA256, err := registry.DatasetSHA256(stagedDataset)
	if err != nil {
		t.Fatalf("hash staged runtime registry: %v", err)
	}
	stagedRevision := registry.RevisionRecord{
		ID: "revision-runtime-staged-0001", SchemaVersion: registry.RegistrySchemaVersion,
		CatalogRevisionID: "catalog-runtime-integration", ContentSHA256: stagedSHA256,
		CreatedAt: time.Date(2026, 8, 20, 12, 1, 0, 0, time.UTC), CreatedBy: "actor-runtime-admin-0001",
		Reason: "least privilege staged registry workflow",
	}
	if err := registry.NewPostgresRevisionStore(pool).Stage(ctx, stagedRevision, stagedDataset); err != nil {
		t.Fatalf("stage runtime registry: %v", err)
	}
	repository := devices.NewPostgresPairingRepository(pool)
	manager, err := devices.NewPairingManager(devices.PairingManagerConfig{
		Repository: repository, RateLimitKey: rateKey,
		Now: time.Now,
		RateLimits: devices.PairingRateLimits{
			Window: 10 * time.Minute, SourceAttempts: 20, DeviceAttempts: 10, CodeAttempts: 5,
		},
	})
	if err != nil {
		t.Fatalf("NewPairingManager() error = %v", err)
	}
	issued, err := manager.Issue(ctx, devices.IssuePairingCommand{
		MosqueID: "mosque-runtime-0001", ActorID: "actor-runtime-0001", Reason: "runtime integration",
		RequestID: "request-runtime-issue", ExpiresIn: 10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("Issue() error = %v", err)
	}

	server := httptest.NewServer(service.Handler())
	optionsRequest, err := http.NewRequest(
		http.MethodGet,
		server.URL+"/v1/admin/mosques/mosque-runtime-0001/setup/prayer-policy-options?revision_id="+stagedRevision.ID+"&city_id=city-runtime-ulyanovsk&date=2026-08-30",
		nil,
	)
	if err != nil {
		t.Fatalf("create registry options request: %v", err)
	}
	optionsRequest.Header.Set("Authorization", "Bearer "+adminToken)
	optionsResponse, err := http.DefaultClient.Do(optionsRequest)
	if err != nil {
		t.Fatalf("registry options request: %v", err)
	}
	optionsBody, readErr := io.ReadAll(optionsResponse.Body)
	optionsResponse.Body.Close()
	if readErr != nil || optionsResponse.StatusCode != http.StatusOK ||
		!bytes.Contains(optionsBody, []byte(`"allowed_actions":["request_binding"]`)) {
		t.Fatalf("registry options response = %d %s, read=%v", optionsResponse.StatusCode, optionsBody, readErr)
	}
	bindingRequest, err := http.NewRequest(
		http.MethodPost,
		server.URL+"/v1/admin/mosques/mosque-runtime-0001/setup/prayer-policy-binding-requests",
		bytes.NewBufferString(`{"revision_id":"`+stagedRevision.ID+`","city_id":"city-runtime-ulyanovsk","policy_id":"policy-runtime-uly","date":"2026-08-30","reason":"least privilege explicit policy review"}`),
	)
	if err != nil {
		t.Fatalf("create registry binding request: %v", err)
	}
	bindingRequest.Header.Set("Content-Type", "application/json")
	bindingRequest.Header.Set("Authorization", "Bearer "+adminToken)
	bindingRequest.Header.Set("Idempotency-Key", "idem-runtime-registry-binding-0001")
	bindingResponse, err := http.DefaultClient.Do(bindingRequest)
	if err != nil {
		t.Fatalf("registry binding request: %v", err)
	}
	bindingBody, readErr := io.ReadAll(bindingResponse.Body)
	bindingResponse.Body.Close()
	if readErr != nil || bindingResponse.StatusCode != http.StatusCreated ||
		!bytes.Contains(bindingBody, []byte(`"status":"pending_review"`)) {
		t.Fatalf("registry binding response = %d %s, read=%v", bindingResponse.StatusCode, bindingBody, readErr)
	}
	var runtimeBindingRows int
	if err := pool.QueryRow(ctx, `SELECT count(*) FROM registry_binding_requests WHERE revision_id = $1`, stagedRevision.ID).Scan(&runtimeBindingRows); err != nil || runtimeBindingRows != 1 {
		t.Fatalf("least-privilege registry binding rows = %d, %v", runtimeBindingRows, err)
	}
	adminIssueRequest, err := http.NewRequest(
		http.MethodPost,
		server.URL+"/v1/admin/mosques/mosque-runtime-0001/pairing-codes",
		bytes.NewBufferString(`{"expires_in_seconds":600,"reason":"runtime admin integration"}`),
	)
	if err != nil {
		t.Fatalf("create admin issue request: %v", err)
	}
	adminIssueRequest.Header.Set("Content-Type", "application/json")
	adminIssueRequest.Header.Set("Authorization", "Bearer "+adminToken)
	adminIssueRequest.Header.Set("Idempotency-Key", "idem-runtime-admin-0001")
	adminIssueResponse, err := http.DefaultClient.Do(adminIssueRequest)
	if err != nil {
		t.Fatalf("admin issue request: %v", err)
	}
	adminIssueBody, readErr := io.ReadAll(adminIssueResponse.Body)
	adminIssueResponse.Body.Close()
	if readErr != nil {
		t.Fatalf("read admin issue response: %v", readErr)
	}
	if adminIssueResponse.StatusCode != http.StatusCreated || !bytes.Contains(adminIssueBody, []byte("pairing_code")) {
		t.Fatalf("admin issue response = %d %s", adminIssueResponse.StatusCode, adminIssueBody)
	}
	registrySearchRequest, err := http.NewRequest(
		http.MethodGet,
		server.URL+"/v1/admin/mosques/mosque-runtime-0001/setup/cities?q=Ulyanovsk",
		nil,
	)
	if err != nil {
		t.Fatalf("create registry search request: %v", err)
	}
	registrySearchRequest.Header.Set("Authorization", "Bearer "+adminToken)
	registrySearchResponse, err := http.DefaultClient.Do(registrySearchRequest)
	if err != nil {
		t.Fatalf("registry search request: %v", err)
	}
	registrySearchBody, readErr := io.ReadAll(registrySearchResponse.Body)
	registrySearchResponse.Body.Close()
	if readErr != nil {
		t.Fatalf("read registry search response: %v", readErr)
	}
	if registrySearchResponse.StatusCode != http.StatusOK || !bytes.Contains(registrySearchBody, []byte(`"candidates":[]`)) {
		t.Fatalf("empty active registry search response = %d %s", registrySearchResponse.StatusCode, registrySearchBody)
	}
	pairResponse := postPair(t, server.URL, issued.Code)
	server.Close()
	service.Close()
	if pairResponse.StatusCode != http.StatusOK {
		t.Fatalf("pair response = %d %s", pairResponse.StatusCode, pairResponse.Body)
	}
	var paired struct {
		DeviceID    string `json:"device_id"`
		DeviceToken string `json:"device_token"`
	}
	if err := json.Unmarshal([]byte(pairResponse.Body), &paired); err != nil {
		t.Fatalf("decode pair response: %v", err)
	}
	if paired.DeviceID != issued.DeviceID || paired.DeviceToken == "" {
		t.Fatalf("paired response = %#v", paired)
	}

	restarted, err := loadRuntimeService(configPath)
	if err != nil {
		t.Fatalf("reload runtime service: %v", err)
	}
	defer restarted.Close()
	restartedServer := httptest.NewServer(restarted.Handler())
	defer restartedServer.Close()
	reused := postPair(t, restartedServer.URL, issued.Code)
	if reused.StatusCode != http.StatusBadRequest || !strings.Contains(reused.Body, "pairing_code_invalid") {
		t.Fatalf("reused response = %d %s", reused.StatusCode, reused.Body)
	}
	manifestRequest, err := http.NewRequest(http.MethodGet, restartedServer.URL+"/v1/devices/"+issued.DeviceID+"/manifest", nil)
	if err != nil {
		t.Fatalf("create manifest request: %v", err)
	}
	manifestRequest.Header.Set("Authorization", "Bearer "+paired.DeviceToken)
	manifestResponse, err := http.DefaultClient.Do(manifestRequest)
	if err != nil {
		t.Fatalf("get manifest: %v", err)
	}
	defer manifestResponse.Body.Close()
	if manifestResponse.StatusCode != http.StatusNotFound {
		t.Fatalf("authenticated unassigned manifest status = %d", manifestResponse.StatusCode)
	}
	citySearchRequest, err := http.NewRequest(
		http.MethodGet,
		restartedServer.URL+"/v1/devices/"+issued.DeviceID+"/setup/cities?q="+url.QueryEscape("Ульяновск"),
		nil,
	)
	if err != nil {
		t.Fatalf("create device city search request: %v", err)
	}
	citySearchRequest.Header.Set("Authorization", "Bearer "+paired.DeviceToken)
	citySearchResponse, err := http.DefaultClient.Do(citySearchRequest)
	if err != nil {
		t.Fatalf("device city search request: %v", err)
	}
	citySearchBody, readErr := io.ReadAll(citySearchResponse.Body)
	citySearchResponse.Body.Close()
	if readErr != nil || citySearchResponse.StatusCode != http.StatusOK ||
		!bytes.Contains(citySearchBody, []byte(`"city_id":"city-runtime-ulyanovsk"`)) ||
		!bytes.Contains(citySearchBody, []byte(`"federal_subject_code":"RU-ULY"`)) {
		t.Fatalf("device staged city search = %d %s, read=%v", citySearchResponse.StatusCode, citySearchBody, readErr)
	}
	choicesRequest, err := http.NewRequest(
		http.MethodGet,
		restartedServer.URL+"/v1/devices/"+issued.DeviceID+
			"/setup/schedule-choices?city_id=city-runtime-ulyanovsk&date=2026-08-30",
		nil,
	)
	if err != nil {
		t.Fatalf("create device schedule choices request: %v", err)
	}
	choicesRequest.Header.Set("Authorization", "Bearer "+paired.DeviceToken)
	choicesResponse, err := http.DefaultClient.Do(choicesRequest)
	if err != nil {
		t.Fatalf("device schedule choices request: %v", err)
	}
	choicesBody, readErr := io.ReadAll(choicesResponse.Body)
	choicesResponse.Body.Close()
	if readErr != nil || choicesResponse.StatusCode != http.StatusOK ||
		!bytes.Contains(choicesBody, []byte(`"allowed_actions":["request_selection"]`)) {
		t.Fatalf("device staged schedule choices = %d %s, read=%v", choicesResponse.StatusCode, choicesBody, readErr)
	}
	var choicesDocument struct {
		Choices []struct {
			ID string `json:"choice_id"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(choicesBody, &choicesDocument); err != nil || len(choicesDocument.Choices) != 1 {
		t.Fatalf("decode device schedule choices = %#v, %v", choicesDocument, err)
	}
	deviceProposalRequest, err := http.NewRequest(
		http.MethodPost,
		restartedServer.URL+"/v1/devices/"+issued.DeviceID+"/setup/schedule-choice-requests",
		bytes.NewBufferString(`{"city_id":"city-runtime-ulyanovsk","choice_id":"`+
			choicesDocument.Choices[0].ID+
			`","date":"2026-08-30","interaction_id":"interaction-runtime-tv-0001"}`),
	)
	if err != nil {
		t.Fatalf("create device schedule choice proposal: %v", err)
	}
	deviceProposalRequest.Header.Set("Content-Type", "application/json")
	deviceProposalRequest.Header.Set("Authorization", "Bearer "+paired.DeviceToken)
	deviceProposalResponse, err := http.DefaultClient.Do(deviceProposalRequest)
	if err != nil {
		t.Fatalf("device schedule choice proposal: %v", err)
	}
	deviceProposalBody, readErr := io.ReadAll(deviceProposalResponse.Body)
	deviceProposalResponse.Body.Close()
	if readErr != nil || deviceProposalResponse.StatusCode != http.StatusCreated ||
		!bytes.Contains(deviceProposalBody, []byte(`"status":"pending_review"`)) ||
		!bytes.Contains(deviceProposalBody, []byte(`"origin":"local_tv_operator"`)) {
		t.Fatalf("device schedule choice proposal = %d %s, read=%v", deviceProposalResponse.StatusCode, deviceProposalBody, readErr)
	}
	var deviceProposalRows, deviceProposalAuditRows int
	if err := pool.QueryRow(ctx, `
		SELECT
			(SELECT count(*) FROM device_registry_binding_requests WHERE device_id = $1),
			(SELECT count(*) FROM audit_events WHERE actor_type = 'device' AND actor_id = $1
			 AND action = 'registry.binding_requested_from_tv')`, issued.DeviceID).Scan(
		&deviceProposalRows, &deviceProposalAuditRows,
	); err != nil || deviceProposalRows != 1 || deviceProposalAuditRows != 1 {
		t.Fatalf("least-privilege device proposal rows/audit = %d/%d, %v", deviceProposalRows, deviceProposalAuditRows, err)
	}
	heartbeatRequest, err := http.NewRequest(
		http.MethodPost,
		restartedServer.URL+"/v1/devices/"+issued.DeviceID+"/heartbeat",
		bytes.NewBufferString(`{"sent_at":"2200-01-01T00:00:00Z","app_version":"1.1.0","os_version":"36","model":"Runtime TV","active_snapshot_id":"","sync_status":"ok","coverage_days_remaining":30,"clock_mismatch":false,"timezone_mismatch":false,"storage_health":"ok","memory_health":"ok","boot_mode":"best_effort","kiosk_mode":"none"}`),
	)
	if err != nil {
		t.Fatalf("create heartbeat request: %v", err)
	}
	heartbeatRequest.Header.Set("Content-Type", "application/json")
	heartbeatRequest.Header.Set("Authorization", "Bearer "+paired.DeviceToken)
	heartbeatResponse, err := http.DefaultClient.Do(heartbeatRequest)
	if err != nil {
		t.Fatalf("post heartbeat: %v", err)
	}
	heartbeatResponse.Body.Close()
	if heartbeatResponse.StatusCode != http.StatusNoContent {
		t.Fatalf("least-privilege heartbeat status = %d", heartbeatResponse.StatusCode)
	}
	var healthRows int
	var forcedClockMismatch bool
	if err := pool.QueryRow(ctx, `
		SELECT count(*), bool_and(clock_mismatch)
		FROM device_health WHERE device_id = $1`, issued.DeviceID).Scan(&healthRows, &forcedClockMismatch); err != nil {
		t.Fatalf("read least-privilege heartbeat: %v", err)
	}
	if healthRows != 1 || !forcedClockMismatch {
		t.Fatalf("least-privilege heartbeat state: rows=%d clock_mismatch=%v", healthRows, forcedClockMismatch)
	}
	runtimePool, err := pgxpool.New(ctx, runtimeDatabaseURL)
	if err != nil {
		t.Fatalf("connect runtime role for rollout: %v", err)
	}
	runtimeRepository := devices.NewPostgresPairingRepository(runtimePool)
	runtimeAdmin, err := devices.NewAdminFleetManager(devices.AdminFleetManagerConfig{
		Repository: runtimeRepository, IdempotencyKey: adminIdempotencyKey,
		Now: func() time.Time { return time.Date(2026, 8, 20, 12, 5, 0, 0, time.UTC) },
	})
	if err != nil {
		runtimePool.Close()
		t.Fatalf("configure runtime rollout manager: %v", err)
	}
	runtimePrincipal, err := runtimeAdmin.AuthenticateAdmin(ctx, adminToken)
	if err != nil {
		runtimePool.Close()
		t.Fatalf("authenticate runtime rollout admin: %v", err)
	}
	if err := runtimeAdmin.SetDeviceRolloutGroup(ctx, runtimePrincipal, devices.AdminSetRolloutGroupCommand{
		MosqueID: "mosque-runtime-0001", DeviceID: issued.DeviceID, RolloutGroup: "canary-runtime-01",
		Reason: "least privilege canary", RequestID: "request-runtime-group-01",
		IdempotencyKey: "idem-runtime-group-0001",
	}); err != nil {
		runtimePool.Close()
		t.Fatalf("set least-privilege rollout group: %v", err)
	}
	rollout, err := runtimeAdmin.AssignRolloutGroup(ctx, runtimePrincipal, devices.AdminAssignRolloutGroupCommand{
		MosqueID: "mosque-runtime-0001", RolloutGroup: "canary-runtime-01",
		SnapshotID:     "synthetic-runtime-snapshot-01",
		SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-runtime-snapshot-01",
		SnapshotSHA256: strings.Repeat("a", 64), SigningKeyID: "runtime-test-key-01",
		SnapshotMosqueID: "mosque-runtime-0001", SnapshotTimezone: "Europe/Ulyanovsk",
		Reason: "least privilege rollout", RequestID: "request-runtime-rollout-01",
		IdempotencyKey: "idem-runtime-rollout-0001",
	})
	if err != nil || rollout.DeviceCount != 1 || len(rollout.Assignments) != 1 || rollout.Assignments[0].ManifestVersion != 1 {
		runtimePool.Close()
		t.Fatalf("least-privilege rollout = %#v, %v", rollout, err)
	}
	supportBundle, err := runtimeAdmin.GetDeviceSupportBundle(
		ctx, runtimePrincipal, "mosque-runtime-0001", issued.DeviceID,
	)
	runtimePool.Close()
	if err != nil {
		t.Fatalf("read least-privilege support bundle: %v", err)
	}
	if supportBundle.Assignment == nil || supportBundle.Assignment.SnapshotID != "synthetic-runtime-snapshot-01" {
		t.Fatalf("least-privilege support assignment = %#v", supportBundle.Assignment)
	}
	if supportBundle.Health == nil || !supportBundle.Health.ClockMismatch {
		t.Fatalf("least-privilege support health = %#v", supportBundle.Health)
	}
	var storedRolloutGroup, storedRolloutSnapshot string
	if err := pool.QueryRow(ctx, `
		SELECT d.rollout_group, a.snapshot_id
		FROM devices d JOIN device_assignments a ON a.device_id = d.id
		WHERE d.id = $1`, issued.DeviceID).Scan(&storedRolloutGroup, &storedRolloutSnapshot); err != nil {
		t.Fatalf("read least-privilege rollout: %v", err)
	}
	if storedRolloutGroup != "canary-runtime-01" || storedRolloutSnapshot != "synthetic-runtime-snapshot-01" {
		t.Fatalf("least-privilege rollout state = %q/%q", storedRolloutGroup, storedRolloutSnapshot)
	}

	pool.Close()

	config["database_url"] = databaseURL
	invalidBytes, err := json.Marshal(config)
	if err != nil {
		t.Fatalf("encode literal-secret config: %v", err)
	}
	if err := os.WriteFile(configPath, invalidBytes, 0o600); err != nil {
		t.Fatalf("write literal-secret config: %v", err)
	}
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("runtime accepted a literal database URL field")
	}
}

func createLeastPrivilegeRuntimeRole(t *testing.T, ownerPool *pgxpool.Pool, ownerURL string) string {
	t.Helper()
	const role = "namaz_runtime_test"
	const password = "runtime-test-only"
	if _, err := ownerPool.Exec(t.Context(), `CREATE ROLE namaz_runtime_test LOGIN PASSWORD 'runtime-test-only'`); err != nil {
		t.Fatalf("create runtime database role: %v", err)
	}
	if _, err := ownerPool.Exec(t.Context(), `
		GRANT USAGE ON SCHEMA public TO namaz_runtime_test;
		GRANT SELECT ON schema_migrations, mosques, devices, pairing_codes,
			pairing_rate_buckets, audit_events, admin_actors, admin_credentials,
			admin_memberships, device_assignments, admin_requests, device_health TO namaz_runtime_test;
		GRANT SELECT ON registry_revisions, registry_regions, registry_cities,
			registry_city_aliases, registry_scopes, registry_authorities, registry_sources,
			registry_source_authorities, registry_source_qualifications, registry_policies, registry_policy_authorities,
			registry_policy_mosques, registry_calculation_profiles, registry_timetables,
			registry_source_overrides, registry_source_override_fields,
			registry_timetable_overrides, registry_active_revision, registry_audit_events,
			registry_verified_approvals, registry_verified_snapshots,
			registry_binding_requests, device_registry_binding_requests TO namaz_runtime_test;
		GRANT INSERT ON devices, pairing_codes, pairing_rate_buckets, audit_events,
			device_assignments, admin_requests, device_health,
			registry_binding_requests, device_registry_binding_requests TO namaz_runtime_test;
		GRANT UPDATE ON devices, pairing_codes, pairing_rate_buckets,
			device_assignments, device_health TO namaz_runtime_test;
		GRANT DELETE ON pairing_rate_buckets TO namaz_runtime_test;
	`); err != nil {
		t.Fatalf("grant runtime database role: %v", err)
	}
	parsed, err := url.Parse(ownerURL)
	if err != nil {
		t.Fatalf("parse owner database URL: %v", err)
	}
	parsed.User = url.UserPassword(role, password)
	runtimeURL := parsed.String()
	runtimePool, err := pgxpool.New(t.Context(), runtimeURL)
	if err != nil {
		t.Fatalf("connect runtime database role: %v", err)
	}
	defer runtimePool.Close()
	if _, err := runtimePool.Exec(t.Context(), `INSERT INTO admin_actors (id, display_name, status, created_at) VALUES ('actor-forbidden-runtime', 'forbidden', 'active', clock_timestamp())`); err == nil {
		t.Fatal("runtime database role could provision an admin actor")
	}
	if _, err := runtimePool.Exec(t.Context(), `ALTER TABLE audit_events DISABLE TRIGGER audit_events_append_only`); err == nil {
		t.Fatal("runtime database role could disable an audit trigger")
	}
	var canInsertBindingRequest, canUpdateBindingRequest, canInsertDeviceBindingRequest, canUpdateDeviceBindingRequest, canInsertRegistryRevision, canUpdateActiveRevision bool
	if err := runtimePool.QueryRow(t.Context(), `
		SELECT
			has_table_privilege(current_user, 'registry_binding_requests', 'INSERT'),
			has_table_privilege(current_user, 'registry_binding_requests', 'UPDATE'),
			has_table_privilege(current_user, 'device_registry_binding_requests', 'INSERT'),
			has_table_privilege(current_user, 'device_registry_binding_requests', 'UPDATE'),
			has_table_privilege(current_user, 'registry_revisions', 'INSERT'),
			has_table_privilege(current_user, 'registry_active_revision', 'UPDATE')`).Scan(
		&canInsertBindingRequest, &canUpdateBindingRequest, &canInsertDeviceBindingRequest,
		&canUpdateDeviceBindingRequest, &canInsertRegistryRevision, &canUpdateActiveRevision,
	); err != nil {
		t.Fatalf("inspect registry workflow privileges: %v", err)
	}
	if !canInsertBindingRequest || canUpdateBindingRequest || !canInsertDeviceBindingRequest ||
		canUpdateDeviceBindingRequest || canInsertRegistryRevision || canUpdateActiveRevision {
		t.Fatalf(
			"registry workflow privileges: insert_binding=%v update_binding=%v insert_device_binding=%v update_device_binding=%v insert_revision=%v update_active=%v",
			canInsertBindingRequest, canUpdateBindingRequest, canInsertDeviceBindingRequest,
			canUpdateDeviceBindingRequest, canInsertRegistryRevision, canUpdateActiveRevision,
		)
	}
	return runtimeURL
}

type apiTestResponse struct {
	StatusCode int
	Body       string
}

func postPair(t *testing.T, serverURL, code string) apiTestResponse {
	t.Helper()
	response, err := http.Post(
		serverURL+"/v1/devices/pair", "application/json",
		bytes.NewBufferString(`{"pairing_code":"`+code+`","device":{"app_version":"1.0.0","os_version":"35","model":"Runtime TV"}}`),
	)
	if err != nil {
		t.Fatalf("post pair: %v", err)
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("read pair body: %v", err)
	}
	return apiTestResponse{StatusCode: response.StatusCode, Body: string(body)}
}
