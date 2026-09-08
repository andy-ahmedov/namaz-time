# Sochi public workbook adapter

Checked on 2026-09-08. This records source evidence, exact-city mapping and
validated parsing. It is not a qualification, publication, external endorsement
or a new calculation profile. Qualification remains separate under
[ADR 0019](../../docs/adr/0019-public-first-party-source-qualification.md).

## First-party artifact and scope

`CONFIRMED_PUBLIC`: the [community's own page](https://www.xn--h1aaoemc7b6c.xn--p1ai/index.html)
identifies Местная религиозная организация мусульман города Сочи Краснодарского
края, formerly «Ясин». It publishes ИНН 2317027238, КПП 232001001 and
ОГРН 1032335035985, describes direct DUM RF canonical/administrative membership,
and attributes the Greater Sochi timetable to DUM RF specialists. The workbook
title itself specifies the city of Sochi, Krasnodar Krai.

`CONFIRMED_PUBLIC`: its actual timetable loader requests
[namaz/sochi2026.xlsx](https://www.xn--h1aaoemc7b6c.xn--p1ai/namaz/sochi2026.xlsx).
An ordinary HTTPS GET returned 200 and the complete workbook. The separate
download button points to a root-level filename; that wrong route and the
page's static unavailable-message container do not establish that the real
artifact is unavailable. The adapter never consumes the page's JavaScript
fallback times.

| Retained evidence | Value |
| --- | --- |
| Raw XLSX size | 37,018 bytes |
| Raw XLSX SHA-256 | `91f57f3a1658788481b64a9f219602d04f6f8b4b0dfa86557daa546706d573ee` |
| HTTP Date | `2026-09-07T23:05:44Z` (2026-09-08 in Europe/Moscow) |
| Last-Modified | `2026-06-21T18:26:09Z` |
| ETag | `"6a382cc1-909a"` |
| Content-Type | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| Own-page SHA-256 | `3c5fad6142e51e864061350127c2be4122aad10ede8ce6c067aa98e37b1c73db` |
| Effective rows | 2026-01-01 through 2026-12-31, 365 dates |

Raw XLSX, page and HTTP headers are retained outside Git under
`/home/andy/github.com/andy-ahmedov/namaztime-artifacts/t049-sochi.LbOpAp/`, as
`sochi-2026.xlsx`, `sochi-2026.headers`, `sochi-index.html` and
`sochi-index.headers`. The page has a general rights-reserved notice; only
sanitized metadata, hashes, labels and a few seasonal values are retained here.
Annual naming is observed; an authoritative refresh commitment is `UNKNOWN`.

`CONFIRMED_PUBLIC`: in the retained `russia-cities-2026-09-08.json` catalog,
Сочи has exactly one name/alias match within `ru-kda`:
`city-08753d164833c8eac76ccbd01d634827`, `geonames:491422`, settlement type
`PPLA2`, coordinates 43.59699/39.72477, timezone `Europe/Moscow`.
The source city title agrees with that identity. This evidence does not create
bindings for other Greater Sochi settlements or all of Krasnodar Krai.

`UNKNOWN`: the inspected workbook/page does not literally publish an IANA
timezone or UTC offset. `Europe/Moscow` is the exact canonical city's timezone,
not a source-authored string. The parser preserves city-local clocks unchanged;
the qualification caller must retain the city/timezone evidence and binding.
No device timezone, coordinate-based time adjustment or neighboring-city rule
is used.

## Exact normalization contract

`PROPOSAL`: [ParseXLSX](../../internal/providers/sochi/xlsx.go) is a pure,
network-free parser:

```go
ParseXLSX(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error)
// ParserVersion: "sochi-city-xlsx/v1"
```

Coverage must contain canonical dates in a single year from 2000 through 2100.
The input must contain every date of that full Gregorian year before any
requested subset is returned. The year comes from the actual Excel serials,
not from the URL or caller alone. A bad unselected January row therefore
rejects a September request. Leap-year behavior has synthetic coverage; the
retained public artifact verifies 2026 only.

`CONFIRMED_PUBLIC`: the sole worksheet contains one geographic title row, one
header row and 365 daily rows. Column A uses integral Excel 1900-epoch serials;
columns B:G use shared-string canonical `HH:mm` values.

| Published column | Candidate field |
| --- | --- |
| A: Дата | `Date` |
| B: Фаджр | `Fajr` |
| C: Шурук | `Sunrise` |
| D: Зухр | `Dhuhr` |
| E: Аср | `Asr` |
| F: Магриб | `Maghrib` |
| G: Иша | `Isha` |

`CONFIRMED_PUBLIC`: the fixed notes in column I publish coordinates
43.587/39.72, elevation 0 m, standard Asr, Fajr 18°, Isha 17°, sunrise −5 min,
Dhuhr +5 min and Maghrib +5 min. These cells are checked as known source
metadata; the parser does **not** recalculate or apply the adjustments again.
A changed note requires review, not silent reuse of this parser version.
The notes alone are not a qualified calculation profile. Published
high-latitude and special seasonal policy explanations are `UNKNOWN`.

`PROPOSAL`: every daily row must have six strictly ordered local times. No day
offset, iqamah, recommended Fajr, congregation time, Jumu'ah, Hijri date or
optional prayer field is invented. No result is returned on any error.

## Archive and schema boundaries

`PROPOSAL`: standard Go ZIP/XML libraries are used without extraction or
external relationship resolution. Limits are 2 MiB compressed artifact,
1 MiB per inflated member, 4 MiB total inflated bytes, ten exact known archive
members, XML depth 32, 20,000 nodes per part and bounded attributes/text.

The parser rejects unknown/duplicate/missing package members, unsafe paths,
symlinks, encryption, unsupported compression, CRC/size mismatches, extra
worksheets, hidden rows/columns, 1904 dates, external/changed relationships,
DTD/directives, non-XML processing instructions, malformed/duplicate XML
attributes, changed namespaces, duplicate shared strings, rich strings,
formulas, extra data columns, date gaps/duplicates, noncanonical dates/times,
changed geographic/header/policy cells and inconsistent reference counts.

Known workbook/window/print metadata is bounded and allowlisted. An editor's
absolute-path metadata is tolerated only in its known inert XML shape; it is
never resolved, exposed or retained in fixtures. Styles, theme and document
properties have exact package identities and bounded well-formed XML but are
not used to interpret timetable values. This is deliberately a source-specific
parser, not a general-purpose OOXML schema validator.

## Reproducible verification

Synthetic fixtures are authored in
[xlsx_test.go](../../internal/providers/sochi/xlsx_test.go); they do not copy
real workbook bytes or published annual rows. Test-first evidence included
failing full-year/September positive tests against an unimplemented parser,
then additional red/green cases for real Excel compatibility metadata,
unknown metadata containers, duplicate strings and hidden zero-height rows.
The separate ZIP integrity cases exercise encrypted/symlink/unsafe-path,
CRC, size and compression rejection.

The independent reference uses Python's ZIP/XML libraries, not production Go
parser helpers. For all 365 rows, serialize compact UTF-8 JSON as
`[[ISO-date,[Fajr,Sunrise,Dhuhr,Asr,Maghrib,Isha]],...]` in calendar order.
Its SHA-256 is
`3d2de35b5a629d0c8a4ea54cbe8c1ee94e2a0ea1df62c5406635ded8194ebfd4`.
The Go output reproduces that complete 365-date/2,190-time-field matrix.
The separate full candidate JSON SHA-256 is
`b9a6bcc2179732f2e1e8a22861aa9e91ceaed687f06355b15d733c59b123b9fb`.
Explicit September returns exactly the same 30 rows as the annual subset.

Small first-party seasonal checks (Fajr / Isha): 2026-01-01 06:11 / 18:33;
2026-06-21 02:17 / 22:17; 2026-09-08 04:13 / 20:18;
2026-12-31 06:11 / 18:32. Complete source rows are not retained in Git.

```bash
go test ./internal/providers/sochi
go test -race -cover ./internal/providers/sochi
go vet ./internal/providers/sochi
NAMAZTIME_SOCHI_XLSX=/home/andy/github.com/andy-ahmedov/namaztime-artifacts/t049-sochi.LbOpAp/sochi-2026.xlsx \
  go test ./internal/providers/sochi -run TestLocalRetainedArtifact2026 -count=1 -v
```

The opt-in test is offline, pins the independently recorded raw/matrix hashes
and skips by default when the real workbook is absent. Recompute the reference
without writing or printing the source matrix:

```bash
python3 - /path/to/retained/sochi-2026.xlsx <<'PY'
import hashlib, json, sys
import xml.etree.ElementTree as ET
from datetime import date, timedelta
from zipfile import ZipFile

ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with ZipFile(sys.argv[1]) as archive:
    strings = [item.find('s:t', ns).text for item in
               ET.fromstring(archive.read('xl/sharedStrings.xml'))]
    sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
    matrix = []
    for row in sheet.find('s:sheetData', ns)[2:]:
        cells = {cell.attrib['r'].rstrip('0123456789'): cell for cell in row}
        serial = int(cells['A'].find('s:v', ns).text)
        day = date(1899, 12, 30) + timedelta(days=serial)
        times = [strings[int(cells[column].find('s:v', ns).text)]
                 for column in 'BCDEFG']
        assert day == date(2026, 1, 1) + timedelta(days=len(matrix))
        assert all(a < b for a, b in zip(times, times[1:]))
        matrix.append([day.isoformat(), times])
    assert len(matrix) == 365
    encoded = json.dumps(matrix, ensure_ascii=False, separators=(',', ':')).encode()
    print(len(matrix), hashlib.sha256(encoded).hexdigest())
PY
```

Checks run on this slice: synthetic package tests PASS; race/coverage PASS
(87.1% statements); focused `go vet` PASS; opt-in retained full-year/September
comparison PASS; independent Python reference PASS; `gofmt` clean;
`make docs-check` PASS. The default offline test skip is intentional, not a
claim that a real-artifact check ran in normal CI.

No registry activation, signature, live fetcher, database migration or TV code
is part of this slice. Rollback is removal of this currently unregistered
package. Aggregate repository test/lint gates are coordinated by the root
agent after concurrent registry and Android changes settle; they are not
claimed by this parser handoff.
