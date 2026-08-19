package main

import "testing"

func TestComponentName(t *testing.T) {
	t.Parallel()

	if componentName != "api" {
		t.Fatalf("componentName = %q, want api", componentName)
	}
}
