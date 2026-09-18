# PLANS.md

This is the living execution plan. Update statuses, evidence and decisions after substantive implementation or instruction changes; read-only answers and incidental typo fixes do not need entries. Do not mark runtime behavior complete from static analysis.

Status values: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `DEFERRED`.

## Restore root verification gates — 2026-09-18

Status: DONE (root workspace verification).

PROPOSAL: preserve owner inputs outside the repository and run the existing
root gates without weakening artifact checks. Nine untracked/ignored root files
(debug APK, its Zone.Identifier and seven wal images) were copied to
`../namaztime-artifacts/workspace-cleanup-20260918T205358Z/`, SHA-256 verified,
then removed from their original locations. The external `manifest.json`
records original/saved paths, sizes and hashes for reversible restoration.
No tracked application, signing, schema or schedule file changed.

Root `make test` PASS at implementation commit `3a59351`: 634 Android cases,
630 passed and four optional external-bundle/Go-exporter cases skipped; skill
suite has two existing upstream-module skips. Root `make lint` PASS, including
Go vet/staticcheck and Android debug lint. Final documentation-only update
passes `make docs-check` and `git diff --check`.
Full output: `/tmp/namaztime-priority-checks-20260918.log`.
ADB lists only `emulator-5554`; physical-TV acceptance remains UNKNOWN.
Signing-key backups, signed release packaging and source renewal are outside
this workspace verification step. No key, device data or remote state changed.

## Compact clock and countdown seconds — 2026-09-18

Status: DONE (local implementation and emulator acceptance).

PROPOSAL: display exact HH:mm:ss in both compact cards, removing minute-only
formatting and countdown rounding. Use the existing second-ticking local state
and fitted typography. No persistence migration; rollback is presentation-only.
Acceptance: exact values and minute-boundary updates, responsive layout checks,
full test/lint gates, APK assembly and emulator installation/visual check.
Focused CompactPolish/CompactReference tests PASS (11 tests), including minute
rollover, unavailable countdown and 720p/1080p/4K layout profiles. Full make test
and make lint PASS on /tmp/namaz-seconds-verification, a copy of tracked sources
with this change: 634 Android cases, 630 passed and four optional skips; skill
suite retains two skips. Root gates stop on the owner-supplied root APK; no
owner artifact was removed and no gate weakened. CONFIRMED_RUNTIME: updated
APK installs in place on emulator-5554, seconds advance in both cards at
1920x1080, and D-pad Settings/Back returns to the display. Screenshots remain
outside Git at /tmp/namaz-seconds-emulator.png and
/tmp/namaz-seconds-emulator-after.png. Physical-TV acceptance remains UNKNOWN.

## QR white-border reduction — 2026-09-18

Status: DONE (local implementation and emulator check).

PROPOSAL: use fractional full-square module mapping once the displayed QR has
at least three physical pixels per native module. The supplied 1920×1080 image
shows the default donation QR at 224 pixels with 61 native modules: the old
uniform three-pixel pitch left 20 extra blank pixels on each side, in addition
to the mandatory four-module quiet zone. The new mapping keeps those four
modules and reallocates the excess to the symbol; the encoded URL, correction
level and all local preferences remain unchanged. Below three pixels per module,
keep the old integer-pitch path for dense-code scan reliability. No migration is
needed; renderer-only rollback. Focused raster and full-view decode tests PASS
on 720p/1080p/4K profiles, including the default donation URL. Debug APK build,
in-place installation and launch PASS on the API 36/1920×1080 TV emulator.
`CONFIRMED_RUNTIME`: white paper stays 224 px square, while the dark QR region
grows from 159 to 195 px per side; the left white margin contracts from 32 to
15 px. The captured QR decodes to the same default URL, both directly and
after 0.55 px Gaussian blur. Before/after screenshots stay outside Git at
`/tmp/namaztime-qr-before.png` and `/tmp/namaztime-qr-after-reduction.png`.
Full `make test lint` PASS on a source copy at
`/tmp/namaztime-wip-verification.2IpvmZ`, excluding only untracked owner photos
from the repository root: 636 Android cases, 632 passed and four optional skips;
the skill suite retained two existing skips. `git diff --check` PASS. Root
`make test`/`make lint` still stop at the existing 5 MiB gate for the untracked
7.6 MiB `wal_3.png`; no photo was moved or committed and the gate was unchanged.
Physical-TV phone scan remains `UNKNOWN`.

## Owner background replacement — 2026-09-18

Status: DONE (local asset, APK build and emulator installation).

PROPOSAL: replace the packaged image behind the existing `winter_twilight`
selection with a 1672×940 WebP conversion of the owner-supplied `wal_3.png`.
Keep the stable persisted ID and label, so saved selections continue to work.
The raw PNG remains untracked; no migration or schedule behavior changes.
The debug APK contains the converted resource with SHA-256
`d8131737750bca21357f2a260cc8a618cfb8a4175c92039850d35773f7f06c95`.
Android debug assemble, unit tests and lint PASS; ADB install and app launch on
the API 36/1920×1080 TV emulator PASS. `make docs-check` PASS in a source copy
excluding only untracked owner photos. Root `make test`/`make lint` stop at the
existing 5 MiB gate on the untracked 7.6 MiB source `wal_3.png`; no gate was
weakened and the owner original was not moved. Initial runtime selection hit a
gallery FocusRequester crash when crossing the lazy filmstrip. Follow-up
`CONFIRMED_RUNTIME` on the
same emulator: after fixing detached horizontal focus targets, D-pad selected
Winter Twilight and displayed the converted image in Settings and on the main
schedule. Screenshots remain outside Git at `/tmp/namaztime-wal3-selected.png`
and `/tmp/namaztime-wal3-main.png`; physical-TV appearance remains `UNKNOWN`.

## Shared QR rounded paper and tighter margins — 2026-09-09

Status: DONE (local implementation and emulator acceptance).

PROPOSAL: reclaim raster-size remainders previously added as extra white padding,
using sharp pixel boundaries with module widths differing by at most one pixel.
Retain four-module side clearances and unchanged encoded payload/error correction.
Round the outer paper corners with a 20%-of-side target capped to the four-module
margin, so rounding never touches encoded modules or the four side strips aligned
with the symbol. Shared Standard/Compact/Donation/preview component, no overlays.
No persistence, snapshot, URL or signing changes; rollback is renderer-only.
Acceptance: native whole-view decode and module preservation on every surface
at 720p/1080p/4K profiles, visible emulator result and independent screenshot decode.
Focused QR and whole-view native tests PASS for Standard, Compact, Donation and
preview at three resolution/density profiles. Dense 720p fractional-pitch failure
was reproduced and fixed with uniform integer pitch below 4 pixels/module at
this checkpoint; the 2026-09-18 refinement above narrows that fallback to below
3 pixels/module. Debug assemble PASS.
CONFIRMED_RUNTIME: API 36 / 1920x1080 emulator screenshot before/after independently
decoded by zxing-cpp, both directly and after 0.55px Gaussian blur, same payload.
Current symbol grows 165→181px per side. After screenshot SHA-256:
667de03ce6fc04cf70b3901f00779a33772fc41d52f7ebf1d0b70996c2a009cc.
Evidence stays outside Git at /tmp/namaztime-qr-before.png and
/tmp/namaztime-qr-after.png. Physical-TV camera acceptance remains UNKNOWN.
Root make test/lint was blocked by the owner-supplied 7.6 MiB wal_3.png size gate.
Exact source changes were copied to /tmp/namaztime-qr-verification, excluding only
the three untracked owner photos; make test/lint PASS there: 631 Android cases,
627 pass, 4 optional external source/interop skips, plus 2 existing skill skips.
No gate weakened and no owner image moved/deleted. Debug APK is installed on
the emulator; no pilot publication or signing changes. git diff --check PASS.

## Schedule block transparency — 2026-09-09

Status: DONE (local implementation and emulator acceptance).

PROPOSAL: add an Appearance slider for schedule block background transparency
(0–100%, 5% D-pad steps, default 20%). It applies to Standard and Compact card
fills and the preview, while text, QR, outlines and the photograph retain their
own rendering. Persist a validated device-local integer in DataStore; missing
or invalid values use 20%. No snapshot/domain/permission change. Older versions
ignore the key; no database migration or destructive rollback is needed.
Acceptance: arrow adjustments including rapid repeats, bounds, up/down exit,
persistence/reopen, both display layouts, 720p/1080p/4K layout and full checks.
Focused tests and debug assemble PASS. Final make test/lint PASS: 630 Android
cases, 626 passed and 4 pre-existing optional external-source/interop cases
skipped. Skills retain 2 absent upstream-module skips.
CONFIRMED_RUNTIME: emulator API 36, rapid six-key input 30→60%, 0%/100% Compact,
50% Standard, and complete Appearance layout at 1280x720 and 1920x1080.
Cold restart retains the setting; emulator is left in Appearance with 20% and
Compact selected, using the previously imported owner photograph.
The attempted 4K override remained 1920x1080 (only density changed), so it is
not 4K evidence; normal size/density were restored. Physical TV/true 4K remain
UNKNOWN. Screenshots/logs are outside Git at /tmp/namaztime-opacity-*.
No pilot artifact, push or deployment; updated debug APK installed on emulator.


## TV storage image selection — 2026-09-09

Status: DONE (local implementation and emulator acceptance).

