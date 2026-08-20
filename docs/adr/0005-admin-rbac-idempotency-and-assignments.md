# ADR 0005: Fleet administration is role-scoped and idempotent

- Status: Proposed
- Date: 2026-08-20

## Context

T011 provides durable device pairing commands but intentionally exposes no
operator endpoint. T012 needs a minimum remote administration surface without
creating an unauthenticated bootstrap path, leaking cross-mosque existence, or
allowing repeated writes to create duplicate devices/audit events.

Pairing-code issuance has a special idempotency constraint: an HTTP retry must
receive the same plaintext code, while PostgreSQL must retain only a verifier.
Persistent device assignments must also reference only snapshots already
verified by the immutable device API registry; T012 cannot become a back door
around T010 approval/signing requirements.

## Decision

PostgreSQL migration v2 adds active/suspended admin actors, hashed bearer
credentials, role/mosque memberships, durable idempotency records and device
assignments. There is no HTTP bootstrap endpoint. A privileged out-of-band
process creates the first actor, 256-bit bearer token verifier and memberships;
the runtime database role only authenticates and operates them.

The schema-owner DSN is injected only into a short-lived out-of-band migration
process. The long-running API receives only a least-privileged runtime DSN,
read-only verifies exact schema v2, and cannot alter schema/audit/idempotency
triggers or provision actor credentials.

The role matrix is:

- `service_admin`: global fleet read/write;
- `mosque_admin`: fleet read/write only for its mosque membership;
- `viewer_support`: read-only for its mosque membership;
- `approver`: read-only fleet visibility for its mosque, with publication
  approval remaining outside T012.

The manager checks scope before repository calls, and every PostgreSQL
transaction rechecks the active actor/membership. Authorization rows remain
read-only to the runtime role; only device/code rows being mutated are locked.
Unauthorized and out-of-scope resources share the same `404` outcome after
authentication.

Write requests require an 8–128 character `Idempotency-Key`. Stable request
hashes detect key reuse with different payloads. Pairing codes and resource IDs
are derived with domain-separated HMAC-SHA-256 from a protected 32-byte key;
only the code verifier and idempotency/request hashes persist. The runtime
accepts an ordered compatibility-key ring. Rotation first stages the future
key on all replicas, then switches the current key while retaining the old one;
the old key is removed only after the 24-hour retry guarantee. Non-secret
assignment responses are stored in the idempotency row so an old retry returns
its historical manifest version even after a later assignment. Expired evidence
returns `409` and never repeats the mutation. Assignment request hashes bind
only client-semantic fields, and stored evidence is consulted before current
artifact metadata, preserving retries across registry/config changes.

Assignment requests contain only a snapshot ID, minimum app version and audit
reason. The server resolves hash, signing key, canonical URL, mosque and
timezone from its already verified immutable registry. PostgreSQL rechecks the
device/mosque/timezone relation and increments `manifest_version`; the device
read path revalidates the stored assignment against the local registry before
serving it.

## Consequences

Positive:

- no unauthenticated admin bootstrap or plaintext admin token in PostgreSQL;
- global and local roles are enforced in both service and transaction layers;
- cross-mosque reads/writes do not reveal whether a resource exists;
- issue/revoke/assignment retries do not duplicate state or audit events;
- durable assignments feed the existing signed manifest/snapshot contract;
- HMAC key rotation can retain exact pairing-code retry behavior.
- schema-owner credentials exist only in a short-lived migration process; API
  startup verifies exact v2 through its runtime role without DDL.

Costs:

- initial actor/credential provisioning remains an explicit privileged
  operational step;
- compatibility HMAC keys must remain configured while their idempotency records may
  be retried, then be retired deliberately;
- admin bearer login/session UI, federation and MFA are future hardening, not
  implied by this API-token foundation;
- T010 remains blocked; T012 can assign only an artifact already admitted to
  the runtime registry and does not approve or sign it.

## Rejected alternatives

- an unauthenticated first-admin HTTP route;
- trusting mosque IDs supplied by a browser without membership checks;
- storing plaintext or reversibly encrypted pairing codes for retries;
- process-local idempotency caches;
- returning the current assignment for an older idempotency key;
- accepting arbitrary snapshot URL/hash/key fields from an administrator.
