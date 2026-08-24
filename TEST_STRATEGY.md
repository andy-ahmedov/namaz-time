# TEST_STRATEGY.md

## Risk order

1. wrong prayer date/time/timezone;
2. wrong source or silent fallback;
3. failed update replacing good data;
4. incorrect iqamah/Jumu'ah precedence;
5. unreadable/unreachable TV UI;
6. power/network/process recovery;
7. security/privacy regression.

Tests are allocated in that order.

## Domain unit tests

### Time and next-event

- before Fajr, exact event boundary and after Isha;
- next-day Fajr;
- midnight/date rollover in mosque timezone;
- device timezone differs from mosque timezone;
- leap day and year boundary;
- DST zones even if pilot is non-DST;
- sunrise excluded/included according to configured countdown policy;
- `HH:MM` ordering and next-day semantics.

### Iqamah resolution

- fixed vs offset;
- exact-date override beats range/base;
- weekday rule;
- overlapping priorities;
- missing iqamah remains missing;
- offset crossing midnight is rejected or explicitly normalized;
- Maghrib special policy only when configured.

### Jumu'ah

- multiple sessions;
- Friday local date;
- effective range and locale;
- Dhuhr source row remains intact;
- an active Friday Jumuah replaces Dhuhr in countdown events without deleting
  the displayed/provenance Dhuhr row.

## Provider contract tests

Every provider ships:

- sanitized representative fixture;
- parser golden output;
- malformed/missing-field fixture;
- schema-drift fixture;
- duplicate/gap fixture;
- checksum test;
- source scope test;
- rate-limit/retry test where networked.

No live external site is required for normal CI. A separate scheduled canary may check reachability/shape without publishing.

## Validation/diff tests

- 365 and 366-day calendars;
- threshold report by prayer;
- changed source/method is always highlighted;
- candidate cannot be approved if raw hash changed;
- parser version included in approval;
- invalid timezone and date gaps block publication;
- deterministic diff hash.
- signed approval receipt binds candidate/raw/transcription/normalized/diff,
  every warning code and the exact mosque-prayer-policy hash;
- revoked/rebound approver keys and trust-revision resurrection fail closed;
- approved collective-Dhuhr mapping, +5 iqamah and Friday 13:15 Jumuah are
  present in the prepared 365-day snapshot without raw-source mutation.

## Snapshot tests

- JSON Schema validation;
- deterministic canonical payload;
- correct SHA-256;
- valid/invalid/unknown-key Ed25519 signature;
- tampered byte rejection;
- malformed UTF-8 and schema-only optional-child drift rejection in Go and
  Android;
- correctly signed invalid iqamah, Jumu'ah, campaign and theme children fail
  closed;
- payload size limit;
- duplicate date rejection;
- insufficient future coverage rejection;
- transaction rollback on import failure;
- active pointer changes only after full validation;
- previous snapshot remains restorable.

## Android integration tests

- explicit synthetic tests import their fixture directly from `examples/`;
- pilot-local first install authenticates and imports the approved 365-day
  Ulyanovsk snapshot, while release packages no local schedule/trust assets;
- cold launch offline;
- Room migration preserves active snapshot;
- WorkManager sync with 200/304/401/404/500/timeout;
- process kill during download/import/activation;
- corrupt staged file;
- missing custom background fallback;
- clock/timezone mismatch diagnostic;
- saved focus/navigation state.

## Compose/UI tests

- main grid renders all fields and missing iqamah correctly;
- countdown does not relayout every second;
- D-pad reaches every setting;
- initial focus and back behavior;
- 720p/1080p/4K screenshots;
- long Russian/Arabic labels;
- RTL mixed content;
- QR panel on/off and expired state;
- stale vs expired status.

Golden images are reviewed for layout, not copied from a competitor.

T017 adds semantic palette contrast floors, verifies that the actual TV
Material theme receives the dark semantic palette, and checks component and
root-relative safe-frame bounds for main, settings and recovery UI on the three
local 16:9 profiles. Robolectric `captureToImage` times out in the current
environment under both default and native graphics, so no pixel-level
screenshot evidence is claimed at this checkpoint. Physical-TV and any future
instrumented screenshot review remain separate release evidence.

T018 adds a Compose navigation lifecycle regression: the host view is marked
screen-on in display mode, released in settings and marked again after D-pad
return. A disposal regression also preserves a pre-existing host flag. This is
local lifecycle evidence only; OEM power-management behavior remains in the
physical device matrix.