CONFIRMED_PUBLIC: supplied physical-TV screenshot shows a system no-handler
notification after custom donation image selection. INFERENCE: firmware picker
capabilities do not guarantee a usable picker; installed APK version is unknown.
PROPOSAL: leanback devices open the existing D-pad MediaStore browser directly,
with the existing explicit read-permission request and bounded private-copy import.
Non-TV cascade stays available. No permissions, schema, keys or schedule changes;
rollback needs no migration. Both background and donation use this route.
Regression test first failed as expected (1 of 9), then focused tests passed.
Initial make test/lint and debug build passed. Runtime with owner wal_1.jpg
exposed focus escaping the gallery into underlying settings after pagination.
The gallery now owns a modal window. A separate reproduced navigation crash
(FocusRequester not initialized) is fixed by attaching the appearance entry to
the persistent focus group instead of a disposable lazy thumbnail.
On 2026-09-18, a rapid reverse pass across the ten built-in backgrounds
reproduced another detached requester crash on emulator and in a Compose test.
Long horizontal and vertical MediaStore lists reproduced the same exception.
Directional focus now uses Compose lazy-list search for offscreen items; explicit
header/footer targets remain only for controls that are mounted. Focus tests
cover bursts, long rows, bucket transitions and Cancel/Load more return.
Focused Settings/app/gallery tests and debug build PASS.
CONFIRMED_RUNTIME: API 36 / 1920x1080 emulator, owner wal_1.jpg copied to Pictures
and indexed; D-pad opens local gallery, loads page 2, selects Pictures/wal_1.jpg,
imports and renders it as background. Full process stop/cold restart retains it.
Photo SHA-256: 5e793e286ac8e9e14824b96cfcb886ce6edfa07eb391ef52bcfcae240421723c.
Post-restart screenshot SHA-256:
23a6eab2e1e063ac188220528d15dc352b35a5b0e1cad01d049949f3c3595be2.
Screenshots and logs stay outside Git in /tmp/namaztime-wal-after-restart.png
and /tmp/namaztime-photo-final-checks.log; owner originals remain untracked.
Final make test/lint PASS; Android: 618 passed, 4 optional externally configured
source-bundle/Go-interop cases skipped (fixtures not supplied). Skill suite has
2 explicitly absent upstream-module skips. Debug assemble PASS; git diff --check
PASS. Physical-TV, 720p and 4K runtime acceptance were not performed this turn.
Emulator is left on the imported background; no pilot APK update or publication.
UNKNOWN: physical-TV import and unindexed USB availability.

## T049 — nationwide verified first-party prayer-source onboarding

Status: DONE (2026-09-08), within the authorized local acceptance scope.
Final result, artifact pins and genuine limitations are in
[ACCEPTANCE.md](research/t049/ACCEPTANCE.md). Full owner requirements are preserved in
[the assignment](docs/tasks/T049-nationwide-first-party-onboarding.md).
Baseline verified 2026-09-08: clean main and actual origin/main both
`325a343f3fabec337e56df0abcd93c2812be2c67`. GitHub CI run 34162587927 failed
in docs-check on the ignored local `new_compact.png` link; failure reproduced
from a clean `git archive`. The reference is now plain text with its outside-Git
boundary, not a broken repository link. Remote CI is not rerun or claimed green.

| Phase | Status | Required evidence / remaining work |
|---|---|---|
| 0: persistent policy reconciliation | DONE | ADR 0019, AGENTS, streamlined provider skill, source checklist and governing docs separate qualification from optional endorsement. `make docs-check test-skills` PASS: 2 package checks, 130 runtime/data cases, 2 explicit non-bundled upstream module skips. Provider frontmatter validation and `git diff --check` PASS. No application/schema/signing changes in this checkpoint. |
| 1: previous research/demo audit | DONE | `research/t049/BASELINE_AUDIT.md`: 83 mapped subjects versus old 31 (52 gaps), previous debug factory/constant-row projection, persisted legacy admission and pilot inspected. No live NamazTime PostgreSQL instance was found at baseline; the fresh isolated v9 DB gate subsequently passed. |
| 2–3: nationwide research and scope resolution | DONE | Four pinned ledgers cover the exact 83-subject catalog set, 166,559 localities and 204,641 aliases; superseding machine registry retains all research/search-trail pointers and current operational proof links. Qualified source scopes, currentness and values were separately rechecked; unsupported localities remain unavailable. No inferred day offset, repair or regional expansion. |
| 4/6: qualified providers and verified activation | DONE | Six deterministic public adapters, 48 complete qualified sources, two-signature publication/receipt verification and corrected real Go Stage/Activate/export pass, plus unchanged retained legacy Uly. PostgreSQL v9 up/down-refusal/reapply/backup-restore and Go test/race/lint/security pass. Full catalog remains intact; 248 covered localities and 250 current choices at the recorded report time. Device runtime acceptance remains phase 5. |
| 5: normal setup/runtime | DONE | Clean b1855c0 final48 APK installed in place on debug API 36 emulator. Both real Kazan authorities activate/restart independently; unrelated homonym remains unavailable with unchanged Room. Arsk selector, exact Saratov monthly/source-handler absence, Sochi annual, Omsk partial and corrected KBR previews match real values. Full provenance D-pad/Back works. Original Uly six-row preview and final activation/cold restart pass; original ordered data and device iqamah projections are unchanged. |
| Evidence and repository gates | DONE | Exact make test/lint, Go/race/security, PostgreSQL v9 migration/refusal/reapply/restore and strict Android 621 tests/zero skips PASS. Skills: 130 pass, 2 explicit upstream skips. Real final48 export/audit PASS; the rejected final47 legacy-suppression defect is fixed and covered RED→GREEN. Final documentation and secret-scan handoff recorded in ACCEPTANCE.md. |

Scope includes coherent local checkpoint commits, not push/PR/deployment,
signing-key changes, organization contact or production-data deletion. Unknown
source evidence makes that source/city unavailable, not the whole task blocked.
T038 is DONE under the explicit T049 closure rule: the second non-Ulyanovsk
regional adapter (DUM KBR annual PDF, explicit republic-wide scope) is implemented
and verified against all 365 retained first-party rows. Forty-eight public
sources now have evidence-reviewed, locally signed qualifications and verified
local admission. The controlled debug emulator is left on the exact original
Ulyanovsk snapshot after the final48 representative runtime round and cold restart.

Final implementation commit `b1855c0`; installed/frozen debug APK SHA-256
`bacc07465c4eb37f9a4f57518faaa8b84b270c61be19793db7759699debc5b3f`.
Original ordered Uly snapshot/days/rules/overrides/Jumuah projection before and
after the round is identical:
`58d4b431876cf92d1ec2b70ab864b6548eb3699164a555906ff51bfa227efc47`.
Device-local iqamah/show setting projection is unchanged:
`0825ae2eb1e2ad41318e5942c59c1e79efd34b9e92e5db05b734cacdabedb9b5`.
Pilot APK/certificate/install identity remains unchanged. A test-navigation
extra Back briefly revealed the pre-existing pilot task and opened its Settings;
no text/Save/source/iqamah action was issued. Whole private pilot preference
equality is UNKNOWN because ordinary access is unavailable; no bypass or restore
was attempted. This incident and the foreground guard correction are documented
in ACCEPTANCE.md. Physical-TV acceptance, clock-health discrepancy, future source
updates and remote operation remain separate; no push/PR/deployment occurred.

### Retained chronological checkpoints

The following entries preserve the investigation, earlier failures and then-current
pending work. The final status above and ACCEPTANCE.md supersede their progress
wording; old artifacts are not silently relabelled as final evidence.

Corrected final48 export (04:05:00Z) is now the operational checkpoint:
raw manifest `ba8a5b9bb7e7ac24651836bcb1cd31788fd2472a451cbb3a2aae5a56e051718f`,
registry `registry-t049-reviewed-local-2026-09-08-v3`.
The compiler's 04:08:18Z state verifies 49 current policies (48 public + one
legacy), 248 covered / 166,311 unavailable localities, 250 city/choice bindings,
and two multi-choice cities (Kazan and Ulyanovsk). Subject coverage is one full,
12 partial, 70 unavailable. Legacy Uly now has one uninterrupted annual binding;
CDUM's September table is separate. The 48th public source is CDUM Rostov,
qualified through its exact existing RU-ROS alias, with 180 compared onset
fields and no guessed identity. Its signed/published timestamp is 04:02:55Z;
final isolated ledger SHA-256 is
`43cf186c04352d7cb1a983ad573517f6f5fcfd8766f15e740a17efcb974b3a8a`.
Current public ranges: two annual, 45 September-only, one June–September.
The rejected final47 checkpoint below remains chronological failure evidence.

Independent final48 delta audit PASS (outside-Git report SHA-256
`0d863168cb26751dd4345d5e3245964f9fdd550ceb69c85a3952cc89ddb7aa8d`),
reviewed in full by root: exact expected 250 city-policy pairs, complete catalog,
unchanged prior snapshots/proofs/trust and all 2,202 public days / 13,212 onset
fields. Fixed-bundle expiry correctly leaves three annual policies on October 1.
Expanded production Android regression is GREEN, including original Uly+CDUM,
Kazan two independent authorities, no homonym fallback and Omsk partial bounds.
`make test-android-all` PASS: 67 suites / 621 tests, zero failures/errors/skips,
strict dependency verification, debug/release lint/build identity and unchanged
Room schema files. Build identity remains code 10 / HEAD60bc5b9 dirty; no pilot
APK install is implied. Exact `make test` and `make lint` now both PASS with
the real final48 and synthetic interop fixture environments: Android reused
621 passing cases, skills 132 cases with two explicit upstream skips, research
38+12 and public-contract three cases. Full-history secret scan PASS across
125 commits; staged implementation/metadata scan PASS, with no raw source or
private operator/key artifact staged. Gradle is idle for the coherent local
checkpoint before a commit-bound debug APK and round-two emulator interactions.

Latest checkpoint (2026-09-08 03:47 UTC): all 34 further exact DUM RT locality
sources and seven fresh CDUM exact-city sources passed root raw/catalog/value
adjudication, closed signing checks, two-signature materialization, separate
finalization and receipt-chain verification. Total: 47 qualified public sources
(two annual, 44 September-only, one June–September), plus unchanged legacy Uly.
Final isolated ledger raw SHA-256:
`84f39028209cc9c6da991c650fe25006f7b69839ad7918b4cc15d2210d28747d`.
No dataset repair, key change or remote deployment occurred.

