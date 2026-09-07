# ADR 0019: Public first-party qualification is distinct from endorsement

- Status: Accepted; implementation tracked by T049, not implied by this ADR
- Date: 2026-09-08
- Basis: [explicit owner assignment](../tasks/T049-nationwide-first-party-onboarding.md)
- Supersedes: mandatory external approval/partnership for public-source
  onboarding in ADRs 0001, 0011, 0015 and 0016; preserves their signing,
  immutability, access-control and last-known-good boundaries

## Decision

NamazTime may autonomously qualify and use ordinary publicly accessible
first-party prayer data from real authoritative Muslim organizations. No
separate owner approval, mosque approval, written DUM permission, contact,
partnership or named external approver is needed for this source path.
Qualification is a NamazTime technical/research decision, not an organization
endorsing NamazTime. The existing pilot/manual approval path remains supported
without rewriting its receipts, mosque-local rules or signed snapshot bytes.

## Qualification evidence

Every qualified mapping needs independently checkable evidence of:

1. canonical organization identity and first-party publisher ownership;
2. exact geographic applicability, linked to the canonical city catalog;
3. a current exact timetable, or sufficiently specified official calculation
   policy with deterministic parameters and multi-season first-party values;
4. legitimate public transport without authentication/anti-bot/access-control
   bypass or violation of explicit restrictive terms;
5. canonical URL, retrieval metadata, effective dates, IANA timezone, source
   raw/content hash, parser version, normalized hash and validation result;
6. actual date comparisons and relevant seasonal-transition validation, with
   no unresolved parser/schema/coverage failures.

Absence of separate written permission is not a failure. Substantial raw
copyrighted PDFs/databases/assets stay outside Git unless redistribution rights
are clear; retain operational metadata/hash evidence and sanitized/synthetic
fixtures. This policy is not a declaration that every public artifact is
licensed for unrestricted redistribution. Known restrictive terms can make a
source unavailable. No organization contact is authorized or required by T049.

Exact tables need not publish astronomical parameters; those remain UNKNOWN.
Calculation policies must publish sufficient parameters, including rounding,
Asr and applicable Fajr/Isha/high-latitude/seasonal behavior. A generic library
name or one matching date is not enough. Never infer missing values from
competitor applications, aggregators, commercial APIs, neighboring localities,
the subject capital, interpolation, MWL or a generic Russia/Hanafi method.
Partial and Ramadan calendars remain partial. Missing qualified coverage is
`unavailable`.

## Independent authorities and scope

Keep each real qualified authority as an independent choice. Specificity orders
evidence within one confirmed authority chain: locality table, locality/district
interface, explicitly subject-wide table/policy, verified calculation policy.
It does not rank unrelated Muslim organizations or remove their choices. No
top-N, averaging, winner by insertion order, inferred exclusivity or automatic
selection among multiple authorities is allowed. A city-specific timetable is
never promoted to subject-wide scope.

## Machine-verifiable lifecycle

The conceptual progression is `researched` → `first_party_verified` →
`scope_verified` → `source_validated` → `qualified` → `active/selectable`.
Persist evidence and exact artifact bindings, not a manually trusted status
string. Implementation may use a qualification record with validated stages;
it must reject missing/contradictory evidence and hash/parser/scope drift.
Qualification does not automatically activate a source outside the task's
authority. T049 explicitly authorizes local activation/materialization for E2E.

Qualification and optional endorsement are different contract branches. The
public branch must not mint a human approval, invent an `approved_by` person,
borrow the Ulyanovsk approver or reuse a mosque's signature for another source.
Optional endorsement records its actual organization/person, supplied evidence,
scope and dates. The legacy approval branch retains exact receipt verification.

Publication verifies the qualification/legacy-approval binding and materializes
immutable prayer data before signing. Use the existing isolated signer
abstraction without exposing or changing keys. A NamazTime signature proves
integrity/authenticity of NamazTime's artifact; it does not assert that a DUM
endorses the application. Hashes, canonical encoding, trust/environment
separation, anti-rollback, append-only audit and last-known-good stay mandatory.
An unsigned preview is never an activation artifact.

## Normal setup and local data

Normal setup searches canonical cities and shows all currently qualified real
choices with authority, type, exact scope, effective range and provenance.
Without a choice it says: «Для этого города подтверждённое расписание пока
недоступно». A debug build is not by itself an unapproved data classification.
Synthetic organizations/rows are allowed only in automated tests or an explicit,
unmistakable evidence scenario, never as a normal fallback.

Selection is not source qualification or external endorsement. Explicit
selection may activate only a fully verified signed materialized artifact via
the accepted local contract. Device credentials stay device-scoped; no admin
bearer, source fetch, parser, calculator or signing private key moves into UI
code. The display reads immutable local persistence, remains offline and keeps
the previous last-known-good snapshot after any failed update.

Regional prayer onset does not define mosque iqamah. Preserve device/mosque-local
iqamah and require independently applicable evidence for Jumu'ah. Existing
Ulyanovsk source composition, August override and local mosque policy are not
silently replaced by regional data.

## Migration and verification obligations

Evolve registry/publication/API/snapshot contracts explicitly; document schema
versions and compatibility. Old signed pilot bytes must continue to verify.
If PostgreSQL changes, prove up/down, rollback/reapply and backup/restore in
isolated databases. Verify missing proof, drift, stale/unavailable, independent
authorities, signatures, local atomic activation and Ulyanovsk regressions.
Document evidence separately from implemented behavior until each gate passes.

The old T038 written-confirmation blocker is superseded. T038 becomes DONE only
when T049 implements and verifies a second non-Ulyanovsk regional adapter with
proven exact scope. Research alone does not satisfy it.

Local checkpoint commits are authorized. Push, PR, production deployment,
signing-key changes, production data deletion and partnership claims are not.
