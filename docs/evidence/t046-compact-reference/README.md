# T046 — compact reference fidelity

T046 refines only RIGHT_SIDE_COMPACT. The previous composition put QR beside
next/date and a full-width prayer table below. The new composition puts the
prayer schedule in the left main column, next/date in the right main column,
and a full-width horizontal campaign below, under centered NamazTime identity.

## Basis and baseline

- `CONFIRMED_PUBLIC`: the owner supplied `new_compact.png` as the layout and
  hierarchy reference and `qemu-system-x86_64_9ziWsns3jL.png` as the before view.
  SHA-256 values are in `verification-summary.json`. The explicit T046 request
  authorizes this composition comparison. Root originals stay local/ignored;
  neither image nor extracted branding/decor/data is packaged in the app.
- `CONFIRMED_RUNTIME`: fetched HEAD and origin/main were
  `0bbe7d0be4c7d1b659f7fe6d6cfb9bd637a41fb6`; CI 34034658957 succeeded at that
  exact SHA. Only the supplied references were untracked. Baseline was
  0.6.0 / code 7. The emulator reports API 36, 1920×1080, density 320.
- `PROPOSAL`: retain NamazTime's 48 dp right overscan inset, rather than
  reference's approximate x=928 edge. Keep seconds in countdown/clock and
  use runtime mosque/campaign content. Shared state, selected background,
  approval/source semantics and QR reliability take precedence over sample
  reference content. Synthetic screenshots reuse existing T045 fixtures;
  reference timetable values are not imported into production data.

## Screenshot-driven iterations

The side-by-side comparison boards were rendered locally from the root owner
reference and actual 1920×1080 emulator captures; local boards are
`/tmp/t046-pass1/comparison.png`, `/tmp/t046-pass2/comparison.png` and
`/tmp/t046-final-comparison.png`. The installed final debug APK is identified
in `capture-build.json`; its embedded commit/dirty-state/version were verified.
Only original NamazTime screenshots are retained here.

1. `02-iteration-one.png`: the new two-column main area and lower horizontal
   campaign establish the target ordering. Compared with the owner reference,
   source-attention space shortened the main area and the next-event name
   looked too small. The unchanged shared clock includes seconds. The QR is
   unobstructed; the owner reference's center logo is deliberately excluded.
2. `03-iteration-two.png`: source attention moves into remaining bottom safe
   space, restoring main y=108..359 and campaign y=367..504. Proportional
   next-event slots strengthen the name; clock and countdown remain dominant.
   Equal main columns, centered identity and the 137 dp bottom panel now
   follow the reference hierarchy. Campaign text sits to the right of QR.
3. Final acceptance adds a real subtitle and the full matrix. The allowed
   160-character title plus six explicit subtitle lines exposed a smaller
   date/countdown box; measured typography and proportional date slots retain
   every line without changing ordinary campaign proportions.
   OFF prayer times increase from 20 to 22 sp at normalized height 540; a
   measured-font regression enforces the requested size increase. The long-copy
   state can increase campaign height; it is not represented as reference's
   short-copy geometry.

`CONFIRMED_RUNTIME`: the final screenshots visibly have the requested block
ordering, large prayer times, active warm row, prominent mosque-local clock,
full campaign text and original shared architectural watermark. See
`04-compact-target-1080p.png`, the ON/no-QR/tomorrow/English/long-copy states,
and original 720p / native 4K images. Screenshot-driven comparison is a
composition assessment, not a claim of identical assets/data or physical-TV
acceptance.

## Measured geometry

Native Compose drawing measures card bounds in pixels, retained in
`native-card-bounds.json`. Below are normalized 960×540 bounds, without
retention shift, for ordinary campaign copy at 1080p/density 320.

| Foreground block | Left, top | Right, bottom |
|---|---|---|
| Entire rail | 484, 26 | 910, 504 |
| Centered header | 484, 26 | 910, 100 |
| Prayer schedule | 484, 108 | 693, 359 |
| Next prayer | 701, 108 | 910, 246.5 |
| Date and clock | 701, 252.5 | 910, 359 |
| Campaign panel | 484, 367 | 910, 504 |
| QR image | 496, 379.5 | 608, 491.5 |

The two main columns are 209 normalized dp wide, separated by 8 dp; the
right stack gap is 6 dp. QR is normally 112 normalized dp, enlarged only when
needed to retain at least two physical pixels per module at low density.
Full retention budget remains ±2 actual dp, not ±2 normalized pixels.

`runtime-decode-and-geometry.json` records original PNG sizes/hashes, full
foreground pixel bounds, per-frame QR/blur decode and left-half differences.
The foreground mask comes from subtraction of an otherwise identical
background-only capture. Source attention, Settings and every ornament count
in this mask. The final 48-frame matrix has 42/42 exact QR decodes, 42/42
bounded-blur decodes and 42/42 unchanged-left comparisons. Minimum measured
empty-left width is 50.15625%; no foreground pixel crosses the midpoint.
Aggregates are in `verification-summary.json`. Temporary display 6 used capture
ID `11529215050152914890`; it was removed after capture and the emulator
returned to its original 1920×1080 / 320 density.

## Tests and regression evidence

- Red/green: the new composition test failed the old renderer at “schedule
  must be left of next/date”, then passed the new renderer.
- Red/green: a long accepted title overflowed the first pass, and a dense
  159-character HTTPS payload failed at 149 px on 720p. Text-measured campaign
  allocation and QR's minimum two-pixel module pitch fix both. Extending the
  title to the actual 160-character limit reproduced the constrained
  date/countdown case and verifies its adaptive fix.
