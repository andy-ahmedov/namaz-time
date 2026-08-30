# Persisted executable prayer-policy registry

Date: 2026-08-30
Status: T036 implemented; no nationwide prayer-source rollout

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
state. T037 supplies adapters to the existing Ulyanovsk evidence chain.

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

Migration rollback from v6 to v5 removes only registry tables/functions and
preserves the existing fleet tables. This is a schema rollback, not a recovery
mechanism for an active production registry; operators must export/retain the
database before applying it.

## Database roles

The registry writer/activator needs write access and remains an operator-side
control-plane process. The API runtime role needs `SELECT` only. The PostgreSQL
restore drill recreates a read-only runtime role and proves it can read the
registry while lacking registry `INSERT`/`UPDATE`, audit `UPDATE`, and schema
`CREATE` privileges.

## Search behavior

PostgreSQL search uses the active revision only and performs exact
case/whitespace-normalized matching over canonical names and explicit aliases.
Results are deterministically ordered by federal-subject code, canonical name
and city ID. `UniqueActiveCity` succeeds only when exactly one result exists;
zero or multiple matches never select a city automatically.

## Verification evidence

- `CONFIRMED_RUNTIME`: the local Docker-backed PostgreSQL 18 gate applies v6,
  stages and activates two synthetic revisions, searches duplicate names,
  verifies canonical hashes, rolls back the active revision, checks retained
  evidence/audit rows, rejects append-only mutations, rolls v6 down to v5
  without deleting fleet state, and reapplies v6.
- `CONFIRMED_RUNTIME`: the backup/restore drill restores v6 and verifies the
  least-privileged runtime role described above.
- `UNKNOWN`: production backup retention, RPO/RTO and external approval/source
  store recovery have not been exercised; the local drill makes no such claim.
