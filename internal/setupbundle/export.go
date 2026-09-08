package setupbundle

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/canonicaljson"
)

func exportAt(ctx context.Context, config Config, now time.Time, anchors trustAnchors) (Manifest, error) {
	var empty Manifest
	if err := ctx.Err(); err != nil {
		return empty, err
	}
	output, err := outputPath(config.OutputDirectory)
	if err != nil {
		return empty, err
	}
	if now.IsZero() || now.Location() != time.UTC || now.Nanosecond() != 0 {
		return empty, fmt.Errorf("canonical current UTC admission time is required")
	}
	in, err := loadInputs(ctx, config, now, anchors)
	if err != nil {
		return empty, err
	}
	if _, err := admit(ctx, in, config, now); err != nil {
		return empty, err
	}
	choices, files, err := projectChoices(ctx, in)
	if err != nil {
		return empty, err
	}
	choicesRaw, err := json.MarshalIndent(choices, "", "  ")
	if err != nil {
		return empty, err
	}
	choicesRaw = append(choicesRaw, '\n')
	if len(choicesRaw) > MaximumChoicesBytes {
		return empty, fmt.Errorf("complete choices exceed byte bounds")
	}
	files["choices.json"] = choicesRaw
	for path, raw := range in.trustFiles {
		files[path] = raw
	}
	staging, err := os.MkdirTemp(filepath.Dir(output), ".namaztime-local-setup-")
	if err != nil {
		return empty, err
	}
	// Only this exact newly created private directory is ever removed on failure.
	defer os.RemoveAll(staging)
	for _, directory := range []string{"snapshots", "trust"} {
		if err := os.Mkdir(filepath.Join(staging, directory), 0o700); err != nil {
			return empty, err
		}
	}
	indexPath := filepath.Join(staging, "catalog.sqlite")
	info, err := buildIndex(ctx, indexPath, config.SQLiteExecutable, in.catalog)
	if err != nil {
		return empty, err
	}
	paths := make([]string, 0, len(files))
	for path := range files {
		paths = append(paths, path)
	}
	sort.Strings(paths)
	manifest := Manifest{SchemaVersion: ManifestSchema, CreatedAt: now.Format(time.RFC3339), RegistryRevision: in.record, RegistryState: "active", Admission: Admission{Kind: "persistent_service_verified_local", VerifiedAt: now.Format(time.RFC3339), ActorID: config.ActorID, Reason: config.Reason}, Catalog: info, MinimumTrustRevision: anchors.Revision, Files: []File{}}
	var total int64
	for _, path := range paths {
		if !safeMember(path) {
			return empty, fmt.Errorf("invalid generated bundle path")
		}
		raw := files[path]
		if err := writeExclusive(filepath.Join(staging, path), raw); err != nil {
			return empty, err
		}
		manifest.Files = append(manifest.Files, File{Path: path, ByteLength: int64(len(raw)), SHA256: sum(raw)})
		total += int64(len(raw))
	}
	index, err := os.Open(indexPath)
	if err != nil {
		return empty, err
	}
	indexRaw, readErr := readBounded(index, MaximumDatabaseBytes)
	closeErr := index.Close()
	if readErr != nil {
		return empty, readErr
	}
	if closeErr != nil {
		return empty, closeErr
	}
	manifest.Files = append(manifest.Files, File{Path: "catalog.sqlite", ByteLength: int64(len(indexRaw)), SHA256: sum(indexRaw)})
	total += int64(len(indexRaw))
	sort.Slice(manifest.Files, func(i, j int) bool { return manifest.Files[i].Path < manifest.Files[j].Path })
	raw, err := encodeManifest(manifest)
	if err != nil {
		return empty, err
	}
	if err := decodeStrict(raw, &manifest); err != nil {
		return empty, err
	}
	if len(raw) > MaximumManifestBytes || total+int64(len(raw)) > MaximumBundleBytes {
		return empty, fmt.Errorf("complete bundle exceeds byte bounds")
	}
	if err := writeExclusive(filepath.Join(staging, "manifest.json"), raw); err != nil {
		return empty, err
	}
	if err := verifyFiles(staging, manifest); err != nil {
		return empty, err
	}
	for _, file := range manifest.Files {
		if err := syncFile(filepath.Join(staging, file.Path)); err != nil {
			return empty, err
		}
	}
	for _, directory := range []string{filepath.Join(staging, "snapshots"), filepath.Join(staging, "trust"), staging} {
		if err := syncDirectory(directory); err != nil {
			return empty, err
		}
	}
	if err := ctx.Err(); err != nil {
		return empty, err
	}
	if err := renameExclusive(staging, output); err != nil {
		return empty, fmt.Errorf("publish exclusive local bundle: %w", err)
	}
	if err := syncDirectory(filepath.Dir(output)); err != nil {
		return empty, fmt.Errorf("bundle renamed but parent directory sync failed: %w", err)
	}
	return manifest, nil
}

