// Command api serves the device pairing and immutable manifest/snapshot read path.
package main

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
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
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
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
	RegistryBackend                      string                     `json:"registry_backend"`
	DatabaseURLEnv                       string                     `json:"database_url_env"`
	PairingRateLimitKeyEnv               string                     `json:"pairing_rate_limit_key_env"`
	AdminIdempotencyKeyEnv               string                     `json:"admin_idempotency_key_env"`
	AdminCompatibilityIdempotencyKeyEnvs []string                   `json:"admin_compatibility_idempotency_key_envs"`
	PairingRateLimits                    runtimePairingRateLimits   `json:"pairing_rate_limits"`
	PairingBackendTimeoutSeconds         int64                      `json:"pairing_backend_timeout_seconds"`
	TrustedPublicKeyFiles                []string                   `json:"trusted_public_key_files"`
	TrustBundleFile                      string                     `json:"trust_bundle_file"`
	PreviousTrustBundleFile              string                     `json:"previous_trust_bundle_file"`
	TestTrustBundleFile                  string                     `json:"test_trust_bundle_file"`
	StagingTrustBundleFile               string                     `json:"staging_trust_bundle_file"`
	PublicationLedgerHeadFile            string                     `json:"publication_ledger_head_file"`
	MinimumTrustBundleRevision           uint64                     `json:"minimum_trust_bundle_revision"`
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
	SnapshotID          string `json:"snapshot_id"`
	File                string `json:"file"`
	ReceiptFile         string `json:"receipt_file,omitempty"`
	PreviousReceiptFile string `json:"previous_receipt_file,omitempty"`
	SHA256              string `json:"sha256"`
	SigningKeyID        string `json:"signing_key_id"`
}

