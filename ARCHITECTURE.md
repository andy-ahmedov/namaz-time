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

Production publication has two independent signatures. A dedicated approver
key signs the exact candidate/diff/warnings and canonical mosque prayer policy;
the approval public trust bundle cannot sign snapshots. The publisher verifies
that receipt and recreates approval-bound canonical bytes, and an isolated KMS/HSM signer
returns Ed25519 signatures over both the snapshot and a domain-separated
provenance/approval/trust/actor/audit-chain attestation. Finalization verifies both against a
versioned environment-scoped public trust bundle before emitting immutable
snapshot and signer-attested hash-chained receipt. Go registry admission
requires this receipt and anchors one registry artifact to the durable release
ledger head. Publisher finalization advances that head under an exclusive
compare-and-swap lock, rejecting repeated genesis and sibling forks. Go and
Android share direct-predecessor trust transition
and active/retired/revoked semantics; the device clock is
not used to authorize a signing key. See ADR 0011 and
[PUBLICATION_SIGNING_RUNBOOK.md](PUBLICATION_SIGNING_RUNBOOK.md).

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

The T018 display route owns a Compose `DisposableEffect` that sets the host
view's `keepScreenOn` flag and restores its prior value when that route leaves
composition. The unavailable/recovery projection is still display mode;
settings and background sync do not hold this flag. This uses the platform view
hint, not a wake lock, and adds no permission.

T021 derives a deterministic, bounded foreground offset from the display
route's existing injected clock. Every ten-minute slot selects one of six
positions within ±2 dp on each axis. The offset is applied inside the shared
overscan-safe frame to both available and unavailable display projections;
settings, the static atmospheric background, focus order and persisted state
are unaffected. It has no network dependency and makes no panel-protection
guarantee.

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

The original T004 synthetic example is now test-only. In the explicitly
non-release T022 pilot-local build, first launch reads an immutable approved
Ulyanovsk 2026 snapshot plus public-only, disjoint local trust bundles from the
debug source set. It verifies the canonical hash/Ed25519 signature and exact
trust environment separation before persistence, imports all supported child records in
one transaction and only then changes the display pointer. A corrupt asset
leaves Room without an active schedule and exposes a bounded support code.
When an active selection already exists, startup verifies its local coverage;
an incomplete active snapshot restores a complete previous selection, while a
database read failure or no usable snapshot produces a bounded diagnostic.
Local schedule-flow corruption is also mapped to a bounded UI support code
instead of escaping the Compose collector. Startup recovery revalidates the
persisted timezone, provenance/integrity envelope and complete ordered prayer
coverage before trusting either active or previous selection.
The ephemeral pilot-local private key is discarded after fixture generation;
it is not a production key, KMS substitute or API registry credential. Release
builds package neither this schedule nor its local trust assets and continue to
depend on pairing/T009 plus the ADR 0011 production trust deployment.

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

D-014 makes the coverage boundary normative: last-known-good is not permission
to extrapolate. It remains visible only while the current mosque-local date is
covered by its signed daily rows. The first uncovered date produces the safe
unavailable screen; the TV never swaps to calculation or another provider.

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

T017 establishes a single Compose design system above the existing T005–T007
behavior. Semantic dark-surface, text, amber state, warning, separator and
focus tokens are shared by the main display, settings, QR and safe unavailable
screen. T028 expands the packaged offline background set to eight images and
adds a separate device-local custom slot. The system document picker grants
temporary read access; the app validates type, byte size, decoded dimensions
and pixel count, normalizes to JPEG, and atomically replaces an app-private
copy. Composables decode only that local copy and fall back to the packaged
Golden dusk asset when it is missing or corrupt. No content URI, broad storage
permission, network path or schedule mutation is introduced.

The main display uses an overscan-safe content frame around a dominant local
next-event card, mosque-local date/clock card, six-row adhan/iqamah table and
iqamah/source strip. Optional QR gets an independent full-height column so it
cannot shrink prayer rows or the local clock. Room remains the only content
source, while the T006 result supplies event kind/time, countdown and resolved
iqamah/Jumu'ah. Presentation code never guesses a missing iqamah or derives an
iqamah countdown across a possibly ambiguous wall-clock transition.

