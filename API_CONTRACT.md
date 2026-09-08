# API_CONTRACT.md

The machine-readable draft is [contracts/openapi.yaml](contracts/openapi.yaml). This document defines semantics that OpenAPI alone does not capture.

## Principles

- TV reads local state; API is a synchronization channel, not the UI source of truth.
- All requests use HTTPS.
- Pairing credentials are scoped, revocable and never placed in logs.
- Snapshot payload is immutable and content-addressed.
- Device manifest is small and cacheable.
- Server may return no update without forcing a full snapshot download.

## Pairing

`POST /v1/devices/pair`

Request contains a short-lived code plus device metadata and optionally a device-generated public key. Success returns:

- opaque `device_id`;
- scoped device token or certificate bootstrap;
- mosque identity and timezone;
- current manifest URL/version.

Security:

- rate limit by code/IP/device fingerprint without invasive tracking;
- code is single-use or low-use and expires in minutes;
- return identical public error for invalid/expired code;
- token stored in Android Keystore where available.

T009 stores the returned provisioning envelope with AES-256-GCM under an
Android Keystore key. A normal local-only install has no fixture credential and
does not schedule remote work.

T011 adds the production pairing backend while preserving this response. It
generates a 128-bit one-time code and a 256-bit device bearer credential,
stores only SHA-256 verifiers, and consumes the code in the same PostgreSQL
transaction that activates the device and appends audit evidence. Codes expire
within 15 minutes and persistent HMAC buckets limit attempts independently by
submitted code, device metadata/public key and direct network source. Invalid,
expired, consumed and revoked codes share the same public error. Rate limiting
uses the existing `429` response and never echoes a code or token.

The server does not trust `X-Forwarded-For`; the TLS/reverse-proxy deployment
must preserve a trustworthy direct peer address. T012 will add the authorized
admin endpoint that issues these records. Until then, T011 exposes issuance as
an internal service boundary and does not claim a production admin login.

## Manifest

`GET /v1/devices/{deviceId}/manifest`

Headers:

```text
Authorization: Bearer <device-token>
If-None-Match: "manifest-etag"
```

Response:

- `304` if unchanged;
- `200` with current snapshot ID/version/hash/size/signing-key ID and optional asset list;
- optional `minimum_app_version` and controlled rollout metadata;
- no prayer rows in the manifest.

Both successful responses carry the standard HTTP `Date` header. The Android
client may compare this HTTPS-authenticated response time with the device clock
for a bounded health diagnostic. It is not snapshot/source authenticity, is
not used to calculate prayer times, and never changes the OS clock or blocks
last-known-good activation. A missing or malformed header produces no new
clock-health conclusion.

The manifest and snapshot URL use the same HTTPS origin while the same bearer
token authenticates both requests. A future CDN must use an explicit host
allowlist and separate credential design; the client never forwards a bearer
token to an arbitrary manifest-selected host.

`manifest_version` is monotonic. An authorized rollback increments the
manifest version while pointing to a prior immutable signed snapshot. Reusing
one manifest version with different snapshot ID/hash/length/key is rejected.

The device must not delete its active snapshot when authentication or manifest retrieval fails.

## Snapshot download

`GET /v1/snapshots/{snapshotId}`

Response body conforms to `prayer-snapshot.schema.json`. Recommended headers:

```text
Content-Type: application/json
ETag: "<payload-sha256>"
Digest: sha-256=<base64>
Cache-Control: private, max-age=...
```

The application-level Ed25519 signature inside/enveloping the snapshot remains mandatory; TLS and HTTP digest are not substitutes.

T009 limits both manifest-declared and downloaded snapshots to 5 MiB, requires
the raw byte length/SHA-256 from the manifest, then independently verifies ADR
0003 canonical SHA-256, signing key, signature, schema and domain rules.
Rejected raw bytes are quarantined for bounded local diagnostics and never
imported.

## Heartbeat

`POST /v1/devices/{deviceId}/heartbeat`

Contains only operational status:

- app/OS/device model;
- active snapshot ID;
- last sync outcome code;
- days of coverage remaining;
- clock/timezone mismatch flags;
- storage/memory health bucket;
- boot/kiosk capability flags.

Do not send precise GPS, Wi-Fi SSID/BSSID, installed-app list, user account, advertising ID or full logs by default.

