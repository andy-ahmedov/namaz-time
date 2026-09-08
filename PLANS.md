# PLANS.md

This is the living execution plan. Update statuses, evidence and decisions after substantive implementation or instruction changes; read-only answers and incidental typo fixes do not need entries. Do not mark runtime behavior complete from static analysis.

Status values: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

## T049 — nationwide verified first-party prayer-source onboarding

Status: IN_PROGRESS. Full owner requirements are preserved in
[the assignment](docs/tasks/T049-nationwide-first-party-onboarding.md).
Baseline verified 2026-09-08: clean main and actual origin/main both
`325a343f3fabec337e56df0abcd93c2812be2c67`. GitHub CI run 34162587927 failed
in docs-check on the ignored local `new_compact.png` link; failure reproduced
from a clean `git archive`. The reference is now plain text with its outside-Git
boundary, not a broken repository link. Remote CI is not rerun or claimed green.

| Phase | Status | Required evidence / remaining work |
|---|---|---|
| 0: persistent policy reconciliation | DONE | ADR 0019, AGENTS, streamlined provider skill, source checklist and governing docs separate qualification from optional endorsement. `make docs-check test-skills` PASS: 2 package checks, 130 runtime/data cases, 2 explicit non-bundled upstream module skips. Provider frontmatter validation and `git diff --check` PASS. No application/schema/signing changes in this checkpoint. |
| 1: previous research/demo audit | DONE | `research/t049/BASELINE_AUDIT.md`: 83 mapped subjects versus old 31 (52 gaps), current debug factory/constant-row projection, persisted legacy admission and pilot inspected; narrow pilot/artifact tests and 4 research tests PASS. No live NamazTime PostgreSQL instance found; fresh isolated DB gate remains required. |
| 2–3: nationwide research and scope resolution | IN_PROGRESS | All four ledgers delivered: western 29, eastern 21, southern 13 and Volga/Ural 20 subjects. Cross-ledger synthesis, currentness reconciliation and qualification remain in progress. Current hash-pinned GeoNames import contains 166,559 localities and 83 mapped subjects; old pilot catalog bindings remain unchanged. DUM RT Kazan CSV and XLSX agree for all 365 dates, including unrepresentable May 5 Fajr 23:54: explicit September coverage passes, full-year coverage fails closed. No inferred day offset or regional expansion. |
| 4/6: qualified providers and verified activation | IN_PROGRESS | DUM RT CSV, Omsk JSON, KBR extracted-PDF text, CDUM HTML and Sochi XLSX parsers have synthetic and retained-artifact checks. Qualification/snapshot/signing/audit v2 and Go registry v2 admission pass narrow tests without human approval; every activated qualified snapshot must carry the exact proof hash, and canonical scope/catalog bindings are checked. PostgreSQL v9 migration, rollback refusal, reapply and backup/restore integration pass. Android v2/Room v4 passes 135 focused tests reported by the Android lane, including cross-language signed fixture and legacy LKG. KBR now has a real hash-bound qualification inspection outside Git; signed materialization and normal setup still remain to be completed. |
| 5: normal setup/runtime | TODO | Canonical search, all independent real choices/previews, signed local activation, explicit synthetic test-only path, no generic fallback |
| Evidence and repository gates | TODO | Representative real patterns, pilot byte regression, parser drift/stale tests, PostgreSQL up/down/reapply/restore if changed, Android emulator + full requested gates |

Scope includes coherent local checkpoint commits, not push/PR/deployment,
signing-key changes, organization contact or production-data deletion. Unknown
source evidence makes that source/city unavailable, not the whole task blocked.
T038 is DONE under the explicit T049 closure rule: the second non-Ulyanovsk
regional adapter (DUM KBR annual PDF, explicit republic-wide scope) is implemented
and verified against all 365 retained first-party rows. No new source has yet
been qualified, materialized or activated by T049.

Current narrow evidence: `go test ./internal/registry ./internal/devices -count=1`
PASS, retained Kazan September hash-bound provider test PASS, and Android
`DeviceScheduleChoiceClientTest` PASS after its new mixed-authority test failed
against the previous global-tier rejection. Agents report full retained Omsk
122-day and KBR 365-day comparisons; root integration review and aggregate
gates remain pending. These checks do not establish runtime activation.

