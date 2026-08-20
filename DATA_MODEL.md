# DATA_MODEL.md

## Design principles

- immutable source artifacts and published snapshots;
- explicit mosque-local timezone;
- adhan separate from iqamah;
- candidate separate from approved schedule;
- audit every state-changing action;
- no polymorphic JSON blobs where relational constraints matter;
- public IDs are opaque UUID/ULID values, never sequential tenant identifiers.

## Main entities

### `mosque`

```text
id
slug
name
country_code
region
locality
latitude / longitude (optional after onboarding)
timezone_id
status
created_at / updated_at
```

### `source_definition`

```text
id
kind
authority_name
authority_branch
scope_json
canonical_url
contact
license_reference
attribution_text
retrieval_policy_json
stale_after
status
```

### `source_binding`

Binds a mosque to one source and defines priority/effective period. It never means automatic fallback unless `fallback_policy_id` explicitly allows it.

```text
id
mosque_id
source_definition_id
priority
effective_from / effective_to
approval_required
status
```

### `raw_artifact`

```text
id
source_definition_id
retrieved_at
request_reference
http_status
content_type
etag
last_modified
byte_length
sha256
storage_key
parser_version
```

Unique constraint: `(source_definition_id, sha256)`.

### `candidate_schedule`

```text
id
mosque_id
source_definition_id
raw_artifact_id
parser_version
timezone_id
coverage_from / coverage_to
status
validation_report_json
created_at
```

A derived effective candidate also owns ordered `source_components`. Each
component records its role (`baseline` or `override`), candidate/source IDs,
raw artifact metadata and SHA-256, transcription/normalized SHA-256, parser
version, effective date range and exact applied fields. The effective policy
artifact is the candidate's primary raw binding; changing policy bytes or any
component identity creates a different candidate and invalidates approval.

### `candidate_prayer_day`

```text
candidate_schedule_id
local_date
fajr
recommended_fajr nullable (source-only al-Isfar/performance time)
sunrise
zenith nullable
dhuhr
dhuhr_congregation nullable (candidate only; not implicit mosque iqamah)
asr
maghrib
isha
optional fields
source_revision
source_flags
```

Primary key: `(candidate_schedule_id, local_date)`.

`recommended_fajr`, zenith and source congregation fields are review evidence;
they participate in candidate identity/diff but do not become adhan rows.
Collective Dhuhr can become an iqamah override only through a separate explicit
mosque-practice decision/configuration that covers that value. Schedule approval
alone does not promote it. Missing daily Hijri fields remain absent.

### `approval`

```text
id
candidate_schedule_id
actor_id
decision
scope
reason
approved_at
raw_sha256
transcription_sha256
normalized_sha256
diff_sha256
parser_version
acknowledged_warning_codes
```

Approval must bind to exact raw/transcription/normalized/diff hashes and parser
version so a changed candidate cannot reuse an old decision. Every unresolved
parser-warning code must be acknowledged explicitly.

### `approved_schedule`

Immutable logical version created from one approval.

```text
id
mosque_id
candidate_schedule_id
approval_id
version
coverage_from / coverage_to
created_at
supersedes_id
```

### `approved_prayer_day`

Same fields as candidate day; immutable under an approved schedule version.

### `iqamah_rule`

```text
id
mosque_id
prayer
mode
fixed_time nullable
offset_minutes nullable
valid_from / valid_to
weekdays_mask
priority
reason
status
approved_by / approved_at
```

Check constraint ensures exactly one value matches mode.

### `iqamah_date_override`

```text
id
mosque_id
local_date
prayer
mode
value
reason
approved_by / approved_at
```

### `jumuah_session`

```text
id
mosque_id
label
khutbah_time nullable
salah_time
valid_from / valid_to
weekdays_mask
locale
priority
```

### `campaign`

```text
id
mosque_id
kind
https_url
title
subtitle
starts_at / ends_at
placement
status
created_by / approved_by
```

