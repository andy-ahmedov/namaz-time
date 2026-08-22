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

## T023 visual redesign loop

Evidence label: `CONFIRMED_RUNTIME` for the following bounded observations on
the same controlled API 36 / 1920×1080 emulator. This does not promote the
observations to physical-TV evidence.

- version `0.4.0-pilot-local` / versionCode 3 built, installed with `adb
  install -r`, launched and remained the focused `MainActivity`;
- the final main screen rendered the original Golden dusk image full bleed,
  centered NamazTime pill and mosque identity, left next-prayer/countdown and
  clock cards, the six-row prayer table with distinct gold glyphs, centered
  Sunrise time, highlighted next row and bottom iqamah/provenance strip;
- D-pad entered Appearance, exposed the persisted current background and
  switched between Golden dusk and Blue hour without changing the displayed
  schedule; Settings retained visible focus and the shared glass/token system;
- main and Settings screenshots were visually inspected after the final APK
  install against the supplied hierarchy/mood reference. No reference pixels,
  background, logo or competitor asset were packaged.

The final debug APK SHA-256 is
`c0fd08c172219641eeefc5905e845224899cb5508f580f87f20675aaf1758610`.
The final main and Appearance/Settings screencap SHA-256 values are
`bf21629145d61c8088f01a6727425f55ed90798da4cf468c7a69a07ab59353c0`
and
`8477d2c053be5a97396d9f8a76185b4e5ef8824c5f3e5c75a6558c22d5e1791a`.
The raw screenshots remain outside Git.

Evidence label: `UNKNOWN` for physical overscan/readability, panel retention,
OEM focus differences and long-running 4K memory behavior.

## T024 reference-accuracy loop — 2026-08-21

Evidence label: `CONFIRMED_RUNTIME` for the bounded observations below on the
same controlled API 36 / 1920×1080 emulator. This remains emulator evidence,
not physical-TV/OEM evidence.

- version `0.4.0-pilot-local` / versionCode 3 was rebuilt, installed with
  `adb install -r` and launched as the focused `MainActivity`;
- four visual passes were captured and compared with `design.png` after both
  images were normalized to 960×540;
- the final foreground composition occupies approximately 71% of the viewport,
  uses equal-width columns, and aligns the two left cards and bottom strip with
  the reference proportions recorded in the T024 gap analysis;
- UIAutomator exposed the compact settings control as focusable, focused and
  labelled `Настройки`; the app window retained `KEEP_SCREEN_ON`;
- the screen continued to render real local display state, including the long
  mosque identity, separate adhan/iqamah values, centered Sunrise time and the
  Friday session, without changing T022/T023 schedule logic.

The final debug APK SHA-256 is
`24211cf8e227851f2048813d736dae5f1ec1df9b7ca0dfb680a1e51978ba154b`.
The final 1920×1080 screencap SHA-256 is
`56f3c3b989d1a4866df79fec0ca6ff071401454e28d6a88c66772f930254f89a`;
its normalized 960×540 review image is
`0e0b7e371a45d53732a4cd77a765940b5d1db0b395793afc6d09930f910ba4b3`.
Raw screenshots and UIAutomator output remain outside Git.

Evidence label: `UNKNOWN` for physical-device overscan/readability, OEM focus
rendering and long-running 4K behavior. The emulator clock could not be changed
by non-root ADB, so this run naturally exercised the Friday/Jumu'ah state rather
than reproducing the reference screenshot's exact weekday and prayer.

## T025 local QR and iqamah runtime loop — 2026-08-21

Evidence label: `CONFIRMED_RUNTIME` for the bounded observations below on the
controlled Android TV Emulator API 36 at 1920×1080/320 dpi. The supplied
`main_with_qr.png` is `CONFIRMED_PUBLIC` as a product-owner-supplied screenshot;
its explicit visual-use authorization is recorded in ADR 0014.

- the existing app was rebuilt, installed with `adb install -r` and launched
  without clearing Room or DataStore;
- the operator entered a synthetic `https://example.org/sadaqah` URL, purpose
  and motivation through the new QR settings, saved them, returned with D-pad
  and observed the three-column Sadaqah display;
- the operator entered and saved five independent iqamah values. UIAutomator
  then exposed Fajr `03:10`, Asr `18:00`, Maghrib `20:30` and Isha `22:20` in
  the public prayer rows. Friday Dhuhr remained separate from the real 13:15
  Jumu'ah session;
- QR Settings shows URL/purpose/motivation fields plus local preview. Iqamah
  Settings shows exactly five prayer fields and no removed Friday technical
  explanation;
- the final Sadaqah panel aligns its top with the prayer panel and its bottom
  with the event strip. It includes `Садака`, operator purpose/motivation, the
  corner frame, scan-tested NamazTime center badge, supplied-reference support
  icon treatment and subdued lower geometric ornament;
- the synthetic destination and transliterated content exist only in emulator
  DataStore/runtime evidence and are not a production campaign claim.

The authorized reference SHA-256 is
`dbe3fa01283d5176237923b6ae87f6a4beadaacb312e56707c2c90a74577b2ea`.
The final debug APK SHA-256 is
`4111151806786f2dc154321b7659ae6e7c239ed76f4c3c07a100b1ea87ddafb3`.
The final main, QR Settings and Iqamah Settings screencap SHA-256 values are
`d5b2efb270bd56412b1b4e8f802e0ebf675b7199658a59a9f90ed0e199bc9293`,
`7225026e45f46d7013a978b67c6b2910516b758affbe868b5b986ef6f62cb6fd`
and `f11e3859319d1dd2e1620b711212fa8f34e823154aa9ff34362672017e297b02`.
Raw reference and screenshots remain outside Git.

Evidence label: `UNKNOWN` for physical-TV overscan/readability, real-phone
scan distance, OEM keyboard/focus behavior and long-running panel retention.