func outputPath(value string) (string, error) {
	if value == "" {
		return "", fmt.Errorf("new outside-Git output directory is required")
	}
	abs, err := filepath.Abs(value)
	if err != nil {
		return "", err
	}
	if filepath.Base(abs) == "." || abs == string(filepath.Separator) {
		return "", fmt.Errorf("invalid output directory")
	}
	if _, err := os.Lstat(abs); err == nil {
		return "", fmt.Errorf("output already exists")
	} else if !os.IsNotExist(err) {
		return "", err
	}
	parent, err := filepath.EvalSymlinks(filepath.Dir(abs))
	if err != nil {
		return "", fmt.Errorf("output parent must already exist: %w", err)
	}
	for directory := parent; ; directory = filepath.Dir(directory) {
		if _, err := os.Lstat(filepath.Join(directory, ".git")); err == nil {
			return "", fmt.Errorf("raw local setup bundles must remain outside Git worktrees")
		}
		if filepath.Dir(directory) == directory {
			break
		}
	}
	return filepath.Join(parent, filepath.Base(abs)), nil
}

func encodeManifest(m Manifest) ([]byte, error) {
	m.BundleID, m.ManifestSHA256 = "", ""
	raw, err := json.Marshal(m)
	if err != nil {
		return nil, err
	}
	canonical, err := canonicaljson.WithoutRootMembers(raw, "bundle_id", "manifest_sha256")
	if err != nil {
		return nil, err
	}
	m.ManifestSHA256 = sum(canonical)
	m.BundleID = "local-setup-" + m.ManifestSHA256[:32]
	raw, err = json.MarshalIndent(m, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(raw, '\n'), nil
}

func writeExclusive(path string, raw []byte) error {
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		return err
	}
	_, writeErr := file.Write(raw)
	syncErr := file.Sync()
	closeErr := file.Close()
	if writeErr != nil {
		return writeErr
	}
	if syncErr != nil {
		return syncErr
	}
	return closeErr
}
func syncFile(path string) error {
	file, err := os.OpenFile(path, os.O_RDWR, 0)
	if err != nil {
		return err
	}
	syncErr := file.Sync()
	closeErr := file.Close()
	if syncErr != nil {
		return syncErr
	}
	return closeErr
}
func syncDirectory(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	syncErr := file.Sync()
	closeErr := file.Close()
	if syncErr != nil {
		return syncErr
	}
	return closeErr
}

func verifyFiles(root string, m Manifest) error {
	expected := map[string]File{}
	if len(m.Files) < 7 || len(m.Files) > MaximumSnapshots+6 {
		return fmt.Errorf("bundle inventory count is invalid")
	}
	var total int64
	for _, file := range m.Files {
		if !safeMember(file.Path) || !validSHA(file.SHA256) || file.ByteLength < 1 {
			return fmt.Errorf("invalid bundle inventory member")
		}
		if _, exists := expected[file.Path]; exists {
			return fmt.Errorf("duplicate bundle inventory member")
		}
		maximum := int64(0)
		switch file.Path {
		case "catalog.sqlite":
			maximum = MaximumDatabaseBytes
		case "choices.json":
			maximum = MaximumChoicesBytes
		case "trust/production.json", "trust/previous-production.json", "trust/test.json", "trust/staging.json":
			maximum = MaximumTrustBytes
		default:
			base := strings.TrimPrefix(file.Path, "snapshots/")
			hash := strings.TrimSuffix(base, ".json")
			if base == file.Path || base == hash || !validSHA(hash) || hash != file.SHA256 {
				return fmt.Errorf("unknown bundle inventory kind or snapshot content path")
			}
			maximum = MaximumSnapshotBytes
		}
		if file.ByteLength > maximum {
			return fmt.Errorf("bundle member exceeds type byte bounds")
		}
		total += file.ByteLength
		expected[file.Path] = file
	}
	for _, required := range []string{"catalog.sqlite", "choices.json", "trust/production.json", "trust/previous-production.json", "trust/test.json", "trust/staging.json"} {
		if _, exists := expected[required]; !exists {
			return fmt.Errorf("bundle lacks mandatory inventory member")
		}
	}
	if total > MaximumBundleBytes {
		return fmt.Errorf("bundle exceeds total byte bounds")
	}
	seen := map[string]bool{}
	err := filepath.WalkDir(root, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if path == root {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		if entry.Type()&os.ModeSymlink != 0 {
			return fmt.Errorf("bundle contains symlink")
		}
		if entry.IsDir() {
			if rel != "snapshots" && rel != "trust" {
				return fmt.Errorf("unknown bundle subdirectory")
			}
			return nil
		}
		if rel == "manifest.json" {
			return nil
		}
		file, exists := expected[rel]
		if !exists {
			return fmt.Errorf("undeclared bundle member %q", rel)
		}
		opened, err := os.Open(path)
		if err != nil {
			return err
		}
		info, statErr := opened.Stat()
		if statErr != nil {
			_ = opened.Close()
			return statErr
		}
		if !info.Mode().IsRegular() || info.Size() != file.ByteLength {
			_ = opened.Close()
			return fmt.Errorf("bundle file type/size differs")
		}
		hasher := sha256.New()
		_, copyErr := io.Copy(hasher, io.LimitReader(opened, file.ByteLength+1))
		closeErr := opened.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeErr != nil {
			return closeErr
		}
		if hex.EncodeToString(hasher.Sum(nil)) != file.SHA256 {
			return fmt.Errorf("bundle member hash differs")
		}
		seen[rel] = true
		return nil
	})
	if err != nil {
		return err
	}
	if len(seen) != len(expected) {
		return fmt.Errorf("bundle inventory member is missing")
	}
	return nil
}
