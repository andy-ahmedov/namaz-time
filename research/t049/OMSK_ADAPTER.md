# T049 — Omsk public JSON parser

Research and local verification: 2026-09-08. This adapter is an unqualified
candidate normalizer, not an external endorsement or publication mechanism.

## Source and scope

`CONFIRMED_PUBLIC`: the authority's [prayer page](https://dum-omsk.ru/prayer-times.html)
identifies the DUM of Omsk and Omsk Oblast, explicitly limits the schedule to
Omsk city and states UTC+6. The page links its public JavaScript bundle, which
identifies the [JSON artifact](https://dum-omsk.ru/data/prayer-times.json).
Other oblast localities and the independent Siberian DUM at 55islam.ru are not
covered by this source.

The retained artifact contains 122 dated rows, 2026-06-01 through 2026-09-30,
and four monthly metadata records. The [eastern research ledger](regions-east.json)
records the ownership chain, accessible transport, monthly file hashes and
six visual sample comparisons against the authority's June/September images.
That ledger captures research-stage gaps; the parser gap is addressed here,
while qualification and operational integration remain separate.

`UNKNOWN`: published numerical Asr/Fajr/Isha parameters, high-latitude policy,
future-month release guarantee and operational update SLA. These unknowns do
not authorize calculation, interpolation, neighboring-city reuse or extending
the exact published table. External approval/partnership absence is not a
qualification blocker under ADR 0019.

## Interface and field semantics

[Implementation](../../internal/providers/omsk/json.go):

```go
omsk.ParseJSON(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error)
```

`ParserVersion = omsk-city-json/v1`, `SourceCity = Омск`,
`SourceTimezone = Asia/Omsk`. No `ParseConfig` is needed: this source-specific
parser rejects another city, and the JSON does not carry a timezone field.
The caller must bind the pinned city and IANA timezone in source provenance;
the returned day records do not themselves establish a timezone or authority.
An added JSON timezone field is unknown schema and fails closed.

| Published field | Candidate treatment |
| --- | --- |
| `fajr`, `sunrise`, `dhuhr`, `asr`, `maghrib`, `isha` | Corresponding explicit prayer-day fields, unchanged |
| `suhur` | Validate independently, including `suhur <= fajr`; retain in raw artifact only |
| Row `hijri` | Preserve explicit integer day and month text |
| Global `hijriYear` | Validate banner format, never infer a per-row year |
| Monthly files/dimensions/Hijri label/updated date | Validate metadata; never fetch from the parser |
| Iqamah, recommended Fajr, zenith, Jumu'ah | Not supplied or generated |

The source's global Hijri-year display spans a Gregorian range containing a
lunar-year rollover. Assigning it to every row would invent row-level evidence.
`HijriYear` therefore stays zero; `HijriDay` and `HijriMonth` come only from the
explicit row. Suhur never replaces Fajr. Fixed published Dhuhr is not silently
reclassified as mosque iqamah.

## Fail-closed contract

`PROPOSAL`: parser limits are 1 MiB UTF-8, 1–24 monthly records, at most 732
source rows, Gregorian years 2000–2100 and explicit requested coverage of
1–366 consecutive days. Leap day, month rollover and Gregorian-year rollover
are supported. An invalid artifact returns an error and no partial days.

The entire artifact is checked, including rows outside requested coverage:

- Duplicate members at every nesting level are rejected through
  `internal/strictjson`; malformed/trailing JSON is rejected.
- Exact case-sensitive key sets are required at root, month and row levels;
  missing, null, unknown and wrong-type fields fail closed.
- Month/date keys and metadata update dates must be canonical and valid.
  Every declared month must include all its Gregorian days. Rows cannot name
  undeclared months; requested coverage cannot contain a gap.
- Monthly file paths must match the observed same-month relative filename
  convention. Dimensions must be positive and bounded; they are not treated
  as fetched or verified image dimensions.
- Clocks must be ASCII `HH:MM` in the same local day, with strict
  Fajr–sunrise–Dhuhr–Asr–Maghrib–Isha order. Unrepresentable midnight crossings
  fail rather than being repaired.
- JSON member order cannot change normalized output. Hijri labels are bounded
  explicit text; they are not calculated from Gregorian dates.

The pure parser does not use network, device locale, system clock, database,
approval, signing or publication services. Retrieval metadata, raw/content
hashes, diffs, exact locality/timezone binding and machine-verifiable source
qualification remain caller responsibilities.

## Verification performed

The `test-driven-development` skill required a test-first acceptance contract.
The initial focused run failed on the intentional missing implementation,
including correct-field, leap-day/year-rollover and supported-order cases;
the same cases passed after implementation. All normal fixture values are
synthetic, not published prayer rows.

Passed:

```sh
go test ./internal/providers/omsk
go test -race -cover ./internal/providers/omsk ./internal/strictjson
go test ./internal/providers/...
make docs-check
```

Omsk statement coverage from the race/coverage run: 97.1%. Tests include root,
month, date and row duplicate keys; escaped duplicate keys; case drift;
missing/null/unknown fields; bad metadata; noncanonical/invalid clocks and
dates; month completeness; corruption outside selected coverage; deterministic
reordered objects; leap day; year rollover; and absent inferred fields.

The offline [retained-artifact test](../../internal/providers/omsk/local_artifact_test.go)
is opt-in and skips in normal CI. No source data are downloaded by a test.
Run it with `NAMAZTIME_OMSK_JSON` pointing to a retained file outside Git and
`NAMAZTIME_OMSK_SHA256` set to its independently recorded lowercase SHA-256:

```sh
go test ./internal/providers/omsk -run TestLocalRetainedArtifact -v
```

`CONFIRMED_PUBLIC`: the retained first-party artifact passed the opt-in local
check, reproducing all 122 rows and separately the 30-day September range.
This is public-source parsing verification, not Android runtime evidence.

```text
raw SHA-256:
23eefdcad7972713338ecf46a1611b5caba10c471a7bb2c8dfb98d483b686874
normalized 122-day JSON SHA-256 (json.Marshal of candidate days):
b5d7070d4274f321fd595f1c8795327ca9d29a1f2e95c5799355fbb19fc477b7
```

No substantial raw JSON, PDF, image or normalized real timetable was added to
Git. Qualification, activation/materialization, aggregate `make test`/`make lint`
and T049 plan updates belong to the coordinating task. This package introduces
no migration or persistent state; rollback is removal of this isolated adapter
before it is integrated.
