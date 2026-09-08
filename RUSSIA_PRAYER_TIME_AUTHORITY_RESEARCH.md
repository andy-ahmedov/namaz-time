# Russia prayer-time authority and source research

Date: 2026-08-30  
Scope: first-party public research for city/subject prayer-source resolution; 31 federal subjects investigated  
Structured draft: `research/russia-prayer-source-registry-draft.json`

Historical T034 report, superseded for current onboarding by T049 and ADR 0019.
Do not treat its conclusions, transport failures or approval/partnership gates
as current evidence. [The T049 baseline audit](research/t049/BASELINE_AUDIT.md)
records the re-audit and identified gaps. The [superseding nationwide report](research/t049/README.md)
and [machine-readable registry](research/t049/nationwide-registry.json) preserve
all current catalog subjects and distinguish research from admitted coverage;
T049 materialization/runtime work remains in progress.

## Executive result

There is no evidence for one correct nationwide chain such as “Russia → DUM RF” or “Russia → CDUM.” Regional organizations publish different city tables, regional calculation policies, Ramadan-only artifacts, opaque widgets, and locality-specific calendars. Several subjects have parallel administrations. A first-party publisher can be confirmed without proving that it is the exclusive religious authority for every mosque in the subject.

Current subject-level research distribution:

| Mapping status | Subjects | Meaning |
|---|---:|---|
| `confirmed_official` | 6 | A first-party prayer source/policy states the represented scope |
| `strong_evidence` | 5 | First-party evidence exists, but scope/format/policy/custody is incomplete |
| `ambiguous` | 6 | Multiple plausible authorities/sources prevent automatic choice |
| `unknown` | 14 | No sufficiently evidenced first-party prayer mapping was recovered |
| Total researched | 31 | Not a claim of nationwide completion |

`PROPOSAL`: every record stays research-only until source terms, exact geographic scope, parser/normalizer, schema-drift behavior, approval path, and refresh/failure policy are onboarded. The existing Ulyanovsk pilot is the only already approved production-quality record in this draft.

## Evidence and status protocol

Repository evidence labels remain exactly:

- `CONFIRMED_PUBLIC`: official public page/file or supplied reference;
- `INFERENCE`: supported conclusion without direct proof;
- `PROPOSAL`: NamazTime design choice;
- `UNKNOWN`: unresolved.

The user-requested source-assessment words are separate registry values, not evidence labels:

- `confirmed_official`: a first-party authority source exists and states the scope represented by the mapping;
- `strong_evidence`: the authority/source is plausible and first-party, but at least one critical fact is incomplete;
- `ambiguous`: multiple organizations/sources or conflicting scopes prevent automatic selection;
- `unknown`: no safe mapping.

`confirmed_official` does **not** mean “exclusive authority for all Muslims or mosques in the subject.” It means the candidate is an official publication for its stated scope. Mosque approval and source binding remain separate.

## Priority regions

### Татарстан — `confirmed_official`

