#!/usr/bin/env bash
set -Eeuo pipefail

container_name="namaz-time-postgres-test-$$"
container_id=""

cleanup() {
  if [[ -n "${container_id}" ]]; then
    docker rm -f "${container_id}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

started_container_id="$(docker run --rm --detach \
  --name "${container_name}" \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=namaz_time_test \
  --env POSTGRES_USER=namaz_time_test \
  --env POSTGRES_PASSWORD=local-integration-only \
  postgres@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15)"
if [[ ! "${started_container_id}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Docker did not return a valid PostgreSQL integration container ID" >&2
  exit 1
fi
container_id="${started_container_id}"

database_ready() {
  docker exec "${container_id}" psql \
    --username namaz_time_test \
    --dbname namaz_time_test \
    --no-psqlrc \
    --tuples-only \
    --command 'SELECT 1' >/dev/null 2>&1
}

for _ in $(seq 1 60); do
  if database_ready; then
    break
  fi
  sleep 0.25
done

if ! database_ready; then
  echo "PostgreSQL integration container did not become ready" >&2
  exit 1
fi

mapped_port="$(docker port "${container_id}" 5432/tcp | sed -n 's/.*://p')"
if [[ ! "${mapped_port}" =~ ^[0-9]+$ ]]; then
  echo "Could not resolve PostgreSQL integration port" >&2
  exit 1
fi

export NAMAZ_TEST_POSTGRES_URL="postgres://namaz_time_test:local-integration-only@127.0.0.1:${mapped_port}/namaz_time_test?sslmode=disable"
go test -tags=integration ./internal/devices -count=1
go test -tags=integration ./internal/registry -count=1
go test -tags=integration ./cmd/api -count=1
