# Task Plan: 1Muslim clean-room research and Russia city/source foundation

## Goal

Determine, with clean-room evidence, how 1Muslim 5.9.5 resolves prayer times (especially Ulyanovsk), compare its independently reproduced 2026 output with NamazTime's approved effective schedule, research Russian regional authorities/sources, and build the smallest safe city-to-policy foundation when evidence permits.

## Current Phase

Phase 3

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
- [ ] Commit checkpoint 1 without adding the APK or extracted proprietary artifacts.
- **Status:** in_progress

### Phase 3: Ulyanovsk independent reproduction and comparison

- [ ] Identify the Ulyanovsk coordinates, timezone, calculation/timetable policy, Asr/high-latitude rules, seasonal switches, and offsets evidenced by the app.
- [ ] Build an independent, synthetic research harness if the observed mechanism is reproducible without proprietary code/data.
- [ ] Compare every 2026 effective Ulyanovsk row/prayer; report exact, ±1, ±2–5, >5, maxima, ranges, and late-August Dhuhr behavior.
- [ ] Commit checkpoint 2.
- **Status:** in_progress

### Phase 4: Russia official-authority/source research

- [ ] Research official regional/federal sources, prioritizing named regions and primary authorities.
- [ ] Keep evidence strength separate from authority choice and record ambiguous/unknown mappings explicitly.
- [ ] Create `RUSSIA_PRAYER_TIME_AUTHORITY_RESEARCH.md` and a structured registry draft with sanitized metadata/links only.
- [ ] Compare IslamApp, 1Muslim, and proposed NamazTime architecture without copying competitor datasets.
- [ ] Commit checkpoint 3.
- **Status:** pending

### Phase 5: Architecture and Ulyanovsk vertical slice

- [ ] Specify City, Region, GeographicScope, PrayerAuthority, PrayerSource, PrayerPolicy, CalculationProfile, TimeTable, and SourceOverride boundaries.
- [ ] Define precedence: exact city timetable → official regional timetable → approved regional calculation profile → explicitly configured fallback → unavailable.
- [ ] Write `RUSSIA_CITY_SOURCE_ARCHITECTURE.md` and an ADR.
- [ ] If evidence is sufficient, implement registry/resolver foundation and tests that route Ulyanovsk to the existing approved signed-offline pilot without weakening publication/signature/provenance.
- [ ] Commit checkpoint 4.
- **Status:** pending

### Phase 6: Falsification review and delivery

- [ ] Re-read the plan/findings and independently try to disprove correlations and inferred causation.
- [ ] Run narrow tests, then `make docs-check`, `make test`, and `make lint`.
- [ ] Update behavior docs/contracts only if changed; update `PLANS.md` and `CODEX_TASKS.md`.
- [ ] Audit Git for APKs, decompilation, secrets, proprietary bulk data, and unrelated changes.
- [ ] Commit checkpoint 5 and report commands, results, risks, changed files, region counts, implementation state, and next 3–5 tasks.
- **Status:** pending

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

## Notes

- Use local checkpoint commits only; never push or create a PR.
- Preserve all pre-existing user changes and exclude the root APK from Git.
- Update `findings.md` after at most two browser/view operations and after material discoveries.
