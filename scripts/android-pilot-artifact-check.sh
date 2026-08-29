#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly APK_PATH="${1:-}"
readonly EXPECTED_APPLICATION_ID="ru.namaztime.tv"
readonly EXPECTED_CERT_SHA256="${NAMAZTIME_EXPECTED_PILOT_CERT_SHA256:-}"

fail() {
  printf 'android-pilot-artifact-check: %s\n' "$*" >&2
  exit 1
}

resolve_build_tool() {
  local -r tool_name="$1"
  local configured=""
  local sdk_root=""
  local resolved=""

  case "$tool_name" in
    aapt) configured="${AAPT:-}" ;;
    apksigner) configured="${APKSIGNER:-}" ;;
    *) fail "unsupported build tool: $tool_name" ;;
  esac
  if [[ -n "$configured" ]]; then
    [[ -x "$configured" ]] || fail "$tool_name is not executable: $configured"
    printf '%s\n' "$configured"
    return
  fi
  if resolved="$(command -v "$tool_name" 2>/dev/null)"; then
    printf '%s\n' "$resolved"
    return
  fi

  sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk_root" && -f "$ROOT/local.properties" ]]; then
    sdk_root="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -n 1)"
  fi
  [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]] ||
    fail "cannot locate Android SDK build-tools for $tool_name"
  resolved="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$tool_name" -perm -u+x -print | sort -V | tail -n 1)"
  [[ -n "$resolved" ]] || fail "cannot locate $tool_name under $sdk_root/build-tools"
  printf '%s\n' "$resolved"
}

[[ -n "$APK_PATH" ]] || fail "usage: $0 /absolute/or/relative/path/to/pilot.apk"
[[ -f "$APK_PATH" ]] || fail "APK does not exist: $APK_PATH"
command -v unzip >/dev/null 2>&1 || fail "unzip is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"

readonly AAPT_BIN="$(resolve_build_tool aapt)"
readonly APKSIGNER_BIN="$(resolve_build_tool apksigner)"
readonly TMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TMP_DIR"' EXIT

"$AAPT_BIN" dump badging "$APK_PATH" > "$TMP_DIR/badging.txt"
readonly PACKAGE_LINE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$TMP_DIR/badging.txt")"
[[ "$PACKAGE_LINE" == "$EXPECTED_APPLICATION_ID" ]] ||
  fail "expected application ID $EXPECTED_APPLICATION_ID, found ${PACKAGE_LINE:-<missing>}"

"$APKSIGNER_BIN" verify --verbose --print-certs "$APK_PATH" > "$TMP_DIR/signing.txt"
readonly CERT_SHA256="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$TMP_DIR/signing.txt" | tr '[:upper:]' '[:lower:]')"
[[ "$CERT_SHA256" =~ ^[0-9a-f]{64}$ ]] || fail "missing or malformed APK certificate SHA-256"

if [[ -n "$EXPECTED_CERT_SHA256" ]]; then
  readonly NORMALIZED_EXPECTED_CERT="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
  [[ "$CERT_SHA256" == "$NORMALIZED_EXPECTED_CERT" ]] ||
    fail "APK certificate does not match NAMAZTIME_EXPECTED_PILOT_CERT_SHA256"
fi

unzip -Z1 "$APK_PATH" > "$TMP_DIR/entries.txt"
for required_asset in \
  assets/pilot-local-ulyanovsk-2026-snapshot.json \
  assets/pilot-local-production-trust-bundle.json \
  assets/pilot-local-production-trust-bundle-previous.json \
  assets/pilot-local-staging-trust-bundle.json \
  assets/pilot-local-test-trust-bundle.json; do
  grep -Fxq "$required_asset" "$TMP_DIR/entries.txt" ||
    fail "required authenticated pilot asset is missing: $required_asset"
done

readonly APK_SHA256="$(sha256sum "$APK_PATH" | cut -d ' ' -f 1)"
readonly VERSION_CODE="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" "$TMP_DIR/badging.txt")"
readonly VERSION_NAME="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" "$TMP_DIR/badging.txt")"

printf 'android-pilot-artifact-check: PASS\n'
printf 'application_id=%s\n' "$PACKAGE_LINE"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'version_name=%s\n' "$VERSION_NAME"
printf 'certificate_sha256=%s\n' "$CERT_SHA256"
printf 'apk_sha256=%s\n' "$APK_SHA256"
