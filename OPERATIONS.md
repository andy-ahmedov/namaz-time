# OPERATIONS.md

## Operating objective

The screen should fail stale-but-correct rather than fresh-but-unverified. Network loss is normal; wrong time is an incident.

## Daily device behavior

- render from local Room database;
- roll local date in mosque timezone;
- schedule background manifest checks with WorkManager;
- also sync on explicit operator action and sensible app start/network recovery;
- cache active and previous snapshots;
- keep built-in theme and safe diagnostics offline.

WorkManager is inexact and may be delayed by OS constraints. Never design a religious-time boundary around a worker firing at an exact clock time.

## Suggested sync cadence

For published snapshots:

- manifest check: daily or every few days with ETag and jitter;
- urgent update: push/foreground hint may request a one-shot sync, but display remains local;
- asset prefetch: only after manifest/snapshot validation;
- heartbeat: low frequency, batched and not required for display.

The first source ingest cadence depends on source policy. Annual files may be checked weekly/monthly; dynamic official pages can be checked daily; parser frequency must stay within terms and operational need.

## Coverage alerts

Backend and TV expose:

- `coverage_days_remaining`;
- warning at 30 days;
- critical at 7 days;
- expired at no current-day row.

Exact thresholds are configurable and approved for the pilot.

## Clock health

Checks:

- system timezone vs mosque timezone;
- system time compared to the HTTPS manifest response `Date` when online;
- monotonic countdown anomalies;
- large wall-clock jump;
- NTP/automatic-time setting visible in installation guide.

The app should recalculate display state after clock/timezone broadcasts. It must not require exact-alarm permission just to render a countdown.

T020 records `clock_mismatch` only when a valid manifest `Date` differs from
local response receipt by more than five minutes, or the wall clock moves
backward during that request. Missing/malformed evidence does not clear or
manufacture health. The nullable sample survives process restart, but the
current required-boolean heartbeat contract cannot represent `unknown`; no
production mapping is claimed until that contract and its state assembler are
updated. Sync/activation remains authoritative and unblocked. A clock wrong
enough to fail TLS certificate validation cannot
receive this hint; installation guidance and physical wrong-RTC testing remain
required.

## Boot and kiosk

### Best-effort installation

- boot receiver enabled only by operator setting;
- document OEM “auto start/energy optimization/last app” settings;
- keep screen on while active;
- keep the bounded foreground retention shift enabled; treat it as a local UI
  measure, not a replacement for panel/OEM signage and retention settings;
- recover from process death;
- show diagnostics if overlay/autostart capability unavailable.

### Managed installation

- provision as fully managed dedicated device;
- allowlist app for lock task;
- optionally assign custom Home;
- control OS/app updates and remote support through DPC/EMM;
- test factory-reset/provisioning recovery.

Do not promise guaranteed boot launch in ordinary consumer mode.

## Source ingest runbook

Inspect the two immutable pilot inputs and their effective composition:

```bash
go run ./cmd/ingestor inspect --fixture-dir fixtures/pilot/ulyanovsk-2026
go run ./cmd/ingestor inspect --fixture-dir fixtures/pilot/ulyanovsk-2026-08
go run ./cmd/ingestor inspect-effective \
  --baseline-dir fixtures/pilot/ulyanovsk-2026 \
  --override-dir fixtures/pilot/ulyanovsk-2026-08
```

The effective command never approves, signs or publishes. The committed pilot
approval receipt separately binds its policy/component hashes, normalized
hash, diff hash, all warning codes and accepted D-009 mosque policy. Recreate
and verify it with `cmd/approver`; never edit the receipt or policy in place.

1. retrieve artifact with identifiable user agent and rate limit;
2. verify status/content type/size;
3. store raw hash/metadata;
4. parse with pinned parser version;
5. run validation and diff;
6. if shape changed, stop and alert—do not “best effort” publish;
7. obtain approval;
8. build/sign snapshot;
9. canary rollout;
10. verify activation before broad rollout.

