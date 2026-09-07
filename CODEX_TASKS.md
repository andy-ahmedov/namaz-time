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

**Non-goals:** a remote-production signing shortcut, private material in Git/APK,
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
evidence remain. The dedicated pilot source set (also consumed by debug tests)
authenticates the asset with the existing lifecycle-aware verifier, imports it
through the existing Room
transaction and reuses trust-aware startup selection. Tampering fails before
activation; release has no bundled schedule path. Room exposes mosque,
provenance, approval, iqamah/Jumu'ah, campaigns and bounded diagnostics to the
settings UI. Appearance protection and whole-app RU/EN are real persisted local
actions; kiosk truthfully reports inactive state and opens Android's actual app
settings instead of pretending to enable device-owner/autostart. D-pad tests
cover every section/action and 720p/1080p/4K safe frames. The product owner will
perform emulator runtime acceptance separately, so that evidence remains
`UNKNOWN` rather than being promoted from local tests.

In-place offline pilot updates are explicit rather than requiring data clearing.
Under the same Room transaction, only a known predecessor or the dedicated
`ulyanovsk-second-cathedral-pilot-local-` family may be replaced. After the
initial synthetic migration, mosque and timezone must match, and the incoming
signed `generated_at` must be later. The replaced row is deleted instead of
becoming rollback state. Unexpected family, cross-mosque/timezone or downgrade
input aborts without persistence and leaves last-known-good active.

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

## T025 — device-local sadaqah QR and per-prayer iqamah controls

**Goal:** let a TV operator configure the existing local QR generator and five
fixed iqamah values, and render the configured QR in the explicitly authorized
`main_with_qr.png` visual composition without weakening signed schedule
provenance.

**In scope:** validated DataStore fields for HTTPS URL, sadaqah purpose and
motivation; five independent Fajr/Dhuhr/Asr/Maghrib/Isha times; a D-pad editor
under renamed `Икамат` and `QR-код` settings; removal of Friday technical copy;
ephemeral time-engine projection; configured-QR three-column display; the
authorized corner frame, support icon and lower geometric ornament treatment;
RU/EN, adaptive tests and API 36 runtime screenshots.

**Non-goals:** editing the signed Room snapshot, assigning iqamah to Sunrise,
labelling local values official, a live donation destination, payment
processing, analytics, remote administration/publication, network calls from
Compose, APK-extracted code/resources, physical-TV or real-phone scan claims,
push or PR.

**Acceptance:** unsafe or partial QR settings fail closed; five valid values
persist atomically and project without mutating source lists; pre-adhan local
iqamah cannot hide prayer data; the renamed settings are fully D-pad reachable
and contain no removed Jumu'ah/Dhuhr explanation; complete QR settings activate
the reference-proportion right panel and all decorative anchors; existing
prayer, QR-decode, localization and 720p/1080p/4K regressions plus repository
checks pass; emulator evidence and authorization basis are recorded.

**Result:** completed locally on 2026-08-21. The pre-existing ZXing generator
now has a validated operator input path. DataStore keeps local QR and iqamah
state separate from Room; the resolver overlays only current/next-day valid
iqamah values. Settings exposes three QR fields and five prayer fields with
explicit save actions. A complete QR switches display mode to the authorized
three-column Sadaqah treatment while preserving real T022 prayer data. API 36
runtime evidence confirms entry, persistence and projection at 1920×1080. The
NamazTime center badge uses high QR error correction and a four-size
worst-case-obstruction decode regression;
physical-TV readability and real-phone scan distance remain `UNKNOWN`.

## T028 — local TV operator UX and alternate display modes

**Goal:** complete the device-local operator experience with reference-matched
Settings focus, relative iqamah controls, safe local image selection and a
standalone donation display while preserving the signed Room schedule.

**In scope:** one gold-only D-pad focus treatment; the product-owner-authorized
`new_main_page_with_setting_icon.png` Settings-control geometry and glass
treatment; five bounded `adhan + N minutes` iqamah offsets with approved-policy
fallback; eight packaged display backgrounds; a validated Android document
picker that copies JPEG/PNG/WebP input into app-private storage; a separate
persisted donation display using the existing HTTPS/QR validation, transfer
text, five packaged images and an independent custom-photo slot; explicit
D-pad paths from either display mode to Settings and back to the schedule;
RU/EN and 720p/1080p/4K regressions.

**Non-goals:** changing signed Room schedule or provenance, guessing a migration
from legacy fixed clock times, iqamah for Sunrise, broad storage permission,
payment processing, analytics, remote/fleet asset publication, network access
from display composition, physical-TV claims, nationwide source selection,
push or PR.

**Acceptance:** legacy fixed-time preferences cannot silently become offsets;
valid offsets project from each prayer's adhan without Room mutation and unset
values use approved policy; packaged/custom background and donation images
survive restart with bounded type/size/decode validation and safe fallback;
invalid donation state cannot activate; all settings and both display modes are
D-pad reachable within the adaptive safe frame; the Settings control matches
the normalized reference contract and focus has no additional white frame;
targeted tests, `make test`, `make lint`, controlled emulator build/install and
the bounded screenshot loop pass, with unsupported emulator document-provider
behavior recorded as `UNKNOWN`.

**Result:** completed locally on 2026-08-24 in five checkpoint commits. The
signed Room snapshot remains immutable; local iqamah offsets are ephemeral
projection only. Both custom-image slots use atomic app-private copies and
fall back independently. The donation mode validates complete local content,
reuses the existing QR generator and always exposes Settings plus a schedule
return action. Controlled API 36/1920×1080 evidence is
`CONFIRMED_RUNTIME` for the main screen, Settings, eight-background gallery,
donation configuration/display and restoration of schedule mode. The emulator
had no document-provider activity, so OEM picker selection and physical-TV
behavior remain `UNKNOWN`.

## T029 — pixel-accurate standalone donation display

**Goal:** rebuild the standalone donation screen as a measured 1:1 visual
reproduction of the product-owner-authorized `qr_page.png`, except for
NamazTime identity, operator-entered details, the local QR payload and the
selected background.

