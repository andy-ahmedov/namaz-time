# ARCHITECTURE.md

## Architecture goal

The system must keep showing an approved schedule when the network, backend, parser or device process fails. Accuracy, provenance and recovery have priority over real-time freshness and feature count.

## System context

```text
Official authority / mosque / approved calculation profile
                      |
                      v
              Go ingest adapters
                      |
              raw artifact store
                      |
           normalize -> validate -> diff
                      |
               human approval
                      |
            snapshot publisher/signing
                      |
          HTTPS + ETag / version manifest
                      |
             Android TV synchronizer
                      |
          verify -> stage -> atomic activate
                      |
               Room / SQLite
                      |
                  TV UI
```

The TV is a display node, not a web scraper and not the authority that decides religious correctness.

## Repository layout

```text
apps/tv-android/              Kotlin Android TV client
cmd/api/                      Go HTTP API entry point
cmd/ingestor/                 Go scheduled/manual ingest entry point
internal/domain/              source-independent domain rules
internal/providers/           source adapters
internal/publication/         validation, approval and snapshot building
internal/devices/             pairing, manifests and heartbeats
web/admin/                    operator/approver UI
contracts/                    OpenAPI and JSON Schemas
examples/                     synthetic fixtures only
docs/adr/                     architecture decisions
research/                     sanitized evidence and research notes
```

The code directories are intentionally empty in this docs-first package. Codex creates them through bounded tasks in [CODEX_TASKS.md](CODEX_TASKS.md).

## Backend style

Start as a modular monolith in Go. Separate modules by domain boundary, not by deployment:

- `catalog`: mosque, locality, timezone and device bindings;
- `sources`: source registry, permission and retrieval policy;
- `ingest`: fetch/import, raw hash and parser execution;
- `schedule`: normalized adhan times and comparison;
- `iqamah`: mosque-local rules and overrides;
- `approval`: review state and audit trail;
- `publication`: immutable signed snapshots;
- `campaigns`: QR and announcements;
- `devices`: pairing, manifest, heartbeat and rollout;
- `admin`: operator and approver workflows.

Do not introduce a message broker or microservices until measured load or organizational boundaries require them. A database-backed job/outbox table is enough for the first releases.

## TV client layers

```text
presentation/    Compose for TV screens and focus behavior
domain/          next-event, countdown and rule resolution
repository/      local schedule/config interfaces
data/local/      Room, DataStore, staged snapshot files
data/remote/     manifest/snapshot HTTP client
sync/            WorkManager and explicit "sync now"
platform/        boot, keep-screen-on, clock/network diagnostics
```

Composables only observe local state. A network response never directly mutates what is visible; it first passes signature/schema/domain validation and an atomic activation transaction.

### Android local baseline (T003–T004)

Room schema version 1 stores immutable snapshot provenance/integrity metadata,
daily adhan rows, separate iqamah rules/overrides, Jumu'ah sessions, campaigns,
theme selection and the display's active/previous snapshot pointers. Foreign
keys cascade snapshot-owned rows, while active/previous references prevent a
selected snapshot from being deleted. The exported schema JSON is committed as
the migration baseline. T004 adds strict Android snapshot decoding/domain
validation, full transactional import and atomic activation. Room schema v2
adds prayer-day source flags (existing v1 rows initialize them to `[]`), and
schema v3 retains optional provenance and theme-asset integrity references.
Explicit v1→v2→v3 migrations preserve snapshots, prayer rows and the active
pointer while retaining v1 as the rollback baseline. Prayer-day reads always sort by
mosque-local date; SQLite's unspecified row order is never treated as schedule
order.

On first launch, the app reads the explicitly synthetic example from its APK
assets, validates it before persistence, imports all supported child records in
one transaction and only then changes the display pointer. A corrupt asset
leaves Room without an active schedule and exposes a bounded support code.
When an active selection already exists, startup verifies its local coverage;
an incomplete active snapshot restores a complete previous selection, while a
database read failure or no usable snapshot produces a bounded diagnostic.
Local schedule-flow corruption is also mapped to a bounded UI support code
instead of escaping the Compose collector. Startup recovery revalidates the
persisted timezone, provenance/integrity envelope and complete ordered prayer
coverage before trusting either active or previous selection.
Cryptographic signature verification is intentionally added at T008; T004 does
not treat the synthetic placeholder integrity envelope as authentic production
data.