T023 adds semantic anchors for the NamazTime brand pill, six distinct prayer
glyph slots and the iqamah glyph; it also verifies Sunrise centers one time
across the combined adhan/iqamah area. DataStore tests pin the allowlisted image
background default, persistence and invalid-ID rejection, while a D-pad Compose
test changes the background from Appearance. Existing root-relative 720p,
1080p-density and 4K-density bounds remain the adaptive regression matrix.
Controlled API 36 emulator screenshots provide bounded `CONFIRMED_RUNTIME`
visual evidence; they do not establish physical-TV overscan, scan distance or
panel behavior.

T028 expands the Appearance regression to eight built-in previews and a custom
slot. JVM tests reject oversized, unsupported, corrupt and undersized documents,
preserve the previous app-local copy on rejection, and verify a missing custom
copy renders the packaged default. The manifest regression forbids both legacy
storage permissions and `READ_MEDIA_IMAGES`; picker behavior and OEM document
providers still require the controlled emulator/runtime loop.

T024 adds a controlled 960×540 reference-proportion contract: the centered
composition must occupy 70–76 percent of the full viewport, left and right
columns may differ by no more than four percent, next/clock cards must retain
their measured height ratios, and the bottom strip must share the composition
edges. It also asserts the location, next-card and clock-card ornament anchors,
the original calendar glyph and the accessible focused circular Settings
target. Existing Sunrise, six-glyph, D-pad, long-text, Jumu'ah and
720p/1080p/4K tests remain the functional/adaptive guardrails. Emulator
screencaps are compared at a normalized size; this is visual runtime evidence,
not a claim of automated perceptual equivalence or physical-TV acceptance.

T025 adds repository tests for atomic QR/purpose/motivation persistence,
unsafe-URL and unencodable high-correction QR rejection, five independent fixed
iqamah values and invalid time/Sunrise rejection. Projection tests prove local
values do not mutate the signed schedule, apply independently for current/next
day and fail closed when a configured iqamah precedes adhan. Compose tests enter all settings with
D-pad focus, assert that the removed Friday technical copy is absent, verify
the tall QR panel/frame/support icon/lower ornament anchors and pin its top and
bottom relationship to the prayer panel and event strip. The existing
720p/1080p/4K safe-frame matrix and nearest-neighbor QR decode tests remain
mandatory; a worst-case dark obstruction test covers the same area as the
NamazTime center badge at four raster sizes under high error correction. API
36 screenshots are bounded runtime evidence; real-phone scan distance and
physical-TV entry/readability remain `UNKNOWN`.

T028 replaces fixed device-local iqamah clock times with five nullable minute
offsets. Repository tests pin the bounded values and prove legacy `HH:mm` keys
are not guessed into offsets. Projection tests prove `adhan + N minutes`,
approved-policy fallback when unset, Sunrise exclusion and no Room mutation.
Compose tests drive the five +/− rows by D-pad and verify the resolved displayed
times. Donation repository tests pin complete HTTPS/text validation, five
built-in image IDs plus a separate custom slot, persisted mode and fail-closed
activation. Compose tests cover the 720p/1080p/4K safe frame, local QR/image/
details rendering, nine-item Settings reachability and the complete D-pad path
from donation display through Settings back to schedule mode. The shared asset
tests import the donation photo into an independent app-private slot.

T026 adds token regressions for high-opacity photographic-background surfaces
and the minimum bounded scrim, while the existing semantic contrast tests guard
the muted palette. QR raster tests prove modules are dark navy rather than pure
black and remain decodable. Compose semantics pin the next-event watermark,
bottom-strip ornament and a complete clock region after the three-column
composition is measured. Three API 36 build/install/screenshot passes were
compared directly with `main_with_qr.png`; this is bounded visual runtime
evidence, not automated perceptual equivalence or physical-TV acceptance.

T027 pins the concise public display identity for the Ulyanovsk pilot mosque
on both the main screen and Mosque settings page. A non-pilot passthrough
assertion and the authenticated pilot bootstrap test prove the presentation
mapping does not rewrite another mosque or mutate canonical signed Room data.

T019 reuses the connected display resolver with one immutable synthetic local
schedule and advances an injected instant over seven consecutive mosque-local
dates at the 4K-density profile. Every covered day retains its date, local
clock and safe-frame projection; the first uncovered date must replace the
prayer display with `SCHEDULE_DATE_OUTSIDE_COVERAGE`. This is an accelerated
deterministic matrix, not elapsed-time, memory, thermal or OEM soak evidence.

T020 injects deterministic request clocks into Android manifest sync. Tests
cover healthy and ±large-skew samples, a backward wall-clock jump, missing or
malformed `Date`, 200 activation, 304 refresh and file-backed reopen. The Go
contract test pins `Date` on both 200 and 304 without changing ETag identity.
Clock health never changes a completed sync result. This does not reproduce a
bad RTC, TLS failure, reboot or power cut on physical hardware.

