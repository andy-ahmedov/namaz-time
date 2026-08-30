// Command registryctl validates, stages and activates reviewed policy bindings
// against a pinned geographic catalog and independently verified references.
package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const (
	artifactManifestSchema = "namaztime-registry-reference-artifacts/v1"
	maximumCatalogBytes    = 256 * 1024 * 1024
	maximumMetadataBytes   = 8 * 1024 * 1024
	maximumSnapshotBytes   = 5 * 1024 * 1024
)

type artifactReference struct {
	File   string `json:"file"`
	SHA256 string `json:"sha256"`
}

type approvalArtifactDocument struct {
	ApprovalID          string            `json:"approval_id"`
	MosqueID            string            `json:"mosque_id"`
	PrayerPolicy        artifactReference `json:"prayer_policy"`
	Receipt             artifactReference `json:"receipt"`
	TrustBundle         artifactReference `json:"trust_bundle"`
	PreviousTrustBundle artifactReference `json:"previous_trust_bundle,omitempty"`
}

type snapshotArtifactDocument struct {
	SnapshotID          string            `json:"snapshot_id"`
	Snapshot            artifactReference `json:"snapshot"`
	Receipt             artifactReference `json:"receipt"`
	TrustBundle         artifactReference `json:"trust_bundle"`
	PreviousTrustBundle artifactReference `json:"previous_trust_bundle"`
	TestTrustBundle     artifactReference `json:"test_trust_bundle"`
	StagingTrustBundle  artifactReference `json:"staging_trust_bundle"`
}

type artifactManifest struct {
	SchemaVersion string                     `json:"schema_version"`
	Approvals     []approvalArtifactDocument `json:"approvals"`
	Snapshots     []snapshotArtifactDocument `json:"snapshots"`
}

func main() {
	if err := run(os.Args[1:], os.Stdout, os.Stderr); err != nil {
		log.New(os.Stderr, "registryctl: ", 0).Println(err)
		os.Exit(1)
	}
}

