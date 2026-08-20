// Command api serves the device pairing and immutable manifest/snapshot read path.
package main

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
)

const componentName = "api"

func main() {
	if err := run(os.Args[1:], os.Stderr); err != nil {
		log.New(os.Stderr, "api: ", 0).Println(err)
		os.Exit(1)
	}
}

func run(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet(componentName, flag.ContinueOnError)
	flags.SetOutput(stderr)
	configPath := flags.String("config", "", "path to a private runtime JSON configuration")
	listenAddress := flags.String("listen", "127.0.0.1:8080", "HTTP listen address behind the deployment TLS terminator")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *configPath == "" {
		return errors.New("-config is required; no fixture credentials or trust keys are built in")
	}
	service, err := loadRuntimeService(*configPath)
	if err != nil {
		return err
	}
	defer service.Close()
	server := &http.Server{
		Addr:              *listenAddress,
		Handler:           service.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 * 1024,
	}
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return fmt.Errorf("serve device API on %s: %w", *listenAddress, err)
	}
	return nil
}

type runtimeConfig struct {
	PublicBaseURL                        string                     `json:"public_base_url"`
	PairingFixtureMode                   string                     `json:"pairing_fixture_mode"`
	PairingBackend                       string                     `json:"pairing_backend"`
	DatabaseURLEnv                       string                     `json:"database_url_env"`
	PairingRateLimitKeyEnv               string                     `json:"pairing_rate_limit_key_env"`
	AdminIdempotencyKeyEnv               string                     `json:"admin_idempotency_key_env"`
	AdminCompatibilityIdempotencyKeyEnvs []string                   `json:"admin_compatibility_idempotency_key_envs"`
	PairingRateLimits                    runtimePairingRateLimits   `json:"pairing_rate_limits"`
	PairingBackendTimeoutSeconds         int64                      `json:"pairing_backend_timeout_seconds"`
	TrustedPublicKeyFiles                []string                   `json:"trusted_public_key_files"`
	PairingFixtures                      []runtimePairingFixture    `json:"pairing_fixtures"`
	Snapshots                            []runtimeSnapshot          `json:"snapshots"`
	Assignments                          []devices.DeviceAssignment `json:"assignments"`
}

type runtimePairingRateLimits struct {
	WindowSeconds  int64 `json:"window_seconds"`
	SourceAttempts int   `json:"source_attempts"`
	DeviceAttempts int   `json:"device_attempts"`
	CodeAttempts   int   `json:"code_attempts"`
}

type runtimePairingFixture struct {
	Code     string                 `json:"code"`
	DeviceID string                 `json:"device_id"`
	Token    string                 `json:"token"`
	Mosque   devices.MosqueIdentity `json:"mosque"`
}

type runtimeSnapshot struct {
	SnapshotID   string `json:"snapshot_id"`
	File         string `json:"file"`
	SHA256       string `json:"sha256"`
	SigningKeyID string `json:"signing_key_id"`
}

type publicKeyFile struct {
	SigningKeyID       string `json:"signing_key_id"`
	PublicKey          string `json:"public_key_ed25519_base64"`
	Classification     string `json:"classification"`
	PrivateKeyRetained bool   `json:"private_key_retained"`
}

