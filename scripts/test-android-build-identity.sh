#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
readonly TMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TMP_DIR"' EXIT

touch "$TMP_DIR/app.apk"
cat > "$TMP_DIR/aapt" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "$1 $2" == "dump badging" ]]; then
  printf "%s\n" "package: name='ru.namaztime.tv' versionCode='4' versionName='0.5.0-pilot.1'"
  exit 0
fi
cat <<'TREE'
E: application
  E: meta-data
    A: android:name="ru.namaztime.tv.BUILD_COMMIT" (Raw: "ru.namaztime.tv.BUILD_COMMIT")
    A: android:value="0123456789abcdef0123456789abcdef01234567" (Raw: "0123456789abcdef0123456789abcdef01234567")
  E: meta-data
    A: android:name="ru.namaztime.tv.BUILD_VARIANT" (Raw: "ru.namaztime.tv.BUILD_VARIANT")
    A: android:value="pilot" (Raw: "pilot")
  E: meta-data
    A: android:name="ru.namaztime.tv.BUILD_STATE" (Raw: "ru.namaztime.tv.BUILD_STATE")
    A: android:value="clean" (Raw: "clean")
TREE
EOF
chmod +x "$TMP_DIR/aapt"

output="$({
  AAPT="$TMP_DIR/aapt" bash "$ROOT/scripts/android-build-identity-check.sh" \
    "$TMP_DIR/app.apk" \
    ru.namaztime.tv \
    4 \
    0.5.0-pilot.1 \
    pilot \
    clean \
    0123456789abcdef0123456789abcdef01234567
} 2>&1)"
grep -Fxq "android-build-identity-check: PASS" <<<"$output"
grep -Fxq "build_commit=0123456789abcdef0123456789abcdef01234567" <<<"$output"
grep -Fxq "build_variant=pilot" <<<"$output"
grep -Fxq "build_state=clean" <<<"$output"

if AAPT="$TMP_DIR/aapt" bash "$ROOT/scripts/android-build-identity-check.sh" \
  "$TMP_DIR/app.apk" ru.namaztime.tv 4 0.5.0-pilot.1 release clean \
  0123456789abcdef0123456789abcdef01234567 >/dev/null 2>&1; then
  echo "test-android-build-identity: wrong variant unexpectedly passed" >&2
  exit 1
fi

if AAPT="$TMP_DIR/aapt" bash "$ROOT/scripts/android-build-identity-check.sh" \
  "$TMP_DIR/app.apk" ru.namaztime.tv 5 0.5.0-pilot.1 pilot clean \
  0123456789abcdef0123456789abcdef01234567 >/dev/null 2>&1; then
  echo "test-android-build-identity: wrong version code unexpectedly passed" >&2
  exit 1
fi

printf 'test-android-build-identity: PASS\n'
