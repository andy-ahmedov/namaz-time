# ADR 0009: Fleet backups restore into a clean database

- Status: Proposed
- Date: 2026-08-20

## Context

PostgreSQL now owns durable pairing, device, assignment, latest-health,
idempotency and audit state. A backup that merely contains rows is insufficient:
recovery also has to preserve exact schema compatibility, relational bindings,
credential verification and append-only enforcement. Restoring over a live
database risks mixing recovery points and partially replacing current state.

The database is not the only durable system. Signed snapshot/raw-source
artifacts, trust configuration and publication signing keys have different
access and recovery boundaries.

## Decision

Fleet logical backups use PostgreSQL custom format. Restore suppresses archived
ownership and ACL commands; roles and grants come from deployment configuration.
Archives are stored as sensitive encrypted artifacts with an authenticated,
immutable inventory; SHA-256 and byte count are retained as corruption evidence
but are not treated as source authenticity.

A restore targets a newly created isolated database and uses one transaction
with fail-fast error handling. Deployment automation separately recreates roles
and least-privilege grants. Before any API replica is pointed at the database,
the runtime implementation must accept the exact migration ledger, read linked
identity/assignment/latest-health state and prove both append-only trigger sets
still reject update, delete and truncate. Referenced immutable snapshot
artifacts and trust configuration are verified as a separate recovery unit.

## Consequences

Positive:

- restore cannot silently merge an old recovery point into live tables;
- the drill exercises application invariants, not only row counts;
- database ownership remains separated from the long-running runtime role;
- corrupt/truncated archives fail closed before service startup.

Costs:

- role/grant, snapshot/source and signing-key recovery need distinct automation;
- logical restore does not provide point-in-time recovery by itself;
- production RPO/RTO require measured infrastructure-specific drills;
- restored production-derived data needs the same privacy controls as live data.

## Rejected alternatives

- restoring directly over the production database;
- treating a successful `pg_restore` or row count as complete validation;
- preserving broad owner/ACL state in a portable archive;
- treating an unauthenticated checksum as proof of backup origin;
- claiming database recovery also recovers publication keys or snapshot bytes.