For the pilot, the approved operational transform is source-aware: candidate
`dhuhr_congregation` becomes the TV Dhuhr adhan, while the original onset stays
in candidate provenance; iqamah is +5 minutes; Friday Dhuhr iqamah is replaced
by one Jumuah at 13:15. Any change creates a new policy hash and requires a new
signed approval. Correction actions are assigned to the admin workflow, but
D-014 is accepted: last-known-good is displayed only while the current
mosque-local date is covered. After coverage ends the client shows the bounded
schedule-unavailable state and never silently calculates or changes provider.

## Snapshot rollback runbook

1. identify affected mosque/device group and bad snapshot;
2. pause rollout;
3. select previous approved snapshot;
4. publish rollback manifest with incident reference;
5. monitor devices activating prior version;
6. do not delete bad evidence;
7. correct source/candidate and repeat full approval.

The rollback manifest must use a new, monotonic `manifest_version` even when it
points to an older snapshot. The TV downloads and re-verifies those bytes,
re-imports them transactionally and keeps the replaced snapshot as previous.
A decreased version or same-version identity change is treated as a downgrade/
conflict, not an authorized rollback.

## T009 device API startup

The local runtime has no embedded credential or trust default:

```text
go run ./cmd/api -config /private/path/api-config.json -listen 127.0.0.1:8080
```

The config must be `0600`; referenced public-key/snapshot files must be regular,
bounded, non-symlink files inside its directory. `public_base_url` must be the
external HTTPS origin. Configured pairing fixtures require
`pairing_fixture_mode: "ephemeral-test-only"`: they are process-local and are
not a production expiring/rate-limited issuer. TLS termination and production
secret injection are deployment responsibilities. Do not promote the Phase 1
public test key to production.

Android stores at most one pending and one rejected raw snapshot locally. A
pending checkpoint is resumed before any network request after process restart
only if its provisioning fingerprint still matches; re-pairing quarantines it.
Rejected support codes are bounded and credentials/full URLs never enter the
diagnostic record.

## T011 PostgreSQL pairing startup

Production pairing is explicit and cannot be combined with T009 fixtures or
static assignments. The private `0600` config contains environment-variable
names, not credential values:

```json
{
  "public_base_url": "https://api.example.invalid",
  "pairing_backend": "postgres",
  "registry_backend": "postgres",
  "database_url_env": "NAMAZ_DATABASE_URL",
  "pairing_rate_limit_key_env": "NAMAZ_PAIRING_RATE_KEY",
  "admin_idempotency_key_env": "NAMAZ_ADMIN_IDEMPOTENCY_KEY_CURRENT",
  "admin_compatibility_idempotency_key_envs": ["NAMAZ_ADMIN_IDEMPOTENCY_KEY_COMPAT"],
  "pairing_backend_timeout_seconds": 5,
  "pairing_rate_limits": {
    "window_seconds": 600,
    "source_attempts": 20,
    "device_attempts": 10,
    "code_attempts": 5
  },
  "trust_bundle_file": "production-trust-bundle.json",
  "test_trust_bundle_file": "test-trust-bundle.json",
  "staging_trust_bundle_file": "staging-trust-bundle.json",
  "minimum_trust_bundle_revision": 1,
  "publication_ledger_head_file": "publication-ledger-head.json",
  "snapshots": [{
    "snapshot_id": "ulyanovsk-second-cathedral-2026-v1",
    "file": "snapshot.json",
    "receipt_file": "publication-receipt.json",
    "sha256": "<snapshot-sha256>",
    "signing_key_id": "prod-schedule-2026-01"
  }],
  "assignments": []
}
```

