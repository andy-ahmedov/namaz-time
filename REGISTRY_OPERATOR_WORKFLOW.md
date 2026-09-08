# Registry ambiguity, staleness and unavailability workflow

Date: 2026-09-08

Status: T049 public local activation and retained T039–T041 remote review handoff

## T049 policy supersession (2026-09-08)

[ADR 0019](docs/adr/0019-public-first-party-source-qualification.md) separates
autonomous first-party qualification from optional external endorsement. An
operator's explicit choice of a qualified, verified signed artifact need not
wait for an external approver. All independent qualified authorities must be
visible, even when their scope specificity differs. The T039–T041 pending-review
behavior below documents the retained legacy contract, not a universal approval
gate. Public qualification and the local signed-selection path are implemented
separately; the API does not gain an implicit remote activation operation.

## Current public-source operator path

1. Retain public raw bytes and each evidence page's own retrieval metadata/hash
   outside Git when redistribution rights are unclear. Establish real publisher
   identity, exact scope, currentness, time semantics, terms and value comparisons.
2. Run `ingestor inspect-public` with a strict import manifest and the complete
   pinned canonical catalog. An unsupported schema, invalid date/value, incomplete
   proof or scope mismatch is an error, not a reason to invent approval/fallback.
3. Use `publisher assemble` and `prepare`, then the separately authorized existing
   signer abstraction. `finalize` verifies both signatures and publication-ledger
   continuity; `verify` checks the resulting snapshot/receipt. No key material
   belongs in manifests, the API, the TV or Git.
4. Bind each independent qualified source to its exact published artifact in a
   schema-2 registry. Concrete Stage/Activate revalidates it. Public source
   qualification is not a human `pending_review` request.
5. For task-authorized offline local delivery, use
   `registryctl export-local-setup` with the three required input SHA-256 pins.
   Follow [PUBLIC_LOCAL_SETUP_BUNDLE.md](PUBLIC_LOCAL_SETUP_BUNDLE.md) for the
   closed inventory, fixed trust, full city index and debug build property.
   This creates a local admitted bundle, not a remote production deployment.
6. Select the exact canonical city and authority explicitly on the TV. Preview
   shows real values, scope, coverage and provenance. Activation rechecks the
   signed payload and preview's expected active snapshot in one local transaction.
   An error, stale choice or concurrent selection preserves last-known-good.

Admin and device choice envelopes use their respective `.../v2` version for
schema-2 revisions, including retained legacy choices within such a revision.
All applicable independently qualified authorities are returned without top-N.
Unproven places remain searchable but unavailable. The automatic resolver still
does not choose a religious authority for the operator. Source attribution must
be retained; required hyperlinks are an explicit user action, never TV scraping.

See [PERSISTED_POLICY_REGISTRY.md](PERSISTED_POLICY_REGISTRY.md) for v9 proof
storage, reader grants and downgrade refusal. Local/public activation does not
remove or rewrite existing mosque approvals, pilot receipts or review history.

## Retained legacy review outcome

`PROPOSAL`: a policy that cannot resolve automatically must remain visible and
explainable without becoming executable. The operator workflow therefore has
two separate operations:

1. assess one immutable registry revision for an explicit city, mosque and
   local date;
2. request review of one eligible policy from a staged revision.

The second operation records a choice as `pending_review`. It does **not**
approve a source, resolve same-tier overlap in place, activate a revision,
publish a snapshot, alter a device assignment, or replace last-known-good.
An authorized registry curator must create a new unambiguous revision with the
retained approval/publication evidence before the existing activation pipeline
can make it executable.

## Explainable assessment

`GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy-options` requires exact
`revision_id`, `city_id`, and local Gregorian `date` values. It returns the
revision identity/state, canonical city with subject and timezone, overall
status/reason, and every applicable option in deterministic precedence/ID
order.

Overall statuses are `resolved`, `ambiguous`, `stale`, and `unavailable`.
Stable reasons distinguish same-tier ambiguity, stale/unavailable/unapproved
sources, unavailable schedules, out-of-range policies and no policy. Every
option carries:

- exact precedence tier and `selectable` decision;
- blocked reason, including lower precedence and not-fresh-for-date;
- geographic scope, separately named authorities and evidence labels;
- source status/freshness, policy effective range and payload reference;
- timetable/calculation-profile and source-override provenance when present.

Only highest-precedence options with approved, date-fresh component sources
and an available date/mosque/timezone-compatible payload are selectable. Two
such options at one tier remain `ambiguous`; neither becomes the active answer.
No neighboring subject, coordinates, insertion order, or generic Russia method
breaks the tie.

City ambiguity is handled one step earlier by the exact catalog search. Every
same-name candidate remains subject-qualified; zero or multiple candidates do
not produce a canonical selection.

## Setup schedule-choice projection

