.PHONY: docs-check format format-check lint lint-go lint-android test test-go test-contracts test-android-unit

GO_FILES := $(shell find cmd internal -type f -name '*.go' 2>/dev/null)
GRADLE_FLAGS ?= --no-daemon

docs-check:
	bash ./scripts/docs-check.sh

format:
	gofmt -w $(GO_FILES)

format-check:
	test -z "$$(gofmt -l $(GO_FILES))"

lint: docs-check format-check lint-go lint-android

lint-go:
	go vet ./...

lint-android:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:lintDebug

test: docs-check test-go test-android-unit

test-go:
	go test ./...

test-contracts:
	go test ./internal/domain -run 'Test(SyntheticSnapshotMatchesJSONSchemaAndDomain|InvalidSnapshotFixturesFailDeterministically|ProviderKindsMatchJSONSchemas|DomainAcceptsJSONSchemaDateTimeVariants|DomainRejectsJSONSchemaInvalidLeapSecond|ConditionalProvenanceRejectedBySchemaAndDomain)'

test-android-unit:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:testDebugUnitTest
