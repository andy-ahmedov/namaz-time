SHELL := /bin/bash

.PHONY: docs-check format format-check lint lint-go lint-android security-go secret-scan test test-skills test-go test-go-race test-contracts test-research test-android-unit test-android-build-identity test-android-all test-postgres test-postgres-restore test-android-t045-emulator build-android-pilot verify-android-pilot

GO_FILES := $(shell find cmd internal -type f -name '*.go' 2>/dev/null)
GRADLE_FLAGS ?= --no-daemon --no-build-cache
CONTRACT_PYTHON ?= python3
GITLEAKS_VERSION ?= v8.29.1
PILOT_APK_CERT_SHA256 := da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9
ANDROID_VERSION_PROPERTIES := apps/tv-android/version.properties
ANDROID_VERSION_CODE := $(shell sed -n 's/^versionCode=//p' $(ANDROID_VERSION_PROPERTIES))
ANDROID_VERSION_NAME := $(shell sed -n 's/^versionName=//p' $(ANDROID_VERSION_PROPERTIES))
ANDROID_PILOT_SEQUENCE := $(shell sed -n 's/^pilotSequence=//p' $(ANDROID_VERSION_PROPERTIES))
ANDROID_REMOTE_SEQUENCE := $(shell sed -n 's/^remoteSequence=//p' $(ANDROID_VERSION_PROPERTIES))
ANDROID_DEBUG_VERSION := $(ANDROID_VERSION_NAME)-dev
ANDROID_PILOT_VERSION := $(ANDROID_VERSION_NAME)-pilot.$(ANDROID_PILOT_SEQUENCE)
ANDROID_REMOTE_VERSION := $(ANDROID_VERSION_NAME)-remote.$(ANDROID_REMOTE_SEQUENCE)
ANDROID_PILOT_ARTIFACT_DIR ?= $(abspath ../namaztime-artifacts/android)

docs-check:
	bash ./scripts/docs-check.sh
	python3 -B scripts/test_skill_package.py

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

test: docs-check test-skills test-go test-contracts test-research test-android-unit test-android-build-identity

test-skills:
	python3 -B scripts/test_skill_package.py
	PYTHONDONTWRITEBYTECODE=1 python3 -B -m unittest discover -s .agents/skills/ui-ux-pro-max/scripts/tests

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
	$(CONTRACT_PYTHON) -B scripts/test_public_setup_contract.py
	go test ./internal/domain -run 'Test(SyntheticSnapshotMatchesJSONSchemaAndDomain|InvalidSnapshotFixturesFailDeterministically|ProviderKindsMatchJSONSchemas|DomainAcceptsJSONSchemaDateTimeVariants|DomainRejectsJSONSchemaInvalidLeapSecond|ConditionalProvenanceRejectedBySchemaAndDomain)'

test-research:
	python3 -m unittest discover -s research/tools -p 'test_*.py'
	python3 -B -m unittest discover -s research/t049 -p 'test_*.py'

test-android-unit:
	./gradlew $(GRADLE_FLAGS) :apps:tv-android:testDebugUnitTest
	git diff --exit-code -- apps/tv-android/schemas

test-android-build-identity:
	bash ./scripts/test-android-build-identity.sh

test-android-all:
	./gradlew $(GRADLE_FLAGS) --dependency-verification=strict \
		:apps:tv-android:testDebugUnitTest \
		:apps:tv-android:lintDebug \
		:apps:tv-android:lintRelease \
		:apps:tv-android:assembleDebug \
		:apps:tv-android:assembleRelease
	@set -Eeuo pipefail; \
	build_commit="$$(git rev-parse HEAD)"; \
	build_state="$$(if test -z "$$(git status --porcelain)"; then echo clean; else echo dirty; fi)"; \
	bash ./scripts/android-build-identity-check.sh \
		apps/tv-android/build/outputs/apk/debug/tv-android-debug.apk \
		ru.namaztime.tv.debug $(ANDROID_VERSION_CODE) $(ANDROID_DEBUG_VERSION) debug "$$build_state" "$$build_commit"; \
	bash ./scripts/android-build-identity-check.sh \
		apps/tv-android/build/outputs/apk/release/tv-android-release-unsigned.apk \
		ru.namaztime.tv $(ANDROID_VERSION_CODE) $(ANDROID_REMOTE_VERSION) release "$$build_state" "$$build_commit"
	git diff --exit-code -- apps/tv-android/schemas

