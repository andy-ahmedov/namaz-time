package devices

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	registrydomain "github.com/andy-ahmedov/namaz-time/internal/registry"
)

const (
	testDeviceID = "device-fixture-0001"
	testToken    = "fixture-device-token-not-production"
	testCode     = "ULSK-TEST-2026"
	testKeyID    = "phase1-fixture-key-2026-08"
)

func TestDeviceReadPathPairsOnceAndServesCacheableImmutableSnapshot(t *testing.T) {
	t.Parallel()

	config := validServiceConfig(t)
	serverTime := time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC)
	manifestRequests := 0
	config.Now = func() time.Time {
		current := serverTime.Add(time.Duration(manifestRequests) * time.Minute)
		manifestRequests++
		return current
	}
	snapshot := config.Snapshots[0].Bytes
	hash, err := hex.DecodeString(config.Snapshots[0].SHA256)
	if err != nil {
		t.Fatalf("decode fixture hash: %v", err)
	}
	hashHex := config.Snapshots[0].SHA256
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	pairBody := []byte(`{"pairing_code":"ULSK-TEST-2026","device":{"app_version":"0.2.0-shell","os_version":"35","model":"Robolectric"}}`)
	pairResponse := request(t, http.MethodPost, server.URL+"/v1/devices/pair", pairBody, "", "")
	if pairResponse.StatusCode != http.StatusOK {
		t.Fatalf("pair status = %d, body = %s", pairResponse.StatusCode, pairResponse.Body)
	}
	if pairResponse.Header.Get("Cache-Control") != "no-store" {
		t.Fatalf("pair Cache-Control = %q", pairResponse.Header.Get("Cache-Control"))
	}
	var paired struct {
		DeviceID    string         `json:"device_id"`
		DeviceToken string         `json:"device_token"`
		ManifestURL string         `json:"manifest_url"`
		Mosque      MosqueIdentity `json:"mosque"`
	}
	decodeJSON(t, pairResponse.Body, &paired)
	if paired.DeviceID != testDeviceID || paired.DeviceToken != testToken {
		t.Fatalf("pair identity = %#v", paired)
	}
	if paired.ManifestURL != "/v1/devices/"+testDeviceID+"/manifest" {
		t.Fatalf("manifest_url = %q", paired.ManifestURL)
	}
	if paired.Mosque.ID != "synthetic-verification-mosque" || paired.Mosque.Timezone != "Europe/Ulyanovsk" {
		t.Fatalf("mosque = %#v", paired.Mosque)
	}

	reused := request(t, http.MethodPost, server.URL+"/v1/devices/pair", pairBody, "", "")
	invalid := request(
		t,
		http.MethodPost,
		server.URL+"/v1/devices/pair",
		[]byte(`{"pairing_code":"WRONG-CODE","device":{"app_version":"0.2.0-shell","os_version":"35","model":"Robolectric"}}`),
		"",
		"",
	)
	if reused.StatusCode != http.StatusBadRequest || invalid.StatusCode != http.StatusBadRequest || reused.Body != invalid.Body {
		t.Fatalf("pair failures must be indistinguishable: reused=%d %s invalid=%d %s", reused.StatusCode, reused.Body, invalid.StatusCode, invalid.Body)
	}

	manifestURL := server.URL + "/v1/devices/" + testDeviceID + "/manifest"
	unauthorized := request(t, http.MethodGet, manifestURL, nil, "wrong-token", "")
	if unauthorized.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unauthorized manifest status = %d", unauthorized.StatusCode)
	}
	manifestResponse := request(t, http.MethodGet, manifestURL, nil, testToken, "")
	if manifestResponse.StatusCode != http.StatusOK {
		t.Fatalf("manifest status = %d, body = %s", manifestResponse.StatusCode, manifestResponse.Body)
	}
	manifestETag := manifestResponse.Header.Get("ETag")
	if manifestETag == "" {
		t.Fatal("manifest ETag is empty")
	}
	if manifestResponse.Header.Get("Date") != serverTime.Add(time.Minute).Format(http.TimeFormat) {
		t.Fatalf("manifest Date = %q", manifestResponse.Header.Get("Date"))
	}
	var manifest DeviceManifest
	decodeJSON(t, manifestResponse.Body, &manifest)
	if manifest.SnapshotID != "synthetic-android-verification-v1" || manifest.SnapshotSHA256 != hashHex || manifest.SnapshotByteLength != int64(len(snapshot)) || manifest.SigningKeyID != testKeyID {
		t.Fatalf("manifest = %#v", manifest)
	}
	unchanged := request(t, http.MethodGet, manifestURL, nil, testToken, manifestETag)
	if unchanged.StatusCode != http.StatusNotModified || unchanged.Body != "" {
		t.Fatalf("unchanged manifest = %d %q", unchanged.StatusCode, unchanged.Body)
	}
	if unchanged.Header.Get("Date") != serverTime.Add(2*time.Minute).Format(http.TimeFormat) {
		t.Fatalf("unchanged manifest Date = %q", unchanged.Header.Get("Date"))
	}

	snapshotURL := server.URL + "/v1/snapshots/synthetic-android-verification-v1"
	snapshotResponse := request(t, http.MethodGet, snapshotURL, nil, testToken, "")
	if snapshotResponse.StatusCode != http.StatusOK || snapshotResponse.Body != string(snapshot) {
		t.Fatalf("snapshot response = %d, %d bytes", snapshotResponse.StatusCode, len(snapshotResponse.Body))
	}
	if snapshotResponse.Header.Get("ETag") != `"`+hashHex+`"` {
		t.Fatalf("snapshot ETag = %q", snapshotResponse.Header.Get("ETag"))
	}
	if snapshotResponse.Header.Get("Digest") != "sha-256="+base64.StdEncoding.EncodeToString(hash) {
		t.Fatalf("snapshot Digest = %q", snapshotResponse.Header.Get("Digest"))
	}
	cachedSnapshot := request(t, http.MethodGet, snapshotURL, nil, testToken, snapshotResponse.Header.Get("ETag"))
	if cachedSnapshot.StatusCode != http.StatusNotModified || cachedSnapshot.Body != "" {
		t.Fatalf("cached snapshot = %d %q", cachedSnapshot.StatusCode, cachedSnapshot.Body)
	}
}

