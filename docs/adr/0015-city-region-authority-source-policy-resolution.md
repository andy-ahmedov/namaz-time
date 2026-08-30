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

Registry approval and snapshot IDs are references, not cryptographic proof. Future persistence adapters may construct executable datasets only from the existing verified approval and publication stores. Serving and device verification remain mandatory.

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
- registry persistence, admin API, and revision rollout remain future work.

## Rejected alternatives

- one nationwide authority or `method=Russia`;
- using polygon containment as proof of religious authority;
- copying competitor profiles, timetable rows, coordinates, or datasets;
- automatically choosing the first matching authority/source;
- silently calculating when an official timetable is stale or unavailable;
- putting geocoding, source fetches, or policy resolution in the TV display path;
- bypassing candidate validation, approval, signatures, or last-known-good activation.
