# Persisted executable prayer-policy registry

Date: 2026-08-30
Status: T040 multi-authority choice projection implemented; no nationwide prayer-source rollout

## Outcome

`PROPOSAL`: PostgreSQL migration v6 persists immutable, versioned city/source
registry revisions. A staged revision may contain research-only records, but it
cannot become active until the service verifies every executable approval and
published-snapshot reference against their authoritative stores.

This is a control-plane registry. It neither publishes prayer rows nor changes
the Android snapshot contract. The TV continues to consume only its assigned,
signed snapshot from local persistence.

## Stored model

One revision owns normalized rows for:

- regions, cities, aliases and geographic scopes;
- prayer authorities and exact evidence labels;
- sources and ordered source-to-authority bindings;
- policies and explicit mosque/authority bindings;
- timetable and calculation-profile references;
- source overrides, fields and timetable composition order.

`registry_revisions.schema_version = 1` identifies the canonical dataset
encoding contract. `catalog_revision_id` independently records which T035
geographic revision supplied the city rows. The dataset's deterministically
sorted canonical JSON has a SHA-256 recorded on the revision and every
stage/activation/rollback audit event.

The research draft in `research/russia-prayer-source-registry-draft.json` is not
an import format and is never read by the executable registry.

## Lifecycle and trust boundary

```text
validated Dataset
  → stage immutable revision + content hash + audit
  → verify executable source status/freshness
  → verify exact mosque-scoped approval references
  → verify exact signed-snapshot ID/mosque/timezone/range/hash/key reference
  → reject same-tier overlap
  → atomically advance singleton active pointer + retain evidence audit
```

An ID is not proof. `ApprovalReferenceVerifier` and
`SnapshotReferenceVerifier` return independently verified evidence. Activation
records its hashes and timestamps; it cannot manufacture approval or signature
state. T037 supplies `ArtifactReferenceVerifier`, which independently checks
the signed mosque approval receipt, approval trust bundle, production
publication trust transition, test/staging/production key separation,
publication admission, exact snapshot identity and exact raw payload hash.
Manifest paths and hashes are pinned; private keys are neither read nor stored.

Activation fails closed when:

- a policy source is `research_only`, `stale`, `unavailable`, or not fresh for
  the whole effective range;
- one tier contains overlapping policies for the same geography and mosque;
- a policy lacks exactly one explicit mosque binding;
- approval evidence is missing, malformed, future-dated, or bound to another
  mosque;
- a timetable override component lacks approved/fresh source state or its own
  verified approval;
- published-snapshot evidence does not cover the exact timetable mosque,
  timezone or date range, or lacks a valid payload hash/signing-key reference.

There is no implicit country policy and no neighboring-city fallback.

## Immutability, activation and rollback

Revision-scoped tables, audit rows and verified-reference rows reject
`UPDATE`, `DELETE` and `TRUNCATE` with SQLSTATE `55000`. Only the singleton
active pointer is mutable. Stage and activation take a transaction-scoped
advisory lock and use serializable transactions; activation also compares the
previous active revision to prevent a lost update.

Registry rollback means re-verifying a retained immutable revision and moving
the active pointer to it while appending a `rollback` audit event. It does not
delete a newer revision, mutate a timetable, rewrite an approval, or alter a
published signed snapshot.

`cmd/registryctl` is the bounded operator entry point. `validate` composes a
strict reviewed policy-binding document with the exact catalog revision/hash
and verifies pinned reference artifacts without a database. `apply` reads the
database URL only from a named environment variable, stages the immutable
revision, re-verifies executable references and activates it with an explicit
actor/reason. The 96 MB generated geographic catalog and raw source dumps stay
outside Git.

Migration rollback from v6 to v5 removes only registry tables/functions and
preserves the existing fleet tables. This is a schema rollback, not a recovery
mechanism for an active production registry; operators must export/retain the
database before applying it.

Migration v7 adds append-only `registry_binding_requests` for the T039
operator-review handoff. A request binds one staged revision content hash,
city, policy, mosque, local date and precedence tier. It remains
`pending_review`; it cannot update the active pointer, approve a source,
publish a snapshot or change a device assignment. Rolling v7 back to v6 removes
these pending requests only, so operators must back them up before rollback.