T005 replaces the launch placeholder with a responsive, built-in offline main
display. It renders the six daily adhan rows from immutable Room-backed local
state and keeps iqamah in its own column: sunrise is explicitly not applicable,
while unresolved mosque-local iqamah remains unset. Synthetic classification
is visible and never described as official. The only focusable display action
opens settings and has explicit
initial focus across the tested 720p, 1080p and 4K density profiles.
Source state fails conservatively: synthetic, calculated, unapproved
and not-yet-authenticity-verified production data receive distinct labels.
Countdown uses a fixed-width monospaced region so the T006 ticker cannot shift
the status layout.

T006 adds a source-independent time engine and an injected UTC clock. Each
instant is converted at the domain boundary with the mosque's named IANA zone;
device timezone and locale never select the displayed schedule date. The
engine resolves exact-date iqamah overrides before the single highest-priority
matching date-range/weekday rule, preserves an unset result, rejects ambiguous
top-priority rules and rejects offsets that cross the mosque-local date.
Sunrise is excluded from the main countdown by an explicit default policy.
Adhan, resolved iqamah and active Friday Jumu'ah salah sessions participate as
separate events, and the next day's Fajr is considered after the final event.
Jumu'ah sessions are displayed separately and never mutate the Dhuhr row.
Nonexistent or ambiguous DST wall times, invalid daily ordering/rule state and
dates outside local coverage produce bounded diagnostics. The full time model is validated before Room activation
and again when an immutable local snapshot reaches the display; per-second
ticks then resolve only from that local state.

T007 reads campaign rows from the same immutable active Room snapshot. A pure
domain boundary accepts only bounded, exact lowercase `https://` targets with
a host and without user information, validates start-inclusive/end-exclusive
lifecycle instants, and fails closed when more than one campaign is active.
The TV generates the QR bitmap locally with a four-module quiet zone and exact
black/white contrast; it never renders the raw destination URL or requires a
network permission. Invalid, future, expired, ambiguous or QR-generation
failures hide only the optional panel and cannot replace the prayer display.
Settings may preview one otherwise valid campaign independently of lifecycle
and clearly labels it as preview. The audit model stores campaign ID plus a
SHA-256 target fingerprint; durable publication audit and domain allowlisting
remain later work pending D-010.

Small operator UI preferences, including the last focused settings section and
reduced-motion default, use a single Preferences DataStore instance. Schedule
data never moves into DataStore, and the Compose shell contains no network
client.

## Source ingestion pipeline

1. **Retrieve or import.** Store raw bytes unchanged when terms permit, otherwise store immutable metadata plus an approved fixture.
2. **Fingerprint.** Record SHA-256, byte count, content type, retrieval time and source revision headers.
3. **Parse.** A versioned provider adapter creates normalized rows but cannot publish them.
4. **Validate.** Check schema, timezone, date coverage, duplicate dates, missing prayers and suspicious minute deltas.
5. **Diff.** Compare against the currently approved schedule and produce a human-readable per-day/per-prayer diff.
6. **Approve.** Authorized mosque/authority representative accepts or rejects the candidate.
7. **Build.** Create a deterministic immutable snapshot for a mosque and effective period.
8. **Sign.** Sign the canonical payload with an offline-protected Ed25519 publication key.
9. **Roll out.** Publish manifest first to a canary group, then broader devices.
10. **Observe.** Track activation, coverage remaining, signature failures and rollback.

## Provider interface

Conceptual Go interface:

```go
type Provider interface {
    Kind() ProviderKind
    Retrieve(ctx context.Context, req RetrieveRequest) (RawArtifact, error)
    Parse(ctx context.Context, raw RawArtifact) (CandidateSchedule, error)
    ValidateConfig(cfg ProviderConfig) error
}
```

`Provider` does not know about Android or UI. `CandidateSchedule` is unapproved by definition.

Supported kinds:

- `official_api` — documented authorized API;
- `official_file` — official CSV/XLSX/JSON/PDF;
- `official_html` — controlled server-side parser of an official table;
- `mosque_calendar` — calendar maintained or explicitly adopted by a mosque;
- `calculation_profile` — deterministic calculation with explicit approved parameters;
- `manual_import` — operator-imported CSV/JSON with full provenance.

