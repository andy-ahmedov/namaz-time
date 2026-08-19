# PRAYER_TIMES_DATA.md

## Core policy

A prayer time is not just `HH:MM`. It is a dated value interpreted in a timezone and backed by a source, geographic scope, method, revision and approval decision.

## Authority hierarchy

The hierarchy is configured per mosque; it is not globally hard-coded.

1. schedule explicitly approved by the mosque;
2. official regional authority selected by that mosque;
3. official central/federal source that explicitly covers the locality;
4. approved imported table from another authoritative channel;
5. approved calculation profile;
6. no value.

Never infer that DUM RF, DUM RT or Central DUM is automatically authoritative for every Russian locality.

## Source kinds

### `manual_import`

Operator-imported CSV/JSON with recorded source authority, scope, raw hash and parser version. This is the first provider to implement because it is reviewable, deterministic and easy to cache.

### `official_api`

Documented API with permission and a stable contract. Retrieve only on the backend; store response metadata and normalized snapshot.

### `official_file`

CSV/XLSX/JSON/PDF published by an authority. Prefer structured formats. PDF requires controlled conversion and human comparison.

### `official_html`

Last-resort server-side parser for an approved official table. It needs rate limiting, terms review, parser fixtures, schema-drift detection and a kill switch.

### `mosque_calendar`

A calendar maintained or explicitly adopted by the mosque. It still requires effective dates, import metadata and named approval; mosque-local ownership must not be inferred from a city label.

### `calculation_profile`

Deterministic astronomical calculation from coordinates, date and explicit parameters. Store method, Fajr/Isha parameters, madhab, high-latitude rule and adjustments. It remains “calculated” unless an authority/mosque approves it as its schedule.

## Why continuous parsing is the wrong default

Static analysis shows IslamApp uses a periodically replaceable regional package, not a live DUM scrape per screen/day. Public mosque-display products also use annual calendars, mosque-admin data or local calculation. A controlled parser may still be needed when an authority exposes only HTML, but it should run periodically on the backend and publish a validated snapshot—not be a runtime dependency of the TV.

See [PRAYER_TIME_SOURCE_PATTERNS.md](PRAYER_TIME_SOURCE_PATTERNS.md).

## Source registry

Required fields:

```text
source_id
kind
authority_name
authority_branch
geographic_scope
canonical_url
contact
license_or_permission_reference
attribution_text
retrieval_method
update_cadence
expected_format
stale_after
status
notes
```

## Raw artifact record

```text
artifact_id
source_id
retrieved_at_utc
request_url_or_filename
http_status
content_type
etag
last_modified
byte_length
sha256
storage_location
parser_version
terms_snapshot_reference
```

Do not transform bytes before the raw hash. Never log credentials or secret query parameters.

## Normalized schedule

Daily fields:

```text
local_date
fajr
sunrise
dhuhr
asr
maghrib
isha
optional_imsak
optional_duha
optional_tahajjud_start
optional_midnight
source_revision
validation_flags
```

Use local wall-clock `HH:MM` plus the schedule's IANA timezone. Preserve source precision; do not invent seconds.

## Candidate validation

Block publication on:

- missing mandatory fields or dates;
- duplicate date;
- invalid timezone;
- unparseable/nonexistent local time;
- coverage outside source scope;
- obvious ordering failure without a documented exception;
- source checksum mismatch;
- unresolved parser warnings;
- unapproved fallback/provider substitution.

Flag for review:

- minute delta above per-prayer threshold;
- large day-to-day jump;
- changed calculation parameters;
- changed authority or scope;
- calendar has 365/366 mismatch;
- source page/file format changed;
- new `24:xx`/next-day semantics.

## Diff report

A review must show:

- old/new source and revision;
- effective date coverage;
- changed days count;
- maximum and median minute delta by prayer;
- rows with missing/new values;
- exact calculation/configuration changes;
- parser warnings;
- raw hashes and parser versions.

## Approval state machine

```text
retrieved -> parsed -> validation_failed
                    -> needs_review -> approved -> published
                                   -> rejected
published -> superseded
```

Only named authorized roles can approve. Publication is reversible and separate from approval.

## Snapshot provenance

Every published TV snapshot includes:

```text
source_id
source_kind
authority_name
geographic_scope
canonical_reference
retrieved_at
raw_sha256
parser_version
calculation_profile (nullable)
approval_id
approved_by
approved_at
approval_scope
```

For `calculation_profile`, the snapshot field is a required immutable
profile/version reference. The referenced frozen source record carries the
full method, coordinates, adjustments and approval details listed below.

The main screen may show a concise label; diagnostics must expose the full record.

## Calculation profile

Required fields when source kind is `calculation_profile`:

```text
library_name
library_version
method_name
fajr_angle_or_rule
isha_angle_or_interval
madhab
high_latitude_rule
prayer_adjustments
coordinates
coordinate_source
timezone
approved_by
```

A single generic “Russia” method is insufficient for all Russian regions. The observed competitor dataset contains region-specific seasonal profiles and exact city tables, which demonstrates the operational need for scoped overrides.

## Iqamah

Iqamah is mosque-local and separate from the source adhan schedule.

Rule:

```text
prayer
mode: fixed_time | offset_after_adhan
value
valid_from
valid_to
weekdays
priority
reason
approved_by
```

Precedence:

1. exact-date override;
2. matching range/weekday rule by priority;
3. seasonal rule;
4. base rule;
5. unset.

## Jumu'ah

Store independent sessions with label, khutbah/start time, effective dates, weekdays and language. Do not overwrite Dhuhr in source data.

## Staleness policy

Initial proposal, to validate with the pilot:

- normal: over 30 future days;
- warning: 7–30 days;
- critical: under 7 days;
- expired: no approved current-day row.

An expired official source does not automatically activate calculation. A fallback needs explicit pre-approval and visible labeling.

## Official Russian source onboarding

Public research confirmed that DUM RT publishes a city/district selector and annual material, DUM RF publishes Moscow times/materials, and Central DUM publishes city-specific pages. No stable nationwide public API was confirmed. Therefore:

1. contact the relevant authority/mosque;
2. request machine-readable format or permission;
3. define exact locality scope and update cadence;
4. record attribution/license;
5. create fixture and parser only after the source agreement;
6. review the first full year manually before publication.

Use [SOURCE_PARTNERSHIP_CHECKLIST.md](SOURCE_PARTNERSHIP_CHECKLIST.md).
