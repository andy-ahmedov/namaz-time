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
| Repair repository-local Codex skill metadata | DONE | `android-tv-screen` and `prayer-times-provider` have valid YAML frontmatter; `quick_validate.py` passes |
| Add repository-local design skills | DONE | nine design/Compose skill packages are tracked; `quick_validate.py` passes for every package; generated caches and local visual/runtime evidence remain ignored |
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
| T010 first real source onboarding | DONE | immutable PDF baseline + August override, deterministic 365-day candidate/diff, signed approver receipt, Dhuhr/iqamah/Jumuah policy, no-fallback expiry and protected-signer protocol are complete. D-015 selects the signed bundled-snapshot/USB path for the first mosque pilot; KMS and remote trust deployment are deferred to remote-managed operation. |

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
Friday Dhuhr iqamah and publishes one Jumuah at 13:15. D-013 is accepted and locally implemented: production code
accepts only an isolated signer interface/two-signature response, Go/API
and Android consume the same `scheduled`/`active`/`retired`/`revoked` public
trust model with direct-predecessor transition checks, and finalization emits a
signer-attested hash-chained receipt required by production API admission.
The earlier `0.3.0-pilot-local` emulator evidence is historical and has been
superseded by T022. Runtime acceptance of the new APK belongs to the product
owner; repository claims remain local build/Robolectric evidence until that
run is recorded. Remote-managed delivery still requires T009 pairing and the
ADR 0011 production signer deployment; those are not requirements for D-015's
offline USB pilot.

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
| remote/fleet custom background pipeline | BLOCKED | asset custody/type/size/CDN policy plus the T010/D-013 signed publication path; T028 device-local picker is explicitly outside fleet publication |

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
| T022 pilot-local real runtime and settings | DONE | pilot-local packages an approval-bound 365-day Ulyanovsk snapshot plus disjoint public trust, authenticates and atomically activates it through Room, and supports monotonic in-place bundled-snapshot successors. Replacement is limited to known predecessors/the pilot family, same timezone/same mosque after initial migration, and later signed generation time; rejection preserves last-known-good. August precedence/D-009/D-014, settings, RU/EN, D-pad and 720p/1080p/4K regressions pass locally. |
| T023 Android TV visual redesign | DONE | original Golden dusk/Blue hour offline image assets, validated persisted Appearance selection, app-wide navy/gold Material 3 glass system, NamazTime identity, six original prayer glyphs plus iqamah, centered Sunrise time, explicit active-row treatment and redesigned display/settings/recovery surfaces; UI/DataStore/adaptive tests and controlled API 36 emulator screenshots pass while prayer/source logic remains unchanged |
| T024 pixel-accurate main-display refinement | DONE | explicit normalized gap analysis against `design.png`; about 71% centered foreground, equal columns, reference-like next/clock/strip proportions, decorated location/date treatments, softer prayer rows and separators, compact icon-only Settings focus target and exceptional-only public source status; UI geometry/D-pad/adaptive regressions plus repeated API 36 build/install/screenshot comparison pass without prayer/domain/settings changes |
| T025 device-local sadaqah QR and iqamah controls | DONE | validated HTTPS QR/purpose/motivation and five independent fixed iqamah values persist in DataStore without mutating the signed Room snapshot; Settings removes Friday technical copy and exposes D-pad editors; a configured QR activates the authorized-reference three-column Sadaqah panel with frame, support icon, scan-tested NamazTime center badge and lower geometric ornament; repository/projection/Compose/adaptive/QR-decode tests plus API 36 build/install/runtime screenshots pass |
| T026 focused main-display visual refinement | DONE | three direct API 36 build/install/screenshot comparisons against `main_with_qr.png`; dense matte navy surfaces, subdued background, neutral borders, muted champagne accents, lighter type, arch/lantern watermark, fading diamond lines, shared geometric ornaments, softer prayer/highlight treatment and refined QR/support/strip presentation; token, contrast, semantics, clock-bounds and QR-decode regressions pass without prayer/domain/QR business/D-pad/offline changes |
| T027 concise pilot display identity | DONE | local commit `feat(tv): shorten pilot display identity`; main display and Mosque settings show `Вторая Соборная Мечеть` / `Ульяновск` for the exact pilot mosque ID through a presentation-only mapping; canonical signed snapshot/provenance remains unchanged, non-pilot identity passes through, focused Compose/bootstrap tests pass and the updated APK is installed on the API 36 emulator |
| T028 local TV operator UX and alternate display modes | DONE | five checkpoint commits replace ambiguous fixed `HH:mm` preferences with bounded `adhan + N minutes` offsets and approved-policy fallback; add eight offline built-in backgrounds plus validated app-private background/donation image imports; add a persisted, fail-closed donation display with the existing local QR generator, transfer text, five packaged images and explicit Settings/schedule D-pad exits; remove the Settings white/double frame and match the authorized main reference with a normalized rounded-square glass target, white gear and one gold non-scaling focus outline. Repository/projection/import/Compose/adaptive tests, `make test`, `make lint` and a controlled API 36/1920×1080 runtime loop cover the completed scope. The emulator had no document-provider activity, so OEM picker selection and physical-TV behavior remain `UNKNOWN`; signed Room data is unchanged. |
| T029 pixel-accurate standalone donation display | DONE | local implementation commit `80c7200` replaces the three-column donation layout with the authorized `qr_page.png` full-bleed composition; adds one right card, exact localized footer, custom vector icons/fade ornaments, reference-scale decodable QR and compact gold-outline gear; replaces the transfer blob with five bounded DataStore/Settings fields plus deterministic RU/EN/unlabelled legacy preservation. 720p/1080p/4K geometry and actual-badge QR decode tests plus four API 36/1920×1080 build/install/full-and-region overlay loops pass. Final runtime values remain operator-local; signed Room data is unchanged. Physical-TV/OEM and real-phone scan distance remain `UNKNOWN`. |
| T030 independent production-readiness engineering/security review | DONE | [independent report](docs/reviews/2026-08-28-engineering-security-review.md) records 0 CRITICAL, 4 HIGH, 12 MEDIUM, 2 LOW and 2 CLEANUP findings. Checkpoints `888f534`, `7a6e5b8`, `26cfbd0`, `2346fb8` and follow-up `a1e05d8` close every locally actionable material issue, including the AGP 9.3/KSP migration. The second pass found no new CRITICAL or unblocked HIGH. D-015 now separates a conditionally ready offline USB pilot from the still-not-ready future remote-managed mode. |
| T031 permanent Android pilot identity | DONE | checkpoint `27bf040`; D-005 accepts `ru.namaztime.tv`; Android namespace, application ID, Kotlin packages, Room schema export path and identity regression now use it. The old T001 placeholder remains only in historical evidence and cannot be upgraded in place because Android treats the accepted ID as a separate app. |
| T032 signed offline pilot artifact | DONE | checkpoint `7767f46`; a dedicated non-debuggable `pilot` variant packages the authenticated local schedule, requires an external PKCS12 keystore, and fails without it. Debug uses `ru.namaztime.tv.debug`. `make build-android-pilot` verifies exact package ID, APK signature, pinned public certificate and required assets; the permanent key exists outside Git/APK with mode 0600. Two operator-chosen offline backups and physical-TV acceptance remain external actions. |

