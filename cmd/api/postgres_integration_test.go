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
	t.Setenv("NAMAZ_DATABASE_URL_TEST", databaseURL)
	t.Setenv("NAMAZ_PAIR_RATE_KEY_TEST", base64.StdEncoding.EncodeToString(rateKey))

	directory := t.TempDir()
	config := map[string]any{
		"public_base_url":                 "https://api.example.invalid",
		"pairing_backend":                 "postgres",
		"database_url_env":                "NAMAZ_DATABASE_URL_TEST",
		"pairing_rate_limit_key_env":      "NAMAZ_PAIR_RATE_KEY_TEST",
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
