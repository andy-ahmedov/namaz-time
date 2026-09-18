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

## T049 qualification and real setup acceptance

ADR 0019 changes public-source admission, not provenance/signing guarantees.
Test the qualification branch without any human approval or endorsement, reject
missing first-party ownership/scope/validation and raw/parser/hash drift, and
keep the legacy signed Ulyanovsk snapshot byte-identical. Qualifying a public
source must not create an external approver. Independent real authorities at
different specificity tiers must all remain available for explicit selection.

Prove real-source sample comparisons, seasonal/date/timezone behavior, partial
coverage, stale/unavailable and schema-drift fail-close for implemented adapters.
Calculation policies need multi-season first-party published-value comparisons.
Normal Android setup must not show synthetic organizations/rows; explicit test
scenarios retain deterministic coverage. Preview and activation must use the
same provenance model; activation verifies signed immutable artifacts into
local persistence and preserves last-known-good on every failure.

Representative real E2E requirements: republic-wide official policy, multiple
authorities, city/district selector, city-specific table, annual/monthly table
and existing Ulyanovsk. Research-only evidence does not establish these gates.
Required final commands: `make docs-check`, `make test-skills`, `make test`,
`make lint`, `make test-postgres`, `go test -race ./...`,
`make test-android-all`, `make security-go`, `make secret-scan`. Schema changes
also require up/down, rollback/reapply and backup/restore. Record actual results
and skips; do not repeat unchanged successful broad gates without a reason.

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
- iqamah policy is materialized before protected signing: duplicate overrides,
  equal-priority winners, fixed times before daily adhan and offsets crossing
  the local date are rejected, and the protected signer is not called;
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
- the signed pilot artifact gate requires exact application ID
  `ru.namaztime.tv`, a valid APK signature, the pinned certificate fingerprint
  and all four authenticated snapshot/trust assets; assembly without external
  signing configuration fails;
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
import. JVM tests reject oversized, unsupported, corrupt and undersized documents,
preserve the previous app-local copy on rejection, and verify a missing custom
copy renders the packaged default. At that checkpoint the manifest regression
forbade storage/media permissions because OpenDocument was the only route.
T043/ADR 0018 supersedes that boundary for TVs without either system picker:
source/merged-manifest tests allow only legacy read capped at API 32 and
`READ_MEDIA_IMAGES` on API 33+, while still forbidding write/all-files access.

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
The main-display reference contract additionally pins the Settings target to
3.9–4.4 percent of viewport width, a 1.8–2.4 percent right margin and a
4.5–5.5 percent top margin at the controlled 960×540 profile. Focus tests pin a
single gold focus token, no focus scaling, selected semantics and complete
nine-item D-pad navigation. The controlled API 36/1920×1080 loop verifies the
reference-positioned main control, gold-only Settings focus, all eight
background previews, donation entry/configuration/display and the return to
the normal schedule. The first donation-settings pass found vertically clipped
actions; the corrected horizontal action row was rebuilt and rechecked inside
the safe frame. The installed emulator had no document-provider activity, so
dispatch without a crash is `CONFIRMED_RUNTIME`, while actual OEM document
selection remains `UNKNOWN` and is not promoted from JVM importer tests.

T029 historically replaced the donation transfer blob with five bounded DataStore fields.
Repository tests cover labelled RU/EN legacy mapping, unlabelled preservation,
structured round-trip persistence, removal of superseded keys, unsafe URL and
partial-content rejection. Settings/Compose tests pinned all five fields and the
D-pad route through the compact editor. Donation-display tests assert the
normalized brand, compact Settings visual, right card, QR, detail block and
footer bounds at 720p, 1080p and 4K profiles, keep the full image behind the
safe frame, and verify that the raw QR URL is absent from public text. QR
raster tests cover dark-navy modules and decode with the actual 44/140 center
badge obstruction at 128, 192, 360 and 540 pixels. Four controlled
build/install/screenshot passes use normalized 50/50 full and region overlays;
this remains bounded emulator evidence, not physical-TV or real-phone proof.

T043 supersedes the fifth donation detail. Repository tests prove the active
model has only recipient, bank, card number and SBP/phone, ignores a legacy
collection-link key or labelled line, fails closed when that was the only
detail, and removes the tombstoned key on the next configuration save. Compose
tests assert the Settings field and focus node are absent, D-pad moves directly
to gratitude, and the fixed details region contains exactly four 30-dp rows at
720p, 1080p-density and 4K-density. The HTTPS QR payload remains unchanged.

