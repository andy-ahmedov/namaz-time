# CODEX_TASKS.md

Execute tasks in order unless `PLANS.md` records a deliberate change. Each task is independently reviewable.

## T001 — repository and CI scaffold

**Goal:** create compilable Go/Android module skeletons and stable root commands without implementing product logic.

**In scope:** Go workspace/module, Gradle Android TV project, CI, formatting/lint/test commands, package/module naming placeholder through a documented decision.

**Non-goals:** prayer calculation, backend API behavior, real source import, UI polish.

**Acceptance:**

```text
make docs-check
make test-go
make test-android-unit
```

Update `README.md`, `PLANS.md` and tooling section of `CONTRIBUTING.md`.

## T002 — domain model and snapshot contract validation

**Goal:** implement source-independent Go types and validation for the existing synthetic snapshot/schema.

**In scope:** mosque timezone, daily prayer row, source metadata, integrity envelope metadata, validation errors, JSON Schema parity tests.

**Non-goals:** HTTP server, database, signature private key, calculation.

**Acceptance:** valid synthetic fixture passes; fixtures with gap, duplicate date, invalid time/timezone and missing provenance fail deterministically.

## T003 — Android TV shell and local persistence

**Goal:** launch a Compose for TV app with D-pad settings shell, Room schema and DataStore preferences.

**In scope:** TV manifest/features/banner placeholder, navigation, focus states, local entities, repository interfaces.

**Non-goals:** network, production schedule, competitor visual copy.

**Acceptance:** unit/UI tests prove focus reachability and Room migration baseline; app builds for pilot API level.

## T004 — bundled synthetic snapshot import

**Goal:** package and atomically activate the synthetic example on first launch.

**In scope:** schema/domain validation on Android, staging import, active/previous pointer, diagnostics metadata.

**Non-goals:** remote sync/signature private key; real times.

**Acceptance:** cold install works offline; corrupt fixture leaves app in safe diagnostic state; process failure test preserves prior active data.

## T005 — main prayer display

**Goal:** implement independent main-screen layout from `UI_UX_SPEC.md`.

**In scope:** six rows, separate adhan/iqamah, date/time, next-event placeholder from domain state, built-in background/overlay, 720p/1080p/4K tests.

**Non-goals:** remote themes, video, copied competitor assets/layout.

**Acceptance:** screenshot/UI tests and D-pad path to settings; missing iqamah handled without fake value.

## T006 — time and next-event engine

**Goal:** resolve local date, iqamah rules and countdown in mosque timezone.

**In scope:** clock abstraction, exact boundaries, next-day Fajr, date/range/weekday precedence, Jumu'ah sessions.

**Non-goals:** alarms/notifications, calculation library.

**Acceptance:** exhaustive table tests from `TEST_STRATEGY.md`; device timezone mismatch does not change resolved schedule date.

## T007 — QR campaign

**Goal:** show locally generated optional QR with validated campaign lifecycle.

**In scope:** HTTPS validation, title/subtitle, start/end, preview, expired/invalid behavior, audit model stub.

**Non-goals:** payment processing, URL shortener, analytics.

**Acceptance:** QR scans in test fixture; invalid URL cannot break prayer display; UI tests for on/off/expired.

## T008 — manual file provider and publication prototype

**Goal:** complete raw CSV/JSON → candidate → diff → approval → signed snapshot for one synthetic/approved pilot source.

**In scope:** Go provider, raw hash metadata, parser fixtures, validators, CLI or minimal admin flow, test-only signing key and public verification fixture.

**Non-goals:** HTML scraping, multi-tenant production auth, real production signing key.

**Acceptance:** golden annual fixture publishes deterministically; changed row creates clear diff; unapproved candidate cannot publish; signature verifies in Go and Android test.

## T009 — manifest/snapshot sync

**Goal:** implement the OpenAPI device read path and Android staged synchronization.

**In scope:** pair test fixture, manifest ETag/304, snapshot download, signature/hash/schema validation, atomic activation, rollback.

**Non-goals:** full admin portal, push notifications.

**Acceptance:** integration tests for 200/304/failures/tamper/process kill; last-known-good remains visible.

## T010 — first real source onboarding

Blocked until [SOURCE_PARTNERSHIP_CHECKLIST.md](SOURCE_PARTNERSHIP_CHECKLIST.md) is complete.

