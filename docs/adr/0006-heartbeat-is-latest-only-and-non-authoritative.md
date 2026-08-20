# ADR 0006: Device heartbeat is latest-only and non-authoritative

- Status: Proposed
- Date: 2026-08-20

## Context

Fleet support needs to know whether a paired display was recently online, what
snapshot it reports and whether its storage, clock or kiosk state needs
attention. A heartbeat must not become an analytics stream, a source of prayer
schedule authority, or a dependency of the offline display.

Client time and reported snapshot identity are untrusted diagnostic claims.
Keeping every report would create unnecessary behavioral history and an
unbounded storage path. Conversely, using only the client timestamp for
`last_seen_at` would let a bad device clock corrupt fleet freshness.

## Decision

Migration v3 adds one `device_health` row per device plus server-owned
`devices.last_seen_at`. Heartbeat authentication uses the existing opaque
device bearer and requires the URL device ID to match its principal. The
PostgreSQL write rechecks the active device/mosque row and serializes on the
device; revoked, missing and cross-mosque principals fail closed.

The request is a strict allowlist: app/OS/model, reported snapshot ID, bounded
sync status, coverage days, clock/timezone flags, storage/memory buckets and
boot/kiosk modes. It cannot carry logs, URLs, network identifiers, accounts,
location or arbitrary diagnostic codes. `received_at` comes from the API clock
and is the authoritative last-seen value. `sent_at` remains a reported value;
the server forces `clock_mismatch` when the difference exceeds 15 minutes.

`device_health` is upserted rather than appended. A backward server-clock value
cannot replace newer health. Reported snapshot ID never mutates or validates
the durable assignment and cannot activate Room data. Admin list reads expose
only the allowlisted latest projection inside the existing mosque RBAC scope.

Android derives the heartbeat URL from the already provisioned manifest origin
and exact device path. Reporting may be attached after a completed sync, but
all non-cancellation reporting failures are ignored by the wrapper; the original
sync result and local display remain authoritative.

## Consequences

Positive:

- trusted server last-seen and useful bounded health without history growth;
- bearer, path, device and mosque scope are enforced at multiple layers;
- heartbeat cannot change assignments, snapshots or offline display state;
- privacy-sensitive fields fail strict decoding rather than being ignored;
- retry/outage behavior does not turn successful sync into failure.

Costs:

- only the newest report is available; trend analysis is deliberately absent;
- device-reported fields may be wrong and must be labelled as reported;
- actual Android runtime wiring and physical-TV evidence remain separate from
  the tested client/runner components until provisioning deployment exists.

## Rejected alternatives

- append-only raw heartbeat history;
- treating client `sent_at` as trusted last-seen;
- accepting arbitrary maps, full logs or support bundles in heartbeat;
- changing assignment from `active_snapshot_id` in the request;
- making snapshot sync or public display success depend on heartbeat delivery.