T021 unit tests pin every position in the six-slot cycle, slot stability and
the ±2 dp axis bound. Existing connected and unavailable UI matrices exercise
the shifted shared safe frame at 720p, 1080p-density and 4K-density while the
D-pad tests continue to pin initial focus and display/settings return. These
tests prove deterministic layout bounds, not burn-in prevention on a panel.

T022 pins the pilot-local asset SHA-256, snapshot/approval identity, 365-day
coverage, exact August 20/24 Dhuhr selection, all five +5-minute iqamah rules
and one Friday 13:15 Jumu'ah session. The same test path performs real Android
canonical/signature/trust validation before Room activation, rejects tampering
without changing selection, and proves D-014 returns
`SCHEDULE_DATE_OUTSIDE_COVERAGE` on 2027-01-01. These are local
JVM/Robolectric/build facts. Settings tests traverse every section and real
local action with D-pad input, verify Room-projected provenance/policy state,
Russian safe defaults, persisted English selection and localized main/error/QR
semantics. Responsive settings bounds remain pinned at 720p, 1080p-density and
4K-density. An upgrade regression seeds the exact legacy synthetic active ID,
then proves authenticated pilot activation and synthetic removal occur in one
transaction with no previous pointer; a negative importer regression proves an
unexpected active ID is not overwritten. The product owner owns emulator
acceptance.

## Physical device tests

Required before pilot:

- power cut/replug;
- boot and app relaunch;
- Wi-Fi off for seven-day soak or accelerated clock-safe test;
- router restart and captive/no-internet network;
- remote-only setup;
- real hall readability at multiple distances;
- QR scan from representative seating;
- 4K background memory soak;
- TV overscan/safe area;
- wrong system timezone/clock;
- OEM process killing;
- managed kiosk if promised.

## Runtime research of IslamApp

The competitor runtime plan is separate and non-invasive: [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md). Its results inform requirements only; they are not product acceptance tests.

## Security tests

- authz cross-mosque access;
- expired/reused pairing code;
- token redaction;
- path/content-type/image bomb validation;
- snapshot downgrade/rollback authorization;
- signature key rotation;
- production/test trust separation; scheduled rejection; historical retired
  verification; unconditional revoked-key rejection and pinned-revision
  rollback rejection in Go and Android;
- isolated signing-request recomputation, signer-response binding, snapshot/
  receipt hash verification, rehashed attestation tamper rejection and
  authenticated direct-predecessor receipt-chain enforcement;
- direct trust-bundle transition parity, canonical timestamp/revision bounds,
  rebinding/resurrection rejection, mandatory scheduled preflight, disjoint
  environment material and revoked persisted-snapshot cold start;
- retired-key rollback admission under a newer validated bundle; exclusive
  ledger genesis/head CAS and API registry-head anchoring;
- admin CSRF/session protections;
- dependency and secret scans;
- diagnostic bundle privacy review.

## CI gates

Initial:

```text
make docs-check
make lint
make test
```

As code appears, split into:

```text
make test-go
make test-contracts
make test-android-unit
make test-android-instrumented   # device/emulator job
```

A provider/publication PR cannot merge without fixtures and diff/validation tests.

T008 adds a static 365-day synthetic CSV golden, a real authorized one-month
pilot candidate fixture and a public cross-platform signature fixture. The
pilot fixture is validation/diff evidence only while D-002 is open. Direct
parser-entry provenance bypasses, normalized metadata/Hijri-only diffs and
cross-runtime malformed UTF-8 are regression-tested.

T010 adds the retained 18-page Ulyanovsk 2026 PDF plus a strict 365-row
controlled transcription. Provider tests bind raw/transcription/normalized/
diff hashes, exact header and source scope, gap/duplicate/time failures,
candidate-only al-Isfar ordering, absent Hijri values and the four printed
May/August summer-transition markers. A field-by-field test covers all 31
overlapping August days and requires the reconciliation ledger to contain
exactly eleven Dhuhr-onset differences plus the Aug 24 collective difference.
An immutable effective-policy fixture binds both raw/candidate/transcription/
normalized/parser identities. Composer tests apply fields present in the photo
for all 31 August days, retain PDF al-Isfar when the photo has no such field,
reject policy/hash/range/field drift, rewrite only the two resolved review flags
and preserve the D-009 warning. The effective candidate remains `needs_review`
as provider output; publication rejects it without the exact signed named
approval. Approval receipt/trust lifecycle tests bind the candidate, diff,
warnings and mosque policy. Publication regressions prove the collective Dhuhr
field changes only through that explicit policy, becomes Dhuhr adhan rather
than iqamah, and emits the approved +5 rules and Friday Jumu'ah. This is
static/local evidence, not physical TV or production signer evidence.

