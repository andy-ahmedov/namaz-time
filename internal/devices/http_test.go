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
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
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
	var manifest DeviceManifest
	decodeJSON(t, manifestResponse.Body, &manifest)
	if manifest.SnapshotID != "synthetic-android-verification-v1" || manifest.SnapshotSHA256 != hashHex || manifest.SnapshotByteLength != int64(len(snapshot)) || manifest.SigningKeyID != testKeyID {
		t.Fatalf("manifest = %#v", manifest)
	}
	unchanged := request(t, http.MethodGet, manifestURL, nil, testToken, manifestETag)
	if unchanged.StatusCode != http.StatusNotModified || unchanged.Body != "" {
		t.Fatalf("unchanged manifest = %d %q", unchanged.StatusCode, unchanged.Body)
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

type recordingHTTPPairingBackend struct {
	attempt      PairingAttempt
	provisioning PairingProvisioning
	pairErr      error
	principal    DevicePrincipal
	authErr      error
}

type deadlineHTTPPairingBackend struct{}

func (*deadlineHTTPPairingBackend) Pair(ctx context.Context, _ PairingAttempt) (PairingProvisioning, error) {
	<-ctx.Done()
	return PairingProvisioning{}, ctx.Err()
}

func (*deadlineHTTPPairingBackend) Authenticate(ctx context.Context, _ string) (DevicePrincipal, error) {
	<-ctx.Done()
	return DevicePrincipal{}, ctx.Err()
}

func (backend *recordingHTTPPairingBackend) Pair(_ context.Context, attempt PairingAttempt) (PairingProvisioning, error) {
	backend.attempt = attempt
	return backend.provisioning, backend.pairErr
}

func (backend *recordingHTTPPairingBackend) Authenticate(_ context.Context, _ string) (DevicePrincipal, error) {
	return backend.principal, backend.authErr
}

func pairBodySecret(_ []byte) string { return "ABCDEFGHIJKLMNOPQRSTUVWX26" }