**Goal:** implement exactly one authorized source for the pilot mosque.

**Non-goals:** nationwide coverage or unapproved scraping.

**Acceptance:** full-year fixture, manual comparison evidence, source permission record, candidate diff/approval, canary TV validation and rollback drill.

**Result (local source/effective slice, 2026-08-20):** the supplied RDUM
Ulyanovsk PDF and August photo are retained byte-for-byte with provenance and
SHA-256. Strict adapters normalize the 365-day baseline and 31-day override;
al-Isfar, collective Dhuhr and May/August Fajr/Isha transition evidence remain
separate. D-002 is encoded as immutable `effective-policy.json`: PDF baseline,
photo priority for every field it supplies in August, exact component hash
bindings and no raw edits. `effective-schedule/v1` produces a deterministic
365-day `needs_review` candidate/diff and resolves only the recorded source
conflicts. Ordinary publication cannot promote collective Dhuhr into iqamah.
A named religious approval and D-009 are now locally complete: a separate
approver key signs the exact candidate/diff/warnings and mosque policy; Dhuhr
adhan uses the approved collective column, iqamah is +5, and one Friday Jumuah
is 13:15. A concrete production KMS Ed25519 key, distinct security operator,
authenticated signing-trust deployment remain
`BLOCKED`, so T010 is not marked DONE. The selected paired Android TV emulator
is sufficient for the future canary/rollback checkpoint; physical OEM testing
is deferred and is not a local development blocker. D-013 itself is ACCEPTED and locally implemented: strict public trust
bundles share lifecycle semantics across Go/API/Android; production raw-key
signing is forbidden; isolated prepare/finalize tooling verifies exact
approval/candidate/diff/trust/actor/chain bindings with a second
domain-separated Ed25519 attestation and emits receipts required by API admission;
rotation/revocation/rollback operations are documented in the runbook.

## T011 — production pairing persistence

**Goal:** replace the T009 process-local pairing fixture with a production-capable, restart-safe pairing and device-credential foundation without depending on T010.

**In scope:** versioned PostgreSQL migrations for mosque/device/pairing/audit state; cryptographically generated one-time pairing codes and bearer tokens; hashes only at rest; expiry, bounded attempts, privacy-safe source/device rate buckets, transactional single-use redemption, revocation, append-only audit evidence, injectable clock/randomness, production API runtime wiring, and compatibility with the existing Android pair response/device read contract.

**Non-goals:** admin browser/login/RBAC endpoints, heartbeat, snapshot publication policy, production signing keys, real-source onboarding, and physical-TV validation.

**Acceptance:** real PostgreSQL integration tests prove restart-safe consumption, concurrent single winner, expiry/attempt/rate-limit behavior, token revocation and mosque/device isolation; public failures do not reveal code state or secrets; `make test`, `make lint`, and the documented PostgreSQL test command pass.

## T012 — role-based fleet administration and mosque isolation

**Goal:** expose the minimum authenticated admin/API model needed to issue/revoke pairing codes and manage devices without cross-mosque authority.

**In scope:** admin actors, role/membership persistence, service-layer authorization, secure session/API bootstrap decision, scoped mosque/device queries and pairing issue/revoke endpoints, idempotency and audit binding.

**Non-goals:** full visual admin portal, source approval/publication UI, heartbeat dashboards, billing, or multi-tenant branding.

**Acceptance:** integration tests prove `service_admin` and mosque-scoped roles, deny cross-mosque reads/writes without existence leaks, preserve idempotency, and record actor/request/reason audit evidence.

## T013 — privacy-safe device heartbeat and fleet health

**Goal:** add bounded operational health reporting without making display or
snapshot activation depend on telemetry success.

**In scope:** bearer/path-scoped heartbeat API; strict allowlisted health
contract; server-received last-seen time; latest-only PostgreSQL health state;
reported snapshot/sync/coverage/clock/timezone/storage/memory/boot/kiosk fields;
mosque-scoped fleet read projection; best-effort Android heartbeat client that
uses the provisioning origin and never changes the underlying sync result.

**Non-goals:** full logs, SSID/BSSID, installed-app/account/location data,
unbounded heartbeat history, analytics/crash SDK, dashboard UI, remote commands,
physical-TV validation, or changing snapshot assignment/activation.