Expanded local export actually passed ComposeCatalog/Stage/Activate/reference
verification at 03:36:59Z; raw manifest
`a9bd9866bed518d5de18256ac85099002bf9b08929ee386ca117cd3e51b3a260`.
The compiler checked all 83 subjects and derives 247 currently covered localities,
166,312 unavailable, one full/11 partial/71 unavailable subjects. Admission is
not runtime acceptance: the full Android gate reached 618 tests with two failures
because the exported September choices exclude the unchanged legacy Uly policy
when CDUM applies. The actual bindings split legacy coverage into January–August
and October–December. This is not a stale `.single` test: the required retained
choice is missing. The package is held from installation while the Go projection
rule and synthetic regression are investigated; no signed data is changed.

Root cause confirmed: `ProjectCityScheduleChoices` marked legacy `Executable`
only in its single-choice branch, while public choices used per-option active
eligibility. Adding CDUM left legacy selectable but non-executable, and the
exporter faithfully removed that interval. Android's HTTP decoder and OpenAPI
also retained the same sole-legacy restriction. The correction aligns active
eligibility for both proof branches, preserves explicit selection and staged
pending-review semantics, and does not change authority precedence or freshness.

Projection correction is now RED→GREEN: the verified mixed fixture preserves
legacy on all 365 dates, exposes both choices in September in both input orders,
keeps automatic resolution ambiguous, and rejects wrong/missing legacy approval
or snapshot context. Android mixed v2 decoder tests preserve the same strict
semantics (37 focused client/bundle tests PASS). Root reviewed both changes.
`make test-go lint-go test-go-race security-go` PASS after the Go fix; no known
Go vulnerabilities. The repeated affected `make test-postgres` also PASS,
including v9 migration/refusal/reapply/least-privilege and exact backup/restore;
dump SHA-256 `ecd61fe3d3d248df6f1f87a5a6d357041146184256107d3c6714455e4ca98dc9`,
151,797 bytes. Corrected immutable export and real Android gate remain pending.

Focused preview fixes passed 56 tests, including 720p/1080p/4K, all six values,
coverage dates, long/RTL names, availability wording, source-handler absence,
and D-pad access to lines 28 and 55 of an oversized attribution paragraph.
The latter was reproduced RED before correction; Back restores action focus.
The corrected layout is not yet runtime-verified. `make test-research` now
includes both 38 compiler/tool tests and 12 independent DUM RT comparator tests;
all pass. Later paragraphs below retain chronological checkpoint evidence.

Correction after checkpoint `da4c906`: the first operational KBR inspection
passed structural validation but used an unobserved PDF capture timestamp and
the PDF hash for homepage evidence. Its qualification ID
`qualification-7b5c03f34a47252c282249a9a777c508` is invalid; the manifest,
inspection and unsigned publication/signing requests are quarantined outside
Git and must not enter signing or admission. No snapshot was signed or activated.
Actual re-capture/review is recorded below. Checkpoint `5a9eb73` also
introduced fixture-specific CDUM HTML exceptions; these are replaced in the
working tree by general raw-tag provenance checks, with adversarial fixtures
and retained-page checks. A passing test suite alone did not
prove either research correctness or absence of parser bypasses.

KBR re-capture: public PDF retrieved at the observed `2026-09-08T01:52:19Z`,
HTTP 200 / `application/octet-stream`; homepage/share retain their own distinct
hashes and timestamps. Lead visually compared six values on four dates, one
per calendar quarter, against rendered PDF pages; September 8 also matches the
current homepage. `inspect-public` with the complete pinned canonical catalog,
retained PDF/text and normal 15-minute delta threshold PASS: 365 days, no
errors/warnings, qualification `qualification-809bf016513c15764254adbb68991b76`
at `2026-09-08T02:03:25Z`. Inputs/output remain outside Git in
`namaztime-artifacts/t049-kbr-recapture.NhKQww`; this is qualification, not
signature/admission or device-runtime evidence.

Setup wire contract correction: OpenAPI reuses the complete snapshot
qualification schema; public source/policy branches reject fake approvals and
preserve empty `mosque_ids` as either `null` or `[]`, while legacy requires one
mosque. Three Python schema regression tests (including the observed null
serialization failure) and `make test-contracts` PASS. These checks are now
part of `make test` and CI; Python PyYAML/jsonschema dependencies are documented.
The root README's residual universal approval/generic fallback wording is
reconciled with ADR 0019. `go test ./internal/providers/cdum ./internal/devices
-count=1` PASS after parser and device-v2 response regression changes.

Saratov dispatcher: exact city/subject/timezone/canonical URL guards, a positive
synthetic case and 16 negative cases pass, including a catalog alias that must
not extend publisher scope to another city. The opt-in fresh retained check
matches all 180 fields against the separately extracted print table. Lead
reviewed the captured main/print/page-2348 composite currentness proof and
three dated value comparisons; full-catalog `inspect-public` PASS at
`2026-09-08T02:11:49Z`, 30 days, qualification
`qualification-d9c025f49669fb9f5b2a0a9c9cea88aa`. Raw evidence remains in
`namaztime-artifacts/t049-saratov.ihEI1m` outside Git. Its explicit hyperlink
attribution requirement must be supported before selectable runtime admission;
no signature or activation is claimed. Source iqamah/performance notices remain
separate from the exact onset table.

KBR local materialization: existing schedule key `pilot-local-schedule-2026-02`
and the unchanged production trust revision 3 were used through an isolated,
ignored one-shot operator (`research/private/t049-operator/`). It independently
pins/re-imports the original PDF/text/home/share, complete catalog, manifest,
publication request and all four trust bundles before any key access.
Independent read-only review found two response-recovery defects (premature
final-file creation and stale absence-check race); both were corrected and
the reservation interleaving regression was observed RED then GREEN. Operator
race tests PASS; review confirmed the final ordering without key access.

The operator returned two verified signatures at `2026-09-08T02:21:55Z`.
Separate `publisher finalize` then `publisher verify` PASS for
`ru-kb-dum-2026-t049-v1`, snapshot raw SHA-256
`5ce03d71326d83890cba7a9c94b1a0bafd827ea26cf0f54187d6c141e29e0239`,
publication receipt raw SHA-256
`429888772261e378def4783ee73f3c9d2c120d93af41493b14ef27cd1371c225`.
The fresh local T049 ledger is separate from the pilot ledger; no existing key,
pilot bytes, approval or device state changed. Inputs, response, snapshot and
receipt remain in the KBR re-capture directory outside Git. Normal local bundle
admission subsequently passed below; real emulator E2E remains pending. Checkpoint `15f554a` records the
reviewed parser/contract/Saratov-dispatch corrections; no push was performed.

First real local setup export at `2026-09-08T02:28:55Z`: actual
`ComposeCatalog -> PersistentService.Stage/Activate -> ArtifactReferenceVerifier`
PASS, bundle `local-setup-d19f162a4fc1413907be592fd5aafa74` outside Git in
`namaztime-artifacts/t049-setup-real.a1cCCM/bundle`. All 83 subjects, 166,559
localities, 204,641 aliases and 371,200 normalized name/city pairs are retained.
Exactly 202 KBR localities and the exact legacy Ulyanovsk mosque choice have
bindings; neither city-only evidence nor legacy mosque scope was expanded.
Both signed snapshots are byte-identical to their inputs. Actual registry
content hash is `56969e7fcc97cda53312f4c6928539bd0a653a14dde3a2f7e9f55dd8f532de93`.
The Android lane reports default-anchor bundle/SQLite/proof checks PASS on
API 28 and 35, including all three exact Nalchik alias matches and explicit
selection of GeoNames 523523, KBR v2, retained Uly v1, and Omsk unavailable.
These are local test/admission results, not a device installation claim.

Root aggregate Go gates after exporter integration:
`make test-go lint-go test-go-race security-go` PASS, including all packages,
race detector, vet/staticcheck and no known Go vulnerabilities. Operator-only
follow-up adds closed native Saratov pins and an authenticated KBR predecessor
on the same local ledger; synthetic race tests and `--check-saratov` PASS
without key access. Independent review and signing remain separate gates.
Lead also visually checked the public Omsk June/September images against all
ten retained sample dates, including summer and lunar-date transitions;
the fixed published Dhuhr value does not prove an astronomical calculation
rule or establish mosque iqamah.

Saratov local materialization: repeated independent operator review found no
remaining actionable issue; at `2026-09-08T02:44:46Z` the existing isolated
signer returned both signatures. Separate `publisher finalize`/`verify` PASS,
continuing the same T049 ledger from the authenticated KBR receipt (no second
genesis). Snapshot raw SHA-256
`ca90c6417ee78be2f10641b5dc0f759137af836462889c9ef0b51d55a8ddca41`,
receipt raw SHA-256
`91551a60d4eed1874a244b19b62ba4bb0398beaf2a8a4544e5b690a5c50e4b9d`.
Signed source attribution and a user-initiated HTTPS source link have passed
focused Android tests; actual device selection is still pending.

The first root `make test-android-all` run reached 609 tests and failed four
legacy expectations: two still assert Room version 3 instead of 4, and two
expect the now-removed implicit synthetic debug route. Four external-fixture
opt-ins were explicitly skipped in this environment-free broad run; the Android
lane previously ran them with retained fixtures. These failures are not waived:
the affected tests are being updated to assert the new normal route while
retaining explicit synthetic-scenario persistence and LKG coverage. Full
debug/release lint/build gates had not yet completed at that checkpoint.

