# DECISIONS.md

Record product decisions here before converting stable architecture choices into ADRs. Never invent answers on behalf of a mosque/authority.

## Blocking decisions

| ID | Decision | Status | Owner | Needed by | Notes |
|---|---|---|---|---|---|
| D-001 | First pilot mosque and locality | ACCEPTED | Product owner | before T008/T010 | Second Cathedral Mosque of Ulyanovsk, 18A Dzerzhinsky Street, Ulyanovsk; confirmed 2026-08-20 |
| D-002 | Canonical prayer-time authority/source | ACCEPTED | Product owner | before real publication | 2026 baseline is the retained RDUM Ulyanovsk annual PDF; for every field present in the retained August 2026 photo, the photo has priority throughout August. Named approval and D-009 are recorded separately and cryptographically bound to the effective candidate. |
| D-003 | Written permission and attribution | ACCEPTED | Product owner/source | before real data commit/publication | product owner confirmed project use on 2026-08-20; preserve the exact raw SHA-256 and printed attribution |
| D-004 | Pilot TV/box model and Android version | OPEN | Installer | before performance/autostart promises | Android Studio TV Emulator API 36 / 1920×1080 is the current controlled runtime; physical TV/box remains unselected and emulator evidence is not an OEM support promise |
| D-005 | Product name and Android application ID | OPEN | Product owner | before distributable build | product name accepted as `NamazTime` on 2026-08-20; `com.example.namaztime.tv` remains a non-production placeholder until a final application ID is selected |
| D-006 | Repository software license | OPEN | Product owner | before public release | do not assume competitor/data licenses |
| D-007 | Required languages for pilot | ACCEPTED | Product owner / mosque | T022 | Russian is the default; Russian and English are selectable for the whole TV UI and the choice persists locally. Confirmed 2026-08-20. |
| D-008 | Local-only vs remote admin in MVP | OPEN | Product owner | before remote administration | T003 implements only the reversible local-first settings shell; it does not choose the final administration mode |
| D-009 | Iqamah/Jumu'ah rule policy | ACCEPTED | Mosque approver | T006/T010 | all iqamah values are adhan +5 minutes; Friday Dhuhr congregation is replaced by one Jumuah at 13:15; no separate Ramadan/holiday exceptions yet; collective-Dhuhr source column is the mosque Dhuhr adhan |
| D-010 | QR campaign domains and approval | OPEN | Mosque | T007 | HTTPS and official destination |
| D-011 | Best-effort boot vs managed kiosk | OPEN | Installer/product | before pilot deployment | separate support promises |
| D-012 | Analytics/crash reporting policy | OPEN | Product/privacy | before store release | recommended privacy-minimal default |
| D-013 | Snapshot signer/KMS strategy | ACCEPTED | Product owner / security owner | before production publication | KMS selected; exact vendor/service, production Ed25519 key and distinct security operator remain deployment inputs. Separate approver/signer, versioned trust, rotation/revocation and no production private material in Git/APK/API/ordinary CI remain mandatory. |
| D-014 | Source stale/expiry/fallback behavior | ACCEPTED | Product owner / mosque approver | before real source | No silent calculation/provider fallback. Last-known-good is displayable only while the current mosque-local date is inside its signed coverage; after coverage the TV shows the safe “schedule unavailable” state. Confirmed 2026-08-20. |
| D-015 | Pilot schedule delivery channel | ACCEPTED | Product owner | pilot rollout | Production pilot delivery remains pairing + T009 signed immutable snapshots. T022 may embed the exact approved schedule and a disjoint public-only local trust anchor only in the non-release pilot-local QA build; it is not API-admissible production publication or a delivery shortcut. |

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

## T006 time-engine record

- `PROPOSAL` — the main countdown excludes sunrise by default and includes
  adhan, explicitly resolved iqamah, Friday Jumu'ah salah and next-day Fajr.
  This is a visible policy, not a change to stored source rows.
- `PROPOSAL` — an equal highest-priority iqamah-rule match fails closed instead
  of selecting by incidental storage order; an exact-date override remains
  authoritative over every range/weekday rule.
