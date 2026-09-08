# T049 — baseline research and demo-path audit

Date: 2026-09-08. Implementation/research is in progress, not nationwide complete.
Owner requirements: [T049](../../docs/tasks/T049-nationwide-first-party-onboarding.md).
Policy checkpoint: `8685832`; baseline `325a343` matched actual origin/main and
had a clean tree. No push or PR is authorized.

## What the old research actually established

Reviewed ONE_MUSLIM_APK_RESEARCH.md, ULYANOVSK_ONE_MUSLIM_COMPARISON.md,
RUSSIA_PRAYER_TIME_AUTHORITY_RESEARCH.md, RUSSIA_CITY_SOURCE_ARCHITECTURE.md,
the old JSON draft and T034–T041 tasks/implementation. Historical static counts
are retained as historical evidence, not reclassified as current runtime facts.

| Prior conclusion | Current assessment | Consequence |
|---|---|---|
| Competitors combine stored tables/regional policies with local calculation | Historical `CONFIRMED_STATIC`, useful architecture only; the supplied 1Muslim base APK runtime was blocked by missing splits | No competitor rows, IDs or match statistics enter source provenance |
| Ulyanovsk 1Muslim numeric correlation implies a shared source | Still only `INFERENCE`; no author/authority chain was proven | Preserve NamazTime's own retained source/approval chain |
| One national Russia/MWL calculator is insufficient | Current explicit owner policy and compatible with the reviewed heterogeneity | No generic, nearby-city, capital or commercial fallback |
| Six `confirmed_official` subjects in the 31-subject draft are ready sources | Not established: that label never verified current transport, parser, scope and artifact validity together | Requalify each actual source from primary evidence |
| Parallel authorities make a subject `ambiguous`/unusable | Superseded product decision, not a religious fact | Independent qualified choices coexist; no global-tier suppression |
| Public first-party use requires written permission/contact/external approver | Superseded by ADR 0019 | Qualify autonomously; record actual partnership only when supplied |
| DUM RT helper is unavailable (410) | Direct public HTTPS retrieval now returns 200 and links 2026 XLSX plus locality CSVs; web-tool 410 is not current origin transport proof | Inspect actual files and dates; do not stop at cached search/reader output |

The [DUM RT policy account](https://dumrt.ru/ru/news/news_26996.html) remains
`CONFIRMED_PUBLIC` for a republic-wide decision based on Hanafi scholarship and
astronomical/geographic factors. It does not itself publish sufficient numeric
calculation parameters. Its [current timetable page](https://dumrt.ru/ru/help-info/prayertime/)
was retrieved directly on 2026-09-08 with HTTP 200; the Kazan CSV contains 365
dated 2025 rows and 365 dated 2026 rows with different column counts. This
disproves both an unavailable-transport assumption and an assumption that every
row belongs to the year in the download heading. Qualification still requires
actual normalization and comparisons, not this discovery.

## Geographic coverage audit

The current tracked `geodata/geonames/ru-region-map.json` has **83** mapped
subjects. This number was derived with jq, not hard-coded from a political list
or competitor catalog. The old JSON has 31 entries, leaving 52 not investigated
in that pass:

RU-AL, RU-ALT, RU-AMU, RU-ARK, RU-BEL, RU-BRY, RU-BU, RU-CHE, RU-CHU,
RU-IRK, RU-IVA, RU-KGD, RU-KL, RU-KLU, RU-KR, RU-KHA, RU-KK, RU-KIR,
RU-KO, RU-KOS, RU-KGN, RU-KRS, RU-LIP, RU-MAG, RU-ME, RU-MUR, RU-NEN,
RU-NGR, RU-NVS, RU-OMS, RU-ORL, RU-PRI, RU-PSK, RU-RYA, RU-SA, RU-SAK,
RU-SMO, RU-TAM, RU-TOM, RU-TUL, RU-TVE, RU-TY, RU-UD, RU-VLA, RU-VLG,
RU-VOR, RU-YAN, RU-YAR, RU-YEV, RU-KYA, RU-KAM, RU-ZAB.

The old generated full catalog and pinned raw archives are absent in the current
workspace/checked artifact caches; this is not proof they never existed.
The tracked T035 revision/hash remains historical and must not be assigned to
new downloads. Current GeoNames archives were retrieved outside Git; gazetteer
and aliases differ from the old pinned hashes, while admin1 metadata matches.
A separate newly pinned import is needed for current local materialization;
no old manifest or pilot binding may be silently relabelled as those new bytes.

## Why normal debug setup shows demo

Inspected source, not a new emulator reproduction:

- `src/debug/.../RuntimeDeviceSetupGatewayFactory.kt` unconditionally ignores
  transport/provisioning and creates `DevelopmentDeviceSetupGateway` plus a
  `DevelopmentPrayerScheduleRepository` wrapper.
- The gateway contains six synthetic city records and generates one authority
  per city (two for Moscow), named «Демо-организация …». Canonical source IDs,
  coordinates and geography provenance are fixtures, not GeoNames bindings.
- `developmentPreview` repeats six hard-coded prayer values with a five-minute
  index shift. `toDevelopmentSchedule` projects them across yesterday through
  next year, generates fixed iqamah, and labels diagnostics synthetic/proposal.
- Selection saves only synthetic city/choice IDs in application-scoped shared
  preferences. The wrapper overrides the display projection while retaining
  the signed Room base. Thus normal debug behavior can show fake organizations
  even when a real local pilot snapshot exists underneath.
- Release/pilot use the provisioned device-scoped client and T041 pending-review
  handoff. Merely replacing authority labels would leave invented prayer data
  and unsigned projection in place and would not satisfy T049.

T049 must move synthetic behavior to explicit test/evidence wiring, use canonical
geography and real qualified source materialization, and activate only verified
signed immutable artifacts through local persistence. Android TV state/ownership
review follows the repository android-tv-screen and compose-agent boundaries;
no visual redesign or generic calculator is part of this change.

## Persisted registry and pilot

Current schema-v1 domain/source records contain only `research_only`, `approved`,
`stale`, `unavailable`; policies require a mosque binding and approval ID.
T036–T041 activation verifies signed legacy approval and published-snapshot
references. T040 retains all choices only at the globally highest eligible tier;
T039/T041 selection appends `pending_review` without assigning a snapshot.
These real admission/model restrictions must be evolved, not bypassed with a
fake `approved_by` or an alternate unsigned debug repository.

The Docker inventory has no running NamazTime PostgreSQL instance. Unrelated
containers were not queried or changed. Existing PostgreSQL E2E documentation is
historical evidence; current DB/schema claims require a fresh isolated gate.

Fresh narrow verification:

- `go test ./internal/registry -run 'TestPilotBindings|TestArtifactVerifier' -count=1`
  passes canonical pilot composition, retained artifact proof and tamper checks.
- `make test-research`: 4 synthetic comparison-harness tests pass. This does not
  rerun or certify competitor APK behavior or recover missing external raw data.
- Pilot snapshot raw SHA-256 is still
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
- `make docs-check test-skills` passes; two unbundled upstream maintenance modules
  explicitly skip. A clean archive reproduced the prior CI broken-reference
  failure; checkpoint `8685832` fixes the documentation target, not remote CI.

## Continuation obligations

Complete all mapped subjects, including repeated first-party checks for old
claims. Preserve actual search trails and distinguish source existence from
qualification and active coverage. Implement reusable real transports/parsers,
qualification/publication contracts and normal Android setup, then all requested
representative real E2E and repository gates. T038's former external blocker is
removed, but T038 is not DONE until a second actual regional adapter is verified.
