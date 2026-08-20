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
