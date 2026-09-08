# Public local setup bundle, version 1

`PROPOSAL` — the local exporter/Android contract for T049. A bundle is an
offline delivery of admitted registry choices, not source qualification,
external endorsement, a new signing authority, or device provisioning.
Implementation and executed checks are recorded separately in the task handoff.

## Admission and trust

`registryctl export-local-setup` reads SHA-256-pinned catalog, policy bindings,
and reference-artifact manifest files. Reference members retain their own byte
pins. It composes the **complete** canonical catalog with the bindings, then
runs the real registry `PersistentService.Stage` and `Activate` admission with
the real `ArtifactReferenceVerifier`. Its private, one-shot store exists only
for this local admission; no PostgreSQL or server state is modified.

Public policies require the complete schema-2 qualification and its exact
catalog, authority, scope, timezone, freshness, and signed snapshot bindings.
An enum or a valid signature alone is insufficient. All policy references must
be admitted; invalid references fail the whole export. Stale qualification is
not exported as current. A public timetable does not create mosque iqamah.

Version 1 uses only this existing public trust set from
`fixtures/pilot/ulyanovsk-2026/pilot-local/`:

| Bundle file | Existing source | Exact raw SHA-256 |
| --- | --- | --- |
| `trust/production.json` | `production-trust-bundle.json` | `2fc9b7a34cbba4bf57ba5242aec6b782d41877ff6863006e83a3d40bc34eb085` |
| `trust/previous-production.json` | `production-trust-bundle-revision-2.json` | `55d58bef5426876b8f47be721409d211644cf5bcbad24ddfdc7903ee5549c4a7` |
| `trust/test.json` | `test-trust-bundle.json` | `82c7e7e943f5796ab689265a2d24862fc1f869f5ea574f7a940d2adaf74c1f77` |
| `trust/staging.json` | `staging-trust-bundle.json` | `db4d936d6894d6ff60bfa422c12a886bfc0f553b47a7e1201fa9e2560b25d0bd` |

Production revision is exactly 3. The exporter and Android compare these
independently anchored hashes, validate the production transition and
test/staging separation, and authenticate snapshot bytes. Manifest-provided
hashes cannot replace these anchors. No keys are generated, exported, or
changed. Test-only alternate anchors are inaccessible through the public
export API and CLI.

The retained Ulyanovsk schema-1 artifact may remain a separate legacy choice
only for its exact canonical city, original single approved mosque, and signed
display context. Its bytes, approval, composition, and mosque rules are not
rewritten or promoted to citywide public qualification. Its label includes the
original mosque name. Region-scoped legacy sources are not local public choices.
Version 1 supports only this explicitly pinned retained legacy pilot binding;
it does not infer other legacy mosque/city bindings from matching names.
The retained Ulyanovsk locality contains its street address. The exception is
bound to its exact existing snapshot SHA-256, mosque ID, and canonical
`geonames:479123` city ID; no address-prefix or nearest-city matching is used.

## Closed directory and manifest

The output directory must not exist. Export prepares a private sibling directory,
checks all files, synchronizes them, then atomically renames without replacement.
A failure exposes no partial destination and never overwrites an existing one.
The Linux exporter requires `renameat2(RENAME_NOREPLACE)`; unsupported platforms
fail closed. Outputs remain outside Git and enter Android only as generated
debug assets, never as committed raw source datasets.

The complete inventory is `manifest.json`, `catalog.sqlite`, `choices.json`,
the four trust files above, and `snapshots/<raw-sha256>.json` for each referenced
unchanged signed artifact. No symlinks, traversal, absolute member paths,
unknown files, duplicate members, or undeclared artifacts are accepted.
Input publication/approval receipts are mandatory for admission where applicable
but are not copied to the TV; qualifications remain in `choices.json` and the
signed public snapshots.

`manifest.json` has exactly these members:

- `schema_version`: `namaztime-local-setup-bundle/v1`.
- `bundle_id`: `local-setup-` plus the first 32 hexadecimal hash characters.
- `manifest_sha256`: SHA-256 of canonical JSON after omitting exactly the two
  root members `bundle_id` and `manifest_sha256`. Canonicalization is the
  repository's sorted-key JSON contract, **not** generic RFC 8785; numbers here
  are integers. Both omitted members are present in the stored document.
- `created_at`: canonical UTC RFC 3339 export/admission time.
- `registry_revision`: the complete admitted `registry.RevisionRecord`.
- `registry_state`: `active` (local admission only, not remote activation).
- `admission`: `kind` = `persistent_service_verified_local`, `verified_at`
  equal to `created_at`, `actor_id`, and `reason` from the export request.
