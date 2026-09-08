package setupbundle

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

func TestExportRejectsRePinnedInvalidProofAndReferenceInputs(t *testing.T) {
	cases := []struct {
		name   string
		change func(*testing.T, *syntheticExport)
	}{
		{"missing qualification", func(t *testing.T, f *syntheticExport) { f.bindings.Qualifications = nil; repinBindings(t, f) }},
		{"unavailable qualified source", func(t *testing.T, f *syntheticExport) {
			f.bindings.Sources[0].Status = domain.PrayerSourceUnavailable
			repinBindings(t, f)
		}},
		{"stale qualified source", func(t *testing.T, f *syntheticExport) {
			f.bindings.Sources[0].Status = domain.PrayerSourceStale
			repinBindings(t, f)
		}},
		{"qualified status alone", func(t *testing.T, f *syntheticExport) {
			f.bindings.Qualifications[0].Evidence = nil
			repinBindings(t, f)
		}},
		{"signed snapshot tampered", func(t *testing.T, f *syntheticExport) {
			ref := firstReference(f)
			snapshot := ref["snapshot"].(map[string]string)
			path := filepath.Join(f.config.ArtifactRoot, snapshot["file"])
			raw, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			raw = bytes.Replace(raw, []byte("04:00"), []byte("04:01"), 1)
			pin := writeTestPin(t, path, raw)
			snapshot["sha256"] = pin.SHA256
			repinReferences(t, f)
		}},
		{"publication receipt tampered", func(t *testing.T, f *syntheticExport) {
			ref := firstReference(f)
			receipt := ref["receipt"].(map[string]string)
			pin := writeTestPin(t, filepath.Join(f.config.ArtifactRoot, receipt["file"]), []byte(`{"schema_version":"1.0"}`))
			receipt["sha256"] = pin.SHA256
			repinReferences(t, f)
		}},
		{"artifact traversal", func(t *testing.T, f *syntheticExport) {
			firstReference(f)["snapshot"].(map[string]string)["file"] = "../outside.json"
			repinReferences(t, f)
		}},
		{"absolute artifact", func(t *testing.T, f *syntheticExport) {
			firstReference(f)["snapshot"].(map[string]string)["file"] = "/outside.json"
			repinReferences(t, f)
		}},
		{"artifact symlink", func(t *testing.T, f *syntheticExport) {
			ref := firstReference(f)["snapshot"].(map[string]string)
			if err := os.Symlink(ref["file"], filepath.Join(f.config.ArtifactRoot, "linked.json")); err != nil {
				t.Fatal(err)
			}
			ref["file"] = "linked.json"
			repinReferences(t, f)
		}},
		{"missing snapshot", func(t *testing.T, f *syntheticExport) {
			f.references["snapshots"] = f.references["snapshots"].([]any)[1:]
			repinReferences(t, f)
		}},
		{"duplicate snapshot ID", func(t *testing.T, f *syntheticExport) {
			refs := f.references["snapshots"].([]any)
			f.references["snapshots"] = append(refs, refs[0])
			repinReferences(t, f)
		}},
		{"unused snapshot", func(t *testing.T, f *syntheticExport) {
			extra := map[string]any{}
			for k, v := range firstReference(f) {
				extra[k] = v
			}
			extra["snapshot_id"] = "unused-snapshot"
			f.references["snapshots"] = append(f.references["snapshots"].([]any), extra)
			repinReferences(t, f)
		}},
		{"unknown manifest field", func(t *testing.T, f *syntheticExport) { f.references["unexpected"] = true; repinReferences(t, f) }},
		{"duplicate manifest field", func(t *testing.T, f *syntheticExport) {
			raw := testJSON(t, f.references)
			raw = append([]byte(`{"schema_version":"duplicate",`), raw[1:]...)
			f.config.Artifacts = writeTestPin(t, f.config.Artifacts.Path, raw)
		}},
		{"null approvals inventory", func(t *testing.T, f *syntheticExport) { f.references["approvals"] = nil; repinReferences(t, f) }},
		{"unmaterialized calculation", func(t *testing.T, f *syntheticExport) {
			f.bindings.Policies[0].Kind = domain.PrayerPolicyCalculationProfile
			repinBindings(t, f)
		}},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			f := newSyntheticExport(t)
			test.change(t, &f)
			if _, err := exportAt(t.Context(), f.config, f.now, f.anchors); err == nil {
				t.Fatal("invalid re-pinned input bypassed real admission")
			}
			assertNoOutput(t, f.config.OutputDirectory)
		})
	}
}

