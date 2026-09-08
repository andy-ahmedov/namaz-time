# Public first-party qualification and artifact protocol

`PROPOSAL`: executable T049 implementation of
[ADR 0019](docs/adr/0019-public-first-party-source-qualification.md).
Qualification/domain/publication and registry admission narrow tests pass.
PostgreSQL migration/recovery, normal Android setup and real materialization have
separate integration gates recorded in [PLANS.md](PLANS.md); this document does not claim
that a researched source is active.

## Trust boundary

`internal/qualification.Create` takes retained raw bytes, a deterministic
candidate, a reproducible diff fingerprint, independently recorded first-party
evidence and an actual canonical-catalog projection. It checks the raw hash and
length, complete dated data, scope/timezone/catalog identity, comparison values,
validation report and evidence-bound warning resolutions before producing a
`namaztime-source-qualification/v1` record. It never fetches a source, invents a
person, invokes a signer or activates a device.

Researchers still establish whether the named organization owns the publisher
and whether its explicit language supports the scope and clock meanings. A
content hash identifies a capture; it does not prove the truth of a claim.
Ownership, scope, currentness, terms, time semantics and actual value evidence
must all be retained with public URL, capture time and hash. At least three
distinct covered comparison dates are required, or every date for a shorter
table; every calendar quarter touched by the requested range needs a comparison.
No unresolved validation error or warning can be hidden by a status enum.

The current executable qualification contract supports exact `official_api`,
`official_file`, `official_html` and `mosque_calendar` tables. It deliberately
does not admit a calculation-profile string: a future implemented official
calculation policy needs sufficient published parameters and its own verified
multi-season proof. Researching angles alone does not enable calculation.

Public data validation does not require a fabricated permission record or a
zenith field absent from the source. It still checks the six ordered onset
values, optional fields, gaps/duplicates, named timezone, DST gaps/folds and
bounded day-to-day change policy. The retained legacy controlled-import
validator continues to require its actual permission and existing pilot fields.

## Immutable fingerprints

The candidate keeps its existing normalized fingerprint, including source/raw/
transcription/parser and every reviewed field. Qualification additionally binds:

- the full evidence, scope, authority, catalog revision and freshness;
- candidate, raw, normalized, transcription, diff and validation SHA-256;
- `onset_sha256`, covering all materialized `prayer_days`;
- an honest `decision_system = namaztime:source-qualification/v1` and UTC time.

Qualification SHA-256 uses the existing snapshot sorted-key canonical JSON
encoding, excluding only root `qualification_id` and `sha256` fields. Its ID is
`qualification-` plus the first 32 hex characters of that hash. Onset SHA-256
covers the canonical prayer-day array. The shared encoder preserves the legacy
snapshot bytes: ASCII object keys sorted recursively, array order retained,
UTF-8 strings and lexical JSON numbers unchanged. Duplicate JSON members and
invalid UTF-8 fail closed. Wire validation does not silently discard present
empty/default fields before checking hashes.

## Snapshot and signing versions

| Contract | Legacy `1.0` | Public-qualified `2.0` |
|---|---|---|
| Snapshot source | Actual `approval` | Full `qualification`; no `approval` property |
| Signing request / audit receipt | Existing approval and authenticated receipt fields | Separate qualification reference; no human-approval fields |
| Signer response | Two-signature `1.0` envelope | Same transport envelope, matching v2 request identity |
| Provenance attestation domain | `namaz-time/publication-attestation/v1` + NUL | `namaz-time/publication-attestation/v2` + NUL |
| Local prayer policy | Actual approved mosque rules | No inferred iqamah or Jumuah; device-local settings remain separate |

The historical snapshot field named `mosque` carries a geographic display
context for v2, explicitly identified by qualification scope. It is not a claim
that a new mosque exists. The backend checks its complete name/region/locality/
timezone against the canonical catalog. Its ID is `public-scope-` plus the first
32 hex characters of SHA-256 of the exact scope ID. The signed artifact protects
the resulting display identity; TV clients additionally bind its scope ID and
timezone without needing the whole city catalog.