**Acceptance:** strict HTTP/unit tests reject unknown/private fields and
cross-device credentials; PostgreSQL integration proves latest-only persistence,
revocation/scope behavior and server-trusted last-seen; admin list exposes only
the allowlisted projection; Android tests prove same-origin authenticated JSON,
bounded enums and failure-isolated best-effort reporting; full/race/PostgreSQL
gates pass.

## T014 — bounded canary rollout cohorts

**Goal:** target a small server-side device cohort with an already verified
immutable snapshot and roll it back without weakening per-device manifest
monotonicity.

**In scope:** mosque-scoped rollout-group label on devices; idempotent group
membership mutation; bounded atomic group assignment; verified artifact
registry lookup; exact 24-hour retry response; per-device monotonic manifest
versions and canonical audit hashes; rollback by assigning a previous verified
snapshot as a new manifest version.

**Non-goals:** percentage/random targeting, automatic promotion, publication or
approval, production signing keys, remote iqamah/Jumu'ah mutation, campaign or
asset upload, analytics, physical-TV validation, or groups larger than the
documented transaction bound.

**Acceptance:** RBAC/HTTP tests reject cross-mosque access and unverified
artifacts; PostgreSQL integration proves atomic multi-device assignment,
idempotent retry, deterministic lock order, bounded group size, monotonic
rollback and migration v4↔v3; manifest reads expose only each device's durable
assignment; full/race/PostgreSQL gates pass.

## T015 — privacy-safe device support bundle

**Goal:** give an authorized operator one bounded, structured diagnostic export
without collecting new telemetry or exposing credentials/history.

**In scope:** mosque-scoped admin read endpoint; server-generated bundle schema;
device/app/OS/model/lifecycle; mosque timezone; current durable assignment
identity/hash/key/version; latest-only heartbeat projection; rollout group;
`no-store` response and explicit reported-vs-server timestamp semantics.

**Non-goals:** device or backend logs, crash dumps, IP/MAC/SSID/BSSID, accounts,
location, installed apps, arbitrary key/value diagnostics, heartbeat history,
tokens/pairing codes, snapshot URLs, support upload, dashboard UI, remote
commands, or physical-TV validation.

**Acceptance:** viewer/support and admin roles can read only their mosque;
missing/cross-mosque devices share `404`; PostgreSQL integration proves the
bundle is assembled only from current bounded rows and contains no secret/URL;
HTTP/OpenAPI tests prove strict authentication, `no-store`, stable schema and
backend error handling; full/race/PostgreSQL gates pass.

## T016 — PostgreSQL backup and restore drill

**Goal:** prove that durable fleet state can be recovered into a clean database
without weakening schema/version, authentication, assignment or append-only
audit invariants.

**In scope:** a disposable PostgreSQL 18 logical backup/restore harness; a
synthetic linked v4 fleet fixture; custom-format archive integrity metadata;
fail-closed corrupt-archive handling; exact-schema verification; restored
device/admin authentication, current assignment/latest health reads and audit/
idempotency mutation guards; production recovery runbook and evidence template.

**Non-goals:** production credentials or data, managed cloud backup setup,
point-in-time-recovery automation, production RPO/RTO claims, signing-key/raw-
artifact backup, in-place restore, physical-TV validation, or unblocking T010.

**Acceptance:** `make test-postgres-restore` restores only into a newly created
database, rejects a truncated archive, verifies schema v4 and the linked fixture
through repository/service boundaries, and proves restored append-only triggers;
`make test-postgres`, full/race gates and docs pass. The runbook treats the dump
as sensitive, separates database/schema ownership from runtime grants, records
SHA-256 as integrity evidence rather than authenticity, and requires independent
snapshot-artifact/signing-key recovery.

## T017 — connected TV display design system

**Goal:** turn the existing functional T005–T007 display into a cohesive,
production-connected TV-first main screen and establish its visual language as
the reusable Compose foundation for every application screen.

**In scope:** clean-room visual derivation from the product-owner-supplied
`design.png`; semantic dark/amber color, type, spacing, shape, surface and focus
tokens; an original offline atmospheric background; Room-backed mosque/date/
adhan data; T006 next-event/countdown/iqamah/Jumu'ah resolution; T007 optional
QR; distinct adhan/iqamah labels; stable tabular clock digits; conservative
source/recovery indicators; explicit D-pad settings focus; consistent safe
error/settings surfaces; overscan-safe 16:9 layouts at 720p, 1080p and 4K
density profiles.

