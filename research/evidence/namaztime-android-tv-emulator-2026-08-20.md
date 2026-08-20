# NamazTime Android TV Emulator runtime evidence — 2026-08-20

Evidence label: `CONFIRMED_RUNTIME` for the bounded observations below on a
controlled Android Studio emulator. This is not physical-TV/OEM evidence.

## Environment

- ADB serial: `emulator-5554`;
- product/model: `sdk_google_atv64_x86_64`;
- Android: 16, API 36;
- physical framebuffer: 1920×1080 at 320 dpi;
- device timezone: `GMT`;
- installed package: `com.example.namaztime.tv`;
- version: `0.2.0-shell` / versionCode 1.

## Observed runtime

- `MainActivity` was top-resumed, fullscreen and backed by a live process;
- launch intent used `android.intent.category.LEANBACK_LAUNCHER`;
- the app window held the screen-on flag while display mode was active;
- the screen displayed 20 August 2026 and Ulyanovsk time `18:41:33` while the
  emulator system clock reported `14:41:31 GMT`, demonstrating the bounded
  mosque-timezone display path on this run;
- UIAutomator exposed the prayer rows, countdown and accessible row
  descriptions;
- D-pad center opened Settings; Back returned to the main prayer display.

The final local checkpoint rebuilt and installed
`apps/tv-android/build/outputs/apk/debug/tv-android-debug.apk` successfully.
Its SHA-256 is
`1308f1e65622335ab988ca481a508613d80ab882540929e56c40541e76f98539`;
APK metadata exposes application label `NamazTime`, the Leanback activity and
version `0.3.0-pilot-local` / versionCode 2, plus only the documented
sync/WorkManager permissions. After reinstall, the same activity was
top-resumed/fullscreen, retained `KEEP_SCREEN_ON`, and a fresh D-pad center/back
check again entered Settings and returned to the display.

The product-owner-supplied screenshot SHA-256 is
`75274f756bcdc69a091678d3218e70cdb0467e616558102a66b742312f44a2f8`.
An independent ADB screencap from the same emulator session had SHA-256
`84e4ee0f8a8cb8d149a39a2dbd276f060cfe637180f41bcb1d2193b26a53c512`.
The raw screenshots remain outside Git; this record contains no credentials or
private application data.

## Not established

- real approved snapshot activation (the installed APK still uses the visibly
  synthetic fixture);
- physical overscan/readability, OEM boot, panel retention or power-loss;
- pairing/API sync, rollback or key revocation on this emulator session;
- performance soak or seven-day offline runtime.