**In scope:** full-bleed selected donation image; centered NamazTime glass pill;
compact non-scaling Settings gear; one tall right donation card; localized
heading/instruction/footer; large dark-navy-on-warm-white QR with a
reference-scale NamazTime badge; five structured recipient/bank/card/SBP-phone/
collection-link DataStore fields and Settings editors; deterministic legacy
blob migration; custom Compose Canvas/vector icons and fade-ended gold
ornaments; five built-in images plus the independent custom slot; RU/EN, D-pad,
safe-frame and 720p/1080p/4K tests; repeated 1920×1080 build/install/screenshot
and full/region overlay/diff evidence.

**Non-goals:** reference banking defaults, a payment flow, network access from
display composition, mutation of the signed Room snapshot, remote/fleet asset
publication, bitmap extraction from the screenshot/APK, broad storage
permissions, physical-TV or real-phone scan claims, push or PR.

**Acceptance:** the normalized brand, Settings visual, right card, QR,
five-row block and footer anchors remain within the measured 4–8 px tolerance
at 1920×1080 and scale coherently across the three adaptive profiles; all
specified icons and fade ornaments are local vectors; Settings persists five
structured fields without losing an existing legacy blob; sample reference
banking values never enter runtime defaults; QR rasters decode with the actual
center-badge obstruction; targeted and repository-wide gates pass; final
emulator screenshot/50-50 overlay/diff evidence and authorization SHA are
recorded.

**Result:** completed locally on 2026-08-27. Four installed visual passes after
the T028 baseline corrected the QR Y/scale, row rhythm/value column, compact
gear, brand clipping and footer ornaments/text. The final 1920×1080 and region
overlays pin the reference geometry; runtime Settings exposes the five
structured fields while retaining the five packaged images and custom picker.
The signed Room schedule, offline path and QR destination remain unchanged.
Controlled emulator observations are `CONFIRMED_RUNTIME`; physical-TV/OEM and
real-phone scan distance remain `UNKNOWN`.

## T033 — pilot UI fidelity and operator customization

The product-owner request names this task T030, but repository T030 already
records the completed production-readiness audit. T033 preserves that history
and is the repository identifier for the requested work.

**Goal:** reproduce the supplied canonical main, selector and donation-layout
references while adding mosque-local Dhuhr/Jumu'ah policy and bounded operator
identity/gratitude customization without weakening the signed offline model.

**In scope:** six separately committed checkpoints: main-screen/vector/QR
fidelity; Dhuhr onset plus shared 13:15 Dhuhr/Jumu'ah policy and one-minute
operator control; local display-name/address overrides; eight-item Appearance
preview plus one D-pad LazyRow and separate SAF action; equivalent five-item
donation selector plus localized optional gratitude; and the new 16:9
standalone donation block hierarchy driven by the existing Room-backed time
engine. Each persisted field requires bounded defaults and regression tests.

**Non-goals:** changing mosque ID, source locality, timezone, provenance or
signed schedule through presentation settings; independent prayer calculation;
broad storage access; bypassing artifact validation/signatures; third-party
brand/resources; remote publication; push or PR.

**Acceptance:** all functional requirements in the owner request pass; main
anchors normalize to approximately 4–8 px where content permits; selector and
donation references pass region/component comparisons; D-pad, RU/EN, image
validation, QR decode, offline/rollback and signed pilot invariants regress;
each checkpoint has a local commit; final `make test`, `make lint`,
`make docs-check`, Android gates, debug/pilot builds, four runtime screenshots
and independent code/visual reviews pass with a clean worktree.

**Result:** completed locally on 2026-08-29. Checkpoint 1 passed targeted tests
and repeated controlled API 36 build/install/normalize/refine passes.
Checkpoint 2 keeps source Dhuhr onset, publishes the authenticated 13:15
Dhuhr/Jumu'ah successor through approval/receipt/trust rotation, and adds
one-minute local controls with explicit fixed-Dhuhr persistence/projection.
Checkpoint 3 adds bounded local display-name/address preferences, blank pilot
fallback and read-only canonical locality/timezone context without touching
Room identity or provenance. Checkpoint 4 replaces the three-column Appearance
grid with one large 16:9 preview, an eight-built-in-only scrolling D-pad
filmstrip and a separate SAF action; focused traversal, safe-import regressions
and five controlled 1920×1080 component-review iterations pass.
Checkpoint 5 applies the built-in-only filmstrip to five donation images and
adds a bounded persisted gratitude override with render-time RU/EN fallback;
DataStore/Compose tests and three emulator component-review passes succeed.
Checkpoint 6 translates the new portrait composition into a measured 16:9
status/gear, central-card and gratitude-block layout. Its current-prayer label
comes from the existing active Room schedule and `PrayerTimeEngine`; missing
schedule state fails closed. Adaptive/state/app tests and three controlled API
36 component-review passes succeed.

Follow-up `852f98d` implements the product owner's 2026-08-30 visual correction,
which supersedes only Checkpoint 6 geometry: every standalone donation
foreground block now sits in one
right-anchored 264/960-wide rail, while the first 656/960 of the viewport has no
foreground card and exposes the selected image. The status and gear remain
equal-height, the QR/details stack vertically in the central card, and the
gratitude card shares the rail edges. No Room, engine, QR payload, operator
configuration or signed-snapshot behavior changes.

The six checkpoint commits are `2ffd7e0`, `92c49d0`, `69442af`, `9a186cf`,
`8fce081` and `37f3183`. Independent review follow-ups `2c0992f` and `30f32bd`
add complete donation-field D-pad traversal, move current-prayer ownership into
`PrayerTimeEngine` with Sunrise excluded, materialize effective iqamah policy
before protected signing, remove dead donation drawings and preserve Cyrillic
heading diacritics. Final `make test`, `make lint`, `make docs-check`, strict
debug/release Android gates, secret/vulnerability scans and the externally
signed pilot build pass. The signed pilot remains `ru.namaztime.tv`, version
`0.4.0-pilot-local` (3), with the pinned certificate and bundled signed
offline snapshot intact. Fresh 1920×1080 runtime screenshots cover main,
Appearance, Donation Settings and standalone Donation. Independent code and
visual reviews report zero Critical, Important or Minor findings and return
`Ready` / `ACCEPT`. These are `CONFIRMED_RUNTIME` emulator results;
physical-TV/OEM picker and representative-distance QR acceptance remain
`UNKNOWN`.

