# T049: Volga and Ural source handoff

Research date: 2026-09-08. Evidence and queries are retained in
[the 20-subject ledger](regions-volga-ural.json). The exact subject codes and
Russian names match the canonical region map. There are 61 authority or
representation records and 31 source records; repeated national publishers
and historical representations are not 61 independent current choices.

`CONFIRMED_PUBLIC` describes inspected first-party evidence, not qualification.
`UNKNOWN` gaps do not introduce an external approval or partnership gate.
No source was activated or qualified by this research lane. Substantial raw
artifacts remain outside Git. The existing Ulyanovsk pilot was not changed.

## Implementation-ready or promising transports

- **Tatarstan:** the exact 44 locality CSV/workbook investigation and parser
  outcomes are in [DUMRT details](DUMRT_ADAPTER.md) and
  [the per-locality matrix](dumrt-localities.json). Forty-three September files
  parse; 36 locality names have unambiguous canonical bindings, with 35 in the
  intersection. Aktanysh has a malformed row; eight locality bindings remain
  unresolved. No republic-wide extension or annual-validity inference.
- **CDUM:** [the independent city matrix](cdum-localities.json) includes the
  observed current navigation, hashes, date comparisons and parser outcomes.
  Orenburg contains two visible annual sequences. The path named `Surgut`
  actually publishes Salekhard; its September sequence also fails validation.
  A filename is not geographic evidence.
- **Saratov:** current HTML, linked print HTML and public WordPress page 2348
  contain the same 30 September rows and 180 prayer values. The print heading
  explicitly names Saratov. The table does not print a year: qualification must
  additionally bind the API's `modified_gmt=2026-08-31T17:17:26` evidence and
  matching rendered content. A dynamic sidebar/copyright date alone is not
  sufficient. Required attribution is an active publisher hyperlink.
- **Nizhny Novgorod / Sergach:** separate annual 2026 PDFs carry explicit city
  and organization headers and prayer-onset labels. Each has clipped duplicate
  printing fragments after the authoritative monthly footer and a literal
  mixed-script `Cр` on May 13. The associated HTML has incorrect weekdays on all
  16 checked dates, although its 112 clock values match the PDF. Do not repair
  or qualify the HTML from that value agreement.
- **Bashkortostan:** official app-store links connect old `dumrb.ru` support and
  new `dumrb.com` privacy ownership. The site's public JavaScript uses
  `api.dumrb.com/Prayer/GetSupportedCities` and `GetByCity`. Sixty-three named
  locality descriptors exist; only Ufa's 30-row September response was checked.
  The meaning of separate `suhurDo`/`fajr` and `zaual`/`dhuhr` fields remains
  unresolved. Do not invent onset mappings or use its location calculator.
- **Orenburg:** the first-party newspaper's August 27, 2026 issue contains a
  current September Orenburg-city calendar on raster page 4. The publisher is
  Kargala Mahalla 3008, but that does not change the table's explicit city scope.
- **Izhevsk and Kurgan:** publisher-attributed September 2026 raster calendars
  were visually inspected. Full independent transcription is still needed.
- **Chelyabinsk:** the latest Hijri-month XLSX covers August 14–September 11,
  not the full month. Cached Excel time fractions contain seconds. Display
  rounding and suhur/onset semantics must be reproduced before normalization.

## Important unavailable or unresolved cases

Perm's same-site prayer iframe obtains generic Aladhan calculator output; it
does not become an official schedule merely through the authority's branding.
Tyumen's current raster repeats day 9 where day 21 would normally appear; do not
repair it. Saransk's accessible image is August 2026 and expired; another
Mordovia prayer page is explicitly 2016. Yugra has no reproduced current
applicable timetable in the inspected transports. Mari El's current indexed
page identifies four locality calendars, but ordinary origin retrieval failed.

Independent current organizations and historical affiliations are distinguished
in the ledger, including Penza's several real organizations. A shared acronym,
legacy domain, template footer or former national affiliation does not create
or eliminate an independent current authority.

## Verification

The exact 20-code set, Russian names, required source fields, evidence-label
vocabulary and IANA timezone validity were checked. `make docs-check` passed
for the ledger; rerun it after subsequent handoff-document or adapter changes.
Calendar/parser validation is stated per source, not generalized to a subject.
