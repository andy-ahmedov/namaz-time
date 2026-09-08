# T049 — local acceptance and handoff

Date: 2026-09-08. Implementation checkpoint: `b1855c09c27f0de064faced394380af10d6d67ab`.
Status: DONE within the authorized local acceptance scope. This report
does not claim remote deployment, nationwide timetable coverage or physical-TV
acceptance. The [owner assignment](../../docs/tasks/T049-nationwide-first-party-onboarding.md)
defines the authorized local scope.

## Baseline and persistent instructions

The initial clean `main` and actual `origin/main` matched
`325a343f3fabec337e56df0abcd93c2812be2c67`; T048 and repository skill cleanup
were complete. Baseline CI run 34162587927 failed on a documentation link to an
ignored local visual reference. The failure was reproduced from a clean archive
and corrected without committing that asset. Remote CI was not rerun.

`PROPOSAL`: [ADR 0019](../../docs/adr/0019-public-first-party-source-qualification.md),
AGENTS.md, the streamlined provider skill and source checklist now separate
machine-verifiable first-party qualification from optional external endorsement.
Mandatory mosque approval, written reuse permission, organization contact and
invented external approvers no longer gate ordinary qualified public sources.
Explicit restrictive terms, access controls, exact scope, provenance, signing
and the actual legacy pilot approval remain binding. Governing older documents
carry explicit supersession notices. No global skills/configuration were edited.

The [baseline audit](BASELINE_AUDIT.md) preserves conclusions that remain
`CONFIRMED_STATIC` about supplied competitor APKs, distinguishes source-matching
`INFERENCE`, and rejects the old draft's status labels as sufficient admission
proof. No competitor rows, resources or city identifiers became runtime data.

## Nationwide result and qualification states

The [report](README.md), [machine registry](nationwide-registry.json) and
[pinned proof index](operational-proof-index.json) supersede the historical
31-subject draft while preserving its useful evidence. The exact current catalog
set contains 83 subjects, 166,559 localities and 204,641 aliases; all 52 formerly
missing subjects now have research/search-trail records. The count is derived
from the canonical catalog, not a hard-coded national total.

Four source ledgers contain 161 authority occurrences, 135 distinct declared
authority IDs and 65 candidate records using 63 distinct source IDs. These are
discovery identifiers, not 135 independently qualified legal organizations.
Six first-party authorities have actually qualified public publications:
DUM RT, CDUM Russia, DUM KBR, DUM Saratov, DUM Omsk and Sochi's local Muslim
organization. Two retained legacy publisher records are not newly qualified
organizations.

At the explicit `2026-09-08T04:08:18Z` report state:

| Operational state | Count and boundary |
| --- | --- |
| Public qualified, signed and locally admitted | 48 exact-source policies |
| Retained real legacy approval | 1 unchanged Ulyanovsk mosque policy |
| Current selectable policies | 49; 250 city/policy choices |
| Covered localities | 248 |
| Unavailable localities | 166,311; full lossless canonical complement |
| Subjects with coverage | 1 full (KBR), 12 partial, 70 unavailable |
| Public effective ranges | 2 annual, 45 September-only, 1 June–September |
| Public normalized data verified | 2,202 day records / 13,212 onset fields |

Research stages are not interchangeable with admission: the discovery ledgers
retain 54 subjects marked researched/qualification-not-performed-by-lane, 25
unavailable and one each scope-limited, validation-gaps, current-candidates and
expired. These historical lane labels are not current operational counts;
the admitted-proof section supplies those. Selector records may fan out into
multiple independently scoped policies, so candidate and policy counts must
not be subtracted to invent an unqualified-source total.

Kazan, Татарстан, exposes independent DUM RT and CDUM choices without ranking
or top-N suppression. Ulyanovsk exposes both the retained mosque composite and
the separate CDUM city table; their CDUM affiliation is not represented as two
independent religious chains. All other exact coverage and reasons are in the
machine registry and the report's 13-subject table.

`UNKNOWN`: unavailable cases include unproven ownership, absent/currently
expired or Ramadan-only tables, incomplete official calculation parameters,
ambiguous locality identity, narrower proven scope, inaccessible/restricted
transport and unsupported annotated/next-day rows. None receives generic
Russia/MWL/Hanafi, capital-city, neighboring-city, competitor or interpolated
fallback data. No sufficiently complete official calculation policy was admitted.

## Providers, admission and normal setup