## T034 — clean-room Russia city/source research and Ulyanovsk foundation

**Goal:** determine how 1Muslim 5.9.5 resolves prayer times, reproduce its
stored Ulyanovsk path without copying proprietary code/data, research Russian
regional prayer authorities/sources, and make the existing Ulyanovsk pilot the
first entry of a general fail-closed city/source resolver.

**In scope:** sanitized APK static inventory and ordinary install attempt;
stored/calculated/network/update data-flow recovery; independent aggregate
2026 Ulyanovsk comparison; first-party authority/source research with explicit
confirmed/strong/ambiguous/unknown mapping status; IslamApp/1Muslim comparison;
City/Region/GeographicScope/PrayerAuthority/PrayerSource/PrayerPolicy/
CalculationProfile/TimeTable/SourceOverride domain boundaries; precedence and
ambiguity tests; control-plane-only Ulyanovsk seed referencing the unchanged
approved signed pilot; ADR, plans and falsification review.

**Non-goals:** committing the APK, DEX/decompilation, keys, competitor rows or
bulk datasets; claiming static paths executed; assigning nationwide authority;
tuning a generic calculator to mimic official values; adding TV/Compose
networking; changing signed snapshot bytes, Room, UI or Android permissions;
activating any subject beyond the existing Ulyanovsk pilot; push or PR.

**Acceptance:** the Ulyanovsk mechanism is identified and compared across 365
days × six prayers; discrepancies and `UNKNOWN` causes are bounded; public
research preserves locality and competing-authority ambiguity; the resolver
implements exact timetable → regional timetable → approved regional
calculation → explicit fallback → unavailable with same-tier ambiguity;
Ulyanovsk returns the existing source/policy/snapshot IDs and exact component
authority evidence; tests/checks pass; five local commits record the work.

**Result:** completed locally on 2026-08-30 in five checkpoint commits. Static
evidence establishes a hybrid 1Muslim architecture and a stored-table
Ulyanovsk path; runtime app behavior remains unavailable because the supplied
base APK requires missing ABI/density splits. The independent 365-day
comparison and a second one-off implementation agree exactly. Research covers
31 subjects (6 confirmed, 5 strong, 6 ambiguous, 14 unknown), but only the
existing Ulyanovsk pilot is executable. Falsification review corrected the
composite source to preserve annual RDUM as `CONFIRMED_PUBLIC` and the August
publisher identity as `UNKNOWN`. Full docs/Go/Android/lint/race/vulnerability/
secret gates and clean-room Git audit pass. No competitor artifact, new
production regional mapping, TV behavior, or signed snapshot byte changed.

## T035 — licensed canonical Russia city catalog and search

**Goal:** replace the one-city in-memory geographic seed with a revisioned,
licensed control-plane catalog that can safely distinguish Russian settlements.

**In scope:** source/license decision; deterministic import; canonical stable
IDs; RU federal-subject codes; settlement type; names and explicit aliases;
coordinates, IANA timezone and geographic provenance; duplicate-name and
transliteration search results; update diff, review, rollback and tests.

**Non-goals:** prayer authority inference, embedded competitor/GeoJSON data,
live geocoding from TV, silent selection among duplicate names, or source
policy activation.

**Acceptance:** an operator can search duplicate and transliterated names and
choose a canonical city with visible subject/timezone/provenance; invalid
timezone/schema drift fails closed; catalog revisions are auditable and
rollbackable.

**Result:** completed locally on 2026-08-30. GeoNames RU was selected under CC
BY 4.0 after an explicit OSM/ODbL comparison. The tracked manifest pins three
2026-08-29 inputs by byte length/SHA-256; raw archives and the 96 MB generated
catalog remain outside Git. The deterministic importer produces 166,557
Russian-named records across 83 mapped subjects, preserves stable source-based
NamazTime IDs, aliases, feature code, coordinates, IANA timezone and full
geographic provenance, and fails closed on checksum/schema/admin/timezone
drift. Exact `Киров` returns nine subject-qualified candidates and cannot be
auto-selected. Two full imports were byte-identical with an empty deterministic
diff; narrow docs/Go/lint gates pass. No prayer authority or policy is inferred.

## T036 — persisted policy registry with verified reference adapters

**Goal:** persist the T034 entity model while keeping approval and snapshot
authentication in their existing authoritative stores.

**In scope:** PostgreSQL migrations for regions, scopes, authorities and exact
evidence labels, sources, policies, calculation/timetable payload references
and overrides; revision/audit lifecycle; adapter inputs from verified approval
receipts and verified published-snapshot registry; resolver explanation API;
foreign-key, scope, mosque, effective-range, ambiguity and rollback tests.

**Non-goals:** treating a non-empty approval/snapshot ID as authentication,
promoting the research JSON into production, automatic fallback, or TV schema
changes.

**Acceptance:** only records backed by verified approval/publication state can
enter an executable registry revision; same-tier conflicts and stale/missing
references block activation; previous registry revision remains recoverable.

**Result:** completed locally on 2026-08-30. PostgreSQL migration v6 persists
immutable schema-v1 revisions across the full T034 entity model, deterministic
content hashes, one active pointer and append-only activation/rollback evidence.
The service verifies exact mosque-scoped approval and published-snapshot
references, rejects research/stale/unavailable sources and same-tier overlap,
and re-verifies rollback targets. Exact active city/alias search returns all
deterministically ordered subject-qualified matches and never auto-selects a
duplicate. Real PostgreSQL tests cover stage/activate/search/rollback,
append-only guards and v6↔v5 fleet-preserving migration; the restore drill
proves read-only runtime-role access. The research draft remains non-executable,
and no TV or signed-snapshot contract changed.

## T037 — persisted Ulyanovsk end-to-end migration

**Goal:** move the T034 hard-coded pilot seed into T035/T036 storage without
changing the current signed offline result.

