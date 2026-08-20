# Fleet PostgreSQL backup and restore runbook

## Scope and evidence

This runbook covers the fleet PostgreSQL database: mosque/device identity,
credential verifiers, pairing/rate state, assignments, latest health,
idempotency and audit evidence. It does not back up signed snapshot files,
permitted raw source artifacts, runtime configuration, database roles/grants or
publication signing keys.

Evidence labels:

- `CONFIRMED_RUNTIME` — `make test-postgres-restore` completes against the
  disposable local PostgreSQL 18 container and its synthetic fixture.
- `UNKNOWN` — production backup cadence, retention, point-in-time recovery,
  RPO and RTO until deployment owners configure and measure them.
- `UNKNOWN` — production signing-key and immutable artifact recovery until
  D-013 and the production storage design are accepted.

## Local repeatable drill

Requirements: Docker, Go and the repository toolchain. Run:

```bash
make test-postgres-restore
```

The command creates isolated source, corrupt-target and restore databases in an
ephemeral PostgreSQL 18 container. It migrates the source to the exact current
schema, loads only the committed synthetic fixture, writes a custom archive,
records SHA-256 and byte count, rejects a truncated copy, and restores the intact
archive with fail-fast single-transaction semantics. The final Go check proves:

- exact migration-ledger compatibility;
- linked mosque/device/admin/pairing/assignment/latest-health state;
- device and admin credential verification from restored hashes;
- current assignment and health projection;
- audit and idempotency update/delete/truncate rejection.

The container and archive are removed on exit. A passing local drill does not
measure a production recovery time or prove production storage durability.

## Production backup procedure

1. Record deployment/release ID, PostgreSQL server/client major versions,
   migration target, start time and the immutable snapshot-artifact inventory.
2. Use a dedicated backup identity with only the required connection, schema
   usage and table read privileges. Do not use the API runtime or migration-owner
   credential in an interactive shell.
3. Write to access-controlled encrypted storage with restrictive process umask.
   The dump and restore commands must both carry `--no-owner --no-privileges`;
   for custom format, the restore flags are what prevent archived owner/ACL
   commands from being applied. Roles and grants are deployment configuration.
4. Record archive byte count and SHA-256, then bind them to an authenticated,
   immutable inventory/signature supplied by the deployment platform. SHA-256
   alone does not authenticate the archive.
5. Verify `pg_restore --list` can parse the stored archive. Monitor the backup
   job and retain its bounded stderr separately from the archive.
6. Apply the accepted retention and off-site policy. Never commit the archive,
   attach it to tickets or place it on a TV/device-accessible network.

Example shape, with credentials supplied through a protected libpq service and
passfile rather than command-line arguments:

```bash
umask 077
PGSERVICE=namaz_backup pg_dump \
  --dbname=service=namaz_backup \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges \
  --file=/secure/encrypted-volume/namaz-fleet.dump
sha256sum /secure/encrypted-volume/namaz-fleet.dump
pg_restore --list /secure/encrypted-volume/namaz-fleet.dump >/dev/null
```

The archive includes hashed credentials and tenant/assignment/audit data and is
therefore sensitive even without plaintext bearer tokens.

## Restore procedure

1. Declare an incident/change window, stop fleet writers for a production
   cutover, and select a backup by authenticated inventory plus required
   recovery point. Preserve the current database for rollback.
2. Provision a new isolated PostgreSQL database on the tested compatible major
   version. Never restore over the live database and never expose a restored
   production copy to developer or TV networks.
3. Verify the authenticated inventory, SHA-256, byte count and
   `pg_restore --list` before restore.
4. Restore with `--exit-on-error --single-transaction --no-owner
   --no-privileges`. Failure leaves the new target without a partially committed
   archive.
5. Reapply schema ownership, runtime/backup roles and least-privilege grants
   from deployment-controlled configuration. The long-running API receives no
   migration-owner or backup credential.
6. Run exact migration-ledger verification through the current runtime binary.
   Check linked mosque/device/assignment/latest-health records and explicitly
   prove audit/idempotency update, delete and truncate are rejected.
7. Verify that every current assignment references an available, immutable,
   hash/signature-valid snapshot and that the trust configuration matches the
   recovered release. Recover raw sources and publication signing keys through
   their separate procedures.
8. Rotate credentials if archive custody may have been exposed. Start one
   controlled API instance, perform mosque-isolation/read checks, then resume
   writers and replicas. The TV fleet continues using local last-known-good data
   during backend recovery.
9. Record finish time, actual recovery point/time, commands/tool versions,
   verification results, exceptions and cleanup. Destroy or reclassify the old
   and validation databases according to the incident plan.

## Drill evidence template

```text
Date/time UTC:
Operator/reviewer:
Environment and PostgreSQL version:
Release commit and migration version:
Backup inventory ID:
Archive SHA-256 and bytes:
Recovery point:
Restore target (new/isolated):
Exact-schema verification:
Device/admin authentication verification:
Assignment/latest-health verification:
Append-only trigger verification:
Snapshot/source/signing-key recovery verification:
Start/finish and measured RPO/RTO:
Exceptions and follow-up owner/date:
Cleanup confirmation:
```
