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

- **Status:** complete
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

- **Status:** complete
- **Started:** 2026-08-30
- Actions taken:
  - Compared all 365 projected ID 1187 rows and six prayer fields with both the official annual CSV and approved effective signed snapshot.
  - Determined the exact field/day distributions and isolated the July Dhuhr, late-August effective Dhuhr, and July 3 Isha discrepancy clusters.
  - Added a read-only external-input comparison harness, four synthetic tests, and the `make test-research` repository command.
  - Confirmed January–May is a complete 906-field exact match and disproved coordinates-only causation by comparing the materially different same-coordinate `Ульяновск 2` table.
  - Wrote `ULYANOVSK_ONE_MUSLIM_COMPARISON.md` with annual/effective per-prayer statistics and bounded date-range explanations.
- Files created/modified:
  - `ULYANOVSK_ONE_MUSLIM_COMPARISON.md`
  - `research/tools/one_muslim_ulyanovsk_compare.py`
  - `research/tools/test_one_muslim_ulyanovsk_compare.py`
  - `Makefile`

### Phase 4: Russia official-authority/source research

- **Status:** complete
- **Started:** 2026-08-30
- Actions taken:
  - Began first-party web research with DUM RT, DUM RB, CDUM, and RDUM Ulyanovsk.
  - Confirmed Татарстан's republic-wide approved calculation policy and city/district selector.
  - Identified a real Башкортостан ambiguity between DUM RB and Ufa-based CDUM publications.
  - Confirmed RDUM Ulyanovsk publishes locality-specific calendars and transition policies rather than one coordinate-free subject schedule.
  - Confirmed first-party city/district timetable interfaces for Dagestan and first-party Grozny times for Chechnya.
  - Separated Moscow federal city from Moscow Oblast and recorded competing Moscow city first-party publishers.
  - Confirmed a Saint Petersburg city schedule while withholding Leningrad Oblast/Northwest scope promotion.
  - Left Ingushetia `unknown` because no current stable first-party timetable was recovered.
  - Found DUM KBR's current times and 2026 annual-download link but retained `strong_evidence` pending explicit locality/republic scope.
  - Identified candidate official organizations for North Ossetia-Alania, Karachay-Cherkessia, and Stavropol while keeping timetable mappings `unknown`.
  - Confirmed city-scoped official schedule pages for Saratov and Orenburg, and a candidate DUM RM prayer interface for Saransk/Mordovia.
  - Recorded cross-subject DUM RA/KK organization scope without pretending it supplies a timetable.
  - Recorded Penza's parallel regional organizations as ambiguous and rejected aggregator schedules for Samara/Nizhny Novgorod as authority evidence.
  - Confirmed Astrakhan city and Narimanov District first-party schedules while retaining subject-level ambiguity due parallel organizations and locality-specific policy.
  - Recorded Volgograd's parallel organizations as ambiguous and confirmed a city-scoped Perm official prayer page.
  - Retained Rostov, Tyumen, Sverdlovsk, Kemerovo, and Khanty-Mansi timetable mappings as unknown.
  - Corroborated Татарстан's republic-wide 2014 policy decision with official printed tables and noted the site's general CC BY 4.0 statement without assuming attachment-specific license scope.
  - Completed a sanitized 31-subject registry draft: 6 `confirmed_official`, 5 `strong_evidence`, 6 `ambiguous`, and 14 `unknown`; only Ulyanovsk is marked as an existing approved pilot.
  - Compared IslamApp, 1Muslim, and the proposed NamazTime resolver across search, geocoding, policy selection, calculation, timetables, updates, offline behavior, fallback, and provenance.
  - Validated 31 unique subject codes and independently recomputed the declared status and resolver-state counts with `jq`.
  - Rechecked source volatility: DUM RT's former helper URL returns HTTP 410, so it is retained only as a historical candidate URL while the live first-party policy page remains the evidence basis.
- Files created/modified:
  - `RUSSIA_PRAYER_TIME_AUTHORITY_RESEARCH.md`
  - `research/russia-prayer-source-registry-draft.json`
  - planning logs

### Phase 5: Architecture and Ulyanovsk vertical slice