T043 also adds measured QR fit validation shared by save, preview and public
display; compact five-row Iqamah/reset coverage; and the ADR 0018 picker
capability/permission/import/focus matrix. Controlled Android 16 / API 36,
1920×1080 evidence confirms the long QR message, five Iqamah rows and explicit
reset, system Photo Picker fallback with one actual private-copy import, the
removed donation field and four-row public display. The targeted audit also
corrected a narrow Donation prayer-status cell and compact Mosque source/
timezone clipping. Evidence and screenshot hashes are under
[`docs/evidence/t043-operator-ux/`](docs/evidence/t043-operator-ux/). The
last-resort MediaStore browser is automated evidence on this emulator because
the system Photo Picker remained available; physical-TV picker behavior,
overscan, hall readability and representative-distance QR scanning remain
`UNKNOWN`.

T026 adds token regressions for high-opacity photographic-background surfaces
and the minimum bounded scrim, while the existing semantic contrast tests guard
the muted palette. QR raster tests prove modules are dark navy rather than pure
black and remain decodable. Compose semantics pin the next-event watermark,
bottom-strip ornament and a complete clock region after the three-column
composition is measured. Three API 36 build/install/screenshot passes were
compared directly with `main_with_qr.png`; this is bounded visual runtime
evidence, not automated perceptual equivalence or physical-TV acceptance.

T033 supersedes the main-screen geometry reference with `new_reference.png`.
The controlled 960×540 Compose contract pins all five campaign-layout regions
to the normalized 4–8 px tolerance, verifies the highlight reaches within 6 dp
of both prayer-card edges, and pins the raised next-event/date anchors. QR
raster tests obscure the actual 30 percent badge region at four physical sizes
and assert a four-orientation equal-arm corner declaration. Controlled API 36
build/install/screenshots are normalized to 1920×1080 for side-by-side and
difference review. They are `CONFIRMED_RUNTIME` emulator evidence; physical-TV
overscan, panel behavior and representative-distance phone scanning remain
`UNKNOWN`.

T044 adds a pure normalized-geometry regression for the next-event watermark:
the arch must remain 22–26 percent of full card width, its apex must remain in
the 18–23 percent height band, its shoulder in the 37–42 percent band and its
bases must reach the bottom edge. Compose tests additionally require the
watermark Canvas to equal the complete card bounds and keep the full
`Фаджр · завтра` title inside the card at 720p, 1080p-density and
4K-density. Five controlled API 36 screenshot iterations isolate the new arch,
refine width/apex/shoulders/opacity, evaluate the filled lantern and confirm
short/long-title Golden Dusk plus Blue Hour acceptance. Evidence is under
[`docs/evidence/t044-next-prayer-watermark/`](docs/evidence/t044-next-prayer-watermark/).
This is `CONFIRMED_RUNTIME` emulator evidence, not physical-TV readability or
overscan evidence.

T033 Checkpoint 2 adds publication tests that keep source Dhuhr onset while
accepting a fixed-time Dhuhr rule on Friday alongside Jumu'ah. Android
projection tests prove one operator Dhuhr value changes both resolved Dhuhr
iqamah and Jumu'ah without mutating Room, while the other four prayers remain
adhan-relative. DataStore tests pin the new fixed-minutes key, bounded default,
and deliberate non-migration of the legacy Dhuhr offset. Compose input tests
press + repeatedly and assert one-minute results.

T033 Checkpoint 3 adds DataStore tests for trimmed blank fallback, Unicode
length bounds and control-character rejection. State and Compose tests prove
the two local labels reach the main display while the schedule's mosque ID,
canonical name, locality and IANA timezone remain unchanged.

T033 Checkpoint 4 pins one large selected-background preview and one D-pad
LazyRow containing only the eight built-in IDs. The Compose regression traverses
all eight thumbnails left-to-right so off-screen entries must scroll into view,
asserts that no custom thumbnail exists, and verifies Down from the filmstrip
reaches the separate system-picker action. Existing importer MIME, decoded
dimensions, byte/pixel bounds, atomic app-private copy, corrupt fallback and
no-storage-permission tests remain unchanged. Five API 36/1920×1080
build/install/component-review passes corrected a clipped filmstrip, restored
16:9 preview/thumbnail proportions, and made the picker label fully visible.
This is `CONFIRMED_RUNTIME` emulator evidence; OEM picker availability and
physical-TV focus/readability remain `UNKNOWN`.

