# PRAYER_TIMES_DATA.md

## Core policy

A prayer time is not just `HH:MM`. It is a dated value interpreted in a timezone
and backed by source, exact geographic scope, method, revision, validation and
qualification evidence. [ADR 0019](docs/adr/0019-public-first-party-source-qualification.md)
separates autonomous public first-party qualification from optional external
endorsement. Existing signed manual/mosque approvals remain valid legacy proof;
they are not required for every new public source. Contract evolution is tracked
in T049; the existing approval fields below describe that retained legacy path.

## Authority and scope

First qualify each authority independently. Within a confirmed authority chain,
prefer exact locality timetable, locality/district first-party interface,
explicitly subject-wide timetable/policy, then verified official calculation
policy. Specificity never suppresses another independently qualified authority.
Show every applicable real choice without religious ranking or top-N. A mosque
may explicitly prefer a source, but that preference is not inferred or required
to establish the source's own authority. No qualified current coverage means
unavailable, without a generic, nearby-city, capital or competitor fallback.

Never infer that DUM RF, DUM RT or Central DUM is automatically authoritative for every Russian locality.

## Source kinds

### `manual_import`

Operator-imported CSV/JSON with recorded source authority, scope, raw hash and parser version. This is the first provider to implement because it is reviewable, deterministic and easy to cache.

### `official_api`

Public first-party API with a validated contract and legitimate transport.
Respect explicit restrictive terms; separate written permission is not required.
Retrieve only on the backend; store response metadata and normalized snapshot.

### `official_file`

CSV/XLSX/JSON/PDF published by a verified authority. Prefer structured formats.
PDF requires controlled conversion and independent actual-date comparisons;
store substantial raw files outside Git unless redistribution rights are clear.

### `official_html`

Server-side parser for a qualified first-party table. It needs rate limiting,
terms/access review, parser fixtures, schema-drift detection and a kill switch.

### `mosque_calendar`

A calendar maintained or explicitly adopted by the mosque. A public first-party
mosque calendar can be qualified from evidence. A private/manual adoption uses
the actual legacy approval path. Both need dates and import provenance;
mosque-local ownership must not be inferred from a city label.

### `calculation_profile`

Deterministic calculation from an authority's sufficiently specified official
policy for the exact scope. Store method, coordinates, Fajr/Isha rules, madhhab,
rounding, high-latitude/seasonal behavior and adjustments; verify across seasons
against first-party published values. Label it calculated from that policy, not
an exact published timetable. Generic calculators or inferred angles are ineligible.

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
contact (optional partnership evidence)
terms_assessment_reference
license_or_permission_reference (optional; do not invent)
attribution_text
retrieval_method
update_cadence
expected_format
stale_after
status / qualification_reference
external_endorsement_reference (optional and separate)
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
optional_recommended_fajr (source-only al-Isfar/performance recommendation)
optional_zenith
optional_collective_dhuhr (review candidate, not implicit mosque iqamah)
source_revision
validation_flags
```

Use local wall-clock `HH:MM` plus the schedule's IANA timezone. Preserve source precision; do not invent seconds.
Do not invent daily Hijri values when a source supplies only Gregorian rows.
Recommended performance times such as al-Isfar remain distinct from prayer
onset, just as a city-wide collective recommendation remains distinct from an
approved mosque-local iqamah rule.

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

A policy-resolved source precedence is not a silent fallback. It must be an
immutable effective-policy artifact binding exact component candidate/raw/
transcription/normalized/parser identities, bounded dates and applied fields.
The current pilot uses the annual PDF as baseline and the retained photo for
fields present in August 2026. Both raw sources remain unchanged.

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

## Qualification and legacy approval

The public-source path is researched → first_party_verified → scope_verified →
source_validated → qualified → active/selectable. A machine-verifiable decision
binds source ownership/scope/currentness evidence, exact raw/normalized hashes,
parser, validation and effective range. An enum or source URL alone is not proof.
Publication/signing remains separate from parser execution and qualification;
local activation must be authorized by the task (explicitly authorized in T049).

The following state machine and signed human receipt remain the legacy
manual/mosque path; do not fabricate one to onboard a qualified public source:

```text
retrieved -> parsed -> validation_failed
                    -> needs_review -> approved -> published
                                   -> rejected
published -> superseded
```

Only actual named authorized roles can issue legacy human approval. Publication
is reversible and separate from qualification/approval. External endorsement is
optional evidence, never an alias for source qualification.

For manual transcription, approval also binds the raw-artifact,
transcription, normalized-candidate and diff SHA-256 values plus parser
version. Parser warnings are not informational decoration: every warning code
must be explicitly acknowledged or publication fails closed.

## Snapshot provenance

Every published TV snapshot includes source/scope/hash/parser provenance plus
exact qualification proof or a retained legacy approval branch. The legacy
snapshot contract carries the following fields; T049 must version/evolve the
contract rather than populate human approval fields with a fictitious approver:

```text
source_id
source_kind
authority_name
geographic_scope
canonical_reference
retrieved_at
raw_sha256
transcription_sha256 (when human transcription is separate from the raw artifact)
parser_version
calculation_profile (nullable)
approval_id
approved_by
approved_at
approval_scope
```

For `calculation_profile`, the snapshot field is a required immutable
profile/version reference. The referenced frozen source record carries the
full method, coordinates, adjustments and qualification details listed below.

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
qualification_reference
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
- expired: no qualified/legacy-approved current-day row.

An expired official source does not automatically activate calculation or
another authority. Missing current qualified coverage is unavailable.

## Official Russian source onboarding

Reverify previous research rather than treating it as current authority proof.
Qualify actual organization ownership, exact locality scope, accessible transport,
current effective range and reproducible prayer values under ADR 0019. Do not
wait for external contact, partnership, permission letters or an external named
approver. Preserve restrictive terms/access boundaries, operational hashes and
sanitized/synthetic tests. Unknown ownership, scope or insufficient official
calculation policy leaves the city/source unavailable while research continues.

Use [SOURCE_PARTNERSHIP_CHECKLIST.md](SOURCE_PARTNERSHIP_CHECKLIST.md).