**Non-goals:** copying the reference's branding, background, icons, ornament or
pixel geometry; remote/custom assets; invented prayer/iqamah values; T010;
D-008/D-009/D-010/D-013; physical-TV/ADB evidence; portrait; video/motion
backgrounds; or Phase 5.

**Acceptance:** state tests prove event-kind/time and iqamah presentation come
from `PrayerTimeResolution`; UI tests prove all six rows, separate adhan/iqamah,
stable countdown width, source/recovery semantics, explicit initial D-pad focus
and card bounds inside a five-percent-equivalent safe frame on all three local
resolution profiles; available, campaign, long mixed-script, Jumu'ah and safe
unavailable states remain renderable; Android unit/build plus repository-wide
gates pass. Evidence is local JVM/Robolectric only, not physical-TV runtime.

**Result:** completed locally on 2026-08-20. The implementation supplies one
explicit dark TV Material color/shape theme, an original code-drawn offline
background, reusable panels and one responsive safe-frame component shared by
display, settings and recovery UI. The reference file remains an untracked
product-owner input and is not an Android or repository asset.

## T018 — display-only screen-on lifecycle

**Goal:** keep a dedicated prayer display awake while its public display route
is active without making settings, background work or the whole process hold
the screen on.

**In scope:** a lifecycle-bound Compose effect on the display route (including
its bounded unavailable/recovery state); restoration of the host view's prior
`keepScreenOn` value on route disposal; D-pad navigation regression proving the
flag is set on display, cleared in settings and restored on return; no Android
permission or network dependency.

**Non-goals:** wake locks, managed kiosk/device-owner behavior, OEM boot/relaunch
promises, burn-in mitigation policy, D-011, physical-TV/ADB evidence or Phase 5.

**Acceptance:** a deterministic local Compose test covers the complete
display → settings → display transition, the manifest gains no permission, and
Android unit/build plus repository-wide gates pass. Physical power-management
behavior remains deferred to the Phase 4 device matrix.

**Result:** completed locally on 2026-08-20. A display-route disposable effect
preserves the host view's prior flag, and SDK 28/35 tests cover settings release,
display return and pre-existing flag restoration. The manifest is unchanged.

## T019 — accelerated offline rollover hardening

**Goal:** prove locally that a connected display can continue projecting one
immutable local schedule across a full week of mosque-local date changes and
fails closed when that schedule's coverage ends.

**In scope:** a deterministic seven-day clock matrix reusing one already
materialized local schedule projection, the named mosque timezone, the
connected T006/T017 resolver/display and the 4K-density 16:9 profile; daily
date/content/safe-frame assertions; an explicit post-coverage unavailable
assertion; no network or source fallback.

**Non-goals:** claiming seven days of wall-clock runtime, measuring memory or
thermal behavior, OEM sleep/process behavior, changing the device clock,
inventing pilot data, physical TV/ADB evidence or automated fallback.

**Acceptance:** one accelerated test traverses seven consecutive local dates
without reloading the snapshot or rendering unavailable state, then proves the
first uncovered date produces the bounded support diagnostic. Android unit and
repository-wide gates pass; the physical seven-day soak remains deferred.

**Result:** completed locally on 2026-08-20. The connected resolver/projection
was separated from its unchanged route ticker so one immutable synthetic local
schedule could be advanced deterministically across seven named-timezone dates.
The 4K-density matrix and post-coverage diagnostic pass independent review.

## T020 — bounded device-clock health

**Goal:** detect an implausible Android wall clock when authenticated server
time is available without coupling schedule display or snapshot activation to
the diagnostic.

**In scope:** explicit `Date` on 200/304 manifest responses; an injected Android
request clock; a five-minute response-receipt tolerance; backward-clock
detection; durable unknown/healthy/mismatch evidence in the provisioning-scoped
sync checkpoint; explicit preservation of `unknown` rather than collapsing it
into heartbeat's required boolean; file-backed restart and 200/304 regressions.

