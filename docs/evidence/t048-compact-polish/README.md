# T048 compact elegance and cinematic polish evidence

Date: 2026-09-07

## Scope and evidence boundary

- `CONFIRMED_PUBLIC`: the product owner explicitly authorized
  `new_compact.png` (owner-supplied local reference, intentionally outside Git)
  as a visual reference for layout,
  styling, ornament, wording and atmosphere. T048 uses it only as art direction.
- `PROPOSAL`: NamazTime's minute-only presentation, compact semantic palette,
  glass depth, active-row treatment, bounded warning chip, adaptive campaign
  hierarchy and Luminous dusk background are independent project decisions.
- `CONFIRMED_RUNTIME`: screenshots in this directory were reproduced on the
  controlled Android 16 / API 36 TV emulator from the T048 debug evidence build.
- `UNKNOWN`: physical-TV viewing distance, OEM overscan/power behavior and a
  real phone-camera QR scan remain unverified. They are not promoted from the
  emulator results.

No competitor logo, QR badge, times, code or extracted resource entered the
application. The owner reference was not passed to the image-generation model
and is not packaged in the APK.

## Three deliberate 1080p reviews

| Pass | Evidence | Review outcome |
|---|---|---|
| 1 — hierarchy and time | [frame](01-pass1-hierarchy.png) | Removed visible seconds, ceiling-rounded the countdown, calmed secondary type, strengthened the active row and integrated the warning state. Compared with the [T047 frame](../t047-compact-premium/04-pass3-golden-dusk.png) and the authorized target. |
| 2 — glass and depth | [frame](02-pass2-soft-glass.png) | Reduced ordinary perimeter opacity/weight and strengthened the inset upper reflection; retained the brighter hero and deeper campaign roles. [Luminous dusk](03-pass2-luminous-dusk.png), [warning](04-warning-chip.png), [long identity](05-long-mosque-name.png) and [six-line campaign](06-six-line-campaign.png) were reviewed at the same checkpoint. |
| 3 — final balance | [frame](12-pass3-final-1080.png) | Repeated the complete API 36 capture/decode/protected-half loop after scale review and accepted the final composition. [Before/after](before-after-comparison.png) shows T047 and T048 with identical geometry inputs. |

The pass-2 and pass-3 main pixels are intentionally identical: pass 3 was an
acceptance pass after 720p/native-4K and alternate-state review, not an
unrecorded style change.

## Original Luminous dusk asset

The eight pre-existing built-ins were reviewed first. Golden dusk was the best
available baseline but did not supply the lighter slate/rose/amber sky and calm
right-side negative space requested for this optional direction. Luminous dusk
was therefore created with the repository's approved image-generation workflow
as a selectable, non-default ninth built-in.

Generation prompt (no referenced image input):

> Create an original, photorealistic cinematic 16:9 Android TV background for
> a mosque prayer-time display. Composition: a graceful generic mosque
> silhouette occupying the left third, with domes and minarets reflected in
> calm water; a wide luminous dusk sky shifting through deep navy, slate blue,
> muted rose and warm amber near the horizon; the entire right half must be
> calmer negative space for translucent UI panels and high contrast.
> Sophisticated realistic editorial photography, atmospheric depth,
> restrained warm light, smooth tonal transitions, crisp enough at 4K but no
> busy detail behind right-side text. No text, logo, QR code, interface,
> watermark, people, recognizable mosque, or copying/recreation of an existing
> reference image.

The generator returned a 1672×941 RGB PNG outside the repository. Its SHA-256
is `d7f83181e408b85e01cbf22e08fedc2e1473f4d2b4340ea49841e38202245b38`.
FFmpeg 6.1.1 converted it deterministically with libwebp quality 88,
compression level 6 and the picture preset. The packaged 1672×941 WebP is
`apps/tv-android/src/main/res/drawable-nodpi/tv_background_luminous_dusk.webp`,
SHA-256 `204bdd88a863f06eb9417908c8bf3f0a3c00b73498e407494f82456aa5fb9989`.
Only the WebP is committed.

## Adaptive, QR and isolation evidence

- [720p](07-compact-720p.png) and [maximum 720p copy](08-maximum-copy-720p.png)
  retain readable complete text.