T013 makes every field above explicit and required, with bounded enums/lengths;
unknown JSON fields fail closed. The existing device bearer must authenticate
the exact `{deviceId}` path. `sent_at` is a client report, while PostgreSQL
records its own `received_at` as authoritative last-seen and forces the clock
mismatch flag when skew exceeds 15 minutes. Only one latest health row exists
per device. `active_snapshot_id` is diagnostic and never changes assignment or
activation. Success is `204`; malformed input is `400`, invalid/revoked scope is
`401`, and backend failure is retryable `500`.

## Error format

```json
{
  "error": {
    "code": "snapshot_not_found",
    "message": "Snapshot is unavailable",
    "request_id": "...",
    "retryable": false
  }
}
```

Client behavior is driven by stable `code`, not localized `message`.

## Versioning

- URL major version: `/v1`;
- snapshot schema has independent `schema_version`;
- additive fields require clients to tolerate unknown properties only where schema explicitly permits them;
- breaking snapshot changes require dual-publishing during migration;
- backend can require a minimum app version but should preserve a safe offline display.

## Idempotency

Admin write endpoints added later should accept `Idempotency-Key`. Pairing is logically single-use. Heartbeat is safe to repeat.

## Clock semantics

All API timestamps are RFC 3339 UTC. Prayer rows are mosque-local `HH:MM` plus the snapshot IANA timezone. Never infer prayer date from server UTC date.

## Rollout

Manifest selection can depend on a server-side rollout group. A device receives exactly one active snapshot. Rollback means manifest points back to a known valid snapshot version; the TV still verifies it normally.

At T009, non-empty remote asset lists are rejected explicitly. Built-in themes
remain the safe fallback until the separately staged asset pipeline exists.

## T009 runtime configuration

`cmd/api` starts only with `-config <private-json>`; the config file must be a
regular non-symlink file with mode `0600`. It references bounded snapshot and
public-key files inside the same directory. Registry startup runs full
signature/schema/domain verification, binds the signed mosque ID/timezone to
the paired device and requires snapshot URLs to use the configured public HTTPS
origin plus canonical snapshot path before serving any bytes.

The T009 pairing issuer is deliberately an `ephemeral-test-only`, process-local
fixture. It rejects duplicate code/token values but does not claim durable
expiry, attempt accounting or restart-safe consumption. A production pairing
issuer must implement those controls in persistent storage; codes and tokens
are never committed as defaults.

## T011 production pairing runtime

Set `pairing_backend` to `postgres` and provide only the *names* of environment
variables containing the PostgreSQL URL and Base64-encoded 32-byte HMAC key.
`pairing_backend_timeout_seconds` bounds each persistent pair/auth call (five
seconds when omitted, maximum 30), so a database partition or lock wait returns
a retryable `500` instead of exhausting the connection pool indefinitely.
The private JSON config never accepts a literal `database_url`. Migrations run
out of band through the short-lived `cmd/migrate` process with a schema-owner
credential. API startup carries only the least-privileged runtime DSN, opens a
bounded pgx pool, verifies the exact current migration ledger (v6) without
changing it, and fails
closed if the database, exact schema or key is unavailable.
Remote PostgreSQL endpoints must use certificate and hostname verification
equivalent to `sslmode=verify-full`; `disable`, `prefer`, `require`, and
`verify-ca` are rejected outside loopback/local-socket development.

Static T009 assignments are rejected in this mode. A newly authenticated but
unassigned device receives `404 manifest_not_found` and keeps its local display;
T012 owns persistent assignment administration and mosque-scoped authorization.

## T012 fleet administration

The minimum admin surface uses a distinct opaque bearer principal and is
available only with the PostgreSQL backend:

- `GET /v1/admin/mosques/{mosqueId}/devices`;
- `POST /v1/admin/mosques/{mosqueId}/pairing-codes`;
- `POST /v1/admin/mosques/{mosqueId}/devices/{deviceId}/revoke`;
- `PUT /v1/admin/mosques/{mosqueId}/devices/{deviceId}/assignment`.

Every write requires `Idempotency-Key` and a bounded audit reason. During the
24-hour retry guarantee, an identical retry returns the original result and
does not append a second audit row. Reusing a key with different semantic input,
or retrying after its evidence expires, returns non-retryable `409` without a
new side effect. Assignment retry identity binds only client-semantic input;
the stored historical response is checked before the mutable artifact registry,
so URL/config changes cannot change an exact retry. Admin
authentication failure is `401`. A missing resource, an out-of-scope mosque,
and a cross-mosque device all return the same `404`, so authorization does not
act as an existence oracle. Database/backend failure remains retryable `500`.

