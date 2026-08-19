# ADR 0001: Source authority and signed snapshots

- Status: Proposed
- Date: 2026-08-19

## Context

Russian prayer schedules vary by locality, authority, seasonal methodology and mosque practice. A generic calculation method or web page cannot safely be treated as universally official. TVs also need to keep working without network and must reject tampered/broken updates.

## Decision

Each mosque is explicitly bound to an approved source. Every publication carries provenance and human approval. TVs receive immutable versioned snapshots with SHA-256 and Ed25519 signature, validate them in staging and retain last-known-good plus a previous version.

No source adapter, calculation library or parser can publish directly. No silent provider fallback is allowed.

## Consequences

Positive:

- traceable religious correctness;
- offline operation;
- safe rollback and tamper detection;
- source adapters remain replaceable.

Costs:

- approval workflow and signing infrastructure;
- more metadata/storage;
- onboarding each authority requires organizational work;
- stale data may remain visibly stale rather than silently calculated.

## Rejected alternatives

- live TV requests to public prayer API;
- one nationwide calculation profile labeled official;
- encryption-only snapshot without signature;
- automatic parser publication.
