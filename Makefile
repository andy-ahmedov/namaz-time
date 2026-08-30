.PHONY: docs-check format format-check lint lint-go lint-android security-go secret-scan test test-go test-go-race test-contracts test-research test-android-unit test-android-all test-postgres test-postgres-restore build-android-pilot verify-android-pilot

GO_FILES := $(shell find cmd internal -type f -name '*.go' 2>/dev/null)
GRADLE_FLAGS ?= --no-daemon --no-build-cache
GITLEAKS_VERSION ?= v8.29.1
PILOT_APK_CERT_SHA256 := da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9

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

secret-scan:
	go run github.com/zricethezav/gitleaks/v8@$(GITLEAKS_VERSION) git --no-banner --redact=100 --log-opts='--all' .

lint-android:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:lintDebug

test: docs-check test-go test-research test-android-unit

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

test-research:
	python3 -m unittest discover -s research/tools -p 'test_*.py'

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

build-android-pilot:
	test -n "$(NAMAZTIME_PILOT_SIGNING_PROPERTIES)"
	./gradlew $(GRADLE_FLAGS) \
		-PnamaztimePilotSigningProperties="$(NAMAZTIME_PILOT_SIGNING_PROPERTIES)" \
		:apps:tv-android:assemblePilot
	NAMAZTIME_EXPECTED_PILOT_CERT_SHA256="$(PILOT_APK_CERT_SHA256)" \
		bash ./scripts/android-pilot-artifact-check.sh \
		apps/tv-android/build/outputs/apk/pilot/tv-android-pilot.apk

verify-android-pilot:
	test -n "$(NAMAZTIME_PILOT_APK)"
	NAMAZTIME_EXPECTED_PILOT_CERT_SHA256="$(PILOT_APK_CERT_SHA256)" \
		bash ./scripts/android-pilot-artifact-check.sh "$(NAMAZTIME_PILOT_APK)"