- `catalog`: `revision_id`, `content_sha256`, `region_count`, `city_count`,
  `alias_count`, `search_name_count`, `license`, `license_url`, `attribution`.
- `minimum_trust_revision`: 3.
- `files`: path-sorted objects with exactly `path`, `byte_length`, `sha256`.
  This inventory excludes `manifest.json` to avoid recursive byte hashing.

Bounds: manifest 512 KiB; SQLite 128 MiB; choices 16 MiB; each trust file
256 KiB; each snapshot 5 MiB; at most 1,024 snapshots; total directory 256 MiB.
An exceeded bound is an error, never truncation. JSON rejects duplicate and
unknown fields. A bundle hash proves internal identity, not publisher authority.

## SQLite catalog

`catalog.sqlite` is immutable UTF-8 SQLite with `user_version=1`,
`application_id=0x4e545342` (`NTSB`), 4,096-byte pages, and no WAL or journal
sidecars. No virtual tables, triggers, or views are present. Tables are:

- `metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)`.
- `regions(id TEXT PRIMARY KEY, federal_subject_code TEXT NOT NULL,
  name TEXT NOT NULL, country_code TEXT NOT NULL)`.
- `cities(id TEXT PRIMARY KEY, name TEXT NOT NULL, region_id TEXT NOT NULL,
  settlement_type TEXT NOT NULL, timezone TEXT NOT NULL, latitude REAL NOT NULL,
  longitude REAL NOT NULL, geographic_source_id TEXT NOT NULL,
  geographic_revision TEXT NOT NULL, geographic_license TEXT NOT NULL,
  aliases_json TEXT NOT NULL, country_code TEXT NOT NULL, population INTEGER
  NOT NULL, geographic_source TEXT NOT NULL, source_modified_date TEXT NOT NULL,
  fallback_policy_id TEXT NOT NULL)`.
- `search_names(normalized_name TEXT NOT NULL, city_id TEXT NOT NULL,
  PRIMARY KEY(normalized_name, city_id))`.

Tables use `WITHOUT ROWID`; city and search-name foreign keys bind exact parent
IDs. `aliases_json` retains the original ordered JSON array, including every
catalog alias. No fallback policy is introduced. Index
`cities_region_name(region_id,name,id)` supports city/region display.

Metadata keys are exactly `schema_version` = `namaztime-local-city-index/v1`,
`catalog_revision`, `content_sha256`, `normalizer` =
`go-simple-lower-unicode-space/v1`, `region_count`, `city_count`, `alias_count`,
and `search_name_count`; counts are canonical decimal strings.

All canonical cities and aliases are included, including unavailable and
same-named places. For the 2026-09-08 canonical catalog the required counts are
83 regions, 166,559 cities, and 204,641 aliases. These are catalog input facts,
not hard-coded limits or a reason to discard a future catalog revision.
`search_name_count` counts unique normalized `(name, city_id)` pairs, independently
of the lossless alias count.

Search version 1 is exact canonical-name/alias lookup. Normalize with Go
`strings.ToLower(strings.Join(strings.Fields(value), " "))`: collapse Unicode
White_Space runs to ASCII space, trim, then simple Unicode lowercase by code
point. Do not add accent removal, `ё`/`е` substitution, transliteration,
NFC/NFKC, locale-sensitive casing, or full case-fold expansion. Unicode whitespace
is U+0009–000D, 0020, 0085, 00A0, 1680, 2000–200A, 2028, 2029, 202F, 205F, 3000.
Return every distinct matching city sorted by federal-subject code, city name,
then city ID, all binary. A blank query returns no matches. There is no prefix
search or population-based disambiguation in v1.

The Android implementation opens a hash-checked app-private copy read-only,
without decoding the 92 MiB catalog JSON or applying Room migrations to it.

## Choices and date intervals

`choices.json` contains exactly:

- `schema_version`: `namaztime-local-setup-choices/v1`.
- `registry_revision_id`: the admitted revision ID.
- `policies`: policy-ID-sorted records described below.
- `bindings`: records sorted by `city_id`, `choice_id`, then `effective.from`.

Each policy record has `policy_id`, `authority_label`, `policy` (the full domain
PrayerPolicy), `scope`, `authorities` (ID-sorted), `source`, optional full
`qualification`, `timetable`, `source_overrides`, and `snapshot`. Snapshot has
exactly `snapshot_id`, `path`, `sha256`, `byte_length`, and `display_context`
(the signed domain Mosque record, including the non-mosque public context for
qualified public sources). Public records have no human approval branch.
The full public policy may contain `mosque_ids: null` or `mosque_ids: []`, never
a nonempty list. This preserves existing registry clone/fingerprint semantics;
the legacy branch retains its original singleton mosque ID.

