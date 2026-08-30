# Ulyanovsk persisted city/source vertical slice

Date: 2026-08-30

Status: T037 implemented locally; no nationwide prayer-source rollout

## Result

`CONFIRMED_RUNTIME`: the control plane now resolves the canonical GeoNames
Ulyanovsk city through the active PostgreSQL registry revision to the existing
Second Cathedral Mosque 2026 timetable and the exact signed offline-pilot
snapshot:

```text
search "Ульяновск" or explicit alias "Ulyanovsk"
  → city-4adcfc15932f3850d5dd5dbaa17e3a4c / geonames:479123
  → ru-uly / RU-ULY / Europe/Ulyanovsk
  → scope-ulyanovsk-city
  → rdum-ulyanovsk-oblast [CONFIRMED_PUBLIC]
    + rdumul-attributed-publisher-unconfirmed [UNKNOWN]
  → effective-ulyanovsk-2026-v1
  → policy-ulyanovsk-second-cathedral-2026
  → timetable-ulyanovsk-second-cathedral-2026
  → second-cathedral-mosque-ulyanovsk
  → ulyanovsk-second-cathedral-2026-pilot-local-v2
```

The former hard-coded executable `NewPilotRegistry` seed has been removed.
Geographic catalog data still carries no prayer authority. The one executable
policy remains explicitly mosque-bound and is not generalized to Ulyanovsk
Oblast or another mosque.

## Reviewed import inputs

`fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json` is a strict,
schema-v1 binding document. It targets the exact T035 catalog:

- catalog revision:
  `catalog-geonames-ru-2026-08-29-58af26706f194677`;
- catalog content SHA-256:
  `58af26706f194677d5d703c512fd9290d9b97657b26713c4c52f9685593f29f2`;
- composed executable registry SHA-256:
  `0114c41e6e7d58fc5886b5a4363567348a0c5bb56835e5d9d830dc0b4fef8e5f`.

It stores references, not prayer rows. The annual baseline and bounded August
override remain distinct sources. The annual publisher is
`CONFIRMED_PUBLIC`; the August image's legal publisher remains `UNKNOWN`.
Composition rejects another catalog revision/hash, unknown fields, duplicate
JSON members, non-UTC revision time, invalid foreign keys, ambiguous policy,
and invalid IANA timezone.

`registry-reference-artifacts.json` pins the exact existing policy, approval,
trust, snapshot and publication files by SHA-256. `ArtifactReferenceVerifier`
then verifies their contents rather than trusting those hashes or IDs alone:

- canonical mosque-policy hash and signed approval receipt;
- approval trust chain and mosque identity;
- production publication trust transition;
- test/staging/production key separation;
- publication admission receipt and Ed25519 snapshot signature;
- snapshot ID, mosque, timezone, effective range, signing key and raw bytes.

Private signing/approval keys are not inputs and are not stored in Git or the
registry.

## Signed-pilot preservation

`CONFIRMED_RUNTIME`: before and after activation, successor activation and
registry rollback, the packaged pilot file remains byte-identical:

- snapshot ID: `ulyanovsk-second-cathedral-2026-pilot-local-v2`;
- raw SHA-256:
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`;
- signing key ID: `pilot-local-schedule-2026-02`;
- mosque ID: `second-cathedral-mosque-ulyanovsk`;
- timezone: `Europe/Ulyanovsk`.

The registry does not rewrite, regenerate, or re-sign the snapshot. Rollback
re-verifies retained references and changes only the active registry pointer.
The Android pilot asset, Room schema, USB delivery, local signature check and
last-known-good activation path are unchanged.

## Operator command and API

Generate the ignored full catalog using the pinned T035 importer, then run the
offline preflight:

```bash
go run ./cmd/registryctl validate \
  -catalog geodata/generated/russia-cities-2026-08-29.json \
  -bindings fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json \
  -artifacts fixtures/pilot/ulyanovsk-2026/registry-reference-artifacts.json \
  -artifact-root .
```

`apply` additionally requires `-actor`, `-reason`, and a valid environment
variable name supplied with `-database-url-env`; a literal database URL is not
accepted. It stages an immutable revision and re-runs executable-reference
verification before activation.

The API enables persisted registry reads only with
`registry_backend: "postgres"` alongside the existing PostgreSQL admin
backend. Both endpoints require an authenticated principal authorized to read
the mosque path and send `Cache-Control: no-store`:

- `GET /v1/admin/mosques/{mosqueId}/setup/cities?q=Ульяновск` returns all
  exact canonical/alias candidates with subject, timezone and geographic
  provenance;
- `GET /v1/admin/mosques/{mosqueId}/setup/prayer-policy?city_id=…&date=…`
  resolves one explicitly selected canonical ID for that mosque and local
  Gregorian date.
- `GET /v1/admin/mosques/{mosqueId}/setup/schedule-choices?city_id=…&date=…`
  projects the active Ulyanovsk binding as exactly one selectable/executable
  choice referencing the same policy, timetable and signed snapshot. Supplying
  a staged `revision_id` can show multiple eligible choices without selecting
  or activating one.

Duplicate names remain separate candidates. Zero/multiple matches never pass
the repository's unique-city operation. Unknown, unavailable or same-tier
ambiguous prayer policies return a fail-closed error and cannot change a
device assignment.

## Verification scope and limits

`CONFIRMED_RUNTIME` covers:

- strict composition against the exact canonical catalog identity;
- real approval/publication evidence and tamper rejection;
- real PostgreSQL migration, immutable stage/activation and rollback;
- authenticated HTTP search and explicit policy resolution;
- duplicate and unknown city non-selection;
- exact snapshot bytes/hash/signature identity before and after rollback;
- existing Android pilot unit authentication/import behavior.

`UNKNOWN`: this local test does not constitute a production deployment,
production backup/RPO/RTO proof, or physical-TV acceptance. No second region
is executable; T038 remains deferred pending external source scope/terms and
approval.
