package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestComponentName(t *testing.T) {
	t.Parallel()

	if componentName != "api" {
		t.Fatalf("componentName = %q, want api", componentName)
	}
}

func TestLoadRuntimeServiceRequiresPrivateConfigAndServesFixture(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	snapshotSource := filepath.Join("..", "..", "fixtures", "verification", "synthetic-signed-snapshot.json")
	keySource := filepath.Join("..", "..", "fixtures", "verification", "phase1-public-key.json")
	snapshot, err := os.ReadFile(snapshotSource)
	if err != nil {
		t.Fatalf("read snapshot fixture: %v", err)
	}
	key, err := os.ReadFile(keySource)
	if err != nil {
		t.Fatalf("read key fixture: %v", err)
	}
	if err := os.WriteFile(filepath.Join(directory, "snapshot.json"), snapshot, 0o600); err != nil {
		t.Fatalf("write snapshot: %v", err)
	}
	if err := os.WriteFile(filepath.Join(directory, "public-key.json"), key, 0o600); err != nil {
		t.Fatalf("write key: %v", err)
	}
	config := map[string]any{
		"public_base_url":          "https://api.example.invalid",
		"pairing_fixture_mode":     "ephemeral-test-only",
		"trusted_public_key_files": []string{"public-key.json"},
		"pairing_fixtures": []map[string]any{{
			"code": "ULSK-TEST-2026", "device_id": "device-fixture-0001",
			"token": "fixture-device-token-not-production",
			"mosque": map[string]any{
				"id": "synthetic-verification-mosque", "name": "Synthetic verification fixture",
				"timezone": "Europe/Ulyanovsk",
			},
		}},
		"snapshots": []map[string]any{{
			"snapshot_id": "synthetic-android-verification-v1", "file": "snapshot.json",
			"sha256":         "5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d",
			"signing_key_id": "phase1-fixture-key-2026-08",
		}},
		"assignments": []map[string]any{{
			"device_id": "device-fixture-0001", "manifest_version": 1,
			"snapshot_id":     "synthetic-android-verification-v1",
			"snapshot_url":    "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
			"snapshot_sha256": "5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d",
			"signing_key_id":  "phase1-fixture-key-2026-08",
		}},
	}
	configBytes, err := json.Marshal(config)
	if err != nil {
		t.Fatalf("encode config: %v", err)
	}
	configPath := filepath.Join(directory, "api-config.json")
	if err := os.WriteFile(configPath, configBytes, 0o600); err != nil {
		t.Fatalf("write config: %v", err)
	}

	service, err := loadRuntimeService(configPath)
	if err != nil {
		t.Fatalf("loadRuntimeService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)
	pairResponse, err := http.Post(
		server.URL+"/v1/devices/pair",
		"application/json",
		bytes.NewBufferString(`{"pairing_code":"ULSK-TEST-2026","device":{"app_version":"0.2.0-shell","os_version":"35","model":"test"}}`),
	)
	if err != nil {
		t.Fatalf("pair request: %v", err)
	}
	defer pairResponse.Body.Close()
	body, err := io.ReadAll(pairResponse.Body)
	if err != nil {
		t.Fatalf("read pair response: %v", err)
	}
	if pairResponse.StatusCode != http.StatusOK || !bytes.Contains(body, []byte("device-fixture-0001")) {
		t.Fatalf("pair response = %d %s", pairResponse.StatusCode, body)
	}

	config["pairing_fixture_mode"] = "production"
	invalidModeBytes, err := json.Marshal(config)
	if err != nil {
		t.Fatalf("encode invalid fixture mode: %v", err)
	}
	if err := os.WriteFile(configPath, invalidModeBytes, 0o600); err != nil {
		t.Fatalf("write invalid fixture mode: %v", err)
	}
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("runtime accepted pairing fixtures without explicit ephemeral test-only mode")
	}
	config["pairing_fixture_mode"] = "ephemeral-test-only"
	configBytes, err = json.Marshal(config)
	if err != nil {
		t.Fatalf("restore config encoding: %v", err)
	}
	if err := os.WriteFile(configPath, configBytes, 0o600); err != nil {
		t.Fatalf("restore config: %v", err)
	}
	pairingFixtures := config["pairing_fixtures"].([]map[string]any)
	pairingFixtures[0]["token"] = strings.Repeat("t", 4097)
	oversizedBytes, err := json.Marshal(config)
	if err != nil {
		t.Fatalf("encode oversized credential config: %v", err)
	}
	if err := os.WriteFile(configPath, oversizedBytes, 0o600); err != nil {
		t.Fatalf("write oversized credential config: %v", err)
	}
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("runtime accepted a credential outside the OpenAPI bounds")
	}
	pairingFixtures[0]["token"] = "fixture-device-token-not-production"
	configBytes, err = json.Marshal(config)
	if err != nil {
		t.Fatalf("restore bounded config encoding: %v", err)
	}
	if err := os.WriteFile(configPath, configBytes, 0o600); err != nil {
		t.Fatalf("restore bounded config: %v", err)
	}

	if err := os.Chmod(configPath, 0o644); err != nil {
		t.Fatalf("chmod config: %v", err)
	}
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("world-readable credential config was accepted")
	}
}

func TestRunRequiresExplicitRuntimeConfig(t *testing.T) {
	t.Parallel()

	if err := run([]string{}, io.Discard); err == nil {
		t.Fatal("run() accepted missing -config")
	}
}
