package main

import "testing"

func TestComponentName(t *testing.T) {
	t.Parallel()

	if componentName != "ingestor" {
		t.Fatalf("componentName = %q, want ingestor", componentName)
	}
}
