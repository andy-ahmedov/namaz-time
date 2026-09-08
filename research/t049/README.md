# T049 — nationwide first-party source research

This report supersedes the historical 31-subject T034 draft for current source
onboarding. It does not retroactively validate that draft. The complete task
and the previous demo/approval-policy audit are in [the assignment](../../docs/tasks/T049-nationwide-first-party-onboarding.md)
and [BASELINE_AUDIT.md](BASELINE_AUDIT.md). T049 remains in progress; see
[PLANS.md](../../PLANS.md) for later materialization and runtime checkpoints.

## Complete research inventory

`CONFIRMED_PUBLIC` claims and their limitations are recorded individually in
four sanitized research ledgers. The compiler verifies exact subject-set
equality against the pinned canonical catalog, not merely equal counts.

| Research group | Subjects | Evidence, sources and search trail |
| --- | ---: | --- |
| Western | 29 | [regions-west.json](regions-west.json) |
| Eastern | 21 | [regions-east.json](regions-east.json) |
| Southern | 13 | [regions-south.json](regions-south.json) |
| Volga / Ural | 20 | [regions-volga-ural.json](regions-volga-ural.json) |

The actual GeoNames-derived catalog revision is
`catalog-geonames-ru-2026-09-08-5267d8bf5e48a158`: 83 subjects, 166,559 localities
and 204,641 aliases. Its substantial source JSON stays outside Git; raw SHA-256
is `d00cf6cb6b975e5ffd1ebb303c9ae101cb0a39ca5f444fafc7fbc27df6988231`.
All 52 subjects missing from the old 31-subject pass now have a research record.

There are 161 authority occurrences and 65 source-candidate records, using 135
distinct declared authority IDs and 63 distinct source IDs. These are research
identifiers, not a count of independently qualified legal organizations.
Central organizations may recur across subjects; similar names do not prove
common ownership, mandatory precedence or partnership with NamazTime.

## Machine-readable results and trust boundary

[nationwide-registry.json](nationwide-registry.json) preserves every subject,
authority and candidate using pinned file hashes and exact JSON pointers.
It also records implemented/admitted policy IDs, full qualification or retained
legacy proof identity, exact scope, snapshot hash, current coverage, unavailable
coverage, evidence dates and unresolved/search-trail references.

[operational-proof-index.json](operational-proof-index.json) explicitly links
research records to a previously verified local export. The compiler is a
reporting tool, not a source qualifier, signature verifier or registry admission
service. An `active` enum, an available parser or a research status cannot add
coverage. The caller must independently trust the prior-admission record and
its pinned manifest. The real exporter performs signature/proof verification
and registry Stage/Activate before such a record exists.

Each report has an explicit `state_at` and mosque-local dates by IANA timezone.
Current counts are distinct from immutable admitted-proof counts. Unavailable
city IDs are represented losslessly as the full canonical region minus the
explicit covered IDs, not a truncated list. Independent applicable choices
are retained without top-N or religious ranking.

At the initial export checkpoint (`2026-09-08T03:01:45Z` report state),
KBR covers all 202 canonical localities under its explicitly republic-wide
table, and the exact retained Ulyanovsk mosque covers one locality: 203 covered,
166,356 unavailable. This is the initial two-policy bundle, not a statement
that subsequent qualified publications are already selectable in that bundle.
The current pinned corrected export contains 48 qualified public policies plus
the exact retained legacy policy. Its `2026-09-08T04:08:18Z` report state derives
248 covered localities and 166,311 unavailable: one fully covered subject (KBR),
12 partially covered and 70 unavailable. All 49 admitted policies are current;
there are 250 city/choice bindings and two cities with multiple choices.
Public sources comprise two annual tables, 45
September-only tables and one June–September table; partial coverage is never
extended to annual data.

The superseded final47 export exposed a real projection defect: adding CDUM
made the retained Ulyanovsk choice non-executable in September. That package
was held from installation. Mixed-proof 365-date and Android decoder regressions
now pass; the corrected export retains original Uly continuously January–December
and CDUM separately in September. No signed snapshot, approval or qualification
was changed to repair the defect. Representative expanded runtime acceptance
remains a separate requirement, not implied by export or compiler success.

Six first-party authority IDs have qualified public publications: DUM RT,
CDUM Russia, DUM KBR, DUM Saratov, DUM Omsk and the local Muslim organization
of Sochi. The dataset's two additional legacy publisher records are retained
context, not two newly qualified organizations. Kazan has independently
qualified DUM RT and CDUM choices. Ulyanovsk retains both its original
mosque-approved composite and the CDUM public city table; their shared CDUM
affiliation is not claimed to be two independent religious chains.

