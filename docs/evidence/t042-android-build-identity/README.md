# T042 Android build-identity and in-place upgrade evidence

Evidence date: 2026-08-31

Environment: controlled Android Studio TV emulator `emulator-5554`, Android 16
/ API 36, 1920×1080, density 320. This is `CONFIRMED_RUNTIME` evidence for the
emulator only; physical-TV/OEM upgrade behavior remains `UNKNOWN`.

## Artifact identity

The signed pilot was packaged outside the repository from clean checkpoint
`609dda98a92417b48b6771a8f3d3dc0b0940ff25` with the external permanent pilot
APK key. The ignored handover bundle reported:

| Field | Value |
|---|---|
| Application ID | `ru.namaztime.tv` |
| Version | `0.5.0-pilot.1` |
| Version code | `4` |
| Variant/state | `pilot` / `clean` |
| APK SHA-256 | `87ab34c42ccb8adc6709f65bce113586bc5c1206322a9f956d884ef8256e2509` |
| APK certificate SHA-256 | `da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9` |
| Snapshot ID | `ulyanovsk-second-cathedral-2026-pilot-local-v2` |
| Raw snapshot SHA-256 | `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b` |

`make build-android-pilot` also rejected a deliberately dirty tree before
Gradle packaging. No APK, keystore, properties file or private key is stored in
the workspace, this evidence directory or Git.

## Upgrade observation

Before `adb install -r`, Android package manager reported version
`0.4.0-pilot-local` / code `3`, first install time
`2026-08-29 13:32:25` and last update time `2026-08-29 13:38:05`.
Installation returned `Success`. Afterwards it reported
`0.5.0-pilot.1` / code `4`, the same first install time and last update time
`2026-08-30 20:43:49`. The retained first-install identity is evidence of an
in-place update, not uninstall/reinstall.

A cold launch rendered the existing Second Cathedral Mosque schedule from
local persistence. Diagnostics displayed `0.5.0-pilot.1 (4)` and
`pilot · 609dda98a924 · чистая`, while retaining the existing snapshot ID,
parser, approval ID and signature-key ID. The signed snapshot file was checked
again byte-for-byte at the expected SHA-256 above.

## Screenshots

- `after-upgrade-main.png` — cold-launched last-known-good pilot display;
  SHA-256 `81c6f4d3539a29548d05af087fdab2440754b9f164889e920044d29290583838`.
- `diagnostics.png` — version/build/snapshot identity in Settings;
  SHA-256 `d21220129a7d0282e4aed94aca36e613ce60531f41e3859345aa4dd75aa5418d`.

These screenshots contain no tokens, device identifiers, private keys or APK
contents.