T040 adds
`GET /v1/admin/mosques/{mosqueId}/setup/schedule-choices` as a smaller
setup/presentation projection of the same `PolicyAssessment`. It returns only
the complete set of highest-precedence eligible choices; this is not a second
resolver. The T039 endpoint above continues to return the complete applicable
option set, including lower-precedence, stale, unavailable, research-only,
out-of-range and schedule-missing records.

One canonical city may return zero, one or any number of choices. More than one
choice is a normal discovery result with `selection_required=true`; automatic
resolution remains `ambiguous`, and no insertion/lexical/display order selects
an authority. Choices are neutrally ordered by precedence tier and stable
policy ID. `PrayerAuthority.Name` is used directly as the canonical display
name; NamazTime does not invent abbreviations or use the source/transport name
as an authority label. Equal labels do not merge choices because policy and
stable choice IDs remain distinct.

The endpoint accepts an optional immutable `revision_id`. Without it, the sole
resolved active choice can be marked executable. With a staged revision, all
eligible choices remain selectable but non-executable and the existing
`request_binding` action leads only to `pending_review`. No top-N limit exists.
Any future transport bounding must use explicit pagination or equivalent
lossless mechanics and must never claim an incomplete set is complete.

## Explicit review handoff

`POST /v1/admin/mosques/{mosqueId}/setup/prayer-policy-binding-requests`
requires an admin bearer credential, mosque write scope and an idempotency key.
The request names the exact staged revision, city, policy, local date and
operator reason.

Before persistence, the service re-assesses that immutable revision and
requires the named option to be selectable. PostgreSQL then serializes against
registry activation, rechecks that the revision is still staged, and appends a
`registry_binding_requests` row plus the existing 24-hour idempotency record.
The retained selection SHA-256 binds revision content, city, policy, mosque,
date and tier; actor, reason, request ID and UTC time retain the audit context.
Binding-request rows reject `UPDATE`, `DELETE` and `TRUNCATE`.

Active, stale, unavailable, research-only, out-of-range, lower-precedence and
schedule-missing choices fail closed with
`registry_binding_not_selectable`. Requests never write
`registry_active_revision`, publication records, snapshots or assignments.

## Persistence and rollback

PostgreSQL migration v7 adds only the append-only request/handoff table and the
corresponding idempotency operation. Rolling v7 back to v6 removes pending
handoff rows; it does not alter the active registry, signed snapshots, device
assignments, or the Ulyanovsk offline pilot. This schema rollback is not an
operator decision rollback; retained production requests must be backed up
before migration rollback.

The API runtime role needs `SELECT` on registry revisions and
`SELECT`/`INSERT` on `registry_binding_requests`; it receives no permission to
stage or activate registry revisions. Registry activation remains a separate
operator-side control-plane action with verified approval and signed-snapshot
adapters.

## Device-originated setup proposals

T041 does not expose this admin workflow or its bearer to Android TV. A
provisioned TV uses separate device-scoped read routes; the server derives its
device/mosque identity and private configured setup revision. An explicit
choice appends to `device_registry_binding_requests` with status
`pending_review` and origin `local_tv_operator` plus a device audit event.

This proposal is input to later operator review only. It is not an admin actor,
does not create or activate a registry revision, and does not approve, publish,
sign or assign a snapshot. The active T039 surface remains the complete
operator/debug explanation layer. Schema v8 rollback deletes unreviewed device
proposals only; export them before rollback.

## Verification evidence

- `CONFIRMED_RUNTIME`: unit tests cover same-tier ambiguity, explicit policy
  choice, idempotent replay, active-revision rejection, stale/unavailable
  sources, expired freshness, missing schedule, out-of-range and no-policy
  explanations.
- `CONFIRMED_RUNTIME`: the Docker-backed PostgreSQL/HTTP test stages an
  ambiguous Ulyanovsk successor, exposes both exact-city options, appends one
  explicit `pending_review` request, proves append-only guards and confirms the
  active Ulyanovsk revision is unchanged.
- `CONFIRMED_RUNTIME`: the same test stages a stale-source successor, exposes
  its exact blocked reason and rejects a binding request without adding a row.
- `CONFIRMED_RUNTIME`: T040 unit/HTTP tests project 0, 1, 2, 3, 5 and 8
  synthetic equal-tier choices without truncation, preserve deterministic
  ordering and resolver ambiguity, exclude blocked/lower-tier records without
  removing them from T039, and keep equal presentation labels independently
  identifiable.
- `CONFIRMED_RUNTIME`: migration v7 upgrade/rollback/reapply and the current
  backup/restore gate pass while the signed Ulyanovsk snapshot bytes retain
  SHA-256 `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
- `UNKNOWN`: no production operator UI or production deployment has been
  exercised. The versioned API is the implemented operator surface for this
  slice; a visual admin client may consume it later without changing resolver
  rules.