**In scope:** canonical Ulyanovsk city and RU-ULY region rows; annual RDUM
`CONFIRMED_PUBLIC` component; August attributed-publisher `UNKNOWN` component;
effective source, mosque-bound policy, override and timetable references;
admin/setup search and explanation; exact snapshot/hash/signing-key comparison;
rollback test.

**Non-goals:** changing schedule rows, authority evidence, mosque scope,
timezone, approval, signature, Android bootstrap/UI, or adding another city.

**Acceptance:** search `Ульяновск` plus explicit Second Cathedral Mosque choice
resolves the same `effective-ulyanovsk-2026-v1` and
`ulyanovsk-second-cathedral-2026-pilot-local-v2`; signed bytes remain identical;
failure leaves the existing pilot usable.

**Result:** completed locally on 2026-08-30. The executable in-memory seed was
removed and replaced by strict policy bindings targeting the exact pinned
GeoNames catalog revision/hash. A bounded operator command verifies the real
signed mosque approval and publication/trust chain before immutable PostgreSQL
activation. The authenticated setup API returns every exact city candidate and
resolves only an explicit canonical city/mosque/date. A real PostgreSQL/HTTP
test proves `Ульяновск` → canonical `city-4adc…3a4c` → `RU-ULY` → retained
`CONFIRMED_PUBLIC`/`UNKNOWN` authority identities → existing effective source,
policy, timetable, mosque and signed snapshot; successor activation and
rollback leave the snapshot ID, raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`,
signing key and bytes unchanged. Duplicate/unknown cities do not auto-select;
ambiguous/unavailable policy errors fail closed. Go, full-catalog preflight,
PostgreSQL migration/restore, Android pilot unit and docs gates pass. No
schedule row, signature, TV UI/Room path or nationwide source coverage changed.

## T038 — second official regional source adapter

**Goal:** prove the architecture on one non-Ulyanovsk locality using a directly
onboarded first-party authority source.

**In scope (superseded by T049 / ADR 0019):** qualify first-party organization,
exact geographic scope and legitimate public transport from evidence, without
external written approval; implement one allowed provider kind with raw
capture/metadata, deterministic normalization, fail-closed schema drift,
validation/diff, reproducible sanitized or synthetic fixtures, qualification,
signing and last-known-good behavior. Татарстан calculation policy and one
Dagestan locality timetable remain candidates until actual source/policy
evidence and deterministic validation are complete, not until a contact replies.

**Non-goals:** choosing the easiest scraped page, inferring undocumented
angles, subject-wide promotion from a city widget, or silent alternate source.

**Acceptance:** one explicitly named locality/mosque has a complete retained
source-to-signed-snapshot chain and independently reviewed comparison evidence;
all other localities remain unavailable.

## T039 — ambiguity, unavailability and staleness operator workflow

**Goal:** make fail-closed regional resolution usable without hiding why a city
cannot be auto-selected.

**In scope:** admin API/UI projections for candidate authority/source, evidence
label, geographic scope, effective range, precedence tier, freshness and
blocked reason; explicit mosque/operator binding and approval handoff; audit;
tests for Moscow/Ufa-style parallel authorities, expired schedules and missing
fallback.

**Non-goals:** aggregator recommendations, coordinate-based religious choice,
automatic nationwide/neighboring fallback, or modifying the TV display path.

**Acceptance:** ambiguous/stale/unavailable results are explained and cannot be
published until an explicit approved binding exists; devices retain the last-
known-good signed snapshot.

**Result:** completed locally on 2026-08-30 as a control-plane API/review
handoff. Revision-specific assessment deterministically projects every
applicable policy with precedence, named authority evidence/scope, source
freshness, payload range and a stable blocked reason. Same-tier eligible
options remain `ambiguous`; stale, unavailable, research-only, expired,
out-of-range and missing-schedule options are non-selectable. An authorized
mosque operator can append an idempotent `pending_review` choice only from a
still-staged revision. PostgreSQL migration v7 stores the selection digest and
audit context under append-only guards while serializing against activation.
The request does not activate a revision, approve/publish anything, or modify
an assignment/snapshot. Real PostgreSQL/HTTP tests cover ambiguous selection,
idempotent replay, stale rejection, active-pointer preservation, migration
rollback/reapply and signed-pilot byte preservation. The versioned API is the
operator surface for this slice; no visual web client, neighboring fallback,
generic Russia method or new regional source was added.

## T040 — multi-authority city schedule choices

**Goal:** keep one canonical geographic `City` while exposing every eligible
authoritative prayer schedule choice for that city as a separate setup
projection, without ranking or automatically selecting a religious authority.

**Product decision:** one canonical city may expose any number of eligible
authoritative schedule choices. The system must not arbitrarily truncate or
rank same-precedence religious authorities, and presentation multiplicity must
never be interpreted as automatic authority selection. Transport/UI bounding,
if ever needed, must use explicit pagination or equivalent lossless mechanics
rather than top-N selection.

**In scope:** `CityScheduleChoiceSet` derived from existing
`RevisionPolicyAssessment`; stable city+policy choice identity; canonical
authority labels/evidence, source, scope, approval/effective range, precedence
and timetable/calculation-profile provenance; complete 0..N highest-tier
eligible results; neutral deterministic ordering; a separate authenticated
`/setup/schedule-choices` v1 endpoint; staged `pending_review` integration;
synthetic 0/1/2/3/5/8 matrix; persisted and signed-Ulyanovsk regressions.

**Non-goals:** authority-specific duplicate City rows, real T038 onboarding,
top-N/display slots, religious ranking, auto-selection, promoting blocked or
lower-precedence policies, changing resolver ambiguity, approval/publication or
device assignment, visual web admin, Android/Room/TV changes, snapshot rewrite,
new source scraping, migration solely for presentation metadata, push or PR.

**Acceptance:** `/setup/cities` remains geographic; after explicit canonical
city selection, setup can retrieve all eligible choices with stable IDs,
canonical authority label/provenance and exact payload reference. Multiplicity
is an available discovery result while automatic resolution remains ambiguous;
stale/unavailable/lower-tier options remain non-selectable and fully visible in
T039. Eight synthetic choices are not truncated, ordering is stable and never
selects. Binding remains `pending_review`. Active Ulyanovsk returns exactly one
executable choice and its signed snapshot raw SHA-256 remains
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

**Result:** completed locally on 2026-08-30 in checkpoint commits `4f0c9dd`,
`3e41749` and `b7f8e76`. `CityScheduleChoiceSet` derives stable, complete
highest-tier choices from `RevisionPolicyAssessment`; it uses canonical
`PrayerAuthority.Name`, keeps equal labels independently identifiable and
marks only one resolved active choice executable. The separate authenticated
`city-schedule-choices/v1` endpoint supports active or exact staged revisions,
returns all eight synthetic options without pagination/top-N and preserves
T039's complete blocked-option surface plus `pending_review` handoff.

No migration was required: existing immutable v6 entities already contain all
choice/provenance fields and v7 already stores review requests. Real PostgreSQL
tests cover active Ulyanovsk, two staged equal-tier choices and stale
unavailability; migration/restore behavior remains v7. `make docs-check`,
`make test`, `make lint`, `make test-postgres`, `go test -race ./...`,
`make security-go` and `make secret-scan` pass. The pilot snapshot remains
byte-identical at the expected SHA-256. T038 and visual admin remain deferred;
no real regional authority or source was added.

## T041 — Android TV city and authoritative schedule setup flow

**Goal:** implement the missing TV-operated path from Mosque/location settings
through canonical city search and complete authoritative schedule-choice
discovery to an explicit non-authoritative `pending_review` proposal, while the
current signed last-known-good schedule remains active.

**In scope:** a device-bearer-scoped setup API that derives device/mosque and
server-selected immutable review revision; Cyrillic/alias city search with
debounce and superseded-request cancellation; duplicate-name disambiguation by
subject/type/timezone; D-pad/IME/back/focus-safe TV screens; complete 0..N T040
choice display without ranking/truncation; explicit request state; RU/EN and
720p/1080p/4K tests; PostgreSQL append-only audit; emulator evidence.

**Non-goals:** admin credentials on TV, direct approval/publication/activation,
Room or signed-snapshot mutation, T038 or any new real regional source, generic
calculation fallback, top-N, custom keyboard, nationwide scraping, production
deployment, signing keys or build-version naming work.

**Acceptance:** a provisioned TV can search and explicitly select a canonical
city, see all eligible choices and submit one proposal that remains
`pending_review`. Wrong/revoked/cross-device credentials and client
mosque/revision injection fail closed. Duplicate cities and multiple authorities
never auto-select. Failure/recreation retains the old Room snapshot. The
Ulyanovsk pilot remains byte-identical at SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

**Result:** completed locally on 2026-08-30 in checkpoints `6375915`,
`a5456ec`, `64ed91d`, `08500e5`, `cc02d51`, `824f279`, `ea37c5e`,
`5bab92f` and `8840ffa`.
The TV uses only its provisioned device bearer against a server-derived
device/mosque/revision boundary. PostgreSQL schema v8 can append only an
audited, idempotent `pending_review` proposal; it cannot approve, publish,
activate, sign or assign a snapshot.

The Mosque/location page now opens canonical Cyrillic/alias city search with
debounce and cancellation, disambiguates same-name candidates by subject/type/
IANA timezone, and exposes the complete 0..N T040 choice set. One or many
choices all require explicit selection; there is no top-N, preferred authority
or fallback. Each choice exposes its source, evidence label, approval identity
and explicit freshness state without creating a second source of truth. RU/EN
loading, empty, authorization, failure, unavailable, submitting and pending
states preserve the signed last-known-good schedule.
Controlled Android 16 TV-emulator evidence records the system IME, duplicate
cities, one/many choices, D-pad scrolling, unavailable/pending states and
IME-close focus recovery. This is not physical-TV evidence.

The original GitHub Actions failure on `7e56d1a` was the missing
`kotlinx-serialization-bom:1.6.3` strict-verification record. A later clean
runner on `cc02d51` exposed one additional KSP-resolved JetBrains coroutines
BOM POM hidden by the local cache; `824f279` pins only its independently
matched Maven Central SHA-256. A clean Gradle dependency home and the final
local `make test-android-all` both pass without disabling strict verification.

`make docs-check`, `make test`, `make lint`, `make test-postgres`,
`make test-android-all`, `go test -race ./...`, `make security-go` and
`make secret-scan` pass. Release excludes the debug evidence activity and Room
schema is unchanged. The Ulyanovsk snapshot remains byte-identical with ID
`ulyanovsk-second-cathedral-2026-pilot-local-v2` and raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
T038 remains `DEFERRED`; no real regional source was added.

## T042 — traceable Android APK version and build identity

**Goal:** make every distributable NamazTime Android APK unambiguously
traceable to an application version, monotonically increasing Android version
code, build variant, exact Git commit and APK checksum, and expose the same
identity in TV diagnostics without touching prayer data.

**In scope:** one checked-in Android version source; `0.5.0-pilot.1` / code `4`
for the next offline pilot; embedded commit/variant/clean-state metadata;
RU/EN Diagnostics projection; a fail-closed clean-tree signed-pilot packaging
command that emits a versioned outside-repository APK plus checksum/manifest;
artifact verification; tests; and a controlled API 36 emulator update from the already
installed `0.4.0-pilot-local` / code `3` package.

**Correctness-sensitive unknowns:** physical-TV update behavior remains
`UNKNOWN`; two operator-chosen offline backups of the permanent APK signing
key still require owner action; Git tags/GitHub Releases and remote app-update
transport are not yet selected. These unknowns do not permit an unsigned,
dirty or untraceable pilot artifact.

**Non-goals:** changing the application ID or APK signing key; changing Room,
the bundled schedule, approval, signing trust or snapshot bytes; onboarding a
new prayer source; T038; Google Play; remote self-update; creating a tag,
GitHub Release, push or PR without a separate owner command.

**Acceptance:** version data has one validated source; debug/release/pilot are
visibly distinct; Diagnostics shows version/code plus variant/commit and dirty
state; the pilot packaging path rejects a dirty tree and wrong embedded
identity; its filename/manifest/checksum map back to the exact clean commit;
Android accepts code `4` over the existing code `3` package with the same
certificate and retained install identity; repository gates pass; and
`ulyanovsk-second-cathedral-2026-pilot-local-v2` remains byte-identical at raw
SHA-256 `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

