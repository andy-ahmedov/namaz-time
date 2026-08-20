# ADR 0004: Production pairing uses durable hashed credentials and database scope

- Status: Proposed
- Date: 2026-08-20

## Context

T009 proves the Android provisioning and signed-snapshot read path with an
explicitly process-local test fixture. That fixture cannot provide expiry,
restart-safe single use, credential revocation, durable attempt accounting or
operator audit. Remote administration also requires mosque isolation below the
HTTP/UI layer.

T010 is blocked on source/approval/signing/hardware inputs, but pairing and
fleet identity are logically independent and can proceed without changing any
prayer-time authority claim.

## Decision

Production pairing state is stored in PostgreSQL. The server creates 128-bit
Base32 pairing codes and 256-bit Base64url device bearer credentials with the
operating-system CSPRNG. Plaintext code/token material is returned only at the
required boundary; PostgreSQL stores 32-byte SHA-256 verifiers.

Redemption uses one transaction with row locking to:

1. update persistent HMAC-SHA-256 rate buckets for code, device and direct
   network source;
2. lock and validate the code/device/mosque relation;
3. consume the one-use code;
4. activate the device with its token verifier and bounded metadata;
5. append immutable audit evidence.

Devices have `pending`, `active` and `revoked` states. A composite
`(device_id, mosque_id)` foreign key prevents a pairing record from crossing a
mosque boundary. Revocation is scoped by both identifiers and removes the token
verifier atomically. Audit rows reject update/delete.

Runtime JSON contains only environment-variable names for the PostgreSQL URL
and 32-byte rate HMAC key. Startup migrates under a transaction advisory lock
and fails closed. Remote endpoints require verified TLS, while loopback/Unix
development endpoints may explicitly disable it. Pair/auth calls and rollback
cleanup have bounded contexts. The service does not trust forwarded-address
headers by default.

Audit triggers reject update, delete and truncate under the runtime principal.
A production deployment must still separate the schema/migration owner from a
least-privileged runtime role; trigger enforcement cannot defend against its
own table owner changing schema.

T011 exposes issue/revoke as internal service commands only. T012 must add
authenticated actors, role/membership authorization, idempotent admin HTTP
endpoints and persistent snapshot assignments before remote administration is
operational.

## Consequences

Positive:

- single use, expiry, rate state and revocation survive process restart;
- concurrent API processes cannot both redeem one code;
- database and service layers both preserve mosque scope;
- no recoverable device credential or raw rate identity is stored server-side;
- pairing work does not weaken T010's source/publication blockers.

Costs:

- remote pairing now depends on PostgreSQL availability at issue/redeem time;
- deployment must protect and rotate the database credential and HMAC key;
- a reverse proxy must preserve a trustworthy direct peer address or accept a
  deliberately shared source-rate bucket;
- authenticated admin issuance and manifest assignment remain a separate task.

## Rejected alternatives

- promoting T009 plaintext/process-local fixtures to production;
- storing bearer tokens or pairing codes encrypted for later recovery;
- relying only on an in-process/IP rate limiter;
- enforcing mosque isolation only in handlers or UI filters;
- trusting arbitrary `X-Forwarded-For` values;
- coupling pairing completion to blocked real-source onboarding.