T033 Checkpoint 5 repeats the selector contract with only the five built-in
donation images and a separate custom-image action. Compose tests traverse all
five focus targets through LazyRow scrolling, assert the custom image is not a
sixth thumbnail, and verify Down reaches the picker. DataStore tests pin the
optional gratitude field's 240-Unicode-code-point bound, trimming, blank
fallback marker and control-character rejection. RU/EN display tests prove a
blank field resolves at render time to the localized standard string, while a
non-blank value replaces it. Three API 36/1920×1080 component-review passes
corrected field clipping, preview aspect ratio and action-label wrapping. This
is `CONFIRMED_RUNTIME` emulator evidence; physical-TV readability and OEM
picker completion remain `UNKNOWN`.

T033 Checkpoint 6 replaces the obsolete T029 donation geometry with normalized
status, equal-height gear, central donation-card, QR/row and gratitude-block
anchors at 720p, 1080p-density and 4K-density profiles. The 2026-08-30 owner
clarification adds a right-rail regression: the combined foreground begins no
earlier than 68 percent of the viewport and occupies no more than 30 percent,
while its status/gear, card and gratitude relationships retain their measured
anchors. State tests derive the current prayer from the resolved adhan/Jumu'ah
timeline, including pre-Fajr, post-Sunrise/pre-Dhuhr and post-Jumu'ah cases.
The Sunrise interval proves that Sunrise never becomes the current prayer. An
app-level Compose test proves the donation status uses
the active local schedule and the existing mosque-local engine projection;
another proves donation mode fails closed when that schedule is absent. The
shared QR decode suite remains unchanged. Three controlled API 36/1920×1080
build/install/component-review passes corrected the gear shape, scan copy and
detail punctuation. This is `CONFIRMED_RUNTIME` emulator evidence; real-phone
scan distance and physical-TV readability remain `UNKNOWN`.

T033 final-review regressions traverse every compact donation field, including
the Bank and Phone column, with explicit D-pad up/down/left/right focus links.
They also pin current-prayer selection in `PrayerTimeEngine` rather than
presentation and prove unmaterializable iqamah policy is rejected before a
protected signer receives canonical bytes.

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

The current pilot-local successor pins the asset SHA-256, v2 snapshot/approval
identity, 365-day coverage, exact source-onset Dhuhr values on August 20/24,
four +5-minute rules, fixed Dhuhr 13:15 on all weekdays and one Friday 13:15
Jumu'ah session. The same test path performs real Android
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

Instruction/skill-only changes use `make docs-check` and `make test-skills`;
application builds are required only when their inputs/behavior are affected.
The docs gate checks literal bundled entrypoint resources and documented
repository skill-script paths, including commands inside code fences.
The skill gate also runs the local ui-ux-pro-max runtime/data tests. Two retained
upstream maintenance modules explicitly skip when their non-bundled catalog
refresh/evaluation tools are absent. Report these skips: they do not establish
catalog-refresh correctness or a passed upstream relevance benchmark. No tools
are downloaded and no production/provider requests are made by this gate.
Frontmatter can additionally be checked with the environment's skill-creator
validator; it is not a repository dependency. Metadata/link tests do not prove
behavioral skill selection or improvement in agent latency/quality.

Initial:

```text
make docs-check
make lint
make test
```

As code appears, split into:

```text
make test-go
make test-go-race
make test-contracts
make test-android-unit
make test-android-all          # strict dependency verification + release gates
make test-postgres             # migrations, integration and clean restore
make security-go               # pinned govulncheck
make secret-scan               # complete Git history
make test-android-instrumented   # device/emulator job
```

`test-android-all` disables Gradle/Kotlin build caches and validates dependency
checksums. Android instrumentation remains a separate hardware/emulator gate;
it is not implied by the Robolectric unit/Compose suite.

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
warnings and mosque policy. Publication regressions prove source Dhuhr onset
remains the adhan, the separate collective field is not promoted, and the
approved fixed Dhuhr/+5/Jumu'ah policy is emitted. This is
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

T036 advances the PostgreSQL ledger through v6. Unit tests prove canonical
dataset hashing, verified-reference activation, same-tier ambiguity rejection,
research/stale/unavailable fail-closed behavior and rollback re-verification.
The real PostgreSQL gate stages and activates immutable synthetic revisions,
returns duplicate city names without auto-selection, verifies active/persisted
hash parity, rolls back the pointer, rejects update/delete/truncate with
SQLSTATE `55000`, rolls v6 down to v5 while retaining fleet state, and reapplies
v6. The backup/restore drill additionally proves its runtime role can select
registry state but cannot insert/update it, update audit rows, or create schema
objects.

