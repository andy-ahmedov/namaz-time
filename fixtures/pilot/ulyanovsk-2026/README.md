# Ulyanovsk 2026 official annual pilot source

Evidence label: `CONFIRMED_PUBLIC` for facts visible in the product-owner-
supplied PDF. This label does not establish mosque approval or TV runtime.

## Raw provenance

- Authority printed on the source: Региональное духовное управление
  мусульман Ульяновской области в составе ЦДУМ России.
- Scope printed on the source: `г. Ульяновск`, Gregorian year 2026 and Hijri
  years 1447–1448.
- Raw file: `calendar_for_the_year_Ulyanovsk.pdf`, retained byte-for-byte.
- Raw SHA-256:
  `82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21`.
- Raw size: 35,078,839 bytes; PDF 1.4, 18 A5 portrait pages.
- Import capture time: `2026-08-20T09:35:29Z`, derived from the workspace file
  mtime. It is not represented as the publisher's publication time.
- PDF metadata creation time: `2025-12-23T17:11:55+03:00`.
- Printed canonical site: `www.rdumul.ru`; printed Telegram: `dumul73`.
- Project-use permission: confirmed by the product owner on 2026-08-20.
- Source precedence: D-002 is accepted by the product owner. This PDF is the
  2026 baseline, while the retained August photo has priority for every field
  it supplies throughout August. The named approval is a separate signed
  receipt; candidates intentionally remain `needs_review` until the publication
  boundary and must not be called published/official merely because parsed.

## Controlled transcription

`schedule.csv` retains every printed daily table field that affects review:
Fajr/Suhur, recommended al-Isfar, sunrise, zenith, Dhuhr onset, recommended
collective Dhuhr in mosques, Asr, Maghrib, Isha and source markers. The PDF has
no daily Hijri column, so no Hijri values are invented. Its SHA-256 is
`41b44173e99534ffc7d6a1ee7b429f98a95ca863bb29d2971b57e8693dd51538`.
The resulting candidate has normalized SHA-256
`867862c453fc167a9c9b17240dfb4c9a5be0822882c3dfbc68e8c454a4c7efb2`;
its first-import diff has SHA-256
`41e63772fb6d8e96d41caf8fd88a729087e04a5dfc0bbdebc30a92c42c1339db`.
These fingerprints are regression-pinned and must be rebound to a named
approval if any source or normalization field changes.

Poppler 24.02.0 extracted the existing PDF text layer in layout mode. All 365
rows are contiguous from 2026-01-01 through 2026-12-31 and all 12 rendered
monthly pages were visually reviewed. `extraction-record.json` records the
tool/version, page set and intermediate text fingerprint; the production
pipeline consumes the retained PDF and controlled CSV, not a runtime Poppler
dependency.

The provider keeps al-Isfar as source-only review metadata. It keeps the
collective Dhuhr value separate from Dhuhr onset and flags it for mosque iqamah
approval; D-009 is not inferred from a city-wide recommendation.

## Printed high-latitude rule

The introductory pages say that Ulyanovsk's summer Fajr and Isha use analogous
times from Astrakhan, the nearest qualifying region where the signs remain
observable, and that cities below latitude 45° may not be used. For Ulyanovsk
the printed overnight transitions are:

- start: 10→11 May 2026, marked on May 10 Isha `22:07` and May 11 Fajr
  `02:48`;
- completion: 2→3 August 2026, marked on Aug 2 Isha `23:15` and Aug 3 Fajr
  `01:10`.

These four cells carry explicit source flags and regression coverage. The TV
still consumes the already resolved daily rows; it does not recalculate them.

## August reconciliation

All 31 August days were compared field-by-field with
`../ulyanovsk-2026-08/time-namaz.jpg` and its controlled transcription.
The sources agree on Fajr, sunrise, zenith, Asr, Maghrib and Isha for every
day. They disagree on:

- Dhuhr onset for Aug 20–30: monthly photo is equal to zenith, annual PDF is
  ten minutes later;
- collective Dhuhr on Aug 24: monthly photo `13:53`, annual PDF `13:15`;
- transition footnote wording: monthly photo says “Начало”, annual PDF says
  “Завершение”.

`august-reconciliation.csv` stores every numeric disagreement without changing
either observed value. D-002 resolves selection deterministically in favor of
the monthly photo; every row is marked `resolved_monthly_photo_precedence`.
The resolved ledger SHA-256 is
`2b7a2f73e6752abaed844bfb0b2985f89a0c77def91421164b7bff252a0c4370`.
The wording difference is likewise retained as evidence, with the monthly
source governing effective August flags. This selection is not an iqamah
decision and is not a religious approval.

## Effective schedule

`effective-policy.json` is the immutable product-policy artifact. Its SHA-256
is `c7d95bbc900a683b3be4fa66f6d1a8237ccf3e882452674a2cdd946c800d935a`.
It binds both component candidate IDs, raw SHA-256 values, transcription
SHA-256 values, normalized SHA-256 values, parser versions, the bounded August
range and the exact fields supplied by the photo.

`effective-schedule/v1` applies the photo's available fields for all 31 August
days and leaves other dates on the PDF baseline. The photo has no
`recommended_fajr` column, so the PDF's printed al-Isfar value remains with
explicit baseline provenance rather than being invented or erased. Two raw
`requires_review_*` flags whose source conflict is now resolved are rewritten
only in derived output to bounded `source_*_preserved_by_monthly_precedence`
evidence. The raw CSV and image are unchanged.

Deterministic effective fingerprints:

- derived-row transcription SHA-256:
  `94ef664fafa69192992b7bc5b405baa6678d5a49c8f6b1c770c0281760d60e4f`;
- normalized candidate SHA-256:
  `e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77`;
- baseline-to-effective diff SHA-256:
  `de139a27b2f0f5b42253783f5a4aeca11d2f643bee572d860e2c4c24564d7e4e`.

The candidate record remains `needs_review` by design; approval is a separate
immutable record rather than a provider status mutation. The named approver
acknowledges `mosque_iqamah_approval_required` and binds a distinct mosque
policy. Candidate collective Dhuhr is never promoted directly to TV iqamah.

## Named approval and mosque policy

The product owner identified Ахмедов Эльмаддин Фазил Оглы, representative of
Ulyanovsk mosques and the Second Cathedral Mosque, as the responsible schedule
approver. Stable identity:
`approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly`.

- `mosque-prayer-policy.json` canonical SHA-256:
  `6b8ce3c01001d41162f18d5fb26d6a68251ce2d60a5f7598c5c96b06b51aa629`;
- `approver-trust-bundle.json` raw SHA-256:
  `29ab69e969e75bee879cbe5f73993e711c160da754f4182653c32cb058ad2974`;
- `approval-receipt.json` raw SHA-256:
  `36e889022c2db28ffa8221e1d104631f4a64f1c0d0211f89e942e4527453186c`.

The private approval key is not in the repository. It is stored under the
local operator account with mode `0600`; production custody backup/transfer is
still an operator responsibility. The signed v2 receipt approves the exact
candidate/diff/warnings, selects `dhuhr_onset` as Dhuhr adhan, assigns fixed
Dhuhr iqamah `13:15` on all seven weekdays and the same Friday Jumu'ah time,
and keeps Fajr/Asr/Maghrib/Isha at adhan +5 minutes. No Ramadan or holiday
exception is invented. The raw PDF/photo/CSV and collective-Dhuhr evidence are
unchanged; 24 August Dhuhr adhan is the accepted photo onset value `12:48`.
