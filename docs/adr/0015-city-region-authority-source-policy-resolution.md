# ADR 0015: City, region, authority, source and policy resolution are separate

- Status: Accepted
- Date: 2026-08-30

## Context

Russian prayer-time policy is not uniform. Some regional organizations publish republic-wide calculation policies, others publish city or district timetables, and several subjects have parallel administrations. A city coordinate can establish geography but cannot establish religious authority, source approval, or mosque acceptance.

The existing Ulyanovsk pilot already has retained raw sources, deterministic normalization, field-level override provenance, named approval, and an Ed25519-signed offline snapshot. City search must reuse that pipeline rather than create a second schedule path.

Static clean-room study of IslamApp and 1Muslim confirms that regional profiles and exact timetables are useful architectural categories. Their embedded data and fallback choices are not NamazTime authority evidence and cannot be copied.

## Decision

NamazTime keeps these source-independent concepts distinct:

- `City` and `Region` for canonical geography and IANA timezone;
- `GeographicScope` for policy applicability candidates;
- `PrayerAuthority` for each named organization/publisher identity and its evidence label;
- `PrayerSource` for the allowed provider kind, one or more explicit authority references, and source identity;
- `PrayerPolicy` for a curated scope/authority-reference/source/mosque/effective-range/approval-reference binding;
- `CalculationProfile` or `TimeTable` as mutually exclusive policy payloads;
- `SourceOverride` for retained field-level source composition.

The control-plane resolver applies this precedence:

1. approved exact-city timetable;
2. approved regional official timetable;
3. approved regional calculation profile;
4. explicitly configured fallback policy;
5. unavailable.

Multiple eligible policies at one tier are an error. Insertion order, coordinates, country code, a calculation-library default, or a generic “Russia” method cannot break the tie. Research confidence is not production eligibility: a source becomes resolvable only after scope, provenance, permission, parser/normalizer, validation/diff, approval, effective range, and failure policy are complete.

Resolution does not publish prayer rows. Timetable and calculated candidates still pass the existing approval/signing pipeline. TVs receive only versioned signed snapshots, verify them, import atomically into Room, and retain last-known-good on failure. No source/geocoder/resolver network path is added to composables or display reducers.

The first executable registry entry is Ulyanovsk city bound to the Second Cathedral Mosque's existing 2026 effective source and signed pilot snapshot. The composite preserves the annual RDUM identity as `CONFIRMED_PUBLIC` and the August source's `rdumul.ru` attribution/legal identity as `UNKNOWN`; it does not promote the latter. It is not a city-wide or oblast-wide authority claim.

Registry approval and snapshot IDs are references, not cryptographic proof.
The T036 persisted service may activate a revision only after verifier adapters
return the exact mosque-scoped approval evidence and published-snapshot
ID/timezone/range/hash/key evidence from their authoritative stores. Serving
and device verification remain mandatory.

Registry revisions use schema version 1, a deterministic canonical dataset
hash, immutable normalized PostgreSQL rows, append-only evidence/audit records,
and one atomic active pointer. Rollback re-verifies a retained revision and
moves only that pointer; it never mutates publication data. Research-only,
stale, unavailable, unverifiable, or same-tier-overlapping records cannot be
activated.

## Consequences

Positive:

- geography cannot silently promote a religious authority;
- parallel authorities and same-tier conflicts fail closed;
- exact timetables and approved calculation profiles share one publication boundary;
- Ulyanovsk becomes the first entry of a general mechanism without changing signed bytes or TV behavior;
- source overrides remain traceable rather than overwriting raw artifacts;
- regional expansion can proceed record by record with explicit evidence.

Costs:

- every city/mosque needs explicit policy onboarding before service is available;
- a nationwide catalog requires licensed geography, alias maintenance, and duplicate-name UX;
- ambiguous regions require operator/authority coordination;
- verified-reference store adapters and the setup/admin API are completed by
  concrete vertical slices rather than by granting a generic registry ID trust;

T035 follow-up (2026-08-30): GeoNames RU under CC BY 4.0 is the selected
canonical geography source. Its pinned importer yields stable city IDs,
Russian names/aliases, ISO-mapped subjects, coordinates and IANA timezones.
This follow-up does not alter the decision: GeoNames has no prayer-authority
meaning, duplicate names never auto-select, and the executable registry still
contains only explicitly approved prayer records.

T037 follow-up (2026-08-30): the hard-coded executable Ulyanovsk constructor
is removed. A reviewed policy-binding document targets the exact pinned T035
catalog revision/hash, and `registryctl` verifies its pinned approval and
publication artifacts before PostgreSQL staging/activation. The authenticated
setup API searches the active revision and resolves only an explicitly chosen
canonical city ID for an authorized mosque/date. Registry rollback re-verifies
the retained approval/snapshot references and moves only the active pointer;
the existing signed USB-pilot snapshot is neither rewritten nor re-signed.

T039 follow-up (2026-08-30): a revision-specific assessment projects every
applicable option with exact precedence, authority/source evidence, freshness
and blocked reason. An authorized mosque operator may append an explicit
`pending_review` choice only for a selectable option in a still-staged
revision. This handoff does not mutate or activate that ambiguous revision;
the normal curator, approval, publication and activation boundaries remain in
force. Active/stale/unavailable/lower-precedence choices fail closed, and no
neighboring or nationwide fallback is introduced.

T040 follow-up (2026-08-30): one canonical `City` may expose zero, one or any
number of eligible authoritative `CityScheduleChoice` projections from one
immutable revision. This multiplicity is a normal discovery state; it does not
create authority-specific city rows and does not make the first choice an
automatic result. Each choice is derived from the existing scope, authority,
source, policy, approval/effective-range and timetable/calculation-profile
records. The stable choice ID is derived from canonical city and policy IDs;
the projection is not persisted and is not a new source of truth.

The automatic resolver continues to reject same-tier ambiguity, and T036 still
prevents an ambiguous staged revision from becoming the executable active
revision. T039 `pending_review` remains the only handoff: an explicit choice
does not approve, publish, activate, sign, or assign content. Neutral
tier/policy-ID ordering is presentation determinism only. No cardinality limit,
top-N filter or religious authority ranking exists.

> One canonical city may expose any number of eligible authoritative schedule choices. The system must not arbitrarily truncate or rank same-precedence religious authorities, and presentation multiplicity must never be interpreted as automatic authority selection.

> Transport/UI bounding, if ever needed, must use explicit pagination or equivalent lossless mechanics rather than top-N selection.

## Rejected alternatives

- one nationwide authority or `method=Russia`;
- using polygon containment as proof of religious authority;
- copying competitor profiles, timetable rows, coordinates, or datasets;
- automatically choosing the first matching authority/source;
- silently calculating when an official timetable is stale or unavailable;
- putting geocoding, source fetches, or policy resolution in the TV display path;
- bypassing candidate validation, approval, signatures, or last-known-good activation.