- `PROPOSAL` — nonexistent and ambiguous DST wall times both fail closed. The
  snapshot contract has no fold/offset evidence, so the client does not guess
  which occurrence a source intended during a fall-back overlap.
- `PROPOSAL` — D-009 is accepted in the signed pilot policy: five minutes after
  adhan for each daily congregation; Friday Dhuhr iqamah is omitted because one
  Jumuah at 13:15 replaces it. Dhuhr remains visible as a source row, but does
  not compete with Jumuah in Friday countdown events.
- `CONFIRMED_RUNTIME` is not claimed for wall-clock behavior on a television;
  current evidence is deterministic JVM/Robolectric execution with injected
  clocks and named timezone databases.

## T007 QR-campaign record

- `PROPOSAL` — campaign lifecycle is start-inclusive and end-exclusive. More
  than one active valid campaign is ambiguous and fails closed because the
  snapshot contract has no publication priority.
- `PROPOSAL` — the TV accepts only bounded lowercase `https://` targets with a
  host and no user information, creates the QR locally, displays no raw URL,
  and retains only a SHA-256 target fingerprint in the current audit stub.
- `UNKNOWN` — D-010 remains open, so T007 does not create an allowlist or live
  pilot campaign. All automated campaign destinations are synthetic
  `example.org` fixtures.
- `CONFIRMED_RUNTIME` is not claimed for scan distance or television contrast;
  the QR is software-decoded in a JVM test and Compose bounds are checked with
  Robolectric at 720p, 1080p and 4K density profiles.

## T008 manual-publication record

- `CONFIRMED_PUBLIC` — the retained Ulyanovsk image visibly covers August 2026,
  distinguishes Dhuhr onset from a collective-in-mosques column and prints the
  `rdumul` / `rdumul.ru` / `dum.ul` / `dumul` attribution. Its exact SHA-256 is
  `11b4aaaa4765b486103e6532fb560bde9dec6617215fad9c462bd9253cba993c`.
- `PROPOSAL` — manual imports bind raw-artifact, transcription, normalized
  candidate and deterministic diff hashes plus parser version. Every parser
  warning code requires explicit approval acknowledgement before publication.
- `PROPOSAL` — snapshot signing follows ADR 0003 canonical JSON plus Ed25519;
  only authenticated bytes or the explicitly synthetic bundled fixture can
  reach Room activation.
- `PROPOSAL` — the raw monthly candidate remains `needs_review` and is not
  labeled approved/official. D-002 now selects it as the August override input;
  the `13:53` collective value, starred Fajr/Isha values and 31 August Dhuhr
  value remain verbatim source evidence rather than invented corrections.
- `CONFIRMED_PUBLIC` — the product owner accepted D-013 on 2026-08-20. The
  committed key remains public/test-only; production uses a disjoint protected
  signer and lifecycle-aware public trust bundle under ADR 0011.

## T010 annual Ulyanovsk source record

- `CONFIRMED_PUBLIC` — the supplied 18-page PDF names the Regional Spiritual
  Administration of Muslims of the Ulyanovsk Region within the Central
  Spiritual Administration of Muslims of Russia, covers Ulyanovsk for all of
  2026 and has raw SHA-256
  `82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21`.
- `CONFIRMED_PUBLIC` — the PDF prescribes an Astrakhan analogy for summer Fajr
  and Isha, starts the Ulyanovsk period overnight 10→11 May and completes it
  overnight 2→3 August. The exact four marked cells are retained as flags.
- `PROPOSAL` — `ulyanovsk-official-pdf-csv/v1` is a source-specific,
  fail-closed controlled-transcription adapter. Al-Isfar and collective Dhuhr
  remain candidate review metadata; absent daily Hijri dates remain absent.
- `PROPOSAL` — `effective-schedule/v1` binds an immutable policy artifact plus
  both component candidate/raw/transcription/normalized/parser identities. It
  uses the PDF as the 2026 baseline and the photo for fields present throughout
  August; absent monthly al-Isfar values remain explicitly sourced from the PDF.
  Policy SHA-256 is
  `c7d95bbc900a683b3be4fa66f6d1a8237ccf3e882452674a2cdd946c800d935a`;
  effective normalized SHA-256 is
  `e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77`.