func TestExportRechecksSignedDisplayAgainstChangedCanonicalCatalog(t *testing.T) {
	f := newSyntheticExport(t)
	f.catalog.Cities[0].Name = "Rebound canonical city name"
	content := struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{f.catalog.Regions, f.catalog.Cities}
	f.catalog.Revision.ContentSHA256 = testSHA(testJSON(t, content))
	f.config.Catalog = writeTestPin(t, f.config.Catalog.Path, testJSON(t, f.catalog))
	f.bindings.RegistryRevision.CatalogContentSHA256 = f.catalog.Revision.ContentSHA256
	repinBindings(t, &f)
	_, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if !errors.Is(err, registry.ErrVerifiedReferenceMismatch) {
		t.Fatalf("changed canonical name did not reach/fail actual snapshot admission: %v", err)
	}
	assertNoOutput(t, f.config.OutputDirectory)
}

func TestExportUsesQualificationLocalDateAndCleansFailedSQLiteWork(t *testing.T) {
	f := newSyntheticExport(t)
	// UTC is still September 9; Europe/Moscow has rolled to September 10 and
	// the one-day city qualification is expired. No historical clock CLI exists.
	late := time.Date(2026, 9, 9, 21, 0, 0, 0, time.UTC)
	if _, err := exportAt(t.Context(), f.config, late, f.anchors); !errors.Is(err, registry.ErrRevisionNotExecutable) {
		t.Fatalf("local-date expiry: %v", err)
	}
	f.config.SQLiteExecutable = filepath.Join(f.config.ArtifactRoot, "missing-sqlite3")
	if _, err := exportAt(t.Context(), f.config, f.now, f.anchors); err == nil {
		t.Fatal("missing SQLite command was ignored")
	}
	assertNoOutput(t, f.config.OutputDirectory)
	leftovers, err := filepath.Glob(filepath.Join(f.config.ArtifactRoot, ".namaztime-local-setup-*"))
	if err != nil || len(leftovers) != 0 {
		t.Fatalf("private failed staging left behind: %v %v", leftovers, err)
	}
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	if _, err := exportAt(ctx, f.config, f.now, f.anchors); !errors.Is(err, context.Canceled) {
		t.Fatalf("cancellation lost: %v", err)
	}
}

func TestConcurrentExportsNeverReplaceDestination(t *testing.T) {
	f := newSyntheticExport(t)
	var group sync.WaitGroup
	results := make(chan error, 2)
	for range 2 {
		group.Add(1)
		go func() {
			defer group.Done()
			_, err := exportAt(t.Context(), f.config, f.now, f.anchors)
			results <- err
		}()
	}
	group.Wait()
	close(results)
	successes := 0
	for err := range results {
		if err == nil {
			successes++
		}
	}
	if successes != 1 {
		t.Fatalf("exclusive export successes=%d", successes)
	}
	var manifest Manifest
	readTestJSON(t, filepath.Join(f.config.OutputDirectory, "manifest.json"), &manifest)
	if err := verifyFiles(f.config.OutputDirectory, manifest); err != nil {
		t.Fatalf("winner was damaged: %v", err)
	}
}

