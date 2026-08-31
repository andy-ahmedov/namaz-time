# T043 controlled Android TV runtime validation

Date: 2026-08-31

## Environment and artifact identity

- emulator: `sdk_google_atv64_x86_64`;
- Android: 16 / API 36;
- viewport: 1920×1080 at 320 dpi, equivalent to the controlled 960×540 dp
  profile;
- package: `ru.namaztime.tv`;
- installed version: `0.5.1-pilot.1`, `versionCode=5`;
- final clean build commit: `cfa46219f3523afd01cc5dde683ef3f696fdbbac`;
- final APK SHA-256:
  `c1bd818da96efac067b5c087badb05aba3dfbd8916a52c20a6fdca4c221e591a`;
- APK certificate SHA-256:
  `da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`.

`CONFIRMED_RUNTIME`: `adb install -r` installed the final signed pilot over the
existing package. Android retained `firstInstallTime=2026-08-29 10:32:25` and
reported `lastUpdateTime=2026-08-31 17:42:27`; no uninstall or data clear was
used. The final artifact passed the repository application identity,
certificate and authenticated-pilot-asset checks before installation.

The first seven required screenshots were captured from the clean signed
`1cc2119ea6d7d5d327a0f8946eef56e07db3f5cd` checkpoint after all four owner
issues and code 5 were implemented. Screenshot 8 was recaptured after the
targeted donation status correction at signed checkpoint
`1982d400732fa7b733e52357b8deb313a27adcd6`; screenshots 9–10 are from the
final signed checkpoint above. The later changes are confined to the named
donation-status and Mosque/settings layout audit defects.

## Owner issue results

- `CONFIRMED_RUNTIME`: the main QR panel renders the previously truncating
  long English message in full without an ellipsis, overlap or QR-size change
  (`01-main-qr-long-message.png`). QR decoding at the supported raster sizes is
  automated evidence; a representative-distance physical-phone scan remains
  `UNKNOWN`.
- `CONFIRMED_RUNTIME`: all five Iqamah rows, Save and Return are simultaneously
  visible. Fajr was moved to `+1 мин`, then its ordinary D-pad “По расписанию”
  action restored the signed-schedule state (`02`–`04`).
- `CONFIRMED_RUNTIME`: with the TV document-provider stub disabled, Appearance
  and Donation both opened the available system Photo Picker rather than an
  unresolvable intent (`05`, `06`). One actual PNG was selected, validated,
  copied to the app-private slot and rendered in the Appearance preview
  (`05b`). Focus returned to the invoking Settings action. The package still
  had `READ_MEDIA_IMAGES: granted=false`; the system-picker route did not ask
  for a library grant.
- `CONFIRMED_RUNTIME`: Donation Settings contains QR URL, recipient, bank, card,
  SBP/phone, gratitude and image controls but no collection-link field (`07`).
  The standalone screen renders exactly four transfer rows and the ordinary
  current-prayer label remains readable (`08`).

The OpenDocument stub and the non-serving standalone
`com.google.android.photopicker` package temporarily disabled for the
capability test were re-enabled after capture. The usable Photo Picker hosted
by the system media-provider module remained available. Consequently, the
last-resort in-app MediaStore browser did not become the runtime route; its
grant/denial/revocation/paging behavior is covered by the automated
coordinator, repository and Compose tests and remains `UNKNOWN` on the
physical pilot TV.

## Targeted visual audit

`CONFIRMED_RUNTIME`: Main + QR, Iqamah, Appearance, Donation Settings,
standalone Donation, Mosque/location and city/schedule setup were reviewed at
the controlled profile. Two small directly related defects were found and
fixed:

1. standalone Donation allocated too little width to the ordinary `Магриб`
   status label; `1982d40` increases only that neutral status cell and a
   geometry regression pins the readable width;
2. the long Mosque page heading and default 28-sp secondary line height pushed
   read-only source/timezone context below the clipped editor area. `a674a0e`
   and `cfa4621` use compact heading metrics, explicit source/timezone lines and
   18-sp secondary line height. Both values and all actions are now visible in
   `09-mosque-context-visible.png`.

`CONFIRMED_RUNTIME`: city/schedule setup still opens through D-pad, the system
IME appears for the canonical city field, and the first Back hides the IME
while leaving the setup screen and active Ulyanovsk last-known-good context
intact (`10-city-schedule-setup.png`). No T041 selection, registry or
`pending_review` behavior changed.

## Screenshot hashes

| File | SHA-256 |
|---|---|
| `01-main-qr-long-message.png` | `112cea70d937392c4b8b8b772428754fefbb4537504241f9cb582b5427f4631f` |
| `02-iqamah-all-five.png` | `d177cc3dc4d1019020b6a5333d75e028479a7cfa59bca92a4d03a09c8cc15ee8` |
| `03-iqamah-modified.png` | `131d69f53b57e606006c0acb4c755d1d244fa1b5b8d11662a5fcc479549928aa` |
| `04-iqamah-restored.png` | `c2d18430b7ef4432bf225e2467c3d2283ed4322e490755e78dd728f3dee577d9` |
| `05-appearance-image-selection.png` | `835df923ef96358f3429ffbc746016ee316d7e7a3d1d7049a1af414ad9d40a93` |
| `05b-appearance-imported.png` | `94377c4195b40a4820326ba9da48731611efff02d89f4e7df1f0e2bade182e8d` |
| `06-donation-image-selection.png` | `693625745b8cb80173502493ea56cd231cfa517e259dfdcb8bd0942ee9879804` |
| `07-donation-settings-no-collection-link.png` | `20f1006702f488fdc591cf861a00b7ea5869f860f1a34989ac88d7b828ef3410` |
| `08-donation-four-rows.png` | `11e2a61dcf817a8f01a7a05cfc813e5f570e9c95e86403ef65f6b6da9a4e8f65` |
| `09-mosque-context-visible.png` | `fd3e02f7a2b6c55b60eb40b3033dd7228b6d374061ec2d8122ce5b45b2f9d530` |
| `10-city-schedule-setup.png` | `938cee99c809667692ee67bbac262d35445815d35ba70c5ea75aba7560a391f7` |

All screenshots contain only NamazTime, Android system-picker surfaces and
synthetic `example.*`/test payment values. The four owner reference images,
APK, signing material and imported external bytes remain outside Git.

## Repository verification

The final T043 worktree passed:

- `make docs-check`;
- `make test`;
- `make lint`;
- `make test-postgres`, including the clean restore drill;
- `make test-android-all` with strict dependency verification, debug/release
  tests, lint and APK identity checks;
- `go test -race ./...`;
- `make security-go` — no reachable Go vulnerability found;
- `make secret-scan` — 105 commits / approximately 9.04 MB scanned, no leak
  found.

The Android schema-diff gates remained clean. T043 needs neither a Room nor a
PostgreSQL migration: all changed durable values are bounded Preferences
DataStore fields or a deprecated key cleaned during the existing normal save.

## Preserved schedule invariant

`CONFIRMED_STATIC`: the packaged raw snapshot remains
`ulyanovsk-second-cathedral-2026-pilot-local-v2` with SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
T043 does not rewrite Room rows, approval, authority, signature or snapshot
bytes. Physical-TV overscan, OEM picker behavior, hall readability and the
representative-distance QR scan remain `UNKNOWN` pending the hardware run.
