# T047 — compact premium visual evidence

`PROPOSAL`: T047 gives RIGHT_SIDE_COMPACT a coherent luminous dark visual
system while retaining T046's centered header, left schedule, right next/date
stack and wide lower campaign. [Before/after comparison](before-after-comparison.png)
shows only NamazTime synthetic data. [Final Golden Dusk](04-pass3-golden-dusk.png),
[Blue Hour](05-blue-hour.png) and [Night Minaret](06-night-minaret.png) show the
same treatment on different selected images.

## Basis and baseline

`CONFIRMED_PUBLIC`: the owner explicitly authorized `new_compact.png` as the
T047 visual target for luminance, glass, hierarchy, accents, borders and decor.
Its SHA-256 is recorded in [capture-build.json](capture-build.json). The reference
stays local and is not packaged or committed. No competitor branding, sample
mosque/time, QR logo, APK code or extracted resources were used.

Baseline: clean main/origin `6e86d616da9785fcab7d5da2fb35c8af22f359ef`, T046
complete, version 0.6.1/code 8, CI 34037688038 successful on the same SHA.
The installed T046 evidence APK was pulled before changes; its pre-commit
identity is recorded separately from the clean source baseline.

## Three deliberate visual passes

All passes are `CONFIRMED_RUNTIME` captures on the API 36 TV emulator at
1920×1080 / density 320. Full screenshots are unmodified captures; crops are
right-half analysis derivatives. Three local side-by-side boards included the
owner target, T046 and the current pass; they remain outside Git because the
reference image is not a distributable app asset.

1. [Surface/background pass](02-pass1-surfaces.png), [crop](pass1-crop.png):
   replaced almost opaque flat navy (91–93% alpha) with a vertical graphite
   glass gradient (72% top / 82% bottom), neutral outline and inset reflection.
   The selected photograph became visible through cards; text and accents
   separated from the dark foreground. Typography was still too modest.
2. [Hierarchy/active pass](03-pass2-hierarchy.png), [crop](pass2-crop.png):
   strengthened mosque identity, adhan numbers and prayer/calendar/crescent
   icons. Larger semibold clock/countdown hours and minutes lead smaller
   seconds, with complete strings preserved. A warm gradient, luminous outline
   and narrow leading marker identify the next row. Long-name testing caught
   Settings clearance and extreme-copy height constraints, which were fixed.
3. [Campaign/decor/final pass](04-pass3-golden-dusk.png), [crop](pass3-crop.png):
   enlarged campaign kind/title/message, refined filled champagne diamonds and
   made the original geometric ornament more visible. A white-custom-image
   contrast test exposed excessive transparency; localized right scrim and
   active fill were balanced while the photographic left stayed brighter.
   The final judgment is closer to the target's luminous glass and TV hierarchy,
   without claiming identical photography or owner/physical-TV acceptance.

## Final visual system and geometry

The single selected-image renderer receives an explicit compact option only
on the public compact display. Global navy scrim is 12%; additional localized
scrim fades from transparent at x=35% to 54% at x=50% and stays constant behind
the rail. Settings navigation restores the original scrim, as does STANDARD.
Custom image resolution/fallback is unchanged. No blur, replacement image or
global brightness/saturation filter is used.

[Measured luminance](luminance-analysis.json) on Golden Dusk increased 30% across
the left half; three small text-free glass samples increased 38–86%. These are
linear sRGB image measurements, not physical display luminance or a subjective
quality score. The final modeled white-image contrast is >=4.5:1 for header,
primary/secondary/accent glass text and active-row primary text. Real-panel
contrast and viewing-distance acceptance remain `UNKNOWN`.

At normalized 960×540, the unshifted native test fixture measures:

| Block | Bounds |
|---|---|
| Rail | (484,26)..(910,504) |
| Header | (484,26)..(910,108) |
| Schedule | (484,116)..(693,356) |
| Next | (701,116)..(910,248.5) |
| Date/clock | (701,254.5)..(910,356) |
| Campaign | (484,364)..(910,504) |
| QR | (496,378)..(608,490) |

[Native bounds](native-card-bounds.json) also record 720p and 4K. T046 rail,
column widths, gaps, right safe inset and retention budget are preserved.
Header gains 8 dp; ordinary campaign text changes its measured height by 3 dp.
Allowed extreme copy expands the campaign through the existing fit policy.

Nominal 540 dp-height type sizes: mosque 34 semibold with Settings clearance;
pill 15; heading 18; names 18 and OFF adhan 24 semibold; next label/name/countdown
17/32/50; date/weekday/clock 15/13/48; campaign kind/title/message 24/20/16.
Long prose uses 12 sp (previously 10); signed-pilot visual review exposed the
old automatic reduction despite available space, and three native-density
regressions now cover the larger paragraph. Actual font metrics fit long labels;
160-character titles and six-line messages
retain the previous smaller limits. All six rows, distinct Iqamah values and
Jumu'ah sessions remain visible. Tabular numbers retain every accessible digit.

## Verification and reproduction