func TestRenameExclusiveRefusesEvenAnExistingEmptyDirectory(t *testing.T) {
	parent := t.TempDir()
	old, target := filepath.Join(parent, "private"), filepath.Join(parent, "existing")
	for _, p := range []string{old, target} {
		if err := os.Mkdir(p, 0o700); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(filepath.Join(old, "marker"), []byte("owned staging"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := renameExclusive(old, target); err == nil {
		t.Fatal("empty destination directory was replaced")
	}
	if _, err := os.Stat(filepath.Join(old, "marker")); err != nil {
		t.Fatal("source changed after no-replace refusal")
	}
	entries, err := os.ReadDir(target)
	if err != nil || len(entries) != 0 {
		t.Fatal("existing target changed")
	}
}

func TestOutputRejectsGitAndExistingSymlinkWithoutMutation(t *testing.T) {
	parent := t.TempDir()
	if err := os.Mkdir(filepath.Join(parent, ".git"), 0o700); err != nil {
		t.Fatal(err)
	}
	if _, err := outputPath(filepath.Join(parent, "bundle")); err == nil {
		t.Fatal("generated source bundle entered Git worktree")
	}
	elsewhere := t.TempDir()
	link := filepath.Join(elsewhere, "linked")
	if err := os.Symlink(parent, link); err != nil {
		t.Fatal(err)
	}
	if _, err := outputPath(link); err == nil {
		t.Fatal("existing symlink target accepted")
	}
	if info, err := os.Lstat(link); err != nil || info.Mode()&os.ModeSymlink == 0 {
		t.Fatal("existing symlink was mutated")
	}
}

func TestClosedInventoryRejectsEvenDeclaredUnknownTrustMember(t *testing.T) {
	f := newSyntheticExport(t)
	m, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if err != nil {
		t.Fatal(err)
	}
	raw := []byte("untrusted extra root")
	path := "trust/unknown.json"
	if err := os.WriteFile(filepath.Join(f.config.OutputDirectory, path), raw, 0o600); err != nil {
		t.Fatal(err)
	}
	m.Files = append(m.Files, File{Path: path, SHA256: testSHA(raw), ByteLength: int64(len(raw))})
	if err := verifyFiles(f.config.OutputDirectory, m); err == nil {
		t.Fatal("closed inventory accepted a declared unknown trust member")
	}
}

func TestRetainedUlyBindingRejectsNearMatchIdentityAddressAndHash(t *testing.T) {
	root := filepath.Join("..", "..")
	raw, err := os.ReadFile(filepath.Join(root, "apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json"))
	if err != nil {
		t.Fatal(err)
	}
	snapshot, err := domain.DecodeSnapshot(raw)
	if err != nil {
		t.Fatal(err)
	}
	city := domain.City{ID: "city-4adcfc15932f3850d5dd5dbaa17e3a4c", Name: "Ульяновск", CountryCode: "RU", RegionID: "ru-uly", GeographicSourceID: "geonames:479123", Timezone: "Europe/Ulyanovsk"}
	region := domain.Region{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}
	if !legacyCityBinding(city, region, snapshot, raw) {
		t.Fatal("exact retained pilot binding rejected")
	}
	for _, name := range []string{"city ID", "geonames ID", "region ID", "mosque ID", "address", "raw hash", "name-only"} {
		t.Run(name, func(t *testing.T) {
			c, r, s, data := city, region, snapshot, append([]byte(nil), raw...)
			switch name {
			case "city ID":
				c.ID += "-near"
			case "geonames ID":
				c.GeographicSourceID = "geonames:479124"
			case "region ID":
				r.ID += "-near"
			case "mosque ID":
				s.Mosque.ID += "-near"
			case "address":
				s.Mosque.Locality += " корпус 2"
			case "raw hash":
				data = append(data, ' ')
			case "name-only":
				s.Mosque.Locality = c.Name
				data = []byte("another unknown legacy artifact")
			}
			if legacyCityBinding(c, r, s, data) {
				t.Fatal("near match entered retained legacy binding")
			}
		})
	}
}

func firstReference(f *syntheticExport) map[string]any {
	return f.references["snapshots"].([]any)[0].(map[string]any)
}
func repinReferences(t *testing.T, f *syntheticExport) {
	t.Helper()
	f.config.Artifacts = writeTestPin(t, f.config.Artifacts.Path, testJSON(t, f.references))
}
func repinBindings(t *testing.T, f *syntheticExport) {
	t.Helper()
	f.config.Bindings = writeTestPin(t, f.config.Bindings.Path, testJSON(t, f.bindings))
}

func TestStrictArtifactManifestRejectsCaseAliasedUnknownFields(t *testing.T) {
	f := newSyntheticExport(t)
	raw := testJSON(t, f.references)
	raw = []byte(strings.Replace(string(raw), `"schema_version"`, `"SCHEMA_VERSION"`, 1))
	f.config.Artifacts = writeTestPin(t, f.config.Artifacts.Path, raw)
	if _, err := exportAt(t.Context(), f.config, f.now, f.anchors); err == nil {
		t.Fatal("case-aliased unknown manifest field accepted")
	}
}

func TestManifestHashCoversIdentityAndCompleteInventory(t *testing.T) {
	f := newSyntheticExport(t)
	m, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := encodeManifest(m)
	if err != nil {
		t.Fatal(err)
	}
	var decoded Manifest
	if err := json.Unmarshal(encoded, &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded.ManifestSHA256 != m.ManifestSHA256 || decoded.BundleID != m.BundleID {
		t.Fatal("manifest hash is not stable")
	}
	m.Files[0].SHA256 = strings.Repeat("0", 64)
	changed, err := encodeManifest(m)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Equal(changed, encoded) {
		t.Fatal("inventory mutation was not hash bound")
	}
}