build-android-pilot:
	test -n "$(NAMAZTIME_PILOT_SIGNING_PROPERTIES)"
	@set -Eeuo pipefail; \
	test -z "$$(git status --porcelain)" || { \
		echo "build-android-pilot: refuse to package a dirty or untracked working tree" >&2; \
		exit 1; \
	}; \
	build_commit="$$(git rev-parse HEAD)"; \
	NAMAZTIME_BUILD_COMMIT="$$build_commit" NAMAZTIME_BUILD_DIRTY=false \
		./gradlew $(GRADLE_FLAGS) --dependency-verification=strict \
		-PnamaztimePilotSigningProperties="$(NAMAZTIME_PILOT_SIGNING_PROPERTIES)" \
		:apps:tv-android:assemblePilot; \
	NAMAZTIME_EXPECTED_PILOT_CERT_SHA256="$(PILOT_APK_CERT_SHA256)" \
	NAMAZTIME_EXPECTED_VERSION_CODE="$(ANDROID_VERSION_CODE)" \
	NAMAZTIME_EXPECTED_VERSION_NAME="$(ANDROID_PILOT_VERSION)" \
	NAMAZTIME_EXPECTED_BUILD_COMMIT="$$build_commit" \
		bash ./scripts/android-pilot-release-bundle.sh \
		apps/tv-android/build/outputs/apk/pilot/tv-android-pilot.apk \
		"$(ANDROID_PILOT_ARTIFACT_DIR)"

verify-android-pilot:
	test -n "$(NAMAZTIME_PILOT_APK)"
	@test -n "$(NAMAZTIME_EXPECTED_BUILD_COMMIT)" || { \
		echo "verify-android-pilot: NAMAZTIME_EXPECTED_BUILD_COMMIT is required" >&2; \
		exit 1; \
	}
	NAMAZTIME_EXPECTED_PILOT_CERT_SHA256="$(PILOT_APK_CERT_SHA256)" \
	NAMAZTIME_EXPECTED_VERSION_CODE="$(ANDROID_VERSION_CODE)" \
	NAMAZTIME_EXPECTED_VERSION_NAME="$(ANDROID_PILOT_VERSION)" \
	NAMAZTIME_EXPECTED_BUILD_COMMIT="$(NAMAZTIME_EXPECTED_BUILD_COMMIT)" \
		bash ./scripts/android-pilot-artifact-check.sh "$(NAMAZTIME_PILOT_APK)"

# Explicit, controlled-emulator evidence; never part of unattended repository tests.
T045_EVIDENCE_PYTHON ?= python3
T045_EVIDENCE_ARGS ?= --output artifacts/t045-emulator --profiles 720p 1080p

test-android-t045-emulator:
	$(T045_EVIDENCE_PYTHON) scripts/android-t045-evidence.py $(T045_EVIDENCE_ARGS)

# Explicit controlled-emulator acceptance; dependencies and real 4K display are operator-local.
T046_EVIDENCE_PYTHON ?= python3
T046_EVIDENCE_ARGS ?= --output artifacts/t046-emulator --profiles 720p 1080p
.PHONY: test-android-t046-emulator
test-android-t046-emulator:
	$(T046_EVIDENCE_PYTHON) scripts/android-t046-evidence.py $(T046_EVIDENCE_ARGS)

T047_EVIDENCE_PYTHON ?= python3
T047_EVIDENCE_ARGS ?= --output /tmp/namaztime-t047-evidence
.PHONY: test-android-t047-emulator
test-android-t047-emulator:
	$(T047_EVIDENCE_PYTHON) scripts/android-t046-evidence.py --extra-backgrounds blue_hour night_minaret $(T047_EVIDENCE_ARGS)

T048_EVIDENCE_PYTHON ?= python3
T048_EVIDENCE_ARGS ?= --output /tmp/namaztime-t048-evidence
.PHONY: test-android-t048-emulator
test-android-t048-emulator:
	$(T048_EVIDENCE_PYTHON) scripts/android-t046-evidence.py --extra-backgrounds luminous_dusk blue_hour $(T048_EVIDENCE_ARGS)
