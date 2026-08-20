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
		"public_base_url":                 "https://api.example.invalid",
		"pairing_backend":                 "postgres",
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
	repository := devices.NewPostgresPairingRepository(pool)
	manager, err := devices.NewPairingManager(devices.PairingManagerConfig{
		Repository: repository, RateLimitKey: rateKey,
		Now: func() time.Time { return time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC) },
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
			admin_memberships, device_assignments, admin_requests TO namaz_runtime_test;
		GRANT INSERT ON devices, pairing_codes, pairing_rate_buckets, audit_events,
			device_assignments, admin_requests TO namaz_runtime_test;
		GRANT UPDATE ON devices, pairing_codes, pairing_rate_buckets,
			device_assignments TO namaz_runtime_test;
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
