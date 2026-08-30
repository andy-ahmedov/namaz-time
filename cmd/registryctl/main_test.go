package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

func TestValidateChecksComposedCatalogAndRealPilotEvidence(t *testing.T) {
	repositoryRoot := filepath.Join("..", "..")
	bindingsBytes, err := os.ReadFile(filepath.Join(repositoryRoot, "fixtures", "pilot", "ulyanovsk-2026", "registry-policy-bindings.json"))
	if err != nil {
		t.Fatalf("read bindings: %v", err)
	}
	bindings, err := registry.DecodePolicyBindings(bindingsBytes)
	if err != nil {
		t.Fatalf("DecodePolicyBindings() error = %v", err)
	}
	catalog := registryctlTestCatalog(t)
	bindings.RegistryRevision.CatalogRevisionID = catalog.Revision.ID
	bindings.RegistryRevision.CatalogContentSHA256 = catalog.Revision.ContentSHA256
	directory := t.TempDir()
	catalogBytes, err := geography.Encode(catalog)
	if err != nil {
		t.Fatalf("encode catalog: %v", err)
	}
	encodedBindings, err := json.MarshalIndent(bindings, "", "  ")
	if err != nil {
		t.Fatalf("encode bindings: %v", err)
	}
	catalogPath := filepath.Join(directory, "catalog.json")
	bindingsPath := filepath.Join(directory, "bindings.json")
	if err := os.WriteFile(catalogPath, catalogBytes, 0o600); err != nil {
		t.Fatalf("write catalog: %v", err)
	}
	if err := os.WriteFile(bindingsPath, append(encodedBindings, '\n'), 0o600); err != nil {
		t.Fatalf("write bindings: %v", err)
	}
	var stdout, stderr bytes.Buffer
	err = run([]string{
		"validate", "-catalog", catalogPath, "-bindings", bindingsPath,
		"-artifacts", filepath.Join(repositoryRoot, "fixtures", "pilot", "ulyanovsk-2026", "registry-reference-artifacts.json"),
		"-artifact-root", repositoryRoot,
	}, &stdout, &stderr)
	if err != nil {
		t.Fatalf("run(validate) error = %v, stderr=%s", err, stderr.String())
	}
	if !strings.Contains(stdout.String(), "validated revision=registry-ulyanovsk-pilot-2026-v1") || !strings.Contains(stdout.String(), "content_sha256=") {
		t.Fatalf("validate output = %q", stdout.String())
	}

	bindings.SourceOverrides[0].ApprovalID = "approval-missing-from-artifact-manifest"
	encodedBindings, _ = json.Marshal(bindings)
	if err := os.WriteFile(bindingsPath, encodedBindings, 0o600); err != nil {
		t.Fatalf("write bindings with missing override evidence: %v", err)
	}
	if err := run([]string{
		"validate", "-catalog", catalogPath, "-bindings", bindingsPath,
		"-artifacts", filepath.Join(repositoryRoot, "fixtures", "pilot", "ulyanovsk-2026", "registry-reference-artifacts.json"),
		"-artifact-root", repositoryRoot,
	}, &stdout, &stderr); !errors.Is(err, registry.ErrVerifiedReferenceMissing) {
		t.Fatalf("validate override evidence error = %v, want missing verified reference", err)
	}
	bindings.SourceOverrides[0].ApprovalID = "approval-second-cathedral-mosque-ulyanovsk-2026-002"

	bindings.RegistryRevision.CatalogContentSHA256 = strings.Repeat("0", 64)
	encodedBindings, _ = json.Marshal(bindings)
	if err := os.WriteFile(bindingsPath, encodedBindings, 0o600); err != nil {
		t.Fatalf("write mismatched bindings: %v", err)
	}
	if err := run([]string{
		"validate", "-catalog", catalogPath, "-bindings", bindingsPath,
		"-artifacts", filepath.Join(repositoryRoot, "fixtures", "pilot", "ulyanovsk-2026", "registry-reference-artifacts.json"),
		"-artifact-root", repositoryRoot,
	}, &stdout, &stderr); err == nil {
		t.Fatal("validate accepted policy bindings for another catalog hash")
	}
}

func TestApplyNeverAcceptsLiteralDatabaseURL(t *testing.T) {
	var output bytes.Buffer
	if err := run([]string{"apply", "postgres://literal-secret"}, &output, &output); err == nil {
		t.Fatal("apply accepted a positional database URL")
	}
}

func TestReadPinnedArtifactRejectsSymlinkDirectoryEscape(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	data := []byte("outside artifact")
	outsideFile := filepath.Join(outside, "artifact.json")
	if err := os.WriteFile(outsideFile, data, 0o600); err != nil {
		t.Fatalf("write outside artifact: %v", err)
	}
	if err := os.Symlink(outside, filepath.Join(root, "linked")); err != nil {
		t.Fatalf("create directory symlink: %v", err)
	}
	digest := sha256.Sum256(data)
	if _, err := readPinnedArtifact(root, artifactReference{
		File: "linked/artifact.json", SHA256: hex.EncodeToString(digest[:]),
	}, maximumMetadataBytes); err == nil {
		t.Fatal("readPinnedArtifact() followed a directory symlink outside artifact root")
	}
}

func registryctlTestCatalog(t *testing.T) geography.Catalog {
	t.Helper()
	regions := []domain.Region{{ID: "ru-uly", Name: "Ульяновская область", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}}
	cities := []domain.City{{
		ID: "city-4adcfc15932f3850d5dd5dbaa17e3a4c", Name: "Ульяновск", Aliases: []string{"Ulyanovsk", "Синбирск"},
		CountryCode: "RU", RegionID: "ru-uly", SettlementType: "PPLA", Latitude: 54.32824, Longitude: 48.38657,
		Timezone: "Europe/Ulyanovsk", Population: 626540, GeographicSource: "https://www.geonames.org/479123",
		GeographicSourceID: "geonames:479123", GeographicRevision: "2026-08-29", GeographicLicense: "CC BY 4.0",
	}}
	content, err := json.Marshal(struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{Regions: regions, Cities: cities})
	if err != nil {
		t.Fatalf("marshal catalog: %v", err)
	}
	digest := sha256.Sum256(content)
	return geography.Catalog{
		SchemaVersion: "namaztime-city-catalog/v1",
		Revision:      geography.Revision{ID: "catalog-geonames-ru-registryctl-test", ContentSHA256: hex.EncodeToString(digest[:]), ImportedCities: 1},
		Regions:       regions, Cities: cities,
	}
}