Six deterministic adapters are implemented and tested: official DUM RT locality
CSV with XLSX cross-check; CDUM per-city HTML; KBR annual PDF/text; Saratov monthly
HTML; Sochi annual XLSX; and Omsk public JSON corroborated by publisher images.
The admitted public kinds are 38 `official_file` and 10 `official_html` policies
(including the static public JSON file), not generic calculators.
Partial source ranges remain partial; city tables do not become subject-wide.
KBR alone has explicit republic-wide table evidence covering its 202 localities.
T038 is DONE under the owner's second non-Ulyanovsk regional-adapter criterion;
see [KBR_ADAPTER.md](KBR_ADAPTER.md).

The public branch carries complete qualification evidence with exact authority,
source, raw/candidate hashes, parser, catalog, scope, dates and timezone. Public
snapshot/signing/audit v2 coexist with the exact legacy v1 path. Publication and
receipt verification use the existing isolated signer and fixed trust anchors;
they authenticate NamazTime artifacts, not endorsement by a DUM. No key was
created, changed, exposed or copied into Git. The local ledger is separate from
the pilot ledger. Raw sources, substantial operational data and the private
operator remain outside Git.

Actual Go `ComposeCatalog` / `PersistentService.Stage` / `Activate` and
`ArtifactReferenceVerifier` admitted the final export at `2026-09-08T04:05:00Z`.
The report compiler only reports that pinned prior admission; it cannot itself
qualify sources, verify signatures or confer active status. Full catalog SQLite
preserves all canonical records and aliases, not merely covered cities.

Normal debug setup now uses the provisioned device HTTP path when configured,
otherwise the verified local setup bundle. A missing bundle is unavailable;
there is no implicit synthetic fallback. Real authorities, scope, source type,
range, all six preview values and provenance details are shown before explicit
selection. Activation re-verifies the signed artifact and updates Room atomically
against the observed active snapshot. The display reads local persistence only.
Synthetic gateways/rows remain explicit test/evidence dependencies. Pilot/release
factories remain separate; no server scraping or new Android permission was added.
Regional onsets do not invent mosque iqamah or Jumu'ah.

## Regressions found and corrected

The first emulator round exposed clipped long Ulyanovsk preview content, a
truncated KBR effective range and misleading legacy availability wording.
The corrected layout has 56 focused cases including 720p/1080p/4K, RTL, all six
rows, complete dates and D-pad traversal into the middle and end of an oversized
provenance paragraph. Actual corrected-device acceptance is recorded separately.

The initially exported final47 bundle failed two production Android cases:
adding CDUM left the retained Uly choice non-executable in September. It was
never installed. Per-option eligibility now treats valid active public and
legacy proofs consistently while preserving explicit selection, ambiguity,
staged pending-review behavior and all proof checks. RED→GREEN mixed-source
365-date, reordered-input and HTTP-decoder tests pass. The final48 bundle retains
legacy Uly continuously all year, with CDUM separately in September; no signed
row, qualification or real approval was rewritten to mask the defect.

## Representative emulator evidence

Final48 representative local runtime acceptance passed on `emulator-5554`,
Android TV API 36, 1920×1080, density 320.
`CONFIRMED_RUNTIME`: normal Kazan search distinguishes RU-TA from its RU-KIR
homonym; the latter has no source and leaves the complete ordered Room projection
unchanged. Kazan exposes DUM RT and CDUM independently. Each real choice has
been explicitly activated and retained after a cold restart. Root inspected the
two-choice screen and both restarted main screens, comparing all six values to
the separately pinned native inspections for the observed September 8 local date.
DUM RT uses its published Fajr, not the distinct recommended-Fajr auxiliary field;
the two sources' differing values remain different, without averaging.

