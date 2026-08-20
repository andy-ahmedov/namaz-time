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

**Result (local source slice, 2026-08-20):** the supplied RDUM Ulyanovsk PDF is
retained byte-for-byte with provenance and SHA-256; a source-specific strict
controlled-transcription adapter normalizes all 365 days, retains al-Isfar and
collective Dhuhr as separate candidate metadata, and enforces the printed May
10→11 / August 2→3 Fajr/Isha transition markers. All 31 August rows are
compared against the earlier photo fixture and every numeric disagreement is
recorded without choosing a value. Candidate/diff output is deterministic and
stays `needs_review`; a regression proves it cannot publish without a named
approval. Named D-002/D-009 approval, D-013 production signing/trust and a
physical canary/rollback drill remain `BLOCKED`, so T010 is not marked DONE.

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