- **Status:** complete
- **Started:** 2026-08-30
- Actions taken:
  - Read the complete `test-driven-development` skill and committed to red-green-refactor for the new resolver behavior.
  - Re-read the active plan/findings, confirmed no narrower `AGENTS.md` applies under `internal`, `contracts`, or `docs`, and inspected existing domain/snapshot/publication boundaries.
  - Independently resolved the Ulyanovsk city relation through public OpenStreetMap Nominatim (`54.3150278, 48.4033730`, `RU-ULY`) so no competitor coordinate enters the foundation.
  - Completed red-green-refactor cycles for canonical city search, alias search, the approved Ulyanovsk timetable binding, resolver precedence, same-tier ambiguity, registry validation, and malformed local dates.
  - Added source-independent domain entities and a validated in-memory control-plane registry; no TV or signed snapshot contract was changed.
  - Bound the first registry entry to the exact existing Ulyanovsk source, approval, August source override, timezone, and published snapshot IDs.
  - Wrote `RUSSIA_CITY_SOURCE_ARCHITECTURE.md` and accepted ADR 0015, explicitly separating geometry from authority and preserving the signed offline pipeline.
  - Extended the resolver result with the actual retained `SourceOverride` metadata, rejected provider kinds outside the six repository-approved kinds, and made validated input/output slices defensive copies.
  - Began a second independent falsification review using the code-review skills as checklists without spawning a sub-agent.
  - Recomputed both full-year comparisons with the committed harness and a separate one-off implementation; all field buckets, exact-day counts, within-one-day counts, maxima, and material ranges agree.
  - Verified the late-August Dhuhr explanation directly against the annual CSV, monthly CSV, effective snapshot, effective policy, and reconciliation ledger.
  - Found and corrected an authority-provenance overclaim in the initial registry seed: annual RDUM remains `CONFIRMED_PUBLIC`, while the August source's legal publisher identity remains `UNKNOWN` exactly as its source record states.
  - Clarified that registry approval/snapshot IDs are references and cannot replace approval-receipt or Ed25519 verification.
- Files created/modified:
  - `internal/domain/city_source.go`
  - `internal/domain/doc.go`
  - `internal/registry/registry.go`
  - `internal/registry/registry_test.go`
  - `RUSSIA_CITY_SOURCE_ARCHITECTURE.md`
  - `docs/adr/0015-city-region-authority-source-policy-resolution.md`
  - `docs/adr/README.md`

### Phase 6: Falsification review and delivery

- **Status:** complete
- **Started:** 2026-08-30
- Actions taken:
  - Re-read the requirements, plan, findings, all four research/architecture reports, final implementation diff, source records, effective policy, snapshot and publication receipt.
  - Recomputed annual/effective statistics with both the committed harness and an independent one-off implementation.
  - Challenged correlation-as-provenance, regional-site-as-subject-authority, and registry-reference-as-authentication assumptions; retained `INFERENCE`/`UNKNOWN` boundaries and corrected the composite authority overclaim.
  - Updated `PLANS.md` and `CODEX_TASKS.md` with T034 evidence and T035–T039 concrete next tasks.
  - Ran all stable repository commands plus race, vulnerability and secret scans.
  - Audited tracked extensions, paths, file sizes/types, APK ignore/hash, snapshot identity, registry counts and the complete T034 diff.
  - Prepared the fifth and final local checkpoint; no push or PR was created.

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
| Research harness tests | `make test-research` | Projection, validation, buckets, runs, snapshot parsing | 4 tests pass | PASS |
| Registry structure | `jq` recompute counts/unique subject codes | 31 unique codes; declared counts match entries | 31 unique; 6 confirmed, 5 strong, 6 ambiguous, 14 unknown | PASS |
| Documentation policy check | Temporarily exclude local ignored APK, then `make docs-check` | No tracked/research artifact or documentation policy violation | `docs-check: PASS`; APK restored | PASS |
| Registry/domain narrow tests | `go test ./internal/domain ./internal/registry` | Existing domain and new registry tests pass | PASS | PASS |
| Repository Go tests | `go test ./...` | All Go packages pass | All packages pass | PASS |
| Go static analysis | `go tool staticcheck ./...` | No findings | No output | PASS |
| Checkpoint 4 docs/research checks | Temporarily exclude local ignored APK; `make docs-check`; `make test-research` | Docs pass; four research tests pass | PASS; 4/4 | PASS |
| Independent Ulyanovsk recomputation | Separate one-off SQLite/CSV/JSON comparison, no harness import | Match reported annual/effective buckets and ranges | Exact match to report | PASS |
| Full repository tests | `make test` with ignored research APK temporarily excluded | Docs, Go, research and Android unit suites pass | Exit 0; Android `BUILD SUCCESSFUL` | PASS |
| Full repository lint | `make lint` with ignored research APK temporarily excluded | Formatting, vet, staticcheck and Android lint pass | Exit 0; Android `BUILD SUCCESSFUL` | PASS |
| Go race suite | `make test-go-race` | All Go packages pass under race detector | Exit 0 | PASS |
| Go vulnerability scan | `make security-go` | No known reachable vulnerabilities | `No vulnerabilities found.` | PASS |
| Git history secret scan | `make secret-scan` | No leaks | 73 commits / ~8.07 MB; no leaks | PASS |
| Clean-room Git audit | tracked extensions/paths, MIME/size, APK ignore/hash, snapshot diff/hash, registry counts | No prohibited artifact; existing signed pilot unchanged | PASS | PASS |

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
| 2026-08-30 | Initial synthetic bucket expectation counted four `>5` fields instead of five | 1 | Audited fixture deltas, added the omitted Asr +6 material run, and reran successfully. |
| 2026-08-30 | Initial registry validation queried absent `.subjects` instead of `.entries` | 1 | Corrected the read-only `jq` query; structure and counts pass. |
| 2026-08-30 | DUM RT's former prayer-time helper URL returned HTTP 410 | 1 | Marked transport as volatile; retained the live official policy decision as evidence and deferred transport onboarding. |
| 2026-08-30 | One falsification `awk` projection selected sunrise/zenith columns instead of zenith/Dhuhr | 1 | Read the exact CSV header and verified the intended Dhuhr values through reconciliation rows and named JSON/CSV fields; no conclusion used the incorrect projection. |
| 2026-08-30 | Initial executable seed assigned the whole composite source to RDUM | 1 | Checked both source records and the signed snapshot, then modeled multiple component authority references with exact `CONFIRMED_PUBLIC`/`UNKNOWN` evidence labels. |
| 2026-08-30 | First clean-room `rg` audit treated documented tool/table names and a synthetic test schema as forbidden artifacts | 1 | Replaced the overbroad content pattern with tracked-extension, path, size, MIME, Git diff and explicit extracted-artifact audits; documented terms are allowed sanitized evidence. |