func TestDeviceReadPathFailsClosedOnInvalidConfigurationAndRequests(t *testing.T) {
	t.Parallel()

	snapshot := mustReadSnapshot(t)
	hash := sha256.Sum256(snapshot)
	hashHex := hex.EncodeToString(hash[:])
	_, err := NewService(ServiceConfig{
		TrustedPublicKeys: mustReadPublicKeys(t),
		Assignments: []DeviceAssignment{{
			DeviceID: testDeviceID, ManifestVersion: 1,
			SnapshotID: "missing-snapshot", SnapshotURL: "http://insecure.invalid/snapshot",
			SnapshotSHA256: hashHex, SigningKeyID: testKeyID,
		}},
	})
	if err == nil {
		t.Fatal("NewService() accepted an insecure/dangling assignment")
	}
	tampered := append([]byte(nil), snapshot...)
	tampered[bytes.IndexByte(tampered, 'S')] = 'X'
	tamperedHash := sha256.Sum256(tampered)
	_, err = NewService(ServiceConfig{
		TrustedPublicKeys: mustReadPublicKeys(t),
		Snapshots: []SnapshotArtifact{{
			ID:           "synthetic-android-verification-v1",
			Bytes:        tampered,
			SHA256:       hex.EncodeToString(tamperedHash[:]),
			SigningKeyID: testKeyID,
		}},
	})
	if err == nil {
		t.Fatal("NewService() accepted raw-hash-consistent but signature-invalid bytes")
	}

	wrongMosque := validServiceConfig(t)
	wrongMosque.PairingFixtures[0].Mosque.ID = "different-mosque"
	if _, err := NewService(wrongMosque); err == nil {
		t.Fatal("NewService() accepted a signed snapshot assigned to a different mosque")
	}

	crossOrigin := validServiceConfig(t)
	crossOrigin.Assignments[0].SnapshotURL = "https://cdn.example.invalid/v1/snapshots/synthetic-android-verification-v1"
	if _, err := NewService(crossOrigin); err == nil {
		t.Fatal("NewService() accepted a snapshot URL outside the bearer API origin")
	}

	duplicateCredentials := validServiceConfig(t)
	duplicateCredentials.PairingFixtures = append(
		duplicateCredentials.PairingFixtures,
		PairingFixture{
			Code: "SECOND-CODE", DeviceID: "device-fixture-0002", Token: testToken,
			Mosque: duplicateCredentials.PairingFixtures[0].Mosque,
		},
	)
	if _, err := NewService(duplicateCredentials); err == nil {
		t.Fatal("NewService() accepted duplicate bearer credentials")
	}
	duplicateCodes := validServiceConfig(t)
	duplicateCodes.PairingFixtures = append(
		duplicateCodes.PairingFixtures,
		PairingFixture{
			Code: testCode, DeviceID: "device-fixture-0002", Token: "different-device-token-not-production",
			Mosque: duplicateCodes.PairingFixtures[0].Mosque,
		},
	)
	if _, err := NewService(duplicateCodes); err == nil {
		t.Fatal("NewService() accepted duplicate pairing codes")
	}

	boundCases := []struct {
		name   string
		mutate func(*ServiceConfig)
	}{
		{"oversized token", func(config *ServiceConfig) { config.PairingFixtures[0].Token = strings.Repeat("t", 4097) }},
		{"oversized mosque ID", func(config *ServiceConfig) { config.PairingFixtures[0].Mosque.ID = strings.Repeat("m", 129) }},
		{"oversized mosque name", func(config *ServiceConfig) { config.PairingFixtures[0].Mosque.Name = strings.Repeat("n", 241) }},
		{"oversized signing key ID", func(config *ServiceConfig) { config.Assignments[0].SigningKeyID = strings.Repeat("k", 129) }},
		{"oversized minimum app version", func(config *ServiceConfig) { config.Assignments[0].MinimumAppVersion = strings.Repeat("1", 65) }},
	}
	for _, testCase := range boundCases {
		t.Run(testCase.name, func(t *testing.T) {
			config := validServiceConfig(t)
			testCase.mutate(&config)
			if _, err := NewService(config); err == nil {
				t.Fatalf("NewService() accepted %s", testCase.name)
			}
		})
	}
	if _, err := NewService(ServiceConfig{BackendTimeout: time.Second}); err == nil {
		t.Fatal("NewService() accepted a backend timeout without a persistent backend")
	}
	if _, err := NewService(ServiceConfig{
		PairingBackend: &recordingHTTPPairingBackend{}, BackendTimeout: 31 * time.Second,
	}); err == nil {
		t.Fatal("NewService() accepted an excessive backend timeout")
	}

	service, err := NewService(ServiceConfig{})
	if err != nil {
		t.Fatalf("NewService(empty) error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)
	unknownField := request(
		t,
		http.MethodPost,
		server.URL+"/v1/devices/pair",
		[]byte(`{"pairing_code":"123456","device":{"app_version":"1","os_version":"1","model":"x"},"unexpected":true}`),
		"",
		"",
	)
	if unknownField.StatusCode != http.StatusBadRequest {
		t.Fatalf("unknown-field status = %d, body = %s", unknownField.StatusCode, unknownField.Body)
	}
	missingSnapshot := request(t, http.MethodGet, server.URL+"/v1/snapshots/not-found", nil, testToken, "")
	if missingSnapshot.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unknown token must not reveal snapshot existence: %d", missingSnapshot.StatusCode)
	}
}

func TestProductionPairingBackendPreservesPublicContractAndFailureBoundaries(t *testing.T) {
	t.Parallel()

	backend := &recordingHTTPPairingBackend{
		provisioning: PairingProvisioning{
			DeviceID: "device-production-0001", Token: "production-token-returned-once-0001",
			Mosque: MosqueIdentity{ID: "mosque-production-0001", Name: "Production Mosque", Timezone: "Europe/Ulyanovsk"},
		},
		principal: DevicePrincipal{
			DeviceID: "device-production-0001",
			Mosque:   MosqueIdentity{ID: "mosque-production-0001", Name: "Production Mosque", Timezone: "Europe/Ulyanovsk"},
		},
	}
	service, err := NewService(ServiceConfig{
		PublicBaseURL:  "https://api.example.invalid",
		PairingBackend: backend,
	})
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	duplicatePairingCode := request(
		t, http.MethodPost, server.URL+"/v1/devices/pair",
		[]byte(`{"pairing_code":"WRONG-CODE","pairing_code":"ABCDEFGHIJKLMNOPQRSTUVWX26","device":{"app_version":"1.0.0","os_version":"35","model":"Android TV"}}`),
		"", "",
	)
	if duplicatePairingCode.StatusCode != http.StatusBadRequest || backend.attempt.Code != "" {
		t.Fatalf("duplicate pairing code reached backend: status=%d attempt=%#v", duplicatePairingCode.StatusCode, backend.attempt)
	}

	pairBody := []byte(`{"pairing_code":"ABCDEFGHIJKLMNOPQRSTUVWX26","device":{"app_version":"1.0.0","os_version":"35","model":"Android TV"}}`)
	paired := request(t, http.MethodPost, server.URL+"/v1/devices/pair", pairBody, "", "")
	if paired.StatusCode != http.StatusOK || !strings.Contains(paired.Body, backend.provisioning.Token) {
		t.Fatalf("pair response = %d %s", paired.StatusCode, paired.Body)
	}
	if backend.attempt.Code == "" || backend.attempt.SourceAddress == "" || !validIdentifier(backend.attempt.RequestID) {
		t.Fatalf("backend attempt = %#v", backend.attempt)
	}

	manifest := request(
		t, http.MethodGet, server.URL+"/v1/devices/device-production-0001/manifest",
		nil, backend.provisioning.Token, "",
	)
	if manifest.StatusCode != http.StatusNotFound {
		t.Fatalf("unassigned production manifest status = %d", manifest.StatusCode)
	}
	crossDevice := request(
		t, http.MethodGet, server.URL+"/v1/devices/device-production-0002/manifest",
		nil, backend.provisioning.Token, "",
	)
	if crossDevice.StatusCode != http.StatusUnauthorized {
		t.Fatalf("cross-device manifest status = %d", crossDevice.StatusCode)
	}
	backend.authErr = errors.New("database unavailable")
	infrastructureFailure := request(
		t, http.MethodGet, server.URL+"/v1/devices/device-production-0001/manifest",
		nil, backend.provisioning.Token, "",
	)
	if infrastructureFailure.StatusCode != http.StatusInternalServerError ||
		!strings.Contains(infrastructureFailure.Body, `"retryable":true`) {
		t.Fatalf("auth infrastructure response = %d %s", infrastructureFailure.StatusCode, infrastructureFailure.Body)
	}
	snapshotInfrastructureFailure := request(
		t, http.MethodGet, server.URL+"/v1/snapshots/synthetic-android-verification-v1",
		nil, backend.provisioning.Token, "",
	)
	if snapshotInfrastructureFailure.StatusCode != http.StatusInternalServerError ||
		!strings.Contains(snapshotInfrastructureFailure.Body, `"retryable":true`) {
		t.Fatalf("snapshot auth infrastructure response = %d %s", snapshotInfrastructureFailure.StatusCode, snapshotInfrastructureFailure.Body)
	}
	backend.authErr = ErrDeviceUnauthorized
	rejectedCredential := request(
		t, http.MethodGet, server.URL+"/v1/devices/device-production-0001/manifest",
		nil, backend.provisioning.Token, "",
	)
	if rejectedCredential.StatusCode != http.StatusUnauthorized {
		t.Fatalf("rejected credential status = %d", rejectedCredential.StatusCode)
	}
	backend.authErr = nil

	backend.pairErr = ErrPairingInvalid
	invalid := request(t, http.MethodPost, server.URL+"/v1/devices/pair", pairBody, "", "")
	backend.pairErr = ErrPairingRateLimited
	limited := request(t, http.MethodPost, server.URL+"/v1/devices/pair", pairBody, "", "")
	if invalid.StatusCode != http.StatusBadRequest || limited.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("pair failure statuses: invalid=%d limited=%d", invalid.StatusCode, limited.StatusCode)
	}
	if strings.Contains(invalid.Body, pairBodySecret(pairBody)) || strings.Contains(limited.Body, pairBodySecret(pairBody)) {
		t.Fatal("pair failure disclosed the submitted code")
	}

	withAssignments := validServiceConfig(t)
	withAssignments.PairingFixtures = nil
	withAssignments.PairingBackend = backend
	if _, err := NewService(withAssignments); err == nil {
		t.Fatal("NewService() accepted static assignments with a persistent pairing backend")
	}
}

func TestProductionPairingBackendCallsHaveBoundedDeadlines(t *testing.T) {
	t.Parallel()

	backend := &deadlineHTTPPairingBackend{}
	service, err := NewService(ServiceConfig{
		PairingBackend: backend,
		BackendTimeout: 20 * time.Millisecond,
	})
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	client := &http.Client{Timeout: 500 * time.Millisecond}
	started := time.Now()
	pairRequest, err := http.NewRequest(
		http.MethodPost,
		server.URL+"/v1/devices/pair",
		bytes.NewBufferString(`{"pairing_code":"ABCDEFGHIJKLMNOPQRSTUVWX26","device":{"app_version":"1","os_version":"35","model":"TV"}}`),
	)
	if err != nil {
		t.Fatalf("NewRequest(pair) error = %v", err)
	}
	pairRequest.Header.Set("Content-Type", "application/json")
	pairResponse, err := client.Do(pairRequest)
	if err != nil {
		t.Fatalf("Do(pair) error = %v", err)
	}
	pairResponse.Body.Close()
	if pairResponse.StatusCode != http.StatusInternalServerError || time.Since(started) >= 250*time.Millisecond {
		t.Fatalf("bounded pair response = %d after %s", pairResponse.StatusCode, time.Since(started))
	}

	started = time.Now()
	authRequest, err := http.NewRequest(
		http.MethodGet,
		server.URL+"/v1/devices/device-production-0001/manifest",
		nil,
	)
	if err != nil {
		t.Fatalf("NewRequest(auth) error = %v", err)
	}
	authRequest.Header.Set("Authorization", "Bearer valid-length-bearer-token")
	authResponse, err := client.Do(authRequest)
	if err != nil {
		t.Fatalf("Do(auth) error = %v", err)
	}
	authResponse.Body.Close()
	if authResponse.StatusCode != http.StatusInternalServerError || time.Since(started) >= 250*time.Millisecond {
		t.Fatalf("bounded auth response = %d after %s", authResponse.StatusCode, time.Since(started))
	}
}

func TestDeviceHeartbeatIsStrictScopedAndFailureIsolated(t *testing.T) {
	t.Parallel()

	backend := &recordingHTTPPairingBackend{principal: DevicePrincipal{
		DeviceID: "device-production-0001",
		Mosque:   MosqueIdentity{ID: "mosque-ulyanovsk-0001", Name: "Second Cathedral Mosque", Timezone: "Europe/Ulyanovsk"},
	}}
	config := validServiceConfig(t)
	config.PairingFixtures = nil
	config.Assignments = nil
	config.PairingBackend = backend
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)
	body := []byte(`{"sent_at":"2026-08-20T12:00:00Z","app_version":"0.3.0-shell","os_version":"35","model":"Android TV","active_snapshot_id":"synthetic-android-verification-v1","sync_status":"ok","coverage_days_remaining":12,"clock_mismatch":false,"timezone_mismatch":false,"storage_health":"ok","memory_health":"low","boot_mode":"best_effort","kiosk_mode":"none"}`)
	heartbeat := request(t, http.MethodPost, server.URL+"/v1/devices/device-production-0001/heartbeat", body, "valid-device-token-0001", "")
	if heartbeat.StatusCode != http.StatusNoContent || backend.heartbeat.AppVersion != "0.3.0-shell" {
		t.Fatalf("heartbeat response = %d %s; report=%#v", heartbeat.StatusCode, heartbeat.Body, backend.heartbeat)
	}
	crossDevice := request(t, http.MethodPost, server.URL+"/v1/devices/device-other-0001/heartbeat", body, "valid-device-token-0001", "")
	if crossDevice.StatusCode != http.StatusUnauthorized {
		t.Fatalf("cross-device heartbeat = %d %s", crossDevice.StatusCode, crossDevice.Body)
	}
	unknownField := request(t, http.MethodPost, server.URL+"/v1/devices/device-production-0001/heartbeat", append(body[:len(body)-1], []byte(`,"wifi_ssid":"private"}`)...), "valid-device-token-0001", "")
	if unknownField.StatusCode != http.StatusBadRequest {
		t.Fatalf("private-field heartbeat = %d %s", unknownField.StatusCode, unknownField.Body)
	}
	backend.heartbeatErr = errors.New("database unavailable")
	infrastructure := request(t, http.MethodPost, server.URL+"/v1/devices/device-production-0001/heartbeat", body, "valid-device-token-0001", "")
	if infrastructure.StatusCode != http.StatusInternalServerError {
		t.Fatalf("heartbeat infrastructure response = %d %s", infrastructure.StatusCode, infrastructure.Body)
	}
	backend.heartbeatErr = ErrDeviceUnauthorized
	revoked := request(t, http.MethodPost, server.URL+"/v1/devices/device-production-0001/heartbeat", body, "valid-device-token-0001", "")
	if revoked.StatusCode != http.StatusUnauthorized {
		t.Fatalf("revoked heartbeat response = %d %s", revoked.StatusCode, revoked.Body)
	}
}