T008 implements the first provider as a strict, network-free `manual-csv/v1`
adapter. Raw artifact capture is separate from the human transcription; the
candidate binds both SHA-256 values, a normalized-candidate hash, parser
version, mosque scope and named IANA timezone. Exact header drift, invalid
times/order, gaps, duplicates, scope mismatch, insufficient coverage and
ungranted permission fail closed. Source markers, large day-to-day deltas and
collective-in-mosques values remain warnings that a later approval must
explicitly acknowledge. The provider can emit only `needs_review` or
`validation_failed`. `Parse` revalidates the complete source record even when
the caller bypasses JSON decoding, so a manual transcription cannot claim an
`official_*` provider kind or a disabled retrieval policy.

The separate publication package recomputes the candidate and diff bindings,
requires an exact signed human approval and mosque prayer policy for production,
builds a deterministic snapshot and signs
the canonical payload defined by ADR 0003. Android accepts production data
only through that authenticated-byte gate; the only other activation path is
the explicitly synthetic bundled fixture. At the T008 checkpoint the app still
has no network permission and all activation remains inside the existing Room
transaction. T009 adds only the permissions required for the separate sync
layer; display repositories remain network-free.
Go and Android both reject malformed UTF-8, explicit JSON nulls and invalid
typed iqamah, Jumu'ah, campaign or theme children before activation.

T010 adds exactly one source-specific `official_file` adapter for the supplied
RDUM Ulyanovsk 2026 PDF. The 35 MB PDF is retained unchanged and hash-bound;
the runtime parser consumes a strict, visually reviewed CSV transcription, not
Poppler or OCR. The candidate retains recommended al-Isfar as source-only
metadata and the printed collective Dhuhr separately from onset. It has no
daily Hijri values because the PDF has none. Four required source flags bind
the printed summer Fajr/Isha transition night (10→11 May) and completion night
(2→3 August); drift fails before candidate creation.

The raw full-year and monthly candidates stop at `needs_review`. The separate
`effective-schedule/v1` composition transform consumes an immutable policy
artifact that binds both candidates and raw/transcription/normalized/parser
identities. It keeps the PDF as the 2026 baseline and applies every field
actually supplied by the photo for all 31 August days. Missing monthly al-Isfar
does not erase the explicit PDF value. The machine-checked reconciliation keeps
eleven Dhuhr-onset differences, one collective-time difference and the wording
difference unchanged as evidence, while D-002 resolves selection in favor of
the monthly source.

Composition cannot approve or publish. The effective candidate stays
`needs_review`. The pilot's named mosque approval now binds the source decision,
the interpretation of collective Dhuhr as Dhuhr adhan, +5-minute iqamah rules
and Friday 13:15 Jumu'ah through a separate signed policy. Protected production
snapshot-key provisioning and rollout/rollback evidence remain outside the
source transform. The collective Dhuhr value never becomes iqamah.

T009 adds the device-facing delivery path. The Go API loads only explicitly
configured immutable snapshots that pass raw hash, canonical Ed25519,
schema/domain, snapshot-ID/signing-key and paired-mosque binding checks. Pairing
fixtures are explicitly ephemeral/test-only, one-use in process, duplicate-
credential rejecting, bearer-scoped to one device assignment, and absent unless
a private runtime config supplies them. The configured public HTTPS origin and
canonical snapshot path are validated before startup. Responses implement
strong ETag/304; snapshot responses also include the raw-byte Digest.

On Android, pairing is strict and its device token is AES-GCM encrypted under
an Android Keystore key. The synchronizer accepts same-origin HTTPS only,
persists a fsync/atomic-rename checkpoint plus one staged or quarantined raw
file, scoped by device/mosque/timezone/manifest-origin fingerprint so an old
pending stage cannot cross re-pairing, and never exposes network state to
Compose. Manifest/raw byte identity,
canonical signature, schema/domain validation and the existing time-engine
smoke check plus provisioned mosque ID/timezone binding all precede the Room
transaction. An authorized rollback
re-imports authenticated previous bytes transactionally rather than trusting a
possibly corrupt old local copy. File-backed tests reopen after interruption
inside import and after Room commit/before checkpoint finalization. WorkManager
runs only for provisioned remote mode; 304 or failure never clears Room.

