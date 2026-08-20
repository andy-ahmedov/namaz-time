#!/usr/bin/env bash
set -euo pipefail

container_name="namaz-time-postgres-test-$$"

cleanup() {
  docker rm -f "${container_name}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker run --rm --detach \
  --name "${container_name}" \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=namaz_time_test \
  --env POSTGRES_USER=namaz_time_test \
  --env POSTGRES_PASSWORD=local-integration-only \
  postgres:18-alpine >/dev/null

for _ in $(seq 1 60); do
  if docker exec "${container_name}" pg_isready --username namaz_time_test --dbname namaz_time_test >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

if ! docker exec "${container_name}" pg_isready --username namaz_time_test --dbname namaz_time_test >/dev/null 2>&1; then
  echo "PostgreSQL integration container did not become ready" >&2
  exit 1
fi

mapped_port="$(docker port "${container_name}" 5432/tcp | sed -n 's/.*://p')"
if [[ ! "${mapped_port}" =~ ^[0-9]+$ ]]; then
  echo "Could not resolve PostgreSQL integration port" >&2
  exit 1
fi

export NAMAZ_TEST_POSTGRES_URL="postgres://namaz_time_test:local-integration-only@127.0.0.1:${mapped_port}/namaz_time_test?sslmode=disable"
go test -tags=integration ./internal/devices -count=1
go test -tags=integration ./cmd/api -count=1