func TestDeviceHeartbeatBackendCallHasBoundedDeadline(t *testing.T) {
	t.Parallel()

	service, err := NewService(ServiceConfig{
		PairingBackend: &deadlineHeartbeatBackend{},
		BackendTimeout: 20 * time.Millisecond,
	})
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)
	body := []byte(`{"sent_at":"2026-08-20T12:00:00Z","app_version":"1","os_version":"","model":"","active_snapshot_id":"","sync_status":"unknown","coverage_days_remaining":0,"clock_mismatch":false,"timezone_mismatch":false,"storage_health":"unknown","memory_health":"unknown","boot_mode":"unknown","kiosk_mode":"unknown"}`)
	started := time.Now()
	response := request(t, http.MethodPost, server.URL+"/v1/devices/device-production-0001/heartbeat", body, "valid-device-token-0001", "")
	if response.StatusCode != http.StatusInternalServerError || time.Since(started) >= 250*time.Millisecond {
		t.Fatalf("bounded heartbeat response = %d after %s", response.StatusCode, time.Since(started))
	}
}

func TestAdminFleetHTTPRequiresAuthScopeAndIdempotency(t *testing.T) {
	t.Parallel()

	admin := &recordingHTTPAdminBackend{
		principal: AdminPrincipal{
			ActorID:     "actor-mosque-admin-0001",
			Memberships: []AdminMembership{{MosqueID: "synthetic-verification-mosque", Role: AdminRoleMosqueAdmin}},
		},
		issued: IssuedPairing{
			DeviceID: "device-production-0001", Code: "ABCDEFGHIJKLMNOPQRSTUVWX26",
			ExpiresAt: time.Date(2026, 8, 20, 12, 10, 0, 0, time.UTC),
		},
		devices: []FleetDevice{{DeviceID: "device-production-0001", MosqueID: "synthetic-verification-mosque", Status: "pending"}},
	}
	config := validServiceConfig(t)
	config.PairingFixtures = nil
	config.Assignments = nil
	config.AdminBackend = admin
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	unauthorized := request(t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices", nil, "", "")
	if unauthorized.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unauthorized admin status = %d", unauthorized.StatusCode)
	}

	issued := adminRequest(
		t, http.MethodPost, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/pairing-codes",
		[]byte(`{"expires_in_seconds":600,"reason":"install lobby display"}`),
		"admin-bearer-token-valid-0001", "idem-admin-issue-0001",
	)
	if issued.StatusCode != http.StatusCreated || !strings.Contains(issued.Body, admin.issued.Code) {
		t.Fatalf("admin issue response = %d %s", issued.StatusCode, issued.Body)
	}
	if admin.issueCommand.MosqueID != "synthetic-verification-mosque" ||
		admin.issueCommand.IdempotencyKey != "idem-admin-issue-0001" || !validIdentifier(admin.issueCommand.RequestID) {
		t.Fatalf("admin issue command = %#v", admin.issueCommand)
	}

	missingIdempotency := adminRequest(
		t, http.MethodPost, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/pairing-codes",
		[]byte(`{"expires_in_seconds":600,"reason":"install lobby display"}`),
		"admin-bearer-token-valid-0001", "",
	)
	if missingIdempotency.StatusCode != http.StatusBadRequest {
		t.Fatalf("missing idempotency status = %d", missingIdempotency.StatusCode)
	}

	listed := adminRequest(
		t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices",
		nil, "admin-bearer-token-valid-0001", "",
	)
	if listed.StatusCode != http.StatusOK || !strings.Contains(listed.Body, "device-production-0001") {
		t.Fatalf("admin list response = %d %s", listed.StatusCode, listed.Body)
	}
	admin.supportBundle = DeviceSupportBundle{
		SchemaVersion: "device-support-bundle/v1",
		GeneratedAt:   time.Date(2026, 8, 20, 12, 0, 0, 0, time.UTC),
		Mosque:        SupportMosque{ID: "synthetic-verification-mosque", Timezone: "Europe/Ulyanovsk"},
		Device:        SupportDevice{ID: "device-production-0001", Status: "pending"},
	}
	support := adminRequest(
		t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/support-bundle",
		nil, "admin-bearer-token-valid-0001", "",
	)
	if support.StatusCode != http.StatusOK || support.Header.Get("Cache-Control") != "no-store" ||
		!strings.Contains(support.Body, `"schema_version":"device-support-bundle/v1"`) ||
		strings.Contains(support.Body, "admin-bearer-token-valid-0001") || strings.Contains(support.Body, "snapshot_url") {
		t.Fatalf("admin support response = %d %s, headers=%v", support.StatusCode, support.Body, support.Header)
	}
	if strings.Contains(support.Body, `"assignment"`) || strings.Contains(support.Body, `"health"`) {
		t.Fatalf("empty optional support objects were invented: %s", support.Body)
	}
	if admin.supportMosqueID != "synthetic-verification-mosque" || admin.supportDeviceID != "device-production-0001" {
		t.Fatalf("admin support scope = %s/%s", admin.supportMosqueID, admin.supportDeviceID)
	}
	admin.supportErr = ErrAdminResourceNotFound
	missingSupport := adminRequest(
		t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-missing-0001/support-bundle",
		nil, "admin-bearer-token-valid-0001", "",
	)
	if missingSupport.StatusCode != http.StatusNotFound {
		t.Fatalf("missing support response = %d %s", missingSupport.StatusCode, missingSupport.Body)
	}
	admin.supportErr = errors.New("database unavailable")
	failedSupport := adminRequest(
		t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/support-bundle",
		nil, "admin-bearer-token-valid-0001", "",
	)
	if failedSupport.StatusCode != http.StatusInternalServerError || !strings.Contains(failedSupport.Body, `"retryable":true`) {
		t.Fatalf("failed support response = %d %s", failedSupport.StatusCode, failedSupport.Body)
	}
	admin.supportErr = nil

	revoked := adminRequest(
		t, http.MethodPost, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/revoke",
		[]byte(`{"reason":"device replaced"}`), "admin-bearer-token-valid-0001", "idem-admin-revoke-0001",
	)
	if revoked.StatusCode != http.StatusNoContent || admin.revokeCommand.DeviceID != "device-production-0001" {
		t.Fatalf("admin revoke response = %d %s, command=%#v", revoked.StatusCode, revoked.Body, admin.revokeCommand)
	}

	assigned := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/assignment",
		[]byte(`{"snapshot_id":"synthetic-android-verification-v1","minimum_app_version":"0.2.0-shell","reason":"approved test assignment"}`),
		"admin-bearer-token-valid-0001", "idem-admin-assign-0001",
	)
	if assigned.StatusCode != http.StatusOK || admin.assignCommand.SnapshotSHA256 != config.Snapshots[0].SHA256 ||
		admin.assignCommand.SnapshotMosqueID != "synthetic-verification-mosque" {
		t.Fatalf("admin assignment response = %d %s, command=%#v", assigned.StatusCode, assigned.Body, admin.assignCommand)
	}
	grouped := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/rollout-group",
		[]byte(`{"rollout_group":"canary-group-0001","reason":"select canary"}`),
		"admin-bearer-token-valid-0001", "idem-admin-group-set-01",
	)
	if grouped.StatusCode != http.StatusNoContent || admin.groupCommand.RolloutGroup != "canary-group-0001" {
		t.Fatalf("admin rollout-group response = %d %s, command=%#v", grouped.StatusCode, grouped.Body, admin.groupCommand)
	}
	privateGroupField := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/rollout-group",
		[]byte(`{"rollout_group":"canary-group-0001","reason":"private field","wifi_ssid":"private"}`),
		"admin-bearer-token-valid-0001", "idem-admin-group-private-1",
	)
	if privateGroupField.StatusCode != http.StatusBadRequest {
		t.Fatalf("private rollout-group field response = %d %s", privateGroupField.StatusCode, privateGroupField.Body)
	}
	admin.rolloutResult = RolloutAssignmentResult{
		RolloutGroup: "canary-group-0001", SnapshotID: "synthetic-android-verification-v1", DeviceCount: 1,
		Assignments: []DeviceAssignment{{
			DeviceID: "device-production-0001", ManifestVersion: 2,
			SnapshotID: "synthetic-android-verification-v1", SnapshotSHA256: config.Snapshots[0].SHA256,
		}},
	}
	rollout := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/rollout-groups/canary-group-0001/assignment",
		[]byte(`{"snapshot_id":"synthetic-android-verification-v1","minimum_app_version":"0.2.0-shell","reason":"canary rollout"}`),
		"admin-bearer-token-valid-0001", "idem-admin-group-assign-01",
	)
	if rollout.StatusCode != http.StatusOK || admin.rolloutCommand.SnapshotSHA256 != config.Snapshots[0].SHA256 ||
		!strings.Contains(rollout.Body, `"device_count":1`) {
		t.Fatalf("admin rollout assignment response = %d %s, command=%#v", rollout.StatusCode, rollout.Body, admin.rolloutCommand)
	}
	admin.rolloutRetry = RolloutAssignmentResult{
		RolloutGroup: "canary-group-0001", SnapshotID: "removed-snapshot-0001", DeviceCount: 1,
		Assignments: []DeviceAssignment{{
			DeviceID: "device-production-0001", ManifestVersion: 3,
			SnapshotID: "removed-snapshot-0001", SnapshotURL: "https://api.example.invalid/v1/snapshots/removed-snapshot-0001",
			SnapshotSHA256: strings.Repeat("b", 64), SigningKeyID: "removed-key",
		}},
	}
	admin.rolloutRetryFound = true
	historicalRollout := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/rollout-groups/canary-group-0001/assignment",
		[]byte(`{"snapshot_id":"removed-snapshot-0001","reason":"historical group retry"}`),
		"admin-bearer-token-valid-0001", "idem-admin-group-history-1",
	)
	if historicalRollout.StatusCode != http.StatusOK || !strings.Contains(historicalRollout.Body, "removed-snapshot-0001") {
		t.Fatalf("removed-artifact rollout retry = %d %s", historicalRollout.StatusCode, historicalRollout.Body)
	}
	admin.rolloutRetryFound = false
	admin.rolloutErr = ErrRolloutGroupTooLarge
	oversizedRollout := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/rollout-groups/canary-group-0001/assignment",
		[]byte(`{"snapshot_id":"synthetic-android-verification-v1","reason":"oversized canary"}`),
		"admin-bearer-token-valid-0001", "idem-admin-group-overflow-1",
	)
	if oversizedRollout.StatusCode != http.StatusConflict || !strings.Contains(oversizedRollout.Body, "rollout_group_too_large") {
		t.Fatalf("oversized rollout response = %d %s", oversizedRollout.StatusCode, oversizedRollout.Body)
	}
	admin.rolloutErr = nil
	admin.retryAssignment = DeviceAssignment{
		DeviceID: "device-production-0001", ManifestVersion: 1,
		SnapshotID: "removed-snapshot-0001", SnapshotURL: "https://api.example.invalid/v1/snapshots/removed-snapshot-0001",
		SnapshotSHA256: strings.Repeat("a", 64), SigningKeyID: "removed-key",
	}
	admin.retryFound = true
	historicalRetry := adminRequest(
		t, http.MethodPut, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices/device-production-0001/assignment",
		[]byte(`{"snapshot_id":"removed-snapshot-0001","reason":"historical retry"}`),
		"admin-bearer-token-valid-0001", "idem-admin-historical-01",
	)
	if historicalRetry.StatusCode != http.StatusOK || !strings.Contains(historicalRetry.Body, "removed-snapshot-0001") {
		t.Fatalf("removed-artifact retry response = %d %s", historicalRetry.StatusCode, historicalRetry.Body)
	}
	admin.retryFound = false
	admin.authorizeErr = ErrAdminResourceNotFound
	beforeAuthorizationCalls := admin.authorizeCalls
	for _, snapshotID := range []string{"synthetic-android-verification-v1", "unknown-snapshot-0001"} {
		denied := adminRequest(
			t, http.MethodPut, server.URL+"/v1/admin/mosques/other-mosque-000001/devices/device-production-0001/assignment",
			[]byte(`{"snapshot_id":"`+snapshotID+`","reason":"cross scope probe"}`),
			"admin-bearer-token-valid-0001", "idem-admin-probe-0001",
		)
		if denied.StatusCode != http.StatusNotFound {
			t.Fatalf("cross-scope assignment %s status = %d", snapshotID, denied.StatusCode)
		}
	}
	for _, snapshotID := range []string{"synthetic-android-verification-v1", "unknown-snapshot-0001"} {
		denied := adminRequest(
			t, http.MethodPut, server.URL+"/v1/admin/mosques/other-mosque-000001/rollout-groups/canary-group-0001/assignment",
			[]byte(`{"snapshot_id":"`+snapshotID+`","reason":"cross scope group probe"}`),
			"admin-bearer-token-valid-0001", "idem-admin-group-probe-01",
		)
		if denied.StatusCode != http.StatusNotFound {
			t.Fatalf("cross-scope rollout assignment %s status = %d", snapshotID, denied.StatusCode)
		}
	}
	if admin.authorizeCalls != beforeAuthorizationCalls+4 {
		t.Fatalf("assignment registry lookup bypassed scope preauthorization: calls=%d", admin.authorizeCalls)
	}
	admin.authorizeErr = nil

	admin.issueErr = ErrAdminIdempotencyConflict
	conflict := adminRequest(
		t, http.MethodPost, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/pairing-codes",
		[]byte(`{"expires_in_seconds":600,"reason":"conflicting retry"}`),
		"admin-bearer-token-valid-0001", "idem-admin-issue-0001",
	)
	if conflict.StatusCode != http.StatusConflict {
		t.Fatalf("idempotency conflict status = %d", conflict.StatusCode)
	}
	admin.issueErr = nil
	admin.authErr = errors.New("database unavailable")
	infrastructure := adminRequest(
		t, http.MethodGet, server.URL+"/v1/admin/mosques/synthetic-verification-mosque/devices",
		nil, "admin-bearer-token-valid-0001", "",
	)
	if infrastructure.StatusCode != http.StatusInternalServerError || !strings.Contains(infrastructure.Body, `"retryable":true`) {
		t.Fatalf("admin auth infrastructure response = %d %s", infrastructure.StatusCode, infrastructure.Body)
	}
}

