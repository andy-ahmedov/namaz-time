package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"

	"github.com/andy-ahmedov/namaz-time/internal/setupbundle"
)

func runExportLocalSetup(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("registryctl export-local-setup", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var config setupbundle.Config
	flags.StringVar(&config.Catalog.Path, "catalog", "", "complete canonical city catalog JSON")
	flags.StringVar(&config.Catalog.SHA256, "catalog-sha256", "", "required SHA-256 of catalog file bytes")
	flags.StringVar(&config.Bindings.Path, "bindings", "", "registry policy bindings JSON")
	flags.StringVar(&config.Bindings.SHA256, "bindings-sha256", "", "required SHA-256 of policy bindings bytes")
	flags.StringVar(&config.Artifacts.Path, "artifacts", "", "reference-artifact manifest JSON")
	flags.StringVar(&config.Artifacts.SHA256, "artifacts-sha256", "", "required SHA-256 of reference manifest bytes")
	flags.StringVar(&config.ArtifactRoot, "artifact-root", "", "root containing pinned relative artifact paths")
	flags.StringVar(&config.OutputDirectory, "output", "", "new exclusive output directory outside Git")
	flags.StringVar(&config.ActorID, "actor", "", "local admission audit actor, not an external approver")
	flags.StringVar(&config.Reason, "reason", "", "local admission audit reason")
	flags.StringVar(&config.SQLiteExecutable, "sqlite3", "", "optional path to the existing sqlite3 CLI")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse export flags: %w", err)
	}
	if flags.NArg() != 0 || config.Catalog.Path == "" || config.Catalog.SHA256 == "" || config.Bindings.Path == "" || config.Bindings.SHA256 == "" || config.Artifacts.Path == "" || config.Artifacts.SHA256 == "" || config.ArtifactRoot == "" || config.OutputDirectory == "" || config.ActorID == "" || config.Reason == "" {
		return errors.New("export requires -catalog/-catalog-sha256, -bindings/-bindings-sha256, -artifacts/-artifacts-sha256, -artifact-root, -output, -actor and -reason; positional arguments are forbidden")
	}
	manifest, err := setupbundle.Export(context.Background(), config)
	if err != nil {
		return err
	}
	_, err = fmt.Fprintf(stdout, "exported bundle_id=%s manifest_sha256=%s revision=%s catalog=%s cities=%d aliases=%d output=%s\n", manifest.BundleID, manifest.ManifestSHA256, manifest.RegistryRevision.ID, manifest.Catalog.RevisionID, manifest.Catalog.CityCount, manifest.Catalog.AliasCount, config.OutputDirectory)
	return err
}
