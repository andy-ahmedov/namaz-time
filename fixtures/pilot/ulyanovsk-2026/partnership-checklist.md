# Ulyanovsk 2026 source onboarding checklist

This is the source-specific application of
[`SOURCE_PARTNERSHIP_CHECKLIST.md`](../../../SOURCE_PARTNERSHIP_CHECKLIST.md).
`CONFIRMED_PUBLIC` means visible in the supplied PDF. It does not mean that the
Second Cathedral Mosque has approved the schedule.

## Authority and scope

| Item | Status | Evidence / blocker |
|---|---|---|
| Organizational name | `CONFIRMED_PUBLIC` | Printed authority name is recorded verbatim in `source-record.json`. |
| Named contact and role | `PROPOSAL` | Product owner identifies Ахмедов Эльмаддин Фазил Оглы, representative of Ulyanovsk mosques; stable signed identity is retained. Public third-party role verification is not claimed. |
| Territory/locality | `CONFIRMED_PUBLIC` | Calendar cover and all monthly tables are for `г. Ульяновск`. |
| Adhan vs congregation semantics | `PROPOSAL` | Signed policy v2 keeps the source Dhuhr-onset column as adhan. Mosque-local Dhuhr iqamah and Friday Jumu'ah share the fixed 13:15 default; Fajr/Asr/Maghrib/Isha remain adhan +5. The source collective-Dhuhr column remains provenance evidence and is not relabelled. |
| Madhab / regional rules | `CONFIRMED_PUBLIC` | Hanafi Asr and Astrakhan-analogy summer Fajr/Isha rules are printed and regression-tested. |
| Ramadan / exceptional-day policy | `PROPOSAL` | Approver states that no separate Ramadan or holiday rule is currently defined; no exception is invented. |
| Final correction authority | `PROPOSAL` | Corrections are assigned to the admin workflow; exact stale/fallback policy remains D-014. |

## Data access and permission

| Item | Status | Evidence / blocker |
|---|---|---|
| Preferred machine-readable feed | `UNKNOWN` | Only the supplied PDF is available; CSV is controlled transcription, not publisher output. |
| Intended project use | `PROPOSAL` | Product owner authorized project use on 2026-08-20; source-authority automation acknowledgement is not claimed. |
| Canonical channel | `CONFIRMED_PUBLIC` | `www.rdumul.ru` and Telegram `dumul73` are printed. Stable correction feed is unknown. |
| Update cadence / deadline | `UNKNOWN` | Annual coverage is visible; correction cadence/deadline is not stated. |
| Timezone | `PROPOSAL` | `Europe/Ulyanovsk` is the stored IANA zone; named source confirmation remains part of approval. |
| Full-year artifact | `CONFIRMED_PUBLIC` | 365 contiguous days, raw SHA-256 and extraction record retained. |
| Raw retention / derived use | `PROPOSAL` | Product-owner instruction authorizes project use; production redistribution terms remain subject to source/mosque approval. |
| Attribution | `CONFIRMED_PUBLIC` | Printed authority/site are retained verbatim. |

## Technical validation

| Item | Status | Evidence / blocker |
|---|---|---|
| Raw hash and immutable capture | `CONFIRMED_PUBLIC` | 35,078,839 bytes; SHA-256 `82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21`. |
| Parser contract / schema drift | `PROPOSAL` | Exact `ulyanovsk-official-pdf-csv/v1` header, source identity, PDF magic/hash, scope and seasonal markers fail closed in tests. |
| Full-year coverage | `CONFIRMED_PUBLIC` | 2026-01-01 through 2026-12-31, 365 rows. |
| Manual comparison | `CONFIRMED_PUBLIC` | All 12 monthly pages reviewed; all 31 August rows compared to the earlier photo. |
| Seasonal transitions | `CONFIRMED_PUBLIC` | May 10→11 start and Aug 2→3 completion retained as four exact flags. |
| Alternate-source differences | `PROPOSAL` | Twelve numeric differences and one wording difference remain recorded; D-002 selects the monthly photo for effective August fields through the hash-bound policy artifact. |
| Stale threshold | `PROPOSAL` | Source record uses annual/8760 hours; authority agreement is not yet recorded. |

## Approval and publication

Named approval and D-009 are complete as a signed receipt over the exact
effective candidate/diff/warnings and mosque policy. Production publication
remains `BLOCKED` on the concrete KMS provider and Ed25519 key, a distinct
security/signer operator, authenticated signing-trust deployment, D-014's exact
stale/fallback policy and the paired-emulator canary/rollback drill after
signing. Physical OEM validation is deferred and non-blocking. Approval remains
separate from the candidate's intentional `needs_review` workflow status.
