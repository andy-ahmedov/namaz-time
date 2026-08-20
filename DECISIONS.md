# DECISIONS.md

Record product decisions here before converting stable architecture choices into ADRs. Never invent answers on behalf of a mosque/authority.

## Blocking decisions

| ID | Decision | Status | Owner | Needed by | Notes |
|---|---|---|---|---|---|
| D-001 | First pilot mosque and locality | ACCEPTED | Product owner | before T008/T010 | Second Cathedral Mosque of Ulyanovsk, 18A Dzerzhinsky Street, Ulyanovsk; confirmed 2026-08-20 |
| D-002 | Canonical prayer-time authority/source | OPEN | Mosque approver | before real publication | August 2026 `rdumul.ru`-attributed photo is selected as the pilot manual-import fixture; a named religious approver/authority confirmation is still required before labeling publication official |
| D-003 | Written permission and attribution | ACCEPTED | Product owner/source | before real data commit/publication | product owner confirmed project use on 2026-08-20; preserve the exact raw SHA-256 and printed attribution |
| D-004 | Pilot TV/box model and Android version | OPEN | Installer | before performance/autostart promises | T001 compiles with minSdk 28 / targetSdk 35; this is a scaffold baseline, not a hardware support promise |
| D-005 | Product name and Android application ID | OPEN | Product owner | before distributable build | T003 deliberately retains the T001 placeholder: `Namaz Time` / `com.example.namaztime.tv` |
| D-006 | Repository software license | OPEN | Product owner | before public release | do not assume competitor/data licenses |
| D-007 | Required languages for pilot | OPEN | Mosque | T005 | Russian + ? |
| D-008 | Local-only vs remote admin in MVP | OPEN | Product owner | before remote administration | T003 implements only the reversible local-first settings shell; it does not choose the final administration mode |
| D-009 | Iqamah/Jumu'ah rule policy | OPEN | Mosque approver | T006 | fixed/offset, exceptions, sessions |
| D-010 | QR campaign domains and approval | OPEN | Mosque | T007 | HTTPS and official destination |
| D-011 | Best-effort boot vs managed kiosk | OPEN | Installer/product | before pilot deployment | separate support promises |
| D-012 | Analytics/crash reporting policy | OPEN | Product/privacy | before store release | recommended privacy-minimal default |
| D-013 | Snapshot signer/KMS strategy | OPEN | Security owner | before production publication | test key must never become production key |
| D-014 | Source stale/expiry/fallback behavior | OPEN | Mosque approver | before real source | default: no silent fallback |

## Confirmed repository proposals

These are proposals until accepted by the product owner:

- Kotlin + Compose for TV client;
- Go modular-monolith backend;
- PostgreSQL backend, Room/SQLite TV;
- signed immutable offline snapshots;
- annual/manual source as first provider;
- landscape MVP, portrait later;
- no payment processing or video backgrounds in MVP.

## T001 reversible scaffold record

- `PROPOSAL` — Go module path follows the repository location:
  `github.com/andy-ahmedov/namaz-time`.
- `PROPOSAL` — Android uses JDK 17, compile/target SDK 35 and min SDK 28 until
  D-004 selects the pilot hardware.
- `PROPOSAL` — `com.example.namaztime.tv` is deliberately non-production and
  does not settle D-005.
- `PROPOSAL` — the T001 launcher contains no network, schedule, Room, Compose,
  analytics, identifiers or broad Android permissions.

## T003 reversible shell record

- `PROPOSAL` — settings are local-first until D-008 is resolved; the shell has
  no network dependency or Android network permission.
- `PROPOSAL` — Room schema version 1 is the migration baseline for immutable
  snapshots, prayer days, iqamah/Jumu'ah/campaign configuration and an atomic
  active/previous selection pointer.
- `PROPOSAL` — the temporary T001 product name/application ID remains in use;
  T003 is not a distributable product-identity decision.

## T004 bundled snapshot record

- `PROPOSAL` — the explicitly synthetic repository fixture is bundled for
  first-launch offline bootstrap and is never presented as real or official.
- `PROPOSAL` — Android validates the snapshot contract before opening a Room
  transaction, imports all child rows, and changes active/previous selection
  only at the end of that transaction.
- `PROPOSAL` — Room schema v2 adds deterministic prayer-day source flags
  (migrated v1 rows initialize to `[]`); v3 retains optional provenance and
  theme-asset integrity references. Explicit v1→v2→v3 migrations keep the
  committed v1 export as the rollback baseline.
- `CONFIRMED_PUBLIC` — the user-supplied August 2026 pilot schedule image is
  authorized for this project. Its religious approval status remains distinct
  from permission to use the file.

## T005 main-display record

- `PROPOSAL` — the built-in dark gradient and overlay are independent offline
  UI assets; no competitor visual resource or pixel layout is used.
- `PROPOSAL` — T005 displays unresolved iqamah and time-engine output as
  explicit placeholders. T006 replaces only those presentation values and does
  not change the stored adhan rows.
- `CONFIRMED_RUNTIME` is not claimed for the resolution profiles: the
  720p/1080p/4K evidence is Robolectric UI coverage, while physical-TV
  visibility and overscan remain D-004 acceptance work.

## Decision template

```text
ID:
Date:
Status: PROPOSED | ACCEPTED | SUPERSEDED
Context:
Options considered:
Decision:
Reason:
Consequences:
Evidence/approver:
Supersedes / superseded by:
```
