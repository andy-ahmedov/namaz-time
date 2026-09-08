# DUM KBR annual PDF parser

Checked 2026-09-08. This is source research and deterministic normalization,
not an external endorsement or a completed source-qualification decision.
The implementation is [text.go](../../internal/providers/kbr/text.go);
the evidence inventory is [regions-south.json](regions-south.json).

## Primary source and scope

`CONFIRMED_PUBLIC`: the [DUM KBR homepage](https://www.kbrdum.ru/home)
explicitly links its 2026 calendar to the
[public cloud share](https://cloud.mail.ru/public/3dJj/wg5Wr2Dge).
Its `2026kbr.pdf` contains twelve dated month pages. Every title explicitly
states `2026 г. ПО КБР`, establishing republic-wide applicability rather than
an inferred Nalchik-capital fallback. The publication names DUM KBR.

The public share permits normal downloading without a password. Its public
page exposes a temporary download-dispatcher URL. Resolve that public link
during capture; do not persist temporary transport tokens or bypass access
controls. Keep the stable share URL and the linking authority page as the
provenance chain. A publicly accessible source does not require a separate
permission letter under ADR 0019. No redistribution licence for the complete
PDF was verified; raw PDF, extracted text and full reference remain outside Git.

`UNKNOWN`: published calculation method, Asr convention, Fajr/Isha parameters,
rounding and future refresh cadence. None is invented when importing exact
published rows. `Europe/Moscow` is the geographic IANA binding to verify in the
coordinator, not an identifier printed by the PDF. No iqamah or timed Jumu'ah
session is supplied by this parser.

## Deterministic interface

```go
const ParserVersion = "kbr-annual-pdf-text/v1"

func ParseText(rawExtractedText []byte, coverage domain.DateRange) (
    []domain.CandidatePrayerDay, error,
)
```

The parser accepts the unedited UTF-8 extraction of all twelve pages, validates
all 365 days of Gregorian 2026, then returns the requested inclusive range
within that year. Even a September request rejects a malformed January row.
It does not read the network, calculate times, set a timezone, qualify a source,
publish, activate, or replace last-known-good data.

Expected extraction order per page:

1. Authority heading and printed authority-site line.
2. Uppercase month, explicit 2026 year and explicit KBR scope.
3. Month label followed by six ordered prayer labels and their descriptions.
4. Every expected day number, Russian weekday and six exact `HH:mm` fields.
5. Repeated month/prayer-label footer.

Whitespace and page wrapping are insignificant. The observed extraction's
unusual word spacing in the authority heading is pinned explicitly; the parser
does not silently repair names. Unknown headings, swapped columns, wrong
weekdays, missing/duplicate/reordered days, February 29, invalid times, unknown
footnotes, extra/truncated content and unsupported midnight crossings fail
closed with no partial result. Year 2027 needs a newly verified source/parser
revision, not automatic date rollover of the 2026 timetable.

The coordinator must bind original PDF hash, extraction hash, extractor
version/options, parser version, normalized candidate hash, exact scope,
effective range, IANA timezone, validation/diff and qualification decision.
Parser success alone is not qualification. Day-to-day/source-revision deltas
remain a coordinator validation/diff responsibility.

## Reproducible extraction

`CONFIRMED_PUBLIC`: normal public retrieval and local parsing were performed;
this label does not assert controlled-device runtime behavior.

`pdftotext` is not installed in the checked environment. The reproduced
converter is PyMuPDF **1.28.2**, installed only in a temporary directory,
not added to `go.mod` or the application's runtime dependencies. Its pinned
operation is `page.get_text("text", sort=False)`, joining the twelve page
strings with exactly one form-feed (`\f`), then UTF-8 encoding without any
other trimming or edits.

Given a captured primary PDF and explicitly chosen output path outside Git:

```bash
kbr_extractor_dir=$(mktemp -d)
python3 -m pip install --target "$kbr_extractor_dir/python" pymupdf==1.28.2
PYTHONPATH="$kbr_extractor_dir/python" python3 - "$NAMAZTIME_KBR_PDF" "$NAMAZTIME_KBR_TEXT" <<'PY'
import pathlib
import sys
import pymupdf

assert pymupdf.VersionBind == "1.28.2"
with pymupdf.open(sys.argv[1]) as document:
    assert len(document) == 12
    text = "\f".join(page.get_text("text", sort=False) for page in document)
pathlib.Path(sys.argv[2]).write_bytes(text.encode("utf-8"))
PY
```

This command is a local artifact-conversion example, not an automatic website
fetcher or production installation. Pin and review deployment packaging
separately if the coordinator uses this extractor operationally.

Captured artifact identities on 2026-09-08:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Primary cloud PDF | 164268 | `9c60359cf7cefe71890c56c9348d9fe955c969888b26e469868ba7c3230b5264` |
| Exact extraction described above | 25430 | `8affa8876414d5d3d7a5fcd011cb83491e09d7b49adf593f6de57fa207b345a9` |
| Separate full-date reference JSON | 43072 | `e7a4b41fb6f037ae66fd03cacb712e9ae7f17f1b4b6f2703a15a393162c91918` |

The reference was produced by a separate Python per-page regular-expression
normalizer, not the Go parser. Its JSON is an ordered array of all 365 objects
with `date,fajr,sunrise,dhuhr,asr,maghrib,isha`, compact UTF-8 serialization and
one final newline. It is not a claimed independent human transcription.
January and September PDF pages were also visually inspected; all six
September 8 values match the authority's separately retrieved current homepage.

An additional [direct PDF](https://dinri-duneiri.ru/azan/2026.pdf) has different
raw bytes but exactly matching 365 date/weekday/six-time rows. `UNKNOWN`: that
hosting domain's first-party affiliation. It is only an independent content
cross-check, not an approved alias or replacement for the authority-linked
primary cloud source.

## Verification

`PROPOSAL`: ordinary CI uses only an unmistakably synthetic annual fixture
generated in the tests, with artificial repeated times. It never fetches a
website. Tests cover complete/partial ranges, deterministic golden fields,
input immutability and the strict rejection cases above.

Test-first evidence: a compiling stub failed the three successful-parse tests
with `parser not implemented`; implementing the parser made the suite pass.
No substantial real timetable was committed as a test fixture.

The opt-in full-artifact test requires `NAMAZTIME_KBR_PDF`,
`NAMAZTIME_KBR_TEXT`, `NAMAZTIME_KBR_REFERENCE` and each corresponding
`_SHA256` environment variable. All three files must be retained outside Git;
the hashes must come from independently recorded capture metadata, not be
computed from whatever happens to be on disk inside the test command.

```bash
go test ./internal/providers/kbr -run TestLocalRetainedAnnualArtifact -count=1 -v
```

With those variables configured, the test verifies all three artifact hashes
and compares every field of all 365 normalized days to the separate reference.
Without them it reports an explicit skip; it never downloads a substitute.

Actually run for this implementation slice:

- `go test ./internal/providers/kbr` — pass after the recorded initial red run.
- Full retained primary PDF/text/reference check — pass, all 365 days equal.
- `go test ./internal/providers/...` — pass.
- `go vet ./internal/providers/kbr` — pass.
- `go tool staticcheck ./internal/providers/kbr` — pass.
- `go test -race ./internal/providers/kbr` — pass.
- `make docs-check` — pass, including this document.
- Opt-in check with a deliberately incorrect text hash — expected rejection
  before parsing; the correctly bound full comparison passed separately.

Broad repository/Android/database/signing gates belong to
the coordinating T049 implementation; this package does not replace them.
There is no schema migration or active-source mutation in this slice, so
rollback is removal of the unused parser until a coordinator explicitly binds
and activates a qualified source.