Small operator UI preferences, including the last focused settings section,
reduced-motion default, Russian-by-default whole-app language, bounded
screen-retention shift toggle, one validated local sadaqah QR presentation and
five device-local iqamah offsets, use a single Preferences DataStore
instance. Signed schedule data never moves into DataStore:
mosque/source/approval/Jumu'ah/campaign and diagnostics settings project the
active Room snapshot. Local iqamah is applied only as an ephemeral
`offset_after_adhan` override for the current/next mosque-local date;
legacy device-local `HH:mm` keys are intentionally ignored because they
cannot be converted without choosing a date and adhan source. An unset offset
therefore preserves the approved signed policy rather than guessing.
This projection never mutates or acquires the provenance of the signed Room
snapshot. The local QR similarly has no official
or approval claim and takes display precedence only while fully valid.
Only locally effective actions are interactive; the Compose shell contains no
network client. Localized resources cover display, settings, safe errors, QR
and accessibility text, while mosque/source-provided names remain provenance
data rather than translated UI copy.

### Production pairing foundation (T011)

T011 introduces the independent production pairing foundation without
unblocking T010. PostgreSQL owns mosque, pending/active/revoked device,
one-time-code, rate-bucket and append-only audit state. The Go pairing manager
generates high-entropy code/token material but repository commands carry only
SHA-256/HMAC values. Redemption locks one code/device row and atomically commits
rate accounting, single use, device metadata/token verifier and audit evidence;
concurrent requests therefore have one winner even across API processes.

The database enforces `(device_id, mosque_id)` as a composite foreign-key
boundary, while service commands scope revocation by the same pair. Runtime
configuration contains environment-variable names rather than database/HMAC
secrets. Persistent snapshot assignments and role-authorized code issuance are
deliberately deferred to T012, so a paired but unassigned device fails stale-
but-correct with a manifest `404`.

Handler-to-database calls have a bounded context deadline; PostgreSQL lock waits
are cancelled without consuming the code or activating the device. Transaction
cleanup also uses a bounded rollback context. Migration triggers reject audit
row update/delete and table truncation under the runtime database principal;
production deployment should still separate the migration owner from a
least-privileged runtime role because an owner can alter its own triggers.

### Role-scoped fleet administration (T012)

Migration v2 adds admin actors, SHA-256 bearer verifiers, global/local
memberships, durable idempotency evidence and device assignments. The Go admin
manager owns the role matrix and deterministic response derivation; PostgreSQL
rechecks the active actor/membership inside every scoped transaction. This
double boundary prevents a handler or query filter from becoming the only
tenant-isolation control.

The schema-owner DSN exists only in a short-lived `cmd/migrate` deployment
process. It moves the ledger to an explicit version under an advisory lock,
including a targeted v2-to-v1 rollback. At the T012 checkpoint the API process
received only the least-privileged runtime DSN and read-only verified exact v2;
T013 advances the same fail-closed rule to v3.

Pairing issue uses domain-separated HMAC derivation so a retry can reproduce
the same 128-bit code while storage remains verifier-only. Current and staged
compatibility keys form an explicit two-phase rotation ring. Assignment
idempotency stores its non-secret historical response for a 24-hour guarantee,
avoiding a retry being silently upgraded to a later manifest version. Its
client-semantic request hash and stored response are checked before current
artifact-registry lookup, so deploy-time URL or registry changes cannot alter
an exact retry; expired evidence returns `409` without a duplicate mutation.

The device API keeps immutable snapshot bytes and trust keys in its verified
registry. Admin assignment can reference that registry but cannot inject raw
URL/hash/signing-key fields. Durable assignment reads are rebound to the
authenticated device mosque and registry mosque/timezone/hash/key before a
manifest or snapshot is served.

### Latest-only device health (T013)

Migration v3 adds a single replaceable `device_health` row and server-owned
`devices.last_seen_at`. The authenticated heartbeat path requires the bearer
principal to match the URL device, then PostgreSQL rechecks and locks the active
device/mosque row. Client `sent_at`, reported snapshot and health fields remain
diagnostic claims; server `received_at` determines fleet freshness, and reported
snapshot never mutates the signed assignment path.

The payload is a closed allowlist of bounded enums/strings and cannot transport
logs, network identifiers, accounts, location or arbitrary codes. Admin list
joins only the latest row through the existing mosque RBAC boundary. Android
constructs the endpoint from the provisioned manifest origin/path. A wrapper
may report after sync, but ignores every non-cancellation reporting failure and
returns the original sync result, keeping display and activation independent.
T014 advances the current API's exact-schema check to v4; lower, gapped and
future ledgers all fail startup.

