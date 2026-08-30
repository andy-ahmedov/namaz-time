#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly APK_PATH="${1:-}"
readonly OUTPUT_DIR="${2:-}"
readonly EXPECTED_COMMIT="${NAMAZTIME_EXPECTED_BUILD_COMMIT:-}"
readonly SNAPSHOT_PATH="$ROOT/apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json"
readonly EXPECTED_SNAPSHOT_SHA256="78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b"

fail() {
  printf 'android-pilot-release-bundle: %s\n' "$*" >&2
  exit 1
}

field() {
  local -r name="$1"
  sed -n "s/^${name}=//p" <<<"$VERIFICATION" | tail -n 1
}

[[ -n "$APK_PATH" && -f "$APK_PATH" ]] || fail "pilot APK does not exist: ${APK_PATH:-<missing>}"
[[ -n "$OUTPUT_DIR" ]] || fail "usage: $0 PILOT_APK OUTPUT_DIR"
[[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "NAMAZTIME_EXPECTED_BUILD_COMMIT is required"
[[ -f "$SNAPSHOT_PATH" ]] || fail "bundled pilot snapshot is missing"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"

readonly SNAPSHOT_SHA256="$(sha256sum "$SNAPSHOT_PATH" | cut -d ' ' -f 1)"
[[ "$SNAPSHOT_SHA256" == "$EXPECTED_SNAPSHOT_SHA256" ]] ||
  fail "signed Ulyanovsk snapshot bytes changed"

readonly VERIFICATION="$(bash "$ROOT/scripts/android-pilot-artifact-check.sh" "$APK_PATH")"
printf '%s\n' "$VERIFICATION"

readonly APPLICATION_ID="$(field application_id)"
readonly VERSION_CODE="$(field version_code)"
readonly VERSION_NAME="$(field version_name)"
readonly BUILD_VARIANT="$(field build_variant)"
readonly BUILD_STATE="$(field build_state)"
readonly BUILD_COMMIT="$(field build_commit)"
readonly CERTIFICATE_SHA256="$(field certificate_sha256)"
readonly SOURCE_APK_SHA256="$(field apk_sha256)"
[[ "$BUILD_COMMIT" == "$EXPECTED_COMMIT" ]] || fail "verified commit does not match requested commit"

readonly SHORT_COMMIT="${BUILD_COMMIT:0:12}"
readonly ARTIFACT_STEM="namaztime-${VERSION_NAME}-code${VERSION_CODE}-${SHORT_COMMIT}"
readonly ARTIFACT_DIR="$OUTPUT_DIR/$ARTIFACT_STEM"
readonly ARTIFACT_APK="$ARTIFACT_DIR/$ARTIFACT_STEM.apk"
readonly ARTIFACT_MANIFEST="$ARTIFACT_DIR/$ARTIFACT_STEM.manifest.json"
readonly ARTIFACT_CHECKSUM="$ARTIFACT_DIR/$ARTIFACT_STEM.sha256"

mkdir -p "$ARTIFACT_DIR"
if [[ -f "$ARTIFACT_APK" ]]; then
  readonly EXISTING_SHA256="$(sha256sum "$ARTIFACT_APK" | cut -d ' ' -f 1)"
  [[ "$EXISTING_SHA256" == "$SOURCE_APK_SHA256" ]] ||
    fail "artifact path already contains different APK bytes: $ARTIFACT_APK"
else
  install -m 0644 "$APK_PATH" "$ARTIFACT_APK"
fi
printf '%s  %s\n' "$SOURCE_APK_SHA256" "$(basename "$ARTIFACT_APK")" > "$ARTIFACT_CHECKSUM"

python3 - "$ARTIFACT_MANIFEST" <<PY
import json
import sys

manifest = {
    "schema_version": "namaztime-android-artifact/v1",
    "application_id": "$APPLICATION_ID",
    "version_code": int("$VERSION_CODE"),
    "version_name": "$VERSION_NAME",
    "build_variant": "$BUILD_VARIANT",
    "build_state": "$BUILD_STATE",
    "build_commit": "$BUILD_COMMIT",
    "apk_file": "$(basename "$ARTIFACT_APK")",
    "apk_sha256": "$SOURCE_APK_SHA256",
    "apk_certificate_sha256": "$CERTIFICATE_SHA256",
    "snapshot_id": "ulyanovsk-second-cathedral-2026-pilot-local-v2",
    "snapshot_sha256": "$SNAPSHOT_SHA256",
}
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as target:
    json.dump(manifest, target, ensure_ascii=False, indent=2, sort_keys=True)
    target.write("\n")
PY

printf 'android-pilot-release-bundle: PASS\n'
printf 'artifact_apk=%s\n' "$ARTIFACT_APK"
printf 'artifact_manifest=%s\n' "$ARTIFACT_MANIFEST"
printf 'artifact_checksum=%s\n' "$ARTIFACT_CHECKSUM"