The assignment request accepts a snapshot ID rather than URL/hash/key. Those
fields are resolved from the server's verified immutable registry, persisted
with a monotonically increasing manifest version, and checked again on every
device manifest/snapshot read. This does not approve, sign or publish a new
snapshot and therefore does not unblock T010.

## T014 bounded canary rollout cohorts

Two additional mosque-admin writes target an explicit server-side cohort:

- `PUT /v1/admin/mosques/{mosqueId}/devices/{deviceId}/rollout-group`;
- `PUT /v1/admin/mosques/{mosqueId}/rollout-groups/{groupId}/assignment`.

The first sets an 8–64 character ASCII slug (`A-Z`, `a-z`, digits, `.`, `_`,
`-`), or clears it with an empty string. The
second atomically assigns one artifact already verified by the server registry
to the current non-revoked cohort, in deterministic device-ID order. A cohort
contains at most 100 devices per transaction; a larger target fails with `409`
and changes no assignment. Each changed device receives a new monotonically
increasing manifest version and its own canonical audit transition.

Both writes use the T012 RBAC, reason and 24-hour idempotency contract. An exact
group-assignment retry is read from durable evidence before current registry
lookup. Rollback submits the previous verified snapshot ID as a new group
assignment; snapshot versions may move backward, but manifest versions never
do. Cohort labels select devices only: they cannot approve, sign, upload or
mutate snapshot content.

## T015 privacy-safe device support bundle

`GET /v1/admin/mosques/{mosqueId}/devices/{deviceId}/support-bundle` returns
`device-support-bundle/v1` with `Cache-Control: no-store`. It uses the same
service-admin/mosque read roles as fleet list; a missing, suspended or
cross-mosque device returns the uniform `404`.

The response is assembled on demand from one device row, its optional current
assignment and its optional latest health row. It contains mosque ID/timezone,
bounded device/app/OS/model lifecycle fields, rollout label, snapshot
ID/SHA-256/signing-key ID/manifest version and allowlisted health. It deliberately
has no bearer/pairing secret, installation public key, capability list,
snapshot URL, network/account/location field, arbitrary map, log or history.
`generated_at` and health `received_at` are server times; `reported_at` and
`reported_snapshot_id` remain explicitly non-authoritative device claims.

## T037 persisted city/source setup reads

The optional PostgreSQL registry reader adds two authenticated, mosque-scoped
read endpoints:

- `GET /v1/admin/mosques/{mosqueId}/setup/cities?q=…`;
- `GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy?city_id=…&date=…`.

The first returns every exact canonical-name or explicit-alias match in
deterministic subject/name/ID order. It does not claim uniqueness or select a
duplicate. The second requires an operator-selected canonical city ID, the
authorized mosque path and a local Gregorian date. It returns the explicit
scope, authority evidence labels, approved source, policy and existing
timetable/snapshot reference from the active immutable registry revision.

Both endpoints require the existing admin bearer and mosque read membership,
return `Cache-Control: no-store`, reject unknown query parameters and make no
write or publication side effect. Same-tier ambiguity returns
`prayer_policy_ambiguous`; absent, stale, unavailable or inactive state returns
`prayer_policy_unavailable`. Neither condition chooses a neighboring city,
generic Russia method or another source, and neither changes the TV's
last-known-good snapshot.

Runtime access is opt-in with `registry_backend: "postgres"` and is valid only
with `pairing_backend: "postgres"`. The API role reads the active registry;
the separate `registryctl apply` operator command owns staged revision writes
and explicit activation.

## T039 explainable registry review handoff

`GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy-options` assesses one
explicit immutable `revision_id`, canonical `city_id`, mosque path and local
date. Unlike the active resolution endpoint, it returns every applicable
option with deterministic precedence, authority evidence/scope, source
freshness, effective range, payload reference and stable blocked reason. The
overall status is `resolved`, `ambiguous`, `stale` or `unavailable`. A staged
response advertises `request_binding` only when at least one highest-tier
option is actually selectable.

`POST /v1/admin/mosques/{mosqueId}/setup/prayer-policy-binding-requests`
requires mosque write scope and `Idempotency-Key`. It re-assesses the exact
staged revision and appends a `pending_review` request for the explicitly named
selectable policy. Exact retries return the retained response for 24 hours,
including if registry state changes afterward; changed input conflicts.

