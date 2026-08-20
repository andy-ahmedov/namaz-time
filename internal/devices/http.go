// Package devices implements the device-facing pairing and immutable read path.
package devices

import (
	"crypto/ed25519"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
)

const (
	maxPairRequestBytes = 16 * 1024
	maxSnapshotBytes    = 5 * 1024 * 1024
)

type MosqueIdentity struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Timezone string `json:"timezone"`
}

type PairingFixture struct {
	Code     string
	DeviceID string
	Token    string
	Mosque   MosqueIdentity
}

type DeviceAssignment struct {
	DeviceID          string `json:"device_id"`
	ManifestVersion   int64  `json:"manifest_version"`
	SnapshotID        string `json:"snapshot_id"`
	SnapshotURL       string `json:"snapshot_url"`
	SnapshotSHA256    string `json:"snapshot_sha256"`
	SigningKeyID      string `json:"signing_key_id"`
	MinimumAppVersion string `json:"minimum_app_version,omitempty"`
}

type SnapshotArtifact struct {
	ID           string
	Bytes        []byte
	SHA256       string
	SigningKeyID string
}

type ServiceConfig struct {
	PublicBaseURL     string
	PairingFixtures   []PairingFixture
	Assignments       []DeviceAssignment
	Snapshots         []SnapshotArtifact
	TrustedPublicKeys map[string]ed25519.PublicKey
}

type DeviceManifest struct {
	ManifestVersion    int64  `json:"manifest_version"`
	SnapshotID         string `json:"snapshot_id"`
	SnapshotURL        string `json:"snapshot_url"`
	SnapshotSHA256     string `json:"snapshot_sha256"`
	SnapshotByteLength int64  `json:"snapshot_byte_length"`
	SigningKeyID       string `json:"signing_key_id"`
	MinimumAppVersion  string `json:"minimum_app_version,omitempty"`
}

type pairingRecord struct {
	codeHash [sha256.Size]byte
	deviceID string
	token    string
	mosque   MosqueIdentity
	used     bool
}

type snapshotRecord struct {
	id           string
	bytes        []byte
	sha256       string
	signingKeyID string
	mosqueID     string
	timezone     string
}

type Service struct {
	mu          sync.Mutex
	pairings    []*pairingRecord
	assignments map[string]DeviceAssignment
	snapshots   map[string]snapshotRecord
	tokens      map[string]string
	mosques     map[string]MosqueIdentity
	publicBase  *url.URL
	handler     http.Handler
}

func NewService(config ServiceConfig) (*Service, error) {
	service := &Service{
		assignments: make(map[string]DeviceAssignment, len(config.Assignments)),
		snapshots:   make(map[string]snapshotRecord, len(config.Snapshots)),
		tokens:      make(map[string]string, len(config.PairingFixtures)),
		mosques:     make(map[string]MosqueIdentity, len(config.PairingFixtures)),
	}
	if config.PublicBaseURL != "" {
		publicBase, err := parsePublicBaseURL(config.PublicBaseURL)
		if err != nil {
			return nil, fmt.Errorf("configure public base URL: %w", err)
		}
		service.publicBase = publicBase
	}
	codeHashes := make(map[[sha256.Size]byte]struct{}, len(config.PairingFixtures))
	credentialHashes := make(map[[sha256.Size]byte]struct{}, len(config.PairingFixtures))
	for _, fixture := range config.PairingFixtures {
		if err := validatePairingFixture(fixture); err != nil {
			return nil, fmt.Errorf("configure pairing fixture %q: %w", fixture.DeviceID, err)
		}
		codeHash := sha256.Sum256([]byte(fixture.Code))
		credentialHash := sha256.Sum256([]byte(fixture.Token))
		if _, exists := service.tokens[fixture.DeviceID]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate device", fixture.DeviceID)
		}
		if _, exists := codeHashes[codeHash]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate pairing code", fixture.DeviceID)
		}
		if _, exists := credentialHashes[credentialHash]; exists {
			return nil, fmt.Errorf("configure pairing fixture %q: duplicate bearer credential", fixture.DeviceID)
		}
		service.pairings = append(service.pairings, &pairingRecord{
			codeHash: codeHash,
			deviceID: fixture.DeviceID,
			token:    fixture.Token,
			mosque:   fixture.Mosque,
		})
		codeHashes[codeHash] = struct{}{}
		credentialHashes[credentialHash] = struct{}{}
		service.tokens[fixture.DeviceID] = fixture.Token
		service.mosques[fixture.DeviceID] = fixture.Mosque
	}
	for _, artifact := range config.Snapshots {
		snapshot, err := validateSnapshotArtifact(artifact, config.TrustedPublicKeys)
		if err != nil {
			return nil, fmt.Errorf("configure snapshot %q: %w", artifact.ID, err)
		}
		if _, exists := service.snapshots[artifact.ID]; exists {
			return nil, fmt.Errorf("configure snapshot %q: duplicate ID", artifact.ID)
		}
		service.snapshots[artifact.ID] = snapshotRecord{
			id:           artifact.ID,
			bytes:        append([]byte(nil), artifact.Bytes...),
			sha256:       artifact.SHA256,
			signingKeyID: artifact.SigningKeyID,
			mosqueID:     snapshot.Mosque.ID,
			timezone:     snapshot.Mosque.Timezone,
		}
	}
	for _, assignment := range config.Assignments {
		if err := service.validateAssignment(assignment); err != nil {
			return nil, fmt.Errorf("configure assignment %q: %w", assignment.DeviceID, err)
		}
		if _, exists := service.assignments[assignment.DeviceID]; exists {
			return nil, fmt.Errorf("configure assignment %q: duplicate device", assignment.DeviceID)
		}
		service.assignments[assignment.DeviceID] = assignment
	}
	service.handler = service.routes()
	return service, nil
}