T037 adds real-evidence and vertical-slice regressions. Unit tests compose the
reviewed Ulyanovsk bindings with the canonical GeoNames city, reject catalog
hash drift and mutable nested inputs, verify the approval/publication trust
chains, reject tampered snapshot bytes and pin the existing snapshot ID,
SHA-256, signing-key ID and signature. The real PostgreSQL/HTTP gate requires
admin mosque scope, searches `Ульяновск`, returns duplicate `Киров` candidates
without auto-selection, rejects unknown uniqueness, resolves the explicit
city/mosque/date to the existing timetable/snapshot, activates a successor and
rolls back while comparing the signed bytes before/after. The Android pilot
unit suite continues to authenticate and import the same packaged snapshot;
no TV network or display path is added.

T040 adds a synthetic multi-authority matrix for 0, 1, 2, 3, 5 and 8 eligible
choices with no truncation. Reordered registry inputs must produce the same
neutral policy-ID order while the resolver remains ambiguous for more than one
same-tier choice. Stale and missing-schedule options are excluded from the
selectable projection, lower precedence cannot become selectable, and T039
retains the full applicable option set. Equal authority labels retain distinct
choice/policy IDs; canonical authority names are used without generated
abbreviations. Same-name/alias geography remains separate. HTTP tests cover
the full eight-choice response, unknown-limit rejection, empty unavailable
state and the active executable choice. T049 extends this to mixed retained
legacy/public active choices: adding another eligible source must not suppress
the original mosque-approved choice or imply automatic selection. Staged
choices stay non-executable; unknown/invalid proofs stay rejected. PostgreSQL and pilot regressions
pin active/staged/stale projections plus the unchanged Ulyanovsk snapshot raw
SHA-256.

T041 adds four coupled test layers. Android client tests enforce the exact
device-scoped HTTPS origin, provisioned bearer/path identity, strict unknown
field rejection, 512 KiB response bound, 16 KiB request bound, principal/date/
choice echo validation, 401/403/409/5xx/I/O mapping and cancellation
propagation. Go HTTP and real PostgreSQL tests reject wrong, revoked and
cross-device credentials plus arbitrary mosque/revision injection; the only
write is one idempotent append-only `pending_review` proposal under the
least-privileged runtime role.

ViewModel/Compose tests cover empty/debounced/superseded search, loading,
network failure, no result, Cyrillic and alias results, duplicate canonical
cities, 0/1/2/5/8 schedule choices, no implicit selection, duplicate authority
labels, unavailable/stale rejection, explicit request failure/retry, preserved
query/back state and process recreation. D-pad tests traverse all eight choice
rows and adaptive bounds run at 720p, 1080p-density and 4K-density profiles.
The Room integration regression authenticates and imports the real signed
Ulyanovsk pilot, submits a pending proposal through the Android client and
proves the active selection, prayer rows, raw snapshot bytes, snapshot ID and
SHA-256 remain unchanged at
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

`CONFIRMED_RUNTIME` controlled Android 16 / API 36 TV-emulator evidence records
city search with the system IME, same-name candidates, one choice, a complete
multi-choice list with D-pad scrolling, unavailable and pending states. It also
records `Center` opening the IME and the first `Back` hiding it while focus
remains on the search field. This evidence is stored under
[`docs/evidence/t041-android-tv-setup/`](docs/evidence/t041-android-tv-setup/)
and does not establish physical-TV behavior. Physical overscan, OEM IME and
representative-distance readability remain `UNKNOWN` until the hardware
matrix runs.

The 2026-09-03 debug-runtime regression additionally proves that an ordinary
unprovisioned debug build selects its source-set-only synthetic gateway without
touching the HTTP transport, filters `моск` to multiple city candidates and
shows both Moscow demo authority choices; `Омск` and `Omsk` resolve to one
synthetic Omsk candidate with `Asia/Omsk`. Compose regressions require the
synthetic selection to bypass `pending_review`, render six local rows with
separate adhan/iqamah values, focus the explicit `Use on this TV` action and say
that no server request was sent. ViewModel tests require activation of the exact
previewed choice, truthful failure state and no pending request. Repository and
Robolectric persistence tests require the chosen fixture to replace only the
debug display projection, survive store recreation, cover today/tomorrow,
remain visibly synthetic/unapproved and leave the base Room repository intact.
The runtime recreation regression retains the original gateway, recreates the
display repository, then requires a subsequent choice to reach that repository.
An app-shell regression requires success to clear old city labels and return to
the main display for the selected city. Back before activation still follows
preview → authority choices → city search. Release/pilot compilation continues
through the provisioned client factory and receives no synthetic preview rows
or activation projection; this is not production-server evidence and no real
Moscow or Omsk schedule is claimed.