type runtimePublicationLedgerHead struct {
	SchemaVersion string `json:"schema_version"`
	Environment   string `json:"environment"`
	ReceiptSHA256 string `json:"receipt_sha256"`
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
	if config.TrustBundleFile != "" && len(config.TrustedPublicKeyFiles) > 0 {
		return nil, errors.New("trust bundle and legacy public key files are mutually exclusive")
	}
	var trustPolicy *trust.Policy
	if config.TrustBundleFile != "" {
		bundleBytes, err := readContainedRegularFile(baseDirectory, config.TrustBundleFile, 256*1024)
		if err != nil {
			return nil, fmt.Errorf("read trust bundle: %w", err)
		}
		trustPolicy, err = trust.Decode(bundleBytes)
		if err != nil {
			return nil, fmt.Errorf("decode trust bundle: %w", err)
		}
		if trustPolicy.Environment() == "production" {
			if config.MinimumTrustBundleRevision == 0 || trustPolicy.Revision() < config.MinimumTrustBundleRevision {
				return nil, errors.New("production trust bundle is below the pinned minimum revision")
			}
			if config.TestTrustBundleFile == "" || config.StagingTrustBundleFile == "" {
				return nil, errors.New("production trust requires test and staging comparison bundles")
			}
		} else if config.MinimumTrustBundleRevision != 0 {
			return nil, errors.New("minimum trust bundle revision is production-only")
		}
		if trustPolicy.Revision() > 1 && config.PreviousTrustBundleFile == "" {
			return nil, errors.New("non-genesis trust bundle requires its directly preceding bundle")
		}
		if config.PreviousTrustBundleFile != "" {
			previousBytes, readErr := readContainedRegularFile(baseDirectory, config.PreviousTrustBundleFile, 256*1024)
			if readErr != nil {
				return nil, fmt.Errorf("read previous trust bundle: %w", readErr)
			}
			previousPolicy, decodeErr := trust.Decode(previousBytes)
			if decodeErr != nil {
				return nil, fmt.Errorf("decode previous trust bundle: %w", decodeErr)
			}
			if transitionErr := trust.ValidateTransition(previousPolicy, trustPolicy); transitionErr != nil {
				return nil, fmt.Errorf("validate trust bundle transition: %w", transitionErr)
			}
		}
		if trustPolicy.Environment() == "production" {
			comparison := make([]*trust.Policy, 0, 3)
			for _, item := range []struct {
				path        string
				environment string
			}{{config.TestTrustBundleFile, "test"}, {config.StagingTrustBundleFile, "staging"}} {
				comparisonBytes, readErr := readContainedRegularFile(baseDirectory, item.path, 256*1024)
				if readErr != nil {
					return nil, fmt.Errorf("read %s trust bundle: %w", item.environment, readErr)
				}
				comparisonPolicy, decodeErr := trust.Decode(comparisonBytes)
				if decodeErr != nil || comparisonPolicy.Environment() != item.environment {
					return nil, fmt.Errorf("decode %s trust bundle: invalid environment bundle", item.environment)
				}
				comparison = append(comparison, comparisonPolicy)
			}
			comparison = append(comparison, trustPolicy)
			if separationErr := trust.ValidateEnvironmentSeparation(comparison...); separationErr != nil {
				return nil, fmt.Errorf("validate trust environment separation: %w", separationErr)
			}
		} else if config.TestTrustBundleFile != "" || config.StagingTrustBundleFile != "" {
			return nil, errors.New("trust comparison bundles are production-only")
		}
	} else if config.MinimumTrustBundleRevision != 0 {
		return nil, errors.New("minimum trust bundle revision requires a trust bundle")
	} else if config.PreviousTrustBundleFile != "" {
		return nil, errors.New("previous trust bundle requires a current trust bundle")
	} else if config.TestTrustBundleFile != "" || config.StagingTrustBundleFile != "" {
		return nil, errors.New("trust comparison bundles require a current production bundle")
	}
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
			keyFile.Classification != "test-only-public-key" {
			return nil, errors.New("trusted public key file is invalid")
		}
		if _, duplicate := trustedKeys[keyFile.SigningKeyID]; duplicate {
			return nil, fmt.Errorf("duplicate trusted signing key ID %q", keyFile.SigningKeyID)
		}
		trustedKeys[keyFile.SigningKeyID] = ed25519.PublicKey(append([]byte(nil), decoded...))
	}
	publicationLedgerHeadSHA256 := ""
	if config.PublicationLedgerHeadFile != "" {
		if trustPolicy == nil || trustPolicy.Environment() != "production" {
			return nil, errors.New("publication ledger head requires production trust")
		}
		headBytes, err := readContainedRegularFile(baseDirectory, config.PublicationLedgerHeadFile, 16*1024)
		if err != nil {
			return nil, fmt.Errorf("read publication ledger head: %w", err)
		}
		var head runtimePublicationLedgerHead
		if err := decodeStrictJSON(headBytes, &head); err != nil {
			return nil, fmt.Errorf("decode publication ledger head: %w", err)
		}
		decoded, decodeErr := hex.DecodeString(head.ReceiptSHA256)
		if head.SchemaVersion != "1.0" || head.Environment != "production" || decodeErr != nil || len(decoded) != sha256.Size {
			return nil, errors.New("publication ledger head is invalid")
		}
		publicationLedgerHeadSHA256 = head.ReceiptSHA256
	}
	snapshots := make([]devices.SnapshotArtifact, 0, len(config.Snapshots))
	for _, snapshot := range config.Snapshots {
		body, err := readContainedRegularFile(baseDirectory, snapshot.File, 5*1024*1024)
		if err != nil {
			return nil, fmt.Errorf("read snapshot %q: %w", snapshot.SnapshotID, err)
		}
		var receipt *publication.AuditReceipt
		var previousReceipt *publication.AuditReceipt
		if snapshot.ReceiptFile != "" {
			receiptBytes, readErr := readContainedRegularFile(baseDirectory, snapshot.ReceiptFile, 256*1024)
			if readErr != nil {
				return nil, fmt.Errorf("read snapshot %q receipt: %w", snapshot.SnapshotID, readErr)
			}
			receipt = &publication.AuditReceipt{}
			if decodeErr := decodeStrictJSON(receiptBytes, receipt); decodeErr != nil {
				return nil, fmt.Errorf("decode snapshot %q receipt: %w", snapshot.SnapshotID, decodeErr)
			}
		}
		if snapshot.PreviousReceiptFile != "" {
			previousBytes, readErr := readContainedRegularFile(baseDirectory, snapshot.PreviousReceiptFile, 256*1024)
			if readErr != nil {
				return nil, fmt.Errorf("read snapshot %q previous receipt: %w", snapshot.SnapshotID, readErr)
			}
			previousReceipt = &publication.AuditReceipt{}
			if decodeErr := decodeStrictJSON(previousBytes, previousReceipt); decodeErr != nil {
				return nil, fmt.Errorf("decode snapshot %q previous receipt: %w", snapshot.SnapshotID, decodeErr)
			}
		}
		snapshots = append(snapshots, devices.SnapshotArtifact{
			ID: snapshot.SnapshotID, Bytes: body, SHA256: snapshot.SHA256,
			SigningKeyID: snapshot.SigningKeyID, Receipt: receipt, PreviousReceipt: previousReceipt,
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
	var registryBackend devices.AdminRegistryBackend
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
		if config.RegistryBackend == "postgres" {
			registryStore, openErr := registry.OpenPostgresRevisionStore(openContext, databaseURL)
			if openErr != nil {
				repository.Close()
				return nil, openErr
			}
			registryReader, readerErr := registry.NewActiveReader(registryStore)
			if readerErr != nil {
				registryStore.Close()
				repository.Close()
				return nil, readerErr
			}
			registryBackend = registryReader
		} else if config.RegistryBackend != "" {
			repository.Close()
			return nil, errors.New("unsupported registry backend")
		}
	} else if config.PairingBackend != "" {
		return nil, errors.New("unsupported pairing backend")
	} else if config.DatabaseURLEnv != "" || config.PairingRateLimitKeyEnv != "" || config.AdminIdempotencyKeyEnv != "" || len(config.AdminCompatibilityIdempotencyKeyEnvs) > 0 || config.PairingRateLimits != (runtimePairingRateLimits{}) || config.PairingBackendTimeoutSeconds != 0 || config.RegistryBackend != "" {
		return nil, errors.New("PostgreSQL pairing settings require pairing_backend postgres")
	}
	service, err := devices.NewService(devices.ServiceConfig{
		PublicBaseURL:               config.PublicBaseURL,
		PairingFixtures:             pairings,
		PairingBackend:              pairingBackend,
		AdminBackend:                adminBackend,
		RegistryBackend:             registryBackend,
		BackendTimeout:              time.Duration(config.PairingBackendTimeoutSeconds) * time.Second,
		Assignments:                 config.Assignments,
		Snapshots:                   snapshots,
		TrustedPublicKeys:           trustedKeys,
		TrustPolicy:                 trustPolicy,
		PublicationLedgerHeadSHA256: publicationLedgerHeadSHA256,
	})
	if err != nil {
		if closer, ok := registryBackend.(interface{ Close() }); ok {
			closer.Close()
		}
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
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return err
	}
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