`CONFIRMED_PUBLIC`: [DUM RT's own policy account](https://dumrt.ru/ru/news/news_26996.html) says its Ulema Council developed prayer times using Hanafi scholarship, astronomical data, and Татарстан's geographic characteristics; the 12 January 2014 plenary approved the new system for all Татарстан. Its site has exposed a city/district selector covering Kazan and dozens of district centers and has published official prayer tables.

`UNKNOWN`: the former `/ru/help-info/prayertime/` helper URL returned HTTP 410 during follow-up on 2026-08-30. It is not treated as a stable ingestion interface; the policy decision remains first-party evidence, while current transport/versioning requires direct onboarding.

This is the strongest researched example of an approved regional calculation policy rather than a generic global method name.

What is still needed:

- exact current parameters, seasonal rules, and version identifier;
- stable download/API or permission to ingest official files;
- expected refresh cadence and schema-drift contact;
- confirmation that the site's general CC BY 4.0 notice covers the intended schedule artifacts.

`PROPOSAL`: model one republic policy with locality-specific resolved outputs. Do not replace it with `method=Russia` or infer settings from an aggregator.

### Башкортостан — `ambiguous`

`CONFIRMED_PUBLIC`: [DUM RB](https://dumrb.ru/) identifies itself as the Spiritual Administration of Muslims of the Republic of Bashkortostan and publishes current Ufa prayer times. [CDUM](https://cdum.ru/) is also based in Ufa and publishes Ufa prayer calendars through its official publications.

`UNKNOWN`: no first-party precedence rule was found establishing which publisher is canonical for every mosque or locality in Башкортостан.

`PROPOSAL`: require an explicit mosque/organization source binding. A city search may show Ufa, but it must not silently choose between DUM RB and CDUM.

### Дагестан — `confirmed_official`

`CONFIRMED_PUBLIC`: the [Muftiyat RD prayer page](https://muftiyatrd.ru/vremya-namaza) identifies the centralized regional organization and provides a city/district selector. The official organization also distributes a prayer-time mobile app.

`UNKNOWN`: the inspected public page did not expose a documented API, calculation policy, data version, approval metadata, or archival contract.

`PROPOSAL`: onboard the official city/district output as separate locality scopes; do not use one Makhachkala row for the mountainous and coastal republic.

### Чечня — `confirmed_official` for Grozny

`CONFIRMED_PUBLIC`: [DUM Chechen Republic](https://dumchr.ru/) describes republic-level coordination and publishes current times explicitly labeled for Grozny. Its first-party channel also publishes monthly schedules.

`UNKNOWN`: evidence does not establish that the Grozny table is valid unchanged for every settlement in the republic.

`PROPOSAL`: create a Grozny city mapping first; keep other localities unavailable until the authority supplies their scope/policy.

### Ингушетия — `unknown`

`UNKNOWN`: no stable current first-party prayer timetable, source interface, or policy document was recovered. Institutional ambiguity is reported in secondary material, but secondary reporting is insufficient to assign prayer authority.

`PROPOSAL`: unavailable until direct muftiate/community confirmation. Do not substitute a neighboring North Caucasus calculation profile.

### Other North Caucasus subjects

| Subject | Status | Public evidence | Safe conclusion |
|---|---|---|---|
| Kabardino-Balkaria | `strong_evidence` | [DUM KBR](https://www.kbrdum.ru/home) publishes current times and links a 2026 annual graph | Source is first-party; Nalchik/republic scope and artifact custody need confirmation |
| Karachay-Cherkessia | `unknown` | Candidate DUM KChR domain/channel found | No stable first-party schedule/policy recovered |
| North Ossetia-Alania | `unknown` | [Regional government page](https://minnats.alania.gov.ru/activity/religion) identifies the DUM/domain | Organization existence is not a prayer policy |
| Stavropol Krai | `unknown` | Candidate [DUM Stavropol Krai](https://dumsk.com/) domain/channel found | No stable current first-party schedule/policy recovered |
| Adygea | `strong_evidence` | [DUM RA and Krasnodar Krai](https://dumraikk.ru/) published an official [Ramadan 2026 artifact](https://dumraikk.ru/wp-content/uploads/2026/02/ramadan-2026_18feb.pdf) | Ramadan-specific evidence is not a full annual republic policy |
| Krasnodar Krai | `unknown` | Same cross-subject organization confirms jurisdiction | No krai/locality annual timetable scope was confirmed |

### Moscow federal city — `ambiguous`

`CONFIRMED_PUBLIC`: DUM RF-linked [Moscow Cathedral Mosque](https://www.mihrab.ru/) and [Council of Muftis / DUM RF](https://www.muslim.ru/) publish Moscow times. The [Spiritual Assembly of Muslims of Russia](https://dsmr.ru/) also publishes a Moscow schedule. Static IslamApp correlation with DUM RF values is not an authority contract.

`PROPOSAL`: mosque or authority binding is mandatory. City coordinates cannot decide between first-party religious publishers.

Moscow Oblast is a different federal subject. [DUM Moscow Oblast](https://www.dummo.ru/) is a first-party regional organization, but no canonical oblast timetable/policy was recovered; status is `unknown`.

### Saint Petersburg — `confirmed_official` for the city

`CONFIRMED_PUBLIC`: [DUM Saint Petersburg and the Northwestern Region](https://dum-spb.ru/) publishes prayer times explicitly for Saint Petersburg.

`UNKNOWN`: the city page does not prove identical validity across Leningrad Oblast or the whole Northwestern region. Leningrad Oblast remains a separate `unknown` mapping.

### Ulyanovsk Oblast — `confirmed_official` for explicit localities

`CONFIRMED_PUBLIC`: [RDUM Ulyanovsk Oblast](https://rdumul.ru/) identifies the regional organization and publishes locality-specific annual PDFs. The 2026 calendars name Ulyanovsk, Dimitrovgrad, Barysh, Inza, Sengiley, Staraya Kulatka, Staraya Maina, and other localities with different summer transition dates.

Repository evidence is stronger than the web summary for Ulyanovsk city:

- retained official annual PDF and SHA-256;
- deterministic 365-row transcription and parser version;
- August photo override with reconciliation ledger;
- approved effective schedule;
- authority/mosque approval receipts;
- signed snapshot and last-known-good TV pipeline.

`PROPOSAL`: the registry must point `ulyanovsk` to `effective-ulyanovsk-2026-v1`; it must not promote that exact table to every city in `RU-ULY`.

## Other researched subjects

### Confirmed or strong city sources

| Subject | Status | Confirmed scope/source | Limitation |
|---|---|---|---|
| Saratov Oblast | `confirmed_official` | [DUM Saratov Oblast](https://dumso.ru/analytics) publishes navigable 2026 monthly times explicitly for Saratov | Not an oblast-wide table |
| Orenburg Oblast | `strong_evidence` | [RDUM Orenburg Oblast](https://dumoo.ru/) publishes a widget and monthly Orenburg calendar posts | Embedded provider, format, and oblast scope need confirmation |
| Mordovia | `strong_evidence` | [DUM RM-associated portal](https://islam-rm.com/) exposes prayer times | Locality/calculation/republic scope not explicit |
| Perm Krai | `strong_evidence` | [RDUM Perm Krai page](https://islam59.ru/help-info/vremya-namazov/) is explicitly for Perm | Embedded provider/data contract and krai scope need confirmation |

### Ambiguous subjects

| Subject | Why automatic selection is unsafe |
|---|---|
| Penza Oblast | [RDUM Penza Oblast](https://muslime-penza.ru/) and [Central DUM Penza](https://cdumpo.ru/) are both current first-party organizations; no canonical timetable precedence recovered |
| Astrakhan Oblast | [RDUM Astrakhan](https://islam-astrakhan.ru/) publishes city times and district branches publish local schedules, but parallel administrations exist; exact locality/mosque binding is required |
| Volgograd Oblast | [Central DUM Volgograd](https://dumvlg.ru/) and another [regional first-party administration](https://www.34islam.ru/) coexist; no canonical timetable recovered |
| Chuvashia | Parallel regional organization evidence exists, but no directly confirmed canonical first-party timetable/source was established |

### Unknown after this pass

The registry keeps the following unavailable: Samara Oblast, Nizhny Novgorod Oblast, Rostov Oblast, Tyumen Oblast, Sverdlovsk Oblast, Kemerovo Oblast–Kuzbass, and Khanty-Mansi Autonomous Okrug–Yugra, in addition to the priority-region unknowns listed above.

`CONFIRMED_PUBLIC`: search results often returned commercial prayer-time aggregators for these places. They describe calculations such as MWL/Hanafi or a generic “Russia” method, but they are not religious authorities and were excluded from mappings.

## Source-format findings

| Public form | Strength | Operational risk |
|---|---|---|
| Official annual PDF/XLSX | Retainable, hashable, reviewable, usually suitable for `official_file` | Manual publishing errors, locality/year scope, licensing, schema/layout drift |
| Official city/district HTML selector | First-party, can cover many localities | Hidden/volatile API, scraper terms, JS/iframe dependency, no archival version |
| Official current-time widget | Useful authority signal | Often insufficient for annual/offline publication; provider may be third party |
| Official policy/ulema decision | Strong basis for `calculation_profile` | Parameters/version/test vectors may be absent |
| Official Ramadan image/PDF | Strong seasonal evidence | Cannot silently fill the rest of the year |
| Aggregator/calculator | Useful cross-check only | Not authority, provenance, or approval |

## IslamApp, 1Muslim, and NamazTime

The competitors are architectural evidence, not source authorities. Their embedded regional data must not enter NamazTime.

| Aspect | IslamApp | 1Muslim 5.9.5 | Recommended NamazTime |
|---|---|---|---|
| City search | Coordinates from geocoding/location select a profile | Concurrent local timetable-city and large city-catalog search; remote GeoNames-style fallback | Curated canonical city catalog with aliases; user-triggered setup/search, not TV display networking |
| Geocoding | Nominatim/Android Geocoder and platform location integrations are packaged | 233,916-place local catalog plus first-party GeoNames-compatible proxy | Licensed/public city seed in control plane; coordinates identify geography only |
| Regional selection | GeoJSON containment; more-specific feature wins | Explicit stored city ID or calculated place/country defaults | Explicit `GeographicScope` candidates resolved to approved `PrayerPolicy`; geometry never grants authority |
| Calculation | `Dynamic` profiles with ranges, angles, offsets, madhhab | Multiple local engines; country methods, custom seasonal range, Asr/high-latitude choices | Versioned `CalculationProfile` only when an approved regional authority publishes/accepts it |
| Static timetable | 366-row `TimeTable` profiles | 366 month/day rows for each of 621 stored cities | Year/effective-range `TimeTable` with source artifact, parser, approval, hash, and override provenance |
| Updates | Bundled parameter snapshot plus periodic atomic temp/replace path | Bundled database plus whole timestamp-addressed database ZIP replacement | Candidate ingestion/validation/diff/approval, then versioned hashed Ed25519-signed device snapshot |
| Offline | Local parameters/timetables | Local catalogs, timetable DB, calculated cache/Room | TV reads immutable Room state only; last-known-good remains active |
| Fallback | Static code contains MWL fallback when no profile matches | Calculated candidates/default methods coexist with stored results | Only an explicitly configured, pre-approved fallback; otherwise unavailable |
| Provenance | No authority/source provenance fields observed in parameter payload | No Ulyanovsk row provenance/approval fields observed | Authority, scope, source, retrieval, raw hash, parser, effective range, approval, signature are mandatory |

`CONFIRMED_STATIC`: IslamApp has 139 geographic features (66 marked RU), 127 dynamic profiles, 12 timetables, a most-specific polygon rule, and a fallback calculation. 1Muslim instead centers its exact timetable branch on stored city IDs and uses a separate global calculated-place path.

`INFERENCE`: both products independently validate the need for “regional profile or exact timetable plus offline data,” but neither observed payload demonstrates NamazTime-grade provenance. This supports the resolver shape, not reuse of their data or fallback behavior.

`PROPOSAL`: NamazTime should combine IslamApp's explicit geographic scope hierarchy with 1Muslim's clear stored-versus-calculated candidate distinction, then add the missing authority, provenance, approval, signature, and fail-closed controls.

## Registry rules derived from the evidence

`PROPOSAL`:

1. Store geographic identity and religious authority separately.
2. A subject code narrows candidate policy scopes; it never grants authority.
3. A confirmed city source is not automatically valid for its whole subject.
4. A regional authority may publish multiple locality tables or seasonal overrides.
5. Parallel authorities require explicit mosque/operator selection and approval.
6. Partial seasonal artifacts cannot silently fill annual gaps.
7. No source record becomes selectable until its terms, retrieval, raw hash, parser/normalizer version, scope, effective range, validation, approval, and refresh policy are complete.
8. Failure or ambiguity resolves to `unavailable`, never a nationwide fallback.

## Remaining research risks

- `UNKNOWN`: several official pages embed third-party widgets; publisher endorsement of the displayed numbers must be confirmed before automated ingestion.
- `UNKNOWN`: no legal/technical reuse terms were confirmed for most schedules.
- `UNKNOWN`: current-time pages may change without versions and cannot alone prove historical/future values.
- `UNKNOWN`: organization names and affiliations do not prove acceptance by a particular mosque.
- `UNKNOWN`: this 31-subject pass is intentionally incomplete and must not be marketed as nationwide coverage.

## Next source-onboarding work

`PROPOSAL`:

1. Contact DUM RT for a versioned policy specification/test vectors and schedule reuse terms.
2. Obtain explicit source/authority choices from pilot mosques in Ufa, Moscow, Astrakhan, Penza, and Volgograd before building adapters.
3. Inspect Muftiyat RD's city/district transport and onboard one synthetic-tested locality adapter fail-closed.
4. Confirm KBR, Orenburg, Mordovia, Perm, and Adygea geographic scope and source custody.
5. Continue first-party research for the 14 unknown subjects, prioritizing direct authority contact over aggregator discovery.
