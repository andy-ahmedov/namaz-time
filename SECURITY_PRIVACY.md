# SECURITY_PRIVACY.md

## Security goals

- a malicious or broken server cannot make the TV display unsigned/tampered times;
- an operator cannot change another mosque;
- a parser cannot self-publish;
- a failed update cannot destroy last-known-good;
- pairing and device credentials are revocable and minimally scoped;
- local mode works without unnecessary personal data.

## Trust boundaries

1. external authority website/file/API;
2. backend ingest/parser;
3. human approver;
4. snapshot signer/publisher;
5. public network/CDN;
6. Android TV device and local storage;
7. admin browser.

Treat external content and the TV device as potentially compromised.

## Snapshot authenticity

- sign canonical snapshot payloads with Ed25519;
- pin trusted publication public keys in the app/config with rotation support;
- include key ID, schema version, payload hash and effective range;
- reject unknown/revoked key, invalid signature, hash mismatch or downgrade outside policy;
- retain previous valid snapshot;
- protect private signing key in KMS/HSM or isolated signer—not in repository or TV APK.

Encryption may protect confidentiality, but it does not establish publisher authenticity by itself.

## Source integrity

- store raw hash and retrieval metadata;
- parser output remains candidate until human approval;
- approval binds to raw and diff hashes;
- parser schema drift trips a circuit breaker;
- official HTML/file use requires terms/license/attribution record;
- never bypass website protection or access controls.

## Pairing and authentication

- short-lived random pairing code, stored hashed;
- rate limiting and attempt limits;
- device token scoped to one device/mosque and read/heartbeat operations;
- Android Keystore storage where available;
- rotate/revoke from admin;
- no secret in QR after pairing expiry;
- no tokens in URLs or logs.

T009 additionally requires the manifest and bearer-authenticated snapshot URL
to share one HTTPS origin. Android encrypts the provisioning record with
AES-256-GCM and fixed context AAD under a non-exportable Android Keystore key;
tamper/wrong-key reads fail closed. Pending transport state is fingerprinted to
the paired device, mosque, timezone and manifest origin; re-pairing quarantines
old staged bytes without mutating Room. Both the Go assignment gate and Android
activation gate bind signed snapshot mosque ID/timezone to provisioning.

The Go runtime config containing fixture credentials must be mode `0600` and
cannot reference symlinks or files outside its directory. Its T009 pairing
issuer is explicitly process-local and test-only, not the persistent expiring/
rate-limited production issuer required by this section.

T011's production issuer uses 128 bits of CSPRNG entropy for the short-lived
Base32 code and 256 bits for the Base64url bearer token. PostgreSQL retains only
32-byte SHA-256 verifiers. Code redemption, device activation and audit append
are one row-locked transaction; invalid, expired, used and revoked states share
one public response. HMAC-SHA-256 attempt buckets cover code, normalized device
metadata/public key and the direct peer address without storing those raw
values. The HMAC key and database URL are injected through named environment
variables and are never accepted as literal JSON config fields.

Remote PostgreSQL connections fail closed unless TLS authenticates the server;
plaintext is limited to loopback/Unix-socket development. Pair/auth database
work is request-deadline bounded. Audit triggers reject update, delete and
truncate, while deployment must additionally keep the runtime database role
from owning or altering the schema.

The composite device/mosque database key and service-layer scoped revocation
provide defense in depth. T011 has no admin HTTP issuer, so it does not imply
an authenticated operator model; T012 must add role/membership checks before
these internal issuance/revocation commands become remotely callable.

## Admin authorization

Roles:

- `service_admin` — source/device operations;
- `mosque_admin` — content/config for assigned mosque;
- `approver` — schedule/iqamah approval;
- `viewer/support` — read-only diagnostics.

Sensitive publication may require two-person control or at least a distinct approver role. Every action is audit logged.

## Android permissions policy

MVP should require as little as possible:

- `INTERNET`, `ACCESS_NETWORK_STATE` for sync;
- `RECEIVE_BOOT_COMPLETED` only if best-effort autostart is enabled;
- foreground/wake-related permissions only when a concrete feature requires them.

