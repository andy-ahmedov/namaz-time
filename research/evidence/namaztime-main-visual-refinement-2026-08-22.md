# T026 main-display visual refinement — 2026-08-22

## Scope and evidence

- `CONFIRMED_PUBLIC` — product-owner-supplied `main_with_qr.png`, authorized
  for visual reproduction by ADR 0014. SHA-256:
  `dbe3fa01283d5176237923b6ae87f6a4beadaacb312e56707c2c90a74577b2ea`.
- `CONFIRMED_RUNTIME` — starting emulator state
  `namaztime-t025-qr-final.png`, SHA-256:
  `d5b2efb270bd56412b1b4e8f802e0ebf675b7199658a59a9f90ed0e199bc9293`.
- `CONFIRMED_RUNTIME` — three controlled build/install/screenshot iterations on
  Android TV Emulator API 36 at 1920×1080/320 dpi.
- `UNKNOWN` — physical-TV overscan/readability, OEM rendering, real-phone QR
  scan distance and long-running 4K/panel behavior.

The task was presentation-only. Prayer/domain logic, real local state, QR
payload generation and validation, localization, D-pad navigation and
offline-first data flow were intentionally unchanged. Raw references and
runtime screenshots remain outside Git under
`artifacts/t026-main-visual-refinement/`.

## Direct comparison loop

Each pass used the same method: assemble the debug APK, replace-install it,
launch `MainActivity`, capture a 1920×1080 screencap, scale the authorized
reference to the same size and compare the two images side by side.

| Pass | Screenshot SHA-256 | Result |
|---|---|---|
| 1 | `abbeaf83d2ff5f81de73a698f61cda2b1d9c0d2eb0f29457ec57fbdd5f2eeba2` | Established dense matte surfaces, subdued background/accent, lighter typography, watermark and shared ornaments; exposed a visually clipped local clock. |
| 2 | `1bb507c2bfa2094c1068330101699a380c252d779ffbb41d68937dbc065b2e9d` | Restored the complete `HH:mm:ss` clock region and brought column/QR proportions closer to the reference. |
| 3 / final | `75c5550a42ec1ad0b7f885e74ce5eab90dd120545f110a84c054c156175981e8` | Refined the asymmetric QR frame, connected geometric lattice, decorative-line fading and final type scale. |

The final side-by-side image SHA-256 is
`0919e22424c997c08769acf640874e1ca8c73e31000f0d652280c2bf60e0d80b`.
The final debug APK SHA-256 is
`190cb91ef2fcba34e60e48bbc70d7a50bfa4682968bc02871e0d2276144daab3`.
The implementation commit is `8bd0753`.

## Requirement-by-requirement visual review

| Requested refinement | Final review |
|---|---|
| Dense matte navy/blue-gray cards | `CONFIRMED_RUNTIME` — main surfaces use high-opacity navy layers; the photograph is only faintly visible and no longer recolors the cards. |
| Subdued background | `CONFIRMED_RUNTIME` — a stronger bounded navy treatment makes the interface dominant while retaining the selected offline image. |
| No gold next-card outline | `CONFIRMED_RUNTIME` — primary card borders are thin neutral silver/blue-gray; gold is local to content and ornaments. |
| Muted champagne/warm-sand accent | `CONFIRMED_RUNTIME` — the former bright yellow-gold was replaced by a calmer champagne palette. |
| Lighter typography and digits | `CONFIRMED_RUNTIME` — display/countdown/clock weights are Light or Regular; Medium/Semibold is limited to key values. The clock remains fully visible. |
| Arch and suspended lantern watermark | `CONFIRMED_RUNTIME` — a low-contrast decorative Canvas layer sits inside the next-prayer card and is non-semantic. |
| Unified arabesque/geometric language | `CONFIRMED_RUNTIME` — the same thin star/diamond lattice language appears below the QR and at the right of the bottom strip. |
| Softly fading decorative lines | `CONFIRMED_RUNTIME` — location, next-card, QR and clock ornaments use thin gradient-ended lines, restrained glow and a central diamond with no hard cutoff. |
| Less tabular prayer list | `CONFIRMED_RUNTIME` — rows have lighter labels, more restrained headings and low-contrast fading separators. |
| Reference-like current/next highlight | `CONFIRMED_RUNTIME` — a full-width translucent gold-brown row, fine outline and subtle glow replace the technical side marker. |
| Consistent prayer/iqamah icons | `CONFIRMED_RUNTIME` — all glyphs share one stroke fraction, calm gold tone and coordinated sizing while retaining the original NamazTime shapes. |
| Reference-like QR treatment | `CONFIRMED_RUNTIME` — rounded warm-white surface, dark-navy modules, an ornamental NamazTime center badge and an asymmetric two-part corner/bottom frame replace pure black and four scanner brackets. Existing high-error-correction and decode behavior remain tested. |
| Compact horizontal support block | `CONFIRMED_RUNTIME` — a small gold support glyph is placed left of compact multiline copy. |
| Denser independent bottom surface | `CONFIRMED_RUNTIME` — the strip uses the strong matte surface and a subtle right-side lattice. |
| Calmer secondary copy | `CONFIRMED_RUNTIME` — secondary labels use a muted blue-gray rather than near-white. |
| More exterior air and lighter brand pill | `CONFIRMED_RUNTIME` — composition width and QR column were slightly reduced; the NamazTime pill has a thinner border, quieter fill and Regular label. |

## Intentional remaining differences

The final screen deliberately retains the NamazTime name and crescent, current
mosque/address/date/time/prayer/iqamah values, the locally configured QR
payload and copy, the project's original prayer glyphs, explicit Adhan/Iqamah
headings, and the focus-visible Settings control required for D-pad access.
Those differences preserve product identity, truthful live state,
accessibility and existing behavior rather than imitating the reference's
foreign brand or sample content.
