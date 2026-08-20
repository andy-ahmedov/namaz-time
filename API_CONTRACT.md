# API_CONTRACT.md

The machine-readable draft is [contracts/openapi.yaml](contracts/openapi.yaml). This document defines semantics that OpenAPI alone does not capture.

## Principles

- TV reads local state; API is a synchronization channel, not the UI source of truth.
- All requests use HTTPS.
- Pairing credentials are scoped, revocable and never placed in logs.
- Snapshot payload is immutable and content-addressed.
- Device manifest is small and cacheable.
- Server may return no update without forcing a full snapshot download.

## Pairing

`POST /v1/devices/pair`

Request contains a short-lived code plus device metadata and optionally a device-generated public key. Success returns:

- opaque `device_id`;
- scoped device token or certificate bootstrap;
- mosque identity and timezone;
- current manifest URL/version.

Security:

- rate limit by code/IP/device fingerprint without invasive tracking;
- code is single-use or low-use and expires in minutes;
- return identical public error for invalid/expired code;
- token stored in Android Keystore where available.

T009 stores the returned provisioning envelope with AES-256-GCM under an
Android Keystore key. A normal local-only install has no fixture credential and
does not schedule remote work.

## Manifest

`GET /v1/devices/{deviceId}/manifest`

Headers:

```text
Authorization: Bearer <device-token>
If-None-Match: "manifest-etag"
```

Response:

- `304` if unchanged;
- `200` with current snapshot ID/version/hash/size/signing-key ID and optional asset list;
- optional `minimum_app_version` and controlled rollout metadata;
- no prayer rows in the manifest.

The manifest and snapshot URL use the same HTTPS origin while the same bearer
token authenticates both requests. A future CDN must use an explicit host
allowlist and separate credential design; the client never forwards a bearer
token to an arbitrary manifest-selected host.

`manifest_version` is monotonic. An authorized rollback increments the
manifest version while pointing to a prior immutable signed snapshot. Reusing
one manifest version with different snapshot ID/hash/length/key is rejected.

The device must not delete its active snapshot when authentication or manifest retrieval fails.

## Snapshot download

`GET /v1/snapshots/{snapshotId}`

Response body conforms to `prayer-snapshot.schema.json`. Recommended headers:

```text
Content-Type: application/json
ETag: "<payload-sha256>"
Digest: sha-256=<base64>
Cache-Control: private, max-age=...
```

The application-level Ed25519 signature inside/enveloping the snapshot remains mandatory; TLS and HTTP digest are not substitutes.

T009 limits both manifest-declared and downloaded snapshots to 5 MiB, requires
the raw byte length/SHA-256 from the manifest, then independently verifies ADR
0003 canonical SHA-256, signing key, signature, schema and domain rules.
Rejected raw bytes are quarantined for bounded local diagnostics and never
imported.

## Heartbeat

`POST /v1/devices/{deviceId}/heartbeat`

Contains only operational status:

- app/OS/device model;
- active snapshot ID;
- last sync outcome code;
- days of coverage remaining;
- clock/timezone mismatch flags;
- storage/memory health bucket;
- boot/kiosk capability flags.

Do not send precise GPS, Wi-Fi SSID/BSSID, installed-app list, user account, advertising ID or full logs by default.

## Error format

```json
{
  "error": {
    "code": "snapshot_not_found",
    "message": "Snapshot is unavailable",
    "request_id": "...",
    "retryable": false
  }
}
```

Client behavior is driven by stable `code`, not localized `message`.

## Versioning

- URL major version: `/v1`;
- snapshot schema has independent `schema_version`;
- additive fields require clients to tolerate unknown properties only where schema explicitly permits them;
- breaking snapshot changes require dual-publishing during migration;
- backend can require a minimum app version but should preserve a safe offline display.

## Idempotency

Admin write endpoints added later should accept `Idempotency-Key`. Pairing is logically single-use. Heartbeat is safe to repeat.

## Clock semantics

All API timestamps are RFC 3339 UTC. Prayer rows are mosque-local `HH:MM` plus the snapshot IANA timezone. Never infer prayer date from server UTC date.

## Rollout

Manifest selection can depend on a server-side rollout group. A device receives exactly one active snapshot. Rollback means manifest points back to a known valid snapshot version; the TV still verifies it normally.

At T009, non-empty remote asset lists are rejected explicitly. Built-in themes
remain the safe fallback until the separately staged asset pipeline exists.

## T009 runtime configuration

`cmd/api` starts only with `-config <private-json>`; the config file must be a
regular non-symlink file with mode `0600`. It references bounded snapshot and
public-key files inside the same directory. Registry startup runs full
signature/schema/domain verification, binds the signed mosque ID/timezone to
the paired device and requires snapshot URLs to use the configured public HTTPS
origin plus canonical snapshot path before serving any bytes.

The T009 pairing issuer is deliberately an `ephemeral-test-only`, process-local
fixture. It rejects duplicate code/token values but does not claim durable
expiry, attempt accounting or restart-safe consumption. A production pairing
issuer must implement those controls in persistent storage; codes and tokens
are never committed as defaults.