Avoid by default:

- contacts;
- advertising ID;
- broad external storage access;
- continuous precise location;
- microphone/camera;
- overlay permission unless the chosen autostart design genuinely requires it and is explained;
- install/query-all-packages permissions.

Location can be selected from a curated mosque catalog. One-time coarse geolocation during onboarding is optional, not a runtime dependency.

## Privacy data map

### Local-only mode

Stores mosque selection, schedule, iqamah, theme and campaign locally. No user account or behavioral analytics required.

### Remote mode

Backend may store:

- organization/mosque operator account;
- device ID/model/app version;
- active snapshot and health timestamps;
- configuration/audit records.

Do not collect precise attendee data. A mosque display is not an audience-tracking device.

## Analytics/crash reporting

Default proposal: no advertising SDKs. Crash reporting is opt-in per deployment or privacy-reviewed, with:

- no schedule URLs/tokens;
- no precise location;
- bounded retention;
- documented processor/region;
- store declarations matching runtime behavior.

The presence of analytics/location libraries in a competitor APK is not a reason to include them.

## QR safety

- HTTPS-only default;
- allowlist or operator warning for domains;
- preview and human confirmation;
- audit target changes;
- optional own redirect only with transparent destination and abuse controls;
- no payment credentials handled by TV app;
- expire campaigns automatically.

## Snapshot authenticity

- canonical SHA-256 and Ed25519 verification precede strict snapshot decoding
  and Room activation;
- signing-key IDs resolve only through an injected public-key trust store;
- unknown keys, tampering, invalid signatures and payloads over 5 MiB fail
  closed without changing last-known-good;
- Android uses the official Tink Android Ed25519 verifier for minSdk 28 rather
  than assuming a newer platform JCA provider;
- the Phase 1 verification key is public/test-only; no private key or
  production trust anchor is stored in Git or the APK;
- production key custody, rotation and revocation remain D-013.

The Go device registry also performs the full public-key signature/schema/domain
verification at startup. A matching raw download SHA alone cannot make bytes
servable. Android independently repeats verification and binds manifest ID,
raw length/hash and signing-key ID before Room activation.

## T009 Android permission review

- explicit application permission: `INTERNET`;
- WorkManager merge requirements: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` for constrained work,
  rescheduling and library foreground support;
- cleartext traffic is explicitly disabled;
- regression tests assert absence of location, contacts, camera, microphone and
  external-storage permissions.

Remote work is not scheduled in local-only/unprovisioned mode. These permissions
do not authorize UI network reads, analytics, identifiers or background source
scraping.

## Asset upload safety

- server-side content-type sniffing;
- pixel/dimension/byte limits;
- decode in sandboxed worker where possible;
- strip metadata;
- re-encode to approved format;
- hash and content-address;
- no SVG/HTML in MVP;
- licensed/owned asset confirmation.

## Threat scenarios

| Threat | Control |
|---|---|
| compromised CDN serves modified time | Ed25519 verification |
| parser sees redesigned HTML | fixture/schema drift blocks candidate |
| insider changes times | approval separation + audit + diff |
| stolen pairing code | short expiry, single use, rate limit |
| TV token leaked | device scope, revocation, no write authority |
| rollback attack | monotonic policy + signed version/revocation |
| image decompression bomb | limits + controlled re-encode |
| wrong device clock | diagnostics + mosque timezone + signed server-time hint |
| support bundle leaks secrets | structured allowlist export + redaction tests |

## Supply chain

- pin dependency versions;
- generate SBOM for releases;
- Dependabot/Renovate or equivalent with reviewed updates;
- secret scanning;
- reproducible/traceable builds where practical;
- verify release signing and store provenance;
- do not commit competitor APK or extracted proprietary artifacts.

## Incident response

1. stop publication/rollout;
2. preserve active known-good snapshots;
3. revoke affected signer/device/admin credentials;
4. point manifests to safe prior version if authorized;
5. notify affected mosque operators with precise scope;
6. preserve audit/evidence;
7. remediate and run post-incident tests;
8. document cause and prevention.
