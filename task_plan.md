# Task Plan: 1Muslim clean-room research and Russia city/source foundation

## Goal

Determine, with clean-room evidence, how 1Muslim 5.9.5 resolves prayer times (especially Ulyanovsk), compare its independently reproduced 2026 output with NamazTime's approved effective schedule, research Russian regional authorities/sources, and build the smallest safe city-to-policy foundation when evidence permits.

## Current Phase

Phase 10 — T039 operator workflow (`completed`)

## Phases

### Phase 1: Context, inventory, and research protocol

- [x] Recover prior context and inspect the worktree without changing existing user work.
- [x] Read all mandatory root docs, provider docs, pilot fixtures, relevant specs, and ADRs.
- [x] Inventory available static/runtime/build tools and APK input.
- [x] Record scope, evidence vocabulary, clean-room boundaries, and correctness-sensitive unknowns.
- **Status:** complete

### Phase 2: 1Muslim static/runtime research

- [x] Hash and identify the APK; inventory manifest, permissions, libraries, resources, native code, network endpoints, datasets, and update mechanisms.
- [x] Trace city/location to timezone/region/policy to calculation/timetable/API to adjustments and displayed output.
- [x] Attempt ordinary emulator black-box checks only where they add evidence; do not bypass controls.
- [x] Write `ONE_MUSLIM_APK_RESEARCH.md` with strict evidence labels and a reproducible sanitized method.
- [x] Commit checkpoint 1 without adding the APK or extracted proprietary artifacts.
- **Status:** complete

### Phase 3: Ulyanovsk independent reproduction and comparison

- [x] Identify the Ulyanovsk coordinates, timezone, calculation/timetable policy, Asr/high-latitude rules, seasonal switches, and offsets evidenced by the app.
- [x] Build an independent, synthetic research harness if the observed mechanism is reproducible without proprietary code/data.
- [x] Compare every 2026 effective Ulyanovsk row/prayer; report exact, ±1, ±2–5, >5, maxima, ranges, and late-August Dhuhr behavior.
- [x] Commit checkpoint 2.
- **Status:** complete

### Phase 4: Russia official-authority/source research

- [x] Research official regional/federal sources, prioritizing named regions and primary authorities.
- [x] Keep evidence strength separate from authority choice and record ambiguous/unknown mappings explicitly.
- [x] Create `RUSSIA_PRAYER_TIME_AUTHORITY_RESEARCH.md` and a structured registry draft with sanitized metadata/links only.
- [x] Compare IslamApp, 1Muslim, and proposed NamazTime architecture without copying competitor datasets.
- [x] Commit checkpoint 3.
- **Status:** complete

### Phase 5: Architecture and Ulyanovsk vertical slice

- [x] Specify City, Region, GeographicScope, PrayerAuthority, PrayerSource, PrayerPolicy, CalculationProfile, TimeTable, and SourceOverride boundaries.
- [x] Define precedence: exact city timetable → official regional timetable → approved regional calculation profile → explicitly configured fallback → unavailable.
- [x] Write `RUSSIA_CITY_SOURCE_ARCHITECTURE.md` and an ADR.
- [x] If evidence is sufficient, implement registry/resolver foundation and tests that route Ulyanovsk to the existing approved signed-offline pilot without weakening publication/signature/provenance.
- [x] Commit checkpoint 4.
- **Status:** complete

### Phase 6: Falsification review and delivery

- [x] Re-read the plan/findings and independently try to disprove correlations and inferred causation.
- [x] Run narrow tests, then `make docs-check`, `make test`, and `make lint`.
- [x] Update behavior docs/contracts only if changed; update `PLANS.md` and `CODEX_TASKS.md`.
- [x] Audit Git for APKs, decompilation, secrets, proprietary bulk data, and unrelated changes.
- [x] Commit checkpoint 5 and report commands, results, risks, changed files, region counts, implementation state, and next 3–5 tasks.
- **Status:** complete

## Correctness-sensitive unknowns

1. Whether 1Muslim uses local calculation, bundled timetable/config, downloaded dataset, remote API, or a hybrid for Ulyanovsk.
2. Whether any static code path is actually executed at runtime; static evidence will never be promoted silently.
3. Exact Ulyanovsk coordinates, timezone source, method/angles, Hanafi Asr rule, high-latitude handling, seasonal rules, and minute corrections.
4. Whether late-August Dhuhr differences come from an app rule, source override, date-version mismatch, or an unresolved external factor.
5. Whether official regional publishers define authoritative city/subject scope and whether parallel organizations create ambiguity.
6. Whether the existing pilot domain/schemas can host a general registry without migration or signed snapshot compatibility risk.
7. Whether ordinary emulator/network observation is available and materially useful without bypassing controls.

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Repository evidence labels remain `CONFIRMED_PUBLIC`, `CONFIRMED_STATIC`, `CONFIRMED_RUNTIME`, `INFERENCE`, `PROPOSAL`, `UNKNOWN`. | Root `AGENTS.md` says these exact labels are mandatory. The requested official-source confidence terms will be represented as registry mapping-status values, not substituted evidence labels. |
| No competitor code, keys, raw extracted assets, or bulk datasets may enter Git or planning/research artifacts. | Clean-room restriction and explicit user requirement. |
| A source registry can resolve only configured approved policy records; it cannot infer nationwide authority. | Geographic matching is not religious authority and silent fallback is prohibited. |

