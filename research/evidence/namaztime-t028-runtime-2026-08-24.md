# T028 local operator UX runtime evidence — 2026-08-24

## Scope and evidence boundary

- `CONFIRMED_PUBLIC` — the product-owner-supplied
  `new_main_page_with_setting_icon.png` is the authorized visual specification
  for the main Settings control under ADR 0014; SHA-256:
  `5e90d9c20773ed6708fd0f4be819dc34494fce20051d0e0785457e8aa1a524f7`.
- `CONFIRMED_RUNTIME` — the bounded observations below were reproduced on the
  controlled Android TV Emulator API 36 at 1920×1080/320 dpi, ADB serial
  `emulator-5554`.
- `UNKNOWN` — physical-TV overscan/readability, OEM focus/IME behavior, an OEM
  document provider, real-phone QR scan distance and long-running 4K/panel
  behavior.

The raw reference, screenshots and UIAutomator dumps remain outside Git under
`artifacts/t028-runtime/`. Synthetic donation content uses `example.org` and is
not an approved payment destination or official mosque claim.

## Build and main/Settings observations

The debug pilot-local APK was built and replace-installed without clearing Room
or Preferences DataStore. APK SHA-256:
`9fd1fe30ff2e53e5f36b33d05c0ef178a6d853fdb539fcd9a0675bbfaab6d2ad`.

- `CONFIRMED_RUNTIME` — the main display retained the real pilot mosque,
  mosque-local clock, six adhan/iqamah rows, existing local QR column and
  offline image background. The Settings control rendered at the
  reference-normalized top-right position as a rounded-square glass surface
  with white gear and one gold focus outline. Main screenshot SHA-256:
  `aff13bc279c47ee15a039dc4de918d2e2cd49753fcc701d0f88d5059257b3ac2`.
- `CONFIRMED_RUNTIME` — Appearance displayed eight built-in previews and the
  custom slot together. The selected navigation item used one gold focused
  fill without the prior white rectangular frame. Appearance screenshot
  SHA-256:
  `6c6bc9680a42dce8c106a2737dc120378606bcfd88485cc1fad81663011956e6`.

## Donation display loop

- `CONFIRMED_RUNTIME` — the first 1080p pass exposed clipped donation message
  and image controls because three actions consumed vertical space. The action
  group was changed to one D-pad-connected horizontal row and the APK was
  rebuilt/reinstalled. The repeat pass exposed all three fields, five built-in
  previews, the custom slot, save/picker/mode actions and return action inside
  the Settings safe frame. Corrected empty/configured Settings screenshot
  SHA-256 values are
  `bc7bc2060a58ceca3fe4ae835b00c958f5a7efe146d9f9dcc8fc9d0c2b579c51`
  and
  `e51b4048f6073b3b4b399e28534d602292b493dbd77cdb334498d30bf34ac420`.
- `CONFIRMED_RUNTIME` — operator input was entered through focused fields,
  validated, saved to DataStore and activated without modifying the Room
  schedule. The standalone display rendered the selected packaged image,
  locally generated QR, message, transfer details and initially focused
  Settings control. Donation display screenshot SHA-256:
  `5ad056f6170096717305ce92c183e4abaa48389506b8f0e6ffaf60db62c85f11`.
- `CONFIRMED_RUNTIME` — Settings remained reachable from donation mode. The
  mode was switched back and the normal prayer schedule/QR composition was
  restored; screenshot SHA-256:
  `0f0fffed11f9fdec952dbecd479e60d49f130ecc09d71de5ce3a77621ea4357f`.

## Platform document picker

- `CONFIRMED_RUNTIME` — the Appearance picker action dispatched the Android
  `OpenDocument` request while the app remained active. This emulator image has
  no installed document-provider activity and displayed the platform message
  that no application can perform the action; screenshot SHA-256:
  `2673bc74315baa83d0bc0c56502583324c320b299897b746613f7bfd0cc64296`.
- `UNKNOWN` — an end-to-end selection from an OEM document provider could not
  be reproduced on this emulator image. JVM tests separately cover bounded
  JPEG/PNG/WebP validation, independent background/donation app-local copies,
  last-known-good preservation and missing/corrupt custom-file fallback. This
  is not promoted to runtime evidence.