T042 adds a single validated Android version source plus unit tests for typed
build identity and the Diagnostics projection. Repository shell tests exercise
the APK identity checker with valid and mismatched code/variant cases. Strict
debug/release builds verify package, version/code, exact Git commit and actual
dirty state from their manifests. Signed-pilot packaging additionally rejects
a dirty tree, requires the external pilot signing configuration, verifies the
pinned certificate and bundled authenticated assets, and emits a versioned
APK/checksum/manifest handover bundle outside the repository. A controlled API
36 emulator upgrade
from code 3 to code 4 retains the package first-install identity and renders
the existing local schedule; screenshots and package facts are recorded under
[`docs/evidence/t042-android-build-identity/`](docs/evidence/t042-android-build-identity/).
The physical-TV upgrade path remains `UNKNOWN`.

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

## T045 presentation verification

T045 supersedes the historical badge-obstruction QR tests above: the shared
primitive must have no overlay or clipping of encoded modules or symbol-aligned
side clearances; the 2026-09-09 paper refinement rounds only the outer corners. Native
Robolectric whole-view drawing samples the final QR pixels, decodes three
HTTPS payload lengths across public surfaces and compares the pixels against
sharp module rasterization, including a parent-constrained size. Real
emulator screenshots are decoded separately. Too little space must not crash
the prayer screen. Physical scan acceptance remains a separate retest.

Native graphics tests measure every required compact block at three resolution/
density profiles, both languages, QR present/absent, Iqamah ON/OFF and all six
retention phases. Text layout overflow and containment inside the next-event
card are checked separately from rail bounds. DataStore close/reopen verifies
presentation defaults and persistence without clearing the Iqamah configuration;
D-pad tests reach the switch and both Appearance choices. Standard tests retain
the existing geometry. Evidence and the final gate record live under
`docs/evidence/t045-tv-presentation/`.


## T046 compact reference verification

T046 replaces only T045's compact block arrangement. Native Compose tests
assert equal main columns, left schedule/right next-date stack, full-width
bottom campaign, absent-panel expansion and localized heading. They retain
RU/EN × QR × Iqamah × six retention phases at all three resolution/density
profiles. Additional native tests cover actual text metrics and card
containment for tomorrow/Jumu'ah, six explicit subtitle lines and a long
accepted title, separate Iqamah values, ordered text blocks and no mosque-name
collision with Settings. Shared whole-view QR raster/decode tests remain
unchanged, including dense payloads at 720p.

`make test-android-t046-emulator` is an explicit controlled-device gate with
`T046_EVIDENCE_PYTHON` (Pillow + zxing-cpp) and `T046_EVIDENCE_ARGS` for output,
profiles and optional real secondary display IDs. It captures original PNGs,
asserts physical image dimensions, independently decodes QR, measures the
foreground bounding box and compares every compact left-half pixel to an
otherwise identical background frame. A bounded 0.55 px blur is recorded
separately. A genuine 3840×2160 Presentation avoids the primary TV display's
1920 px UI cap; upscaled/clamped images are rejected. Two API 36/1080p
reference comparison passes and a fixed-clock STANDARD baseline pixel diff
are separate visual gates. Physical camera/TV acceptance remains `UNKNOWN`.

## T047 compact premium verification

T046 adaptive/QR coverage remains active. Native text tests additionally require
OFF adhan growth, relative countdown/name/label and clock/date hierarchy,
ordinary campaign readability and exact full clock/countdown strings. Three
density-specific regressions also require a long single paragraph to use at
least 12 normalized sp without losing any characters or overflowing. A native
app-shell regression traverses STANDARD → compact → Settings → compact →
STANDARD and checks original/bright background pixels through real navigation.
A color-compositing regression bounds header, glass and active-row text at
>=4.5:1 on a white custom image using the actual compact scrim/surface roles.