func (s *Service) Handler() http.Handler { return s.handler }

func (s *Service) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/devices/pair", s.handlePair)
	mux.HandleFunc("GET /v1/devices/{deviceId}/manifest", s.handleManifest)
	mux.HandleFunc("GET /v1/snapshots/{snapshotId}", s.handleSnapshot)
	return securityHeaders(mux)
}

type pairRequest struct {
	PairingCode           string     `json:"pairing_code"`
	InstallationPublicKey string     `json:"installation_public_key,omitempty"`
	Device                deviceInfo `json:"device"`
}

type deviceInfo struct {
	AppVersion   string   `json:"app_version"`
	OSVersion    string   `json:"os_version"`
	Model        string   `json:"model"`
	Capabilities []string `json:"capabilities,omitempty"`
}

type pairResponse struct {
	DeviceID    string         `json:"device_id"`
	DeviceToken string         `json:"device_token"`
	Mosque      MosqueIdentity `json:"mosque"`
	ManifestURL string         `json:"manifest_url"`
}

func (s *Service) handlePair(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Cache-Control", "no-store")
	mediaType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maxPairRequestBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	var input pairRequest
	if err := decoder.Decode(&input); err != nil || decodeEOF(decoder) != nil || !validPairRequest(input) {
		writeAPIError(writer, http.StatusBadRequest, "invalid_request", false)
		return
	}
	providedHash := sha256.Sum256([]byte(input.PairingCode))
	s.mu.Lock()
	var matched *pairingRecord
	for _, fixture := range s.pairings {
		if subtle.ConstantTimeCompare(providedHash[:], fixture.codeHash[:]) == 1 && !fixture.used {
			matched = fixture
		}
	}
	if matched != nil {
		matched.used = true
	}
	s.mu.Unlock()
	if matched == nil {
		writeAPIError(writer, http.StatusBadRequest, "pairing_code_invalid", false)
		return
	}
	writeJSON(writer, http.StatusOK, pairResponse{
		DeviceID: matched.deviceID, DeviceToken: matched.token, Mosque: matched.mosque,
		ManifestURL: "/v1/devices/" + url.PathEscape(matched.deviceID) + "/manifest",
	})
}

func (s *Service) handleManifest(writer http.ResponseWriter, request *http.Request) {
	deviceID := request.PathValue("deviceId")
	if !s.authenticated(request, deviceID) {
		writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
		return
	}
	assignment, exists := s.assignments[deviceID]
	if !exists {
		writeAPIError(writer, http.StatusNotFound, "manifest_not_found", false)
		return
	}
	artifact := s.snapshots[assignment.SnapshotID]
	manifest := DeviceManifest{
		ManifestVersion: assignment.ManifestVersion, SnapshotID: assignment.SnapshotID,
		SnapshotURL: assignment.SnapshotURL, SnapshotSHA256: assignment.SnapshotSHA256,
		SnapshotByteLength: int64(len(artifact.bytes)), SigningKeyID: assignment.SigningKeyID,
		MinimumAppVersion: assignment.MinimumAppVersion,
	}
	body, err := json.Marshal(manifest)
	if err != nil {
		writeAPIError(writer, http.StatusInternalServerError, "internal_error", true)
		return
	}
	etagHash := sha256.Sum256(body)
	etag := `"` + hex.EncodeToString(etagHash[:]) + `"`
	writer.Header().Set("Cache-Control", "private, max-age=0, must-revalidate")
	writer.Header().Set("ETag", etag)
	if request.Header.Get("If-None-Match") == etag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	writeJSONBytes(writer, http.StatusOK, body)
}

