package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/setupbundle"
)

func sha256HexForExportTest(raw []byte) string {
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func TestExportLocalSetupCommandRetainsVerifiedLegacyBytes(t *testing.T) {
	root, err := filepath.Abs(filepath.Join("..", ".."))
	if err != nil {
		t.Fatal(err)
	}
	read := func(path string) []byte {
		raw, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		return raw
	}
	bindings, err := registry.DecodePolicyBindings(read(filepath.Join(root, "fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json")))
	if err != nil {
		t.Fatal(err)
	}
	catalog := registryctlTestCatalog(t)
	catalog.Source.License, catalog.Source.LicenseURL, catalog.Source.Attribution = "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", "Synthetic geographic fixture with retained real pilot identity"
	bindings.RegistryRevision.CatalogRevisionID, bindings.RegistryRevision.CatalogContentSHA256 = catalog.Revision.ID, catalog.Revision.ContentSHA256
	directory := t.TempDir()
	catalogRaw, err := geography.Encode(catalog)
	if err != nil {
		t.Fatal(err)
	}
	bindingsRaw, err := json.Marshal(bindings)
	if err != nil {
		t.Fatal(err)
	}
	catalogPath, bindingsPath := filepath.Join(directory, "catalog.json"), filepath.Join(directory, "bindings.json")
	if err := os.WriteFile(catalogPath, catalogRaw, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(bindingsPath, bindingsRaw, 0o600); err != nil {
		t.Fatal(err)
	}
	artifactsPath := filepath.Join(root, "fixtures/pilot/ulyanovsk-2026/registry-reference-artifacts.json")
	output := filepath.Join(directory, "bundle")
	args := []string{"export-local-setup", "-catalog", catalogPath, "-catalog-sha256", sha256HexForExportTest(catalogRaw), "-bindings", bindingsPath, "-bindings-sha256", sha256HexForExportTest(bindingsRaw), "-artifacts", artifactsPath, "-artifacts-sha256", sha256HexForExportTest(read(artifactsPath)), "-artifact-root", root, "-output", output, "-actor", "operator:export-test", "-reason", "Retain the original signed Ulyanovsk artifact for exact local setup"}
	var stdout, stderr bytes.Buffer
	if err := run(args, &stdout, &stderr); err != nil {
		t.Fatalf("export command: %v; stderr=%s", err, stderr.String())
	}
	if !strings.Contains(stdout.String(), "manifest_sha256=") || !strings.Contains(stdout.String(), "cities=1 aliases=2") {
		t.Fatalf("command output: %s", stdout.String())
	}
	var choices setupbundle.Choices
	if err := json.Unmarshal(read(filepath.Join(output, "choices.json")), &choices); err != nil {
		t.Fatal(err)
	}
	if len(choices.Policies) != 1 || len(choices.Bindings) != 1 || choices.Policies[0].Qualification != nil || choices.Policies[0].Policy.ApprovalID == "" {
		t.Fatalf("legacy branch changed: %+v", choices)
	}
	if got := sha256HexForExportTest(read(filepath.Join(output, choices.Policies[0].Snapshot.Path))); got != "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b" {
		t.Fatalf("retained signed bytes changed: %s", got)
	}
}

func TestExportLocalSetupRequiresPinsAndForbidsTrustClockAndDatabaseOverrides(t *testing.T) {
	for _, extra := range []string{"", "-trust-anchor", "-now", "-database-url-env"} {
		t.Run(extra, func(t *testing.T) {
			args := []string{"export-local-setup"}
			if extra != "" {
				args = append(args, extra, "untrusted")
			}
			var output bytes.Buffer
			err := run(args, &output, &output)
			if err == nil {
				t.Fatal("unbounded or incomplete export flags accepted")
			}
			if strings.Contains(err.Error(), "first argument") {
				t.Fatalf("export command is not dispatched: %v", err)
			}
		})
	}
}