Arsk's separately published selector table renders its own six values and full
September range. D-pad Details exposes the complete DUM RT name, exact-locality
scope, lower hashes/IDs and full attribution; Back restores Details focus and
the unchanged six-row preview. Saratov's exact-city monthly preview likewise
matches all six retained source values. Its explicit HTTPS Source action reaches
the Android BrowserStub, which reports that no application can perform the
action; the same preview and Source focus remain, without activation or crash.
This is graceful handler-absence evidence, not successful browser/page execution.
Root also inspected Sochi's annual and Omsk's June–September previews: each shows
all six exact September 8 values, its full effective range and its own IANA
timezone. Neither preview supplies inferred mosque iqamah. These are actual
signed-data previews, not a claim that every source was separately activated.
KBR's corrected preview now shows all six matching September 8 values and both
annual range endpoints without clipping. The earlier controlled round already
proved explicit KBR activation and cold restart against its unchanged signed
snapshot; the final48 round verifies the corrected display on the final APK.
The final Ulyanovsk screen exposes both retained and CDUM choices. Explicitly
choosing the original mosque policy restores its exact six onset values; the
corrected preview also shows its separate iqamah and complete annual range.
Root inspected the final cold-restart main screen: the debug emulator is left
on the original Second Cathedral Mosque, Ulyanovsk, not on demo or the new CDUM
table. Final read-only Room `quick_check` is `ok`, schema/user_version remains 4;
active is `ulyanovsk-second-cathedral-2026-pilot-local-v2`, previous is
`cdum-kazan-september-2026-t049-v1`. The original legacy record retains schema
1.0, its real approval, no public qualification, 365 days, five iqamah rules and
one Jumuah. The complete ordered original snapshot/days/rules/overrides/Jumuah
projection before installation and after the round has identical SHA-256
`58d4b431876cf92d1ec2b70ab864b6548eb3699164a555906ff51bfa227efc47`.
The device-local iqamah/show preference projection is likewise unchanged:
`0825ae2eb1e2ad41318e5942c59c1e79efd34b9e92e5db05b734cacdabedb9b5`.
The actual installed debug APK hash equals the frozen artifact below.

The frozen debug APK was built from clean `b1855c0`, SHA-256
`bacc07465c4eb37f9a4f57518faaa8b84b270c61be19793db7759699debc5b3f`.
Only `ru.namaztime.tv.debug` was updated in place. The independent pilot APK,
signing certificate and installation identity remain unchanged, with the private
data verification limit below. Device dates are observed in each mosque's IANA timezone; the device
clock is not adjusted to make tests pass.

One test-navigation incident is retained explicitly: an IME-dismissal race made
an extra Back leave the debug task and reveal the already installed pilot. The
next D-pad cluster opened the pilot Settings/Donations section. Input stopped;
captured intermediate XML and command chronology localized the transition.
No pilot install, clear, text entry, Save or iqamah action was issued. Read-only
checks retained the pilot APK/certificate/install timestamps and visible original
Ulyanovsk times/iqamah. `UNKNOWN`: whole private pilot preference equality cannot
be established with ordinary `run-as` because the pilot is non-debuggable. No
access bypass or unauthorized restore was attempted. Remaining debug checks use
an explicit debug activity and a foreground-package guard before input, with
separately observed IME dismissal. This incident is not a source-switching defect
or proof that all private pilot bytes remained unchanged.

## Validation and compatibility

The following completed gates apply to the final implementation and final48
bundle; later documentation-only edits do not invalidate them:

| Command/check | Observed result |
| --- | --- |
| `make docs-check` | PASS, including 2 skill-package checks |
| `make test-skills` | 132 cases: 130 PASS, 2 explicit missing upstream maintenance-module skips |
| `make test` and `make lint` | Both PASS; full Go, docs, contracts, research, Android and identity checks as wired |
| `make test-go lint-go test-go-race security-go` | PASS; race, vet, staticcheck and no known Go vulnerabilities |
| `make test-postgres` | PASS: schema v9 migration/refusal/rollback-reapply, least privilege, isolated backup/restore |
| `make test-android-all` | PASS: 67 suites, 621 tests, zero failures/errors/skips; strict dependencies, debug/release lint/build/identity |
| `make test-research` | PASS: 38 tool/compiler tests and 12 independent DUM RT comparator tests |
| `make test-contracts` | PASS: 3 public setup contract cases plus schema/Go checks |
| Production bundle and synthetic exporter interop | 4 opt-in Android cases included, all PASS, no skip |
| Independent final48 audit | Exact 250-choice set, full catalog, closed file inventory, unchanged prior proofs and all public onset values PASS |
| Secret scanning through implementation checkpoint | Staged scan PASS; full-history `make secret-scan` PASS across 126 commits |

Android gates used `GRADLE_USER_HOME=/tmp/namaz-time-gradle`,
`NAMAZTIME_ANDROID_LOCAL_SETUP_INTEROP` pointing to the retained synthetic export,
and `NAMAZTIME_ANDROID_PRODUCTION_SETUP_BUNDLE` pointing to the final48 bundle.
Without these external fixtures a normal checkout explicitly skips those opt-in
cases; this acceptance run did not. Gate logs retain the pre-checkpoint dirty
identity; the frozen runtime APK was subsequently rebuilt from clean `b1855c0`.

