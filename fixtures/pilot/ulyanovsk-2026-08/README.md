# Ulyanovsk August 2026 pilot fixture

Evidence label: `CONFIRMED_PUBLIC` for facts visible in the user-supplied
image. This does not establish runtime behavior or religious approval.

- Pilot: Second Cathedral Mosque of Ulyanovsk, 18A Dzerzhinsky Street.
- Raw image: `time-namaz.jpg`, retained byte-for-byte.
- Raw SHA-256:
  `11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c`.
- Raw size: 336797 bytes; JPEG/JFIF, 902×1280.
- Import capture timestamp: `2026-08-19T22:53:41Z`, derived from the file
  mtime recorded when the supplied artifact entered this workspace. It is not
  represented as the publisher's publication time.
- Printed attribution: `rdumul`, `rdumul.ru`, `dum.ul`, `dumul`.
- Permission: project use confirmed by the product owner on 2026-08-20 (D-003).
- Approval: D-002 remains open. This fixture may produce only a
  `needs_review` candidate; it must not produce a production snapshot yet.

`schedule.csv` is a human transcription used by the deterministic manual CSV
parser. The source's zenith and collective-in-mosques columns remain separate.
The collective value is retained as an iqamah candidate, not merged into Dhuhr
onset. Asterisks and legible unusual values are flags, not silently corrected:

- 2026-08-02 Isha `23:15` has the printed summer-calculation marker;
- 2026-08-03 Fajr `01:10` has the printed summer-calculation marker;
- 2026-08-24 collective value is `13:53`;
- 2026-08-31 zenith is `12:46`, while Dhuhr onset is `12:56`.

Expected `manual-csv/v1` inspection fingerprints:

- transcription SHA-256:
  `29c2f62f8eb9da8f984f4e26e81325033bef1499175e24512e569fe534d2f745`;
- normalized SHA-256:
  `b050b6d6f0f567f018112883b80a78146ae42de282ba3f2fa4287963658f705b`;
- initial diff SHA-256:
  `e6737632e901576039d4728c3d0919e2ca6be83caad6cec519cbab682d39fd76`;
- status `needs_review`, 31 changed days, zero blocking errors.

The fixture covers one month only. It is not the full-year onboarding evidence
required by T010 and cannot establish future update cadence or correction
policy.
