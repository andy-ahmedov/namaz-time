# T029 donation-display pixel-match evidence — 2026-08-27

## Scope and evidence boundary

- `CONFIRMED_PUBLIC` — the product-owner-supplied `qr_page.png` is the
  canonical visual specification authorized under ADR 0014; its source size is
  1672×941 and SHA-256 is
  `fe8625a4e74f4c8baa928b8846f63cb00b729ce7868fafde49ea1d1610ab0bf2`.
- `CONFIRMED_RUNTIME` — the bounded observations below were reproduced on the
  controlled Android TV Emulator API 36 at 1920×1080/320 dpi, ADB serial
  `emulator-5554` (`sdk_google_atv64_x86_64`).
- `UNKNOWN` — physical-TV overscan/readability, OEM rendering and picker
  behavior, real-phone QR scan distance and long-running 4K/panel behavior.

The raw reference, screenshots, 50/50 overlays, enhanced diffs and UIAutomator
dumps remain outside Git under `artifacts/t029-donation-pixel-match/`. The
runtime retained the operator's existing local donation URL/details and QR
payload; no reference banking data or payment destination was introduced.

## Visual comparison loop

Every pass assembled the debug APK, replace-installed it without clearing Room
or Preferences DataStore, opened the donation display, captured an ADB
screenshot, normalized the reference to 1920×1080, then generated a 50/50
overlay, a 2× contrast diff and separate brand/settings/card/QR/rows/footer
region overlays with Pillow 12.3.0.

| Pass | Screenshot SHA-256 | Measured result |
|---|---|---|
| T028 baseline | `5ad056f6170096717305ce92c183e4abaa48389506b8f0e6ffaf60db62c85f11` | Three-column image/QR/details layout and dominant headings did not match the reference. |
| 1 | `84e7b47fe75bef3e0cdf420b46d9a97fc4a96f4e2d944f345811c9a2ba7f99dd` | Established the full-bleed image, centered brand, compact gear, one right card and footer; exposed a QR Y error and compressed details rows. |
| 2 | `d79701ea8fe27e25b3af467a6f1ac1e15913b2dc3acb8231dba4e4b1f5aac3fe` | Matched QR surface/badge scale and five-row/separator rhythm; exposed brand clipping and local icon/ornament offsets. |
| 3 | `6e4783eea6bdd075265b69b1e44f3c307f34a95112bb7b4ea3f94357017ef960` | Matched gear/link/footer ornament positions and showed the complete NamazTime label; exposed footer-text ellipsis. |
| Final | `f7851789fa453c299eea7bcb2b600460293b78c7a37ec58313cc9d3ed3baf5c8` | Complete localized footer text and final reference-aligned composition. |

Final evidence SHA-256 values:

- 50/50 full overlay:
  `12edf996575f0efa28ca0d3848432ab283ed83d004770c43d6f22056923758ad`;
- enhanced full diff:
  `c02aa9bd7c5255e9b71b8bfb8179bbfab1080d7d6657a4dea6826b451bd901a5`;
- brand/settings/card/QR/rows/footer region overlays:
  `5426ce62e38469604574b5e0e11a6d744e3c39fa77d900dabef75eee61ae63f4`,
  `f864b68ca86f54a6466d0b1c0255e93310dd2cfbb0fce2ad065e25066e2a3233`,
  `283d78a2820244da8c22ff2d4e7fe3a7b445b8a45e6b5fe7a30cae1ef536529c`,
  `858412839140859d7224144d1522cd1eb96e7bd4ff4209050f7d1fe966a3c970`,
  `dd3431a76a565712ecd596385f4ab047ff3f8fd9a9c0703b6fde7989fd6fb0a9`
  and `2c613f294ca22b55d0c69366397b529f08b23d882a7d7bf8a399f4a42dbb24e7`.

## Pinned 1920×1080 geometry

The adaptive Compose contract is expressed at a normalized 960×540 dp design
surface and scales uniformly. The corresponding controlled-emulator anchors
are:

| Region | Runtime/reference contract in px |
|---|---:|
| Brand pill | x=838, y=33, w=242, h=70 |
| Settings visual | x≈1808, y≈29, w=76, h=76 |
| Donation card | x=1193, y=119, w=590, h=827 |
| QR outer surface | x≈1328, y=250, w=308, h=308 |
| Details rows | x=1233, y=631, w=510, h=290 |
| Footer panel | x=194, y=970, w=1530, h=81 |

`CONFIRMED_RUNTIME` — full-screen background/card/footer bounds, title and
subtitle baselines, QR surface, five equal details rows, separator baselines,
ornament axes and compact Settings focus were inspected in the final full and
region overlays. The remaining deliberate visual differences are NamazTime
branding and center badge, operator-entered values/QR payload and the selected
offline background image.

## Functional observations

- `CONFIRMED_RUNTIME` — Settings remained reachable by D-pad from donation
  mode. The Donation editor displayed separate recipient, bank, card number,
  SBP/phone and collection-link fields, five packaged image previews, the
  custom-photo slot and existing mode actions. Screenshot SHA-256:
  `d0db887551289fd5a6370d821c04dab889682450c9afcf61434cbc1060f55110`.
- `CONFIRMED_RUNTIME` — the focused gear retained its compact 76 px visual
  surface and used a thin gold outline without scaling or a yellow fill.
- QR raster tests obscure the actual 44/140 center-badge ratio and decode at
  128, 192, 360 and 540 physical pixels with high error correction and a
  four-module quiet zone.
- Final debug APK SHA-256:
  `e8eee9ae250e24a749d290daf42772016de39f09e17839d24eb96ead7ebae366`.