The independent local Phase 4 queue is complete through T029. After T029, the
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

## Current offline-pilot blockers

- copy the generated offline APK-signing keystore/properties to two
  operator-chosen offline backup locations before the first mosque install;
- the signed current build is installed and reinstalled in place on the API 36
  emulator; record physical TV/box acceptance at the mosque. Emulator evidence
  cannot prove OEM boot, overscan, storage-provider or long-soak behavior;
- pilot source precedence, named mosque approver, exact signed approval and D-009 mosque policy are accepted; organizational role verification is based on the product-owner statement and public third-party verification is not claimed;
- the official 2026 annual PDF now supplies full-year pilot coverage through T010; its daily Hijri values are absent and therefore remain unset rather than invented;
- abrupt OS process-kill/journal-recovery remains a future instrumentation/ADB
  acceptance case; T004 locally proves transactional rollback followed by a
  file-backed database close/reopen, not a physical-device process death;
- D-010 still requires an approved pilot QR destination/domain for signed or
  remote campaigns. T025 adds a clearly device-local operator QR preference,
  while tests/runtime evidence continue to use synthetic `example.org` and do
  not invent a live destination or official claim.

Remote API/domain, pairing composition, KMS custody, remote trust deployment,
Google Play and remote CI evidence are deferred requirements for a different
deployment mode, not blockers for the D-015 offline USB pilot.
- T009 intentionally rejects non-empty remote asset manifests; custom asset
  staging/type/dimension activation requires a separately bounded task.
- T020 preserves clock health as `unknown`/healthy/mismatch locally, while the
  current heartbeat contract requires a boolean and has no production state
  assembler. Contract evolution/wiring must not collapse `unknown` into a false
  healthy claim.
