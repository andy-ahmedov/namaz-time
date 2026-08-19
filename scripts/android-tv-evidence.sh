#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-islam.islamapp.masjid}"
SERIAL="${1:-}"
OUT_BASE="${2:-./research/runtime-evidence}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$OUT_BASE/${PACKAGE}-${STAMP}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required. Install Android platform-tools and make adb available in WSL." >&2
  exit 2
fi

if [[ -z "$SERIAL" ]]; then
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "Pass the ADB serial as argument 1. Ready devices: ${devices[*]:-none}" >&2
    exit 2
  fi
  SERIAL="${devices[0]}"
fi

ADB=(adb -s "$SERIAL")
"${ADB[@]}" get-state >/dev/null
mkdir -p "$OUT"

run() {
  local name="$1"
  shift
  {
    printf '# command:'
    printf ' %q' "$@"
    printf '\n# captured_utc: %s\n\n' "$(date -u +%FT%TZ)"
    "$@"
  } >"$OUT/$name.txt" 2>&1 || true
}

cat >"$OUT/README.txt" <<EOF
Read-only Android TV evidence bundle
Package: $PACKAGE
ADB serial: $SERIAL
Captured UTC: $(date -u +%FT%TZ)

This script intentionally does NOT:
- pull or redistribute an APK;
- read app-private files;
- intercept TLS traffic;
- capture Wi-Fi credentials, SSID/BSSID, accounts, contacts or precise location;
- modify settings, clear data, force-stop or launch the application;
- collect logcat automatically (logs can contain tokens or personal data).

Review every file before sharing it outside the research team.
EOF

run adb-version adb version
run device-list adb devices -l
run identity "${ADB[@]}" shell sh -c \
  'printf "manufacturer="; getprop ro.product.manufacturer; printf "model="; getprop ro.product.model; printf "device="; getprop ro.product.device; printf "android_release="; getprop ro.build.version.release; printf "sdk="; getprop ro.build.version.sdk; printf "build_fingerprint="; getprop ro.build.fingerprint'
run clock "${ADB[@]}" shell sh -c \
  'printf "date="; date -Iseconds 2>/dev/null || date; printf "timezone="; getprop persist.sys.timezone; printf "auto_time="; settings get global auto_time; printf "auto_time_zone="; settings get global auto_time_zone'
run package-path "${ADB[@]}" shell pm path "$PACKAGE"
run package-dump "${ADB[@]}" shell dumpsys package "$PACKAGE"
run appops "${ADB[@]}" shell appops get "$PACKAGE"
run leanback-resolution "${ADB[@]}" shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER "$PACKAGE"
run launcher-resolution "${ADB[@]}" shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE"
run running-process "${ADB[@]}" shell pidof "$PACKAGE"
run activity-filter "${ADB[@]}" shell sh -c \
  "dumpsys activity activities | grep -F -C 8 '$PACKAGE' || true"
run jobs-filter "${ADB[@]}" shell sh -c \
  "dumpsys jobscheduler | grep -F -C 18 '$PACKAGE' || true"
run alarms-filter "${ADB[@]}" shell sh -c \
  "dumpsys alarm | grep -F -C 12 '$PACKAGE' || true"
run power "${ADB[@]}" shell dumpsys power
run storage "${ADB[@]}" shell df -h /data

cat >"$OUT/NEXT_STEPS.txt" <<'EOF'
1. Record the visible app state and exact test steps in BLACK_BOX_VALIDATION_PLAN.md.
2. For a short logcat experiment, clear logs, reproduce one action, then capture only the app PID:
     adb -s <serial> logcat -c
     adb -s <serial> shell pidof islam.islamapp.masjid
     adb -s <serial> logcat --pid=<pid> -d > logcat-review-before-sharing.txt
   Review and redact tokens, URLs with secrets, identifiers and personal data before sharing.
3. Network metadata should be collected at an owned test router. Do not bypass TLS pinning or decrypt protected traffic.
4. Obtain the official-store installation certificate fingerprint separately and compare it with the APKPure-copy fingerprint recorded in APK_RESEARCH_ISLAMAPP_1_6_2.md.
EOF

printf 'Evidence written to %s\n' "$OUT"