func run(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 || (args[0] != "validate" && args[0] != "apply") {
		return errors.New("first argument must be validate or apply")
	}
	action := args[0]
	flags := flag.NewFlagSet("registryctl "+action, flag.ContinueOnError)
	flags.SetOutput(stderr)
	catalogPath := flags.String("catalog", "", "validated T035 city catalog JSON")
	bindingsPath := flags.String("bindings", "", "reviewed prayer-policy bindings JSON")
	artifactsPath := flags.String("artifacts", "", "pinned reference-artifact manifest JSON")
	artifactRoot := flags.String("artifact-root", "", "root containing manifest artifact paths")
	databaseURLEnv := flags.String("database-url-env", "NAMAZTIME_REGISTRY_DATABASE_URL", "environment variable containing PostgreSQL URL")
	actor := flags.String("actor", "", "activation actor ID (apply only)")
	reason := flags.String("reason", "", "activation reason (apply only)")
	if err := flags.Parse(args[1:]); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if flags.NArg() != 0 || *catalogPath == "" || *bindingsPath == "" || *artifactsPath == "" || *artifactRoot == "" {
		return errors.New("-catalog, -bindings, -artifacts and -artifact-root are required; positional arguments are forbidden")
	}
	if action == "apply" && (*actor == "" || *reason == "" || !validEnvironmentName(*databaseURLEnv)) {
		return errors.New("apply requires -actor, -reason and a valid -database-url-env")
	}
	if action == "validate" && (*actor != "" || *reason != "") {
		return errors.New("validate does not accept activation actor/reason")
	}
	record, dataset, verifier, err := loadInputs(*catalogPath, *bindingsPath, *artifactsPath, *artifactRoot)
	if err != nil {
		return err
	}
	if action == "validate" {
		if err := verifyDatasetReferences(context.Background(), dataset, verifier); err != nil {
			return err
		}
		_, _ = fmt.Fprintf(stdout, "validated revision=%s catalog=%s content_sha256=%s\n", record.ID, record.CatalogRevisionID, record.ContentSHA256)
		return nil
	}
	databaseURL, exists := os.LookupEnv(*databaseURLEnv)
	if !exists || strings.TrimSpace(databaseURL) == "" {
		return fmt.Errorf("database URL environment variable %s is not set", *databaseURLEnv)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	store, err := registry.OpenPostgresRevisionStore(ctx, databaseURL)
	if err != nil {
		return err
	}
	defer store.Close()
	service, err := registry.NewPersistentService(registry.PersistentServiceConfig{
		Store: store, ApprovalVerifier: verifier, SnapshotVerifier: verifier, Now: time.Now,
	})
	if err != nil {
		return err
	}
	if err := service.Stage(ctx, record, dataset); err != nil {
		return err
	}
	if err := service.Activate(ctx, record.ID, *actor, *reason); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(stdout, "activated revision=%s catalog=%s content_sha256=%s\n", record.ID, record.CatalogRevisionID, record.ContentSHA256)
	return nil
}

func verifyDatasetReferences(ctx context.Context, dataset registry.Dataset, verifier *registry.ArtifactReferenceVerifier) error {
	approvalIDs := make(map[string]struct{}, len(dataset.Policies)+len(dataset.CalculationProfiles)+len(dataset.SourceOverrides))
	for _, policy := range dataset.Policies {
		approvalIDs[policy.ApprovalID] = struct{}{}
	}
	for _, profile := range dataset.CalculationProfiles {
		approvalIDs[profile.ApprovalID] = struct{}{}
	}
	for _, override := range dataset.SourceOverrides {
		approvalIDs[override.ApprovalID] = struct{}{}
	}
	orderedApprovals := make([]string, 0, len(approvalIDs))
	for approvalID := range approvalIDs {
		orderedApprovals = append(orderedApprovals, approvalID)
	}
	sort.Strings(orderedApprovals)
	for _, approvalID := range orderedApprovals {
		if _, err := verifier.VerifyApproval(ctx, approvalID); err != nil {
			return fmt.Errorf("verify registry approval %q: %w", approvalID, err)
		}
	}
	snapshotIDs := make(map[string]struct{}, len(dataset.TimeTables))
	for _, timetable := range dataset.TimeTables {
		snapshotIDs[timetable.PublishedSnapshotID] = struct{}{}
	}
	orderedSnapshots := make([]string, 0, len(snapshotIDs))
	for snapshotID := range snapshotIDs {
		orderedSnapshots = append(orderedSnapshots, snapshotID)
	}
	sort.Strings(orderedSnapshots)
	for _, snapshotID := range orderedSnapshots {
		if _, err := verifier.VerifySnapshot(ctx, snapshotID); err != nil {
			return fmt.Errorf("verify published snapshot %q: %w", snapshotID, err)
		}
	}
	return nil
}

func loadInputs(catalogPath, bindingsPath, artifactsPath, artifactRoot string) (registry.RevisionRecord, registry.Dataset, *registry.ArtifactReferenceVerifier, error) {
	catalogBytes, err := readRegularFile(catalogPath, maximumCatalogBytes)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, fmt.Errorf("read city catalog: %w", err)
	}
	catalog, err := geography.DecodeCatalog(catalogBytes)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, err
	}
	bindingsBytes, err := readRegularFile(bindingsPath, maximumMetadataBytes)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, fmt.Errorf("read policy bindings: %w", err)
	}
	bindings, err := registry.DecodePolicyBindings(bindingsBytes)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, err
	}
	record, dataset, err := registry.ComposeCatalog(catalog, bindings)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, err
	}
	manifestBytes, err := readRegularFile(artifactsPath, maximumMetadataBytes)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, fmt.Errorf("read reference manifest: %w", err)
	}
	var manifest artifactManifest
	if err := decodeStrictJSON(manifestBytes, &manifest); err != nil || manifest.SchemaVersion != artifactManifestSchema || len(manifest.Approvals) == 0 || len(manifest.Snapshots) == 0 {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, errors.New("reference artifact manifest is invalid")
	}
	root, err := filepath.Abs(artifactRoot)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, fmt.Errorf("resolve artifact root: %w", err)
	}
	if info, statErr := os.Lstat(root); statErr != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, errors.New("artifact root must be a non-symlink directory")
	}
	config := registry.ArtifactReferenceVerifierConfig{Now: time.Now}
	for _, item := range manifest.Approvals {
		prayerPolicy, readErr := readPinnedArtifact(root, item.PrayerPolicy, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		receipt, readErr := readPinnedArtifact(root, item.Receipt, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		trustBundle, readErr := readPinnedArtifact(root, item.TrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		previousTrust, readErr := readOptionalPinnedArtifact(root, item.PreviousTrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		config.Approvals = append(config.Approvals, registry.ApprovalArtifact{
			ApprovalID: item.ApprovalID, MosqueID: item.MosqueID, PrayerPolicy: prayerPolicy,
			Receipt: receipt, TrustBundle: trustBundle, PreviousTrustBundle: previousTrust,
		})
	}
	for _, item := range manifest.Snapshots {
		snapshot, readErr := readPinnedArtifact(root, item.Snapshot, maximumSnapshotBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		receipt, readErr := readPinnedArtifact(root, item.Receipt, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		trustBundle, readErr := readPinnedArtifact(root, item.TrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		previousTrust, readErr := readPinnedArtifact(root, item.PreviousTrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		testTrust, readErr := readPinnedArtifact(root, item.TestTrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		stagingTrust, readErr := readPinnedArtifact(root, item.StagingTrustBundle, maximumMetadataBytes)
		if readErr != nil {
			return registry.RevisionRecord{}, registry.Dataset{}, nil, readErr
		}
		config.Snapshots = append(config.Snapshots, registry.PublishedSnapshotArtifact{
			SnapshotID: item.SnapshotID, Snapshot: snapshot, Receipt: receipt, TrustBundle: trustBundle,
			PreviousTrustBundle: previousTrust, TestTrustBundle: testTrust, StagingTrustBundle: stagingTrust,
		})
	}
	verifier, err := registry.NewArtifactReferenceVerifier(config)
	if err != nil {
		return registry.RevisionRecord{}, registry.Dataset{}, nil, err
	}
	return record, dataset, verifier, nil
}

func readPinnedArtifact(root string, reference artifactReference, maximum int64) ([]byte, error) {
	if reference.File == "" || len(reference.SHA256) != sha256.Size*2 || strings.ToLower(reference.SHA256) != reference.SHA256 {
		return nil, errors.New("reference artifact identity is invalid")
	}
	if _, err := hex.DecodeString(reference.SHA256); err != nil {
		return nil, errors.New("reference artifact SHA-256 is invalid")
	}
	resolved := filepath.Join(root, filepath.Clean(reference.File))
	relative, err := filepath.Rel(root, resolved)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return nil, errors.New("reference artifact path escapes its root")
	}
	realRoot, err := filepath.EvalSymlinks(root)
	if err != nil {
		return nil, errors.New("resolve reference artifact root")
	}
	realResolved, err := filepath.EvalSymlinks(resolved)
	if err != nil {
		return nil, fmt.Errorf("resolve reference artifact %q: %w", reference.File, err)
	}
	realRelative, err := filepath.Rel(realRoot, realResolved)
	if err != nil || realRelative == ".." || strings.HasPrefix(realRelative, ".."+string(filepath.Separator)) {
		return nil, errors.New("reference artifact symlink escapes its root")
	}
	data, err := readRegularFile(resolved, maximum)
	if err != nil {
		return nil, fmt.Errorf("read reference artifact %q: %w", reference.File, err)
	}
	digest := sha256.Sum256(data)
	if hex.EncodeToString(digest[:]) != reference.SHA256 {
		return nil, fmt.Errorf("reference artifact %q SHA-256 mismatch", reference.File)
	}
	return data, nil
}

func readOptionalPinnedArtifact(root string, reference artifactReference, maximum int64) ([]byte, error) {
	if reference == (artifactReference{}) {
		return nil, nil
	}
	return readPinnedArtifact(root, reference, maximum)
}

func readRegularFile(path string, maximum int64) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 || info.Size() <= 0 || info.Size() > maximum {
		return nil, errors.New("path must be a bounded regular non-symlink file")
	}
	return os.ReadFile(path)
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
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		return errors.New("JSON has trailing content")
	}
	return nil
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