Qualification protocol narrow checks: `go test ./internal/domain
./internal/publication ./internal/registry ./internal/qualification` PASS after
test-first evidence/hash/catalog/timestamp/order/DST and protected-signing tests.
Snapshot v2 and signing/audit v2 JSON Schema positive cases pass; legacy snapshot
and publication tests still pass. Public source qualification cannot create
iqamah/Jumuah or a generic calculation policy. Research-validation totals are
not activation counts: DUM RT has 43 September parser passes, 36 unambiguous
catalog bindings (35 overlap), 8 unresolved identity mappings; CDUM has 17
September parser passes, with canonical mapping still to be admitted.

Registry v2 narrow checks pass for exact proof/source/authority/scope/catalog
binding, public geographic contexts without fake mosque approvals, independent
active choices with mandatory explicit selection, expiry and signed-proof swaps.
The public-import builder now joins the five strict adapters to retained raw
hashes, an exact canonical catalog and evidence-bound qualification.
`ingestor inspect-public` and the qualified `publisher assemble` branch pass
synthetic CLI tests, reject mixed human-approval inputs and protect existing
output files; real operational manifests and runtime wiring remain pending.

Independent trust review findings were reproduced with failing regression tests
and fixed: exact Int64 JSON Schema bounds, freshness at actual signing/publication
instants (not only generated-at), and receipt ordering
`decision <= generated <= signed <= published`. Correctly re-signed invalid
receipts are rejected; historical authenticated LKG verification remains valid.
Latest root checks: `go test ./internal/publication ./internal/registry
./cmd/publisher ./internal/onboarding -count=1` PASS; DUM KBR opt-in primary
PDF/text/reference hash check PASS with all 365 fields equal. Full gates and
real activation are still outstanding.

## 2026-09-08 — repository instruction and skill cleanup

Status: DONE. Follow-up to the owner-requested audit of AGENTS.md and
all eleven repository skills. Scope: task-sensitive workflow, precise discovery,
TV/reference compatibility, usable local routing/commands and package checks.
Prayer data, signing, application behavior and global skills are unchanged.
Behavioral speed/quality improvement remains `UNKNOWN` without comparative agent
runs; this task verifies instruction consistency and local package integrity.

- All eleven SKILL.md entrypoints reduced from 2,875 to 560 lines, with concise
  descriptions, task/platform-specific routing and existing catalog/reference
  resources retained. Authorized-reference use follows ADR 0014; TV keeps
  androidx.tv.material3. Unconditional questionnaires, absent harness tools and
  the slides self-routing loop are removed from the affected workflows.
- AGENTS.md separates read-only, documentation/skill and implementation work;
  PLANS.md follows the same boundary. Product/source/provenance/clean-room and
  Go/TV invariant sections are byte-identical to the pre-change revision.
- New `scripts/test_skill_package.py` reproduced 71 unresolved command paths
  before the corrections. Its resource/command checks now pass and are wired
  into `make docs-check`; `make test-skills` also runs local search/data tests
  and is included in `make test`. Documentation describes the gate's limits.
- Two orphaned upstream maintenance tests now report explicit capability skips
  instead of StopIteration during discovery. No evaluator/refresh tools were
  downloaded or fabricated: upstream refresh/benchmark behavior remains
  unverified. Partial catalog-tool installations still fail rather than skip.
- Verification: all 11 frontmatter checks PASS; `make docs-check test-skills`
  PASS (2 package checks, 130 runtime/data cases pass, 2 upstream module skips);
  `GRADLE_USER_HOME=/tmp/namaz-time-gradle make test lint` PASS, including 512
  Android tests with zero failures/errors/skips, Go, research and build identity.
  Local search/CLI help smoke checks run without provider requests or writes.
  A focus-restoration catalog query returned no match; a narrower state query
  returned relevant results. CLI success alone is not a relevance guarantee.
- No application/schema/fixture changes, installation or signed artifact.
  The owner subsequently requested a commit and push of this cleanup.
  Rollback is a review/revert of these instruction/test-wiring changes;
  no data migration or signing-key operation is required.