**Non-goals:** setting the OS clock, treating HTTP time as source authenticity,
using network time to calculate prayer rows, blocking last-known-good display,
NTP/device-owner policy, TLS recovery when RTC makes certificates invalid,
tri-state heartbeat contract/runtime assembly, power-cut/OEM/runtime claims or
physical-TV evidence.

**Acceptance:** valid in-range and large forward/backward samples classify
deterministically; missing/malformed evidence makes no new claim; clock health
survives checkpoint reopen and refreshes on 304; skew does not prevent a valid
snapshot activation; Go/Android/repository-wide gates pass.

**Result:** completed locally on 2026-08-20. Successful manifest responses carry
a body-independent `Date`; Android persists nullable clock health and refreshes
it on 304 without changing sync outcome. The existing heartbeat payload cannot
represent `unknown`, so T020 intentionally does not manufacture a boolean or
claim production heartbeat wiring.

## T021 — bounded display-retention shift

**Goal:** reduce exact foreground pixel persistence on the always-on public
display without introducing distracting motion, changing the T017 visual
language or weakening overscan and D-pad behavior.

**In scope:** one deterministic six-position cycle selected from wall-clock
ten-minute slots; at most two dp movement on either axis; main and bounded
unavailable display states; movement inside the existing responsive safe frame;
unchanged static atmospheric background, settings route, semantic tree and
focus order; pure policy and existing 720p/1080p/4K UI regressions.

**Non-goals:** claiming prevention of burn-in or image retention; panel/OEM
controls; continuous animation; moving the settings UI or full-bleed
background; wake locks; physical-TV/ADB evidence; D-011 or Phase 5.

**Acceptance:** the offset is stable within each ten-minute slot, cycles
deterministically and never exceeds ±2 dp per axis; the most constrained
connected profile retains its root-relative safe margins and initial settings
focus; unavailable and settings navigation remain usable; Android unit/build
and repository-wide gates pass.

**Result:** completed locally on 2026-08-20. The display route derives a tiny
foreground offset from its existing injected clock and passes it through the
shared safe frame for both available and unavailable projections. No new
animation, permission, data dependency or visual style was introduced. Actual
panel efficacy remains `UNKNOWN` until a physical soak.

## T022 — pilot-local real runtime and settings

**Goal:** replace the synthetic demonstration bootstrap in the explicitly
local pilot build with the approved Ulyanovsk 2026 effective schedule and turn
the settings shell into a truthful, localized TV operator surface.

**In scope:** build-only pilot-local snapshot/trust assets derived from the
retained PDF, August override, signed approval and D-009 policy; authenticated
Room bootstrap and restart selection; D-014 no-fallback coverage behavior;
Room-backed mosque/source/iqamah/Jumu'ah/QR/diagnostic settings; locally
effective appearance/language actions; Russian default and persisted whole-app
language; D-pad and multi-profile regression coverage.

**Non-goals:** a production-signing shortcut, production KMS/private material,
API registry admission of the local artifact, an invented QR destination,
device-owner/kiosk or boot promises, physical TV, emulator/ADB evidence,
production application ID/license, remote settings mutation or Phase 5.

**Acceptance:** pilot-local cold start authenticates and atomically activates
365 approved days; 20/24 August, +5-minute iqamah and Friday 13:15 Jumu'ah are
pinned; 1 January 2027 renders safe unavailable without calculation fallback;
synthetic data is test-only and release contains no embedded schedule/local
trust; every settings section reports real local state and only exposes real
actions; RU/EN selection persists with Russian default and no mixed-language
user-visible literals; Android unit/build and repository gates pass.

**Result:** completed locally on 2026-08-20. An ephemeral, disjoint pilot-local
Ed25519 key signed the exact approval-bound publication request and was then
discarded. Only the immutable snapshot, public trust bundles and local audit
evidence remain. The debug pilot-local source set authenticates the asset with
the existing lifecycle-aware verifier, imports it through the existing Room
transaction and reuses trust-aware startup selection. Tampering fails before
activation; release has no bundled schedule path. Room exposes mosque,
provenance, approval, iqamah/Jumu'ah, campaigns and bounded diagnostics to the
settings UI. Appearance protection and whole-app RU/EN are real persisted local
actions; kiosk truthfully reports inactive state and opens Android's actual app
settings instead of pretending to enable device-owner/autostart. D-pad tests
cover every section/action and 720p/1080p/4K safe frames. The product owner will
perform emulator runtime acceptance separately, so that evidence remains
`UNKNOWN` rather than being promoted from local tests.

