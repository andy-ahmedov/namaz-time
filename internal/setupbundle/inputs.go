package setupbundle

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

type artifactReference struct {
	File   string `json:"file"`
	SHA256 string `json:"sha256"`
}

type approvalReference struct {
	ApprovalID          string            `json:"approval_id"`
	MosqueID            string            `json:"mosque_id"`
	PrayerPolicy        artifactReference `json:"prayer_policy"`
	Receipt             artifactReference `json:"receipt"`
	TrustBundle         artifactReference `json:"trust_bundle"`
	PreviousTrustBundle artifactReference `json:"previous_trust_bundle,omitempty"`
}

type snapshotReference struct {
	SnapshotID          string            `json:"snapshot_id"`
	Snapshot            artifactReference `json:"snapshot"`
	Receipt             artifactReference `json:"receipt"`
	TrustBundle         artifactReference `json:"trust_bundle"`
	PreviousTrustBundle artifactReference `json:"previous_trust_bundle"`
	TestTrustBundle     artifactReference `json:"test_trust_bundle"`
	StagingTrustBundle  artifactReference `json:"staging_trust_bundle"`
}

type artifactManifest struct {
	SchemaVersion string              `json:"schema_version"`
	Approvals     []approvalReference `json:"approvals"`
	Snapshots     []snapshotReference `json:"snapshots"`
}

type inputs struct {
	catalog    geography.Catalog
	record     registry.RevisionRecord
	dataset    registry.Dataset
	artifacts  registry.ArtifactReferenceVerifierConfig
	trustFiles map[string][]byte
}

