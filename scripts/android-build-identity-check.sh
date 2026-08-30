#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly APK_PATH="${1:-}"
readonly EXPECTED_APPLICATION_ID="${2:-}"
readonly EXPECTED_VERSION_CODE="${3:-}"
readonly EXPECTED_VERSION_NAME="${4:-}"
readonly EXPECTED_BUILD_VARIANT="${5:-}"
readonly EXPECTED_BUILD_STATE="${6:-}"
readonly EXPECTED_BUILD_COMMIT="${7:-}"

fail() {
  printf 'android-build-identity-check: %s\n' "$*" >&2
  exit 1
}

resolve_aapt() {
  local configured="${AAPT:-}"
  local sdk_root=""
  local resolved=""
  if [[ -n "$configured" ]]; then
    [[ -x "$configured" ]] || fail "aapt is not executable: $configured"
    printf '%s\n' "$configured"
    return
  fi
  if resolved="$(command -v aapt 2>/dev/null)"; then
    printf '%s\n' "$resolved"
    return
  fi
  sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk_root" && -f "$ROOT/local.properties" ]]; then
    sdk_root="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -n 1)"
  fi
  [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]] ||
    fail "cannot locate Android SDK build-tools for aapt"
  resolved="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt -perm -u+x -print | sort -V | tail -n 1)"
  [[ -n "$resolved" ]] || fail "cannot locate aapt under $sdk_root/build-tools"
  printf '%s\n' "$resolved"
}

extract_metadata() {
  local -r key="$1"
  awk -v target="$key" '
    index($0, "android:name") && index($0, "\"" target "\"") { found = 1; next }
    found && index($0, "android:value") {
      value = $0
      if (index(value, "Raw: \"") > 0) {
        sub(/^.*Raw: "/, "", value)
      } else {
        sub(/^.*android:value="/, "", value)
      }
      sub(/".*$/, "", value)
      print value
      exit
    }
    found && $0 ~ /^[[:space:]]*E:/ { exit }
  ' "$MANIFEST_DUMP"
}

[[ $# -eq 7 ]] || fail "usage: $0 APK APPLICATION_ID VERSION_CODE VERSION_NAME VARIANT STATE COMMIT"
[[ -f "$APK_PATH" ]] || fail "APK does not exist: $APK_PATH"
[[ "$EXPECTED_APPLICATION_ID" =~ ^[A-Za-z][A-Za-z0-9_.]+$ ]] || fail "expected application ID is invalid"
[[ "$EXPECTED_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || fail "expected version code is invalid"
[[ "$EXPECTED_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] ||
  fail "expected version name is invalid"
[[ "$EXPECTED_BUILD_VARIANT" =~ ^(debug|release|pilot)$ ]] || fail "expected build variant is invalid"
[[ "$EXPECTED_BUILD_STATE" =~ ^(clean|dirty)$ ]] || fail "expected build state is invalid"
[[ "$EXPECTED_BUILD_COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "expected build commit is invalid"

readonly AAPT_BIN="$(resolve_aapt)"
readonly TMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TMP_DIR"' EXIT
readonly BADGING_DUMP="$TMP_DIR/badging.txt"
readonly MANIFEST_DUMP="$TMP_DIR/manifest.txt"

"$AAPT_BIN" dump badging "$APK_PATH" > "$BADGING_DUMP"
"$AAPT_BIN" dump xmltree "$APK_PATH" AndroidManifest.xml > "$MANIFEST_DUMP"

readonly APPLICATION_ID="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$BADGING_DUMP")"
readonly VERSION_CODE="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" "$BADGING_DUMP")"
readonly VERSION_NAME="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" "$BADGING_DUMP")"
readonly BUILD_COMMIT="$(extract_metadata ru.namaztime.tv.BUILD_COMMIT)"
readonly BUILD_VARIANT="$(extract_metadata ru.namaztime.tv.BUILD_VARIANT)"
readonly BUILD_STATE="$(extract_metadata ru.namaztime.tv.BUILD_STATE)"

[[ "$APPLICATION_ID" == "$EXPECTED_APPLICATION_ID" ]] ||
  fail "expected application ID $EXPECTED_APPLICATION_ID, found ${APPLICATION_ID:-<missing>}"
[[ "$VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] ||
  fail "expected version code $EXPECTED_VERSION_CODE, found ${VERSION_CODE:-<missing>}"
[[ "$VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] ||
  fail "expected version name $EXPECTED_VERSION_NAME, found ${VERSION_NAME:-<missing>}"
[[ "$BUILD_VARIANT" == "$EXPECTED_BUILD_VARIANT" ]] ||
  fail "expected build variant $EXPECTED_BUILD_VARIANT, found ${BUILD_VARIANT:-<missing>}"
[[ "$BUILD_STATE" == "$EXPECTED_BUILD_STATE" ]] ||
  fail "expected build state $EXPECTED_BUILD_STATE, found ${BUILD_STATE:-<missing>}"
[[ "$BUILD_COMMIT" == "$EXPECTED_BUILD_COMMIT" ]] ||
  fail "expected build commit $EXPECTED_BUILD_COMMIT, found ${BUILD_COMMIT:-<missing>}"

printf 'android-build-identity-check: PASS\n'
printf 'application_id=%s\n' "$APPLICATION_ID"
printf 'version_code=%s\n' "$VERSION_CODE"
printf 'version_name=%s\n' "$VERSION_NAME"
printf 'build_variant=%s\n' "$BUILD_VARIANT"
printf 'build_state=%s\n' "$BUILD_STATE"
printf 'build_commit=%s\n' "$BUILD_COMMIT"
