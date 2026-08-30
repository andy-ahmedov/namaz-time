# Progress Log

## Session: 2026-08-30

### Phase 1: Context, inventory, and research protocol

- **Status:** complete
- **Started:** 2026-08-30
- Actions taken:
  - Created the active long-running goal and an execution plan.
  - Read the complete `planning-with-files` and `prayer-times-provider` skill instructions.
  - Checked the initial Git status and confirmed the root APK exists.
  - Recorded the first tooling error and changed the catch-up approach from `python` to `python3`.
  - Successfully ran the session catch-up script with `python3`; it found no prior unsynchronized planning context.
  - Inventoried repository files and located the full annual/August/effective Ulyanovsk fixture chain, source adapters, contracts, and pilot-local Android assets/tests.
  - Began complete mandatory-document reading in bounded chunks; recorded that combined search output was truncated and switched to exact chunk reads.
  - Read all named planning/source/provider documents and the complete IslamApp static report in exact chunks.
  - Read the Ulyanovsk annual/August fixture READMEs and onboarding checklist, including the exact seasonal transitions and reconciliation behavior.
  - Read ADRs 0001, 0002, 0003, 0011, and 0013; extracted the source/approval/signature/local-overlay boundaries relevant to a registry.
  - Recorded the APK's SHA-256, byte size, package/version/SDK values, ZIP integrity, manifest baseline, asset counts, dependency metadata, and initial tooling inventory.
  - Read exact Architecture source-ingestion/time/failure sections, pilot Decisions, Product Requirements, API semantics, and the structured Ulyanovsk source/effective/approval fixture fields.
  - Installed official JADX 1.5.6 and apktool 3.0.3 user-locally after password-protected `sudo` made apt unavailable.
- Files created/modified:
  - `task_plan.md` (created)
  - `findings.md` (created)
  - `progress.md` (created)

### Phase 2: 1Muslim static/runtime research

- **Status:** complete pending checkpoint commit
- **Started:** 2026-08-30
- Actions taken:
  - Began package/manifest/asset/dependency inventory with Android SDK tools.
  - Decompiled to an external research cache with JADX/apktool; no extracted artifact entered the repository.
  - Characterized the global city catalog and legacy prayer SQLite databases, including complete aggregate counts and both Ulyanovsk rows.
  - Traced bundled database extraction, downloadable replacement, Retrofit timestamp/version endpoints, legacy localized city search, and stored `PrayerDays` loading.
  - Identified calculation-profile/Asr/high-latitude/offset types as a separate calculated-city path.
  - Traced current search merging: local legacy timetable + local global catalog first, GeoNames-compatible proxy only when local search is empty; mapped the different `calculated` flags and UI labels.
  - Cross-checked JADX's failed coroutine decompilation against decoded smali for location lookup and database download/extraction/activation.
  - Traced selected-city materialization through the calculated/stored branch, 366-row invariant, leap-year mapping, Room cache, timezone/DST transform, and user offset application.
  - Verified APK v2/v3 signatures and SourceStamp and independently re-dumped badging/permissions with Android build tools.
  - Recovered the exact Russia calculation profile parameters and high-latitude branches while retaining their separation from stored Ulyanovsk ID 1187.
  - Attempted an ordinary install on the running API 36 Android TV emulator. Android rejected the supplied base because required ABI/density splits are absent; no protection was bypassed.
  - Wrote the sanitized static report `ONE_MUSLIM_APK_RESEARCH.md`.
- Files created/modified:
  - `ONE_MUSLIM_APK_RESEARCH.md`
  - planning logs; no extracted APK artifact is inside the repository.

### Phase 3: Ulyanovsk independent reproduction and comparison

- **Status:** in_progress
- **Started:** 2026-08-30
- Actions taken:
  - Compared all 365 projected ID 1187 rows and six prayer fields with both the official annual CSV and approved effective signed snapshot.
  - Determined the exact field/day distributions and isolated the July Dhuhr, late-August effective Dhuhr, and July 3 Isha discrepancy clusters.

## Test Results

| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Initial worktree inspection | `git status --short --branch` | Establish clean baseline | `main...origin/main`; no changes before planning files | PASS |
| APK presence | `ls -lh 1Muslim_5.9.5.apk` | Local research input exists | 83 MiB file exists | PASS |
| Planning session catch-up | `python3 .../session-catchup.py "$(pwd)"` | Recover unsynchronized context or report none | Completed without a catch-up report | PASS |
| APK byte identity | `sha256sum`, `stat`, `unzip -t` | Stable hash/size and valid archive | SHA-256 `4fea3403...434bfb`; 86,520,143 bytes; no ZIP errors | PASS |
| Bundled DB integrity | unzip and SQLite `integrity_check` outside Git | Both packaged databases open cleanly | Both returned `ok`; schemas and aggregate counts query successfully | PASS |
| APK signature | `apksigner verify --verbose --print-certs` | Verify package authenticity metadata | v2/v3 and SourceStamp verify; one signer | PASS |
| Ordinary APK runtime install | base APK on API 36 Android TV emulator | Install only if a complete supported artifact is present | Fails closed: `INSTALL_FAILED_MISSING_SPLIT`; manifest requires ABI/density splits | BLOCKED AS EXPECTED |
| Ulyanovsk annual comparison | ID 1187 projected to 2026 vs annual CSV | 365 dates × 6 fields | 1,912 exact; 246 ±1; 0 ±2–5; 32 >5 | PASS |
| Ulyanovsk effective comparison | ID 1187 projected to 2026 vs approved snapshot | 365 dates × 6 fields | 1,901 exact; 246 ±1; 0 ±2–5; 43 >5 | PASS |

## Error Log

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-08-30 | `python: command not found` for session catch-up | 1 | Will use installed `python3` and log its result. |
| 2026-08-30 | Combined `rg`/document output truncated | 1 | Switched to smaller exact `sed` chunks; no conclusions rely on omitted output. |
| 2026-08-30 | Combined ADR plus heading-search output truncated | 1 | Selected ADRs were complete; Architecture/Decision/spec sections will be re-read directly. |
| 2026-08-30 | `apkanalyzer manifest permissions` failed through internal `aapt` | 1 | Decoded manifest output is usable; will install/locate `aapt`/`apktool`/`jadx` for independent checks. |
| 2026-08-30 | `sudo apt-get` requires an interactive password | 1 | Switched to official user-local JADX/apktool releases; no privileged install needed. |
| 2026-08-30 | JADX returned status 3 with 620 decode/decompile errors; apktool emitted unresolved-resource warnings | 1 | Usable output retained externally; material findings will be cross-checked against smali/DEX/direct database contents. |
| 2026-08-30 | Base APK install failed with `INSTALL_FAILED_MISSING_SPLIT` | 1 | Manifest requires ABI/density splits that were not supplied. Record runtime behavior as unavailable and do not bypass packaging/protection. |

## 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Phase 3: reproducible Ulyanovsk comparison after completing static research. |
| Where am I going? | APK research → Ulyanovsk comparison → Russia authority research → architecture/foundation → falsification/verification. |
| What's the goal? | Evidence-backed clean-room city/source resolution with Ulyanovsk as the first safe vertical slice. |
| What have I learned? | See `findings.md`. |
| What have I done? | Created persistent planning logs, checked baseline, and read applicable skills. |