**Status:** `DONE` on 2026-08-31.

**Result:** checkpoints `609dda9` and `8f3713d` introduce the validated
`version.properties` source, `0.5.0-pilot.1` / code `4`, typed RU/EN Diagnostics
identity and fail-closed APK inspection/packaging. Debug, remote release and
pilot identify themselves independently; a signed pilot bundle can be produced
only from a clean tree and is written outside the repository with its checksum
and manifest. The clean artifact maps to exact checkpoint
`609dda98a92417b48b6771a8f3d3dc0b0940ff25`, APK SHA-256
`87ab34c42ccb8adc6709f65bce113586bc5c1206322a9f956d884ef8256e2509`
and the pinned certificate.

`CONFIRMED_RUNTIME` on the Android 16 / API 36 TV emulator: `adb install -r`
updated the retained package from `0.4.0-pilot-local` / code `3` to
`0.5.0-pilot.1` / code `4` without changing its first-install timestamp;
Diagnostics shows `pilot · 609dda98a924 · чистая` and the cold-launched display
uses the existing last-known-good schedule. Physical-TV behavior remains
`UNKNOWN`. `make docs-check`, `make test`, `make lint`, `make test-postgres`,
`go test -race ./...`, `make test-android-all`, `make security-go` and
`make secret-scan` pass. Room schemas are unchanged and the bundled signed
snapshot remains byte-identical at raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
No migration, tag, GitHub Release, push or remote update mechanism was added.