`make test-android-t047-emulator` reuses the controlled presentation capture tool
with Blue Hour and Night Minaret in addition to Golden Dusk. Set
`T047_EVIDENCE_PYTHON` to a Python with Pillow/zxing-cpp and
`T047_EVIDENCE_ARGS` for output/profiles and actual secondary display IDs.
The runner verifies dimensions, exact QR/0.55 px blur decode and unchanged left
half against each matching background. It also captures maximum campaign copy.
Full/crop screenshots for three deliberate 1080p visual passes and identical
STANDARD/Donation/Settings before-after comparisons are separate visual evidence.
Physical-TV and camera-distance acceptance remain `UNKNOWN`.

## T048 final compact polish verification

T048 retains every T045–T047 functional, adaptive and QR regression. The
2026-09-18 compact-seconds change supersedes its minute-only clock and
ceiling-rounded countdown presentation. Tests now require exact second-bearing
clock/countdown strings, minute-boundary updates, unavailable placeholders and
values longer than 24 hours. Compose semantics preserve those exact values.
No engine, event-selection or timezone test expectation changes.

Native Compose tests require the regular/medium/hero weight hierarchy, a
readable two-line long mosque identity separated from the Settings target, no
status chip for approved state, a bounded header chip for attention state and
complete six-line campaign copy at >=12 normalized sp. Token tests require a
warm saturated accent, softer perimeter than upper reflection, lighter hero
surface, deeper campaign surface and a left-to-clear active gradient. The
background allowlist/resource test contains ten built-ins and keeps Golden
dusk as the default.

`make test-android-t048-emulator` reuses the controlled fixed-clock runner with
Luminous dusk and Blue hour in addition to Golden dusk. Set
`T048_EVIDENCE_PYTHON` to an environment containing Pillow and zxing-cpp, and
pass output/profiles/secondary-display IDs through `T048_EVIDENCE_ARGS`. Every
captured QR must decode both directly and after 0.55 px Gaussian blur. The
runner rejects wrong dimensions, foreground pixels in the protected left half
or retention bounds outside the safe rail. It covers approved/attention,
Iqamah on/off, no QR, RU/EN, tomorrow, long identity, six-line and maximum
campaign copy, dense QR, every retention phase and all three backgrounds.

`CONFIRMED_RUNTIME` evidence is recorded on the controlled API 36 TV emulator
at 1280×720, 1920×1080 and a true 3840×2160 secondary Presentation at density
480. A pixel diff compares the current fixed-clock STANDARD capture to T047 and
must report zero changed pixels. Release isolation must still prove that the
debug evidence activity is absent. Physical-TV readability, panel overscan and
phone-camera QR remain `UNKNOWN`; PostgreSQL is not required because T048 does
not change backend, schema, restore or publication behavior.


The 2026-09-09 TV image-selection regression advertises both system pickers
while declaring a television: API 28/32/33/36 must still open the built-in browser
or request the matching read permission. Existing app-level import/failure/focus
tests use this capability combination for both image slots. Non-TV cascade tests
remain. Physical-TV acceptance is separate from JVM/Compose evidence.

Runtime acceptance on 2026-09-09 also reproduced a detached appearance entry
FocusRequester; the entry now targets a persistent focus group. API 36 at
1920x1080 confirms owner wal_1.jpg selection via D-pad after Load more, import,
display and retention across force-stop/cold restart. The gallery is a modal
window, asserted in Compose tests, isolating focus from underlying settings.
Rapid D-pad bursts across the full built-in background filmstrip, long MediaStore
rows and offscreen bucket rows must not target a detached `FocusRequester`.
Cancel and Load more must return focus to a visible image after a long gallery
scroll. Compose tests use synthetic images for these cases.


Schedule transparency tests cover validated writes, invalid-value fallback,
DataStore close/reopen without iqamah changes, 5% arrow steps and bounds,
rapid key input, leaving the slider with Down, and Appearance persistence.
Rendering tests pin alpha endpoints and unchanged colors outside the schedule
scope. Runtime acceptance checks both layouts and 720p/1080p/4K settings.


The 2026-09-09 QR tests prove reclaimed remainder padding, preserved four-module
side clearances and sharp per-module widths differing by at most one pixel.
Whole-view native captures decode all four surfaces and compare every non-corner
pixel to the expected matrix. Rounded outer corners are checked separately.

At fewer than three pixels per module, the renderer retains uniform integer
pitch and centered remainder padding for the native 720p dense-code regression.
At three or more pixels per module, the raster fills the square while keeping
the encoded four-module quiet zone. The owner default donation URL is included
in whole-view decode tests on Standard, Compact, Donation and preview at all
three density profiles, plus a 224-pixel raster/margin test. Error correction
and payload never change.