## T048 — final elegance and cinematic polish for RIGHT_SIDE_COMPACT

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline, reference and asset review | DONE | clean `main`/`origin/main` `aa23f52`, CI 34106800146 success, 0.6.2/code 9; owner-authorized `new_compact.png` reviewed against T047; all eight built-ins reviewed and none closes the luminous-background gap |
| Compact presentation and visual-system tests | DONE | compact-only minute clock/countdown with ceiling semantics; calmer type hierarchy; two-line long identity; bounded warning; semantic warm accent/glass/active/campaign roles pass narrowly |
| Original background and three visual passes | DONE | selectable non-default original `Luminous Dusk` packaged from a no-reference-input generated source; API 36 passes cover hierarchy, softer glass and final background/scale balance against T047 and the authorized target |
| Adaptive and regression evidence | DONE | Golden/Luminous/Blue, approved/attention, RU/EN, Iqamah on/off, QR payloads/blur, long copy, 720p/1080p/native 4K and all retention phases pass; protected half unchanged; STANDARD diff is zero pixels |
| Gates and versioned handover | DONE | implementation checkpoint `4f5b158`; docs/test/lint/strict Android (512 tests), Go race/security and secret gates PASS; clean signed 0.6.3-pilot.1/code 10 built and verified, then installed in place over code 9 without changing the first-install timestamp; certificate, QR/configuration and signed Ulyanovsk snapshot retained; T048 handover manifest; no push/PR |

## T047 — premium visual art direction for RIGHT_SIDE_COMPACT

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and reference | DONE | clean main/origin `6e86d61`, CI 34037688038 success, 0.6.1/code 8; owner authorizes new_compact.png visual target; T046 is BEFORE |
| Compact visual system | DONE | isolated glass/color roles, layout-aware selected background, hierarchy/icons/active row; T046 composition and QR contract retained |
| Three visual passes | DONE | API 36/1080p full/crop after surface, hierarchy, final polish; Golden Dusk/Blue Hour/Night Minaret |
| Adaptive and regression evidence | DONE | 66 API 36 frames, 54/54 QR and blur decodes, 54/54 protected-half checks, min free-left 50.15625%; native 4K; zero changed pixels in STANDARD/Donation/three Settings sections; 504 Android tests |
| Gates and handover | DONE | checkpoints `c4eae93`, `1a557d3`; docs/test/lint/strict Android (504 tests), Go race/security and secret gates PASS; clean signed 0.6.2-pilot.1/code 9 verified and installed in place; retained certificate/settings/snapshot; T047 handover manifest; no push/PR |

## T046 — RIGHT_SIDE_COMPACT owner-reference fidelity

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and reference | DONE | HEAD/origin `0bbe7d0`, CI 34034658957 success; only owner-supplied `new_compact.png` untracked; version 0.6.0/code 7 |
| Composition and regression tests | DONE | red/green relational geometry, native text/card containment, 160-character title + six subtitle lines, larger OFF times, retained D-pad/shared state/QR; 499 Android tests pass |
| Screenshot iterations | DONE | two API 36/1080p comparisons plus final review; 48 final frames, 42/42 QR/blur decodes, 42/42 protected-half comparisons; native 4K; zero STANDARD pixel differences; min free-left 50.15625% |
| Gates and versioned handover | DONE | checkpoint `c878d5e`; full docs/test/lint/strict Android (499 tests), Go race/security, PostgreSQL restore and secret gates PASS; clean signed 0.6.1-pilot.1/code 8 and in-place 7→8 upgrade verified, same certificate/settings/snapshot; T046 evidence manifest; no push/PR |

