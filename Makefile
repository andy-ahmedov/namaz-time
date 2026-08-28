.PHONY: docs-check format format-check lint lint-go lint-android security-go test test-go test-go-race test-contracts test-android-unit test-android-all test-postgres test-postgres-restore

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
	go tool staticcheck ./...

security-go:
	go tool govulncheck ./...

lint-android:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:lintDebug

test: docs-check test-go test-android-unit

test-go:
	go test ./...

test-go-race:
	go test -race ./...

test-postgres:
	bash ./scripts/test-postgres.sh
	bash ./scripts/test-postgres-restore.sh

test-postgres-restore:
	bash ./scripts/test-postgres-restore.sh

test-contracts:
	go test ./internal/domain -run 'Test(SyntheticSnapshotMatchesJSONSchemaAndDomain|InvalidSnapshotFixturesFailDeterministically|ProviderKindsMatchJSONSchemas|DomainAcceptsJSONSchemaDateTimeVariants|DomainRejectsJSONSchemaInvalidLeapSecond|ConditionalProvenanceRejectedBySchemaAndDomain)'

test-android-unit:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:testDebugUnitTest
	git diff --exit-code -- apps/tv-android/schemas

test-android-all:
	./gradlew $(GRADLE_FLAGS) --dependency-verification=strict \
		:apps:tv-android:testDebugUnitTest \
		:apps:tv-android:lintDebug \
		:apps:tv-android:lintRelease \
		:apps:tv-android:assembleDebug \
		:apps:tv-android:assembleRelease
	git diff --exit-code -- apps/tv-android/schemas