Follow-up root aggregate Android check PASS after the four expectation fixes:
`NAMAZTIME_ANDROID_LOCAL_SETUP_INTEROP=…/t049-setup-synthetic.Yoq9cZ
NAMAZTIME_ANDROID_PRODUCTION_SETUP_BUNDLE=…/t049-setup-real.a1cCCM/bundle
GRADLE_USER_HOME=/tmp/namaz-time-gradle make test-android-all`.
The XML results contain 67 suites / 609 tests, zero skips, failures or errors;
debug/release lint, assembly, strict dependency verification and build identity
checks pass. No Room schema was silently regenerated. This validates local
fixtures and builds, not physical-device behavior.

Current `make test-postgres` also PASS, using only newly isolated gate containers:
v9 migration, qualified-proof persistence, downgrade refusal, legacy rollback /
reapply, least-privilege access and backup/restore. Restored legacy and qualified
snapshot hashes match their seed bytes; dump SHA-256
`c160e1cda49a31a908ba9b825280f6e7ab26981bd0e4d1ae2130d22c9084dd52`.
No production database or pilot ledger was modified.

Additional local publications continue the authenticated T049 receipt chain:
DUM RT Kazan and CDUM Kazan each cover September only and remain independent
authorities; Sochi covers 365 dates for the exact city; Omsk covers June–September
only (122 dates). Each request was re-imported against retained raw evidence,
the complete catalog and unchanged trust anchors before the existing isolated
signer returned two signatures; separate publisher finalization succeeded.
Omsk snapshot raw SHA-256 is
`72d3b23005d5f674ae04cd030f0034a4c492b7cbd087d9a460233765913d5a53`.
These five additions are not yet claimed device-selectable. DUM RT's 34 further
exact-locality September candidates are in root adjudication, with 8,160 source
fields / 6,120 onset values independently matched to its official workbook.
Eight unresolved identities and the malformed Aktanysh CSV remain excluded;
no inferred scope, repaired raw row or annual extension is allowed.

Operator docs now distinguish current v9 proof storage/downgrade refusal from
legacy v6–v8 handoffs, including the qualification-table reader grant. An admin
schema-2 choice envelope regression was reproduced RED and corrected to v2;
device-v2 behavior remains unchanged. `make docs-check test-contracts` and
`go test ./internal/devices -count=1` PASS after that correction.

First controlled emulator round (`CONFIRMED_RUNTIME`): only
`ru.namaztime.tv.debug` was upgraded in place on `emulator-5554`, API 36,
1920×1080, density 320. Pilot package/APK/version/install timestamps unchanged.
Normal search selected exact Nalchik GeoNames 523523; all six actual local
September 7 KBR values match the raw PDF and signed snapshot. Explicit
activation and force-stop/relaunch retained KBR. Omsk was unavailable in this
initial bundle and left the complete Room projection unchanged; an unknown
query produced no fallback. Explicit return and cold restart retained the
original Uly snapshot, all six September 8 main-screen values and original
approval. The device-iqamah settings projection is byte-identical through both
selections; KBR has no invented iqamah/Jumu'ah, while Uly's signed local rules
remain stored. Raw evidence is outside Git in `t049-runtime.avZADz`.

Root visually reviewed KBR preview, failed Uly preview and final Uly main
screenshots. Long Uly authority/attribution content clips two preview rows;
the legacy executable badge incorrectly implies device activation; the KBR
preview truncates its effective end date. These actual failures are not waived
by the 609-test gate: a bounded screen/strings regression fix and repeat runtime
are in progress. Emulator clock differs from host UTC; actual mosque-local
dates were used without changing the device clock. This is not physical-TV
acceptance or an external-network-disabled test.

Nationwide compiler handoff: exact subject-set equality PASS for 83 catalog
subjects, 161 authority occurrences / 65 source-candidate records, retaining
all evidence/search-trail pointers. Root reviewed the full compiler and reran
its 34 boundary tests; `make docs-check test-research` PASS (38 research tests).
The initial pinned report records only the actually admitted two-policy bundle,
not the later locally signed sources. See `research/t049/README.md`; the final
expanded admission must replace its operational checkpoint before completion.

Current narrow evidence: `go test ./internal/registry ./internal/devices -count=1`
PASS, retained Kazan September hash-bound provider test PASS, and Android
`DeviceScheduleChoiceClientTest` PASS after its new mixed-authority test failed
against the previous global-tier rejection. Agents report full retained Omsk
122-day and KBR 365-day comparisons; root integration review and aggregate
gates remain pending. These checks do not establish runtime activation.

Qualification protocol narrow checks: `go test ./internal/domain
./internal/publication ./internal/registry ./internal/qualification` PASS after
test-first evidence/hash/catalog/timestamp/order/DST and protected-signing tests.
Snapshot v2 and signing/audit v2 JSON Schema positive cases pass; legacy snapshot
and publication tests still pass. Public source qualification cannot create
iqamah/Jumuah or a generic calculation policy. Research-validation totals are
not activation counts: DUM RT has 43 September parser passes, 36 unambiguous
catalog bindings (35 overlap), 8 unresolved identity mappings; CDUM has 17
September parser passes, with canonical mapping still to be admitted.

Registry v2 narrow checks pass for exact proof/source/authority/scope/catalog
binding, public geographic contexts without fake mosque approvals, independent
active choices with mandatory explicit selection, expiry and signed-proof swaps.
The public-import builder now joins the five strict adapters to retained raw
hashes, an exact canonical catalog and evidence-bound qualification.
`ingestor inspect-public` and the qualified `publisher assemble` branch pass
synthetic CLI tests, reject mixed human-approval inputs and protect existing
output files; real operational manifests and runtime wiring remain pending.

Independent trust review findings were reproduced with failing regression tests
and fixed: exact Int64 JSON Schema bounds, freshness at actual signing/publication
instants (not only generated-at), and receipt ordering
`decision <= generated <= signed <= published`. Correctly re-signed invalid
receipts are rejected; historical authenticated LKG verification remains valid.
Latest root checks: `go test ./internal/publication ./internal/registry
./cmd/publisher ./internal/onboarding -count=1` PASS; DUM KBR opt-in primary
PDF/text/reference hash check PASS with all 365 fields equal. Full gates and
real activation are still outstanding.

## 2026-09-08 — repository instruction and skill cleanup

Status: DONE. Follow-up to the owner-requested audit of AGENTS.md and
all eleven repository skills. Scope: task-sensitive workflow, precise discovery,
TV/reference compatibility, usable local routing/commands and package checks.
Prayer data, signing, application behavior and global skills are unchanged.
Behavioral speed/quality improvement remains `UNKNOWN` without comparative agent
runs; this task verifies instruction consistency and local package integrity.

- All eleven SKILL.md entrypoints reduced from 2,875 to 560 lines, with concise
  descriptions, task/platform-specific routing and existing catalog/reference
  resources retained. Authorized-reference use follows ADR 0014; TV keeps
  androidx.tv.material3. Unconditional questionnaires, absent harness tools and
  the slides self-routing loop are removed from the affected workflows.
- AGENTS.md separates read-only, documentation/skill and implementation work;
  PLANS.md follows the same boundary. Product/source/provenance/clean-room and
  Go/TV invariant sections are byte-identical to the pre-change revision.
- New `scripts/test_skill_package.py` reproduced 71 unresolved command paths
  before the corrections. Its resource/command checks now pass and are wired
  into `make docs-check`; `make test-skills` also runs local search/data tests
  and is included in `make test`. Documentation describes the gate's limits.
- Two orphaned upstream maintenance tests now report explicit capability skips
  instead of StopIteration during discovery. No evaluator/refresh tools were
  downloaded or fabricated: upstream refresh/benchmark behavior remains
  unverified. Partial catalog-tool installations still fail rather than skip.
- Verification: all 11 frontmatter checks PASS; `make docs-check test-skills`
  PASS (2 package checks, 130 runtime/data cases pass, 2 upstream module skips);
  `GRADLE_USER_HOME=/tmp/namaz-time-gradle make test lint` PASS, including 512
  Android tests with zero failures/errors/skips, Go, research and build identity.
  Local search/CLI help smoke checks run without provider requests or writes.
  A focus-restoration catalog query returned no match; a narrower state query
  returned relevant results. CLI success alone is not a relevance guarantee.
- No application/schema/fixture changes, installation or signed artifact.
  The owner subsequently requested a commit and push of this cleanup.
  Rollback is a review/revert of these instruction/test-wiring changes;
  no data migration or signing-key operation is required.

## T048 — final elegance and cinematic polish for RIGHT_SIDE_COMPACT

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline, reference and asset review | DONE | clean `main`/`origin/main` `aa23f52`, CI 34106800146 success, 0.6.2/code 9; owner-authorized `new_compact.png` reviewed against T047; all eight built-ins reviewed and none closes the luminous-background gap |
| Compact presentation and visual-system tests | DONE | compact-only minute clock/countdown with ceiling semantics; calmer type hierarchy; two-line long identity; bounded warning; semantic warm accent/glass/active/campaign roles pass narrowly |
| Original background and three visual passes | DONE | selectable non-default original `Luminous Dusk` packaged from a no-reference-input generated source; API 36 passes cover hierarchy, softer glass and final background/scale balance against T047 and the authorized target |
| Adaptive and regression evidence | DONE | Golden/Luminous/Blue, approved/attention, RU/EN, Iqamah on/off, QR payloads/blur, long copy, 720p/1080p/native 4K and all retention phases pass; protected half unchanged; STANDARD diff is zero pixels |
| Gates and versioned handover | DONE | implementation checkpoint `4f5b158`; docs/test/lint/strict Android (512 tests), Go race/security and secret gates PASS; clean signed 0.6.3-pilot.1/code 10 built and verified, then installed in place over code 9 without changing the first-install timestamp; certificate, QR/configuration and signed Ulyanovsk snapshot retained; T048 handover manifest; no push/PR |

