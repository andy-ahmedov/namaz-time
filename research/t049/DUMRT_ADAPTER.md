# DUM RT locality CSV comparison — 2026-09-08

`CONFIRMED_PUBLIC`: the retained [DUM RT selector](https://dumrt.ru/ru/help-info/prayertime/)
contains 44 named locality options and links the same first-party
[2026 workbook](https://dumrt.ru/netcat_files/multifile/2649/vremena_namazov_RT_2026_0.xlsx).
The [machine-readable ledger](dumrt-localities.json) records every exact label,
case-sensitive remote filename, actual URL including its published query,
canonical URL, raw hash, safe HTTP metadata, catalog candidates and parser result.
These are comparison/binding proofs, **not source qualification or publication**.

## Complete comparison and current-range results

- Workbook: 1,195 shared strings; 16,062 rows, consisting of two headers and
  44 × 365 locality/date rows. Columns A/B are locality/Excel date; C–J are
  eight published time fields. Dates use the workbook's 1900 epoch.
- CSV: 16,059 individually parseable 2026 rows compared against the workbook;
  128,472 compared time values, zero differences. Dates and all eight source
  columns are compared, not only the six fields ultimately displayed.
- The actual public `dumrt.ParseCSVRange` API accepted September 1–30, 2026 for
  43 files. Each produced 30 days; a separately extracted workbook digest
  matches all 43 parser outputs across date plus eight fields. The ledger
  includes both candidate JSON SHA-256 and independent field-matrix SHA-256.
- `Aktanis.csv` fails the real parser at raw row 365: its `31.12.2025` record
  has 18 fields, and `2026-01-01` is not a separate parseable row. The malformed
  row appears to join two dates (`INFERENCE`); it is not split, repaired or
  backfilled. Its 364 individually valid 2026 rows agree with the workbook,
  but this is diagnostic evidence only; September still fails whole-artifact
  schema validation. No candidate is supplied for Актаныш.
- Eight files also pass the annual API: `Aleksey.csv`, `BolshAtna.csv`,
  `BolshieKaybesy.csv`, `Kukmor.csv`, `Laish.csv`, `Tulyachi.csv`,
  `VicokayGora.csv`, `Zelenodolsk.csv`. Another 35 fail time-order validation,
  and `Aktanis.csv` fails schema validation. Annual parsing alone does not
  qualify these eight sources or resolve their geographic identities.

`CONFIRMED_PUBLIC`: Kazan's May 5 column-C value `23:54` is present in both
transports. The parser rejects that day in a same-day ordered candidate.
No day offset, seasonal interpolation or neighboring-location replacement is
inferred. A September result does not establish usable annual coverage.

Sanitized seasonal spot checks below retain only two fields, not complete rows.
Each shown value agrees in CSV and workbook; the exhaustive counts above come
from the reproducible comparison, not these samples alone.

| Kazan date | Source C / publisher's daily Fajr | Source J / Isha |
|---|---|---|
| 2026-01-01 | 05:53 | 17:19 |
| 2026-03-20 | 03:39 | 19:41 |
| 2026-05-05 | 23:54 | 21:00 |
| 2026-06-21 | 00:58 | 22:03 |
| 2026-09-08 | 02:43 | 20:10 |
| 2026-12-21 | 05:50 | 17:11 |

## Exact locality bindings

The retained canonical catalog has revision
`catalog-geonames-ru-2026-09-08-5267d8bf5e48a158` and 3,266 `ru-ta` entries.
The helper matches only exact existing names or aliases within that subject;
it performs no transliteration, typo correction, population/admin-class ranking,
distance matching or district-wide expansion.

There are 36 unambiguous catalog bindings, of which 35 also pass September
parsing. Большие Кайбицы uses its already present exact Russian catalog alias;
the canonical display name is `Bol’shiye Kaybitsy`, not an invented alias.

Eight selector labels remain unresolved:

- Алексеевск: no exact catalog match. Its complete CSV contents uniquely equal
  the workbook's Алексеевское rows, but content equality is not evidence that
  the two geographic labels are interchangeable. No city ID is assigned.
- Болгар, Высокая Гора, Заинск, Камское Устье, Мензелинск, Муслюмово and
  Черемшан: each has two exact catalog matches in Tatarstan. Both candidate
  identities are retained, but `canonical_city_ids` stays empty. Additional
  first-party locality/address/district evidence is needed to disambiguate.

The selector says city or district, while the workbook explicitly labels A as
locality. This comparison establishes only the named locality, never all
settlements in its surrounding district or the republic. Bound catalog entries
use `Europe/Moscow`; unresolved bindings retain timezone `UNKNOWN` in the ledger
instead of attaching an unverified geographic identity.

## Morning-column semantics and first-party explanation

The workbook calls C `Завершение сухура` and D `Совершается в мечетях`.
The full HTML table groups both under morning prayer. Those headers alone
would not prove that C is onset rather than a precautionary fasting cutoff.
Additional first-party evidence was therefore checked:

1. `CONFIRMED_PUBLIC`: the current [DUM RT homepage](https://dumrt.ru/ru/)
   labels its daily field `Фaджр`. Its publicly linked
   [JavaScript](https://dumrt.ru/netcat_template/template/dumrt/js/main.js)
   function `fillPrayerMaket` assigns CSV `value[1]` to that daily field;
   `fillPrayerMaketMobile` uses the same column. The full table assigns
   `value[1]` to `sukhur_end` and `value[2]` to the separate mosque-performance
   column. This is public publisher-code evidence, not a claimed browser run.
2. `CONFIRMED_PUBLIC`: [DUM RT's May 10, 2023 fatwa](https://dumrt.ru/ru/help-info/fatwas/fatwas_29202.html)
   answers a question specifically about prayer after *suhur tamam* by applying
   the restriction after dawn, except the two sunnah units of morning prayer.
   This supports the publisher's use of the cutoff as dawn, rather than a
   separately documented earlier safety margin.
3. `CONFIRMED_PUBLIC`: its [February 22, 2023 fatwa](https://dumrt.ru/ru/help-info/fatwas/fatwas_28881.html)
   explicitly describes the organization's timetable calculation: morning
   onset uses the sun 18° below the horizon; Isha uses 15° under its stated
   preferred Hanafi opinion. This is first-party policy evidence, not a
   generic calculator selected by NamazTime.
4. `CONFIRMED_PUBLIC`: page 3 of the official [Умма, April 2020](https://dumrt.ru/netcat_files/561/749/Umma2020_04.pdf)
   states: «начало утренней молитвы – это начало поста». It describes a
   relative-night calculation during the white-night season and names
   Astrakhan as an example reference. This historical explanation does not
   resolve exact 2026 transition dates, rounding, or the May 5 day-offset issue.

Taken together, the current publisher's explicit field assignment and its own
explanations support mapping C to Fajr onset for the exact published table.
This conclusion does not rely on the general assumption that every source's
suhur cutoff equals Fajr. D remains source-only performance evidence, not an
implicit mosque iqamah. No Hijri date, Jumu'ah or mosque-local congregation
schedule is invented. A reproducible calculation profile is still `UNKNOWN`:
these pages do not specify the full current rounding, coordinates, Asr factor
and seasonal transition implementation required to regenerate all 2026 rows.

### Hash-bound semantics evidence

All following bytes were retained outside Git under the same artifact directory.
The first-party HTML/PDF fetches returned ordinary HTTP 200 without bypass.
The web fetch tool returned 410 for `fatwas_28881.html`; a normal direct HTTPS
fetch returned its complete public content, so that tool failure was not
treated as proof the source was inaccessible.

| Retained artifact | SHA-256 |
|---|---|
| `dumrt.html` | `eb86c45ac5b35a0c04b5e7356e3fb1c761020bd09105ceb6bd9eff8e447de489` |
| `dumrt-home-semantics.html` | `da5dd9ecb18157fe204cf90f71ebbc85d525df34cdb114daeb838401a5856181` |
| `dumrt-main.js` | `bac03d01f7d41388329b98a12da45ad8979cb0160b69ded943168209f2803293` |
| `dumrt-fatwa-28881.html` | `56d1a7a1eb24e8f8261a9b91ce4e26dc8ef6a9ed27dfdfc7e8a03769daf2cbe3` |
| `dumrt-fatwa-29202.html` | `153bc4f2184741990528614abd5e1384ce55a601f5e77fe4405a6a02649e5e88` |
| `dumrt-umma2020-04.pdf` | `02a0f9b8eeaef36ad4c8bd38c8c6e3cce00670002095f41abf474fb09202067c` |

The current first-party footer declares CC BY 4.0 availability. No separate
permission or partnership is asserted or required; substantial retained
artifacts nevertheless remain outside Git under the task's data boundary.
Year-labelled workbook publication and daily cache-query tokens are observed;
an exact update/correction SLA is `UNKNOWN`.

## Search trail and limits

The retained selector, all 44 actual CSV files, workbook ZIP/XML and canonical
catalog were inspected directly. Published JavaScript was followed from the
HTML rather than inferred from an aggregator widget.

Targeted searches included `site:dumrt.ru "Алексеевск" "время"`,
`site:dumrt.ru/ru "Алексеевск" "Алексеевское"`,
`site:dumrt.ru/ru "Алексеевское" "расписание"`,
`site:dumrt.ru "Высокая Гора" "время намазов"`, and
`site:dumrt.ru "Муслюмово" "время"`. They did not establish an explicit
selector-to-catalog disambiguation; unrelated locality news was not promoted
to timetable scope proof.

Semantic searches included `site:dumrt.ru "завершение сухура" "утреннего"`,
`site:dumrt.ru "имсак" "сухур"`,
`site:dumrt.ru/ru/ "утреннего намаза" "сухур"`, and
`site:dumrt.ru/ru/ "времени намазов"`. These surfaced the official Умма
explanation. Following the public [fatwa index](https://dumrt.ru/ru/help-info/fatwas/)
then exposed the two specific fatwas above, which were fetched and read.
Search snippets' crawl dates were not treated as publication/effective dates.
No organization was contacted; no authenticated transport was used.

## Reproduction and checks

The helper reads retained artifacts only and prints metadata to stdout. It
does not fetch, publish, mutate the parser, update the catalog or write raw rows.
Use the existing artifact directory, or an identically named retained copy:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/t049 -p test_dumrt_compare.py
PYTHONDONTWRITEBYTECODE=1 python3 research/t049/dumrt_compare.py --artifacts /home/andy/github.com/andy-ahmedov/namaztime-artifacts/t049-sources.w1vQaE --check-report research/t049/dumrt-localities.json
go test ./internal/providers/dumrt
make docs-check
```

[The comparison helper](dumrt_compare.py) independently checks selectors,
CSV structure, exact identities, workbook dates/shared strings and full-field
differences. [The Go harness](dumrt_parse_check.go) calls the production parser
and emits only hashes, counts, dates and failures. Its candidate JSON hash is
over `json.Marshal([]domain.CandidatePrayerDay)` without a trailing newline;
the field digest is compact JSON of sorted `[date, C, D, E, F, G, H, I, J]`
arrays, also without a trailing newline.

Eight comparison/binding test cases were demonstrated red then green. Four
additional tests cover independent workbook extraction/drift, preservation of
unrepresentable values and the synthetic public-Go-parser digest cross-check.
All 12 pass. The real-artifact run and reproducible ledger check are separate
from ordinary synthetic tests; no actual timetable corpus entered Git.

The prayer-times-provider skill required keeping source semantics, exact scope,
raw hashes and candidate evidence separate from qualification/publication; the
test-driven-development skill guided the comparison and ambiguity regressions.
No database migration, activation, signing or rollback operation occurs here;
removing these research artifacts does not alter an active schedule.
