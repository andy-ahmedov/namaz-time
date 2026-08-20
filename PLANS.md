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
| Choose first pilot mosque/source | DONE | D-001 accepted for the Second Cathedral Mosque of Ulyanovsk; August 2026 photo selected as the first manual-import source fixture |
| Choose pilot hardware | DEFERRED | D-004 remains open; physical TV/ADB validation will be performed separately and does not block local Phase 1 work |
| Confirm license/permission for first schedule | DONE | product owner confirmed project use/redistribution permission on 2026-08-20; source SHA-256 recorded for T008 |

## Phase 1 — bounded technical vertical slice

Goal: one TV shows a synthetic, then approved, offline schedule for one mosque.

| Task | Status | Acceptance |
|---|---|---|
| T001 repository and CI scaffold | IN_PROGRESS | local commit `cddc757`; Go/Android scaffold and CI workflow added; local gates pass, remote CI run remains `UNKNOWN` until the initial branch is pushed |
| T002 Go domain types + JSON Schema validation | DONE | `feat(domain): validate prayer snapshots`; valid synthetic snapshot passes Schema + domain checks, five invalid fixtures fail deterministically; local contract/race/vet/docs gates pass |
| T003 Android TV shell + Room | DONE | `feat(tv): add offline settings shell`; Compose for TV launches at API 28+, Robolectric D-pad test reaches all settings/actions, DataStore persists focus destination, Room schema v1 is exported and tested |
| T004 import bundled synthetic snapshot | DONE | strict Android contract validation; offline asset bootstrap; full Room transaction and atomic active/previous pointer; corrupt input/local-state diagnostics and previous restore; file-backed failure/reopen preserves active data; explicit Room v1→v2→v3 migrations |
| T005 main prayer screen | DONE | responsive offline layout with six adhan rows, explicit missing/not-applicable iqamah, explicit preview-day/time/next-event placeholders, conservative source states, fixed-width countdown, recovery warning and D-pad settings path; measured bounds tested at 720p/1080p/4K profiles |
| T006 time/next-event engine | DONE | mosque-IANA clock, exact boundaries, next-day Fajr, explicit sunrise policy, iqamah override/range/weekday/priority resolution, separate Friday sessions and fail-closed DST/config diagnostics; exhaustive local tests pass |
| T007 QR campaign | DONE | local ZXing QR; exact HTTPS/lifecycle validation; lifecycle-independent operator preview; hashed audit stub; invalid, expired or overlapping campaigns fail closed without affecting prayer display; 720p/1080p/4K Robolectric coverage |
| T008 manual approved CSV/JSON provider | DONE | strict `manual-csv/v1` provider and inspect CLI; raw/transcription/normalized hashes; gap/order/scope/delta validation; deterministic diff and warning acknowledgements; approval-bound publication; canonical SHA-256 + Ed25519; Go/Android tamper/unknown-key verification; real August pilot retained as `needs_review`, never auto-approved |

Detailed prompts: [CODEX_TASKS.md](CODEX_TASKS.md).

## Phase 2 — production-grade source and publication pipeline

| Task | Status | Acceptance / evidence |
|---|---|---|
| T009 manifest/snapshot sync | DONE | explicit ephemeral one-use pairing fixture and scoped bearer API; duplicate credential rejection; signed registry and paired-mosque binding; same-origin canonical snapshot URLs; manifest/raw snapshot ETag/304 and Digest; Android AES-GCM/Keystore provisioning, provisioning-scoped durable stage/quarantine/checkpoint, signature/schema/domain/manifest/mosque binding, atomic activation and authenticated rollback; 401/404/500/timeout/tamper/re-pair plus file-backed import/post-commit interruption recovery tests pass |
| T010 first real source onboarding | BLOCKED | requires completed `SOURCE_PARTNERSHIP_CHECKLIST.md`, named D-002 approver, full-year authorized source, D-013 production signer/trust distribution and physical canary/rollback drill |

T009 completes the locally testable delivery half of the Phase 2 invariant. The
runtime command refuses to embed fixture credentials or trust keys, requires
an explicit ephemeral-test-only mode for its process-local pairing fixture and
accepts only a private explicit config. Android remote work remains
unprovisioned in a normal local-only install; a worker is scheduled only after
pairing/trust configuration is supplied. Non-empty remote asset manifests fail
closed until asset staging is implemented.

- PostgreSQL migrations and audit/outbox tables.
- Source registry and permission metadata.
- Raw artifact storage policy.
- Parser versioning and schema-drift circuit breaker.
- Minute-level candidate diff UI.
- Two-person or named-approver publication control.
- Ed25519 signing and key rotation.
- Device manifest, staged download and local/manifest rollback. (T009 local slice DONE; production rollout groups remain part of T010 operations.)
- Coverage/staleness alerts.
- First authority/mosque source adapter.

Exit condition: a source change cannot reach a TV without validation, approval, signature and rollback evidence.

## Phase 3 — remote administration and fleet operations

| Task | Status | Acceptance / evidence |
|---|---|---|
| T011 production pairing persistence | DONE | PostgreSQL-backed, restart-safe pairing lifecycle with hashed one-time secrets, expiry/attempt/rate controls, revocation, mosque suspension/isolation, verified remote TLS, bounded backend/rollback contexts, future-migration fail-close, update/delete/truncate-resistant audit and real database concurrency/restart/lock-cancellation tests; independent of blocked T010 |
| T012 role-based fleet administration and mosque isolation | TODO | authenticated actors, roles/memberships, scoped issue/revoke/read/assignment API, idempotency and audit binding on the T011 durable store |
| T012 role-based fleet administration and mosque isolation | TODO | authenticated admin actors/memberships, scoped issue/revoke/read API, idempotency/audit and cross-mosque denial tests |

Following fleet work after T012:

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
- pilot source image is selected and permission is confirmed, but the named mosque/authority prayer-time approver remains open under D-002;
- the real August 2026 photo is monthly rather than the annual golden fixture required for broad production coverage; T008 uses it as a real candidate fixture without inventing missing months;
- no physical Android TV/box available in the analysis environment;
- abrupt OS process-kill/journal-recovery remains a future instrumentation/ADB
  acceptance case; T004 locally proves transactional rollback followed by a
  file-backed database close/reopen, not a physical-device process death;
- product name, package ID and license remain undecided.
- D-010 still requires an approved pilot QR destination/domain; T007 therefore
  exercises only the authorized local mechanism with synthetic `example.org`
  fixtures and does not invent a live campaign.
- D-013 still requires a production signer/KMS, public-key distribution,
  rotation and revocation policy. T008 commits only a public test fixture; its
  ephemeral private key was discarded.
- local Phase 1 and T009 are complete, but a real pilot publication remains
  intentionally impossible until D-002 approves the exact
  raw/transcription/diff hashes and warnings, a full-year source is obtained,
  D-013 provisions production trust, and a physical canary/rollback drill is
  performed. T010 is therefore blocked rather than partially fabricated.
- T009 intentionally rejects non-empty remote asset manifests; custom asset
  staging/type/dimension activation requires a separately bounded task.
