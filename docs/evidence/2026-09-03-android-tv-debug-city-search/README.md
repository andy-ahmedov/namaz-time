# Android TV debug city-search recovery

Date: 2026-09-03

## Scope

This incident covers the regular `ru.namaztime.tv.debug` application on the
controlled Android 16 / API 36 TV emulator. It does not claim that a production
setup server, pairing deployment, or a real Moscow prayer source exists.
The later activation follow-up also uses a synthetic Omsk fixture; it is not a
claim that a real Omsk source exists.

## Root cause

- `CONFIRMED_RUNTIME` — the emulator's active Ethernet network exposed both
  `INTERNET` and `VALIDATED` capabilities.
- `CONFIRMED_RUNTIME` — `ru.namaztime.tv.debug` had no files in its private
  `shared_prefs` directory, so the encrypted device-provisioning preference was
  absent.
- `CONFIRMED_STATIC` — the regular debug `MainActivity` constructed
  `DeviceSetupClient` with `EncryptedDeviceProvisioningStore`; when that store
  is empty, `searchCities` returns `NotProvisioned` before calling its HTTP
  transport.

Internet reachability was therefore not the failed boundary. The debug UI had
no device identity, bearer credential, or server origin with which to make the
authenticated T041 request.

## Recovery decision

- `PROPOSAL` — the debug source set now supplies an interactive local
  `DevelopmentDeviceSetupGateway` containing only explicit synthetic fixtures.
  It supports city filtering, duplicate candidates, one or multiple authority
  choices, and a local six-row adhan/iqamah preview.
- `PROPOSAL` — release and pilot source sets continue to construct the strict
  provisioned `DeviceSetupClient`. Synthetic debug fixtures are not compiled
  into those variants and cannot replace server pairing or approved sources.
- `PROPOSAL` — a local synthetic selection enters `PREVIEW` directly, creates
  no `pending_review` request, explicitly says that no server request was sent
  and leaves the current signed schedule unchanged. The explicit
  `Use on this TV` action stores only the exact debug fixture identifiers and
  returns to a synthetic, visibly unapproved main display for that city. The
  Room-backed signed schedule remains unchanged underneath. Back before
  activation returns to the same organization choices before returning to city
  search.

The fixture includes `Москва` and `Московский` for the `моск`/`Moscow` search
case. The two Moscow authority rows are named `Демо-организация ...`, use
`manual_import`, carry the `PROPOSAL` evidence label, and contain six synthetic
preview rows. They must not be interpreted as real or approved Moscow
schedules.

The fixture also includes `Омск` with the `Omsk` alias and `Asia/Omsk`
timezone. Its one organization and prayer rows are synthetic `PROPOSAL` data.

## Verification

- `CONFIRMED_RUNTIME` — after installing the updated debug APK in place, typing
  `Moscow` in the normal Mosque → Change city or schedule flow rendered the
  Moscow city result. Selecting it rendered both distinct demo organization
  rows with `PROPOSAL` and `manual_import` provenance text.
- `CONFIRMED_RUNTIME` — selecting the first row rendered all six Moscow demo
  prayer rows with separate adhan/iqamah columns while the Ulyanovsk signed
  schedule remained listed as current. In that pre-activation revision Back
  was initially focused. The
  captured 1920×1080 frame had SHA-256
  `7b231810c77b2970fb918519ceceefd3394188b86309035825680b309f58bb6b`.
- `CONFIRMED_RUNTIME` — Android Back from that preview returned to the same two
  Moscow organization rows instead of returning to city search or Settings.
  The captured frame had SHA-256
  `e4586243f7e096287f22031cc85bb1ef3a874672268dc752fdcac0bbcb35906a`.