Each binding has exactly `city_id`, `choice_id`, `policy_id`, `display_label`,
`tier`, and `effective` (`from`, `to`, inclusive local dates). Choice ID is the
registry's stable city/policy choice ID. Multiple disjoint ranges for the same
choice are permitted; duplicate or overlapping ranges are not.

The exporter runs the existing `Registry.Assess` then
`ProjectCityScheduleChoices` at every relevant eligibility boundary, with the
exact public or retained legacy context. It retains executable choices and
merges only adjacent identical bindings. Scope, source freshness, policy/table
coverage, and same-authority specificity therefore come from the Go registry,
not a separate Android religious resolver. Independent authorities remain
independent choices. No top-N, capital, neighboring-city, generic calculation,
or insertion-order fallback is permitted.

At selection time Android uses the selected city's IANA timezone and intersects
the local date with these explicit binding intervals and signed snapshot
coverage/freshness. It verifies the selected signed bytes before atomic local
activation. An expired/missing interval is unavailable; it never silently
selects another source. Failed import/selection leaves the last-known-good
snapshot active. Public regional onset does not create or overwrite local
iqamah/Jumu'ah rules.

## Build and operator boundary

The CLI accepts `-catalog`, `-catalog-sha256`, `-bindings`, `-bindings-sha256`,
`-artifacts`, `-artifacts-sha256`, `-artifact-root`, `-output`, `-actor`, `-reason`,
and optional `-sqlite3` executable path. All three input byte pins are required;
the artifact manifest is the existing
`namaztime-registry-reference-artifacts/v1` contract. Public-only manifests may
have an empty approvals array. Unused snapshot or approval references are
rejected, not silently copied into the bundle.

Android debug builds receive `-PnamaztimeLocalSetupBundle=/absolute/output/path`
and copy only the validated inventory to generated assets under `public-setup/`.
No configured bundle means `setup_local_not_configured`, not demo data. The
embedded APK/build boundary is trusted; an arbitrary user-supplied directory
is not a new public trust source. Import cannot contact authority websites,
sign snapshots, create credentials, or provision a device.

## Executed exporter checks (2026-09-08)

The synthetic acceptance fixture goes through `qualification.Create`, protected
publication, the concrete artifact verifier, and real registry admission. It
does not substitute a verifier or trust a status enum. It covers independent
regional authorities and a one-day, more-specific table from one of them.
Separate tests retain the exact signed Ulyanovsk pilot and reject near-match
city, GeoNames, mosque, address, and raw-hash bindings.

Executed successfully:

- `go test ./internal/setupbundle ./cmd/registryctl -count=1`.
- `go test -race ./internal/setupbundle ./cmd/registryctl -count=1`.
- `go vet ./internal/setupbundle ./cmd/registryctl`.
- `make docs-check`; changed-file `git diff --check`.
- Opt-in synthetic fixture generation and the full actual catalog roundtrip
  below. Default tests skip these two explicitly opt-in outputs.

The full 2026-09-08 catalog was rebuilt twice with SQLite CLI 3.50.6. Each
68,870,144-byte index had SHA-256
`b55adc22491aa0ff6e4c4064d49960d07a5e3bcf9c0c6d78cf999ede47ca3042`.
Both runs compared all 83 regions, 166,559 complete city records, 204,641 aliases,
and 371,200 normalized name/city pairs after reading them back through SQLite.
Float64 coordinates use 17-digit readback for exact comparison. Byte
reproducibility is for a fixed SQLite engine/toolchain; an engine upgrade may
change its file header and therefore requires a new checked output hash.

To reproduce the full catalog check, set `NAMAZTIME_LOCAL_SETUP_CATALOG` to the
retained canonical JSON and `NAMAZTIME_LOCAL_SETUP_CATALOG_SHA256` to its known
raw byte pin, then run
`go test ./internal/setupbundle -run '^TestCompleteActualCanonicalCatalogSQLiteRoundtripOptIn$' -count=1 -v`.
To generate only a synthetic interop fixture, set
`NAMAZTIME_LOCAL_SETUP_FIXTURE_OUTPUT` to an existing empty outside-Git parent
and run
`go test ./internal/setupbundle -run '^TestExportSyntheticAndroidFixtureOptIn$' -count=1 -v`.
The latter writes test-only public anchor metadata beside `bundle/`, never a
private key. Production export rejects these alternate synthetic anchors.

The first real local export admitted one qualified KBR regional table for all
202 canonical KBR localities plus the exact retained Ulyanovsk choice: two
policies and 203 city bindings, with the complete national geographic index.
Its immutable output and operation/pin records remain outside Git. This check
does not imply nationwide timetable coverage, remote activation, or a device
installation. Unbound places remain present and unavailable.