Publication recomputes candidate, diff, validation and proof bindings before
protected signing. The snapshot verifies every onset row's hash and matches
independent comparison values. A signer-attested audit reference binds the
full qualification hash and candidate provenance. This signature authenticates
NamazTime's artifact, not a DUM endorsement of the application.

The direct publication API does not load the canonical city catalog. Its caller
must first use `qualification.Create`; before a source becomes executable,
registry admission independently derives all scoped cities from the complete
canonical catalog, checks its revision and proof hash, and compares the entire
authenticated snapshot display context (ID, name, country, region, locality and
timezone). Do not route arbitrary signed-file imports around that catalog gate.

Freshness is checked at the actual attested signing and publication instants,
not only the prepared snapshot's `generated_at`. Receipt validation also
requires `decision <= generated <= signed <= published`; correctly re-signed
but contradictory provenance is rejected. These are historical timestamp
checks, not a wall-clock expiry rule that deletes an offline LKG snapshot.

Production still requires the existing isolated signer abstraction, lifecycle-
aware public trust policy and test/staging/production key separation. No signing
key is changed by this protocol. Legacy signed Ulyanovsk artifacts, receipts,
source composition and mosque-local rules keep their original bytes and branch.

## Local import and publication commands

The `namaztime-public-source-import/v1` manifest records source identity/kind,
canonical URL, exact coverage/locality, expected raw artifact metadata, parser
version, catalog content SHA-256, day-delta threshold and `evidence_review`.
The latter contains the researched authority/scope/timezone/catalog revision,
HTTP capture metadata, permitted terms, independent evidence/comparisons and
any evidence-bound warning resolutions. There is no human approval input.

`internal/onboarding.Inspect` uses the native strict DUM RT CSV, CDUM HTML,
Omsk JSON or Sochi XLSX adapter, or the explicitly republic-wide KBR annual
PDF-text adapter. Unsupported parsers, geographic expansion, missing source
proof, changed raw/catalog hash or malformed rows return no usable inspection.
KBR additionally requires the original PDF, extracted-text SHA-256 and pinned
`pymupdf/1.28.2:text:sort=false:join=form-feed` method described in its adapter
evidence; the original raw PDF is not replaced by text provenance.

```bash
go run ./cmd/ingestor inspect-public \
  --manifest /absolute/retained/import.json \
  --catalog /absolute/retained/city-catalog.json \
  --raw /absolute/retained/source-artifact \
  --at 2026-09-08T12:00:00Z \
  --out /absolute/retained/qualified-inspection.json

go run ./cmd/publisher assemble \
  -inspection /absolute/retained/qualified-inspection.json \
  -snapshot-id exact-versioned-source-snapshot \
  -generated-at 2026-09-08T12:01:00Z \
  -signing-key-id existing-active-schedule-key \
  -out /absolute/retained/publication-request.json
```

Use actual recorded timestamps and an existing authorized key ID, not the
illustrative values above. Both outputs are exclusive new files; retained
artifacts stay outside Git. `inspect-public` optionally accepts `--extracted`
and `--previous` (the prior candidate) for conversion/diff inputs. `assemble`
rechecks qualification and the complete reproducible diff and rejects any
mixed approval or mosque prayer-policy arguments. The existing prepare,
isolated two-signature signer, finalize, receipt-ledger and verify workflow then
applies; these commands do not create/change keys or activate a device.

## Compatibility and rollback

Old readers must reject v2 rather than interpreting qualification as approval.
Updated readers retain v1 verification. A malformed/missing/conflicting proof,
wrong raw/parser/source/scope/hash or publication outside bounded freshness
fails closed. Expiry makes a choice unavailable; it never selects another source.
Stored historical qualification can remain evidence after expiry without
authorizing a new publication or active current choice.

The pure qualification/publication protocol changes no persisted schema.
Registry v2/PostgreSQL v9 and Android Room v4 are separate integrations with
their own migration and recovery checks recorded in PLANS.md. Reverting an activated deployment would require
a v2-aware reader or an explicitly verified retained v1 artifact; never discard
last-known-good data or silently downgrade trust to make a rollback succeed.