- `CONFIRMED_RUNTIME` — after the activation follow-up, the controlled emulator
  accepted the Latin query `Omsk`, rendered the canonical `Омск` / `Asia/Omsk`
  result, displayed the synthetic organization preview, and initially focused
  the explicit `Использовать на этом телевизоре` action. That preview frame had
  SHA-256
  `d990060cc22b20935b500637731813e50bcb86ff611970286adeb24e69c3096d`.
- `CONFIRMED_RUNTIME` — activating that exact fixture returned directly to the
  main display with `Демо-организация Омск №1`, locality `Омск`, adhan `04:20`,
  iqamah `04:35`, and the visible `НЕ ОДОБРЕНО` warning. The frame had SHA-256
  `d98875aa751199221bb2199a81632d455df025c97536391fe31a99c8f7dd6df2`.
- `CONFIRMED_RUNTIME` — after `am force-stop` and a fresh launcher start, the
  same Omsk display remained active. UI hierarchy text independently contained
  `Омск`, `Демо-организация Омск №1`, `04:20`, `04:35`, and
  `НЕ ОДОБРЕНО`. The post-restart frame had SHA-256
  `7e733b7137b091ef41d21c19398932c3b31a2fd24527ad4bd6939067408c012a`.
- `CONFIRMED_STATIC` — focused unit tests cover a missing-provisioning debug
  runtime without any network call, Moscow filtering, both distinct authority
  choices, Omsk Russian/Latin search, six synthetic preview rows, no pending
  request, an initially focused activation action, activation failure, local
  store recreation, selected-city display projection and preview-to-choice
  Back behavior.
- `CONFIRMED_STATIC` — the full strict Android gate builds and lints both debug
  and release variants. APK inspection finds `DevelopmentDeviceSetupGateway`
  in debug and not in release.
- `CONFIRMED_STATIC` — the Ulyanovsk pilot snapshot file remains byte-identical
  at SHA-256
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.

## Pre-push review follow-up (2026-09-06)

Review found that Activity recreation constructed a new selection store while
the retained setup ViewModel still wrote to the old store. A regression retaining
the original gateway and recreating the display repository failed before the
fix. The debug runtime now obtains one application-scoped selection store, so
the retained gateway updates the recreated display repository.

Fresh verification:

- Focused Gradle `testDebugUnitTest` with `*DeviceSetup*Test`,
  `*Development*Test` and `*NamazTvAppUiTest`: 145 tests; the new lifecycle
  regression was the sole failure before the fix, then all 145 passed.
- `make docs-check`: passed.
- `GOCACHE=/tmp/namaz-time-go-cache GRADLE_USER_HOME=/tmp/namaz-time-gradle
  make lint test test-android-all`: passed, including all 485 Android tests,
  Go/Python checks, strict dependency verification, debug/release assembly,
  lint and APK build identity checks. APKs reflect the reviewed dirty working
  tree based on `f21fe9a`; this is not a new signed pilot release.
- `apkanalyzer dex packages --defined-only`: the synthetic gateway, display
  repository and selection store are present in debug and absent from release.
- The signed Ulyanovsk snapshot retains SHA-256
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`;
  Room schemas and contracts have no changes.

No fresh device runtime claim is made by this follow-up. Reverting the debug
setup change requires no schema migration; the signed Room snapshot remains
available and the private debug selection IDs can be ignored by older builds.

## Remaining boundary

- `UNKNOWN` — production remote setup remains unavailable until an operator
  deploys the PostgreSQL-backed API, configures its immutable setup revision,
  and provisions the TV with a device-scoped credential.
- `UNKNOWN` — no real approved Moscow or Omsk organization/timetable has been
  onboarded. The repository's only real executable schedule remains the signed
  Ulyanovsk pilot snapshot.
- `UNKNOWN` — production activation of a different city's real timetable needs
  a separate signed, validated, locally persisted assignment/delivery contract.
  The current provisioned release/pilot flow remains the ADR 0016
  `pending_review` handoff.

No Room schema, signed snapshot, real prayer rows, source approval, permission,
or pilot application data changed.