## T047 — premium visual art direction for RIGHT_SIDE_COMPACT

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and reference | DONE | clean main/origin `6e86d61`, CI 34037688038 success, 0.6.1/code 8; owner authorizes new_compact.png visual target; T046 is BEFORE |
| Compact visual system | DONE | isolated glass/color roles, layout-aware selected background, hierarchy/icons/active row; T046 composition and QR contract retained |
| Three visual passes | DONE | API 36/1080p full/crop after surface, hierarchy, final polish; Golden Dusk/Blue Hour/Night Minaret |
| Adaptive and regression evidence | DONE | 66 API 36 frames, 54/54 QR and blur decodes, 54/54 protected-half checks, min free-left 50.15625%; native 4K; zero changed pixels in STANDARD/Donation/three Settings sections; 504 Android tests |
| Gates and handover | DONE | checkpoints `c4eae93`, `1a557d3`; docs/test/lint/strict Android (504 tests), Go race/security and secret gates PASS; clean signed 0.6.2-pilot.1/code 9 verified and installed in place; retained certificate/settings/snapshot; T047 handover manifest; no push/PR |

## T046 — RIGHT_SIDE_COMPACT owner-reference fidelity

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and reference | DONE | HEAD/origin `0bbe7d0`, CI 34034658957 success; only owner-supplied `new_compact.png` untracked; version 0.6.0/code 7 |
| Composition and regression tests | DONE | red/green relational geometry, native text/card containment, 160-character title + six subtitle lines, larger OFF times, retained D-pad/shared state/QR; 499 Android tests pass |
| Screenshot iterations | DONE | two API 36/1080p comparisons plus final review; 48 final frames, 42/42 QR/blur decodes, 42/42 protected-half comparisons; native 4K; zero STANDARD pixel differences; min free-left 50.15625% |
| Gates and versioned handover | DONE | checkpoint `c878d5e`; full docs/test/lint/strict Android (499 tests), Go race/security, PostgreSQL restore and secret gates PASS; clean signed 0.6.1-pilot.1/code 8 and in-place 7→8 upgrade verified, same certificate/settings/snapshot; T046 evidence manifest; no push/PR |

## T045 — physical-TV QR reliability and alternative schedule presentation

| Step | Status | Evidence / exit condition |
|---|---|---|
| Baseline and scope | DONE | clean HEAD/origin `b5bee5c`; CI 34030075138 success at same SHA; 0.5.2 code 6; API 36 emulator available |
| Shared QR hardening | DONE | native final-view pixels decode and equal the integer-module raster on STANDARD/compact/Donation/constrained preview at all three densities; badge and clipping removed |
| Persistent visibility and compact layout | DONE | DataStore close/reopen, retained overrides, existing engine countdown policy, native graphics/D-pad tests; six rows and protected half through all retention phases |
| Emulator composition and decode evidence | DONE | 61 valid frames; 50/50 exact decodes and bounded blur; 26/26 left-half pixel comparisons; real 4K Presentation; STANDARD differs only within QR; `docs/evidence/t045-tv-presentation/` |
| Gates and signed handover | DONE | all required gates PASS, 496 Android tests; clean signed `0.6.0-pilot.1` code 7 from `95bd858`; certificate/snapshot/hash checks and in-place emulator upgrade/decode PASS; handover manifest in T045 evidence; `PHYSICAL_QR_RETEST_REQUIRED` |

## Phase 0 — research and repository foundation

| Item | Status | Evidence / exit condition |
|---|---|---|
| Public IslamApp feature research | DONE | store/help sources recorded in `SOURCES.md` |
| Analyze supplied screenshots | DONE | settings, QR and display observations in `RESEARCH_REPORT.md` |
| Clean-room static APK analysis | DONE | `APK_RESEARCH_ISLAMAPP_1_6_2.md`; sanitized evidence only committed |
| Compare prayer-time acquisition patterns | DONE | `PRAYER_TIME_SOURCE_PATTERNS.md` |
| Create docs-first Codex package | DONE | `make docs-check` passes |
| Repair repository-local Codex skill metadata | DONE | `android-tv-screen` and `prayer-times-provider` have valid YAML frontmatter; `quick_validate.py` passes |
| Add repository-local design skills | DONE | nine design/Compose skill packages are tracked; `quick_validate.py` passes for every package; generated caches and local visual/runtime evidence remain ignored |
| Runtime black-box validation on physical TV/box | BLOCKED | requires device/ADB test environment; see `BLACK_BOX_VALIDATION_PLAN.md` |
| Choose first pilot mosque/source | DONE | D-001 accepted for the Second Cathedral Mosque of Ulyanovsk; August 2026 photo selected as the first manual-import source fixture |
| Choose pilot hardware | DEFERRED | Android Studio TV Emulator API 36 / 1920×1080 is the controlled development runtime; D-004 physical TV/box model remains open and separate |
| Confirm license/permission for first schedule | DONE | product owner confirmed project use/redistribution permission on 2026-08-20; source SHA-256 recorded for T008 |

## Phase 1 — bounded technical vertical slice

Goal: one TV shows a synthetic, then approved, offline schedule for one mosque.

| Task | Status | Acceptance |
|---|---|---|
| T001 repository and CI scaffold | IN_PROGRESS | local commit `cddc757`; Go/Android scaffold and CI workflow added; local gates pass, remote CI run remains `UNKNOWN` until the initial branch is pushed |
| T002 Go domain types + JSON Schema validation | DONE | `feat(domain): validate prayer snapshots`; valid synthetic snapshot passes Schema + domain checks, five invalid fixtures fail deterministically; local contract/race/vet/docs gates pass |
| T003 Android TV shell + Room | DONE | `feat(tv): add offline settings shell`; Compose for TV launches at API 28+, Robolectric D-pad test reaches all settings/actions, DataStore persists focus destination, Room schema v1 is exported and tested |
| T004 import bundled synthetic snapshot | DONE | strict Android contract validation; offline asset bootstrap; full Room transaction and atomic active/previous pointer; corrupt input/local-state diagnostics and previous restore; file-backed failure/reopen preserves active data; explicit Room v1→v2→v3 migrations |
| T005 main prayer screen | DONE | responsive offline layout with six adhan rows, explicit missing/not-applicable iqamah, explicit preview-day/time/next-event placeholders, conservative source states, fixed-width countdown, recovery warning and D-pad settings path; measured bounds tested at 720p/1080p/4K profiles |
| T006 time/next-event engine | DONE | mosque-IANA clock, exact boundaries, next-day Fajr, explicit sunrise policy, iqamah override/range/weekday/priority resolution, separate Friday sessions and fail-closed DST/config diagnostics; exhaustive local tests pass |
| T007 QR campaign | DONE | local ZXing QR; exact HTTPS/lifecycle validation; lifecycle-independent operator preview; hashed audit stub; invalid, expired or overlapping campaigns fail closed without affecting prayer display; 720p/1080p/4K Robolectric coverage |
| T008 manual approved CSV/JSON provider | DONE | strict `manual-csv/v1` provider and inspect CLI; raw/transcription/normalized hashes; gap/order/scope/delta validation; deterministic diff and warning acknowledgements; approval-bound publication; canonical SHA-256 + Ed25519; Go/Android tamper/unknown-key verification; real August pilot retained as `needs_review`, never auto-approved |

Detailed prompts: [CODEX_TASKS.md](CODEX_TASKS.md).

## Phase 2 — production-grade source and publication pipeline

| Task | Status | Acceptance / evidence |
|---|---|---|
| T009 manifest/snapshot sync | DONE | explicit ephemeral one-use pairing fixture and scoped bearer API; duplicate credential rejection; signed registry and paired-mosque binding; same-origin canonical snapshot URLs; manifest/raw snapshot ETag/304 and Digest; Android AES-GCM/Keystore provisioning, provisioning-scoped durable stage/quarantine/checkpoint, signature/schema/domain/manifest/mosque binding, atomic activation and authenticated rollback; 401/404/500/timeout/tamper/re-pair plus file-backed import/post-commit interruption recovery tests pass |
| T010 first real source onboarding | DONE | immutable PDF baseline + August override, deterministic 365-day candidate/diff, signed approver receipt, Dhuhr/iqamah/Jumuah policy, no-fallback expiry and protected-signer protocol are complete. D-015 selects the signed bundled-snapshot/USB path for the first mosque pilot; KMS and remote trust deployment are deferred to remote-managed operation. |

T010's locally executable source slice is complete. The retained PDF SHA-256 is
`82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21`;
the strict parser produces a 2026-01-01→2026-12-31 raw `needs_review`
candidate with zero blocking validation errors. D-002 now binds the PDF as
baseline and the photo as the priority source for fields present in August.
`effective-schedule/v1` preserves both raw/component hash chains, retains PDF
al-Isfar where the photo has no value, keeps collective Dhuhr separate and
produces normalized SHA-256
`e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77`.
All twelve numeric differences remain visible and policy-resolved. Named
approver `approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly` signed the
exact candidate/diff/warnings and policy SHA-256. The T033 signed successor
keeps source Dhuhr onset as adhan, fixes Dhuhr iqamah at 13:15 on all weekdays,
keeps the other four collective prayers at adhan +5 and publishes one Friday
Jumuah at the same 13:15 mosque-local time. D-013 is accepted and locally implemented: production code
accepts only an isolated signer interface/two-signature response, Go/API
and Android consume the same `scheduled`/`active`/`retired`/`revoked` public
trust model with direct-predecessor transition checks, and finalization emits a
signer-attested hash-chained receipt required by production API admission.
The earlier `0.3.0-pilot-local` emulator evidence is historical and has been
superseded by T022. T042 records a controlled Android 16 TV-emulator in-place
upgrade from code 3 to traceable `0.5.0-pilot.1` / code 4; physical-TV
acceptance remains a separate product-owner run. Remote-managed delivery still requires T009 pairing and the
ADR 0011 production signer deployment; those are not requirements for D-015's
offline USB pilot.

