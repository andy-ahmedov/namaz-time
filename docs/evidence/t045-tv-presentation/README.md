# T045 — QR and schedule presentation evidence

Status: implementation and repository gates verified; clean signed handover
packaging is the remaining local step. Physical scan status is
`PHYSICAL_QR_RETEST_REQUIRED`.

## Scope and baseline

- Baseline: fetched `origin/main` and clean worktree at
  `b5bee5ce330801178902867f0faa3258810a56c6`; CI run
  [34030075138](https://github.com/andy-ahmedov/namaz-time/actions/runs/34030075138)
  completed successfully at that exact commit.
- Version advances from 0.5.2/code 6 to 0.6.0/code 7, pilot sequence 1.
- `PROPOSAL`: presentation-only changes; source/authority, registry, T038,
  city selection, approval, Room schema and authenticated snapshot stay intact.
- `UNKNOWN`: why the owner's physical TV failed to scan the previous QR.
  Neither local decode nor emulator output proves a physical camera outcome.

## QR findings and correction

The prior renderer placed a decorative square over 30% of QR width (9% of
area), clipped the square surface with rounded corners, and resampled an
already rasterized 256 px QR. Error correction H did not establish that this
would scan on a physical TV. A regression test reproduced nonuniform modules.
A native rendered-view test also caught rescaling when the parent allocated
less space than the nominal QR size.

`PROPOSAL`: retain native ZXing modules and error correction H, with at least
four quiet-zone modules; rasterize directly at the actual allocated pixel size
using integer module pitch and centered white remainder. No module/quiet-zone
overlay, rounded clipping, filtering or image rescaling remains. External corner
decoration stays. STANDARD, compact, Donation and Settings preview use exactly
one `ReferenceQrCode` primitive. The Settings preview is larger (132/174 dp).
Insufficient space renders a bounded message instead of crashing the schedule;
no URL rewriting/shortener or network dependency is added.

`CONFIRMED_RUNTIME` (local native graphics): `RenderedQrTest` draws the complete
composed View into a native Canvas, samples the final image region (including
potential sibling overlays), decodes exact HTTPS payloads and compares every
pixel with the integer-module raster. STANDARD, compact, Donation and a
parent-constrained preview pass at 720p, 1080p and 4K density profiles. The
short/medium/long synthetic targets are retained in the test and JSON evidence.
An arbitrary 150 px preview failed long-target decoding; the tested constrained
preview and actual 720p Settings preview now use 174 px.

`CONFIRMED_RUNTIME` (emulator): 50/50 full screenshot QR decodes, also 50/50 after
bounded Gaussian blur with radius 0.55 physical pixels. This is an exact local
screen test, not a model of every camera, perspective, glare or TV picture mode.
For the 159-character long HTTPS target, measured module pitch on STANDARD /
Donation is 2 px at 720p, 3 px at 1080p and 6 px at 4K. Compact uses 2/3/7 px.
Physical size, seating-distance readability and scan success remain unproven.

## Iqamah and layout

`PROPOSAL`: `showIqamahOnSchedule` defaults true and `scheduleLayoutMode`
defaults STANDARD. Both are additive DataStore keys, independent of
`OperatorDisplayMode`. OFF removes Iqamah header, cells, spoken labels, summary
and countdown target from public schedule presentation. The remaining Adhan
column uses the whole time area. The existing `CountdownPolicy.includeIqamah`
selects visible Adhan/Jumu'ah events; both geometries consume the same
`PrayerDisplayUiState` and mosque-local clock. No PrayerTimeEngine algorithm
or prayer/source data was modified. ON restores the retained configuration.

`CONFIRMED_RUNTIME` (tests): close/reopen DataStore retains both preferences and
local Iqamah values. D-pad reaches the Iqamah switch, returns to the five editor
rows and selects either Appearance choice; returning to compact restores focus
on Settings. Native graphics tests cover all six rows, required blocks, RU/EN,
QR present/absent, Iqamah ON/OFF and all six retention phases at three densities.
The first emulator composition exposed clipped countdown digits; the native
text-layout assertion reproduced it, and adjusted card height/padding fixed it.

The compact table spans the right rail. Optional QR sits next to the next/time
stack; without QR that stack expands to the rail width. No blank QR placeholder
remains. The left rail boundary is full viewport/2 + 2 dp shift budget + 2 dp
clearance. The existing overscan-safe right inset is retained.

`CONFIRMED_RUNTIME`: 26/26 compact screenshots have **zero changed pixels in the
entire left half** compared with a separately rendered background-only frame.
Measured foreground boxes below include the exterior frame strokes, text and
focused Settings button; right/bottom bounds are exclusive.

| Frame | Foreground x range | Foreground y range | Background-only left share |
|---|---|---|---|
| 1280×720, density 160 | 645..1216 | 44..686 | 50.39% |
| 1920×1080, density 320 | 971..1824 | 66..1028 | 50.57% |
| 3840×2160, density 480 | 1936..3648 | 130..2055 | 50.42% |
| 1080p, leftmost tested retention phase | 963..1816 | 58..1020 | 50.16% |

STANDARD regression: build/install of the clean baseline in an isolated
worktree and the current debug APK used the same fixed-clock
`WatermarkEvidenceActivity` scenario. Full 1920×1080 RGB comparison found
changed pixels **only** inside QR bounds `(1384,435)..(1648,699)`; every pixel
outside that square was identical. The two full frames are screenshots 00/01.

## Controlled emulator capture

Environment: existing Android TV emulator `emulator-5554`, Android 16/API 36.
All screenshots use debug-only synthetic data and fixed mosque-local time.
The evidence activity never edits device preferences or Room. Release DEX was
checked to exclude it and the pre-existing debug synthetic setup classes.

The primary TV display has `config_maxUiWidth=1920` (queried with `cmd overlay
lookup`). A requested `wm size 3840x2160` was clamped to 1920×1080; the resulting
480-dpi frames were rejected as 4K evidence. This behavior is consistent with
[AOSP TV's UI-width limit](https://android.googlesource.com/device/google/atv/+/refs/heads/android-t-preview-1/overlay/TvFrameworkOverlay/res/values/config.xml).
For true 4K, an Android developer overlay display at 3840×2160/480 hosted the
same composables through `Presentation`. `screencap -d` captured its own
SurfaceFlinger display, with PNG dimensions verified before decoding. This
was a native 4K emulator surface, not an upscaled primary screenshot.
The temporary overlay and primary size/density overrides were removed.

Reproduce with the debug APK installed and a dedicated emulator:

```bash
python3 -m venv /tmp/t045-tools
/tmp/t045-tools/bin/pip install pillow==12.3.0 zxing-cpp==3.1.1
T045_EVIDENCE_PYTHON=/tmp/t045-tools/bin/python make test-android-t045-emulator

adb shell settings put global overlay_display_devices 3840x2160/480
adb shell cmd display get-displays --ids-only
adb shell dumpsys SurfaceFlinger --display-id
# Substitute the actual logical display and SurfaceFlinger capture IDs:
T045_EVIDENCE_PYTHON=/tmp/t045-tools/bin/python \
T045_EVIDENCE_ARGS='--output artifacts/t045-4k --profiles 4k --presentation-display 3 --capture-display 11529215048753001239' \
make test-android-t045-emulator
adb shell settings delete global overlay_display_devices
```

The script asserts real PNG dimensions, exact target decode and an unchanged
left half. It records hashes, bounded-degradation results and measured bounds
in `results.json`. The checked-in `decode-and-geometry.json` combines the 61
valid frames; primary-display clamped attempts are excluded. Fourteen selected
original screenshots and `screenshots.sha256` are retained here. Full local
captures are in `/tmp/t045-runtime` (720p/1080p) and `/tmp/t045-runtime-4k`.
Screenshots 12/13 additionally record the final switch and selected layout style.

## Gates and invariant audit

`CONFIRMED_RUNTIME` (local execution, 2026-09-06):

| Command / check | Result |
|---|---|
| `make docs-check` | PASS |
| `make test` | PASS: Go/Python, 496 Android tests, build identity |
| `make lint` | PASS: docs/format, Go vet/staticcheck, Android debug lint |
| `make test-postgres` | PASS: PostgreSQL integration and isolated restore drill |
| `make test-android-all` | PASS: strict dependency verification, unit tests, debug/release lint, APK builds and embedded identities |
| `go test -race ./...` | PASS |
| `make security-go` | PASS: no vulnerabilities found |
| `make secret-scan` | PASS: no leaks in Git history; staged scan recorded at checkpoint |
| `git diff --check` | PASS |
| Release DEX debug isolation | PASS |
| Signed assets, Room schemas, domain/provider/contracts diff | Unchanged |

Commands used `GOCACHE=/tmp/namaz-time-go-cache` and
`GRADLE_USER_HOME=/tmp/namaz-time-gradle`. Android XML results: 496 tests, zero
failures/errors/skips. Lint passed; warning-free output is not claimed.
Broad gates were rerun only after the first run found the stale version-code
expectation; once successful they were not repeated for documentation edits.

Snapshot `ulyanovsk-second-cathedral-2026-pilot-local-v2` remains byte-for-byte:

```text
78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b
```

No Room migration. Presentation rollback is Standard + Iqamah ON; code rollback
requires a newer monotonic versionCode under the same signing certificate,
without uninstalling or wiping settings. No prayer reapproval or re-signing.

`UNKNOWN`: physical QR root cause, actual TV/camera scanning and hall readability.
`PHYSICAL_QR_RETEST_REQUIRED`: follow the T045 section of
[`PILOT_SIDELOAD_RUNBOOK.md`](../../../PILOT_SIDELOAD_RUNBOOK.md) with the new APK.
`DEFERRED`: unrelated physical OEM/long-soak acceptance and existing key-backup
obligations; no T038/source expansion, push, PR or public release in this task.
