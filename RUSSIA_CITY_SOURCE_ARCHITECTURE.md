# Russia city/source resolution architecture

Date: 2026-08-30  
Status: T035 geographic catalog implemented; executable prayer coverage remains the Ulyanovsk pilot only

## Outcome

`PROPOSAL`: NamazTime resolves a searched city to an approved prayer policy in the control plane, not on the TV display path:

```text
query
  → canonical City
  → Region + candidate GeographicScope records
  → explicit PrayerAuthority reference(s) / PrayerSource bindings
  → PrayerPolicy precedence and ambiguity check
  → TimeTable or CalculationProfile
  → existing validation / diff / approval / publication pipeline
  → versioned, hashed, Ed25519-signed snapshot
  → device assignment / local Room / last-known-good display
```

Geographic containment only finds candidate scopes. It never creates religious authority, approval, provenance, or permission to publish.

No nationwide `method=Russia` fallback exists. An unresolved, expired, invalid, or ambiguous mapping returns `unavailable` or `ambiguous` and leaves the last-known-good device snapshot active.

## Evidence basis

- `CONFIRMED_PUBLIC`: regional first-party evidence includes a republic-wide approved system in Татарстан, locality-specific annual calendars in Ulyanovsk Oblast, city-only publications in several subjects, partial seasonal artifacts, and parallel organizations in other subjects. These are incompatible with one silent national mapping.
- `CONFIRMED_STATIC`: IslamApp packages geographic profiles and chooses the most specific matching profile; 1Muslim distinguishes stored timetable cities from calculated catalog cities. These are architectural observations only. No competitor profile, row, coordinate, code, or dataset is used by this foundation.
- `CONFIRMED_PUBLIC`: the Ulyanovsk seed coordinates come independently from OpenStreetMap city relation `2049867`, with ODbL attribution. Coordinates are catalog metadata, not schedule evidence.
- `PROPOSAL`: the current implementation uses only the already approved Ulyanovsk source/policy/publication identifiers. It does not calculate or copy a new schedule.

## Entity boundaries

The initial source-independent types live in `internal/domain/city_source.go`.

| Entity | Responsibility | Must not imply |
|---|---|---|
| `City` | Canonical name/aliases, country, region, coordinates, IANA timezone, geographic provenance, optional explicit fallback policy ID | Religious authority or a prayer method |
| `Region` | Federal-subject identity and ISO 3166-2 code | One canonical authority for every mosque |
| `GeographicScope` | City or region applicability candidate; later versions may add licensed polygons | Authority, approval, or source trust |
| `PrayerAuthority` | One named organization/publisher identity plus its exact evidence label; a composite source can reference several | Exclusive status outside its recorded component binding |
| `PrayerSource` | Allowed provider kind plus explicit authority-reference list and scope reference | Approval or publication eligibility by itself |
| `PrayerPolicy` | Curated binding among scope, authority references, source, mosque(s), effective range, approval reference, and one payload | Proof that the approval receipt/signature was verified |
| `CalculationProfile` | Versioned approved calculation policy reference and effective range | Generic calculator defaults or inferred angles |
| `TimeTable` | Versioned effective timetable reference, timezone, scope, mosque binding, source overrides, and immutable published snapshot ID | Raw competitor rows or live device fetches |
| `SourceOverride` | Approved field-level relationship between retained base and override sources over an effective range | Mutation or deletion of either source artifact |

The existing snapshot `SourceMetadata`, candidate records, approval records, signing receipts, and device assignments remain authoritative for publication. The registry does not duplicate or weaken their hashes and signatures.

## City catalog and search

`PROPOSAL`:

1. Search is against the pinned GeoNames RU catalog under CC BY 4.0; raw bulk inputs/generated output remain outside Git.
2. Canonical NamazTime IDs are deterministically derived from the stable GeoNames record identity and remain independent from display names.
3. Preferred Russian names and current RU/EN/ascii aliases are explicit. Search is exact after case/whitespace normalization.
4. A catalog entry stores IANA timezone and geographic provenance. Numeric offsets are rejected.
5. Coordinates can narrow geographic candidates or support an operator map, but cannot select between parallel religious authorities.
6. Remote geocoding, if later added, belongs in setup/control-plane code and must produce a reviewed canonical match. TV composables and display reducers never call it.
7. Duplicate place names must return multiple canonical candidates with subject/country context; the resolver must not guess.

