# CDUM city HTML adapter — T049

Research date: 2026-09-08. See the independent authority/scope ledger in
[regions-west.json](regions-west.json). No source is qualified or published by
this parser. The independently captured additional-city matrix is
[cdum-localities.json](cdum-localities.json).

## Confirmed public transport

`CONFIRMED_PUBLIC`: original HTTPS GETs returned visible annual calendars at
[Moscow](https://cdum.ru/time-namaz/Moskva/index.php) and
[Saint Petersburg](https://cdum.ru/time-namaz/Spb.php). Each has a city-specific
navigation link explicitly labelled 2026, `div#text_block`, `h1#pagetitle`,
twelve named monthly tables, and seven columns: date, Fajr, sunrise, Dhuhr,
Asr, Maghrib, Isha. The 365 date/weekday rows match Gregorian 2026. Both retain
one standalone Office-export BOM after the final table/formatting paragraphs.
Other CDUM cities are not assumed to share this transport: Ufa uses images.

Retained public bytes are outside Git. Filesystem capture completion was
2026-09-08 01:11:12/13 Europe/Moscow; these timestamps are not source revisions.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Moscow HTML | 1079042 | `a33d327af16c8242d4d285cdd85c3eaa9055809998f6e6d7f8cd47d057c3864f` |
| Saint Petersburg HTML | 1096694 | `3369513ad18e035e8be2c0690778a79536de6aa9d7e7cdb1585fd86eda993c8d` |

## Implemented parser boundary

`PROPOSAL` implemented by [html.go](../../internal/providers/cdum/html.go):
`ParseHTML(raw []byte, locality string, coverage domain.DateRange)` returns
candidate days or an error, never approval/publication. `ParserVersion` is
`cdum-city-html/v1`. It accepts only explicitly researched Russian city labels;
the caller must separately bind exact catalog IDs, authority, timezone,
retrieval metadata, raw/normalized hashes and qualification.

The explicit coverage must be contiguous complete months within one year.
The parser validates all twelve month headings, column labels, day counts,
date order, weekdays and clock syntax, even outside that requested range.
Every requested day must pass strict same-day time ordering. Any error returns
no partial output. Comments, noscript, templates and script text cannot supply
year or timetable evidence. Unknown table/inline structure, hidden/executable
attributes, markers, duplicates, missing fields, oversized input/depth and
invalid UTF-8 fail closed. Raw input is bounded to 4 MiB and 2 Mi codepoints;
fields are bounded to 128 codepoints. Unknown calculation parameters stay
unknown; no recommended prayer time, iqamah or Jumuah is invented.
Each cell must retain exactly one source paragraph; separate paragraphs cannot
be concatenated into a valid-looking clock. Inline styles are restricted to
the observed static export declarations, including bounded positive widths.
This is not a browser renderer: external stylesheet/currentness evidence
remains part of source qualification rather than a CSS execution claim.

`CONFIRMED_PUBLIC`: both actual annual artifacts contain unrepresentable
after-midnight Isha values in the same civil-date row:

- Moscow: May 8 (00:17), August 4 (00:17), August 5 (00:01).
- Saint Petersburg: April 22–24 (00:09, 00:22, 00:46), August 18–21
  (00:44, 00:24, 00:11, 00:01).

Both annual requests therefore reject, not silently omit dates. Explicit
September 2026 requests return 30 days and Q4 requests return 92 days. Unknown
seasonal calculation methodology is not reverse-engineered from those rows.
Daily jumps/diffs and source currentness remain qualification-lane concerns;
a successful parser result alone does not qualify a source.

## Additional current navigation checks

`CONFIRMED_PUBLIC`: eighteen further advertised city URLs were independently
captured with ordinary HTTPS GETs. Their exact URLs, raw hashes, response
metadata, current city/year evidence, all-month counts, anomalous dates and
small September8 comparisons are in the locality matrix. Legacy filenames
are never geographic/year evidence: `Surgut2015.php` currently identifies
**Salekhard**, not Surgut. Fresh Khabarovsk bytes explicitly show 2026 even
when the web search cache returns an older 2025 page.

`PROPOSAL`: sixteen of those city/path pairs pass the researched markup
schema and are supported. Together with Moscow and Saint Petersburg, these
are eighteen exact bindings, not eighteen whole-subject coverage claims.
Retained-file checks establish these requested-range outcomes:

| Explicit cities | September / Q4 | Annual 2026 |
| --- | --- | --- |
| Astrakhan, Rostov-on-Don, Kirov, Penza, Cheboksary | 30 / 92 days | 365 days |
| Kazan, Yekaterinburg, Chelyabinsk, Izhevsk, Yoshkar-Ola, Kurgan, Perm, Samara, Ulyanovsk, Khabarovsk | 30 / 92 days | Reject anomalous civil-date ordering |
| Salekhard | Reject September6 / December19 | Reject April5 |
| Orenburg | Unsupported duplicated annual source | Unsupported duplicated annual source |
| Volgograd | Unsupported unexplained Fajr markers | Unsupported unexplained Fajr markers |

Kazan's annual rejection is August8: Fajr23:59 precedes sunrise04:01 in the
same row. No previous-day offset is guessed. Yoshkar-Ola likewise has a
late-evening Fajr on May2. Orenburg contains **two visible twelve-month
sequences**, not commented legacy data; selecting the first half is forbidden.
Volgograd contains thirteen unexplained Fajr asterisks, first on June15;
stripping those markers or silently selecting convenient months is forbidden.
Ufa's image transport is outside this HTML adapter.

## Raw-attribute ambiguity boundary

The installed HTML tokenizer discards duplicate attributes before building the
DOM. A quote-aware [raw-tag check](../../internal/stricthtml/attributes.go)
therefore runs first. It distinguishes actual attribute names from strings
inside quoted values, including embedded `>` characters and mixed-case names.

`PROPOSAL` implemented: attribute errors are marked only in an internal copy
used to build the DOM. The existing calendar/evidence traversal rejects any
marked timetable node, its ancestors, or the explicit city/year link and its
ancestors. Unrelated navigation and ignored templates are not calendar evidence;
their duplicate attributes do not disqualify the timetable. This distinction is
structural, not a contacts-URL or fixture-text exception. No source value is
repaired or altered.

The reserved `data-namaztime-raw-attribute-error` attribute is rejected if the
original source actually supplies it, including mixed-case, boolean and
self-closing forms. The same text inside an ordinary quoted value is not an
attribute and does not cause a collision. The source byte slice and raw hash
remain untouched; the annotated parse copy must never be retained as the raw
artifact or used as its provenance hash.

The original 4-MiB / 2-Mi-codepoint input limits and 100,000-node / 64-depth
DOM limits remain unchanged. A separate 8-MiB cap bounds internal marker growth.
Clock normalization, explicit city bindings, effective ranges and unsupported
source outcomes are unchanged.

## Verification

Normal CI uses generated synthetic constant-time fixtures, not real calendars.
The initial annual synthetic test failed against a compiled unimplemented API,
then passed after implementation. Retained-artifact verification exposed the
trailing Office BOM; its focused regression failed before the bounded handling
was added. A hidden-year-label regression likewise failed before the visibility
check was added. Split-paragraph clocks and previously unhandled hiding styles
also had failing regressions before their fail-closed fixes. Additional exact
city-binding tests failed with unsupported-locality errors before the
independently researched bindings were added.

The raw-attribute follow-up was reverified against baseline `5a9eb73`, not an
earlier passing state. New adversarial tests first reproduced accepted duplicate
attributes in actual calendar cells, the calendar container and city/year links
when their text matched former exemptions. Boolean/self-closing marker
collisions also reproduced before the fix. The general annotation-copy approach
then passed those cases, arbitrary unrelated-navigation/template cases,
quote-aware checks and input-byte immutability checks. No production exception
depends on fixture contents or navigation URLs.

Executed locally:

- `go test ./internal/providers/cdum -count=1`: passed; optional real-file tests
  skip when their environment variables are absent.
- `go test -race ./internal/providers/cdum -count=1`: passed.
- `go test ./internal/providers/cdum -run '^$' -fuzz FuzzParseHTML -fuzztime=2s -parallel=2`:
  passed (multiple short runs; no crashes).
- `go vet ./internal/providers/cdum`: passed.
- Opt-in retained-file checks: Moscow and Saint Petersburg September/Q4
  passed; both expected annual midnight rejections passed.
- Opt-in eighteen-locality matrix: all expected complete-range successes and
  exact-date/unsupported-source rejections passed.

For retained-file checks, set `T049_CDUM_MOSCOW_HTML` and/or
`T049_CDUM_SPB_HTML` to independently retained files outside Git, and the
matching `T049_CDUM_MOSCOW_SHA256` / `T049_CDUM_SPB_SHA256` capture hash. Then run
`go test ./internal/providers/cdum -run TestRetainedPublicArtifacts -v -count=1`.
Kazan can independently use `T049_CDUM_KAZAN_HTML` and
`T049_CDUM_KAZAN_SHA256` with the same command.
The test performs no network request and verifies raw hashes before parsing.
The inspected September 8 six-event samples are small sanitized comparisons,
not a retained source dataset.

For the additional matrix, set `T049_CDUM_LOCALITIES_DIR` to the directory
containing the filenames recorded in `cdum-localities.json`, then run
`go test ./internal/providers/cdum -run TestRetainedResearchedLocalities -v -count=1`.
The test verifies each recorded raw hash and compares exact requested-range
outcomes against independent source inspection, without network access.

Root owns dependency pinning, shared registry/qualification integration,
`PLANS.md`, full repository/device gates and any activation decision. The
adapter adds no network, database, signing or Android behavior. Rollback is to
stop selecting the parser version; no persistence migration is introduced.