### `theme`

```text
id
mosque_id nullable
name
landscape_asset_id
portrait_asset_id nullable
overlay_opacity
text_palette_json
status
```

### `asset`

```text
id
sha256
byte_length
media_type
width
height
orientation
storage_key
license_reference
status
```

### `snapshot`

```text
id
mosque_id
version
schema_version
effective_from / effective_to
payload_sha256
signature
signing_key_id
approved_schedule_id
created_at
status
```

A snapshot references frozen copies/versions of iqamah, Jumu'ah, theme and campaign configuration.

### `device`

```text
id
mosque_id
installation_public_key nullable
name
model
os_version
app_version
rollout_group
active_snapshot_id
last_seen_at
status
```

T011 persists the pairing subset first. Device lifecycle is `pending` →
`active` → `revoked`; only an active row may contain a 32-byte `token_hash`,
and the plaintext bearer credential is returned once and never stored.

T013 migration v3 adds `last_seen_at` to this row. It is server-received time,
not the untrusted timestamp supplied by the TV.

T014 migration v4 persists `rollout_group` as an optional bounded label on this
same mosque-owned row. It has no content authority and is used only to select a
small transactional assignment cohort. Revoked devices are never assigned by a
cohort operation.

### `device_health`

Latest-only operational health, one row per composite-scoped device:

```text
device_id + mosque_id
reported_at
received_at
reported_snapshot_id nullable
sync_status
coverage_days_remaining
clock_mismatch + timezone_mismatch
storage_health + memory_health
boot_mode + kiosk_mode
```

The row is replaced in place under the device lock; no raw heartbeat history is
retained. `reported_snapshot_id` is deliberately separate from
`device_assignment.snapshot_id` and has no authority to change it. Backward
server time cannot overwrite a newer last-seen/health record.

### TV sync checkpoint (local file)

Transport state is not schedule authority and therefore stays outside Room:

```text
provisioning fingerprint (device + mosque + timezone + manifest origin)
accepted manifest ETag/version
accepted snapshot ID/raw SHA-256/byte length/signing key ID
pending manifest + ETag
last rejected snapshot ID + bounded code
```

The checkpoint and raw stage are written using fsync plus atomic rename. Room
continues to own only immutable schedule/config rows and the atomic
active/previous selection. Tokens are stored separately in an AES-GCM envelope
under Android Keystore, never in the checkpoint. A fingerprint mismatch after
re-pairing quarantines an old stage and resets only transport metadata.

### `pairing_code`

Hash short-lived codes; never store plaintext after issue.

```text
id
device_id
code_hash
expires_at
max_uses
uses_count
consumed_at
revoked_at
mosque_id
issued_by_actor_id
created_at
```

`(device_id, mosque_id)` is a composite foreign key to the device scope.
T011 fixes `max_uses` to one and bounds issuance lifetime to 1–15 minutes.

### `pairing_rate_bucket`

Persistent privacy-safe rate state. Bucket values are HMAC-SHA-256 under a
runtime-only 32-byte key; raw network addresses, submitted codes and device
fingerprints are not stored.

```text
bucket_kind (code / device / source)
bucket_hash
window_started_at
attempts
updated_at
```

### `admin_actor`, `admin_credential`, `admin_membership`

T012 separates operator identity, bearer verification and authority. Bearer
plaintext is never persisted; credentials contain a unique SHA-256 verifier and
optional expiry/revocation timestamps. An active actor has one or more
memberships:

```text
service_admin  -> mosque_id NULL, global fleet read/write
mosque_admin   -> mosque_id required, local fleet read/write
approver       -> mosque_id required, local fleet read-only in T012
viewer_support -> mosque_id required, local fleet read-only
```

The database constraint forbids a global scope on local roles and a mosque
scope on `service_admin`. Service authorization is repeated under row locks in
each write transaction.

### `admin_request`

Durable write idempotency:

```text
idempotency_hash
request_hash
actor_id
mosque_id
operation
resource_id
response (non-secret JSON only, optional)
created_at
expires_at
```

Pairing responses are regenerated with protected HMAC keys, so the plaintext
code is not stored. Assignment responses are non-secret and retained for the
24-hour retry guarantee so an old retry returns its historical manifest version
rather than a later assignment. Expired evidence remains append-only and causes
`409`; its key is never reused for a second mutation.
Update, delete and truncate triggers make these idempotency records append-only
under the runtime role.

### `device_assignment`

One current durable assignment per device, bound by composite
`(device_id, mosque_id)` foreign key:

```text
device_id
mosque_id
manifest_version
snapshot_id
snapshot_url
snapshot_sha256
signing_key_id
minimum_app_version
assigned_by_actor_id
assigned_at
```

Each non-idempotent change increments `manifest_version`. Registry hash/key and
snapshot mosque/timezone are checked before write and again before serving.
T014 group rollout expands to these same per-device rows atomically; there is no
group-level manifest that could bypass device scoping. A rollback points them to
an older verified `snapshot_id` while still incrementing each manifest version.

T015 adds no table and retains no exported bundle. `device-support-bundle/v1`
is assembled at request time from one `device`, its optional
`device_assignment`, its optional latest `device_health` row and mosque
timezone. Snapshot URL, credentials, installation key, capabilities and raw
history are intentionally outside the projection.

### `audit_event`

Append-only:

```text
id
occurred_at
actor_id
tenant/mosque_id
action
entity_type
entity_id
before_hash
after_hash
reason
request_id
```

T011 database triggers reject row update/delete and table truncate. Issue, successful
redeem and revoke records bind actor, mosque, entity, request/reason and
before/after hashes where applicable. The deployment must use a least-privileged
runtime role distinct from the migration owner; a table owner can otherwise
alter the enforcement trigger.

T012 adds actor-bound `device.pairing_issued`, `device.assigned` and
`device.revoked` events. An identical idempotent retry reuses the stored result
without appending another event.

## Publication transaction

1. lock the candidate/version;
2. verify approval hashes;
3. build canonical payload deterministically;
4. calculate payload hash;
5. sign outside the database process or via protected signer;
6. insert immutable snapshot;
7. update publication pointer in one transaction;
8. enqueue rollout via transactional outbox.

## Snapshot canonicalization

Use a documented deterministic JSON encoding or sign a canonical binary envelope. Do not sign arbitrary serializer output that can reorder fields between versions. The schema must define integer/string formats and exclude timestamps generated after signing.

Phase 1 canonical JSON and trust-boundary details are fixed in
[ADR 0003](docs/adr/0003-canonical-snapshot-signatures.md).

Production trust uses the public-only
`publication-trust-bundle.schema.json`. The bundle has a positive monotonic
revision; a key has a unique ID, Ed25519 public material, environment,
lifecycle state and signed-generation validity window.
`scheduled` is preflight-only, `active` may sign/verify, `retired` verifies only
historical snapshots in its closed window, and `revoked` never verifies. The
publication attestation and audit receipt bind source/candidate/diff/approval
hashes, approver, signer, signing/publication times, the exact trust bundle,
snapshot raw/canonical hashes and key ID. Every receipt has exactly one
signer-attested direct predecessor hash or the explicitly approved one-time
ledger genesis reason.

## Tenant isolation

All operator queries are scoped by mosque/organization membership. Add composite indexes containing `mosque_id`. Authorization belongs in service methods and integration tests, not only UI routes.

## Retention

- audit events: long-term according to policy;
- raw artifacts: retain when permission allows, otherwise metadata + approved fixture;
- candidate schedules: retain for traceability;
- snapshots: active + previous indefinitely for pilot, then policy-driven;
- heartbeat detail: latest-only per device; no raw history in T013;
- pairing codes: delete/expire quickly.