## T045 — physical-TV QR reliability and alternative schedule presentation

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and scope | DONE | clean HEAD/origin `b5bee5c`; CI 34030075138 success at same SHA; 0.5.2 code 6; API 36 emulator available |
| Shared QR hardening | DONE | native final-view pixels decode and equal the integer-module raster on STANDARD/compact/Donation/constrained preview at all three densities; badge and clipping removed |
| Persistent visibility and compact layout | DONE | DataStore close/reopen, retained overrides, existing engine countdown policy, native graphics/D-pad tests; six rows and protected half through all retention phases |
| Emulator composition and decode evidence | DONE | 61 valid frames; 50/50 exact decodes and bounded blur; 26/26 left-half pixel comparisons; real 4K Presentation; STANDARD differs only within QR; `docs/evidence/t045-tv-presentation/` |
| Gates and signed handover | DONE | all required gates PASS, 496 Android tests; clean signed `0.6.0-pilot.1` code 7 from `95bd858`; certificate/snapshot/hash checks and in-place emulator upgrade/decode PASS; handover manifest in T045 evidence; `PHYSICAL_QR_RETEST_REQUIRED` |

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
exact candidate/diff/warnings and policy SHA-256. The T033 signed successor
keeps source Dhuhr onset as adhan, fixes Dhuhr iqamah at 13:15 on all weekdays,
keeps the other four collective prayers at adhan +5 and publishes one Friday
Jumuah at the same 13:15 mosque-local time. D-013 is accepted and locally implemented: production code
accepts only an isolated signer interface/two-signature response, Go/API
and Android consume the same `scheduled`/`active`/`retired`/`revoked` public
trust model with direct-predecessor transition checks, and finalization emits a
signer-attested hash-chained receipt required by production API admission.
The earlier `0.3.0-pilot-local` emulator evidence is historical and has been
superseded by T022. T042 records a controlled Android 16 TV-emulator in-place
upgrade from code 3 to traceable `0.5.0-pilot.1` / code 4; physical-TV
acceptance remains a separate product-owner run. Remote-managed delivery still requires T009 pairing and the
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
| T033 pilot UI fidelity and operator customization (requested as T030) | DONE | Six checkpoint commits (`2ffd7e0`, `92c49d0`, `69442af`, `9a186cf`, `8fce081`, `37f3183`) implement the measured main display/shared QR, source-onset Dhuhr plus approved 13:15 Dhuhr/Jumu'ah policy, bounded local identity, built-in-only Appearance and donation filmstrips, gratitude customization and the Room/engine-backed standalone donation hierarchy. Follow-ups `2c0992f` and `30f32bd` close every independent review finding: complete donation-field D-pad traversal, engine-owned current-prayer selection without Sunrise, pre-signer iqamah materialization and unclipped Settings headings. Follow-up `852f98d` records the 2026-08-30 owner clarification by compacting every standalone donation foreground block into a 27.5-percent right rail and leaving the left image unobstructed; adaptive geometry tests and two API 36 review passes cover the correction. Full Go/Android/docs/lint/security gates, debug/release/signed-pilot builds, four fresh API 36 screenshots and independent code/visual reviews pass with no remaining finding; physical-TV/OEM picker and representative-distance QR acceptance remain `UNKNOWN`. |

The independent local Phase 4 queue is complete through T033. Before T034, the
remaining matrix below requires physical hardware, OEM behavior or an open
deployment decision. T034 does not claim those physical items complete; it is
a separate control-plane research/foundation task and does not activate a
nationwide TV rollout.

- physical matrix: Google TV, common Android TV box, Sber/Salute if targeted;
- boot/restart behavior per OEM;
- managed kiosk/device-owner option;
- power loss and bad clock tests;
- seven-day offline soak;
- memory/4K asset soak;
- panel/OEM screen-retention validation and any additional vendor-specific policy.

## Phase 5 — regional scale

The first pilot remains the only executable city/source entry. Research status
is not production eligibility, and the physical pilot blockers below remain
unchanged.

