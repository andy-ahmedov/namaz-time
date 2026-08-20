package main

import (
	"bytes"
	"strings"
	"testing"
)

func TestRunRequiresIndirectMigrationCredential(t *testing.T) {
	var stderr bytes.Buffer
	if err := run(nil, &stderr); err == nil || !strings.Contains(err.Error(), "-database-url-env") {
		t.Fatalf("run() error = %v", err)
	}
	t.Setenv("NAMAZ_MISSING_MIGRATION_DSN", "")
	if err := run([]string{"-database-url-env", "NAMAZ_MISSING_MIGRATION_DSN"}, &stderr); err == nil || !strings.Contains(err.Error(), "is not set") {
		t.Fatalf("run(missing env) error = %v", err)
	}
}

func TestRunRejectsUnsafeOrUnknownTargetBeforeConnecting(t *testing.T) {
	t.Setenv("NAMAZ_MIGRATION_DSN_TEST", "postgres://localhost/unused?sslmode=disable")
	for _, target := range []string{"0", "999"} {
		var stderr bytes.Buffer
		err := run([]string{
			"-database-url-env", "NAMAZ_MIGRATION_DSN_TEST",
			"-target-version", target,
		}, &stderr)
		if err == nil || !strings.Contains(err.Error(), "target version "+target+" is unknown") {
			t.Fatalf("run(target %s) error = %v", target, err)
		}
	}
}