An in-place update from the prior pilot APK is explicit rather than requiring
data clearing: under the same Room transaction, only
`synthetic-ulsk-demo-2026-08-v1` may be replaced, the authenticated real pilot
snapshot becomes active and the synthetic row is deleted instead of becoming
last-known-good. A concurrent/unexpected active ID makes replacement abort
without persistence, so this debug migration cannot overwrite a paired real or
remote snapshot.

## T023 — Android TV visual redesign and offline image backgrounds

**Goal:** redesign the complete Android TV visual language from the post-T022
local state, using `design.png` only as a clean-room composition/mood reference
and preserving the real data-driven display behavior.

**In scope:** an app-wide dark navy/gold Material 3 token system; translucent
glass surfaces; centered NamazTime pill and mosque identity; responsive left
next-event/countdown plus clock stack; right prayer table with six original
prayer glyphs, a separate iqamah glyph, non-color active-row treatment and
Sunrise centered across the combined time area; bottom iqamah/provenance strip;
two original offline image backgrounds with a validated persisted Appearance
choice; D-pad/focus, RU/EN and 720p/1080p/4K regressions; controlled emulator
build/install/screenshot comparison.

**Non-goals:** copying third-party branding, screenshot pixels, background,
icons or ornament; changing T022 prayer/source/domain values; custom/remote
asset upload; network access from display composition; physical-TV/OEM claims;
portrait/video backgrounds; push or PR.

**Acceptance:** packaged background tests, preference validation/persistence,
visual semantic anchors and Sunrise geometry pass; existing prayer, D-pad,
localization, safe-frame and offline tests remain green; debug APK builds and
installs on the API 36 Android TV emulator; main, Settings and Appearance are
recorded and visually inspected; repository checks pass.

**Result:** completed locally on 2026-08-20. The main display now follows the
requested visual hierarchy with original project imagery and glyphs while
continuing to project the unchanged Room/T006 state. Appearance persists only
allowlisted bundled image IDs and safely defaults unknown values. Settings and
recovery reuse the same glass surfaces, typography, gold focus language and
full-bleed background. Emulator evidence is `CONFIRMED_RUNTIME` for the bounded
1920×1080/API 36 observations only; physical-TV readability and panel behavior
remain `UNKNOWN`.

## T024 — pixel-accurate main-display refinement

**Goal:** treat `design.png` as the measurable visual specification for one
additional clean-room pass over the main display, rather than producing another
free visual interpretation.

**In scope:** normalized gap analysis against the T023 emulator screenshot;
centered foreground width and equal-column geometry; next/date/prayer/strip
proportions; thin line/diamond ornament rhythm; calendar and Settings glyphs;
softer translucent surfaces, row dividers and active treatment; reduced public
technical copy; existing original backgrounds, prayer glyphs, D-pad,
localization and 720p/1080p/4K behavior; repeated API 36
build/install/screenshot comparison.

**Non-goals:** copying target pixels, imagery, branding, glyphs or proprietary
ornament; changing T022/T023 prayer/source/domain behavior; redesigning
Settings; backend work; new features; physical-TV claims; push or PR.

**Acceptance:** the normal foreground is about 70–76 percent of the viewport,
columns differ by at most four percent, next/clock cards stay within the pinned
reference height ratios, Sunrise remains centered, all original prayer glyphs
remain present, Settings remains a labeled 48 dp D-pad target, approved source
technical labels do not dominate display mode, all three adaptive profiles and
repository gates pass, and fresh emulator evidence is recorded.

**Result:** completed locally on 2026-08-21. The normalized 960×540 comparison
moved the body left edge from about 50 px to the target 138 px, made both
columns equal, matched the target next/clock heights at about 187/128 px and
placed the bottom strip within one pixel of the target y-position. The public
screen now uses the requested concise next-card structure, original decorative
anchors, softer table rhythm and a subordinate circular Settings action.
Room/T006 data projection, T022 pilot values, background preference and
Settings behavior are unchanged. Controlled emulator evidence is
`CONFIRMED_RUNTIME`; physical overscan/readability remains `UNKNOWN`.