| Subject | Currently covered localities / catalog total | City/choice bindings |
| --- | ---: | ---: |
| RU-KB — Кабардино-Балкария | 202 / 202 | 202 |
| RU-TA — Татарстан | 35 / 3,266 | 36 |
| RU-ULY — Ульяновская область | 1 / 1,005 | 2 |
| RU-CHE — Челябинская область | 1 / 1,931 | 1 |
| RU-KDA — Краснодарский край (Сочи only) | 1 / 2,315 | 1 |
| RU-KHA — Хабаровский край | 1 / 861 | 1 |
| RU-ME — Марий Эл | 1 / 1,775 | 1 |
| RU-OMS — Омская область | 1 / 1,984 | 1 |
| RU-PER — Пермский край | 1 / 4,311 | 1 |
| RU-ROS — Ростовская область | 1 / 3,131 | 1 |
| RU-SAR — Саратовская область | 1 / 2,177 | 1 |
| RU-SVE — Свердловская область | 1 / 2,695 | 1 |
| RU-UD — Удмуртия | 1 / 1,765 | 1 |

The other 70 subjects remain explicitly unavailable. Every exact covered ID and
lossless unavailable complement is in the machine-readable report; this table
does not authorize any expansion from the named city to its subject.

An independent outside-Git final48 audit (SHA-256
`0d863168cb26751dd4345d5e3245964f9fdd550ceb69c85a3952cc89ddb7aa8d`)
confirms the exact expected choice set, unchanged prior publication/legacy bytes,
full catalog equality, all 2,202 public day records / 13,212 onset fields, and
current/unavailable complements. Its fixed-bundle October 1 evaluation leaves
only KBR, Sochi and retained Ulyanovsk (204 choices); January 1, 2027 has no
covered interval. These are explicit expiry boundaries, not forecasts of future
source updates. The audit does not claim cryptographic admission or device execution.

## Implemented source patterns

| Adapter | First-party data and scope | Important boundary |
| --- | --- | --- |
| [DUM RT CSV](DUMRT_ADAPTER.md) | Official locality selector, independent annual XLSX cross-check | Each exact locality separately; current imports September only; no inferred day offsets |
| [CDUM HTML](CDUM_ADAPTER.md) | Official per-city published tables | Independent from DUM RT; malformed, annotated or ambiguous inputs fail closed |
| [KBR PDF](KBR_ADAPTER.md) | Authority-linked annual table explicitly labelled for KBR | Republic-wide evidence is specific to this source, not a general capital-city fallback |
| [Saratov HTML](SARATOV_ADAPTER.md) | Exact-city monthly table and first-party print/metadata corroboration | Not the whole oblast; publisher source hyperlink retained |
| [Sochi XLSX](SOCHI_ADAPTER.md) | Annual first-party workbook and independently corroborated organization identity | Exact canonical Sochi only, not inferred Greater Sochi settlements |
| [Omsk JSON](OMSK_ADAPTER.md) | Public publisher data corroborated by its monthly images | June–September only; published convention is not a complete astronomical calculation policy |

`PROPOSAL`: qualified public publication uses ADR 0019's autonomous,
hash-bound source qualification, never a fictional external approver. The
existing isolated signer authenticates NamazTime's materialized bytes, not
external endorsement. Substantial raw files and operational signed datasets
stay outside Git; automated fixtures are synthetic/sanitized.

The exact legacy Ulyanovsk composite remains a separate retained approval path:
same mosque/address, annual source and local override composition, signed
snapshot bytes and original approval. It is not promoted to an oblast-wide or
newly public-qualified source. Regional onset data never invents mosque iqamah
or Jumu'ah; those require their own applicable local data.

## Why localities remain unavailable

`UNKNOWN` is recorded per candidate rather than filled with a generic method:
unproven official ownership, no current table, Ramadan-only or expired coverage,
ambiguous locality identity, city-only scope, incomplete calculation parameters,
unsupported next-day/annotated rows, restrictive or inaccessible transport,
and unverified seasonal transitions are distinct reasons. No neighboring city,
subject capital, commercial aggregator, competitor dataset, guessed angle or
interpolation supplies missing values. Finding a Muslim organization does not
prove that it publishes an applicable prayer timetable.

Public retrieval does not itself prove unrestricted redistribution rights.
Explicit restrictive terms and access controls remain binding; no organization
contact, authentication bypass, anti-bot bypass or signing-key change is part
of this task. Clean-room competitor findings are discovery/cross-check context
only, not source rows or runtime provenance.

## Reproduction and verification

The [compiler](../tools/t049_nationwide_registry.py) requires the complete
outside-Git catalog, an explicitly pinned proof index and explicit UTC state.
The exact prior export command and all input hashes are retained in the proof
index. It refuses mismatched subject sets, changed pins, ambiguous mappings,
expanded scopes, stale eligibility, fake public approvals and partial inventory.

`python3 -B -m unittest discover -s research/tools -p test_t049_nationwide_registry.py`
passes 34 synthetic boundary tests. These checks and the 12 independent DUM RT
comparison tests are included in `make test-research`; they do not claim
cryptographic admission or device execution.
Provider drift tests, real source comparisons, publication/registry gates,
database rollback/restore and controlled emulator evidence are separate checks.
Only observed device behavior is labelled `CONFIRMED_RUNTIME`; static public
HTML/XLSX inspection is not `CONFIRMED_STATIC`, which is reserved for the
clean-room supplied-APK evidence vocabulary.
