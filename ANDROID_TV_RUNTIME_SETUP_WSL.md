# Android TV runtime evidence setup from Windows 11 + WSL

## Purpose

Connect an owned Android TV/Google TV device or box to ADB and collect the remaining black-box evidence without pulling private app data, bypassing TLS, modifying the APK or disrupting a mosque display.

Read [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md) before starting.

## 1. Prepare the test device

Use a spare/controlled device rather than a television currently serving a prayer hall.

1. Open Android settings.
2. Open **About** and select the build number repeatedly until developer mode is enabled.
3. Open **Developer options**.
4. Enable USB debugging or Wireless debugging, depending on what the OEM exposes.
5. Put the TV/box and computer on the same trusted local network.
6. Record device model, Android version and current timezone before changing anything.

OEM menus differ. Stop if the device does not expose ordinary debugging; do not bypass vendor restrictions.

## 2. Install current platform-tools in WSL

Preferred: use the latest official Android SDK Platform-Tools rather than relying on an old distro package.

After installation, verify:

```bash
adb version
adb devices -l
```

Do not run separate competing ADB servers in Windows and WSL during one test. Keep the workflow in WSL unless a particular OEM driver forces a Windows-hosted USB connection.

## 3. Connect over Wi-Fi

### Android 11 or newer with Wireless debugging

The device shows separate pairing and connection ports.

```bash
adb pair TV_IP:PAIRING_PORT
# enter the pairing code shown on the TV
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

### Older/OEM TCP debugging

Some TV boxes expose ordinary TCP ADB after debugging is enabled:

```bash
adb connect TV_IP:5555
adb devices -l
```

If connection fails, check same-LAN reachability, client/AP isolation and Windows firewall before changing the device. Never expose ADB port 5555 to the public Internet.

## 4. Establish application identity

Prefer a legally obtained official-store installation. Record package/version/certificate evidence before comparing it with the APKPure-copy report.

The read-only helper does not install, extract or redistribute the application:

```bash
cd mosque-prayer-tv-codex-pack-v2
./scripts/android-tv-evidence.sh TV_IP:PORT
```

Output is written under `research/runtime-evidence/`. Review every file before sharing.

## 5. Run the visible black-box matrix

Follow [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md) and record every observation with:

```text
Test ID:
App source/version/certificate:
Device/OS/build:
Network state:
Locality/timezone/date:
Steps:
Expected:
Observed:
Evidence files:
Result: PASS | FAIL | INCONCLUSIVE
Evidence label: CONFIRMED_RUNTIME | INFERENCE | UNKNOWN
```

Highest-priority sequence:

1. normal online setup for Ulyanovsk;
2. cold restart offline;
3. next-day and midnight rollover;
4. city comparison: Kazan, Moscow, Ufa, Makhachkala;
5. fixed and offset iqamah;
6. QR behavior online/offline;
7. autostart after normal reboot and cold power cycle;
8. update-host metadata and failure recovery at an owned router.

## 6. Scoped logs only when needed

Logs can contain identifiers and tokens. Capture a short, app-PID-scoped sample only after reproducing one action:

```bash
SERIAL=TV_IP:PORT
PACKAGE=islam.islamapp.masjid
adb -s "$SERIAL" logcat -c
PID="$(adb -s "$SERIAL" shell pidof "$PACKAGE" | tr -d '\r')"
# reproduce one action on the TV
adb -s "$SERIAL" logcat --pid="$PID" -d > logcat-review-before-sharing.txt
```

Review and redact before sharing. Do not bypass certificate pinning or decrypt protected traffic.

## 7. End the test safely

```bash
adb disconnect TV_IP:PORT
```

Then disable wireless/USB debugging or revoke debugging authorizations on the device. Restore automatic date/time, timezone, network and application settings changed during testing.

## Exit condition

Runtime research is complete only when the evidence matrix covers source selection, offline operation, rollover, iqamah, QR, themes, boot behavior and update failure/recovery on at least the pilot hardware.
