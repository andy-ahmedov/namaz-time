# BLACK_BOX_VALIDATION_PLAN.md

## Purpose

Complete the remaining runtime research of the supplied/official IslamApp build on a controlled Android TV or box without bypassing access controls, TLS security, anti-tamper or store protections.

Static findings are already documented. This plan tests visible behavior and ordinary system/network metadata only.

## Current status

`BLOCKED`: the analysis environment has no physical Android TV/box, Android SDK/emulator tooling or `/dev/kvm`. Therefore no runtime claim is marked confirmed yet.

## Artifact trust

The supplied APK came from APKPure. Before drawing security/update conclusions, compare its package name, version and signing certificate with a legally obtained official-store build. A matching certificate is strong identity evidence; a mismatch requires stopping and investigating.

Do not publish or redistribute either APK.

## Read-only evidence helper

From WSL, after Android platform-tools and ADB-over-network/USB are configured for an owned test device:

```bash
./scripts/android-tv-evidence.sh <adb-serial>
```

The helper gathers package/device/clock/job metadata without pulling the APK, reading app-private files, capturing logcat or modifying the device. Review its output before sharing. It does not replace the visible test matrix below.

## Required equipment

- one Android TV/Google TV device or box, preferably pilot hardware;
- optional second OEM for compatibility comparison;
- isolated test Wi-Fi/router with DNS/connection logs;
- Windows 11 + WSL host with Android platform tools;
- USB/network ADB enabled only for test device;
- test phone for QR scans;
- spreadsheet/JSON evidence template and photos/video of visible results.

## Evidence labels

Every observation is tagged:

- `CONFIRMED_RUNTIME` — reproduced and recorded;
- `INFERENCE` — supported but not directly visible;
- `UNKNOWN` — unresolved.

## Test matrix

### A. Install and identity

1. install official-store version where possible;
2. record package/version/certificate fingerprint;
3. record requested permissions and first-run prompts;
4. compare with supplied APK static metadata;
5. clear app data before each clean-run branch.

### B. City/region source behavior

Test at least:

- Ulyanovsk;
- Kazan/Tatarstan;
- Moscow;
- Ufa/Bashkortostan;
- Makhachkala/Dagestan;
- one uncovered foreign/local coordinate if practical.

For each:

1. choose locality/coordinates through normal UI;
2. record displayed six times for selected dates;
3. compare with relevant official table and static-profile prediction;
4. record source/method wording exposed by UI;
5. test boundary coordinates only through normal location selection, not by modifying app data.

### C. Offline behavior

1. finish setup online;
2. disable internet at router;
3. cold restart app/device;
4. verify today's/next day's schedule;
5. verify QR/background/settings behavior;
6. advance only through safe test dates/device clock procedures and restore automatic time afterward;
7. record how long cached coverage remains and what stale state looks like.

### D. Update behavior

Without intercepting decrypted TLS content:

- observe DNS/SNI/destination metadata at router;
- record timestamps and byte counts when app starts and when manual refresh occurs;
- wait through normal scheduled periods where feasible;
- compare local app-private file metadata only when ADB access legitimately permits it;
- test server unreachable, HTTP-blocked domain and network recovery;
- do not defeat certificate pinning or modify the APK.

Questions:

- is update checked on launch, every six days, or both?
- does a failed download keep current values?
- is there visible freshness/version information?
- does manual city change force an immediate one-shot refresh?

### E. Midnight/year/timezone

- 23:59 → 00:00 in selected mosque timezone;
- December 31 → January 1;
- leap day if testable safely;
- device timezone deliberately different from selected city;
- automatic time off / implausible clock;
- restore settings and document side effects.

### F. Iqamah

For each prayer:

- fixed time;
- offset mode;
- invalid earlier-than-adhan value;
- Maghrib behavior;
- persistence after restart;
- Friday/Jumu'ah settings;
- determine whether date ranges/weekday exceptions exist in UI.

### G. QR fundraising

- direct URL and long URL;
- title/subtitle length;
- always-show vs with-times behavior;
- offline generation/display;
- real scan from hall distance;
- redirect destination transparency;
- invalid/non-HTTPS URL handling.

Do not send money during testing.

### H. Themes and orientation

- all built-in themes;
- landscape/portrait if device supports rotation;
- custom background capability, if any;
- contrast/blur controls;
- memory/restart behavior at 4K;
- fallback when network unavailable.

### I. Boot/autostart

- setting disabled/enabled;
- cold power removal/replug;
- normal reboot;
- device locked/unlocked state;
- overlay permission denied/granted;
- OEM quick boot;
- process force-stop, noting Android's force-stop semantics;
- compare ordinary mode with managed kiosk only on owned test hardware.

### J. Privacy/network inventory

Record domains and ordinary SDK behavior without reading protected payloads:

- analytics/crash endpoints;
- location/geocoding calls;
- update host;
- donation redirect host;
- identifiers visible in Android privacy dashboard/logcat only where normally accessible;
- compare actual behavior with store declarations.

## Evidence record template

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
Evidence label:
Notes/next hypothesis:
```

## Stop conditions

Stop and document rather than bypass when:

- app requires authentication/authorization not provided;
- TLS pinning blocks inspection;
- anti-tamper/store protection triggers;
- requested action would modify a third-party service or violate terms;
- certificate does not match official build;
- a test risks disrupting mosque/public operation.

## Exit criteria

- source selection behavior reproduced for representative regions;
- offline and date rollover recorded;
- update cadence/trigger narrowed with runtime evidence;
- iqamah, QR, theme and boot behavior mapped;
- network/domain inventory compared with declarations;
- unknowns explicitly listed;
- repository research docs updated without copying proprietary data.
