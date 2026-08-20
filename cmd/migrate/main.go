// Command migrate applies or rolls back the PostgreSQL fleet schema from a
// short-lived, deployment-controlled process.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/devices"
)

func main() {
	if err := run(os.Args[1:], os.Stderr); err != nil {
		log.New(os.Stderr, "migrate: ", 0).Println(err)
		os.Exit(1)
	}
}

func run(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("migrate", flag.ContinueOnError)
	flags.SetOutput(stderr)
	databaseURLEnv := flags.String("database-url-env", "", "environment variable containing the privileged migration DSN")
	targetVersion := flags.Int("target-version", devices.PairingSchemaVersion, "explicit schema version to reach")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if flags.NArg() != 0 {
		return errors.New("positional arguments are not accepted")
	}
	if !validEnvironmentName(*databaseURLEnv) {
		return errors.New("-database-url-env must name an uppercase environment variable")
	}
	if *targetVersion < 1 || *targetVersion > devices.PairingSchemaVersion {
		return fmt.Errorf("target version %d is unknown", *targetVersion)
	}
	databaseURL, exists := os.LookupEnv(*databaseURLEnv)
	if !exists || strings.TrimSpace(databaseURL) == "" {
		return errors.New("migration database environment variable is not set")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	repository, err := devices.OpenPostgresPairingRepository(ctx, databaseURL)
	if err != nil {
		return err
	}
	defer repository.Close()
	if err := repository.MigrateTo(ctx, *targetVersion); err != nil {
		return err
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