- [Native 3840×2160](09-compact-native-4k.png) and
  [native-4K Luminous dusk](10-luminous-native-4k.png) came from a real
  secondary Presentation at density 480; the runner rejects a clamped or
  upscaled capture.
- [Blue hour](11-blue-hour.png) confirms the compact tokens remain legible on a
  cooler existing background.
- The final 720p/1080p/native-4K matrix contains 72 frames. All 60 QR-bearing
  cases decode directly and after 0.55 px Gaussian blur; all 60 compact cases
  preserve an exactly unchanged left half. The smallest foreground left edge
  across retention phases is 50.15625% of the full viewport.
- The current [STANDARD capture](regression-standard-after.png) differs from
  T047's fixed-clock STANDARD baseline by 0 RGB pixels (`bbox=null`, maximum
  channel delta 0). STANDARD production UI files were not edited.
- The signed Ulyanovsk snapshot remains byte-identical at SHA-256
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

## Signed pilot and in-place upgrade

- `CONFIRMED_RUNTIME`: `adb install -r` upgraded the existing
  `ru.namaztime.tv` package from `0.6.2-pilot.1`/code 9 to
  `0.6.3-pilot.1`/code 10 without uninstall or data clearing. The package
  first-install timestamp remained `2026-08-29 10:32:25`.
- [The first post-upgrade frame](13-signed-pilot-post-upgrade-retained-standard.png)
  retained the installed package's STANDARD mode, Golden Dusk, Iqamah rows,
  Ulyanovsk identity, long English campaign and QR payload. This is retention
  evidence, not a claim that STANDARD was redesigned.
- The operator then selected RIGHT_SIDE_COMPACT with the D-pad and restored
  Golden Dusk after deliberately exercising Blue Hour. The
  [signed compact acceptance frame](14-signed-pilot-after-code10.png) shows
  minute-only `15:28` and ceiling-rounded `01:49`; a later accessibility dump
  paired visible `01:46` with exact `01:45:42`, and visible `15:31` with exact
  `15:31:18`.
- The signed-frame QR decoded as `https://example.org/donate` both directly and
  after 0.55 px Gaussian blur. The main activity was resumed and the live log
  contained no `FATAL EXCEPTION`.
- The clean artifact is bound to implementation commit `4f5b158`, version 10,
  the pinned certificate and the unchanged signed snapshot. Debug-only evidence
  and development repository types are absent; the Luminous Dusk resource is
  present. See the machine-readable upgrade, isolation and handover records.

Handover directory (outside Git):
`/home/andy/github.com/andy-ahmedov/namaztime-artifacts/android/namaztime-0.6.3-pilot.1-code10-4f5b158df8e5/`.

Machine-readable capture and final gate summaries accompany this README.

The first full `make test` run correctly failed three assertions: the explicit
build-identity test still expected code 9, and the new background had been
inserted between Golden dusk and Blue hour, changing an existing D-pad
transition in two parameterized cases. Root-cause correction updated the
intentional code-10 expectation and appended Luminous dusk after all existing
built-ins. Targeted tests then passed, followed by a successful fresh full gate.

## Reproduction

```bash
GRADLE_USER_HOME=/tmp/namaz-time-gradle \
  ./gradlew :apps:tv-android:assembleDebug --no-daemon
adb install -r apps/tv-android/build/outputs/apk/debug/tv-android-debug.apk

make test-android-t048-emulator \
  T048_EVIDENCE_PYTHON=/tmp/namaz-t048-tools/bin/python \
  T048_EVIDENCE_ARGS='--output /tmp/namaz-t048-final-1080 --profiles 1080p'

adb shell settings put global overlay_display_devices 3840x2160/480
adb shell dumpsys display
adb shell dumpsys SurfaceFlinger --display-id
make test-android-t048-emulator \
  T048_EVIDENCE_PYTHON=/tmp/namaz-t048-tools/bin/python \
  T048_EVIDENCE_ARGS='--output /tmp/namaz-t048-final-4k --profiles 4k --presentation-display <logical-id> --capture-display <surfaceflinger-id>'
adb shell settings delete global overlay_display_devices
```

The [gate record](gates.json) contains the completed commands. PostgreSQL is not
required for this compact-only visual task under `TEST_STRATEGY.md`.