func (s *Service) handleSnapshot(writer http.ResponseWriter, request *http.Request) {
	snapshotID := request.PathValue("snapshotId")
	deviceID, authenticated := s.authenticatedDevice(request)
	if !authenticated {
		writeAPIError(writer, http.StatusUnauthorized, "device_unauthorized", false)
		return
	}
	assignment, assigned := s.assignments[deviceID]
	if !assigned || assignment.SnapshotID != snapshotID {
		writeAPIError(writer, http.StatusNotFound, "snapshot_not_found", false)
		return
	}
	artifact, exists := s.snapshots[snapshotID]
	if !exists {
		writeAPIError(writer, http.StatusNotFound, "snapshot_not_found", false)
		return
	}
	etag := `"` + artifact.sha256 + `"`
	writer.Header().Set("Cache-Control", "private, max-age=86400, immutable")
	writer.Header().Set("ETag", etag)
	if request.Header.Get("If-None-Match") == etag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	hashBytes, _ := hex.DecodeString(artifact.sha256)
	writer.Header().Set("Content-Type", "application/json")
	writer.Header().Set("Content-Length", fmt.Sprintf("%d", len(artifact.bytes)))
	writer.Header().Set("Digest", "sha-256="+base64.StdEncoding.EncodeToString(hashBytes))
	writer.WriteHeader(http.StatusOK)
	_, _ = writer.Write(artifact.bytes)
}

func (s *Service) authenticated(request *http.Request, expectedDeviceID string) bool {
	deviceID, ok := s.authenticatedDevice(request)
	return ok && subtle.ConstantTimeCompare([]byte(deviceID), []byte(expectedDeviceID)) == 1
}

func (s *Service) authenticatedDevice(request *http.Request) (string, bool) {
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") || len(header) == len("Bearer ") {
		return "", false
	}
	provided := header[len("Bearer "):]
	for deviceID, token := range s.tokens {
		if len(provided) == len(token) && subtle.ConstantTimeCompare([]byte(provided), []byte(token)) == 1 {
			return deviceID, true
		}
	}
	return "", false
}

func (s *Service) validateAssignment(assignment DeviceAssignment) error {
	if !validIdentifier(assignment.DeviceID) || assignment.ManifestVersion < 1 || !validIdentifier(assignment.SnapshotID) || !validSHA256(assignment.SnapshotSHA256) || len(assignment.SigningKeyID) < 1 || len(assignment.SigningKeyID) > 128 || len(assignment.MinimumAppVersion) > 64 {
		return errors.New("required assignment metadata is invalid")
	}
	if _, exists := s.tokens[assignment.DeviceID]; !exists {
		return errors.New("device has no pairing credential")
	}
	artifact, exists := s.snapshots[assignment.SnapshotID]
	if !exists {
		return errors.New("snapshot does not exist")
	}
	pairedMosque := s.mosques[assignment.DeviceID]
	if artifact.mosqueID != pairedMosque.ID || artifact.timezone != pairedMosque.Timezone {
		return errors.New("snapshot mosque identity does not match paired device mosque")
	}
	if s.publicBase == nil {
		return errors.New("public base URL is required when assignments exist")
	}
	parsed, err := url.Parse(assignment.SnapshotURL)
	wantPath := "/v1/snapshots/" + url.PathEscape(assignment.SnapshotID)
	if err != nil || !sameOrigin(parsed, s.publicBase) || parsed.User != nil || parsed.Fragment != "" || parsed.RawQuery != "" || parsed.EscapedPath() != wantPath {
		return errors.New("snapshot URL must use the public API origin and canonical snapshot path")
	}
	if artifact.sha256 != assignment.SnapshotSHA256 || artifact.signingKeyID != assignment.SigningKeyID {
		return errors.New("assignment does not match immutable snapshot identity")
	}
	return nil
}

func validatePairingFixture(fixture PairingFixture) error {
	if len(fixture.Code) < 6 || len(fixture.Code) > 32 || !validIdentifier(fixture.DeviceID) || len(fixture.Token) < 16 || len(fixture.Token) > 4096 {
		return errors.New("code, device ID or token is invalid")
	}
	if len(fixture.Mosque.ID) < 1 || len(fixture.Mosque.ID) > 128 || len(fixture.Mosque.Name) < 1 || len(fixture.Mosque.Name) > 240 || len(fixture.Mosque.Timezone) < 3 || len(fixture.Mosque.Timezone) > 64 {
		return errors.New("mosque identity is incomplete")
	}
	zone, err := time.LoadLocation(fixture.Mosque.Timezone)
	if err != nil || zone.String() != fixture.Mosque.Timezone || strings.HasPrefix(fixture.Mosque.Timezone, "+") || strings.HasPrefix(fixture.Mosque.Timezone, "-") {
		return errors.New("mosque timezone must be a named IANA zone")
	}
	return nil
}

