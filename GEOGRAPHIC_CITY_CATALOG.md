# Canonical Russia city catalog

Date: 2026-08-30
Status: T035 implementation complete locally; catalog geography is not prayer policy

## Decision

`CONFIRMED_PUBLIC`: GeoNames publishes daily/country gazetteer extracts under
Creative Commons Attribution 4.0 and explicitly permits commercial use with
attribution. Its main record includes stable `geonameid`, coordinates, feature
code, population, IANA timezone and modification date; a separate export
provides language-tagged preferred/alternate names and daily update/delete
feeds.

`PROPOSAL`: NamazTime uses the pinned GeoNames RU country and alternate-name
exports for its canonical Russia city catalog. Required attribution is:

> GeoNames (https://www.geonames.org/), CC BY 4.0

This is a product data-source decision, not legal advice. Any future license or
distribution-model change requires review before a new catalog revision is
activated.

Official references:

- GeoNames export, terms and update model: https://www.geonames.org/export/
- GeoNames dump readme and country files: https://download.geonames.org/export/dump/
- CC BY 4.0 license: https://creativecommons.org/licenses/by/4.0/
- OpenStreetMap license considered as an alternative: https://www.openstreetmap.org/copyright
- ODbL 1.0 legal text: https://opendatacommons.org/licenses/odbl/1-0/

## Source comparison

| Criterion | GeoNames | OpenStreetMap | Decision impact |
|---|---|---|---|
| Data license | CC BY 4.0 attribution | ODbL 1.0 attribution/share-alike for derivative databases | GeoNames is simpler for a proprietary product and retained database exports |
| Stable identity | Numeric `geonameid` | Element type + ID; settlement representation may change between node/way/relation | GeoNames gives a direct rename-stable source key |
| RU/aliases | Language-tagged alternate-name dump, preferred/historic flags | Rich `name:*` tags where mapped | Both useful; GeoNames has one documented tabular import contract |
| Timezone | IANA timezone column per feature | Usually requires polygon enrichment | GeoNames directly satisfies the product invariant |
| Subject link | GeoNames admin1 code; reviewed mapping to ISO 3166-2 required | Administrative boundaries/tags; topology and ODbL handling required | GeoNames mapping is smaller and fail-closed |
| Updates | Country snapshots plus daily modification/delete feeds | Planet/regional snapshots plus replication diffs | Both updateable; T035 pins full snapshots for reproducibility |
| Coverage/noise | Large gazetteer including very small features | Very rich, locally variable tagging | Both need filtering and operator-visible settlement context |
| Prayer authority | None | None | Neither source may assign prayer authority or policy |

OpenStreetMap remains a possible map/polygon source for a separately reviewed
feature, but mixing it into the GeoNames derivative catalog is intentionally
avoided in T035.

## Pinned revision and coverage

The tracked `geodata/geonames/source-manifest.json` pins exact byte length and
SHA-256 for three 2026-08-29 inputs. Raw archives are retained outside Git and
the generated full catalog is ignored.

The reproducible full import produced:

| Metric | Value |
|---|---:|
| Catalog revision | `catalog-geonames-ru-2026-08-29-58af26706f194677` |
| Canonical content SHA-256 | `58af26706f194677d5d703c512fd9290d9b97657b26713c4c52f9685593f29f2` |
| GeoNames populated-place rows in supported active feature codes | 192,152 |
| Imported cities with a Russian canonical name and mapped subject | 166,557 |
| Skipped because no Russian canonical name was supplied | 25,427 |
| Explicitly excluded because subject could not be mapped | 168 |
| Mapped federal subjects in the GeoNames RU admin1 snapshot | 83 |
| Distinct exact `Киров` search results | 9 |
| Generated pretty JSON size (not tracked) | 96,118,393 bytes |
| Generated JSON SHA-256 (not tracked) | `c9b8ebba82b1418c4ed388a7f818fd9bf69b9817c677864417b2b28d78ec3497` |

`UNKNOWN`: GeoNames does not encode every jurisdiction claimed/administered by
the Russian Federation under its RU country/admin1 snapshot. T035 reports an
83-subject boundary and does not silently merge another country's rows or a
second geographic database. Adding missing jurisdictions requires a separate
source/scope/license decision.

The importer skips records without a Russian name rather than presenting a
Latin source label as a canonical RU name. A future reviewed alias/name update
may increase coverage without changing stable city IDs.

## Identity and canonicalization

`PROPOSAL`:

- NamazTime city ID is `city-` plus the first 128 bits of
  `SHA-256("geonames\0RU\0<geonameid>")`. It does not change when a city is
  renamed and does not expose a sequential tenant/database key.
- Canonical display name is preferred non-historic/non-colloquial Russian,
  then another current Russian name, then a Cyrillic source name. Missing RU
  names are counted and omitted.
- Explicit current Russian and English names plus the source ASCII name become
  normalized aliases. Historic and colloquial alternate-name records are not
  search aliases.
- GeoNames feature code is retained as `settlement_type`; NamazTime does not
  invent a Russian legal settlement classification.
- Every city stores coordinates, IANA timezone, population (when supplied),
  source record/revision/modification date, canonical URL and license.
- The 83 GeoNames admin1 codes are explicitly mapped to ISO 3166-2-style
  `RU-*` subject records. Blank/generic/legacy codes are excluded only through
  the reviewed mapping file and a written reason. A new unmapped code fails the
  whole import as schema drift.

Ulyanovsk is:

```text
city-4adcfc15932f3850d5dd5dbaa17e3a4c
  → Ульяновск / Ulyanovsk / Синбирск
  → RU-ULY
  → 54.32824, 48.38657
  → Europe/Ulyanovsk
  → geonames:479123
```

These coordinates are catalog geography. They do not change the existing
Ulyanovsk timetable, approval, mosque binding or signed snapshot.

## Search and duplicate behavior

Search is exact after Unicode case folding, trimming and whitespace collapse.
It searches canonical names and explicit aliases. Results are sorted by
federal-subject code, canonical name and stable city ID.

No `first()` or population winner is exposed as a safe selection. `Unique`
returns a city only when exactly one result exists. Unknown and duplicate names
return no automatic choice. Each result contains subject, settlement feature
code, coordinates, timezone and geographic provenance so an operator can make
an explicit selection.

Typo tolerance, fuzzy ranking and transliteration generation are excluded from
T035. Source-provided transliterations are sufficient for the first production
contract and are auditable.

## Import, revisions and rollback

`cmd/citycatalog`:

1. strictly decodes the tracked source manifest and admin1 mapping;
2. reads only bounded regular non-symlink files from an explicit directory;
3. validates byte length and SHA-256 before ZIP extraction;
4. rejects column, UTF-8, duplicate-ID, mapping, coordinate, date and timezone
   drift fail-closed;
5. sorts regions/cities and creates a canonical content SHA-256/revision;
6. atomically writes JSON;
7. optionally verifies a previous catalog and emits a deterministic
   add/remove/change diff.

Two imports from the exact pinned inputs produced byte-identical catalog files
and a zero-change diff with SHA-256
`2e7c2371b7d36999be370fb8dbb6f182b2cd7ecbe7640d5c85e7622ce069e62d`.

The importer does not activate a revision. T036 owns PostgreSQL staging,
review, activation and rollback. Raw source bytes and the generated catalog
must be retained in controlled artifact storage for any activated revision.

## Explicit non-capabilities

- no city, subject, coordinate, polygon, population or timezone assigns a
  prayer authority;
- no research registry entry becomes executable;
- no generic Russia calculation or neighboring-city fallback exists;
- no geographic dump or search network call enters the TV client;
- no bulk third-party dataset is committed to Git or embedded in the pilot APK.