Production snapshot registries require the lifecycle-aware public trust bundle;
`trusted_public_key_files` is retained only for synthetic test fixtures and
cannot authenticate production data. The bundle is public but integrity-
sensitive: deploy it through authenticated release configuration, monotonically
raise the pinned minimum revision, keep it next
to the runtime config as a bounded regular non-symlink file, and record its
SHA-256. Revision 2 and later additionally set
`previous_trust_bundle_file` to the directly preceding accepted bundle; startup
rejects revision gaps, rebinding, live-key removal and revocation resurrection.
Every production snapshot supplies its authenticated `receipt_file`; a
non-genesis receipt also supplies its direct `previous_receipt_file`. The API
also requires the public test and staging bundles and rejects reused key IDs or
public material across all three environments. The API
anchors the registry to `publication_ledger_head_file`: at least one configured
production artifact must carry that exact authenticated receipt hash, so a
standalone signed fork/genesis is not silently admitted. The API
process receives no signing private key. Normal publication,
rotation, revocation and rollback commands are in
[PUBLICATION_SIGNING_RUNBOOK.md](PUBLICATION_SIGNING_RUNBOOK.md).

`NAMAZ_PAIRING_RATE_KEY` is standard Base64 for exactly 32 random bytes. Zero
backend-timeout seconds selects the five-second default; an explicit value is
bounded to 30 seconds. Every pairing/authentication database call inherits this
request deadline, and rollback cleanup has its own bounded deadline.

The current admin idempotency key and every compatibility key are also standard
Base64 for exactly 32 random bytes. The current key derives new pairing-code
responses; compatibility keys reproduce an existing response without storing
its plaintext. Exact retries are guaranteed for 24 hours; expired evidence
returns `409` rather than creating a duplicate.

Use a two-phase rotation across all API replicas:

1. stage the future key in `admin_compatibility_idempotency_key_envs` everywhere
   while the old key remains current;
2. after all old replicas can read both keys, make the future key current and
   keep the old key in the compatibility list;
3. wait at least 24 hours after the last response created under the old key,
   then remove it from the compatibility list.

This makes mixed old/new replicas reproduce responses created by either key.
Environment-variable names must be unique; at most eight compatibility keys
are accepted to keep retry work bounded.

Remote TCP database endpoints must use server-authenticated TLS (for example,
`sslmode=verify-full` with the deployment CA/root configuration). Startup
rejects plaintext, `prefer`, and encryption without certificate verification
for a remote host. `sslmode=disable` is accepted only for an explicit Unix
socket, `localhost`, or loopback development endpoint. The repository does not
provide a production password or TLS terminator.

Run migrations as a separate short-lived deployment/init job. Its environment
contains the schema-owner DSN; the API deployment must not contain that variable
or credential:

```bash
go run ./cmd/migrate \
  -database-url-env NAMAZ_MIGRATION_DATABASE_URL \
  -target-version 7
```

The command applies embedded migrations under a transaction-scoped advisory
lock and exits. Only then start the API with `NAMAZ_DATABASE_URL` for the
least-privileged runtime role. API startup performs a read-only exact-v7 ledger
check and refuses missing, lower, gapped or future schemas. The runtime role must
not own schema/functions/triggers.

Minimum runtime privileges are deployment-managed: `SELECT` on mosque/device/
pairing/admin identity/membership/assignment/idempotency tables; required
`INSERT`/`UPDATE` on devices, pairing codes, rate buckets, assignments and the
latest-only `device_health` table;
bounded `DELETE` only on expired rate buckets; and `INSERT`-only on audit and
admin idempotency tables. It needs no DDL, trigger/function ownership,
`schema_migrations` mutation, admin actor/credential/membership writes, or
audit/idempotency update/delete/truncate. It requires `SELECT` on
`schema_migrations` solely for startup verification. When
`registry_backend` is enabled, it also requires `SELECT` on active registry
tables and `SELECT`/`INSERT` on append-only `registry_binding_requests`, but no
revision/active-pointer/audit/DDL write privilege. Verify the grants in
staging rather than granting broad schema ownership.

For a controlled v7-to-v6 rollback, stop registry binding-request writes,
export/verify the database and run `-target-version 6`. Migration `000007` down
removes pending review handoffs only; it does not change the active revision,
device assignments or signed snapshots. Resume only with a v6 binary/config
that does not expose the T039 endpoints.