The pinned 2026-08-29 import yields 166,557 Russian-named records across 83 mapped GeoNames RU admin1 subjects. It excludes 25,427 rows without a Russian canonical name and 168 rows without a safely mapped current subject. Same-name results retain subject, feature code, coordinates, timezone and provenance; exact `Киров` has nine results and cannot auto-select. Source-provided transliteration is supported; generated transliteration and typo tolerance remain later work.

## Resolver contract

Input:

- canonical `city_id`;
- explicitly bound `mosque_id`;
- local Gregorian date (`YYYY-MM-DD`).

Output:

- `City` and `Region` identity;
- selected `GeographicScope`;
- one or more explicit source-component `PrayerAuthority` records, preserving each evidence label, and one `PrayerSource`;
- approved `PrayerPolicy`;
- resolution tier;
- either one `TimeTable` or one `CalculationProfile`.

Errors are typed:

- invalid request;
- invalid registry dataset;
- ambiguous policy at one precedence tier;
- policy unavailable.

The resolver never returns a partially linked policy. Registry construction validates unique IDs, foreign keys, city/region consistency, coordinate bounds, loadable IANA timezones, source-to-authority/scope bindings, exact evidence-label vocabulary, non-empty approval references and mosque bindings, effective ranges, timetable/profile references, publication IDs, and source-override references.

`PROPOSAL`: an approval ID and published snapshot ID in the in-memory registry are references, not authentication. A future persistence adapter must obtain them from the existing verified approval/publication stores before constructing an executable dataset. Serving and device paths continue to verify the signed snapshot independently.

## Precedence and failure semantics

For one city, mosque, and date, apply exactly:

1. approved exact-city `TimeTable`;
2. approved regional official `TimeTable` explicitly bound to the mosque;
3. approved regional `CalculationProfile` explicitly bound to the mosque;
4. the city's explicitly configured fallback policy ID;
5. unavailable.

Rules:

- An expired or unapproved record is ineligible.
- Two eligible policies at the same tier are `ambiguous`; insertion order is not a tiebreaker.
- A regional timetable can cover a city only through its declared region scope and explicit mosque policy binding.
- A calculation profile requires its own authority/source/scope/version/approval. A country default from a calculation library is not eligible.
- A fallback is never inferred from country, nearby city, coordinates, library default, or staleness. It must name an existing approved policy in the city record.
- Source failure does not trigger another source strategy. Publication keeps the last-known-good snapshot active and surfaces staleness/unavailability to operators.

## Timetable and calculation publication

The resolver selects policy metadata, not prayer rows for the TV.

### `TimeTable`

An eligible timetable references an existing retained source chain and immutable published snapshot. Future timetable onboarding still follows:

```text
capture raw artifact/metadata
  → deterministic parser/normalizer
  → schema and semantic validation
  → previous/effective diff
  → named approval
  → canonical snapshot
  → SHA-256 + Ed25519 signature
  → staged assignment
```

### `CalculationProfile`

A profile is not a generic method enum. It must store or reference:

- approving authority and geographic scope;
- version/effective range;
- Fajr/Isha rules, Hanafi/Standard Asr, Dhuhr offsets, high-latitude and seasonal behavior;
- coordinate/timezone inputs and rounding semantics;
- reproducible official test vectors or accepted timetable comparisons;
- source artifact/hash/parser or approved specification;
- named approval and fallback/staleness policy.

Calculated rows then become ordinary candidates and pass the same diff, approval, signing, and last-known-good pipeline.

## Ulyanovsk vertical slice

The implemented seed resolves:

```text
"Ульяновск" / "Ulyanovsk"
  → city ru-uly-ulyanovsk
  → region ru-uly / RU-ULY
  → scope scope-ulyanovsk-city
  → source authorities:
       rdum-ulyanovsk-oblast [CONFIRMED_PUBLIC]
       rdumul-attributed-publisher-unconfirmed [UNKNOWN]
  → source effective-ulyanovsk-2026-v1
  → policy policy-ulyanovsk-second-cathedral-2026
  → timetable timetable-ulyanovsk-second-cathedral-2026
  → snapshot ulyanovsk-second-cathedral-2026-pilot-local-v2
  → timezone Europe/Ulyanovsk
```

The policy is bound to `second-cathedral-mosque-ulyanovsk` and the 2026 effective range. It is not promoted to every Ulyanovsk mosque or every locality in Ulyanovsk Oblast.