This endpoint is a review handoff only. It does not mutate or activate a
revision, approve a source, publish or sign a snapshot, or change any device
assignment. Active, stale, unavailable, research-only, expired,
lower-precedence and schedule-missing choices return
`registry_binding_not_selectable`. The active signed snapshot and TV
last-known-good path are unaffected.

## T040 multi-authority city schedule choices

`GET /v1/admin/mosques/{mosqueId}/setup/schedule-choices` is the setup-facing
projection after one canonical `city_id` has been selected. `revision_id` is
optional: omission reads the active immutable revision, while an exact value
allows a staged revision to be reviewed. The local Gregorian `date` and
mosque-scoped admin bearer remain mandatory. `/setup/cities` continues to
return geography only; authorities never become duplicate city rows.

The response is `city-schedule-choices/v2` and carries the canonical city,
federal subject, IANA timezone, revision identity/state, underlying automatic
resolution status/reason, and the complete set of eligible choices, retaining
each authority's most-specific applicable evidence without cross-authority ranking.
Each choice has a stable city+policy-derived ID, canonical authority
label and organization records, evidence labels, geographic scope, source,
qualification or retained approval, effective range, tier, and exact timetable
or calculation-profile reference. `selectable` means the assessed option may be explicitly chosen;
`executable` is true for each eligible admitted option in the active revision,
including a retained approval-bound option alongside qualified public choices.
It does not mean automatic resolution or that a device has activated the option.
A staged choice remains non-executable and uses the existing
`pending_review` handoff.

Multiple independent eligible choices produce `status=available`,
`selection_required=true`, and preserve the resolver's underlying
`automatic_resolution_status=ambiguous`. Ordering is neutral tier/policy-ID
ordering and never selects the first item. Stale, unavailable, research-only,
out-of-range, missing-schedule and lower-precedence options are not promoted
into this selectable projection; T039 continues to expose all of them with
blocked reasons.

There is no product cardinality limit, top-N filter, display slot count or
religious ranking. The endpoint currently returns the full eligible set. If a
future payload requires bounding, the contract must add explicit pagination or
equivalent lossless completeness metadata; it must not omit choices while
presenting the response as complete. The endpoint is read-only, uses
`Cache-Control: no-store`, rejects unknown query parameters, and cannot approve,
publish, activate, sign or assign a snapshot.

## T041 device-scoped city and schedule setup

The TV never receives an admin bearer. Three bounded endpoints reuse the
existing provisioned device bearer and require the `{deviceId}` path to match
that bearer principal:

- `GET /v1/devices/{deviceId}/setup/cities?q=…`;
- `GET /v1/devices/{deviceId}/setup/schedule-choices?city_id=…&date=…`;
- `POST /v1/devices/{deviceId}/setup/schedule-choice-requests`.

The server derives mosque identity from the authenticated device. It also
selects the immutable setup revision from private deployment configuration;
neither `mosque_id` nor `revision_id` is accepted from the client. With no
configured staged revision, reads use the active revision and its choice is
read-only. A configured staged revision supplies both canonical city search and
the T040 choice projection. Same-name city results retain subject, settlement
type and IANA timezone and are never auto-selected. The choice response remains
complete and unranked; no `limit`, top-N or implicit first choice exists.

The POST body contains only canonical `city_id`, stable `choice_id`, local
Gregorian `date` and a non-secret `interaction_id`. It may append an idempotent
device-originated `pending_review` proposal for a selectable choice in the
still-staged server-configured revision. Origin is `local_tv_operator`; the
append-only audit identifies device and mosque. The operation cannot approve,
publish, activate, sign, assign or change the active/Room snapshot. A revoked
or cross-device bearer receives `401`; stale, active, unavailable or otherwise
non-requestable choices receive fail-closed `409`.

The Android client constructs these URLs only from the HTTPS origin of its
already authenticated manifest and its provisioned device ID; it does not
accept a setup origin, mosque ID or revision from presentation state. Responses
are capped at 512 KiB and decoded with unknown-field rejection. The POST body
is capped at 16 KiB and contains no bearer, mosque, revision, admin actor,
approval or publication fields. Client cancellation is propagated so
superseded city searches and abandoned choice loads cannot later replace the
current UI state. Every response must echo the expected city/date and, for a
proposal, the provisioned device/mosque principal plus the same interaction
identity; mismatch fails closed.
