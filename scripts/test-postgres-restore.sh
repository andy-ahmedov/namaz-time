#!/usr/bin/env bash
set -Eeuo pipefail

drill_container="namaz-time-postgres-restore-$$"
drill_container_id=""
drill_source_database="namaz_time_backup_source"
drill_restore_database="namaz_time_backup_restore"
drill_corrupt_database="namaz_time_backup_corrupt"
drill_runtime_role="namaz_restore_runtime"
drill_backup_path="/tmp/namaz-time-fleet.dump"
drill_corrupt_path="/tmp/namaz-time-fleet-corrupt.dump"
drill_script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
drill_fixture="${drill_script_directory}/fixtures/postgres-backup-restore-seed.sql"

cleanup() {
  if [[ -n "${drill_container_id}" ]]; then
    docker rm -f "${drill_container_id}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

check_dependencies() {
  local drill_command
  for drill_command in docker go sed stat; do
    if ! command -v "${drill_command}" >/dev/null 2>&1; then
      echo "Required restore-drill command is unavailable: ${drill_command}" >&2
      return 1
    fi
  done
}

if (( $# != 0 )); then
  echo "test-postgres-restore.sh does not accept arguments" >&2
  exit 2
fi
check_dependencies
if [[ ! -f "${drill_fixture}" || ! -r "${drill_fixture}" ]]; then
  echo "PostgreSQL restore-drill fixture is not readable: ${drill_fixture}" >&2
  exit 1
fi

drill_started_container_id="$(docker run --rm --detach \
  --name "${drill_container}" \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=postgres \
  --env POSTGRES_USER=namaz_time_test \
  --env POSTGRES_PASSWORD=local-integration-only \
  postgres@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15)"
if [[ ! "${drill_started_container_id}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Docker did not return a valid restore-drill container ID" >&2
  exit 1
fi
drill_container_id="${drill_started_container_id}"

database_ready() {
  docker exec "${drill_container_id}" psql \
    --host 127.0.0.1 \
    --username namaz_time_test \
    --dbname postgres \
    --no-psqlrc \
    --tuples-only \
    --command 'SELECT 1' >/dev/null 2>&1
}

for ((drill_attempt = 1; drill_attempt <= 60; drill_attempt++)); do
  if database_ready; then
    break
  fi
  sleep 0.25
done

if ! database_ready; then
  echo "PostgreSQL restore-drill container did not become ready" >&2
  docker inspect --format 'container-state={{json .State}}' "${drill_container_id}" >&2 || true
  docker logs --tail 100 "${drill_container_id}" >&2 || true
  exit 1
fi

drill_mapped_port="$(docker port "${drill_container_id}" 5432/tcp | sed -n 's/.*://p')"
if [[ ! "${drill_mapped_port}" =~ ^[0-9]+$ ]]; then
  echo "Could not resolve PostgreSQL restore-drill port" >&2
  exit 1
fi

docker exec "${drill_container_id}" createdb \
  --username namaz_time_test "${drill_source_database}"

export NAMAZ_RESTORE_DRILL_MIGRATION_URL="postgres://namaz_time_test:local-integration-only@127.0.0.1:${drill_mapped_port}/${drill_source_database}?sslmode=disable"
go run ./cmd/migrate \
  -database-url-env NAMAZ_RESTORE_DRILL_MIGRATION_URL \
  -target-version 8

docker cp "${drill_fixture}" "${drill_container_id}:/tmp/namaz-time-restore-seed.sql" >/dev/null
docker exec "${drill_container_id}" psql \
  --username namaz_time_test \
  --dbname "${drill_source_database}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --file /tmp/namaz-time-restore-seed.sql >/dev/null

docker exec "${drill_container_id}" pg_dump \
  --username namaz_time_test \
  --dbname "${drill_source_database}" \
  --format custom \
  --compress 9 \
  --no-owner \
  --no-privileges \
  --file "${drill_backup_path}"

drill_backup_bytes="$(docker exec "${drill_container_id}" stat -c '%s' "${drill_backup_path}")"
drill_backup_sha256="$(docker exec "${drill_container_id}" sha256sum "${drill_backup_path}" | sed -n 's/ .*//p')"
if [[ ! "${drill_backup_bytes}" =~ ^[0-9]+$ ]] || (( drill_backup_bytes < 1024 )); then
  echo "PostgreSQL restore-drill archive is unexpectedly small" >&2
  exit 1
fi
if [[ ! "${drill_backup_sha256}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Could not calculate PostgreSQL restore-drill archive SHA-256" >&2
  exit 1
fi
docker exec "${drill_container_id}" pg_restore --list "${drill_backup_path}" >/dev/null

docker exec "${drill_container_id}" createdb \
  --username namaz_time_test "${drill_corrupt_database}"
docker exec "${drill_container_id}" dd \
  if="${drill_backup_path}" \
  of="${drill_corrupt_path}" \
  bs=512 \
  count=1 >/dev/null 2>&1
if docker exec "${drill_container_id}" pg_restore \
  --username namaz_time_test \
  --dbname "${drill_corrupt_database}" \
  --exit-on-error \
  --single-transaction \
  --no-owner \
  --no-privileges \
  "${drill_corrupt_path}" >/dev/null 2>&1; then
  echo "PostgreSQL restore drill accepted a truncated archive" >&2
  exit 1
fi
drill_corrupt_table_count="$(docker exec "${drill_container_id}" psql \
  --username namaz_time_test \
  --dbname "${drill_corrupt_database}" \
  --no-psqlrc \
  --tuples-only \
  --no-align \
  --command "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")"
if [[ "${drill_corrupt_table_count}" != "0" ]]; then
  echo "Failed PostgreSQL restore left partially committed public tables" >&2
  exit 1
fi

docker exec "${drill_container_id}" createdb \
  --username namaz_time_test "${drill_restore_database}"
docker exec "${drill_container_id}" pg_restore \
  --username namaz_time_test \
  --dbname "${drill_restore_database}" \
  --exit-on-error \
  --single-transaction \
  --no-owner \
  --no-privileges \
  "${drill_backup_path}"

docker exec "${drill_container_id}" psql \
  --username namaz_time_test \
  --dbname postgres \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "CREATE ROLE ${drill_runtime_role} LOGIN PASSWORD 'restore-runtime-only'" \
  --command "GRANT CONNECT ON DATABASE ${drill_restore_database} TO ${drill_runtime_role}" >/dev/null
docker exec "${drill_container_id}" psql \
  --username namaz_time_test \
  --dbname "${drill_restore_database}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "GRANT USAGE ON SCHEMA public TO ${drill_runtime_role}" \
  --command "GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${drill_runtime_role}" >/dev/null

export NAMAZ_TEST_POSTGRES_RESTORE_URL="postgres://namaz_time_test:local-integration-only@127.0.0.1:${drill_mapped_port}/${drill_restore_database}?sslmode=disable"
export NAMAZ_TEST_POSTGRES_RESTORE_RUNTIME_URL="postgres://${drill_runtime_role}:restore-runtime-only@127.0.0.1:${drill_mapped_port}/${drill_restore_database}?sslmode=disable"
go test -tags=integration ./internal/devices \
  -run '^TestPostgresBackupRestorePreservesCurrentFleetState$' \
  -count=1

echo "postgres-restore-drill: PASS sha256=${drill_backup_sha256} bytes=${drill_backup_bytes}"