func loadRuntimeService(configPath string) (*devices.Service, error) {
	absoluteConfig, err := filepath.Abs(configPath)
	if err != nil {
		return nil, fmt.Errorf("resolve runtime config: %w", err)
	}
	info, err := os.Lstat(absoluteConfig)
	if err != nil {
		return nil, fmt.Errorf("inspect runtime config: %w", err)
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
		return nil, errors.New("runtime config must be a regular non-symlink file")
	}
	if info.Mode().Perm()&0o077 != 0 {
		return nil, errors.New("runtime config contains credentials and must not be group/world accessible")
	}
	if info.Size() <= 0 || info.Size() > 1024*1024 {
		return nil, errors.New("runtime config size is invalid")
	}
	data, err := os.ReadFile(absoluteConfig)
	if err != nil {
		return nil, fmt.Errorf("read runtime config: %w", err)
	}
	var config runtimeConfig
	if err := decodeStrictJSON(data, &config); err != nil {
		return nil, fmt.Errorf("decode runtime config: %w", err)
	}
	if len(config.PairingFixtures) > 0 && config.PairingFixtureMode != "ephemeral-test-only" {
		return nil, errors.New("pairing fixtures require explicit ephemeral-test-only mode")
	}
	if len(config.PairingFixtures) == 0 && config.PairingFixtureMode != "" {
		return nil, errors.New("pairing fixture mode is set without fixtures")
	}
	if len(config.PairingFixtures) > 0 && config.PairingBackend != "" {
		return nil, errors.New("pairing fixtures and production backend are mutually exclusive")
	}
	baseDirectory := filepath.Dir(absoluteConfig)
	trustedKeys := make(map[string]ed25519.PublicKey, len(config.TrustedPublicKeyFiles))
	for _, relativePath := range config.TrustedPublicKeyFiles {
		keyBytes, err := readContainedRegularFile(baseDirectory, relativePath, 16*1024)
		if err != nil {
			return nil, fmt.Errorf("read trusted public key file: %w", err)
		}
		var keyFile publicKeyFile
		if err := decodeStrictJSON(keyBytes, &keyFile); err != nil {
			return nil, fmt.Errorf("decode trusted public key file: %w", err)
		}
		decoded, err := base64.StdEncoding.DecodeString(keyFile.PublicKey)
		if err != nil || len(decoded) != ed25519.PublicKeySize || keyFile.SigningKeyID == "" ||
			keyFile.PrivateKeyRetained ||
			(keyFile.Classification != "test-only-public-key" && keyFile.Classification != "production-public-key") {
			return nil, errors.New("trusted public key file is invalid")
		}
		if _, duplicate := trustedKeys[keyFile.SigningKeyID]; duplicate {
			return nil, fmt.Errorf("duplicate trusted signing key ID %q", keyFile.SigningKeyID)
		}
		trustedKeys[keyFile.SigningKeyID] = ed25519.PublicKey(append([]byte(nil), decoded...))
	}
	snapshots := make([]devices.SnapshotArtifact, 0, len(config.Snapshots))
	for _, snapshot := range config.Snapshots {
		body, err := readContainedRegularFile(baseDirectory, snapshot.File, 5*1024*1024)
		if err != nil {
			return nil, fmt.Errorf("read snapshot %q: %w", snapshot.SnapshotID, err)
		}
		snapshots = append(snapshots, devices.SnapshotArtifact{
			ID: snapshot.SnapshotID, Bytes: body, SHA256: snapshot.SHA256,
			SigningKeyID: snapshot.SigningKeyID,
		})
	}
	pairings := make([]devices.PairingFixture, len(config.PairingFixtures))
	for index, fixture := range config.PairingFixtures {
		pairings[index] = devices.PairingFixture{
			Code: fixture.Code, DeviceID: fixture.DeviceID, Token: fixture.Token, Mosque: fixture.Mosque,
		}
	}
	var pairingBackend devices.PairingBackend
	var adminBackend devices.AdminFleetBackend
	if config.PairingBackend == "postgres" {
		if len(config.PairingFixtures) > 0 || config.PairingFixtureMode != "" || len(config.Assignments) > 0 {
			return nil, errors.New("PostgreSQL pairing cannot use ephemeral fixtures or static device assignments")
		}
		if !validEnvironmentName(config.DatabaseURLEnv) || !validEnvironmentName(config.PairingRateLimitKeyEnv) ||
			!validEnvironmentName(config.AdminIdempotencyKeyEnv) {
			return nil, errors.New("PostgreSQL pairing environment variable names are invalid")
		}
		databaseURL, exists := os.LookupEnv(config.DatabaseURLEnv)
		if !exists || strings.TrimSpace(databaseURL) == "" {
			return nil, errors.New("PostgreSQL pairing database environment variable is not set")
		}
		encodedRateKey, exists := os.LookupEnv(config.PairingRateLimitKeyEnv)
		if !exists {
			return nil, errors.New("PostgreSQL pairing rate-limit key environment variable is not set")
		}
		rateKey, err := base64.StdEncoding.DecodeString(encodedRateKey)
		if err != nil || len(rateKey) != sha256.Size {
			return nil, errors.New("PostgreSQL pairing rate-limit key must be base64 for exactly 32 bytes")
		}
		encodedAdminKey, exists := os.LookupEnv(config.AdminIdempotencyKeyEnv)
		if !exists {
			return nil, errors.New("PostgreSQL admin idempotency key environment variable is not set")
		}
		adminKey, err := base64.StdEncoding.DecodeString(encodedAdminKey)
		if err != nil || len(adminKey) != sha256.Size {
			return nil, errors.New("PostgreSQL admin idempotency key must be base64 for exactly 32 bytes")
		}
		compatibilityAdminKeys := make([][]byte, 0, len(config.AdminCompatibilityIdempotencyKeyEnvs))
		if len(config.AdminCompatibilityIdempotencyKeyEnvs) > 8 {
			return nil, errors.New("PostgreSQL admin idempotency key ring allows at most eight compatibility keys")
		}
		seenAdminKeyEnvs := map[string]struct{}{config.AdminIdempotencyKeyEnv: {}}
		for _, environmentName := range config.AdminCompatibilityIdempotencyKeyEnvs {
			if !validEnvironmentName(environmentName) {
				return nil, errors.New("PostgreSQL compatibility admin idempotency key environment variable name is invalid")
			}
			if _, duplicate := seenAdminKeyEnvs[environmentName]; duplicate {
				return nil, errors.New("PostgreSQL admin idempotency key environment variable names must be unique")
			}
			seenAdminKeyEnvs[environmentName] = struct{}{}
			encodedCompatibilityKey, exists := os.LookupEnv(environmentName)
			if !exists {
				return nil, errors.New("PostgreSQL compatibility admin idempotency key environment variable is not set")
			}
			compatibilityKey, err := base64.StdEncoding.DecodeString(encodedCompatibilityKey)
			if err != nil || len(compatibilityKey) != sha256.Size {
				return nil, errors.New("PostgreSQL compatibility admin idempotency key must be base64 for exactly 32 bytes")
			}
			compatibilityAdminKeys = append(compatibilityAdminKeys, compatibilityKey)
		}
		limits := devices.PairingRateLimits{
			Window:         time.Duration(config.PairingRateLimits.WindowSeconds) * time.Second,
			SourceAttempts: config.PairingRateLimits.SourceAttempts,
			DeviceAttempts: config.PairingRateLimits.DeviceAttempts,
			CodeAttempts:   config.PairingRateLimits.CodeAttempts,
		}
		if config.PairingBackendTimeoutSeconds < 0 || config.PairingBackendTimeoutSeconds > 30 {
			return nil, errors.New("PostgreSQL pairing backend timeout must be zero for the default or at most 30 seconds")
		}
		openContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		repository, err := devices.OpenPostgresPairingRepository(openContext, databaseURL)
		if err != nil {
			return nil, err
		}
		if err := repository.VerifySchema(openContext); err != nil {
			repository.Close()
			return nil, err
		}
		manager, err := devices.NewPairingManager(devices.PairingManagerConfig{
			Repository: repository, RateLimitKey: rateKey, RateLimits: limits,
		})
		if err != nil {
			repository.Close()
			return nil, err
		}
		pairingBackend = manager
		adminManager, err := devices.NewAdminFleetManager(devices.AdminFleetManagerConfig{
			Repository: repository, IdempotencyKey: adminKey, CompatibilityIdempotencyKeys: compatibilityAdminKeys,
		})
		if err != nil {
			repository.Close()
			return nil, err
		}
		adminBackend = adminManager
	} else if config.PairingBackend != "" {
		return nil, errors.New("unsupported pairing backend")
	} else if config.DatabaseURLEnv != "" || config.PairingRateLimitKeyEnv != "" || config.AdminIdempotencyKeyEnv != "" || len(config.AdminCompatibilityIdempotencyKeyEnvs) > 0 || config.PairingRateLimits != (runtimePairingRateLimits{}) || config.PairingBackendTimeoutSeconds != 0 {
		return nil, errors.New("PostgreSQL pairing settings require pairing_backend postgres")
	}
	service, err := devices.NewService(devices.ServiceConfig{
		PublicBaseURL:     config.PublicBaseURL,
		PairingFixtures:   pairings,
		PairingBackend:    pairingBackend,
		AdminBackend:      adminBackend,
		BackendTimeout:    time.Duration(config.PairingBackendTimeoutSeconds) * time.Second,
		Assignments:       config.Assignments,
		Snapshots:         snapshots,
		TrustedPublicKeys: trustedKeys,
	})
	if err != nil {
		if closer, ok := pairingBackend.(interface{ Close() }); ok {
			closer.Close()
		}
		return nil, fmt.Errorf("configure device service: %w", err)
	}
	return service, nil
}

func validEnvironmentName(value string) bool {
	if len(value) < 1 || len(value) > 128 || value[0] < 'A' || value[0] > 'Z' {
		return false
	}
	for _, character := range value[1:] {
		if (character < 'A' || character > 'Z') && (character < '0' || character > '9') && character != '_' {
			return false
		}
	}
	return true
}

func readContainedRegularFile(baseDirectory, relativePath string, maximumSize int64) ([]byte, error) {
	if relativePath == "" || filepath.IsAbs(relativePath) {
		return nil, errors.New("artifact path must be non-empty and relative to runtime config")
	}
	resolved := filepath.Join(baseDirectory, filepath.Clean(relativePath))
	relative, err := filepath.Rel(baseDirectory, resolved)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return nil, errors.New("artifact path escapes runtime config directory")
	}
	info, err := os.Lstat(resolved)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
		return nil, errors.New("artifact must be a regular non-symlink file")
	}
	if info.Size() <= 0 || info.Size() > maximumSize {
		return nil, errors.New("artifact size is invalid")
	}
	return os.ReadFile(resolved)
}

func decodeStrictJSON(data []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}