func TestAdminRegistrySearchAndResolveRequireExplicitScopedCity(t *testing.T) {
	t.Parallel()
	const (
		mosqueID = "second-cathedral-mosque-ulyanovsk"
		cityID   = "city-4adcfc15932f3850d5dd5dbaa17e3a4c"
	)
	admin := &recordingHTTPAdminBackend{principal: AdminPrincipal{
		ActorID: "actor-mosque-admin-registry", Memberships: []AdminMembership{{MosqueID: mosqueID, Role: AdminRoleMosqueAdmin}},
	}}
	registryBackend := &recordingHTTPRegistryBackend{
		cities: []registrydomain.CitySearchResult{{
			City: domain.City{
				ID: cityID, Name: "Ульяновск", Aliases: []string{"Ulyanovsk", "Синбирск"}, CountryCode: "RU", RegionID: "ru-uly",
				SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657, Timezone: "Europe/Ulyanovsk",
				GeographicSourceID: "geonames:479123", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
			},
			Region: domain.Region{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"},
		}},
		resolution: registrydomain.Resolution{
			Tier:   registrydomain.ResolutionExactCityTimetable,
			City:   domain.City{ID: cityID, Name: "Ульяновск", CountryCode: "RU", RegionID: "ru-uly", SettlementType: "PPLA", Timezone: "Europe/Ulyanovsk", GeographicSourceID: "geonames:479123", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0"},
			Region: domain.Region{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"},
			Scope:  domain.GeographicScope{ID: "scope-ulyanovsk-city", Kind: domain.GeographicScopeCity, CityID: cityID, RegionID: "ru-uly", Description: "Second Cathedral Mosque; 2026"},
			Authorities: []domain.PrayerAuthority{
				{ID: "rdum-ulyanovsk-oblast", Name: "RDUM", EvidenceLabel: "CONFIRMED_PUBLIC"},
				{ID: "rdumul-attributed-publisher-unconfirmed", Name: "Attributed publisher", EvidenceLabel: "UNKNOWN"},
			},
			Source: domain.PrayerSource{
				ID: "effective-ulyanovsk-2026-v1", Kind: domain.ProviderKindManualImport,
				AuthorityIDs:      []string{"rdum-ulyanovsk-oblast", "rdumul-attributed-publisher-unconfirmed"},
				GeographicScopeID: "scope-ulyanovsk-city", Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
			},
			Policy: domain.PrayerPolicy{
				ID: "policy-ulyanovsk-second-cathedral-2026", Kind: domain.PrayerPolicyTimeTable,
				GeographicScopeID: "scope-ulyanovsk-city", AuthorityIDs: []string{"rdum-ulyanovsk-oblast", "rdumul-attributed-publisher-unconfirmed"},
				SourceID: "effective-ulyanovsk-2026-v1", TimeTableID: "timetable-ulyanovsk-second-cathedral-2026", MosqueIDs: []string{mosqueID},
				ApprovalID: "approval-second-cathedral-mosque-ulyanovsk-2026-002", Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			},
			TimeTable: domain.TimeTable{
				ID: "timetable-ulyanovsk-second-cathedral-2026", SourceID: "effective-ulyanovsk-2026-v1",
				GeographicScopeID: "scope-ulyanovsk-city", MosqueID: mosqueID, Timezone: "Europe/Ulyanovsk",
				PublishedSnapshotID: "ulyanovsk-second-cathedral-2026-pilot-local-v2", Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			},
			SourceOverrides: []domain.SourceOverride{{
				ID: "source-override-ulyanovsk-2026-08", BaseSourceID: "official-rdumul-ulyanovsk-2026",
				OverrideSourceID: "manual-rdumul-ulsk-2026-08", AppliedFields: []string{"dhuhr"},
				ApprovalID: "approval-second-cathedral-mosque-ulyanovsk-2026-002", Effective: domain.DateRange{From: "2026-08-01", To: "2026-08-31"},
			}},
		},
	}
	config := validServiceConfig(t)
	config.PairingFixtures = nil
	config.Assignments = nil
	config.AdminBackend = admin
	config.RegistryBackend = registryBackend
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	searchURL := server.URL + "/v1/admin/mosques/" + mosqueID + "/setup/cities?q=" + url.QueryEscape("Ульяновск")
	unauthorized := adminRequest(t, http.MethodGet, searchURL, nil, "", "")
	if unauthorized.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unauthorized search = %d %s", unauthorized.StatusCode, unauthorized.Body)
	}
	search := adminRequest(t, http.MethodGet, searchURL, nil, "admin-bearer-token-valid-registry", "")
	if search.StatusCode != http.StatusOK || search.Header.Get("Cache-Control") != "no-store" ||
		!strings.Contains(search.Body, `"city_id":"`+cityID+`"`) || !strings.Contains(search.Body, `"federal_subject_code":"RU-ULY"`) ||
		!strings.Contains(search.Body, `"timezone":"Europe/Ulyanovsk"`) || !strings.Contains(search.Body, `"geographic_license":"CC BY 4.0"`) {
		t.Fatalf("city search = %d %s", search.StatusCode, search.Body)
	}
	if registryBackend.searchQuery != "Ульяновск" || admin.authorizeCalls != 1 {
		t.Fatalf("search query/scope = %q/%d", registryBackend.searchQuery, admin.authorizeCalls)
	}

	resolveURL := server.URL + "/v1/admin/mosques/" + mosqueID + "/setup/prayer-policy?city_id=" + cityID + "&date=2026-08-30"
	resolved := adminRequest(t, http.MethodGet, resolveURL, nil, "admin-bearer-token-valid-registry", "")
	if resolved.StatusCode != http.StatusOK || !strings.Contains(resolved.Body, `"status":"resolved"`) ||
		!strings.Contains(resolved.Body, `"tier":"exact_city_timetable"`) ||
		!strings.Contains(resolved.Body, `"evidence_label":"CONFIRMED_PUBLIC"`) ||
		!strings.Contains(resolved.Body, `"evidence_label":"UNKNOWN"`) ||
		!strings.Contains(resolved.Body, `"published_snapshot_id":"ulyanovsk-second-cathedral-2026-pilot-local-v2"`) ||
		!strings.Contains(resolved.Body, `"source_overrides":[{"id":"source-override-ulyanovsk-2026-08"`) ||
		strings.Contains(resolved.Body, `"calculation_profile"`) {
		t.Fatalf("policy resolution = %d %s", resolved.StatusCode, resolved.Body)
	}
	if registryBackend.resolveRequest.CityID != cityID || registryBackend.resolveRequest.MosqueID != mosqueID || registryBackend.resolveRequest.Date != "2026-08-30" {
		t.Fatalf("resolve request = %#v", registryBackend.resolveRequest)
	}

	invalid := adminRequest(t, http.MethodGet, resolveURL+"&guess=true", nil, "admin-bearer-token-valid-registry", "")
	if invalid.StatusCode != http.StatusBadRequest {
		t.Fatalf("extra query parameter = %d %s", invalid.StatusCode, invalid.Body)
	}
	registryBackend.resolveErr = registrydomain.ErrPolicyUnavailable
	unavailable := adminRequest(t, http.MethodGet, resolveURL, nil, "admin-bearer-token-valid-registry", "")
	if unavailable.StatusCode != http.StatusConflict || !strings.Contains(unavailable.Body, `"code":"prayer_policy_unavailable"`) {
		t.Fatalf("unavailable resolution = %d %s", unavailable.StatusCode, unavailable.Body)
	}
	registryBackend.resolveErr = registrydomain.ErrPolicyAmbiguous
	ambiguous := adminRequest(t, http.MethodGet, resolveURL, nil, "admin-bearer-token-valid-registry", "")
	if ambiguous.StatusCode != http.StatusConflict || !strings.Contains(ambiguous.Body, `"code":"prayer_policy_ambiguous"`) {
		t.Fatalf("ambiguous resolution = %d %s", ambiguous.StatusCode, ambiguous.Body)
	}
}

func TestAdminRegistryWorkflowExplainsAmbiguityAndRequiresExplicitBinding(t *testing.T) {
	t.Parallel()
	const (
		mosqueID   = "second-cathedral-mosque-ulyanovsk"
		cityID     = "city-4adcfc15932f3850d5dd5dbaa17e3a4c"
		revisionID = "revision-operator-review-0001"
		policyID   = "policy-operator-choice-second"
	)
	admin := &recordingHTTPAdminBackend{
		principal: AdminPrincipal{
			ActorID:     "actor-mosque-admin-registry",
			Memberships: []AdminMembership{{MosqueID: mosqueID, Role: AdminRoleMosqueAdmin}},
		},
		bindingRequest: RegistryBindingRequest{
			ID: "binding-request-operator-0001", RevisionID: revisionID, CityID: cityID,
			PolicyID: policyID, MosqueID: mosqueID, Date: "2026-08-30",
			Tier: registrydomain.ResolutionExactCityTimetable, Status: RegistryBindingPendingReview,
			SelectionSHA256: strings.Repeat("b", 64), RequestedByActorID: "actor-mosque-admin-registry",
			Reason: "choose reviewed city timetable", RequestID: "request-registry-binding-http-0001",
			RequestedAt: time.Date(2026, 8, 30, 9, 0, 0, 0, time.UTC),
		},
	}
	city := domain.City{
		ID: cityID, Name: "Ульяновск", RegionID: "ru-uly", CountryCode: "RU",
		Timezone: "Europe/Ulyanovsk", SettlementType: "PPLA", GeographicSourceID: "geonames:479123",
		GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
	}
	region := domain.Region{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}
	registryBackend := &recordingHTTPRegistryBackend{assessment: registrydomain.RevisionPolicyAssessment{
		Revision: registrydomain.RevisionRecord{ID: revisionID, ContentSHA256: strings.Repeat("a", 64)},
		State:    registrydomain.RevisionStateStaged,
		Result: registrydomain.PolicyAssessment{
			Status: registrydomain.AssessmentAmbiguous, Reason: registrydomain.AssessmentReasonSameTierAmbiguous,
			City: city, Region: region, Date: "2026-08-30",
			Options: []registrydomain.PolicyOption{
				{Tier: registrydomain.ResolutionExactCityTimetable, Selectable: true, BlockedReason: registrydomain.OptionEligible, Policy: domain.PrayerPolicy{ID: "policy-operator-choice-first"}},
				{Tier: registrydomain.ResolutionExactCityTimetable, Selectable: true, BlockedReason: registrydomain.OptionEligible, Policy: domain.PrayerPolicy{ID: policyID}},
			},
		},
	}}
	config := validServiceConfig(t)
	config.PairingFixtures = nil
	config.Assignments = nil
	config.AdminBackend = admin
	config.RegistryBackend = registryBackend
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	optionsURL := server.URL + "/v1/admin/mosques/" + mosqueID + "/setup/prayer-policy-options?revision_id=" + revisionID + "&city_id=" + cityID + "&date=2026-08-30"
	options := adminRequest(t, http.MethodGet, optionsURL, nil, "admin-bearer-token-registry-options", "")
	if options.StatusCode != http.StatusOK ||
		!strings.Contains(options.Body, `"status":"ambiguous"`) ||
		!strings.Contains(options.Body, `"reason":"same_tier_ambiguous"`) ||
		strings.Count(options.Body, `"selectable":true`) != 2 ||
		!strings.Contains(options.Body, `"allowed_actions":["request_binding"]`) {
		t.Fatalf("policy options = %d %s", options.StatusCode, options.Body)
	}
	if registryBackend.assessmentID != revisionID || registryBackend.assessRequest.CityID != cityID {
		t.Fatalf("assessment call = %q %#v", registryBackend.assessmentID, registryBackend.assessRequest)
	}

	bindingBody := []byte(`{"revision_id":"` + revisionID + `","city_id":"` + cityID + `","policy_id":"` + policyID + `","date":"2026-08-30","reason":"choose reviewed city timetable"}`)
	created := adminRequest(
		t, http.MethodPost,
		server.URL+"/v1/admin/mosques/"+mosqueID+"/setup/prayer-policy-binding-requests",
		bindingBody, "admin-bearer-token-registry-options", "idem-registry-binding-http-0001",
	)
	if created.StatusCode != http.StatusCreated || !strings.Contains(created.Body, `"status":"pending_review"`) ||
		!strings.Contains(created.Body, `"policy_id":"`+policyID+`"`) {
		t.Fatalf("binding request = %d %s", created.StatusCode, created.Body)
	}
	if admin.bindingCommand.PolicyID != policyID || admin.bindingCommand.RevisionID != revisionID ||
		admin.bindingCommand.IdempotencyKey != "idem-registry-binding-http-0001" {
		t.Fatalf("binding command = %#v", admin.bindingCommand)
	}
	admin.bindingErr = ErrRegistryBindingNotSelectable
	rejected := adminRequest(
		t, http.MethodPost,
		server.URL+"/v1/admin/mosques/"+mosqueID+"/setup/prayer-policy-binding-requests",
		bindingBody, "admin-bearer-token-registry-options", "idem-registry-binding-http-0002",
	)
	if rejected.StatusCode != http.StatusConflict || !strings.Contains(rejected.Body, `"code":"registry_binding_not_selectable"`) {
		t.Fatalf("blocked binding = %d %s", rejected.StatusCode, rejected.Body)
	}
}

func TestAdminCityScheduleChoicesExposeEveryEligibleAuthorityWithoutAutoSelection(t *testing.T) {
	t.Parallel()
	const (
		mosqueID   = "mosque-synthetic-schedule-choice"
		cityID     = "city-synthetic-schedule-choice"
		revisionID = "revision-synthetic-schedule-choice-0001"
	)
	admin := &recordingHTTPAdminBackend{principal: AdminPrincipal{
		ActorID:     "actor-synthetic-schedule-choice",
		Memberships: []AdminMembership{{MosqueID: mosqueID, Role: AdminRoleMosqueAdmin}},
	}}
	city := domain.City{
		ID: cityID, Name: "Синтетический город", Aliases: []string{"Synthetic City"}, CountryCode: "RU",
		RegionID: "region-synthetic-schedule-choice", SettlementType: "PPL", Latitude: 55, Longitude: 49,
		Timezone: "Europe/Moscow", GeographicSourceID: "synthetic:choice", GeographicRevision: "fixture-v1",
		GeographicLicense: "synthetic-test-only",
	}
	region := domain.Region{
		ID: "region-synthetic-schedule-choice", Name: "Синтетический субъект",
		CountryCode: "RU", FederalSubjectCode: "RU-XX",
	}
	options := make([]registrydomain.PolicyOption, 0, 8)
	for index := range 8 {
		suffix := fmt.Sprintf("%02d", index)
		authorityID := "authority-http-synthetic-" + suffix
		sourceID := "source-http-synthetic-" + suffix
		policyID := "policy-http-synthetic-" + suffix
		timetableID := "timetable-http-synthetic-" + suffix
		scope := domain.GeographicScope{
			ID: "scope-http-synthetic-choice", Kind: domain.GeographicScopeCity,
			CityID: cityID, RegionID: region.ID, Description: "synthetic schedule-choice API fixture",
		}
		timetable := domain.TimeTable{
			ID: timetableID, SourceID: sourceID, GeographicScopeID: scope.ID, MosqueID: mosqueID,
			Timezone: city.Timezone, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
			PublishedSnapshotID: "snapshot-http-synthetic-" + suffix,
		}
		options = append(options, registrydomain.PolicyOption{
			Tier: registrydomain.ResolutionExactCityTimetable, Selectable: true,
			BlockedReason: registrydomain.OptionEligible, Scope: scope,
			Authorities: []domain.PrayerAuthority{{
				ID: authorityID, Name: "Synthetic canonical organization " + suffix, EvidenceLabel: "PROPOSAL",
			}},
			Source: domain.PrayerSource{
				ID: sourceID, Kind: domain.ProviderKindManualImport, AuthorityIDs: []string{authorityID},
				GeographicScopeID: scope.ID, CanonicalURL: "https://example.invalid/source/" + suffix,
				Status: domain.PrayerSourceApproved, FreshThrough: "2026-12-31",
			},
			Policy: domain.PrayerPolicy{
				ID: policyID, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: scope.ID,
				AuthorityIDs: []string{authorityID}, SourceID: sourceID, TimeTableID: timetableID,
				MosqueIDs: []string{mosqueID}, Effective: domain.DateRange{From: "2026-01-01", To: "2026-12-31"},
				ApprovalID: "approval-http-synthetic-" + suffix,
			},
			TimeTable: &timetable, SourceOverrides: []domain.SourceOverride{},
		})
	}
	registryBackend := &recordingHTTPRegistryBackend{assessment: registrydomain.RevisionPolicyAssessment{
		Revision: registrydomain.RevisionRecord{ID: revisionID, ContentSHA256: strings.Repeat("c", 64)},
		State:    registrydomain.RevisionStateStaged,
		Result: registrydomain.PolicyAssessment{
			Status: registrydomain.AssessmentAmbiguous, Reason: registrydomain.AssessmentReasonSameTierAmbiguous,
			City: city, Region: region, Date: "2026-08-30", Options: options,
		},
	}}
	config := validServiceConfig(t)
	config.PairingFixtures = nil
	config.Assignments = nil
	config.AdminBackend = admin
	config.RegistryBackend = registryBackend
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	choicesURL := server.URL + "/v1/admin/mosques/" + mosqueID + "/setup/schedule-choices?revision_id=" + revisionID + "&city_id=" + cityID + "&date=2026-08-30"
	choices := adminRequest(t, http.MethodGet, choicesURL, nil, "admin-bearer-token-schedule-choices", "")
	if choices.StatusCode != http.StatusOK ||
		!strings.Contains(choices.Body, `"schema_version":"city-schedule-choices/v1"`) ||
		!strings.Contains(choices.Body, `"status":"available"`) ||
		!strings.Contains(choices.Body, `"automatic_resolution_status":"ambiguous"`) ||
		!strings.Contains(choices.Body, `"selection_required":true`) ||
		!strings.Contains(choices.Body, `"federal_subject_code":"RU-XX"`) ||
		strings.Count(choices.Body, `"choice_id":`) != 8 ||
		strings.Count(choices.Body, `"selectable":true`) != 8 ||
		strings.Count(choices.Body, `"executable":false`) != 8 {
		t.Fatalf("schedule choices = %d %s", choices.StatusCode, choices.Body)
	}
	for index := range 8 {
		label := "Синтетический город (Synthetic canonical organization " + fmt.Sprintf("%02d", index) + ")"
		if !strings.Contains(choices.Body, `"display_label":"`+label+`"`) {
			t.Fatalf("schedule choices omitted %q: %s", label, choices.Body)
		}
	}
	if registryBackend.assessmentID != revisionID || registryBackend.assessRequest.CityID != cityID ||
		registryBackend.assessRequest.MosqueID != mosqueID {
		t.Fatalf("schedule-choice assessment call = %q %#v", registryBackend.assessmentID, registryBackend.assessRequest)
	}

	registryBackend.assessment.Result.Status = registrydomain.AssessmentUnavailable
	registryBackend.assessment.Result.Reason = registrydomain.AssessmentReasonNoPolicy
	registryBackend.assessment.Result.Options = nil
	unavailable := adminRequest(t, http.MethodGet, choicesURL, nil, "admin-bearer-token-schedule-choices", "")
	if unavailable.StatusCode != http.StatusOK || !strings.Contains(unavailable.Body, `"status":"unavailable"`) ||
		!strings.Contains(unavailable.Body, `"automatic_resolution_reason":"no_policy"`) ||
		!strings.Contains(unavailable.Body, `"selection_required":false`) ||
		!strings.Contains(unavailable.Body, `"choices":[]`) {
		t.Fatalf("unavailable schedule choices = %d %s", unavailable.StatusCode, unavailable.Body)
	}

	registryBackend.assessment.State = registrydomain.RevisionStateActive
	registryBackend.assessment.Result.Status = registrydomain.AssessmentResolved
	registryBackend.assessment.Result.Reason = registrydomain.AssessmentReasonResolved
	registryBackend.assessment.Result.Options = options[:1]
	activeURL := server.URL + "/v1/admin/mosques/" + mosqueID + "/setup/schedule-choices?city_id=" + cityID + "&date=2026-08-30"
	active := adminRequest(t, http.MethodGet, activeURL, nil, "admin-bearer-token-schedule-choices", "")
	if active.StatusCode != http.StatusOK || strings.Count(active.Body, `"choice_id":`) != 1 ||
		!strings.Contains(active.Body, `"executable":true`) ||
		!strings.Contains(active.Body, `"selection_required":false`) ||
		!strings.Contains(active.Body, `"allowed_actions":[]`) || registryBackend.assessmentID != "" {
		t.Fatalf("active schedule choice = %d %s, revision=%q", active.StatusCode, active.Body, registryBackend.assessmentID)
	}

	invalid := adminRequest(t, http.MethodGet, choicesURL+"&limit=3", nil, "admin-bearer-token-schedule-choices", "")
	if invalid.StatusCode != http.StatusBadRequest {
		t.Fatalf("hidden limit parameter accepted = %d %s", invalid.StatusCode, invalid.Body)
	}
}

func TestDeviceSetupEndpointsDeriveMosqueScopeAndNeverUseAdminCredential(t *testing.T) {
	t.Parallel()

	const (
		mosqueID   = "synthetic-verification-mosque"
		cityID     = "city-device-http-setup-0001"
		revisionID = "revision-device-http-setup-0001"
	)
	assessment := syntheticDeviceSetupAssessment(mosqueID, cityID, revisionID, 8)
	registryBackend := &recordingDeviceSetupRegistry{
		cities: []registrydomain.CitySearchResult{{
			City: assessment.Result.City, Region: assessment.Result.Region,
		}},
		assessment: assessment,
	}
	repository := &recordingDeviceSetupRepository{}
	setupManager, err := NewDeviceSetupManager(DeviceSetupManagerConfig{
		Registry: registryBackend, Repository: repository,
		SetupRevisionIDs: map[string]string{mosqueID: revisionID},
		Now:              func() time.Time { return time.Date(2026, 8, 30, 12, 0, 0, 0, time.UTC) },
	})
	if err != nil {
		t.Fatalf("NewDeviceSetupManager() error = %v", err)
	}
	config := validServiceConfig(t)
	config.DeviceSetupBackend = setupManager
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	searchURL := server.URL + "/v1/devices/" + testDeviceID + "/setup/cities?q=" + url.QueryEscape("Синтетический город")
	unauthorized := request(t, http.MethodGet, searchURL, nil, "admin-bearer-must-not-work", "")
	if unauthorized.StatusCode != http.StatusUnauthorized ||
		!strings.Contains(unauthorized.Body, `"code":"device_unauthorized"`) {
		t.Fatalf("admin credential on device setup = %d %s", unauthorized.StatusCode, unauthorized.Body)
	}
	crossDevice := request(
		t, http.MethodGet,
		server.URL+"/v1/devices/other-device-setup-0001/setup/cities?q="+url.QueryEscape("Синтетический город"),
		nil, testToken, "",
	)
	if crossDevice.StatusCode != http.StatusUnauthorized {
		t.Fatalf("cross-device setup search = %d %s", crossDevice.StatusCode, crossDevice.Body)
	}
	search := request(t, http.MethodGet, searchURL, nil, testToken, "")
	if search.StatusCode != http.StatusOK || search.Header.Get("Cache-Control") != "no-store" ||
		!strings.Contains(search.Body, `"schema_version":"device-city-search/v1"`) ||
		!strings.Contains(search.Body, `"city_id":"`+cityID+`"`) ||
		!strings.Contains(search.Body, `"federal_subject_code":"RU-XX"`) ||
		strings.Contains(search.Body, `"mosque_id"`) {
		t.Fatalf("device city search = %d %s", search.StatusCode, search.Body)
	}

	choicesURL := server.URL + "/v1/devices/" + testDeviceID +
		"/setup/schedule-choices?city_id=" + cityID + "&date=2026-08-30"
	choices := request(t, http.MethodGet, choicesURL, nil, testToken, "")
	if choices.StatusCode != http.StatusOK ||
		!strings.Contains(choices.Body, `"schema_version":"device-city-schedule-choices/v1"`) ||
		!strings.Contains(choices.Body, `"selection_required":true`) ||
		strings.Count(choices.Body, `"choice_id":`) != 8 ||
		!strings.Contains(choices.Body, `"allowed_actions":["request_selection"]`) ||
		registryBackend.request.MosqueID != mosqueID || registryBackend.assessmentID != revisionID {
		t.Fatalf("device schedule choices = %d %s; scope=%#v revision=%q", choices.StatusCode, choices.Body, registryBackend.request, registryBackend.assessmentID)
	}
	// The envelope follows the immutable registry revision even for a city
	// whose particular choices all remain legacy approval-bound.
	registryBackend.assessment.Revision.SchemaVersion = registrydomain.QualifiedRegistrySchemaVersion
	qualifiedEnvelope := request(t, http.MethodGet, choicesURL, nil, testToken, "")
	if qualifiedEnvelope.StatusCode != http.StatusOK ||
		!strings.Contains(qualifiedEnvelope.Body, `"schema_version":"device-city-schedule-choices/v2"`) {
		t.Fatalf("qualified registry envelope = %d %s", qualifiedEnvelope.StatusCode, qualifiedEnvelope.Body)
	}
	registryBackend.assessment.Revision.SchemaVersion = registrydomain.RegistrySchemaVersion

	projected, err := setupManager.ScheduleChoices(t.Context(), DevicePrincipal{
		DeviceID: testDeviceID,
		Mosque:   MosqueIdentity{ID: mosqueID, Name: "Synthetic verification fixture", Timezone: "Europe/Ulyanovsk"},
	}, cityID, "2026-08-30")
	if err != nil {
		t.Fatalf("ScheduleChoices() error = %v", err)
	}
	selected := projected.Choices[5]
	body := []byte(`{"city_id":"` + cityID + `","choice_id":"` + selected.ID + `","date":"2026-08-30","interaction_id":"interaction-device-http-0001"}`)
	created := request(
		t, http.MethodPost,
		server.URL+"/v1/devices/"+testDeviceID+"/setup/schedule-choice-requests",
		body, testToken, "",
	)
	if created.StatusCode != http.StatusCreated ||
		!strings.Contains(created.Body, `"schema_version":"device-schedule-choice-request/v1"`) ||
		!strings.Contains(created.Body, `"status":"pending_review"`) ||
		!strings.Contains(created.Body, `"choice_id":"`+selected.ID+`"`) ||
		!strings.Contains(created.Body, `"origin":"local_tv_operator"`) || repository.creates != 1 {
		t.Fatalf("device schedule choice request = %d %s; creates=%d", created.StatusCode, created.Body, repository.creates)
	}

	for name, target := range map[string]string{
		"revision injection": choicesURL + "&revision_id=" + revisionID,
		"hidden limit":       choicesURL + "&limit=3",
	} {
		response := request(t, http.MethodGet, target, nil, testToken, "")
		if response.StatusCode != http.StatusBadRequest {
			t.Errorf("%s status = %d %s", name, response.StatusCode, response.Body)
		}
	}
	injectedBody := []byte(`{"city_id":"` + cityID + `","choice_id":"` + selected.ID + `","date":"2026-08-30","interaction_id":"interaction-device-http-0002","mosque_id":"other-mosque"}`)
	injected := request(
		t, http.MethodPost,
		server.URL+"/v1/devices/"+testDeviceID+"/setup/schedule-choice-requests",
		injectedBody, testToken, "",
	)
	if injected.StatusCode != http.StatusBadRequest || repository.creates != 1 {
		t.Fatalf("mosque injection = %d %s; creates=%d", injected.StatusCode, injected.Body, repository.creates)
	}

	revokedBackend := &recordingHTTPPairingBackend{authErr: ErrDeviceUnauthorized}
	revokedConfig := validServiceConfig(t)
	revokedConfig.PairingFixtures = nil
	revokedConfig.Assignments = nil
	revokedConfig.PairingBackend = revokedBackend
	revokedConfig.DeviceSetupBackend = setupManager
	revokedService, err := NewService(revokedConfig)
	if err != nil {
		t.Fatalf("NewService(revoked setup) error = %v", err)
	}
	revokedServer := httptest.NewServer(revokedService.Handler())
	t.Cleanup(revokedServer.Close)
	revoked := request(
		t, http.MethodGet,
		revokedServer.URL+"/v1/devices/"+testDeviceID+"/setup/cities?q="+url.QueryEscape("Синтетический город"),
		nil, testToken, "",
	)
	if revoked.StatusCode != http.StatusUnauthorized || repository.creates != 1 {
		t.Fatalf("revoked device setup = %d %s; creates=%d", revoked.StatusCode, revoked.Body, repository.creates)
	}
}

func TestPersistentAssignmentFeedsExistingDeviceReadContract(t *testing.T) {
	t.Parallel()

	config := validServiceConfig(t)
	assignment := config.Assignments[0]
	config.PairingFixtures = nil
	config.Assignments = nil
	pairing := &recordingHTTPPairingBackend{principal: DevicePrincipal{
		DeviceID: assignment.DeviceID,
		Mosque:   MosqueIdentity{ID: "synthetic-verification-mosque", Name: "Synthetic verification fixture", Timezone: "Europe/Ulyanovsk"},
	}}
	admin := &recordingHTTPAdminBackend{assignment: assignment}
	config.PairingBackend = pairing
	config.AdminBackend = admin
	service, err := NewService(config)
	if err != nil {
		t.Fatalf("NewService() error = %v", err)
	}
	server := httptest.NewServer(service.Handler())
	t.Cleanup(server.Close)

	manifest := request(t, http.MethodGet, server.URL+"/v1/devices/"+assignment.DeviceID+"/manifest", nil, "valid-device-token-0001", "")
	if manifest.StatusCode != http.StatusOK || !strings.Contains(manifest.Body, assignment.SnapshotID) {
		t.Fatalf("persistent manifest response = %d %s", manifest.StatusCode, manifest.Body)
	}
	snapshot := request(t, http.MethodGet, server.URL+"/v1/snapshots/"+assignment.SnapshotID, nil, "valid-device-token-0001", "")
	if snapshot.StatusCode != http.StatusOK {
		t.Fatalf("persistent snapshot response = %d %s", snapshot.StatusCode, snapshot.Body)
	}
	if admin.assignmentDeviceID != assignment.DeviceID || admin.assignmentMosqueID != "synthetic-verification-mosque" {
		t.Fatalf("assignment scope = %s/%s", admin.assignmentMosqueID, admin.assignmentDeviceID)
	}
	admin.assignment.SnapshotSHA256 = strings.Repeat("0", 64)
	corrupt := request(t, http.MethodGet, server.URL+"/v1/devices/"+assignment.DeviceID+"/manifest", nil, "valid-device-token-0001", "")
	if corrupt.StatusCode != http.StatusInternalServerError || !strings.Contains(corrupt.Body, `"retryable":true`) {
		t.Fatalf("corrupt persistent assignment response = %d %s", corrupt.StatusCode, corrupt.Body)
	}
}

type capturedResponse struct {
	StatusCode int
	Header     http.Header
	Body       string
}

func request(t *testing.T, method, url string, body []byte, token, etag string) capturedResponse {
	t.Helper()
	req, err := http.NewRequest(method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatalf("NewRequest() error = %v", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if etag != "" {
		req.Header.Set("If-None-Match", etag)
	}
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("Do() error = %v", err)
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("ReadAll() error = %v", err)
	}
	return capturedResponse{StatusCode: response.StatusCode, Header: response.Header, Body: string(data)}
}

func decodeJSON(t *testing.T, data string, target any) {
	t.Helper()
	decoder := json.NewDecoder(bytes.NewBufferString(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		t.Fatalf("decode response: %v; body = %s", err, data)
	}
}

func mustReadSnapshot(t *testing.T) []byte {
	t.Helper()
	data, err := os.ReadFile("../../fixtures/verification/synthetic-signed-snapshot.json")
	if err != nil {
		t.Fatalf("read signed fixture: %v", err)
	}
	return data
}

func mustReadPublicKeys(t *testing.T) map[string]ed25519.PublicKey {
	t.Helper()
	data, err := os.ReadFile("../../fixtures/verification/phase1-public-key.json")
	if err != nil {
		t.Fatalf("read public key fixture: %v", err)
	}
	var fixture struct {
		SigningKeyID string `json:"signing_key_id"`
		PublicKey    string `json:"public_key_ed25519_base64"`
	}
	if err := json.Unmarshal(data, &fixture); err != nil {
		t.Fatalf("decode public key fixture: %v", err)
	}
	key, err := base64.StdEncoding.DecodeString(fixture.PublicKey)
	if err != nil {
		t.Fatalf("decode public key: %v", err)
	}
	return map[string]ed25519.PublicKey{fixture.SigningKeyID: key}
}

func validServiceConfig(t *testing.T) ServiceConfig {
	t.Helper()
	snapshot := mustReadSnapshot(t)
	hash := sha256.Sum256(snapshot)
	hashHex := hex.EncodeToString(hash[:])
	return ServiceConfig{
		PublicBaseURL:     "https://api.example.invalid",
		TrustedPublicKeys: mustReadPublicKeys(t),
		PairingFixtures: []PairingFixture{{
			Code: testCode, DeviceID: testDeviceID, Token: testToken,
			Mosque: MosqueIdentity{
				ID: "synthetic-verification-mosque", Name: "Synthetic verification fixture",
				Timezone: "Europe/Ulyanovsk",
			},
		}},
		Assignments: []DeviceAssignment{{
			DeviceID: testDeviceID, ManifestVersion: 1,
			SnapshotID:     "synthetic-android-verification-v1",
			SnapshotURL:    "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
			SnapshotSHA256: hashHex, SigningKeyID: testKeyID,
		}},
		Snapshots: []SnapshotArtifact{{
			ID: "synthetic-android-verification-v1", Bytes: snapshot,
			SHA256: hashHex, SigningKeyID: testKeyID,
		}},
	}
}

func TestSnapshotRegistryRejectsProductionArtifactWithoutPublicationReceipt(t *testing.T) {
	t.Parallel()

	fixture := mustReadSnapshot(t)
	production := bytes.Replace(fixture, []byte(`"data_classification": "synthetic"`), []byte(`"data_classification": "production"`), 1)
	if bytes.Equal(production, fixture) {
		t.Fatal("snapshot fixture classification was not replaced")
	}
	hash := sha256.Sum256(production)
	_, err := validateSnapshotArtifact(SnapshotArtifact{
		ID: "synthetic-android-verification-v1", Bytes: production,
		SHA256: hex.EncodeToString(hash[:]), SigningKeyID: testKeyID,
	}, mustReadPublicKeys(t), nil)
	if err == nil || !strings.Contains(err.Error(), "authenticated publication receipt") {
		t.Fatalf("missing production receipt error = %v", err)
	}
}

func TestProductionRegistryAnchorsOneAuthenticatedReceiptAsLedgerHead(t *testing.T) {
	t.Parallel()
	head := strings.Repeat("a", 64)
	previous := strings.Repeat("c", 64)
	receipts := map[string]string{head: previous, previous: ""}
	if err := validatePublicationLedgerAnchor(receipts, head); err != nil {
		t.Fatalf("valid ledger anchor rejected: %v", err)
	}
	for name, err := range map[string]error{
		"missing head":      validatePublicationLedgerAnchor(receipts, ""),
		"unknown head":      validatePublicationLedgerAnchor(receipts, strings.Repeat("b", 64)),
		"head without data": validatePublicationLedgerAnchor(nil, head),
		"signed fork": validatePublicationLedgerAnchor(
			map[string]string{head: previous, previous: "", strings.Repeat("d", 64): previous},
			head,
		),
	} {
		if err == nil {
			t.Errorf("%s unexpectedly succeeded", name)
		}
	}
}

type recordingHTTPPairingBackend struct {
	attempt      PairingAttempt
	provisioning PairingProvisioning
	pairErr      error
	principal    DevicePrincipal
	authErr      error
	heartbeat    DeviceHeartbeatReport
	heartbeatErr error
}

type deadlineHTTPPairingBackend struct{}

type deadlineHeartbeatBackend struct{}

func (*deadlineHTTPPairingBackend) Pair(ctx context.Context, _ PairingAttempt) (PairingProvisioning, error) {
	<-ctx.Done()
	return PairingProvisioning{}, ctx.Err()
}

func (*deadlineHTTPPairingBackend) Authenticate(ctx context.Context, _ string) (DevicePrincipal, error) {
	<-ctx.Done()
	return DevicePrincipal{}, ctx.Err()
}

func (*deadlineHTTPPairingBackend) Heartbeat(ctx context.Context, _ DevicePrincipal, _ DeviceHeartbeatReport) error {
	<-ctx.Done()
	return ctx.Err()
}

func (*deadlineHeartbeatBackend) Pair(_ context.Context, _ PairingAttempt) (PairingProvisioning, error) {
	return PairingProvisioning{}, errors.New("not used")
}

func (*deadlineHeartbeatBackend) Authenticate(_ context.Context, _ string) (DevicePrincipal, error) {
	return DevicePrincipal{
		DeviceID: "device-production-0001",
		Mosque:   MosqueIdentity{ID: "mosque-ulyanovsk-0001", Name: "Second Cathedral Mosque", Timezone: "Europe/Ulyanovsk"},
	}, nil
}

func (*deadlineHeartbeatBackend) Heartbeat(ctx context.Context, _ DevicePrincipal, _ DeviceHeartbeatReport) error {
	<-ctx.Done()
	return ctx.Err()
}

type recordingHTTPAdminBackend struct {
	principal          AdminPrincipal
	authErr            error
	authorizeErr       error
	authorizeCalls     int
	issued             IssuedPairing
	issueErr           error
	issueCommand       AdminIssuePairingCommand
	devices            []FleetDevice
	revokeCommand      AdminRevokeDeviceCommand
	assignCommand      AdminAssignDeviceCommand
	assignment         DeviceAssignment
	retryAssignment    DeviceAssignment
	retryFound         bool
	retryErr           error
	assignmentDeviceID string
	assignmentMosqueID string
	groupCommand       AdminSetRolloutGroupCommand
	rolloutCommand     AdminAssignRolloutGroupCommand
	rolloutResult      RolloutAssignmentResult
	rolloutRetry       RolloutAssignmentResult
	rolloutRetryFound  bool
	rolloutErr         error
	supportBundle      DeviceSupportBundle
	supportErr         error
	supportMosqueID    string
	supportDeviceID    string
	bindingCommand     AdminRegistryBindingCommand
	bindingRequest     RegistryBindingRequest
	bindingErr         error
}

type recordingHTTPRegistryBackend struct {
	cities         []registrydomain.CitySearchResult
	searchQuery    string
	searchErr      error
	resolution     registrydomain.Resolution
	resolveRequest registrydomain.ResolveRequest
	resolveErr     error
	assessment     registrydomain.RevisionPolicyAssessment
	assessmentID   string
	assessRequest  registrydomain.ResolveRequest
	assessmentErr  error
}

func (backend *recordingHTTPRegistryBackend) AssessRevision(
	_ context.Context,
	revisionID string,
	request registrydomain.ResolveRequest,
) (registrydomain.RevisionPolicyAssessment, error) {
	backend.assessmentID = revisionID
	backend.assessRequest = request
	return backend.assessment, backend.assessmentErr
}

func (backend *recordingHTTPRegistryBackend) SearchCities(_ context.Context, query string) ([]registrydomain.CitySearchResult, error) {
	backend.searchQuery = query
	return append([]registrydomain.CitySearchResult(nil), backend.cities...), backend.searchErr
}

func (backend *recordingHTTPRegistryBackend) Resolve(_ context.Context, request registrydomain.ResolveRequest) (registrydomain.Resolution, error) {
	backend.resolveRequest = request
	return backend.resolution, backend.resolveErr
}

func (backend *recordingHTTPAdminBackend) AuthorizeAdminScope(_ AdminPrincipal, _ string, _ bool) error {
	backend.authorizeCalls++
	return backend.authorizeErr
}

func (backend *recordingHTTPAdminBackend) AuthenticateAdmin(_ context.Context, _ string) (AdminPrincipal, error) {
	return backend.principal, backend.authErr
}

func (backend *recordingHTTPAdminBackend) IssuePairing(_ context.Context, _ AdminPrincipal, command AdminIssuePairingCommand) (IssuedPairing, error) {
	backend.issueCommand = command
	return backend.issued, backend.issueErr
}

func (backend *recordingHTTPAdminBackend) RequestRegistryBinding(
	_ context.Context,
	_ AdminPrincipal,
	command AdminRegistryBindingCommand,
) (RegistryBindingRequest, error) {
	backend.bindingCommand = command
	return backend.bindingRequest, backend.bindingErr
}

func (backend *recordingHTTPAdminBackend) ListDevices(_ context.Context, _ AdminPrincipal, _ string) ([]FleetDevice, error) {
	return append([]FleetDevice(nil), backend.devices...), nil
}

func (backend *recordingHTTPAdminBackend) RevokeDevice(_ context.Context, _ AdminPrincipal, command AdminRevokeDeviceCommand) error {
	backend.revokeCommand = command
	return nil
}

func (backend *recordingHTTPAdminBackend) RetryAssignment(_ context.Context, _ AdminPrincipal, _ AdminAssignmentRetryQuery) (DeviceAssignment, bool, error) {
	return backend.retryAssignment, backend.retryFound, backend.retryErr
}

func (backend *recordingHTTPAdminBackend) AssignDevice(_ context.Context, _ AdminPrincipal, command AdminAssignDeviceCommand) (DeviceAssignment, error) {
	backend.assignCommand = command
	if backend.assignment.DeviceID != "" {
		return backend.assignment, nil
	}
	return DeviceAssignment{
		DeviceID: command.DeviceID, ManifestVersion: 1, SnapshotID: command.SnapshotID,
		SnapshotURL: command.SnapshotURL, SnapshotSHA256: command.SnapshotSHA256,
		SigningKeyID: command.SigningKeyID, MinimumAppVersion: command.MinimumAppVersion,
	}, nil
}

func (backend *recordingHTTPAdminBackend) SetDeviceRolloutGroup(_ context.Context, _ AdminPrincipal, command AdminSetRolloutGroupCommand) error {
	backend.groupCommand = command
	return nil
}

func (backend *recordingHTTPAdminBackend) RetryRolloutAssignment(
	_ context.Context,
	_ AdminPrincipal,
	_ AdminRolloutAssignmentRetryQuery,
) (RolloutAssignmentResult, bool, error) {
	return backend.rolloutRetry, backend.rolloutRetryFound, nil
}

func (backend *recordingHTTPAdminBackend) AssignRolloutGroup(
	_ context.Context,
	_ AdminPrincipal,
	command AdminAssignRolloutGroupCommand,
) (RolloutAssignmentResult, error) {
	backend.rolloutCommand = command
	return backend.rolloutResult, backend.rolloutErr
}

func (backend *recordingHTTPAdminBackend) GetDeviceSupportBundle(
	_ context.Context,
	_ AdminPrincipal,
	mosqueID string,
	deviceID string,
) (DeviceSupportBundle, error) {
	backend.supportMosqueID = mosqueID
	backend.supportDeviceID = deviceID
	return backend.supportBundle, backend.supportErr
}

func (backend *recordingHTTPAdminBackend) GetDeviceAssignment(_ context.Context, deviceID, mosqueID string) (DeviceAssignment, error) {
	backend.assignmentDeviceID = deviceID
	backend.assignmentMosqueID = mosqueID
	if backend.assignment.DeviceID == "" {
		return DeviceAssignment{}, ErrDeviceAssignmentNotFound
	}
	return backend.assignment, nil
}

func adminRequest(t *testing.T, method, url string, body []byte, token, idempotencyKey string) capturedResponse {
	t.Helper()
	req, err := http.NewRequest(method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatalf("NewRequest(admin) error = %v", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if idempotencyKey != "" {
		req.Header.Set("Idempotency-Key", idempotencyKey)
	}
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("Do(admin) error = %v", err)
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("ReadAll(admin) error = %v", err)
	}
	return capturedResponse{StatusCode: response.StatusCode, Header: response.Header, Body: string(data)}
}

func (backend *recordingHTTPPairingBackend) Pair(_ context.Context, attempt PairingAttempt) (PairingProvisioning, error) {
	backend.attempt = attempt
	return backend.provisioning, backend.pairErr
}

func (backend *recordingHTTPPairingBackend) Authenticate(_ context.Context, _ string) (DevicePrincipal, error) {
	return backend.principal, backend.authErr
}

func (backend *recordingHTTPPairingBackend) Heartbeat(_ context.Context, _ DevicePrincipal, report DeviceHeartbeatReport) error {
	backend.heartbeat = report
	return backend.heartbeatErr
}

func pairBodySecret(_ []byte) string { return "ABCDEFGHIJKLMNOPQRSTUVWX26" }
