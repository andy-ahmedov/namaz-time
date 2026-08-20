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
| Choose pilot hardware | DEFERRED | Android Studio TV Emulator API 36 / 1920×1080 is the controlled development runtime; D-004 physical TV/box model remains open and separate |
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
| T010 first real source onboarding | BLOCKED | local source/effective, named approval, D-009 and D-013 publication slices DONE: immutable PDF baseline + August override, deterministic 365-day candidate/diff, signed approver receipt, Dhuhr/iqamah/Jumuah policy, protected-signer protocol, emulator runtime and rollback evidence model; first production publication remains blocked on D-014, a concrete KMS Ed25519 key, distinct security operator and authenticated signing-trust deployment; paired emulator canary follows those inputs, physical OEM acceptance is deferred/non-blocking |

T010's locally executable source slice is complete. The retained PDF SHA-256 is
`82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21`;
the strict parser produces a 2026-01-01→2026-12-31 raw `needs_review`
candidate with zero blocking validation errors. D-002 now binds the PDF as
baseline and the photo as the priority source for fields present in August.
`effective-schedule/v1` preserves both raw/component hash chains, retains PDF
al-Isfar where the photo has no value, keeps collective Dhuhr separate and
produces normalized SHA-256
`e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77`.
All twelve numeric differences remain visible and policy-resolved. Named
approver `approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly` signed the
exact candidate/diff/warnings and policy SHA-256. D-009 maps the approved
collective-Dhuhr source value to Dhuhr adhan, applies iqamah +5 minutes, omits
Friday Dhuhr iqamah and publishes one Jumuah at 13:15. This does not manufacture
a provisioned production KMS key/signature, distinct signer operator,
authenticated signing-trust deployment or D-014 stale/fallback decision, so T010
remains `BLOCKED` rather than DONE. D-013 is accepted and locally implemented: production code
accepts only an isolated signer interface/two-signature response, Go/API
and Android consume the same `scheduled`/`active`/`retired`/`revoked` public
trust model with direct-predecessor transition checks, and finalization emits a
signer-attested hash-chained receipt required by production API admission.
The `0.3.0-pilot-local` debug APK was rebuilt, installed and D-pad checked on
the selected API 36 Android TV emulator; SHA-256 is
`1308f1e65622335ab988ca481a508613d80ab882540929e56c40541e76f98539`.
It intentionally retains the visibly synthetic bootstrap until a KMS-signed
production snapshot is delivered through D-015/T009 pairing and sync.

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
- Ed25519 protected signing, public trust lifecycle and rotation. (D-013 local
  implementation DONE; real KMS/HSM key and authenticated deployment remain.)
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
| remote iqamah/Jumu'ah mutation | BLOCKED | initial D-009 policy is approved; remote changes still require an authenticated admin/approval workflow and the provisioned T010/D-013 signer |
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
| T020 bounded device-clock health | DONE | HTTPS manifest `Date` compared with injected response receipt at ±5-minute tolerance plus rollback detection; durable nullable health never blocks sync/display; heartbeat tri-state contract/wiring and physical bad-RTC/TLS/power evidence remain deferred |
| T021 bounded display-retention shift | DONE | public display foreground follows a deterministic six-position, ten-minute, ±2 dp cycle inside the shared safe frame; unavailable display participates while settings/background/focus order remain unchanged; pure policy plus 720p/1080p/4K safe-frame and D-pad regressions pass locally; panel-specific efficacy remains physical evidence |

The defined, independent local Phase 4 queue is complete through T021. The
remaining matrix below requires physical hardware, OEM behavior or an open
deployment decision, so it is not replaced with an invented local task and
Phase 5 has not started.

- physical matrix: Google TV, common Android TV box, Sber/Salute if targeted;
- boot/restart behavior per OEM;
- managed kiosk/device-owner option;
- power loss and bad clock tests;
- seven-day offline soak;
- memory/4K asset soak;
- panel/OEM screen-retention validation and any additional vendor-specific policy.

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
- pilot source precedence, named mosque approver, exact signed approval and D-009 mosque policy are accepted; organizational role verification is based on the product-owner statement and public third-party verification is not claimed;
- the official 2026 annual PDF now supplies full-year pilot coverage through T010; its daily Hijri values are absent and therefore remain unset rather than invented;
- Android TV Emulator API 36 at 1920×1080 is available and provides controlled runtime evidence; no physical Android TV/box evidence is available;
- abrupt OS process-kill/journal-recovery remains a future instrumentation/ADB
  acceptance case; T004 locally proves transactional rollback followed by a
  file-backed database close/reopen, not a physical-device process death;
- product name is `NamazTime`; production package ID and license remain undecided.
- D-010 still requires an approved pilot QR destination/domain; T007 therefore
  exercises only the authorized local mechanism with synthetic `example.org`
  fixtures and does not invent a live campaign.
- D-013 policy and local implementation are complete under ADR 0011. KMS is
  selected as the custody class, but the concrete provider, real Ed25519 key,
  a signer/security operator distinct from the approver, authenticated production trust-bundle
  deployment and measured rotation/revocation drill remain external rollout
  inputs. T008's public test key is never promoted; its private half was discarded.
- local Phase 1/T009 and T010 source/effective/approval onboarding are complete.
  D-002 resolves August precedence; the signed approval binds the effective
  candidate/diff/warnings; D-009 separately treats collective Dhuhr as adhan
  and derives iqamah +5. A real pilot publication remains intentionally
  impossible until protected production KMS key/trust deployment and a distinct
  signer operator are provisioned and D-014 is accepted. The resulting signed
  snapshot will use the already selected paired emulator for canary/rollback;
  physical OEM acceptance remains a separate future validation.
- T009 intentionally rejects non-empty remote asset manifests; custom asset
  staging/type/dimension activation requires a separately bounded task.
- T020 preserves clock health as `unknown`/healthy/mismatch locally, while the
  current heartbeat contract requires a boolean and has no production state
  assembler. Contract evolution/wiring must not collapse `unknown` into a false
  healthy claim.