| Task | Status | Acceptance / evidence |
|---|---|---|
| T034 clean-room Russia city/source research and Ulyanovsk foundation | DONE | `ONE_MUSLIM_APK_RESEARCH.md`, full-year aggregate comparison, 31-subject first-party research draft, architecture/ADR 0015, and a tested control-plane resolver route Ulyanovsk to the unchanged approved signed pilot. Independent recomputation/falsification corrected composite authority evidence; `make docs-check`, `make test`, `make lint`, Go race/vulnerability and 73-commit secret scans pass; no competitor artifact or signed-snapshot change entered Git. |
| T035 licensed canonical Russia city catalog and search | DONE | GeoNames RU selected under CC BY 4.0 after OSM/ODbL comparison; exact 2026-08-29 inputs are size/SHA-pinned and bulk outputs stay outside Git. Deterministic importer/search/diff yields 166,557 Russian-named cities across 83 mapped subjects, explicit exclusions, stable IDs, IANA timezone/provenance and nine non-auto-selected `Киров` results. Two full imports are byte-identical; docs/narrow/lint gates pass. |
| T036 persisted policy registry with verified reference adapters | DONE | PostgreSQL v6 persists immutable schema-v1 geography/source/policy/payload/override revisions, canonical hashes, active pointer and append-only activation evidence. Service activation verifies exact approval/signed-snapshot references, source freshness and same-tier uniqueness; duplicate city search never auto-selects. Real PostgreSQL, migration rollback/reapply and least-privilege restore gates pass. |
| T037 Ulyanovsk persisted end-to-end migration | DONE | Hard-coded executable seed removed. Reviewed bindings compose the pinned 166,557-city GeoNames catalog with real approval/publication evidence; immutable PostgreSQL activation plus authenticated setup search resolves canonical Ulyanovsk → RU-ULY → explicit dual-evidence source/policy/timetable → Second Cathedral Mosque → unchanged signed snapshot. Real rollback, duplicate/unknown non-selection, API least privilege, full Go/PostgreSQL/Android/docs gates and raw snapshot SHA-256 `78233e7b…50b` pass. |
| T038 second official regional source adapter | DONE | Closed by T049's explicit adapter criterion: `kbr-annual-pdf-text/v1` validates the authority-linked 2026 PDF explicitly scoped «ПО КБР»; all 365 raw/extracted/reference rows and hashes match. See `research/t049/KBR_ADAPTER.md`. Nationwide qualification, signed materialization and runtime integration remain T049 work, not claimed complete by this adapter result. |
| T039 ambiguity, unavailability and staleness operator workflow | DONE | versioned admin assessment explains deterministic tier, authority/source evidence, freshness/range and stable blocked reasons; an authenticated mosque operator can append only a selectable staged choice as `pending_review`. PostgreSQL v7 makes requests idempotent/append-only and serializes against activation. Ambiguous/stale/unavailable never publish or select a neighboring/nationwide method; active revision, assignments, signed Ulyanovsk bytes and TV last-known-good remain unchanged. |
| T040 multi-authority city schedule choices | DONE | Checkpoints `4f0c9dd`, `3e41749`, `b7f8e76` add a non-persisted `CityScheduleChoiceSet` and authenticated `/setup/schedule-choices` v1 projection. It returns every highest-tier eligible authority choice with stable identity/provenance, neutral order and no top-N; multiplicity requires explicit selection while resolver ambiguity and T039 `pending_review` remain fail closed. 0/1/2/3/5/8 synthetic, PostgreSQL active/staged/stale, full/race/lint/docs/security and unchanged Ulyanovsk SHA-256 gates pass; no migration or new real source was added. |
| T041 Android TV city and schedule setup flow | DONE | Checkpoints `6375915`, `a5456ec`, `64ed91d`, `08500e5`, `cc02d51`, `824f279`, `ea37c5e`, `5bab92f`, `8840ffa` provide a provisioned-device-only API boundary, append-only schema-v8 `pending_review` handoff, canonical Cyrillic/alias city search, duplicate-city disambiguation, complete 0..N authority choices with visible evidence/approval/freshness, explicit proposal/pending UI and last-known-good preservation. Android 16 TV-emulator evidence covers system IME, D-pad scrolling, one/many/unavailable/pending states and Back/focus recovery; physical TV remains unclaimed. Clean dependency-home strict verification, full docs/Go/PostgreSQL/restore/Android debug+release/lint/race/vulnerability/secret gates pass, release excludes the debug evidence renderer, Room schema is unchanged and Ulyanovsk retains raw SHA-256 `78233e7b…50b`. |
| T042 traceable Android APK version and build identity | DONE | Checkpoints `609dda9`, `8f3713d` define one validated version source and ADR 0017; debug/release/pilot embed exact commit/variant/state and Diagnostics shows version/code/build identity. Clean signed `0.5.0-pilot.1` / code `4` packaging fails closed and writes an outside-repository APK/checksum/manifest bound to commit `609dda9`, APK/certificate and unchanged snapshot. Android 16 TV-emulator `adb install -r` upgrades code 3 in place and preserves last-known-good. Full docs/Go/PostgreSQL/restore/Android/race/vulnerability/secret gates pass; Room schema and snapshot SHA-256 `78233e7b…50b` are unchanged. Physical TV, key backups, tag/release and remote updates remain external/deferred. |
| T043 Android TV operator UX correctness and media-picker hardening | DONE | Checkpoints `6a60a0c` through `cfa4621` add a measured no-ellipsis QR contract, compact five-row Iqamah plus explicit signed-schedule resets, ADR 0018 picker/Photo Picker/MediaStore cascade with bounded permissions, feedback and actual emulator import, and full donation collection-link tombstoning/four-row display. Targeted API 36 review also fixes Donation status and Mosque context clipping. Signed `0.5.1-pilot.1` / code `5` installs in place; full docs/test/lint/PostgreSQL/strict-Android/race/vulnerability/105-commit secret gates pass, no migration is needed and the Ulyanovsk raw snapshot remains `78233e7b…50b`. Physical-TV acceptance remains `UNKNOWN`. |
| T044 next-prayer architectural watermark fidelity | DONE | Checkpoint `5ebf023` replaces the nested narrow watermark with a normalized full-card 23-percent-wide cubic pointed arch and an original filled warm lantern, without moving any foreground anchor. Geometry/Compose regressions cover 720p/1080p/4K; API 36 evidence records three iterations plus short/long-title and Golden Dusk/Blue Hour acceptance. Clean signed `0.5.2-pilot.1` / code `6` verifies and installs in place; full docs/test/lint/PostgreSQL/strict-Android/race/vulnerability/secret gates pass, no migration is needed and the Ulyanovsk raw snapshot remains `78233e7b…50b`. Physical-TV visual acceptance remains `UNKNOWN`. |
| 2026-09-03 Android TV emulator directional-input incident | DONE | [Controlled evidence](docs/evidence/2026-09-03-android-tv-emulator-input/README.md) isolates the failure from NamazTime and guest-side D-pad dispatch, then records recovery by fully restarting Emulator 37.1.11 without loading Quick Boot state. Live Extended Controls and host-keyboard checks emit the correct raw Up/Right/Down/Left events and move focus after recovery. A transient host-input or snapshot-state defect remains `INFERENCE`; Google Play sign-in causality and the exact emulator defect remain `UNKNOWN`. No wipe, app/data, permission, schema, schedule, provenance, or source-code change occurred. |
| 2026-09-03 Android TV debug city-search provisioning/preview/activation incident | DONE | [Controlled evidence](docs/evidence/2026-09-03-android-tv-debug-city-search/README.md) confirms that emulator networking was validated but the regular debug package had no encrypted provisioning, so T041 returned `NotProvisioned` before HTTP. The debug source set now supplies an explicit synthetic catalog, six-row organization preview and an initially focused `Use on this TV` action. Exact fixture IDs persist locally; the debug-only repository returns the chosen city on the main display after restart while the signed Room last-known-good remains unchanged. Search covers `Омск`/`Omsk` with `Asia/Omsk`; failures preserve the previous display and Back returns first to organization choices. Red/green ViewModel/Compose/repository/persistence tests and controlled `adb install -r` → Omsk activation → force-stop/relaunch evidence cover the flow. The selected fixture remains visibly `НЕ ОДОБРЕНО`; neutral choice copy does not label it approved. Pilot/release retain the strict provisioned client and exclude synthetic rows, selection storage and projection. No Room, signed snapshot, prayer/source approval, permission or pilot data changed; real regional onboarding and a production approved signed-activation contract remain `UNKNOWN`. |

2026-09-06 pre-push review: DONE; checkpoint `ef20768`. The debug setup changes and sanitized
emulator incident evidence are reviewed together. A new regression reproduced
the retained-ViewModel/recreated-Activity selection-store disconnect; the
debug runtime now shares one application-scoped store and all 145 focused
Android tests pass. Raw local PNG/MP4 references are explicitly ignored.
`make docs-check` and `make lint test test-android-all` pass, including all 485
Android tests, strict dependency verification, debug/release builds and APK
identity checks. APK DEX inspection confirms the synthetic gateway, projection
and selection store are absent from release. No Room, contract, permission or
signed-snapshot migration is introduced. Fresh physical-TV acceptance and a
new signed pilot artifact remain outside this Git review.

Later regional work still includes multilingual/portrait behavior, Ramadan and
multiple Jumu'ah workflows, source-specific SLAs, support ownership, and
physical-device acceptance. None is implied by T034.

## Required update after substantive changes

1. Record task result and commit in this file.
2. Update affected requirements/ADR/API/schema docs.
3. Add or update automated tests for affected executable behavior.
4. Record unresolved risk rather than hiding it.
5. Run `make docs-check` and task-appropriate checks from `AGENTS.md`.
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