For a controlled v6-to-v5 rollback, stop registry activation and API setup
reads, export/verify the database, and run `-target-version 5`; migration
`000006` down removes only registry tables and functions while preserving fleet
state. Remove `registry_backend` before starting a v5 binary/config. This is a
schema rollback, not recovery of registry rows; normal registry rollback uses
the active revision pointer instead. For a controlled T014-to-T013 rollback,
stop rollout writes, take a verified
database backup, and run `-target-version 3`; migration `000004` down removes
only cohort labels/indexes while preserving every per-device assignment,
pairing/admin row and latest health state. For T013-to-T012, stop heartbeat/write
traffic, take another verified backup, and run `-target-version 2`; migration
`000003` down
drops only latest health and `last_seen_at`. For T012-to-T011, then run target
`1`; migration `000002` down drops admin identities, idempotency evidence and
assignments while v1 mosque/device/pairing/rate/audit state remains. The command
rejects target `0`; complete schema removal exists only as a repository test
helper. The current v6 binary refuses to start on any lower target until v6 is
reapplied.

Run the restart/concurrency/migration suite in a disposable local PostgreSQL 18
container:

```bash
make test-postgres
```

The server deliberately ignores forwarded-address headers. Configure the
trusted reverse proxy/network so the direct peer address has useful rate-limit
cardinality; otherwise all clients safely share the stricter source bucket.

## T012 admin credential bootstrap

There is deliberately no first-admin HTTP endpoint. Provisioning is a
privileged database/secret-manager operation:

1. generate an opaque 256-bit random bearer token outside the API process;
2. write the plaintext once to the operator secret manager;
3. insert an `active` `admin_actor` and only the token's raw SHA-256 bytes into
   `admin_credentials`;
4. insert either one global `service_admin` membership with `mosque_id = NULL`,
   or explicit mosque-local memberships;
5. grant the runtime role only required DML/sequence privileges, then verify it
   cannot create actors/credentials, alter schema, disable audit triggers or
   read unrelated secret-manager material.

Never place the token or database URL in runtime JSON, command arguments,
shell history, logs or Git. Suspending the actor, revoking the credential, or
removing a membership takes effect on the next transaction recheck. The runtime
role reads authorization rows but cannot update them; write locks cover only
device/code state that the operation is permitted to mutate.

Admin writes require a fresh random `Idempotency-Key` containing no PII or
secret. An identical retry within 24 hours returns the original result. Reuse
with changed reason, expiry, device or assignment, or reuse after expiry,
returns `409` and cannot create another side effect.

## T013 heartbeat operation

Provisioned devices may post one strict health report after the existing daily
sync run. A failure is best effort and cannot fail an already completed sync or
affect local display. PostgreSQL retains only the latest row per device; it does
not require a heartbeat-history pruning job. Fleet freshness uses server
`last_seen_at`, never client `sent_at`.

Monitor aggregate 401, 400 and retryable 500 rates without logging bearer
headers or request bodies. Repeated 401 normally means revoked/stale
provisioning; repeated 400 means client/contract drift. Rate-limit authenticated
heartbeat traffic at the trusted ingress if a compromised device floods it,
while preserving normal daily reports. Never add SSID/BSSID, network address,
location, account identifiers, installed apps, logs or full URLs to this
endpoint.

## T014 canary rollout operation

Assign a stable cohort label only to explicitly selected devices. Confirm the
group contains at most 100 non-revoked rows and that the target snapshot already
exists in the API's verified registry. Record an incident/change reason and a
fresh idempotency key, then call the group assignment endpoint. The response is
the exact ordered list of durable per-device manifest versions; retain it as
rollout evidence.

Observe server last-seen, reported snapshot and sync state, but remember that
heartbeat fields are diagnostic claims. Promotion remains a separate explicit
operation. To roll back, submit the previous verified snapshot ID with a new
reason/idempotency key; verify every returned manifest version increased. A
`409 rollout_group_too_large` or any other failure means no member assignment
was changed. Physical activation evidence is still required for production.