func loadInputs(ctx context.Context, config Config, now time.Time, anchors trustAnchors) (inputs, error) {
	var result inputs
	if err := ctx.Err(); err != nil {
		return result, err
	}
	raw, err := readPinnedInput(config.Catalog, maximumInputBytes)
	if err != nil {
		return result, fmt.Errorf("read catalog: %w", err)
	}
	result.catalog, err = geography.DecodeCatalog(raw)
	if err != nil {
		return result, err
	}
	if len(result.catalog.Cities) == 0 || len(result.catalog.Cities) > 1_000_000 || len(result.catalog.Regions) == 0 || len(result.catalog.Regions) > 1024 ||
		result.catalog.Source.License != "CC BY 4.0" || result.catalog.Source.LicenseURL != "https://creativecommons.org/licenses/by/4.0/" || strings.TrimSpace(result.catalog.Source.Attribution) == "" {
		return result, fmt.Errorf("catalog must retain its complete supported licensed geography")
	}
	raw, err = readPinnedInput(config.Bindings, maximumMetadataBytes)
	if err != nil {
		return result, fmt.Errorf("read policy bindings: %w", err)
	}
	bindings, err := registry.DecodePolicyBindings(raw)
	if err != nil {
		return result, err
	}
	if len(bindings.Policies) == 0 || len(bindings.Policies) > MaximumSnapshots {
		return result, fmt.Errorf("local setup policy count is unsupported")
	}
	result.record, result.dataset, err = registry.ComposeCatalog(result.catalog, bindings)
	if err != nil {
		return result, err
	}
	raw, err = readPinnedInput(config.Artifacts, maximumMetadataBytes)
	if err != nil {
		return result, fmt.Errorf("read artifact manifest: %w", err)
	}
	var manifest artifactManifest
	if err = decodeStrict(raw, &manifest); err != nil {
		return result, fmt.Errorf("decode artifact manifest: %w", err)
	}
	if err = exactArtifactFields(raw); err != nil {
		return result, err
	}
	if manifest.SchemaVersion != "namaztime-registry-reference-artifacts/v1" || manifest.Approvals == nil || len(manifest.Approvals) > MaximumSnapshots || len(manifest.Snapshots) == 0 || len(manifest.Snapshots) > MaximumSnapshots {
		return result, fmt.Errorf("artifact manifest schema or inventory size is invalid")
	}
	root, err := os.OpenRoot(config.ArtifactRoot)
	if err != nil {
		return result, fmt.Errorf("open artifact root: %w", err)
	}
	defer root.Close()
	cache := map[artifactReference][]byte{}
	var total int64
	read := func(ref artifactReference, maximum int64) ([]byte, error) {
		if !safeMember(ref.File) || !validSHA(ref.SHA256) {
			return nil, fmt.Errorf("invalid relative artifact path or SHA-256")
		}
		if cached, ok := cache[ref]; ok {
			if int64(len(cached)) > maximum {
				return nil, fmt.Errorf("artifact exceeds field limit")
			}
			return cached, nil
		}
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		// OpenRoot prevents race-prone symlink escape; reject symlink members too.
		parts := strings.Split(ref.File, "/")
		for i := range parts {
			info, err := root.Lstat(strings.Join(parts[:i+1], "/"))
			if err != nil {
				return nil, err
			}
			if info.Mode()&os.ModeSymlink != 0 {
				return nil, fmt.Errorf("artifact symlink is forbidden")
			}
			if i == len(parts)-1 && !info.Mode().IsRegular() {
				return nil, fmt.Errorf("artifact must be a regular file")
			}
		}
		file, err := openRootForRead(root, ref.File)
		if err != nil {
			return nil, err
		}
		data, err := readBounded(file, maximum)
		closeErr := file.Close()
		if err != nil {
			return nil, err
		}
		if closeErr != nil {
			return nil, closeErr
		}
		if sum(data) != ref.SHA256 {
			return nil, fmt.Errorf("artifact byte hash mismatch")
		}
		total += int64(len(data))
		if total > MaximumBundleBytes {
			return nil, fmt.Errorf("reference artifacts exceed total byte limit")
		}
		cache[ref] = data
		return data, nil
	}
	result.artifacts.Now = func() time.Time { return now }
	for _, ref := range manifest.Approvals {
		a := registry.ApprovalArtifact{ApprovalID: ref.ApprovalID, MosqueID: ref.MosqueID}
		for _, field := range []struct {
			ref    artifactReference
			target *[]byte
		}{{ref.PrayerPolicy, &a.PrayerPolicy}, {ref.Receipt, &a.Receipt}, {ref.TrustBundle, &a.TrustBundle}} {
			*field.target, err = read(field.ref, maximumMetadataBytes)
			if err != nil {
				return result, fmt.Errorf("read approval %q: %w", ref.ApprovalID, err)
			}
		}
		if ref.PreviousTrustBundle != (artifactReference{}) {
			a.PreviousTrustBundle, err = read(ref.PreviousTrustBundle, maximumMetadataBytes)
			if err != nil {
				return result, err
			}
		}
		result.artifacts.Approvals = append(result.artifacts.Approvals, a)
	}
	result.trustFiles = map[string][]byte{}
	for _, ref := range manifest.Snapshots {
		a := registry.PublishedSnapshotArtifact{SnapshotID: ref.SnapshotID}
		a.Snapshot, err = read(ref.Snapshot, MaximumSnapshotBytes)
		if err != nil {
			return result, fmt.Errorf("read snapshot %q: %w", ref.SnapshotID, err)
		}
		a.Receipt, err = read(ref.Receipt, maximumMetadataBytes)
		if err != nil {
			return result, fmt.Errorf("read snapshot receipt %q: %w", ref.SnapshotID, err)
		}
		for _, field := range []struct {
			path, anchor string
			ref          artifactReference
			target       *[]byte
		}{
			{"trust/production.json", anchors.Production, ref.TrustBundle, &a.TrustBundle},
			{"trust/previous-production.json", anchors.Previous, ref.PreviousTrustBundle, &a.PreviousTrustBundle},
			{"trust/test.json", anchors.Test, ref.TestTrustBundle, &a.TestTrustBundle},
			{"trust/staging.json", anchors.Staging, ref.StagingTrustBundle, &a.StagingTrustBundle},
		} {
			if field.ref.SHA256 != field.anchor || !validSHA(field.anchor) {
				return result, fmt.Errorf("snapshot %q differs from fixed %s trust anchor", ref.SnapshotID, field.path)
			}
			*field.target, err = read(field.ref, MaximumTrustBytes)
			if err != nil {
				return result, fmt.Errorf("read %s: %w", field.path, err)
			}
			result.trustFiles[field.path] = *field.target
		}
		result.artifacts.Snapshots = append(result.artifacts.Snapshots, a)
	}
	policy, err := trust.Decode(result.trustFiles["trust/production.json"])
	if err != nil || policy.Revision() != anchors.Revision || policy.Environment() != "production" {
		return result, fmt.Errorf("production trust revision or environment differs from fixed anchor")
	}
	return result, nil
}