## T043 — Android TV operator UX correctness and media-picker hardening

**Goal:** correct the four owner-reported Android TV defects—truncated main QR
copy, overflowing Iqamah controls, a dead-end custom-image action on TVs without
a document provider, and the redundant donation collection-link field—without
changing prayer/source/registry/approval/snapshot semantics.

**In scope:** one measured QR text-fit contract shared by Settings preview and
the public panel, reject-before-save validation plus deterministic legacy-safe
rendering; a compact five-row Iqamah editor with explicit per-prayer “use
schedule” reset and one-minute controls; a testable OpenDocument → system Photo
Picker → permission-gated MediaStore capability cascade with a bounded D-pad
fallback, localized import results and focus restoration; removal/tombstoning
of the donation collection-link field from model, persistence writes,
validation, focus, Settings and four-row display; RU/EN/adaptive tests;
controlled API 36 runtime evidence; and Android version `0.5.1` / code `5`.

**Correctness-sensitive unknowns:** actual physical-TV picker/provider and
permission presentation remains `UNKNOWN`; the controlled emulator can prove
only its own capability path. QR representative-distance scanning and physical
overscan remain separate acceptance. Existing over-limit persisted QR copy must
fail safe without corrupting any other operator preference.

**Non-goals:** general visual redesign; filesystem manager; all-files/write
storage permission; startup media scan; arbitrary files; remote asset upload;
new prayer source or region; registry/city/setup behavior change; Room or
PostgreSQL migration; snapshot rewrite/re-sign; T038; application ID/signing
identity change; push or PR.

**Acceptance:** accepted QR copy has no ellipsis/clip at 720p/1080p/4K and the
first unsafe input cannot be saved; all five Iqamah rows plus Save/Return fit at
960×540 and each prayer can clear only its local override; custom-image actions
never launch an unresolvable intent and at least one emulator import succeeds;
permission denial/revocation/cancel and every import result are bounded; legacy
collection-link data neither reappears nor counts as active detail and is
tombstoned on the next save; Donation renders exactly four detail rows; strict
repository gates pass; and the Ulyanovsk pilot snapshot remains byte-identical
at raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

**Status:** `DONE` on 2026-08-31.

**Result:** checkpoints `6a60a0c`, `f0b9340`, `8e11fbd`, `bf3fc6c` and
`1cc2119` implement the shared measured QR fit/reject contract, compact
five-row Iqamah editor with explicit per-prayer signed-schedule reset, ADR 0018
OpenDocument → Photo Picker → permission-gated MediaStore cascade with bounded
feedback/focus recovery, complete donation collection-link tombstone/removal
and Android `0.5.1-pilot.1` / code `5`. Targeted runtime review then produced
small local corrections `1982d40`, `a674a0e` and `cfa4621` for the Donation
current-prayer label and Mosque source/timezone visibility; no general redesign
was introduced.

`CONFIRMED_RUNTIME` on the controlled Android 16 / API 36, 1920×1080 emulator:
the long QR message is complete; all five Iqamah rows and reset transition are
visible; missing OpenDocument falls through to the system Photo Picker; one
actual PNG import reaches an app-private preview with no media grant; Donation
has no collection-link input and renders four rows; and the final signed clean
APK installs in place as code `5` while retaining the package's first-install
identity. Evidence and hashes are under
`docs/evidence/t043-operator-ux/`.

`make docs-check`, `make test`, `make lint`, `make test-postgres`,
`make test-android-all`, `go test -race ./...`, `make security-go` and
`make secret-scan` pass. No Room/PostgreSQL migration, prayer/source/registry
change or new real source was added. T038 remains `DEFERRED`; physical-TV
picker/overscan/readability and representative-distance QR scanning remain
`UNKNOWN`. The packaged Ulyanovsk snapshot remains byte-identical at raw
SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

## T044 — next-prayer card architectural watermark fidelity

**Goal:** refine only the decorative watermark behind the Android TV
`NextEventCard` so its owner-authorized pointed architectural arch has the
correct broad silhouette, position and subdued visibility, with a lantern only
if an original filled rendering remains visually convincing.

**In scope:** normalized geometry relative to the complete next-event card;
22–25 percent arch width, 19–22 percent apex height, 37–42 percent shoulder
transition and a bottom edge ending at the card boundary; cubic Bézier arch
curvature, semantic-color stroke/glow treatment, optional original filled
lantern, geometry and long-title regressions, at least three controlled API 36
1920×1080 screenshot iterations, short `Аср`, long `Фаджр · завтра`
and alternate-background acceptance evidence, and the next traceable pilot
version `0.5.2-pilot.1` / code `6`.

