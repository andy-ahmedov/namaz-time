package main

import (
	"bytes"
	"crypto/ed25519"
	"encoding/base64"
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
	keySource := filepath.Join("..", "..", "fixtures", "verification", "phase1-trust-bundle.json")
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
	if err := os.WriteFile(filepath.Join(directory, "trust-bundle.json"), key, 0o600); err != nil {
		t.Fatalf("write key: %v", err)
	}
	config := map[string]any{
		"public_base_url":      "https://api.example.invalid",
		"pairing_fixture_mode": "ephemeral-test-only",
		"trust_bundle_file":    "trust-bundle.json",
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

func TestProductionTrustBundleRequiresPinnedMinimumRevision(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	testBundleFile, stagingBundleFile := writeComparisonTrustBundles(t, directory)
	bundle, err := os.ReadFile(filepath.Join("..", "..", "fixtures", "verification", "phase1-trust-bundle.json"))
	if err != nil {
		t.Fatal(err)
	}
	bundle = []byte(strings.Replace(string(bundle), `"environment": "test"`, `"environment": "production"`, 1))
	if err := os.WriteFile(filepath.Join(directory, "trust.json"), bundle, 0o600); err != nil {
		t.Fatal(err)
	}
	configPath := filepath.Join(directory, "api.json")
	writeConfig := func(minimum uint64) {
		t.Helper()
		data, err := json.Marshal(map[string]any{
			"trust_bundle_file": "trust.json", "minimum_trust_bundle_revision": minimum,
			"test_trust_bundle_file": testBundleFile, "staging_trust_bundle_file": stagingBundleFile,
		})
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(configPath, data, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	writeConfig(0)
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("production trust bundle was accepted without pinned revision")
	}
	writeConfig(2)
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("trust bundle below pinned revision was accepted")
	}
	writeConfig(1)
	service, err := loadRuntimeService(configPath)
	if err != nil {
		t.Fatalf("pinned production bundle rejected: %v", err)
	}
	service.Close()
}

func TestRuntimeRequiresMonotonicPreviousTrustBundle(t *testing.T) {
	t.Parallel()

	directory := t.TempDir()
	testBundleFile, stagingBundleFile := writeComparisonTrustBundles(t, directory)
	fixture, err := os.ReadFile(filepath.Join("..", "..", "fixtures", "verification", "phase1-trust-bundle.json"))
	if err != nil {
		t.Fatal(err)
	}
	previous := strings.Replace(string(fixture), `"environment": "test"`, `"environment": "production"`, 1)
	current := strings.Replace(previous, `"revision": 1`, `"revision": 2`, 1)
	if err := os.WriteFile(filepath.Join(directory, "previous.json"), []byte(previous), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "current.json"), []byte(current), 0o600); err != nil {
		t.Fatal(err)
	}
	configPath := filepath.Join(directory, "api.json")
	write := func(previousFile string) {
		t.Helper()
		data, marshalErr := json.Marshal(map[string]any{
			"trust_bundle_file": "current.json", "previous_trust_bundle_file": previousFile,
			"minimum_trust_bundle_revision": 2,
			"test_trust_bundle_file":        testBundleFile, "staging_trust_bundle_file": stagingBundleFile,
		})
		if marshalErr != nil {
			t.Fatal(marshalErr)
		}
		if writeErr := os.WriteFile(configPath, data, 0o600); writeErr != nil {
			t.Fatal(writeErr)
		}
	}
	write("")
	if _, err := loadRuntimeService(configPath); err == nil {
		t.Fatal("revision 2 trust bundle was accepted without its predecessor")
	}
	write("previous.json")
	service, err := loadRuntimeService(configPath)
	if err != nil {
		t.Fatalf("valid trust transition rejected: %v", err)
	}
	service.Close()
}

func writeComparisonTrustBundles(t *testing.T, directory string) (string, string) {
	t.Helper()
	write := func(environment, keyID string, fill byte) string {
		t.Helper()
		name := environment + "-trust.json"
		document := map[string]any{
			"schema_version": "1.0", "revision": 1, "environment": environment,
			"generated_at": "2026-08-20T00:00:00Z",
			"keys": []map[string]any{{
				"key_id": keyID, "algorithm": "ed25519",
				"public_key_ed25519_base64": base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{fill}, ed25519.PublicKeySize)),
				"status":                    "active", "not_before": "2026-08-20T00:00:00Z",
			}},
		}
		encoded, err := json.Marshal(document)
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(directory, name), encoded, 0o600); err != nil {
			t.Fatal(err)
		}
		return name
	}
	return write("test", "test-comparison-key", 0x31), write("staging", "staging-comparison-key", 0x32)
}
