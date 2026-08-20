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
- Religious approval: D-002 is still open. This fixture can produce only a
  `needs_review` candidate and must not be called the pilot mosque's approved
  schedule.

## Controlled transcription

`schedule.csv` retains every printed daily table field that affects review:
Fajr/Suhur, recommended al-Isfar, sunrise, zenith, Dhuhr onset, recommended
collective Dhuhr in mosques, Asr, Maghrib, Isha and source markers. The PDF has
no daily Hijri column, so no Hijri values are invented. Its SHA-256 is
`41b44173e99534ffc7d6a1ee7b429f98a95ca863bb29d2971b57e8693dd51538`.
The resulting candidate has normalized SHA-256
`99a9c1946a21105413ff8fef4ca6d7637908678f3f89e079eb82f473193ae7c0`;
its first-import diff has SHA-256
`3e9164402713e6a2773d0cec64a160a04ab3c16b4d5251ffd669f30113461a8e`.
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

`august-reconciliation.csv` stores every numeric disagreement. Cause and source
precedence remain `UNKNOWN`; values are not silently corrected. A named D-002
approver must resolve the disagreement before production publication.