T040 adds no table or migration. `CityScheduleChoiceSet` is computed from one
loaded immutable revision and its existing `PolicyAssessment`; it is never a
source of truth and is not persisted. Existing `registry_authorities.name` is
already mandatory, immutable and covered by the revision content hash, so no
separate mutable display label or religious ranking is introduced. PostgreSQL
v6/v7 rollback and reapply semantics are unchanged.

## Database roles

The registry writer/activator needs write access and remains an operator-side
control-plane process. The API registry reader needs `SELECT`; the admin
handoff additionally needs `SELECT`/`INSERT` only on
`registry_binding_requests`. It receives no registry revision/pointer write
permission. The PostgreSQL restore drill recreates a read-only recovery role
and proves it can read the registry while lacking registry `INSERT`/`UPDATE`,
audit `UPDATE`, and schema `CREATE` privileges.

## Search behavior

PostgreSQL search uses the active revision only and performs exact
case/whitespace-normalized matching over canonical names and explicit aliases.
Results are deterministically ordered by federal-subject code, canonical name
and city ID. `UniqueActiveCity` succeeds only when exactly one result exists;
zero or multiple matches never select a city automatically.

The API runtime opens a separate read-only registry reader only when
`registry_backend` is `postgres`. Authenticated setup search returns all exact
candidates with federal subject, timezone and geographic provenance. Policy
resolution requires the selected `city_id`, mosque path and local Gregorian
date; unavailable and ambiguous results fail with stable `409` codes and do
not alter the active registry or any device assignment.

T039 adds a revision-specific assessment endpoint that returns every applicable
option with precedence, authority evidence, scope, freshness and a stable
blocked reason. A second endpoint accepts only an explicitly named selectable
policy from a still-staged revision and appends a `pending_review` request. The
request is an audit/review handoff, not executable state. See
`REGISTRY_OPERATOR_WORKFLOW.md`.

T040 adds a separate authenticated
`GET .../setup/schedule-choices` projection. Omitting `revision_id` assesses the
active revision; supplying it assesses that exact staged or active revision.
The result contains every highest-tier eligible choice with stable
city+policy-derived identity, canonical authority label, evidence/provenance,
scope, source, effective range and payload reference. It has no cardinality
cap or top-N filtering. Same-tier multiplicity is available for explicit choice
discovery while the automatic resolver remains ambiguous and the active
execution path remains fail closed.

## Verification evidence

- `CONFIRMED_RUNTIME`: the local Docker-backed PostgreSQL 18 gate applies v7,
  stages and activates two synthetic revisions, searches duplicate names,
  verifies canonical hashes, rolls back the active revision, checks retained
  evidence/audit rows, rejects append-only mutations, rolls current migrations
  down without deleting fleet state, and reapplies v7.
- `CONFIRMED_RUNTIME`: the backup/restore drill restores v7 and verifies the
  least-privileged runtime role described above.
- `CONFIRMED_RUNTIME`: the T037 PostgreSQL/HTTP gate activates the real
  Ulyanovsk binding against its real approval/publication evidence, resolves
  `Ульяновск` to `RU-ULY`, the Second Cathedral Mosque and the existing signed
  snapshot, activates a successor revision, rolls back and proves exact
  snapshot bytes/SHA-256 remain unchanged.
- `CONFIRMED_RUNTIME`: the T039 PostgreSQL/HTTP gate explains two same-tier
  staged options, persists exactly one idempotent append-only review request,
  leaves the active revision unchanged, then explains and rejects a stale
  source choice.
- `CONFIRMED_RUNTIME`: the extended PostgreSQL/HTTP gate projects the active
  Ulyanovsk revision as one executable choice, a staged same-tier revision as
  two selectable/non-executable choices, and a stale revision as no selectable
  choices while retaining the T039 blocked explanation.
- `UNKNOWN`: production backup retention, RPO/RTO and external approval/source
  store recovery have not been exercised; the local drill makes no such claim.
