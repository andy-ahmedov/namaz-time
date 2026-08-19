# PLANS.md

This is the living execution plan. Update statuses, evidence and decisions after every completed task. Do not mark runtime behavior complete from static analysis.

Status values: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

## Phase 0 — research and repository foundation

| Item | Status | Evidence / exit condition |
|---|---|---|
| Public IslamApp feature research | DONE | store/help sources recorded in `SOURCES.md` |
| Analyze supplied screenshots | DONE | settings, QR and display observations in `RESEARCH_REPORT.md` |
| Clean-room static APK analysis | DONE | `APK_RESEARCH_ISLAMAPP_1_6_2.md`; sanitized evidence only committed |
| Compare prayer-time acquisition patterns | DONE | `PRAYER_TIME_SOURCE_PATTERNS.md` |
| Create docs-first Codex package | DONE | `make docs-check` passes |
| Runtime black-box validation on physical TV/box | BLOCKED | requires device/ADB test environment; see `BLACK_BOX_VALIDATION_PLAN.md` |
| Choose first pilot mosque/source/hardware | TODO | decisions D-001 through D-004 resolved |
| Confirm license/permission for first schedule | TODO | completed partnership checklist stored outside repo secrets |

## Phase 1 — bounded technical vertical slice

Goal: one TV shows a synthetic, then approved, offline schedule for one mosque.

| Task | Status | Acceptance |
|---|---|---|
| T001 repository and CI scaffold | IN_PROGRESS | local commit `cddc757`; Go/Android scaffold and CI workflow added; local gates pass, remote CI run remains `UNKNOWN` until the initial branch is pushed |
| T002 Go domain types + JSON Schema validation | DONE | `feat(domain): validate prayer snapshots`; valid synthetic snapshot passes Schema + domain checks, five invalid fixtures fail deterministically; local contract/race/vet/docs gates pass |
| T003 Android TV shell + Room | DONE | `feat(tv): add offline settings shell`; Compose for TV launches at API 28+, Robolectric D-pad test reaches all settings/actions, DataStore persists focus destination, Room schema v1 is exported and tested |
| T004 import bundled synthetic snapshot | TODO | transactionally active after cold install |
| T005 main prayer screen | TODO | six times, source state, date and countdown visible |
| T006 time/next-event engine | TODO | timezone/date rollover tests pass |
| T007 QR campaign | TODO | local QR, preview and safe invalid-URL behavior |
| T008 manual approved CSV/JSON provider | TODO | raw hash → diff → approval → signed snapshot |

Detailed prompts: [CODEX_TASKS.md](CODEX_TASKS.md).

## Phase 2 — production-grade source and publication pipeline

- PostgreSQL migrations and audit/outbox tables.
- Source registry and permission metadata.
- Raw artifact storage policy.
- Parser versioning and schema-drift circuit breaker.
- Minute-level candidate diff UI.
- Two-person or named-approver publication control.
- Ed25519 signing and key rotation.
- Device manifest, staged rollout and rollback.
- Coverage/staleness alerts.
- First authority/mosque source adapter.

Exit condition: a source change cannot reach a TV without validation, approval, signature and rollback evidence.

## Phase 3 — remote administration and fleet operations

- pairing code flow;
- role-based admin and mosque isolation;
- device heartbeat and diagnostics;
- remote iqamah/Jumu'ah rules;
- QR and announcement campaigns;
- custom background upload pipeline;
- canary device groups;
- privacy-safe support bundle;
- backup/restore drill.

## Phase 4 — device reliability

- physical matrix: Google TV, common Android TV box, Sber/Salute if targeted;
- boot/restart behavior per OEM;
- managed kiosk/device-owner option;
- power loss and bad clock tests;
- seven-day offline soak;
- memory/4K asset soak;
- screen burn-in mitigation policy where applicable.

## Phase 5 — regional scale

Do not begin until the first pilot is stable.

- onboard additional authorities through explicit source records;
- create geographically scoped calculation profiles only when approved;
- compare against annual official fixtures;
- add multilingual content and portrait layout;
- Ramadan and multiple Jumu'ah workflows;
- source-specific SLAs and support ownership.

## Required update after each Codex task

1. Record task result and commit in this file.
2. Update affected requirements/ADR/API/schema docs.
3. Add or update automated tests.
4. Record unresolved risk rather than hiding it.
5. Run `make docs-check` and project test commands.
6. Keep `README.md` usage truthful.

## Current blockers

- T001 remote CI evidence cannot be recorded until the initial branch is pushed and GitHub Actions runs;
- T003 retains the non-production product name/application ID while D-005 is open;
- T003 implements only the reversible local-first shell while D-008 is open;
- Room currently uses kapt because Room 2.8.4 KSP processing is incompatible with the scaffold's Kotlin 2.0.21 processor classpath; revisit with a coordinated Kotlin/AGP upgrade;
- no selected pilot mosque and canonical authority;
- no confirmed permission/format for production schedule redistribution;
- no physical Android TV/box available in the analysis environment;
- product name, package ID and license remain undecided.