- Native Compose covers RU/EN, QR present/absent, Iqamah ON/OFF, all six shift
  phases and all three density profiles. Every row stays in the rail. Next
  name cases include Аср, Фаджр · завтра, Fajr · tomorrow and Jumu'ah/Iqamah;
  session rows remain sourced from the shared projection. Text-layout
  overflow, text ordering/card containment and mosque-name/Settings glyph
  nonintersection are checked separately from outer bounds.
- T045 whole-view QR tests decode three payloads on STANDARD/compact/Donation/
  constrained Settings preview at three densities and require exact equality
  with the native integer raster. Generator, correction H, >=4-module quiet
  zone, no badge, no clipping and no filtering remain unchanged.
- `12-standard-before.png` versus `13-standard-after.png` uses identical
  fixed-clock synthetic input. RGB subtraction has **zero changed pixels**.
  `MainPrayerDisplay.kt` changes only visibility of two decorative functions;
  `QrCampaignPanel.kt` changes only visibility of the kind-resource mapping.
  Neither STANDARD layout nor shared primitive implementation changes.

## Reproduction

The explicit capture target requires a locally built/installed debug APK and
Pillow/zxing-cpp. The tools used here live in `/tmp/t045-tools`; they add no
application or Gradle dependency.

```bash
make test-android-t046-emulator \
  T046_EVIDENCE_PYTHON=/tmp/t045-tools/bin/python \
  T046_EVIDENCE_ARGS='--output /tmp/t046-runtime --profiles 720p 1080p'
```

For actual 4K, create the temporary Android developer overlay display
`3840x2160/480`, read its logical ID from `dumpsys display` and capture ID from
`dumpsys SurfaceFlinger --display-id`, then pass `--profiles 4k
--presentation-display <logical-id> --capture-display <capture-id>`.
The debug-only existing Presentation host renders at native 3840×2160;
`screencap -d` captures that display. The primary TV's 1920 px UI cap does not
constitute 4K evidence. The script rejects unexpected PNG dimensions.
Remove `overlay_display_devices` afterward; wm size/density are restored to
1920×1080/320. No wipe, provisioning, Room or pilot data is used for synthetic
capture. Evidence extras add only an optional fixed instant and subtitle in
the debug source set. They remain absent from pilot/release.

## Gates, handover and remaining work

`gates.json` records the final command outcomes: `make docs-check test lint
test-android-all`, `go test -race ./...`, `make test-postgres security-go` and
`make secret-scan` all PASS. Android has 499 tests, zero failures/errors/skips;
strict dependency verification, debug/release builds/lint/identity, Go vet and
staticcheck are included. The PostgreSQL integration/restore drill passes;
govulncheck reports no vulnerabilities. A staged secret scan covers new files
before each checkpoint in addition to the full-history scan. The actual eight
debug-only classes are absent from release DEX. Logs remain in `/tmp/t046-*`;
checksums are retained in the gate record.

Implementation checkpoint: `c878d5ec73eb8000c55221c19b482930aad5960d`.
`NAMAZTIME_PILOT_SIGNING_PROPERTIES=/home/andy/.config/namaztime/pilot-signing.properties
GRADLE_USER_HOME=/tmp/namaz-time-gradle make build-android-pilot` PASS from the
clean checkpoint. Package/identity/signature/four authenticated asset checks
and the external checksum PASS. The handover manifest is copied here as
`handover-manifest.json`; APK and original manifest/checksum stay outside Git:

```text
../namaztime-artifacts/android/namaztime-0.6.1-pilot.1-code8-c878d5ec73eb/
  namaztime-0.6.1-pilot.1-code8-c878d5ec73eb.apk
```

Version `0.6.1-pilot.1`, code `8`, application `ru.namaztime.tv`, clean pilot
build at the exact checkpoint. APK SHA-256:
`74041dbca49185827768aa3894ba80d5e9802c87a5ff76f1f7920e5b32eb0c89`.
Retained certificate SHA-256:
`da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`.

`CONFIRMED_RUNTIME`: `adb install -r` upgrades the existing signed code 7 to
code 8, without uninstall/wipe. MainActivity resumes; no AndroidRuntime errors
appear for the live process. `14-signed-pilot-before-code7.png` and
`15-signed-pilot-after-code8.png` show real approved Ulyanovsk data and retained
RIGHT_SIDE_COMPACT, Iqamah OFF, background, identity and local campaign. All
six displayed prayer times are identical. Independent decoding returns the
same synthetic example.org QR destination before/after; the full previously
stored message is now visible. `pilot-upgrade.json` records this check. The
emulator remains on the upgraded signed pilot with original display settings.

The later handover documentation checkpoint does not change the built APK's
embedded commit or manifest. No push or PR was performed.

The signed snapshot remains `ulyanovsk-second-cathedral-2026-pilot-local-v2`,
SHA-256 `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
No schedule row, approval, registry, source, city selection, T038, permission,
Room schema, operator preference semantics or debug setup isolation changes.

Rollback has no data migration: the operator can select STANDARD and keep
all existing local settings. A code rollback requires a new monotonic Android
code signed by the retained key, per ADR 0017; do not uninstall or reset data.

`UNKNOWN`: physical-TV viewing distance, camera QR acceptance, OEM overscan/
boot behavior and field visual approval. `PHYSICAL_QR_RETEST_REQUIRED` remains
from T045. Physical-device matrix, long soak, key backups and T038 remain
DEFERRED/external; emulator evidence does not complete those requirements.
