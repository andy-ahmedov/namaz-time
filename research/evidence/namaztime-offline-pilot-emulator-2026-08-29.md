# NamazTime signed offline-pilot emulator evidence — 2026-08-29

## Scope

This record covers the product-owner-selected D-015 USB/offline deployment
mode after D-005 accepted application ID `ru.namaztime.tv`. It does not cover a
physical mosque TV, OEM boot behavior, long soak, Google Play or remote sync.

## Controlled environment

- `CONFIRMED_RUNTIME` — Android TV emulator `sdk_google_atv64_x86_64`;
- `CONFIRMED_RUNTIME` — Android 16 / API 36;
- `CONFIRMED_RUNTIME` — 1920×1080 at 320 dpi;
- `CONFIRMED_RUNTIME` — APK versionCode `3`, versionName
  `0.4.0-pilot-local`.

## Artifact identity

- application ID: `ru.namaztime.tv`;
- APK SHA-256:
  `6ed842dc8eaa5e2d437dd2ec3b21c41aa61d15a07ce5fa02cb6914891bf0d43f`;
- signing certificate SHA-256:
  `da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`;
- local handover copy:
  `/home/andy/.local/share/namaztime/pilot/namaztime-tv-0.4.0-v3.apk`
  (outside the repository);
- size: 24,220,224 bytes.

`scripts/android-pilot-artifact-check.sh` verified the APK signature, exact
application ID and the presence of the approved snapshot plus production,
staging and test public trust bundles. No private signing material is packaged
or committed.

## Runtime checks

- `CONFIRMED_RUNTIME` — `adb install -r` installed the signed APK successfully;
- `CONFIRMED_RUNTIME` — `ru.namaztime.tv/.MainActivity` became the resumed TV
  activity and rendered the Second Cathedral Mosque schedule from local state;
- `CONFIRMED_RUNTIME` — package flags omitted `DEBUGGABLE`, and Android
  rejected `run-as` with `package not debuggable`;
- `CONFIRMED_RUNTIME` — a second `adb install -r` of the same package and
  certificate succeeded as an in-place reinstall rather than a second app;
- `CONFIRMED_RUNTIME` — the sampled post-launch log contained no fatal
  exception for `ru.namaztime.tv`.

The earlier `com.example.namaztime.tv` debug package remained separately
installed, confirming that the permanent ID is a new Android identity. The
current debug build now uses `ru.namaztime.tv.debug` so it cannot occupy the
signed pilot identity in future development runs.

## Remaining evidence

- `UNKNOWN` — two operator-controlled offline backups of the APK keystore;
- `UNKNOWN` — physical mosque TV install/update, reboot/autostart, overscan,
  OEM picker behavior, QR scan distance and long-running thermal/memory soak.