## T015 device support bundle

An authorized fleet reader can request one `device-support-bundle/v1` JSON
document from the device support endpoint. The response is `no-store`, is not
persisted as a separate export/history, and contains only:

- mosque ID and IANA timezone;
- device lifecycle, app/model/OS, server last-seen and rollout label;
- current manifest version, snapshot ID/SHA-256/signing-key ID/minimum app version;
- optional latest reported/received times, reported snapshot, sync/coverage,
  clock/timezone flags and storage/memory/boot/kiosk buckets.

Treat `reported_at` and `reported_snapshot_id` as device claims; only
`generated_at`, `received_at` and `last_seen_at` are server-owned. Do not copy
bearer/admin tokens into tickets. The schema cannot contain pairing codes,
installation keys, capability lists, snapshot URLs, IP/MAC/SSID/BSSID, precise
location, accounts, installed apps, arbitrary maps, logs or raw source data.

## T037 registry activation

The generated T035 catalog and raw GeoNames dumps remain ignored and outside
Git. After reproducing the pinned catalog, verify the reviewed Ulyanovsk
binding and its existing approval/publication evidence offline:

```bash
go run ./cmd/registryctl validate \
  -catalog geodata/generated/russia-cities-2026-08-29.json \
  -bindings fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json \
  -artifacts fixtures/pilot/ulyanovsk-2026/registry-reference-artifacts.json \
  -artifact-root .
```

Activation uses a registry-writer DSN exposed only through a named environment
variable, never a literal CLI/config value:

```bash
go run ./cmd/registryctl apply \
  -catalog geodata/generated/russia-cities-2026-08-29.json \
  -bindings fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json \
  -artifacts fixtures/pilot/ulyanovsk-2026/registry-reference-artifacts.json \
  -artifact-root . \
  -database-url-env NAMAZTIME_REGISTRY_DATABASE_URL \
  -actor operator-stable-id \
  -reason "activate reviewed Ulyanovsk pilot registry"
```

Record the reported revision/catalog/content hashes and activation audit ID.
Do not rerun import with unreviewed catalog bytes. Registry rollback is an
explicit service/operator action that re-verifies the target revision; do not
use migration-down as a routine rollback. The signed USB-pilot snapshot remains
an independent immutable artifact and must retain raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

## Monitoring and SLO proposals

Pilot targets, to refine from measurements:

- 100% published snapshots pass schema/signature checks;
- zero silent source substitution;
- zero current-day schedule gaps on active pilot devices;
- 99% of online devices receive non-urgent snapshot within 24 hours;
- rollback manifest available within 30 minutes of confirmed publication incident;
- TV main screen launches from cache under three seconds on pilot hardware.

## Backups

Backend:

- PostgreSQL point-in-time capable backups;
- encrypted object storage for permitted raw artifacts and snapshots;
- separate signing-key backup/rotation policy;
- quarterly restore drill.

The executable local PostgreSQL drill and the production-safe procedure are in
[BACKUP_RESTORE_RUNBOOK.md](BACKUP_RESTORE_RUNBOOK.md). Run the local evidence:

```bash
make test-postgres-restore
```

The logical database archive contains sensitive tenant and credential-verifier
state, is never stored in Git, and is restored without applying archived owner/
ACL commands.
Reapply deployment-managed roles/grants after restoring into a new isolated
database. Database recovery is incomplete until referenced signed snapshots,
raw-artifact custody and signing-key recovery have been verified separately.

TV:

- active + previous snapshot locally;
- configuration re-download from server after re-pairing;
- no assumption that Android app backup is available or secure.

## Release checklist

- migrations backward-compatible or rollback planned;
- contract/schema compatibility tested;
- production trust-bundle hash deployed to API and canary TV;
- signing key ID is active and publication receipt/snapshot verify together;
- approver and signer identities are distinct;
- canary group selected;
- source coverage verified;
- physical pilot smoke test;
- store privacy/permission declarations reviewed;
- rollback artifact/version available;
- release notes list known limitations.