T009 completes the locally testable delivery half of the Phase 2 invariant. The
runtime command refuses to embed fixture credentials or trust keys, requires
an explicit ephemeral-test-only mode for its process-local pairing fixture and
accepts only a private explicit config. Android remote work remains
unprovisioned in a normal local-only install; a worker is scheduled only after
pairing/trust configuration is supplied. Non-empty remote asset manifests fail
closed until asset staging is implemented.

- PostgreSQL migrations and audit/outbox tables.
- Source registry and permission metadata.
- Raw artifact storage policy.
- Parser versioning and schema-drift circuit breaker.
- Minute-level candidate diff UI.
- Two-person or named-approver publication control.
- Ed25519 protected signing, public trust lifecycle and rotation. (D-013 local
  implementation DONE; real KMS/HSM key and authenticated deployment remain.)
- Device manifest, staged download and local/manifest rollback. (T009 local slice DONE; production rollout groups remain part of T010 operations.)
- Coverage/staleness alerts.
- First authority/mosque source adapter.

Exit condition: a source change cannot reach a TV without validation, approval, signature and rollback evidence.

## Phase 3 — remote administration and fleet operations

| Task | Status | Acceptance / evidence |
|---|---|---|
| T011 production pairing persistence | DONE | PostgreSQL-backed, restart-safe pairing lifecycle with hashed one-time secrets, expiry/attempt/rate controls, revocation, mosque suspension/isolation, verified remote TLS, bounded backend/rollback contexts, future-migration fail-close, update/delete/truncate-resistant audit and real database concurrency/restart/lock-cancellation tests; independent of blocked T010 |
| T012 role-based fleet administration and mosque isolation | DONE | verifier-only actors and global/local RBAC; uniform cross-mosque/missing-resource denial; scoped list/issue/revoke/verified-registry assignment; append-only 24-hour idempotency with two-phase HMAC rotation and historical responses; canonical assignment audit chain; out-of-band explicit-target migrations and API exact-v2 verification under a tested least-privileged runtime role; full/race/PostgreSQL gates and independent review pass |
| T013 privacy-safe device heartbeat and fleet health | DONE | strict bearer/path-scoped allowlist; server-trusted last-seen and latest-only v3 storage; active device/mosque recheck; mosque-scoped admin projection; same-origin best-effort Android client; least-privilege HTTP, rollback, full/race/PostgreSQL gates and independent review pass |
| T014 bounded canary rollout cohorts | DONE | persistent mosque-scoped device labels; registry-gated atomic assignment with 101st-row sentinel/100-device maximum; exact retries, deterministic concurrent locks, canonical per-device audit, monotonic rollback, v4↔v3 and least-privilege PostgreSQL evidence; full/race gates and independent review pass |
| T015 privacy-safe device support bundle | DONE | authenticated mosque-scoped bounded JSON projection from existing device/assignment/latest-health rows; closed OpenAPI contract, uniform isolation, `no-store`, least-privilege PostgreSQL read and forbidden-field regression evidence; no new collection, logs, secrets, URLs or history |
| T016 PostgreSQL backup and restore drill | DONE | clean-database custom archive restore, corrupt-archive rejection with zero partial tables, exact-schema/auth/current-state/SQLSTATE-55000 append-only verification through a reapplied read-only runtime role, production recovery runbook, full/race/PostgreSQL gates and independent review pass; no production data or RPO/RTO claim |

The independent local fleet foundation is complete through T016. Remaining
Phase 3 branches are explicitly blocked rather than implemented as alternate
unsigned content paths:

| Future branch | Status | Required decision/dependency |
|---|---|---|
| browser admin portal/session | BLOCKED | D-008 plus deployment identity-provider/session model; bearer bootstrap and production credentials must not be invented in Git |
| remote iqamah/Jumu'ah mutation | BLOCKED | initial D-009 policy is approved; remote changes still require an authenticated admin/approval workflow and the provisioned T010/D-013 signer |
| QR and announcement campaigns | BLOCKED | destination/approval policy D-010 and the T010/D-013 approval/signature publication path |
| remote/fleet custom background pipeline | BLOCKED | asset custody/type/size/CDN policy plus the T010/D-013 signed publication path; T028 device-local picker is explicitly outside fleet publication |

At the T016 checkpoint no Phase 4 work had begun. Physical-TV evidence remains
a separate deferred acceptance item and does not weaken the completed local
backend or current Robolectric evidence labels.

## Phase 4 — device reliability

| Task | Status | Acceptance / evidence |
|---|---|---|
| T017 connected TV display design system | DONE | one explicit dark TV Material palette/shape system, original offline atmospheric background and shared responsive safe frame; Room/T006/T007-connected main display exposes mosque-local date/time, next event/countdown, separate adhan/iqamah, source/recovery and optional QR; D-pad focus plus 720p/1080p/4K main/settings/error bounds, long text, Jumu'ah, campaign and contrast regressions pass locally; reference art is not packaged and physical-TV readability/overscan remains deferred |
| T018 display-only screen-on lifecycle | DONE | lifecycle-bound Compose `keepScreenOn` is active for normal/unavailable display, restores the prior host flag in settings/disposal and adds no permission; SDK 28/35 navigation/restoration tests, Android build and repository gates pass; OEM power behavior remains deferred |
| T019 accelerated offline rollover hardening | DONE | deterministic seven-day matrix over one materialized local schedule preserves mosque-local date/time and safe frame at 4K density, then fails closed on the first uncovered date; Android/repository gates and independent review pass; this is not a Room-reopen or physical seven-day soak |
| T020 bounded device-clock health | DONE | HTTPS manifest `Date` compared with injected response receipt at ±5-minute tolerance plus rollback detection; durable nullable health never blocks sync/display; heartbeat tri-state contract/wiring and physical bad-RTC/TLS/power evidence remain deferred |
| T021 bounded display-retention shift | DONE | public display foreground follows a deterministic six-position, ten-minute, ±2 dp cycle inside the shared safe frame; unavailable display participates while settings/background/focus order remain unchanged; pure policy plus 720p/1080p/4K safe-frame and D-pad regressions pass locally; panel-specific efficacy remains physical evidence |
| T022 pilot-local real runtime and settings | DONE | pilot-local packages an approval-bound 365-day Ulyanovsk snapshot plus disjoint public trust, authenticates and atomically activates it through Room, and supports monotonic in-place bundled-snapshot successors. Replacement is limited to known predecessors/the pilot family, same timezone/same mosque after initial migration, and later signed generation time; rejection preserves last-known-good. August precedence/D-009/D-014, settings, RU/EN, D-pad and 720p/1080p/4K regressions pass locally. |
| T023 Android TV visual redesign | DONE | original Golden dusk/Blue hour offline image assets, validated persisted Appearance selection, app-wide navy/gold Material 3 glass system, NamazTime identity, six original prayer glyphs plus iqamah, centered Sunrise time, explicit active-row treatment and redesigned display/settings/recovery surfaces; UI/DataStore/adaptive tests and controlled API 36 emulator screenshots pass while prayer/source logic remains unchanged |
| T024 pixel-accurate main-display refinement | DONE | explicit normalized gap analysis against `design.png`; about 71% centered foreground, equal columns, reference-like next/clock/strip proportions, decorated location/date treatments, softer prayer rows and separators, compact icon-only Settings focus target and exceptional-only public source status; UI geometry/D-pad/adaptive regressions plus repeated API 36 build/install/screenshot comparison pass without prayer/domain/settings changes |
| T025 device-local sadaqah QR and iqamah controls | DONE | validated HTTPS QR/purpose/motivation and five independent fixed iqamah values persist in DataStore without mutating the signed Room snapshot; Settings removes Friday technical copy and exposes D-pad editors; a configured QR activates the authorized-reference three-column Sadaqah panel with frame, support icon, scan-tested NamazTime center badge and lower geometric ornament; repository/projection/Compose/adaptive/QR-decode tests plus API 36 build/install/runtime screenshots pass |
| T026 focused main-display visual refinement | DONE | three direct API 36 build/install/screenshot comparisons against `main_with_qr.png`; dense matte navy surfaces, subdued background, neutral borders, muted champagne accents, lighter type, arch/lantern watermark, fading diamond lines, shared geometric ornaments, softer prayer/highlight treatment and refined QR/support/strip presentation; token, contrast, semantics, clock-bounds and QR-decode regressions pass without prayer/domain/QR business/D-pad/offline changes |
| T027 concise pilot display identity | DONE | local commit `feat(tv): shorten pilot display identity`; main display and Mosque settings show `Вторая Соборная Мечеть` / `Ульяновск` for the exact pilot mosque ID through a presentation-only mapping; canonical signed snapshot/provenance remains unchanged, non-pilot identity passes through, focused Compose/bootstrap tests pass and the updated APK is installed on the API 36 emulator |
| T028 local TV operator UX and alternate display modes | DONE | five checkpoint commits replace ambiguous fixed `HH:mm` preferences with bounded `adhan + N minutes` offsets and approved-policy fallback; add eight offline built-in backgrounds plus validated app-private background/donation image imports; add a persisted, fail-closed donation display with the existing local QR generator, transfer text, five packaged images and explicit Settings/schedule D-pad exits; remove the Settings white/double frame and match the authorized main reference with a normalized rounded-square glass target, white gear and one gold non-scaling focus outline. Repository/projection/import/Compose/adaptive tests, `make test`, `make lint` and a controlled API 36/1920×1080 runtime loop cover the completed scope. The emulator had no document-provider activity, so OEM picker selection and physical-TV behavior remain `UNKNOWN`; signed Room data is unchanged. |
| T029 pixel-accurate standalone donation display | DONE | local implementation commit `80c7200` replaces the three-column donation layout with the authorized `qr_page.png` full-bleed composition; adds one right card, exact localized footer, custom vector icons/fade ornaments, reference-scale decodable QR and compact gold-outline gear; replaces the transfer blob with five bounded DataStore/Settings fields plus deterministic RU/EN/unlabelled legacy preservation. 720p/1080p/4K geometry and actual-badge QR decode tests plus four API 36/1920×1080 build/install/full-and-region overlay loops pass. Final runtime values remain operator-local; signed Room data is unchanged. Physical-TV/OEM and real-phone scan distance remain `UNKNOWN`. |
| T030 independent production-readiness engineering/security review | DONE | [independent report](docs/reviews/2026-08-28-engineering-security-review.md) records 0 CRITICAL, 4 HIGH, 12 MEDIUM, 2 LOW and 2 CLEANUP findings. Checkpoints `888f534`, `7a6e5b8`, `26cfbd0`, `2346fb8` and follow-up `a1e05d8` close every locally actionable material issue, including the AGP 9.3/KSP migration. The second pass found no new CRITICAL or unblocked HIGH. D-015 now separates a conditionally ready offline USB pilot from the still-not-ready future remote-managed mode. |
| T031 permanent Android pilot identity | DONE | checkpoint `27bf040`; D-005 accepts `ru.namaztime.tv`; Android namespace, application ID, Kotlin packages, Room schema export path and identity regression now use it. The old T001 placeholder remains only in historical evidence and cannot be upgraded in place because Android treats the accepted ID as a separate app. |
| T032 signed offline pilot artifact | DONE | checkpoint `7767f46`; a dedicated non-debuggable `pilot` variant packages the authenticated local schedule, requires an external PKCS12 keystore, and fails without it. Debug uses `ru.namaztime.tv.debug`. `make build-android-pilot` verifies exact package ID, APK signature, pinned public certificate and required assets; the permanent key exists outside Git/APK with mode 0600. Two operator-chosen offline backups and physical-TV acceptance remain external actions. |
| T033 pilot UI fidelity and operator customization (requested as T030) | DONE | Six checkpoint commits (`2ffd7e0`, `92c49d0`, `69442af`, `9a186cf`, `8fce081`, `37f3183`) implement the measured main display/shared QR, source-onset Dhuhr plus approved 13:15 Dhuhr/Jumu'ah policy, bounded local identity, built-in-only Appearance and donation filmstrips, gratitude customization and the Room/engine-backed standalone donation hierarchy. Follow-ups `2c0992f` and `30f32bd` close every independent review finding: complete donation-field D-pad traversal, engine-owned current-prayer selection without Sunrise, pre-signer iqamah materialization and unclipped Settings headings. Follow-up `852f98d` records the 2026-08-30 owner clarification by compacting every standalone donation foreground block into a 27.5-percent right rail and leaving the left image unobstructed; adaptive geometry tests and two API 36 review passes cover the correction. Full Go/Android/docs/lint/security gates, debug/release/signed-pilot builds, four fresh API 36 screenshots and independent code/visual reviews pass with no remaining finding; physical-TV/OEM picker and representative-distance QR acceptance remain `UNKNOWN`. |