- `PROPOSAL` — the twelve numeric source disagreements remain unchanged in the
  reconciliation ledger but are marked `resolved_monthly_photo_precedence`.
  Only the two exact source-conflict review flags are rewritten into preserved
  `source_*` evidence flags in effective output.
- `PROPOSAL` — named approver identity
  `approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly` represents Ахмедов
  Эльмаддин Фазил Оглы, representative of Ulyanovsk mosques and the Second
  Cathedral Mosque. The signed receipt binds the exact effective candidate,
  diff, warnings and mosque policy; the private approval key is outside Git.
- `PROPOSAL` — the approver states that the source collective-Dhuhr column is
  the mosque Dhuhr adhan, not iqamah. Publication therefore selects that value
  as Dhuhr adhan and derives iqamah only from the separately approved +5 rule.
- `UNKNOWN` — the selected KMS vendor/key, a distinct named security operator,
  authenticated production signing-trust deployment still blocks the
  first production activation. The paired Android TV emulator is the accepted
  canary target; physical OEM evidence remains a separate future check.

## T009 device-sync record

- `PROPOSAL` — bearer-authenticated manifest and snapshot URLs must share one
  HTTPS origin until a separate CDN credential/allowlist design is approved.
- `PROPOSAL` — `manifest_version` is monotonic; authorized rollback publishes a
  newer manifest pointing to a prior immutable snapshot, which is downloaded,
  authenticated and transactionally re-imported.
- `PROPOSAL` — sync transport state and rejected raw bytes use an atomic local
  file journal scoped by a device/mosque/timezone/origin fingerprint, while
  Room remains the only display schedule authority.
- `PROPOSAL` — publisher signature, manifest ID/key and paired mosque ID/IANA
  timezone are independent activation bindings; any mismatch fails closed.
- `PROPOSAL` — the T009 pairing fixture is explicitly ephemeral/test-only.
  Persistent expiry, attempt/rate state and restart-safe consumption belong to
  the future production issuer and are not claimed here.
- `PROPOSAL` — non-empty remote asset manifests fail closed in T009 rather than
  silently activating themes whose assets were not staged.
- `PROPOSAL` — D-013 trust bundles are injected through authenticated build or
  deployment configuration. T009 proves the path with a public test bundle and
  embeds no production key/token; remote unsigned trust replacement is forbidden.
- `CONFIRMED_RUNTIME` is not claimed for WorkManager/OEM process behavior or a
  physical TV; evidence is local Go/JVM/Robolectric plus file-backed DB reopen.

## T011 production-pairing record

- `PROPOSAL` — production pairing state uses PostgreSQL migration v1 with
  pending/active/revoked devices, one-use code records, persistent HMAC rate
  buckets and append-only audit events.
- `PROPOSAL` — code/token plaintext exists only at issuance/redemption output;
  repository commands and rows carry SHA-256 verifiers. Code, device and direct
  peer rate identities use HMAC-SHA-256 under a runtime-only key.
- `PROPOSAL` — `(device_id, mosque_id)` is a composite database foreign key and
  revocation requires the same pair at the service/store boundary.
- `PROPOSAL` — T011 does not expose an unauthenticated admin shortcut. T012 must
  define actors/roles/memberships and authorized issue/revoke/read endpoints.
- `CONFIRMED_RUNTIME` — restart, migration, concurrent single-winner, expiry,
  independent rate buckets, revocation and audit immutability were reproduced
  locally against a disposable PostgreSQL 18 container. This label applies to
  the controlled backend test only, not a deployed service or physical TV.

## T012 fleet-administration record

- `PROPOSAL` — PostgreSQL migration v2 separates admin actors, credential
  verifiers and role/mosque memberships; there is no unauthenticated bootstrap
  route.
- `PROPOSAL` — `service_admin` is global fleet read/write, `mosque_admin` is
  local read/write, and `approver`/`viewer_support` are local read-only for T012
  fleet operations. Service checks and transaction-level database rechecks both
  apply; the runtime role cannot update authorization rows.