T009 adds real HTTP-handler tests for one-use pairing, bearer isolation,
manifest/snapshot 200 and 304, Digest and registry-time signature rejection.
Android tests cover 200/304/401/404/500/timeout, raw length/hash tamper,
manifest null/schema/version/origin/app-version failures, signature and
manifest ID/key/mosque substitution, encrypted provisioning, re-pairing with
pending staged bytes, unique constrained
WorkManager scheduling and local-only no-op behavior. File-backed Room tests
close/reopen after interruption inside import and after activation commit but
before checkpoint finalization; both preserve last-known-good and resume the
durable stage without a second download.

T011 adds a Docker-backed PostgreSQL 18 gate. It applies the real up/down
migration, rejects a cross-mosque device/code relation, proves digest-only
secret storage, expiry and independent code/device/source buckets, observes one
winner under concurrent redemption, restarts the API/pool, rejects reuse,
authenticates the durable token, scopes revocation and verifies the audit table
rejects update/delete/truncate. It also holds a real row lock past the request
deadline and proves cancellation leaves the code/device unchanged. Normal
`go test ./...` remains container-independent;
`make test-postgres` is the explicit integration gate.

T012 extends the same real PostgreSQL gate through migration v2. It authenticates
verifier-only admin tokens, exercises global `service_admin`, local
`mosque_admin` and read-only support, denies cross-mosque reads/writes with the
same not-found outcome, and verifies issue/revoke/assignment audit evidence.
Retries prove one pairing/device row, one revocation event and the exact
historical assignment response even after a newer manifest version. Unit/HTTP
tests cover admin 401/404/409/500 boundaries, bidirectional mixed-replica HMAC
rotation and fail-closed persistent assignment registry mismatch. An assignment
retry also succeeds from stored evidence after its artifact leaves the current
registry; expired evidence returns `409` without creating a resource.
The database suite also rejects update/delete/truncate against idempotency rows
It proves a targeted v2-to-v1 rollback preserves T011 pairing state and that API
schema verification rejects v1. The runtime restart path connects as a separate
least-privileged role and proves it cannot provision admin actors or disable an
audit trigger while normal pairing/admin operations still succeed.

T013 extends the PostgreSQL ledger through v3 and proves two reports keep one
latest health row, server time owns last-seen, forged mosque and revoked stale
principals cannot write, and the mosque-scoped admin projection contains only
the allowlisted fields. HTTP tests cover strict unknown-field rejection,
credential/path mismatch and 400/401/500 boundaries. Android unit tests assert
same-origin device-path construction, absence of token and mosque ID from JSON,
exact field allowlist/enums, response mapping and that best-effort reporting
cannot change a completed sync result.

T014 advances the PostgreSQL ledger through v4 and explicitly rolls v4 back to
v3 without losing pairing, administration or latest health state. Integration
tests set two mosque-scoped cohort members, assign them in deterministic order,
prove exact retry creates no new versions, and roll back to a prior snapshot
with newer manifest versions. A 101-device cohort fails before any assignment.
HTTP tests prove artifact metadata is registry-derived and the same RBAC,
unknown-field, idempotency and historical-retry boundaries apply to cohort
writes. Normal device manifest reads continue to consume only each durable
per-device assignment.

T015 uses unit tests for server generation time and read-role scoping, HTTP
tests for authentication, uniform 404, retryable 500, `no-store` and the stable
schema marker, and the real PostgreSQL suite for the one-device/current
assignment/latest-health join. The encoded regression rejects the presence of
device tokens, pairing codes, installation keys, snapshot URLs and arbitrary
network fields. The API startup integration also reads the bundle through the
least-privileged runtime database role. The PostgreSQL harness waits for a real
`SELECT 1` connection before starting tests so an early readiness signal cannot
race server startup. No schema migration or retention job is introduced.

T016 adds `make test-postgres-restore`. A disposable PostgreSQL 18 source is
migrated to v4 and seeded with linked synthetic mosque/admin/device/pairing/
assignment/latest-health/audit/idempotency state. The gate creates a custom
archive, records its SHA-256 and size, rejects a deliberately
truncated copy, and restores the intact archive with `--single-transaction`
and without applying archived owner/ACL commands into a newly created database.
Go then uses the real repository/managers to
verify the exact migration ledger, device/admin authentication, current support
projection and restored update/delete/truncate guards on both append-only
tables. This is `CONFIRMED_RUNTIME` evidence for a controlled local backend
restore only; it does not measure production RPO/RTO or recover external
snapshot/source/key stores.

## Release evidence

Each release records:

- commit and build IDs;
- contract/schema versions;
- test commands/results;
- signing key ID;
- supported hardware/OS matrix;
- active source revisions;
- known limitations;
- rollback procedure and previous version.
