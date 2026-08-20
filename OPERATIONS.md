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

Remote TCP database endpoints must use server-authenticated TLS (for example,
`sslmode=verify-full` with the deployment CA/root configuration). Startup
rejects plaintext, `prefer`, and encryption without certificate verification
for a remote host. `sslmode=disable` is accepted only for an explicit Unix
socket, `localhost`, or loopback development endpoint. The repository does not
provide a production password or TLS terminator.
Startup pings PostgreSQL and applies embedded migrations under a transaction-
scoped advisory lock. If any step fails, the API does not start.

Run the restart/concurrency/migration suite in a disposable local PostgreSQL 18
container:

```bash
make test-postgres
```

The server deliberately ignores forwarded-address headers. Configure the
trusted reverse proxy/network so the direct peer address has useful rate-limit
cardinality; otherwise all clients safely share the stricter source bucket.

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