The independent local Phase 4 queue is complete through T033. Before T034, the
remaining matrix below requires physical hardware, OEM behavior or an open
deployment decision. T034 does not claim those physical items complete; it is
a separate control-plane research/foundation task and does not activate a
nationwide TV rollout.

- physical matrix: Google TV, common Android TV box, Sber/Salute if targeted;
- boot/restart behavior per OEM;
- managed kiosk/device-owner option;
- power loss and bad clock tests;
- seven-day offline soak;
- memory/4K asset soak;
- panel/OEM screen-retention validation and any additional vendor-specific policy.

## Phase 5 — regional scale

The first pilot remains the only executable city/source entry. Research status
is not production eligibility, and the physical pilot blockers below remain
unchanged.

| Task | Status | Acceptance / evidence |
|---|---|---|
| T034 clean-room Russia city/source research and Ulyanovsk foundation | DONE | `ONE_MUSLIM_APK_RESEARCH.md`, full-year aggregate comparison, 31-subject first-party research draft, architecture/ADR 0015, and a tested control-plane resolver route Ulyanovsk to the unchanged approved signed pilot. Independent recomputation/falsification corrected composite authority evidence; `make docs-check`, `make test`, `make lint`, Go race/vulnerability and 73-commit secret scans pass; no competitor artifact or signed-snapshot change entered Git. |
| T035 licensed canonical Russia city catalog and search | DONE | GeoNames RU selected under CC BY 4.0 after OSM/ODbL comparison; exact 2026-08-29 inputs are size/SHA-pinned and bulk outputs stay outside Git. Deterministic importer/search/diff yields 166,557 Russian-named cities across 83 mapped subjects, explicit exclusions, stable IDs, IANA timezone/provenance and nine non-auto-selected `Киров` results. Two full imports are byte-identical; docs/narrow/lint gates pass. |
| T036 persisted policy registry with verified reference adapters | DONE | PostgreSQL v6 persists immutable schema-v1 geography/source/policy/payload/override revisions, canonical hashes, active pointer and append-only activation evidence. Service activation verifies exact approval/signed-snapshot references, source freshness and same-tier uniqueness; duplicate city search never auto-selects. Real PostgreSQL, migration rollback/reapply and least-privilege restore gates pass. |
| T037 Ulyanovsk persisted end-to-end migration | DONE | Hard-coded executable seed removed. Reviewed bindings compose the pinned 166,557-city GeoNames catalog with real approval/publication evidence; immutable PostgreSQL activation plus authenticated setup search resolves canonical Ulyanovsk → RU-ULY → explicit dual-evidence source/policy/timetable → Second Cathedral Mosque → unchanged signed snapshot. Real rollback, duplicate/unknown non-selection, API least privilege, full Go/PostgreSQL/Android/docs gates and raw snapshot SHA-256 `78233e7b…50b` pass. |
| T038 second official regional source adapter | DONE | Closed by T049's explicit adapter criterion: `kbr-annual-pdf-text/v1` validates the authority-linked 2026 PDF explicitly scoped «ПО КБР»; all 365 raw/extracted/reference rows and hashes match. See `research/t049/KBR_ADAPTER.md`. Nationwide qualification, signed materialization and runtime integration remain T049 work, not claimed complete by this adapter result. |
| T039 ambiguity, unavailability and staleness operator workflow | DONE | versioned admin assessment explains deterministic tier, authority/source evidence, freshness/range and stable blocked reasons; an authenticated mosque operator can append only a selectable staged choice as `pending_review`. PostgreSQL v7 makes requests idempotent/append-only and serializes against activation. Ambiguous/stale/unavailable never publish or select a neighboring/nationwide method; active revision, assignments, signed Ulyanovsk bytes and TV last-known-good remain unchanged. |
| T040 multi-authority city schedule choices | DONE | Checkpoints `4f0c9dd`, `3e41749`, `b7f8e76` add a non-persisted `CityScheduleChoiceSet` and authenticated `/setup/schedule-choices` v1 projection. It returns every highest-tier eligible authority choice with stable identity/provenance, neutral order and no top-N; multiplicity requires explicit selection while resolver ambiguity and T039 `pending_review` remain fail closed. 0/1/2/3/5/8 synthetic, PostgreSQL active/staged/stale, full/race/lint/docs/security and unchanged Ulyanovsk SHA-256 gates pass; no migration or new real source was added. |
| T041 Android TV city and schedule setup flow | DONE | Checkpoints `6375915`, `a5456ec`, `64ed91d`, `08500e5`, `cc02d51`, `824f279`, `ea37c5e`, `5bab92f`, `8840ffa` provide a provisioned-device-only API boundary, append-only schema-v8 `pending_review` handoff, canonical Cyrillic/alias city search, duplicate-city disambiguation, complete 0..N authority choices with visible evidence/approval/freshness, explicit proposal/pending UI and last-known-good preservation. Android 16 TV-emulator evidence covers system IME, D-pad scrolling, one/many/unavailable/pending states and Back/focus recovery; physical TV remains unclaimed. Clean dependency-home strict verification, full docs/Go/PostgreSQL/restore/Android debug+release/lint/race/vulnerability/secret gates pass, release excludes the debug evidence renderer, Room schema is unchanged and Ulyanovsk retains raw SHA-256 `78233e7b…50b`. |
| T042 traceable Android APK version and build identity | DONE | Checkpoints `609dda9`, `8f3713d` define one validated version source and ADR 0017; debug/release/pilot embed exact commit/variant/state and Diagnostics shows version/code/build identity. Clean signed `0.5.0-pilot.1` / code `4` packaging fails closed and writes an outside-repository APK/checksum/manifest bound to commit `609dda9`, APK/certificate and unchanged snapshot. Android 16 TV-emulator `adb install -r` upgrades code 3 in place and preserves last-known-good. Full docs/Go/PostgreSQL/restore/Android/race/vulnerability/secret gates pass; Room schema and snapshot SHA-256 `78233e7b…50b` are unchanged. Physical TV, key backups, tag/release and remote updates remain external/deferred. |
| T043 Android TV operator UX correctness and media-picker hardening | DONE | Checkpoints `6a60a0c` through `cfa4621` add a measured no-ellipsis QR contract, compact five-row Iqamah plus explicit signed-schedule resets, ADR 0018 picker/Photo Picker/MediaStore cascade with bounded permissions, feedback and actual emulator import, and full donation collection-link tombstoning/four-row display. Targeted API 36 review also fixes Donation status and Mosque context clipping. Signed `0.5.1-pilot.1` / code `5` installs in place; full docs/test/lint/PostgreSQL/strict-Android/race/vulnerability/105-commit secret gates pass, no migration is needed and the Ulyanovsk raw snapshot remains `78233e7b…50b`. Physical-TV acceptance remains `UNKNOWN`. |
| T044 next-prayer architectural watermark fidelity | DONE | Checkpoint `5ebf023` replaces the nested narrow watermark with a normalized full-card 23-percent-wide cubic pointed arch and an original filled warm lantern, without moving any foreground anchor. Geometry/Compose regressions cover 720p/1080p/4K; API 36 evidence records three iterations plus short/long-title and Golden Dusk/Blue Hour acceptance. Clean signed `0.5.2-pilot.1` / code `6` verifies and installs in place; full docs/test/lint/PostgreSQL/strict-Android/race/vulnerability/secret gates pass, no migration is needed and the Ulyanovsk raw snapshot remains `78233e7b…50b`. Physical-TV visual acceptance remains `UNKNOWN`. |
| 2026-09-03 Android TV emulator directional-input incident | DONE | [Controlled evidence](docs/evidence/2026-09-03-android-tv-emulator-input/README.md) isolates the failure from NamazTime and guest-side D-pad dispatch, then records recovery by fully restarting Emulator 37.1.11 without loading Quick Boot state. Live Extended Controls and host-keyboard checks emit the correct raw Up/Right/Down/Left events and move focus after recovery. A transient host-input or snapshot-state defect remains `INFERENCE`; Google Play sign-in causality and the exact emulator defect remain `UNKNOWN`. No wipe, app/data, permission, schema, schedule, provenance, or source-code change occurred. |
| 2026-09-03 Android TV debug city-search provisioning/preview/activation incident | DONE | [Controlled evidence](docs/evidence/2026-09-03-android-tv-debug-city-search/README.md) confirms that emulator networking was validated but the regular debug package had no encrypted provisioning, so T041 returned `NotProvisioned` before HTTP. The debug source set now supplies an explicit synthetic catalog, six-row organization preview and an initially focused `Use on this TV` action. Exact fixture IDs persist locally; the debug-only repository returns the chosen city on the main display after restart while the signed Room last-known-good remains unchanged. Search covers `Омск`/`Omsk` with `Asia/Omsk`; failures preserve the previous display and Back returns first to organization choices. Red/green ViewModel/Compose/repository/persistence tests and controlled `adb install -r` → Omsk activation → force-stop/relaunch evidence cover the flow. The selected fixture remains visibly `НЕ ОДОБРЕНО`; neutral choice copy does not label it approved. Pilot/release retain the strict provisioned client and exclude synthetic rows, selection storage and projection. No Room, signed snapshot, prayer/source approval, permission or pilot data changed; real regional onboarding and a production approved signed-activation contract remain `UNKNOWN`. |