- `PROPOSAL` — stable request hashes plus domain-separated current/compatibility
  HMAC keys reproduce pairing responses without plaintext code storage;
  non-secret assignment responses preserve historical idempotency for 24 hours,
  including across registry/config changes.
- `PROPOSAL` — schema-owner credentials are confined to the short-lived
  migration command; the API process only verifies exact schema v2 with its
  runtime role.
- `PROPOSAL` — admin assignment can reference only a signed immutable artifact
  already verified in the device API registry. It cannot approve, sign or
  inject a snapshot and does not change T010's blocked status.
- `CONFIRMED_RUNTIME` — migration v2, role/mosque isolation, idempotent
  issue/revoke/assignment, historical response retention and actor/reason/
  request audit evidence were reproduced locally in disposable PostgreSQL 18.
  This is controlled backend evidence, not a deployed identity provider or TV.

## T013 device-health record

- `PROPOSAL` — PostgreSQL migration v3 retains one latest health row per device;
  API `received_at`, not client `sent_at`, owns fleet last-seen.
- `PROPOSAL` — reported snapshot and health are non-authoritative diagnostics
  and cannot mutate assignment, publication or Android Room activation.
- `PROPOSAL` — heartbeat JSON is a closed privacy allowlist; arbitrary codes,
  logs, URLs, network/account/location identifiers and history retention are
  rejected.
- `PROPOSAL` — Android reporting is derived from the provisioned origin/device
  path and best effort; non-cancellation failure cannot change the completed
  sync result or offline display.
- `CONFIRMED_RUNTIME` — latest-only persistence, server last-seen, revoked and
  cross-mosque denial, RBAC health projection and least-privileged runtime
  access were reproduced in disposable PostgreSQL 18. Android sender behavior
  is locally unit-tested, not physical-TV runtime evidence.

## T014 canary-rollout record

- `PROPOSAL` — a rollout group is a bounded operator label on a mosque-owned
  device, not a demographic or analytics segment; devices cannot self-enrol.
- `PROPOSAL` — one cohort transaction uses a 101st-row overflow sentinel, locks
  non-revoked devices in deterministic ID order and updates at most 100, all or
  none, of their durable assignments.
- `PROPOSAL` — cohort assignment accepts only an immutable artifact already
  verified in the API registry. It cannot approve, sign or publish content and
  does not unblock T010.
- `PROPOSAL` — rollback assigns a previous verified snapshot as a new monotonic
  manifest version; every device retains an individual canonical audit chain.
- `CONFIRMED_RUNTIME` — migration v4↔v3, two-device atomic rollout, exact retry,
  monotonic rollback, oversized-cohort rejection and mosque isolation were
  reproduced locally in disposable PostgreSQL 18. This is controlled backend
  evidence, not a production rollout or physical-TV canary.

## T015 support-bundle record

- `PROPOSAL` — `device-support-bundle/v1` is an on-demand bounded projection of
  existing device, current assignment and latest health state; it is not stored
  as new history and collects nothing from the TV.
- `PROPOSAL` — the closed type excludes all credentials, pairing codes,
  installation keys, capabilities, snapshot URLs, network/account/location
  identifiers, arbitrary maps and logs.
- `PROPOSAL` — existing service/mosque read RBAC and uniform not-found behavior
  apply; responses are `no-store` and cannot mutate fleet/display state.
- `CONFIRMED_RUNTIME` — scoped current-state assembly and forbidden-field
  absence were reproduced locally against disposable PostgreSQL 18. This does
  not represent a production support workflow or physical-TV export.

## T016 backup/restore record

- `PROPOSAL` — fleet PostgreSQL logical backups are custom archives restored
  only into a clean isolated database without applying archived owner/ACL
  commands; deployment-managed roles and least-privilege grants are reapplied
  separately.
- `PROPOSAL` — a database dump is sensitive and incomplete on its own. It needs
  encrypted restricted storage plus an authenticated immutable inventory, and
  separate recovery for signed snapshots, permitted raw sources, runtime
  configuration and publication signing keys.
- `PROPOSAL` — archive SHA-256 and byte count are corruption evidence, not
  source authenticity. Production RPO/RTO and point-in-time recovery remain
  deployment decisions and cannot be inferred from a local logical restore.