### Bounded canary rollout cohorts (T014)

Migration v4 adds an optional mosque-scoped rollout label to each device. A
mosque administrator explicitly sets or clears membership; there is no
percentage targeting or client-selected cohort. Group assignment resolves only
an immutable snapshot already admitted to the API registry, then selects and
locks members in sorted ID order with a 101st-row overflow sentinel. It updates
at most 100 durable assignments in one PostgreSQL transaction.

Each device keeps its own monotonic `manifest_version`, canonical before/after
hash and audit row. Exact retries return the stored batch response before
registry lookup. Assigning a prior verified snapshot is therefore a rollback
with a newer manifest version, not a downgrade. Failure, an oversized cohort or
scope mismatch leaves all member assignments unchanged.

### Privacy-safe support bundle (T015)

The support export is a read-only projection, not another telemetry ingest or
database table. Under existing mosque RBAC, PostgreSQL joins exactly one device,
its optional durable assignment and its optional latest-only health row. The
service adds a schema version and server generation time, then returns bounded
JSON with `no-store`.

The type exposes only a public signing-key identifier and has no fields for
credentials, installation keys, capabilities,
snapshot URLs, network/account/location data, arbitrary maps, logs or history.
Missing assignment/heartbeat becomes an omitted object rather than invented
values. The projection cannot mutate display, assignment or publication state.

### Fleet database recovery boundary (T016)

The fleet PostgreSQL database is one recovery unit for mosque/device identity,
hashed credentials, pairing state, assignments, latest health, idempotency and
audit evidence. A logical backup is transaction-consistent, but it is not a
complete service backup: immutable signed snapshot bytes, raw source artifacts,
runtime configuration, database roles/grants and publication signing keys have
separate custody and recovery procedures.

Restore is always into a newly provisioned isolated database. The deployment
reapplies roles and least-privilege grants while restore suppresses archived
ownership and ACL commands,
then the same runtime repository verifies the exact migration ledger and reads
restored identities/current state. Append-only triggers are exercised after
restore. No API replica may start against the target until these checks and the
referenced snapshot-artifact inventory pass.

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

The local implementation of steps 1–8 is file-backed. It does not create an
approval actor, production key or rollout channel. The real August and
full-year sources plus their hash-bound effective composition reach
`needs_review` only. The effective 365-day candidate selects monthly August
fields without inventing absent values and exposes both component provenance
records in its diff; only synthetic golden fixtures are approved with ephemeral
test keys.

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
9. Report the T013 privacy-safe latest-only heartbeat when provisioned; display remains independent from heartbeat success.

Never erase the active snapshot before the new one is proven valid.

Manifest versions never decrease. Server rollback publishes a newer manifest
version pointing at a previously signed snapshot. Same-version identity drift,
304 without an accepted local ETag, cross-origin snapshot URLs and non-empty
asset lists unsupported by the current client fail closed.

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
- Detect implausible device clock by comparing response receipt with the
  `Date` header on an authenticated HTTPS manifest response. This operational
  server response time at a bounded tolerance, and reject a wall-clock rollback
  during the request. This hint is not schedule authenticity: persist
  `unknown`/healthy/mismatch health,
  never use it to calculate prayer rows, block last-known-good activation or
  alter OS time. Snapshot authenticity remains exclusively hash + signature.
- Schedule date rollover based on mosque timezone.
- Cache at least 90 future days; annual coverage is preferred.

## Assets and themes

Built-in themes are packaged with the app. A single-TV operator image imported
through the system document picker is validated and copied into app-private
storage; it is not an approved remote/fleet asset. Remote custom images remain
content-addressed:

```text
asset_id -> sha256 -> byte length -> media type -> dimensions -> orientation
```

The TV downloads an approved remote asset to staging, validates
type/dimensions/hash, then activates it. Keep a built-in fallback background.
MVP excludes video backgrounds.

## Autostart and kiosk modes

- **Consumer/best-effort mode:** boot receiver, vendor setting guidance,
  display-route-only `keepScreenOn` and process recovery. Behavior varies by
  OEM; local lifecycle coverage is not proof that a particular television will
  ignore its own power policy.
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