## Session: 2026-08-30 — Phase 5 continuation after T034

### T035: canonical Russia city catalog

- **Status:** in progress
- Read the applicable `planning-with-files`, `prayer-times-provider`, `test-driven-development` and `verification-before-completion` instructions.
- Recovered the completed T034 planning context with `python3` and recorded the missing `python` command as a resolved tooling error.
- Restated scope and correctness-sensitive unknowns; T038 remains explicitly deferred.
- Read the root invariants, current architecture and ADR 0015; retained strict separation of geography, authority, source, approval and signed publication.
- A combined mandatory-document read was truncated, so no omitted section is treated as read; subsequent reads use bounded exact chunks.
- Read the complete current city/source architecture and Russia authority report, plus the T034–T039 task contracts from `CODEX_TASKS.md`.
- Read provider/source rules, partnership checklist and the complete current data model; extracted immutable revision, verified-reference and append-only audit requirements for T035/T036.
- Read the complete test strategy, including existing PostgreSQL migration/rollback gates and release/security evidence requirements.
- Inspected the T034 in-memory registry and existing PostgreSQL/API layout; confirmed `NewPilotRegistry` is the executable hard-coded seed that T037 must remove.
- Compared official OSM and GeoNames license/update documentation. GeoNames CC BY 4.0 is the provisional T035 choice; real pinned input and Russia coverage remain to be measured.
- Downloaded and hashed the live GeoNames RU and alternate-name archives to a temporary directory outside Git; sampled Ulyanovsk and measured preliminary populated-place coverage.
- Downloaded/hash-pinned GeoNames admin1 metadata, enumerated its 83-subject RU coverage and verified a real multi-subject duplicate-name case (`Киров`).
- Inspected current migration, API and PostgreSQL gate conventions to choose a compatible T036 persistence seam.
- T035 RED: added catalog/import/search/revision/diff fail-closed tests before production code. `go test ./internal/geography` fails on the deliberately absent API (`RegionMapping`, `Import`, `StableCityID`, etc.), confirming the new behavior is not pre-existing.
- T035 first GREEN attempt: implementation compiled; two assertions failed. The checksum test used NUL bytes instead of a valid wrong hex digest, and semantic timezone rejection was classified as schema drift. Logged and correcting each boundary once.

### Error log (continuation)

| Error | Attempt | Resolution |
|---|---:|---|
| Checksum mismatch fixture produced a malformed digest | 1 | Use 64 ASCII zeroes so the manifest is valid and byte verification reaches `ErrArtifactMismatch`. |
| Invalid IANA timezone surfaced as `ErrSchemaDrift` | 1 | Preserve `ErrInvalidCatalog` for semantic row validation while retaining schema drift for shape/admin changes. |