func readPinnedInput(pin PinnedInput, maximum int64) ([]byte, error) {
	if pin.Path == "" || !validSHA(pin.SHA256) {
		return nil, fmt.Errorf("path and lowercase SHA-256 pin are required")
	}
	before, err := os.Lstat(pin.Path)
	if err != nil {
		return nil, err
	}
	if !before.Mode().IsRegular() {
		return nil, fmt.Errorf("pinned input is not a regular non-symlink file")
	}
	file, err := openForRead(pin.Path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	after, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if !os.SameFile(before, after) {
		return nil, fmt.Errorf("pinned input changed during open")
	}
	raw, err := readBounded(file, maximum)
	if err != nil {
		return nil, err
	}
	if sum(raw) != pin.SHA256 {
		return nil, fmt.Errorf("pinned input byte hash mismatch")
	}
	return raw, nil
}

func readBounded(file *os.File, maximum int64) ([]byte, error) {
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Size() < 1 || info.Size() > maximum {
		return nil, fmt.Errorf("file type or byte size is invalid")
	}
	raw, err := io.ReadAll(io.LimitReader(file, maximum+1))
	if err != nil {
		return nil, err
	}
	if int64(len(raw)) != info.Size() || int64(len(raw)) > maximum {
		return nil, fmt.Errorf("file size changed while reading")
	}
	return raw, nil
}

func decodeStrict(raw []byte, target any) error {
	if !utf8.Valid(raw) {
		return fmt.Errorf("invalid UTF-8")
	}
	if err := strictjson.RejectDuplicateObjectMembers(raw); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return fmt.Errorf("unexpected trailing JSON")
	}
	return nil
}

func safeMember(path string) bool {
	return path != "" && len(path) <= 1024 && filepath.IsLocal(path) && filepath.ToSlash(filepath.Clean(path)) == path && !strings.ContainsAny(path, "\\\x00") && path != "." && !strings.HasPrefix(path, "/")
}
func validSHA(s string) bool {
	if len(s) != 64 {
		return false
	}
	for _, r := range s {
		if !((r >= '0' && r <= '9') || (r >= 'a' && r <= 'f')) {
			return false
		}
	}
	return true
}
func sum(raw []byte) string { digest := sha256.Sum256(raw); return hex.EncodeToString(digest[:]) }

// encoding/json accepts case-insensitive struct field aliases. The disk
// contract does not: inventory names are exact and closed at every level.
func exactArtifactFields(raw []byte) error {
	object := func(raw []byte, required, optional []string) (map[string]json.RawMessage, error) {
		var value map[string]json.RawMessage
		if err := json.Unmarshal(raw, &value); err != nil || value == nil {
			return nil, fmt.Errorf("artifact inventory member must be an object")
		}
		allowed := map[string]bool{}
		for _, key := range required {
			allowed[key] = true
			if _, exists := value[key]; !exists {
				return nil, fmt.Errorf("artifact inventory missing exact field %q", key)
			}
		}
		for _, key := range optional {
			allowed[key] = true
		}
		for key := range value {
			if !allowed[key] {
				return nil, fmt.Errorf("unknown artifact inventory field %q", key)
			}
		}
		return value, nil
	}
	root, err := object(raw, []string{"schema_version", "approvals", "snapshots"}, nil)
	if err != nil {
		return err
	}
	for _, kind := range []string{"approvals", "snapshots"} {
		var rows []json.RawMessage
		if err := json.Unmarshal(root[kind], &rows); err != nil {
			return err
		}
		for _, row := range rows {
			var required, optional, references []string
			if kind == "approvals" {
				required = []string{"approval_id", "mosque_id", "prayer_policy", "receipt", "trust_bundle"}
				optional = []string{"previous_trust_bundle"}
				references = []string{"prayer_policy", "receipt", "trust_bundle", "previous_trust_bundle"}
			} else {
				required = []string{"snapshot_id", "snapshot", "receipt", "trust_bundle", "previous_trust_bundle", "test_trust_bundle", "staging_trust_bundle"}
				references = required[1:]
			}
			fields, err := object(row, required, optional)
			if err != nil {
				return err
			}
			for _, key := range references {
				if ref, exists := fields[key]; exists {
					if _, err := object(ref, []string{"file", "sha256"}, nil); err != nil {
						return err
					}
				}
			}
		}
	}
	return nil
}