## Published snapshot

A snapshot is immutable and contains:

- schema and snapshot version;
- mosque identity and IANA timezone;
- source identity, scope, method and approval;
- daily adhan rows for an effective period;
- iqamah rules and exact-date overrides;
- Jumu'ah sessions;
- QR/announcement campaigns;
- referenced theme assets by hash;
- payload hash, key ID and signature.

See [contracts/prayer-snapshot.schema.json](contracts/prayer-snapshot.schema.json).

## TV synchronization protocol

1. TV requests its small device manifest with `If-None-Match`.
2. `304` means no update.
3. On a new snapshot, TV downloads to a staging file.
4. Verify transport status, size limit, canonical SHA-256, Ed25519 signature, JSON Schema, timezone and date coverage.
5. Import into staging tables in one Room transaction.
6. Run domain validation and next-event smoke checks.
7. Atomically switch `active_snapshot_id`.
8. Keep at least one prior valid snapshot for rollback.
9. Report a privacy-safe heartbeat later; display remains independent from heartbeat success.

Never erase the active snapshot before the new one is proven valid.

## Prayer-time resolution

### Adhan

The daily row is already resolved before publication. A TV must not silently recalculate an official row.

### Iqamah

Resolution order:

1. exact-date override;
2. highest-priority matching date-range + weekday rule;
3. seasonal rule;
4. base mosque rule;
5. unset.

Modes are `fixed_time` and `offset_after_adhan`. An unset iqamah stays unset; the client must not guess.

### Next event

The domain clock uses mosque timezone, not the device default. The sequence must explicitly define whether sunrise and optional events participate in the main countdown. After Isha, the next event may be next-day Fajr.

## Time and timezone

- Store IANA IDs, for example `Europe/Ulyanovsk`.
- Convert `Instant` to mosque-local date/time at the domain boundary.
- Detect but do not trust a wrong device timezone.
- Detect implausible device clock by comparing with signed server time metadata when online; never abruptly alter OS time.
- Schedule date rollover based on mosque timezone.
- Cache at least 90 future days; annual coverage is preferred.

## Assets and themes

Built-in themes are packaged with the app. Remote custom images are content-addressed:

```text
asset_id -> sha256 -> byte length -> media type -> dimensions -> orientation
```

The TV downloads an asset to staging, validates type/dimensions/hash, then activates it. Keep a built-in fallback background. MVP excludes video backgrounds.

## Autostart and kiosk modes

- **Consumer/best-effort mode:** boot receiver, vendor setting guidance, keep-screen-on and process recovery. Behavior varies by OEM.
- **Managed mode:** device owner/DPC or compatible EMM, app allowlisted for lock task and optionally configured as Home. This is the reliable digital-signage path.

Treat the modes as separate capabilities and test matrices.

## Failure behavior

| Failure | Required behavior |
|---|---|
| no network | continue from local snapshot |
| backend 5xx | backoff; keep current snapshot |
| parser drift | block candidate; alert operator |
| invalid signature/hash | reject before import |
| new snapshot has gap | reject before activation |
| device clock wrong | show diagnostic warning; resolve with mosque timezone |
| corrupt active DB | attempt prior snapshot restore; show safe diagnostic state |
| campaign image unavailable | hide campaign or use safe local placeholder |
| source expired | warn; never silently switch provider |

## Observability

Backend metrics:

- source retrieval success/latency/status;
- parser version and schema-drift failures;
- schedule coverage and diff magnitude;
- approval age;
- snapshot rollout/rollback;
- devices by last-seen and active snapshot.

TV diagnostics:

- app/build version;
- device model/OS;
- mosque and timezone;
- active/previous snapshot IDs;
- signature key ID;
- last successful sync;
- coverage end and days remaining;
- clock/timezone mismatch;
- boot mode and overlay/device-owner status.

Do not include precise user location, Wi-Fi SSID, tokens or full URLs containing secrets in diagnostic export.

## Architectural decisions

See:

- [ADR 0001 — source authority and signed snapshots](docs/adr/0001-source-authority-and-signed-snapshots.md)
- [ADR 0002 — TV offline-first](docs/adr/0002-tv-offline-first.md)