2026-09-06 pre-push review: DONE; checkpoint `ef20768`. The debug setup changes and sanitized
emulator incident evidence are reviewed together. A new regression reproduced
the retained-ViewModel/recreated-Activity selection-store disconnect; the
debug runtime now shares one application-scoped store and all 145 focused
Android tests pass. Raw local PNG/MP4 references are explicitly ignored.
`make docs-check` and `make lint test test-android-all` pass, including all 485
Android tests, strict dependency verification, debug/release builds and APK
identity checks. APK DEX inspection confirms the synthetic gateway, projection
and selection store are absent from release. No Room, contract, permission or
signed-snapshot migration is introduced. Fresh physical-TV acceptance and a
new signed pilot artifact remain outside this Git review.

Later regional work still includes multilingual/portrait behavior, Ramadan and
multiple Jumu'ah workflows, source-specific SLAs, support ownership, and
physical-device acceptance. None is implied by T034.

## Required update after substantive changes

1. Record task result and commit in this file.
2. Update affected requirements/ADR/API/schema docs.
3. Add or update automated tests for affected executable behavior.
4. Record unresolved risk rather than hiding it.
5. Run `make docs-check` and task-appropriate checks from `AGENTS.md`.
6. Keep `README.md` usage truthful.

## Current offline-pilot blockers

- copy the generated offline APK-signing keystore/properties to two
  operator-chosen offline backup locations before the first mosque install;
- the signed current build is installed and reinstalled in place on the API 36
  emulator; record physical TV/box acceptance at the mosque. Emulator evidence
  cannot prove OEM boot, overscan, storage-provider or long-soak behavior;
- pilot source precedence, named mosque approver, exact signed approval and D-009 mosque policy are accepted; organizational role verification is based on the product-owner statement and public third-party verification is not claimed;
- the official 2026 annual PDF now supplies full-year pilot coverage through T010; its daily Hijri values are absent and therefore remain unset rather than invented;
- abrupt OS process-kill/journal-recovery remains a future instrumentation/ADB
  acceptance case; T004 locally proves transactional rollback followed by a
  file-backed database close/reopen, not a physical-device process death;
- D-010 still requires an approved pilot QR destination/domain for signed or
  remote campaigns. T025 adds a clearly device-local operator QR preference,
  while tests/runtime evidence continue to use synthetic `example.org` and do
  not invent a live destination or official claim.

Remote API/domain, pairing composition, KMS custody, remote trust deployment,
Google Play and remote CI evidence are deferred requirements for a different
deployment mode, not blockers for the D-015 offline USB pilot.
- T009 intentionally rejects non-empty remote asset manifests; custom asset
  staging/type/dimension activation requires a separately bounded task.
- T020 preserves clock health as `unknown`/healthy/mismatch locally, while the
  current heartbeat contract requires a boolean and has no production state
  assembler. Contract evolution/wiring must not collapse `unknown` into a false
  healthy claim.

## Default local QR destination — 2026-09-10

Status: DONE (local implementation and emulator launch).

PROPOSAL — Apply the owner-supplied NSPK URL when the local schedule/donation
QR URL is blank; an explicit custom URL retains priority and validation.
Both settings editors show the default as a placeholder. Existing activation
requirements remain: schedule purpose / donation transfer details; fully empty
configuration remains disabled. Signed campaigns and stored preferences are
unchanged; no migration is needed, rollback restores blank-URL validation.

Changed OperatorPreferencesRepository.kt, SettingsEditors.kt,
OperatorPreferencesRepositoryTest.kt and UI_UX_SPEC.md. Regression coverage
checks the exact query string, whitespace fallback and custom URL priority
for both local QR paths.

Verification: focused repository tests PASS; full Android unit suite PASS
(633 cases, 4 skipped, zero failures/errors); Android lint, Go tests/vet/staticcheck,
contracts, research, skill and build-identity checks PASS. `make -k test lint`
remains nonzero solely because docs-check rejects the pre-existing untracked
`wal_3.png` (>5 MiB). Initial sandbox runs also failed on local sockets/cache;
rerun outside the sandbox cleared those failures. `git diff --check` PASS.
No device installation or physical QR scan was performed (UNKNOWN).
Full check log: `/tmp/namaztime-default-qr-checks.log`.

Follow-up requested emulator launch/APK — 2026-09-10:
`assembleDebug` and build-identity check PASS (`0.6.3-dev`, code 10,
`ru.namaztime.tv.debug`, dirty workspace). CONFIRMED_RUNTIME — `adb install -r`
succeeded on emulator-5554 and MainActivity cold launch returned Status: ok;
1920×1080 screenshot shows the schedule and QR panel. Existing app data retained.
Screenshot: `/tmp/namaztime-default-qr-running.png`. APK:
`apps/tv-android/build/outputs/apk/debug/tv-android-debug.apk`, SHA-256
`937243673b81f6eeb542ae51e321e7c258efb1119a7f827e241700cdb1cdf73d`.
This confirms installation/launch; the displayed QR destination was not decoded.

## Default background set extension — 2026-09-10

Status: DONE (corrected source, local checks and emulator preview).

PROPOSAL — Add `wal_5.png` from repository root as an additional bundled
default background, alongside existing packaged schedule backgrounds.
The initial untracked resource was accidentally copied from `wal_8.png`; on
2026-09-18 it was replaced with the actual owner `wal_5.png` (SHA-256
`fe2decef380fce96972393bcde161af87d1dfecb96b9cc5f959c3375bedddfd5`).
The two UI labels are `Wal 5`. The root source remains untracked.

Changed files:
- `apps/tv-android/src/main/res/drawable-nodpi/tv_background_wal_5.png`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/repository/OperatorPreferencesRepository.kt`
- `apps/tv-android/src/main/kotlin/ru/namaztime/tv/presentation/TvDesignSystem.kt`
- `apps/tv-android/src/main/res/values/strings.xml`
- `apps/tv-android/src/main/res/values-en/strings.xml`
- `apps/tv-android/src/test/kotlin/ru/namaztime/tv/repository/OperatorPreferencesRepositoryTest.kt`
- `apps/tv-android/src/test/kotlin/ru/namaztime/tv/presentation/NamazTvAppUiTest.kt`
- `PLANS.md`

Acceptance check:
1. Built-in background allowlist contains ten IDs and includes the new `wal_5`
   identifier.
2. Settings filmstrip test uses `TvBackgroundStyle.entries`, so the new background
   appears in the same persisted UI order for local selection.
3. New resource is present in release assets at `drawable-nodpi`.

No migration is required; rollback for background defaults is automatic via existing
`backgroundStyleId` persistence fallback.
Final 2026-09-18 source-copy `make test lint` PASS after excluding only
untracked root owner photographs: 635 Android tests, 631 passed and four
optional cases skipped; skill suite retained two existing skips. `git diff
--check` PASS. Debug APK build and `adb install -r` PASS; packaged `wal_5`
matches the source hash above, and the previously replaced `winter_twilight`
WebP is also present. `CONFIRMED_RUNTIME`: API 36 / 1920×1080 emulator shows
the corrected Wal 5 preview and returns to the wal_3 background afterwards.
Screenshots remain outside Git at `/tmp/namaztime-wal5-selected.png` and
`/tmp/namaztime-final-restored-wal3.png`. Physical-TV appearance is `UNKNOWN`.
The root `make test`/`make lint` still stop on the untracked 7.6 MiB wal_3.png
size gate; that gate and the owner originals were left unchanged.