- `CONFIRMED_RUNTIME` — exact v4 schema, linked fleet state, device/admin
  authentication and append-only triggers were restored locally from an intact
  PostgreSQL 18 custom archive; a truncated archive was rejected. This is not a
  production-data restore or disaster-recovery timing claim.

## T017 connected-display design record

- `CONFIRMED_PUBLIC` — the product-owner-supplied `design.png` is a 1672×941
  visual reference with SHA-256
  `afe3803c2fbedc6755bd49093a6854393c246bef547c7cad185b6f2502282ff6`.
  It shows a dark card hierarchy around mosque identity, next prayer/countdown,
  local date/time, prayer list and iqamah, but also visibly includes third-party
  branding and imagery.
- `PROPOSAL` — T017 derives only hierarchy and mood. The application uses an
  original static Compose background and semantic slate/amber surface, text,
  focus and warning tokens; the supplied image is not committed or packaged.
- `PROPOSAL` — the iqamah summary is sourced only from T006 resolution. An
  iqamah countdown is shown only when iqamah is the resolved next event;
  missing values remain explicitly unset.
- `PROPOSAL` — Room/T006/T007-connected state, D-pad focus, palette contrast
  floors and safe component bounds pass local JVM/Robolectric tests for 720p,
  1080p-density and 4K-density profiles.
- `UNKNOWN` — physical-TV pixels, hall readability, OEM overscan behavior and
  QR scan distance remain deferred acceptance evidence; local tests are not
  promoted to `CONFIRMED_RUNTIME`.

## T018 display screen-on record

- `PROPOSAL` — public display mode, including its safe unavailable projection,
  sets only the host Compose view's `keepScreenOn` hint. Leaving the route
  restores the prior flag, so settings and background work do not extend the
  screen-on lifetime.
- `PROPOSAL` — T018 adds no wake lock, Android permission, kiosk mode or boot
  guarantee; those would require separate power/threat/deployment decisions.
- `UNKNOWN` — whether a selected pilot television honors the view hint across
  its vendor sleep, energy-saving and process policies remains part of the
  deferred D-004/D-011 physical matrix.

## T019 accelerated offline-rollover record

- `PROPOSAL` — local rollover hardening reuses one immutable synthetic schedule
  and advances only an injected `Instant`; it performs no network request,
  source fallback or device-clock mutation.
- `PROPOSAL` — the seven covered mosque-local dates remain on the connected
  4K-density prayer display, while the first uncovered date fails closed with
  the existing bounded coverage diagnostic.
- `UNKNOWN` — elapsed seven-day stability, memory/thermal behavior, OEM process
  survival and actual 4K panel output still require the deferred physical soak.

## T020 bounded device-clock-health record

- `PROPOSAL` — the standard `Date` header on a successful authenticated HTTPS
  manifest response is a bounded operational clock hint, not prayer-source or
  snapshot authenticity. Hash and Ed25519 signature remain the content trust
  boundary.
- `PROPOSAL` — a server instant outside device response receipt by more
  than five minutes, or a device wall-clock rollback during that request, sets
  durable `clock_mismatch`; invalid/absent evidence makes no new claim.
- `PROPOSAL` — nullable clock health cannot be collapsed into the existing
  heartbeat's required boolean: `unknown` is retained locally until a tri-state
  contract/runtime assembler is designed. It cannot set OS time, choose a
  prayer date, block activation or replace the last-known-good snapshot.
- `UNKNOWN` — RTC errors that prevent TLS, reboot/power-cut recovery and OEM
  automatic-time behavior require physical-device evidence.

## T021 bounded display-retention record

- `PROPOSAL` — available and unavailable public display foreground content
  follows a deterministic six-position cycle, advancing every ten minutes and
  bounded to ±2 dp on each axis inside the shared overscan-safe frame.
- `PROPOSAL` — the shift is discrete rather than animated and does not move the
  full-bleed background or settings route, reorder semantics/focus, add a
  permission or introduce another visual language.
- `UNKNOWN` — whether this measure materially reduces retention on a selected
  pilot panel, and whether vendor signage controls are also required, remain
  part of the physical-TV soak and cannot be inferred from Robolectric bounds.

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
