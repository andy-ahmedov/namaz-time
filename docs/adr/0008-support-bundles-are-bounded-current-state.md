# ADR 0008: Support bundles are bounded current state

- Status: Proposed
- Date: 2026-08-20

## Context

Support needs a concise answer to which version a display runs, which snapshot
is assigned/reported and whether clock, coverage or storage needs attention.
Free-form logs and repeated exports would create a new sensitive telemetry path
and unnecessary history.

## Decision

`device-support-bundle/v1` is generated on demand under existing mosque read
RBAC. PostgreSQL joins exactly one device, its optional current durable
assignment and its optional latest-only health row. The service supplies schema
version and server generation time; the HTTP response is `no-store`.

The closed type includes only bounded device lifecycle/version fields, mosque
timezone, rollout label, assignment identity/hash/signing-key ID/version and the
existing
health allowlist. It has no credential, pairing code, installation key,
capability list, snapshot URL, network/account/location identifier, arbitrary
map, log or history field. Missing assignment/health is omitted, never inferred.

## Consequences

Positive:

- support receives reproducible, clearly classified current state;
- export adds no collection, table or retention job;
- tenant isolation matches the fleet list boundary;
- secrets and high-cardinality diagnostics are structurally absent.

Costs:

- historical trends and raw logs cannot be reconstructed from the bundle;
- device-reported timestamps/snapshot remain non-authoritative;
- ticketing and secure operator handling remain deployment responsibilities.

## Rejected alternatives

- uploading free-form device logs;
- including tokens, URLs, network identifiers or installation keys;
- retaining every generated bundle;
- inventing previous snapshot/sync values that the server does not store;
- collecting additional TV data solely for support convenience.