PostgreSQL v9 persists the complete immutable qualification JSONB and exact
reference bindings. Down-migration refuses existing v2/history it cannot safely
represent; legacy rollback/reapply and fresh isolated backup/restore preserve
exact bytes. Latest drill dump SHA-256:
`ecd61fe3d3d248df6f1f87a5a6d357041146184256107d3c6714455e4ca98dc9`.
No production database was modified. Registry/device/admin envelopes and
OpenAPI v2 describe the public-versus-legacy proof branches and active/staged
semantics; legacy signed Ulyanovsk remains compatible. Android Room schema files
are unchanged. Rollback is an explicit reviewed code/APK rollback using retained
artifacts, never dropping v2 proof history or silently replacing newer device data.

## Artifact pins and limits

Operational files are below the local sibling `namaztime-artifacts` directory,
not redistributed in Git:

| Artifact | SHA-256 |
| --- | --- |
| `t049-setup-expanded.KaooWx/bundle-final48/manifest.json` (raw) | `ba8a5b9bb7e7ac24651836bcb1cd31788fd2472a451cbb3a2aae5a56e051718f` |
| `t049-setup-expanded.KaooWx/EXPORT_HANDOFF_FINAL48.md` | `b8b0a546f3352198aa7183bae1f1c445c7bf1ae7e298822bc76023257f49aef5` |
| `t049-setup-expanded.KaooWx/FINAL48_BUNDLE_AUDIT.md` | `0d863168cb26751dd4345d5e3245964f9fdd550ceb69c85a3952cc89ddb7aa8d` |
| `t049-publication-index.32vtB5/forty-eight-reviewed-publications.json` | `875ef07ee0f1894cd470eaf18f369e69f467795445ca1d03ad326e5b716f9ab2` |
| `t049-runtime-expanded.V6w4zi/REPORT_DRAFT.md` (sealed final report) | `118d00b961ea06f365fb8fed666a547b9b93920437daa80a34b940be0dc032ea` |
| `t049-runtime-expanded.V6w4zi/COMMANDS.md` (capture paths and reproduction) | `c27e7feabb3b0c5551ed4160356c2ba7d55ce8383d13e4b450dedb7e2a982205` |
| `t049-runtime-expanded.V6w4zi/SCREENSHOTS_SHA256SUMS` (27 PNGs) | `5f8153730371745c44ed444381570ef82442228c931e0807152f95a86c723af6` |
| `t049-runtime-expanded.V6w4zi/UI_DUMPS_SHA256SUMS` (66 XML dumps) | `16ef9ece886fd6fda3823b2d2a404c5abe4727deb7d68cec11a3f8a3e8314e8f` |
| `t049-runtime-expanded.V6w4zi/SAVED_STATE_SHA256SUMS` (12 state captures) | `670b60d0e69304cbc2d7f5d882a466de436c2f017c9dbe7bf2fe399d25c73954` |
| Original retained Ulyanovsk snapshot | `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b` |

Root read the complete sealed report and reproduction record, verified all three
runtime inventories with `sha256sum -c --quiet`, independently reran read-only
SQLite integrity/selection and the complete original-data / filtered iqamah
equality checks against saved before/final captures, and visually
reviewed the representative previews, complete authority details, independent
choices and restarted main screens, not just delegated success messages.
Final documentation `make docs-check` and `git diff --check` pass. The staged
documentation secret scan passes; the post-commit full-history scan is the final
handoff check, not an application rebuild or a claim of remote CI execution.

`UNKNOWN` / separately deferred acceptance: physical mosque TV and hardware-specific
behavior; emulator clock-health discrepancy; future source updates and incomplete
calculation policies; two offline APK-key backups for actual mosque deployment;
future remote operation. This task does not claim an external-network-disabled
test or a newly green remote CI run. Public timetable availability is not a claim
of unrestricted copyright redistribution or external partnership.

Expiry is intentional: evaluating this fixed bundle on October 1 leaves only
the KBR, Sochi and retained Uly annual policies (204 city/choice bindings), and
January 1, 2027 has no covered interval. Future updates require fresh valid
qualification/materialization; unavailable does not trigger another strategy.

## Local commits

- `8685832` — reconcile source qualification and endorsement instructions.
- `da4c906` — implement qualification, providers, research and v2 integration.
- `5a9eb73` — harden CDUM markup validation.
- `15f554a` — enforce qualification and publication boundaries.
- `60bc5b9` — export verified offline setup bundles and align admin v2.
- `b1855c0` — use verified real setup choices, complete nationwide registry and fix mixed legacy/public eligibility.

The documentation-only commit containing this handoff records final acceptance;
it does not change the frozen runtime APK's clean `b1855c0` implementation identity.

Only local checkpoint commits are authorized by T049. No push, PR, deployment,
organization contact, signing-key change or production-data deletion occurred.