func validateSnapshotArtifact(artifact SnapshotArtifact, trustedPublicKeys map[string]ed25519.PublicKey) (domain.Snapshot, error) {
	if !validIdentifier(artifact.ID) || len(artifact.Bytes) == 0 || len(artifact.Bytes) > maxSnapshotBytes || !validSHA256(artifact.SHA256) || len(artifact.SigningKeyID) < 1 || len(artifact.SigningKeyID) > 128 {
		return domain.Snapshot{}, errors.New("snapshot metadata is invalid")
	}
	actual := sha256.Sum256(artifact.Bytes)
	if hex.EncodeToString(actual[:]) != artifact.SHA256 {
		return domain.Snapshot{}, errors.New("snapshot SHA-256 does not match bytes")
	}
	if err := publication.Verify(artifact.Bytes, trustedPublicKeys); err != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot authenticity validation failed: %w", err)
	}
	snapshot, err := domain.DecodeSnapshot(artifact.Bytes)
	if err != nil {
		return domain.Snapshot{}, fmt.Errorf("snapshot contract decoding failed: %w", err)
	}
	if snapshot.SnapshotID != artifact.ID || snapshot.Integrity.SigningKeyID != artifact.SigningKeyID {
		return domain.Snapshot{}, errors.New("snapshot payload identity does not match artifact metadata")
	}
	return snapshot, nil
}

func parsePublicBaseURL(value string) (*url.URL, error) {
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil || parsed.Fragment != "" || parsed.RawQuery != "" || (parsed.EscapedPath() != "" && parsed.EscapedPath() != "/") {
		return nil, errors.New("must be an HTTPS origin without path, query, user info or fragment")
	}
	return parsed, nil
}

func sameOrigin(left, right *url.URL) bool {
	return strings.EqualFold(left.Scheme, right.Scheme) && strings.EqualFold(left.Hostname(), right.Hostname()) && effectivePort(left) == effectivePort(right)
}

func effectivePort(value *url.URL) string {
	if value.Port() != "" {
		return value.Port()
	}
	return "443"
}

func validPairRequest(input pairRequest) bool {
	if len(input.PairingCode) < 6 || len(input.PairingCode) > 32 || input.Device.AppVersion == "" || len(input.Device.AppVersion) > 64 || input.Device.OSVersion == "" || len(input.Device.OSVersion) > 128 || input.Device.Model == "" || len(input.Device.Model) > 240 || len(input.InstallationPublicKey) > 4096 {
		return false
	}
	seen := make(map[string]struct{}, len(input.Device.Capabilities))
	for _, capability := range input.Device.Capabilities {
		if capability == "" || len(capability) > 128 {
			return false
		}
		if _, duplicate := seen[capability]; duplicate {
			return false
		}
		seen[capability] = struct{}{}
	}
	return true
}

func validIdentifier(value string) bool { return len(value) >= 8 && len(value) <= 128 }

func validSHA256(value string) bool {
	if len(value) != sha256.Size*2 || strings.ToLower(value) != value {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func decodeEOF(decoder *json.Decoder) error {
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}

func writeAPIError(writer http.ResponseWriter, status int, code string, retryable bool) {
	writeJSON(writer, status, struct {
		Error struct {
			Code      string `json:"code"`
			Message   string `json:"message"`
			Retryable bool   `json:"retryable"`
		} `json:"error"`
	}{Error: struct {
		Code      string `json:"code"`
		Message   string `json:"message"`
		Retryable bool   `json:"retryable"`
	}{Code: code, Message: "Request could not be completed", Retryable: retryable}})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	body, err := json.Marshal(value)
	if err != nil {
		http.Error(writer, "internal error", http.StatusInternalServerError)
		return
	}
	writeJSONBytes(writer, status, body)
}

func writeJSONBytes(writer http.ResponseWriter, status int, body []byte) {
	writer.Header().Set("Content-Type", "application/json")
	writer.Header().Set("Content-Length", fmt.Sprintf("%d", len(body)))
	writer.WriteHeader(status)
	_, _ = writer.Write(body)
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("X-Content-Type-Options", "nosniff")
		writer.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(writer, request)
	})
}