- T035 GREEN (core): `go test ./internal/geography` passes after implementing checksum-verified ZIP/plain ingestion, stable IDs, RU canonicalization, aliases, timezone/provenance, exact duplicate-safe search, deterministic content revisions/diffs and typed fail-closed errors.
- T035 CLI RED: added a file-based importer/diff test. `go test ./cmd/citycatalog` fails on the deliberately absent `CacheFile`, mapping-file schema and `run` command API.
- T035 CLI GREEN: `go test ./internal/geography ./cmd/citycatalog` passes. The command strictly decodes manifest/mapping/catalog JSON, reads bounded non-symlink files, verifies pinned bytes, emits atomic deterministic catalog bytes, validates previous-catalog hashes and writes an auditable diff.
- Ran the importer twice against the exact pinned 2026-08-29 GeoNames archives outside Git. Both 96 MB generated outputs are byte-identical; the second diff is empty. Narrow geography/CLI/registry tests pass.
- Added the GeoNames/OSM license comparison, pinned coverage, canonicalization, search and rollback/import contract to `GEOGRAPHIC_CITY_CATALOG.md`; updated architecture/ADR 0015 without assigning prayer authority.
- T035 checkpoint gate attempt 1 stopped at `make docs-check` because the ignored root research APK is present. The unchanged gate will be rerun with that exact file temporarily moved under an automatic restoration trap.

| Checkpoint error | Attempt | Resolution |
|---|---:|---|
| `docs-check` rejects ignored `1Muslim_5.9.5.apk` anywhere in the workspace | 1 | Temporarily move the exact APK outside the workspace, run the unchanged gate, restore it, and verify SHA-256/Git status. |
| Planning-log patch used a stale table anchor | 1 | Read the current continuation tail and appended the log under the active T035 section. |
| `docs-check` then rejected the ignored 96 MB generated catalog | 2 | The APK restoration trap worked and its SHA-256 is unchanged. Retry with both ignored research/generated artifacts temporarily outside the workspace; tracked importer inputs remain fully checked. |
| T035 staged `git diff --check` found Markdown hard-break trailing spaces | 1 | Replaced hard-break spaces with ordinary blank-line separation and reran the staged gate before commit. |

- T035 checkpoint gate passed after temporarily excluding only ignored local inputs: `make docs-check`, focused Go tests, `make format-check`, `go vet ./...`, and `staticcheck ./...`. The root APK was restored with SHA-256 `4fea3403...434bfb`.
- **T035 status:** complete; T036 is now in progress. T038 is explicitly deferred.

### T036: persisted executable policy registry

- **Status:** in progress
- T036 service RED: added activation/fail-closed/rollback tests before production code. `go test ./internal/registry` fails on the deliberately absent revision store, verified-reference evidence, service and source-status APIs.
- First T036 GREEN compile found one missing brace in the new overlap helper before tests could run; corrected the local syntax once and retained the same test contract.
- T036 service GREEN: `go test ./internal/registry` passes verified activation, research/stale/missing proof/mismatch/ambiguity rejection and rollback re-verification.
- T036 PostgreSQL RED: added the real revision/activation/search/rollback/up-down/append-only test and wired it into `test-postgres`; integration-tag compilation fails on the deliberately absent `NewPostgresRevisionStore`.
- T036 PostgreSQL GREEN: migration v6 and `PostgresRevisionStore` now persist/load all registry entities, aliases/junctions, verified references, active pointer and append-only audit. Real `make test-postgres` passes devices/registry/API integration plus v6 backup/restore (`postgres-restore-drill: PASS`).
- Added an explicit schema-v1 revision contract, defensive fail-closed relation loading, and restore-drill assertions that the runtime role can read but cannot write registry state.
- Documented the persisted lifecycle, verification boundary, append-only/rollback semantics and database-role split in `PERSISTED_POLICY_REGISTRY.md` and related architecture/model/test docs.
- T036 fresh checkpoint passes `go test ./...`, `make test-postgres` (including v6 backup/restore), `make docs-check`, `make format-check`, `make lint-go`, `go test -race ./...`, and `make security-go`; `govulncheck` reports no vulnerabilities. The ignored APK was restored with unchanged SHA-256 `4fea3403ec5d288163fbe649220bda86ccaaad3b8d76ba57227d7b2aea434bfb`.

### T037: persisted Ulyanovsk end-to-end

