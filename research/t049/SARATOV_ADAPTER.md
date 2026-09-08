# T049: Saratov-city HTML adapter

Research date: 2026-09-08. The pure parser is implemented and independently
reproduces the current 30-day September table. This is normalization and
comparison evidence, **not source qualification, endorsement or activation**.

## Ownership, scope and currentness

`CONFIRMED_PUBLIC`: the [publisher](https://dumso.ru/) identifies itself as
Централизованная религиозная организация «Духовное управление мусульман
Саратовской области». Its [monthly page](https://dumso.ru/raspisanie) has an
explicit Saratov-city title on the `/raspisanie` navigation link. The linked
[print version](https://dumso.ru/print/namaz-time.html) independently names
Saratov city in its heading. This is not a Saratov-oblast timetable.

The sole exact `Саратов` match in the current RU-SAR canonical catalog is
`city-b186fd91bf7761113bf427894d18f605`, `geonames:498677`, timezone
`Europe/Saratov`. Catalog revision:
`catalog-geonames-ru-2026-09-08-5267d8bf5e48a158`. The IANA identifier comes from
this catalog binding; it is not literally printed by the Muslim publisher.
No Engels, neighboring locality or regional-capital fallback is implied.

`CONFIRMED_PUBLIC`: the prayer table and printable heading do **not** print a
Gregorian year. The main page's WordPress sidebar says September 2026, but that
is context, not independently sufficient timetable currentness. Stronger
composite evidence is available from the page's explicitly linked
[public WordPress metadata](https://dumso.ru/wp-json/wp/v2/pages/2348):

- page ID `2348`, slug `raspisanie`, type `page`, status `publish`;
- canonical page URL exactly `https://dumso.ru/raspisanie`;
- `modified_gmt=2026-08-31T17:17:26`;
- its rendered content has the same complete timetable as the main and print
  HTML: all header/data cells agree after whitespace normalization;
- print response `Last-Modified: 2026-08-31T17:19:25 GMT` corroborates that date.

The original page creation date is 2009, not the timetable's current year.
Qualification must bind the exact metadata, main and print artifact hashes and
their full content comparison. A changed table/hash with old metadata must
require a new qualification decision. Parser success plus a dynamic footer or
sidebar year must never auto-admit a replacement table.

## Retained public captures

All captures used ordinary unauthenticated HTTPS GET, status 200. Substantial
raw HTML/JSON and response headers remain outside Git in
`/home/andy/github.com/andy-ahmedov/namaztime-artifacts/t049-sources.w1vQaE`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `saratov-raspisanie.html` | 49,724 | `85ee2d5a8813b47403af55aee92afd1bbcdc536aa3cfb36864548ce66fec6d0d` |
| `saratov-print.html` | 7,780 | `4f94b2a5dfcab1e406c4d870732da65b021af27d90b9f26bcf26461a18933564` |
| `saratov-page2348.json` | 11,796 | `b6c08e8d648189ac9988aa279f836852d18ad79522b94bbc82463dd3d315c3f5` |

The similarly named `saratov.html` in that external folder is a homepage
capture, **not** the monthly parser input. Main-table capture response date is
`2026-09-07T23:42:46Z`; research date is September 8 in the workspace timezone.
Capture metadata and qualification remain separate from the parser.

## Parser contract

Implementation: [html.go](../../internal/providers/saratov/html.go).

```go
ParseHTML(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error)
```

Version: `saratov-city-html/v1`. This version accepts only a canonical date
range within **2026-09-01 through 2026-09-30**. Every one of the 30 source rows
is validated before a smaller requested range is returned. Future months or
years require new researched evidence and a reviewed parser version.

The main HTML must contain unique publisher, exact canonical URL, explicit
Saratov-city monthly link, page-2348 metadata link, expected month/year context
and the complete observed `table.namaz_time` structure. The nine columns are
Gregorian day, Russian weekday, Hijri day, Fajr, sunrise, Dhuhr, Asr, Maghrib and
Isha. The observed Hijri header and 20–30 / 1–19 sequence are checked as source
consistency evidence; no Hijri year or calculation is invented.

Only observed minute-clock shapes with `.` or `:` separators are accepted;
single-digit hours are normalized by zero-padding. Seconds, unknown markers,
invalid date/weekday, duplicates, missing/spanning cells, unknown footnotes,
nonascending clocks and midnight crossings fail closed. Standalone print HTML
or API-rendered fragments lack main-page context and are rejected.

Raw token nesting is checked before DOM repair. Duplicate raw attributes are
checked with a quote-aware scan because the installed HTML tokenizer discards
them. Unknown visibility/executable attributes, foreign namespaces and duplicate
evidence-container IDs are rejected. Input, field, node and depth limits are
bounded. Properly delimited comments, scripts and templates are not evidence.

## Prayer semantics and attribution

The publisher separately states a cathedral-mosque Dhuhr azan at 13:15 and
congregational Fajr 45 minutes after onset. These notices are checked for drift,
but never replace the table's six fields or create mosque iqamah/Jumu'ah rules.
The returned candidates contain only the date and six onset/sunrise fields.

Astronomical method, Asr factor, Fajr/Isha angles, seasonal policy and rounding
parameters remain `UNKNOWN`; this is an exact-table adapter, not a calculation
profile. The table is monthly; replacement SLA and future coverage are unknown.

The publisher requires an active source hyperlink when its material is used.
Publication metadata must retain authority attribution and that hyperlink.
No separate human approval/partnership is required for the public-source path;
this does not grant unrestricted redistribution rights for the raw artifacts.

## Independent comparisons and checks

[The opt-in reproduction](../../internal/providers/saratov/local_artifact_test.go)
hash-checks all three retained inputs. Its separate regex extraction of the
already hash-pinned print table is compared against the production DOM parser.
An independent Python/BeautifulSoup extraction produced the same full matrix:

- 30 exact dates, 180 prayer fields, zero differences;
- all main / print / API header and data cells match;
- all 30 weekdays and six-clock within-day ordering are validated;
- matrix encoding: one UTF-8 line per date,
  `YYYY-MM-DD|Fajr|Sunrise|Dhuhr|Asr|Maghrib|Isha` plus LF;
- 1,410 matrix bytes, SHA-256
  `275824c832aaaeb838189232769eeb2c185cdd3a5c15ba16d486c835e9163b3f`.

Sanitized samples in that column order:

| Date | Fajr | Sunrise | Dhuhr | Asr | Maghrib | Isha |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-09-01 | 04:27 | 06:10 | 13:03 | 17:34 | 19:47 | 21:27 |
| 2026-09-08 | 04:41 | 06:21 | 13:01 | 17:20 | 19:32 | 21:10 |
| 2026-09-30 | 05:31 | 06:53 | 12:54 | 16:38 | 18:42 | 20:16 |

Reproduce without network access:

```bash
go test ./internal/providers/saratov
T049_SARATOV_ARTIFACT_DIR=/home/andy/github.com/andy-ahmedov/namaztime-artifacts/t049-sources.w1vQaE go test ./internal/providers/saratov -run TestRetainedPublicMonth -v
```

Observed checks: synthetic contract tests first failed against the unimplemented
parser, then passed. Duplicate attributes and later ambiguity mutations each
reproduced a red-to-green regression. Retained month/subset comparisons passed.
`go test -race` passed with 91.7% statement coverage; `go vet` and
`go tool staticcheck` passed for this package. Repository-wide integration gates,
qualification, signing and activation belong to the coordinator's T049 slice.

Rollback has no storage migration: removing the adapter stops new parsing. It
must not delete or invalidate a previously qualified last-known-good snapshot.
