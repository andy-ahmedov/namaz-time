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

### `candidate_prayer_day`

```text
candidate_schedule_id
local_date
fajr
sunrise
dhuhr
asr
maghrib
isha
optional fields
source_revision
```

Primary key: `(candidate_schedule_id, local_date)`.

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

### `pairing_code`

Hash short-lived codes; never store plaintext after issue.

```text
id
code_hash
expires_at
max_uses
used_at
target_mosque_id
```

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

## Tenant isolation

All operator queries are scoped by mosque/organization membership. Add composite indexes containing `mosque_id`. Authorization belongs in service methods and integration tests, not only UI routes.

## Retention

- audit events: long-term according to policy;
- raw artifacts: retain when permission allows, otherwise metadata + approved fixture;
- candidate schedules: retain for traceability;
- snapshots: active + previous indefinitely for pilot, then policy-driven;
- heartbeat detail: short retention and aggregation;
- pairing codes: delete/expire quickly.
