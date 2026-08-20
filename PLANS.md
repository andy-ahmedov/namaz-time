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
| T012 role-based fleet administration and mosque isolation | DONE | verifier-only actors and global/local RBAC; uniform cross-mosque/missing-resource denial; scoped list/issue/revoke/verified-registry assignment; append-only 24-hour idempotency with two-phase HMAC rotation and historical responses; canonical assignment audit chain; out-of-band explicit-target migrations and API exact-v2 verification under a tested least-privileged runtime role; full/race/PostgreSQL gates and independent review pass |
| T013 privacy-safe device heartbeat and fleet health | DONE | strict bearer/path-scoped allowlist; server-trusted last-seen and latest-only v3 storage; active device/mosque recheck; mosque-scoped admin projection; same-origin best-effort Android client; least-privilege HTTP, rollback, full/race/PostgreSQL gates and independent review pass |
| T014 bounded canary rollout cohorts | DONE | persistent mosque-scoped device labels; registry-gated atomic assignment with 101st-row sentinel/100-device maximum; exact retries, deterministic concurrent locks, canonical per-device audit, monotonic rollback, v4↔v3 and least-privilege PostgreSQL evidence; full/race gates and independent review pass |
| T015 privacy-safe device support bundle | DONE | authenticated mosque-scoped bounded JSON projection from existing device/assignment/latest-health rows; closed OpenAPI contract, uniform isolation, `no-store`, least-privilege PostgreSQL read and forbidden-field regression evidence; no new collection, logs, secrets, URLs or history |
| T016 PostgreSQL backup and restore drill | DONE | clean-database custom archive restore, corrupt-archive rejection with zero partial tables, exact-schema/auth/current-state/SQLSTATE-55000 append-only verification through a reapplied read-only runtime role, production recovery runbook, full/race/PostgreSQL gates and independent review pass; no production data or RPO/RTO claim |

The independent local fleet foundation is complete through T016. Remaining
Phase 3 branches are explicitly blocked rather than implemented as alternate
unsigned content paths:

| Future branch | Status | Required decision/dependency |
|---|---|---|
| browser admin portal/session | BLOCKED | D-008 plus deployment identity-provider/session model; bearer bootstrap and production credentials must not be invented in Git |
| remote iqamah/Jumu'ah rules | BLOCKED | mosque policy D-009 and the T010/D-013 approval/signature publication path |
| QR and announcement campaigns | BLOCKED | destination/approval policy D-010 and the T010/D-013 approval/signature publication path |
| custom background upload pipeline | BLOCKED | asset custody/type/size/CDN policy plus the T010/D-013 signed publication path |

At the T016 checkpoint no Phase 4 work had begun. Physical-TV evidence remains
a separate deferred acceptance item and does not weaken the completed local
backend or current Robolectric evidence labels.

## Phase 4 — device reliability

| Task | Status | Acceptance / evidence |
|---|---|---|
| T017 connected TV display design system | DONE | one explicit dark TV Material palette/shape system, original offline atmospheric background and shared responsive safe frame; Room/T006/T007-connected main display exposes mosque-local date/time, next event/countdown, separate adhan/iqamah, source/recovery and optional QR; D-pad focus plus 720p/1080p/4K main/settings/error bounds, long text, Jumu'ah, campaign and contrast regressions pass locally; reference art is not packaged and physical-TV readability/overscan remains deferred |
| T018 display-only screen-on lifecycle | DONE | lifecycle-bound Compose `keepScreenOn` is active for normal/unavailable display, restores the prior host flag in settings/disposal and adds no permission; SDK 28/35 navigation/restoration tests, Android build and repository gates pass; OEM power behavior remains deferred |
| T019 accelerated offline rollover hardening | DONE | deterministic seven-day matrix over one materialized local schedule preserves mosque-local date/time and safe frame at 4K density, then fails closed on the first uncovered date; Android/repository gates and independent review pass; this is not a Room-reopen or physical seven-day soak |

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