**Non-goals:** prayer/countdown/next-event logic; title, prayer, divider or
countdown anchors; card size/shape/border; QR, prayer table or other screen
redesign; Room, registry, source, approval or persistence changes; signed
snapshot rewrite; application/signing identity change; new ADR; push or PR.

**Correctness-sensitive unknowns:** emulator review can establish only the
controlled 960×540 composition; physical-TV overscan, panel response and
representative-distance subtle-decoration readability remain `UNKNOWN`.

**Acceptance:** the arch remains clipped, non-semantic, non-focusable and
behind foreground content; normalized bounds prevent another narrow-arch
regression; `Аср` and `Фаджр · завтра` remain complete with unchanged
foreground anchors; another built-in background retains subtle visibility;
the final signed pilot installs in place as code `6`; and
`ulyanovsk-second-cathedral-2026-pilot-local-v2` stays byte-identical at raw
SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

**Status:** `DONE` on 2026-08-31.

**Result:** checkpoint `5ebf023` moves the watermark Canvas to the complete
card coordinate space and pins the final normalized arch at `left=0.04`,
`right=0.27`, `apexX=0.155`, `apexY=0.205`, `shoulderY=0.40` and
`bottomY=1.0`. Symmetric cubic Bézier segments now form the broad pointed arch;
an original filled warm lantern was retained after controlled visual review.
The title, prayer, divider, countdown and card geometry remain unchanged.

TDD first reproduced the old partial-card Canvas (`card.top=124dp` versus
`watermark.top=143dp`), then geometry and Compose regressions passed at the
720p, normalized 1080p and 4K density profiles. Seven controlled API 36
comparison/acceptance captures cover three iterations plus short `Аср`, long
`Фаджр · завтра`, Golden Dusk and Blue Hour; the signed-pilot screen is recorded
separately under `docs/evidence/t044-next-prayer-watermark/`.

The clean signed artifact is `0.5.2-pilot.1` / code `6`, APK SHA-256
`0b27df4f451b7a39fc35afcdbd61427c07a42840b9a6bce497b397a53cf68e09`
and certificate SHA-256
`da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`.
It passed artifact verification and installed in place on the API 36 TV
emulator without changing `firstInstallTime`. `make docs-check`, `make test`,
`make lint`, `make test-postgres`, `make test-android-all`,
`go test -race ./...`, `make security-go` and `make secret-scan` pass. No Room
or PostgreSQL migration was required because the change is presentation-only.
The signed Ulyanovsk snapshot remains byte-identical at raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
Physical-TV overscan, panel response and representative-distance subtlety
remain `UNKNOWN`.

## T045 — physical-TV QR reliability and alternative schedule presentation

**Goal:** harden every shared QR surface, persist public Iqamah visibility and
add `RIGHT_SIDE_COMPACT` alongside unchanged `STANDARD` schedule geometry.

**In scope:** no QR overlay/clipping, integer module rasterization at final size;
DataStore visibility default true and independent layout default STANDARD;
D-pad settings; one shared prayer presentation; foreground strictly in the
right half including retention shift, six rows, clock/date/countdown/identity,
optional readable QR with no empty placeholder; RU/EN and 720p/1080p/4K.

**Non-goals:** source/authority/registry/T038, setup semantics, approval, Room
schema, signed snapshot, application/signing identity or general redesign.

**Acceptance:** final rendered QR decode evidence; persistent visibility without
clearing Iqamah configuration; standard regression; measured right-rail geometry
and screenshot-driven emulator iteration for both Iqamah states and QR absence;
Donation QR; repository docs/test/lint/Postgres/Android/race/security/secret gates;
0.6.0 code 7 pilot.1 clean signed handover APK; unchanged snapshot SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
Physical-camera outcome remains `PHYSICAL_QR_RETEST_REQUIRED` until owner retest.

**Status:** DONE (local implementation and handover). Commit `95bd858` adds the
shared integer-module QR, persistent visibility and right rail; all required
gates pass with 496 Android tests. Sixty-one valid emulator frames include a
true 4K Presentation, 50/50 QR decodes and 26/26 protected-left-half comparisons.
The clean signed `0.6.0-pilot.1`/code-7 artifact passes identity/assets/certificate
checks and upgrades the emulator from code 6 in place; its retained QR decodes.
See `docs/evidence/t045-tv-presentation/` for measurements, exact hashes and
handover manifest. Physical scan remains `PHYSICAL_QR_RETEST_REQUIRED`.
Local checkpoint commits only; no push or PR.

## T046 — RIGHT_SIDE_COMPACT visual fidelity to owner reference

Status: DONE (local). Checkpoint `c878d5e`; clean signed 0.6.1-pilot.1 / code 8
upgrades code 7 in place. All gates, 499 Android tests, two comparison passes
plus final review, 48 final matrix frames, 42 QR/blur decodes and protected-left
checks pass. STANDARD has zero changed pixels; snapshot is unchanged. See
`docs/evidence/t046-compact-reference/`. Physical-TV acceptance remains
`UNKNOWN`; no push/PR.

Owner authorizes layout comparison against root `new_compact.png`; root
`qemu-system-x86_64_9ziWsns3jL.png` is supplied before evidence. Neither image
is an application asset. Keep NamazTime branding, existing backgrounds,
original ornaments, runtime identity/data and shared presentation state.

- Center brand pill, larger mosque identity and ornamented locality; retain
  independently placed, focusable Settings gear within safe bounds.
- Main area: equal-width prayer schedule on the left, next-prayer/date-clock
  stack on the right. Six icon/name/adhan rows, unchanged active semantics,
  optional Iqamah, localized schedule heading and warm subdued highlight.
- Large next-prayer/countdown hierarchy with the existing architectural
  watermark; preserve tomorrow/Jumu'ah labels and mosque-local time source.
- Configured campaign: full-width bottom card, large left QR, vertical divider,
  localized kind, title and complete allowed subtitle on the right. No empty
  panel without QR; use the released space in the main area.
- Reference normalized guides: rail x≈484..928, header y≈15..102, main
  y≈108..360, campaign y≈367..504; preserve existing safe insets and the full
  ±2 dp retention budget, protecting at least half the full viewport.
