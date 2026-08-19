# DECISIONS.md

Record product decisions here before converting stable architecture choices into ADRs. Never invent answers on behalf of a mosque/authority.

## Blocking decisions

| ID | Decision | Status | Owner | Needed by | Notes |
|---|---|---|---|---|---|
| D-001 | First pilot mosque and locality | OPEN | Product owner | before T008/T010 |  |
| D-002 | Canonical prayer-time authority/source | OPEN | Mosque approver | before real data |  |
| D-003 | Written permission and attribution | OPEN | Product owner/source | before real data commit/publication |  |
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
