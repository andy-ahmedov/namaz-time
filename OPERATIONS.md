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
- system time compared to signed server timestamp when online;
- monotonic countdown anomalies;
- large wall-clock jump;
- NTP/automatic-time setting visible in installation guide.

The app should recalculate display state after clock/timezone broadcasts. It must not require exact-alarm permission just to render a countdown.

## Boot and kiosk

### Best-effort installation

- boot receiver enabled only by operator setting;
- document OEM “auto start/energy optimization/last app” settings;
- keep screen on while active;
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
  "trusted_public_key_files": [],
  "snapshots": [],
  "assignments": []
}
```

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
  -target-version 2
```

The command applies embedded migrations under a transaction-scoped advisory
lock and exits. Only then start the API with `NAMAZ_DATABASE_URL` for the
least-privileged runtime role. API startup performs a read-only exact-v2 ledger
check and refuses missing, v1, gapped or future schemas. The runtime role must
not own schema/functions/triggers.

Minimum runtime privileges are deployment-managed: `SELECT` on mosque/device/
pairing/admin identity/membership/assignment/idempotency tables; required
`INSERT`/`UPDATE` on devices, pairing codes, rate buckets and assignments;
bounded `DELETE` only on expired rate buckets; and `INSERT`-only on audit and
admin idempotency tables. It needs no DDL, trigger/function ownership,
`schema_migrations` mutation, admin actor/credential/membership writes, or
audit/idempotency update/delete/truncate. It requires `SELECT` on
`schema_migrations` solely for startup verification. Verify the grants in
staging rather than granting broad schema ownership.

For a controlled T012-to-T011 binary rollback, stop T012 write traffic and take
a verified database backup, then run the short-lived migration command with
`-target-version 1` before deploying the T011 binary. Migration `000002` down
drops only T012 admin identities, idempotency evidence and assignments; v1
mosque/device/pairing/rate/audit state remains. The deployment command rejects
target `0`; complete schema removal exists only as a repository test helper. A
v2 binary will refuse to start after the targeted rollback until v2 is reapplied.

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
   cannot create actors/credentials, alter
   schema, disable audit triggers or read unrelated secret-manager material.

Never place the token or database URL in runtime JSON, command arguments,
shell history, logs or Git. Suspending the actor, revoking the credential, or
removing a membership takes effect on the next transaction recheck. The runtime
role reads authorization rows but cannot update them; write locks cover only
device/code state that the operation is permitted to mutate.

Admin writes require a fresh random `Idempotency-Key` containing no PII or
secret. An identical retry within 24 hours returns the original result. Reuse
with changed reason, expiry, device or assignment, or reuse after expiry,
returns `409` and cannot create another side effect.

## Device support bundle

Structured JSON/text only:

- app build;
- model/OS;
- timezone/clock flags;
- active/previous snapshot IDs and hashes;
- last sync status/timestamps;
- coverage;
- free storage/memory bucket;
- boot/kiosk state;
- last bounded error codes.

No auth tokens, SSID/BSSID, precise GPS, raw external source documents or user account secrets.

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

TV:

- active + previous snapshot locally;
- configuration re-download from server after re-pairing;
- no assumption that Android app backup is available or secure.

## Release checklist

- migrations backward-compatible or rollback planned;
- contract/schema compatibility tested;
- signing key ID correct;
- canary group selected;
- source coverage verified;
- physical pilot smoke test;
- store privacy/permission declarations reviewed;
- rollback artifact/version available;
- release notes list known limitations.