- Preserve T045 integer QR pixels, quiet zone and no badge/clipping; shared
  engine/state/clock/campaign semantics; STANDARD pixels; signed snapshot,
  Room/source/approval/registry/T038/debug-setup isolation.
- Test RU/EN, QR on/off, Iqamah on/off, active/tomorrow/Jumu'ah, allowed long
  copy, 720p/1080p/4K and every retention phase. At least two controlled API 36
  1080p screenshot comparison iterations, plus fresh matrix and measured bounds.
- ADR 0017 patch 0.6.1 / code 8 / pilot.1, same application/signing identity.
- Run docs/test/lint/strict Android, Go race/security/secret and applicable
  PostgreSQL completion gates. Preserve snapshot SHA-256
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
- Record evidence, version, gates, STANDARD/QR regressions, geometry, commits
  and remaining physical-device unknowns; update PLANS/UI spec. No new ADR,
  downloaded assets, unrelated redesign, push or PR.

## T047 — premium visual art direction for RIGHT_SIDE_COMPACT

Status: DONE locally. Checkpoints `c4eae93` and `1a557d3`; 504 Android tests,
66 runtime frames, 54 QR/blur/protected-half checks, zero other-screen pixel
differences and clean signed code-9 in-place handover. Physical TV remains UNKNOWN.
See `docs/evidence/t047-compact-premium/README.md`.

Scope: Refine only the T046 compact visual system against the
owner-authorized `new_compact.png`: selected-image luminance, translucent glass,
TV typography, champagne icons/outlines and luminous active row. Preserve the
composition, protected left half, shared QR/Iqamah semantics and all signed data.
Three screenshot passes and three backgrounds, adaptive/QR/other-screen
regressions and repository gates precede local versioned handover. No push/PR.

## T048 — final elegance and cinematic polish for RIGHT_SIDE_COMPACT

Status: DONE (local). Baseline `aa23f52` was clean and matched `origin/main`;
latest baseline CI 34106800146 passed. Implementation checkpoint `4f5b158`
contains the complete compact-only change and the clean signed pilot source.

Scope: preserve T046 composition and T047 compact-only foundation while making
RIGHT_SIDE_COMPACT calmer and more cinematic. Show minute-only clock/countdown
without changing second-precise state or `PrayerTimeEngine`; strengthen regular
versus hero typography, warm semantic accent, depth-led glass, active row,
campaign hierarchy and bounded warning treatment. Preserve STANDARD, Donation,
Settings, shared QR/Iqamah behavior, safe rail, retention shifts and signed data.

The owner-authorized `new_compact.png` remains a `CONFIRMED_PUBLIC` art-direction
target only. Review found none of the eight built-ins closes the luminous dusk
gap. Add one original generated `Luminous Dusk` built-in as a selectable,
non-default asset; record the prompt/provenance and never package the reference.
Run three API 36/1080p passes plus Golden Dusk/Luminous Dusk/Blue Hour,
approved/attention, short/long identity, long-copy, 720p/1080p/native-4K,
retention, protected-half, QR/blur and pixel-identical STANDARD regressions.
Advance to 0.6.3-pilot.1/code 10 under ADR 0017 and preserve snapshot SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
No new ADR, backend/schema/source change, push or PR.

Result: the compact display now presents minute-only clock/countdown values,
with the countdown ceiling-rounded so it cannot announce the next whole minute
too early; exact seconds remain in semantics and engine state. Typography,
two-line identity, semantic warm-gold/glass roles, the active row, bounded
attention chip and adaptive campaign hierarchy were refined without editing the
STANDARD renderer. The original selectable Luminous Dusk asset was generated
without the owner reference and appended after the eight existing backgrounds,
preserving their D-pad adjacency and default.

Three deliberate API 36/1080p reviews and the final 72-frame
720p/1080p/native-4K matrix passed: 60/60 direct QR decodes, 60/60 blurred QR
decodes, 60/60 protected-left-half comparisons, and a minimum foreground left
edge of 50.15625%. The T047 versus T048 fixed-clock STANDARD comparison is
pixel-identical. Full docs/test/lint/strict-Android, Go race/vulnerability and
secret gates pass with 512 Android tests. PostgreSQL was not required because
no backend/schema/provider/publication path changed.

The clean `0.6.3-pilot.1` / code-10 artifact is bound to `4f5b158`, the pinned
certificate and unchanged snapshot SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
It verified independently and installed with `adb install -r` over code 9 while
preserving the 2026-08-29 first-install timestamp and persisted pilot content.
The signed compact frame retains the real Ulyanovsk identity, Golden Dusk,
Iqamah rows, campaign and decodable QR. Physical-TV viewing distance,
overscan/power behavior and real-phone scanning remain `UNKNOWN`/deferred.
See `docs/evidence/t048-compact-polish/README.md`.

## T049 — nationwide verified first-party prayer-source onboarding

Status: IN_PROGRESS (2026-09-08). Baseline: `325a343` on clean `main`, actual
origin/main confirmed; prior CI failure on a local-only reference link reproduced.

The [full owner assignment](docs/tasks/T049-nationwide-first-party-onboarding.md)
governs scope and all acceptance criteria. [ADR 0019](docs/adr/0019-public-first-party-source-qualification.md)
supersedes mandatory external written approval for public first-party sources,
global-tier suppression of independent authorities and ordinary synthetic setup.

Deliver: re-audit prior research; research every canonical-catalog subject;
superseding machine-readable evidence registry; deterministic qualified real
providers/policies; honest qualification/endorsement separation; verified signed
local materialization and normal real city/authority selection; unavailable for
unsupported cities. Preserve all independent choices and the existing Ulyanovsk
pilot. Close T038 after a verified second regional adapter, not after research.

Validation includes real representative source-pattern E2E, drift/stale/scope
and signing/last-known-good regressions, docs/skills, full Go/race/security,
PostgreSQL/restore and strict Android gates. Record progress in PLANS.md.
Local checkpoint commits are allowed; push/PR, deployment, key changes,
organization contact and production-data deletion are not authorized.