[Gate record](gates.json): 504 Android tests, no failures/errors/skips;
`make docs-check test lint test-android-all`, `go test -race ./...`,
`make security-go` and full-history `make secret-scan` pass. Release DEX excludes
all nine [debug-only types](release-debug-isolation.json). PostgreSQL/restore
was not rerun: no backend/schema/provider/publication change and the repository
policy does not mandate that integration gate for every visual-only task.

[Pixel comparisons](other-screen-regression.json) report zero changed RGB
pixels in STANDARD, Donation and Appearance/QR/Iqamah Settings using identical
fixed-clock inputs. Both original frames for each comparison are stored here.
The app-shell test independently exercises background restoration through real
navigation. Shared whole-view QR tests decode three payload lengths on
STANDARD/compact/Donation/constrained preview at all three densities and assert
exact equality to the unaltered integer raster. No badge or quiet-zone overlay
is introduced.

Controlled capture command (requires Pillow and zxing-cpp):

```sh
make test-android-t047-emulator \
  T047_EVIDENCE_PYTHON=/tmp/t045-tools/bin/python \
  T047_EVIDENCE_ARGS='--output /tmp/t047-final-runtime --profiles 720p 1080p'
adb shell settings put global overlay_display_devices 3840x2160/480
adb shell dumpsys display
adb shell dumpsys SurfaceFlinger --display-id
make test-android-t047-emulator \
  T047_EVIDENCE_PYTHON=/tmp/t045-tools/bin/python \
  T047_EVIDENCE_ARGS='--output /tmp/t047-final-runtime-4k --profiles 4k --presentation-display 8 --capture-display 11529215050034289252'
adb shell settings delete global overlay_display_devices
```

Display IDs must be rediscovered on another session. 4K uses an actual
3840×2160 secondary Presentation; the capture rejects clamped/upscaled frames.
The final [runtime ledger](runtime-decode-and-geometry.json) contains 66 original
frames: 54/54 exact QR decodes, 54/54 bounded blur decodes and 54/54 protected
left-half comparisons. Minimum foreground-free left is 50.15625% across all six
retention phases. [Native 4K](11-compact-native-4k.png) is 3840×2160 at density 480.
The overlay was removed after capture; primary 1920×1080/density 320 restored.
The runner restores primary wm size/density. Extra fixtures are debug-only,
fixed-clock, synthetic and do not mutate device preferences or Room. An initial
maximum-title fixture accidentally contained 162 characters and was correctly
rejected; the final fixture is exactly 160 and its QR is checked.

## Invariants, rollback and limits

PrayerTimeEngine, source/provenance, Iqamah visibility/overrides, city setup,
registry, T038 and approval/publication are unchanged. No Room/preference or
permission migration. Ulyanovsk snapshot remains byte-for-byte:
`ulyanovsk-second-cathedral-2026-pilot-local-v2`, SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

Version advances to 0.6.2-pilot.1/code 9 with the same application ID and signing
identity under ADR 0017. Select STANDARD for immediate visual rollback while
preserving settings. A code rollback must use the retained signing key and a
new monotonic versionCode; do not uninstall or attempt a code downgrade.

`UNKNOWN`: physical-TV viewing distance, camera QR scanning, panel contrast,
OEM overscan/boot and long-duration retention behavior. Physical matrix,
seven-day offline/4K memory soak, offline key backups and T038 remain deferred.
`PHYSICAL_QR_RETEST_REQUIRED` remains in force. No push or PR is authorized.

## Signed handover and local completion

`CONFIRMED_RUNTIME`: the final clean signed APK is
`namaztime-0.6.2-pilot.1-code9-1a557d3a4bae.apk`, stored outside Git at
`../namaztime-artifacts/android/namaztime-0.6.2-pilot.1-code9-1a557d3a4bae/`.
[Canonical manifest](handover-manifest.json) binds the APK to clean commit
`1a557d3a4bae`, version/code, retained certificate and unchanged snapshot.
The package, certificate, four authenticated assets and checksum checks pass.
The installed APK was pulled back and its SHA-256 equals the handover manifest.

[Before code 8](16-signed-pilot-before-code8.png) and
[final code 9](17-signed-pilot-after-code9.png) retain the six approved adhan
values, mosque/locality, Golden Dusk, compact preference, Iqamah OFF and local
campaign/QR. The QR still decodes to the same synthetic example.org destination.
[Upgrade record](pilot-upgrade.json) preserves the original first-install time.
Code 8 was upgraded in place to the initial internal code-9 review candidate;
the final paragraph refinement was then reinstalled as code 9 during this same
local QA session. The current handover is the **1a557d3a4bae** bundle, which
supersedes the c4eae93 review candidate. No uninstall, data wipe or signing change.
MainActivity is resumed, with no live-process AndroidRuntime fatal error.

Implementation checkpoints: `c4eae93` (compact visual system/evidence) and
`1a557d3` (long-paragraph refinement/regression). Final handover/status is a
separate documentation commit. T047 is DONE locally; no push/PR was performed.