## Errors Encountered

| Error | Attempt | Resolution |
|-------|---------|------------|
| `python: command not found` while running the planning skill catch-up script. | 1 | Retry once with installed `python3`; do not repeat the missing `python` command. |
| `sudo` package installation requires an unavailable interactive password. | 1 | Installed official JADX 1.5.6 and apktool 3.0.3 releases user-locally; use Android SDK build tools and platform sqlite3. |
| Ordinary emulator installation fails with `INSTALL_FAILED_MISSING_SPLIT`. | 1 | The supplied base declares required ABI/density splits; record the runtime limit and do not generate modified splits or bypass Pairip/store controls. |
| First synthetic comparison assertion expected four `>5` fields, but the fixture actually contained five. | 1 | Audited the six synthetic deltas, corrected the test expectation to include Asr +6, and reran all four tests successfully. |
| The first registry validation query addressed `.subjects`, but the draft schema uses `.entries`. | 1 | Corrected the read-only query; all 31 entries have unique subject codes and computed status counts match the declared summary. |
| DUM RT's former prayer-time helper URL returned HTTP 410 during follow-up. | 1 | Retained the live first-party 2014 policy evidence, marked the selector URL as volatile, and kept the interface subject to source onboarding rather than treating it as a stable API. |
| A one-off falsification `awk` command projected the wrong annual CSV columns. | 1 | Re-read the header and verified Dhuhr through named JSON/CSV fields plus the reconciliation ledger; discarded the incorrect projection. |
| Initial registry seed promoted the composite Ulyanovsk source to a single RDUM authority. | 1 | Verified the source records and corrected the model to retain annual RDUM as `CONFIRMED_PUBLIC` and the August attributed publisher identity as `UNKNOWN`. |
| The first clean-room `rg` audit matched sanitized research terms and the synthetic SQLite test schema. | 1 | Replaced it with exact tracked-extension/path/size/MIME/diff checks that distinguish prohibited artifacts from allowed summaries and synthetic fixtures. |

## Notes

- Use local checkpoint commits only; never push or create a PR.
- Preserve all pre-existing user changes and exclude the root APK from Git.
- Update `findings.md` after at most two browser/view operations and after material discoveries.

## Phase 5 continuation after T034

### Phase 7: T035 canonical Russia city catalog

- [x] Compare legally usable geographic sources (at least OSM and GeoNames): license, provenance, update/reproduction path, timezone and subject suitability.
- [x] Add a pinned, checksum-verified importer and minimal licensed fixtures; do not commit a bulk dump.
- [x] Define stable NamazTime city IDs, canonical Russian names, aliases/transliteration, subject, coordinates, IANA timezone and source provenance.
- [x] Prove deterministic revisions/diffs and fail-closed duplicate-name search.
- [x] Update docs/task status and run checkpoint gates; create the local T035 commit before T036 edits.
- **Status:** complete

### Phase 8: T036 persisted executable policy registry

- [x] Write failing PostgreSQL repository/service tests for cities, aliases, regions/scopes, authorities, sources, policies, timetables/calculation profiles, overrides and revision audit.
- [x] Add migrations and rollback; enforce approval, ambiguity, stale/unavailable and no-silent-fallback semantics.
- [x] Prove deterministic registry revisions and revision rollback.
- [x] Update docs/task status and run PostgreSQL/checkpoint gates.
- [x] Create the local T036 commit before starting T037 implementation edits.
- **Status:** complete

### Phase 9: T037 Ulyanovsk persisted end-to-end

- [x] Replace the hard-coded executable Ulyanovsk runtime seed with imported/persisted catalog and registry records.
- [x] Add the minimal setup/admin search API proving city → RU-ULY → authority/source/policy → timetable → mosque → existing signed snapshot.
- [x] Prove snapshot bytes/ID/hash/signature unchanged, USB pilot remains compatible, rollback is non-destructive and duplicate/unknown/ambiguous city is not auto-selected.
- [x] Update docs/task status and run checkpoint gates.
- [x] Create the local T037 commit before T039 implementation edits (`804c677`).
- **Status:** complete

### Phase 10: T039 operator workflow (conditional)

- [x] Start only if T035–T037 are complete; expose explicit reasons/actions for duplicate city, ambiguous policy, stale source and unavailable schedule.
- [x] Never auto-select a neighboring region or generic Russia calculation method.
- [x] Run checkpoint gates, record verification evidence and create local T039 commit `f4c7ea9`.
- **Status:** complete

### Phase 11: final verification

- [x] Run all available docs, unit, lint, race, security and PostgreSQL gates from a clean worktree state except intended commits.
- [x] Re-read acceptance criteria and audit Git for third-party dumps, APK/decompilation, keys/secrets and signed-pilot drift.
- [x] Prepare task statuses, commits, chosen geographic source/license, catalog coverage, Ulyanovsk E2E evidence and real blockers; do not push or create a PR.
- **Status:** complete