- **Status:** complete; local commit `804c677`.
- Removed `NewPilotRegistry`; added strict reviewed bindings targeting the exact T035 catalog revision/hash and retaining both Ulyanovsk authority evidence identities.
- Added a pinned artifact manifest and verifier that cryptographically rechecks the real mosque approval, approval trust, publication trust transition/environment separation, receipt/signature and exact signed snapshot identity.
- Added `registryctl validate/apply`; the full ignored 166,557-city catalog composes to stable registry SHA-256 `0114c41e6e7d58fc5886b5a4363567348a0c5bb56835e5d9d830dc0b4fef8e5f`.
- Added an opt-in PostgreSQL registry reader and authenticated mosque-scoped city search/policy resolution API plus OpenAPI 0.7.0 contract.
- Added real PostgreSQL/HTTP coverage for Ulyanovsk resolution, duplicate/unknown city non-selection, successor activation and rollback.
- Pinned the existing USB pilot snapshot ID/signing key/signature and raw SHA-256 `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`; bytes remain unchanged.
- Checkpoint gates pass: `go test ./...`, full catalog `registryctl validate`, `make docs-check`, `make test-contracts`, `make test-postgres` with restore, and `make test-android-unit`. The ignored APK/catalog were restored; APK SHA-256 remains `4fea3403...434bfb`.

### T037 error log

| Error | Attempt | Resolution |
|---|---:|---|
| Registry integration test acquired an import cycle after devices began importing registry | 1 | Converted the integration test to external `registry_test` package and supplied isolated exported-API fixtures. |
| New UTC assertions used `time.Equal(value.UTC())`, which cannot distinguish location representation | 1 | Replaced with canonical `time.UTC` location validation and added non-UTC revision/approval/snapshot tests. |
| `registryctl validate` initially checked policy approvals but not override/profile references | 1 | Added a red missing-override test, then deterministic deduplicated verification for every referenced approval and published snapshot. |

### T039: explainable operator workflow

- **Status:** complete pending checkpoint commit.
- Added revision-specific assessment with deterministic precedence and stable
  `resolved`/`ambiguous`/`stale`/`unavailable` reasons plus per-option authority,
  evidence, scope, freshness/range, payload and blocked reason.
- Added authenticated `prayer-policy-options` and explicit
  `prayer-policy-binding-requests` endpoints under OpenAPI 0.8.0. The latter
  writes only an idempotent append-only `pending_review` handoff and never
  activates/publishes/assigns.
- PostgreSQL migration v7 adds `registry_binding_requests`, preserves audit
  actor/reason/request/time and selection SHA-256, serializes with the registry
  lifecycle lock, and rolls back to v6 without touching active state or signed
  snapshots.
- Unit coverage includes independent parallel-authority options, lower-tier
  precedence, research/stale/unavailable/expired sources, policy/payload range,
  seasonal override range, missing policy, active/new-request rejection and
  exact retry after state change.
- Real PostgreSQL/HTTP coverage proves staged ambiguity selection, idempotent
  replay, append-only guards, stale rejection, unchanged active revision and a
  least-privilege API role that may insert only the handoff—not revisions or
  the active pointer.
- Checkpoint gates pass: `make test`, full-catalog `registryctl validate`,
  `make test-postgres` including v7 restore, `go test -race ./...`,
  `make security-go` (no vulnerabilities), `make lint`, and a clean
  working-tree gitleaks scan. The local T039 commit is the remaining checkpoint
  step.

### T039 error/falsification log

| Finding | Attempt | Resolution |
|---|---:|---|
| Exact idempotent replay was initially assessed before reading retained evidence, so later activation could change the retry response | 1 | Added a scoped persisted-retry read before mutable registry assessment; changed input still conflicts. |
| Binding persistence could race staged→active between assessment and insert | 1 | Shared the registry PostgreSQL lifecycle advisory lock and rechecked state inside the insert transaction. |
| A seasonal override source was initially checked outside its effective range | 1 | Base source remains checked; override source is checked only in-range, with inside/outside regression tests. |
| Assessment initially required a mosque ID on regional timetables | 1 | Matched resolver semantics: blank timetable mosque means regional payload, while explicit nonmatching mosque still blocks. |
| Gitleaks classified a high-entropy synthetic idempotency value as a generic API key | 1 | Replaced it with an obvious low-entropy test-only value and reran the working-tree scan with no findings. |

## 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Complete: all five checkpoints are ready locally; final handoff remains. |
| Where am I going? | APK research → Ulyanovsk comparison → Russia authority research → architecture/foundation → falsification/verification. |
| What's the goal? | Evidence-backed clean-room city/source resolution with Ulyanovsk as the first safe vertical slice. |
| What have I learned? | See `findings.md`. |
| What have I done? | Completed static/public research, independent comparison, registry architecture/foundation, falsification, full verification and clean-room audit. |