The timetable records `source-override-ulyanovsk-2026-08`, which links the retained official annual baseline to the approved August manual source using the existing approval ID. The annual component preserves the confirmed RDUM identity; the August component preserves the source record's legally unconfirmed `rdumul.ru` attribution as `UNKNOWN`. The composite must not promote the latter into confirmed RDUM provenance. It references the existing signed snapshot; it does not regenerate, rewrite, or re-sign it. The Android UI, Room schema, bootstrap asset, signature verifier, publication receipt, and device assignment behavior are unchanged.

## Research registry versus production registry

`research/russia-prayer-source-registry-draft.json` is deliberately non-executable evidence metadata.

- `confirmed_official` means a first-party publication supports its stated scope, not that ingestion rights, parser stability, mosque approval, or exclusivity are complete.
- `strong_evidence`, `ambiguous`, and `unknown` records are not resolver candidates.
- Even `confirmed_official` records remain `research_only` until source onboarding is complete.
- The Ulyanovsk entry is the only `existing_approved_pilot` in the draft.

Promoting research metadata into the executable registry requires a reviewed change containing source contract, reproducible fixtures, validator/diff tests, approval path, effective range, failure behavior, and rollback plan.

## Persistence and API boundary

The current in-memory Go dataset is the smallest tested domain foundation, not a nationwide database migration or public API.

Future persistence should use normalized control-plane tables keyed by immutable IDs and revisioned registry snapshots. An API may expose search results and resolution explanations to the admin/setup UI, but it must not expose a policy as approved unless its source and approval records are complete.

No registry fields are added to the signed TV snapshot contract in this slice. Existing source provenance is already inside the signed snapshot; resolver audit metadata can remain control-plane data until a contract change has a concrete device use case.

## Timezone, date, and coverage rules

- Store/load IANA timezone IDs. The pilot uses `Europe/Ulyanovsk`.
- A resolve date is a local calendar date in the mosque/city timezone, never a UTC-offset-only value.
- Timetable timezone must equal the resolved city timezone.
- Policy and payload effective ranges both contain the requested date.
- Year rollover, leap day, DST changes in affected regions, high-latitude rules, Ramadan, Jumu'ah, and one-off overrides remain mandatory onboarding tests.
- Expiry becomes unavailable/stale; it does not trigger a silent source or calculation change.

## Security and recoverability

- The registry does not hold signing private keys.
- Registry selection cannot approve a candidate or manufacture official provenance.
- Published snapshots remain versioned, hashed, and Ed25519-signed.
- Serving and device verification remain mandatory; a registry reference is not authentication.
- TVs continue to render from immutable local persistence and retain last-known-good on failed sync.
- Registry rollback selects a prior reviewed registry revision; it does not mutate a published snapshot.
- No Android permission, analytics identifier, or network call is introduced.

## Implemented tests

`internal/registry/registry_test.go` verifies:

- canonical Cyrillic search and explicit Latin alias;
- independent geographic provenance and `Europe/Ulyanovsk`;
- exact Ulyanovsk authority/source/policy/timetable/snapshot binding;
- separate `CONFIRMED_PUBLIC` annual and `UNKNOWN` August source-authority identities in the composite;
- all four precedence tiers plus fail-closed unavailable behavior;
- ambiguity rejection at the same tier;
- invalid registry rejection for duplicate IDs, non-IANA timezone, broken authority links, absent approval, invalid effective range, and broken overrides;
- unsupported provider-kind rejection;
- malformed local-date rejection;
- defensive ownership of validated input and returned slice fields.

## Remaining unknowns and intentionally deferred work

- `UNKNOWN`: GeoNames' RU snapshot covers 83 mapped subjects, not every jurisdiction claimed/administered by Russia. T035 does not combine sources or reclassify another country's rows without a separate legal/scope decision.
- `PROPOSAL`: T039 may add richer operator presentation, but T035 already returns all exact duplicate-name candidates and never selects one automatically.
- `UNKNOWN`: no new regional source beyond Ulyanovsk has completed licensing, parser, scope, approval, and publication onboarding.
- `UNKNOWN`: several subjects have parallel authorities and require operator/mosque choice.
- `PROPOSAL`: do not add polygons until their license, update source, topology validation, border behavior, and policy linkage are specified.
- `PROPOSAL`: do not activate a calculation profile until official parameters and test vectors are available; timetable correlation alone is insufficient.
