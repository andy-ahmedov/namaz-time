# ADR 0014: Owner-authorized visual references may be reproduced

- Status: Accepted
- Date: 2026-08-21
- Amends: the reference-use restriction in ADR 0012

## Context

The repository originally prohibited accurate reproduction of supplied
third-party visual references. The product owner has now explicitly confirmed
that references they provide with permission may be used accurately for this
product's layout, styling, decorative patterns, icons and wording.

For T025 the supplied `main_with_qr.png` reference has SHA-256
`dbe3fa01283d5176237923b6ae87f6a4beadaacb312e56707c2c90a74577b2ea`.
The product owner explicitly authorized reproduction of its QR-panel styling,
support icon, corner frame and lower geometric ornament on 2026-08-21. The raw
reference remains outside Git. The QR center uses the NamazTime mark rather
than third-party branding and is protected by an explicit decode regression.

For T028 the product owner explicitly directed the implementation on 2026-08-24
to use the supplied `new_main_page_with_setting_icon.png` as a visual
specification rather than inspiration. Its SHA-256 is
`5e90d9c20773ed6708fd0f4be819dc34494fce20051d0e0785457e8aa1a524f7`.
Only the main-screen Settings control geometry/surface/icon treatment is used;
the raw reference and its third-party brand remain outside Git.

For T029 the product owner explicitly directed the implementation on 2026-08-27
to treat the supplied `qr_page.png` as the canonical pixel-accurate visual
specification for the standalone donation display. Its SHA-256 is
`fe8625a4e74f4c8baa928b8846f63cb00b729ce7868fafde49ea1d1610ab0bf2`.
The authorization covers composition, glass surfaces, decorative lines,
custom-drawn icon silhouettes and wording. NamazTime branding, the locally
generated QR payload and operator-entered details replace the reference sample
content. The raw reference remains outside Git; no bitmap icon was extracted.

For T033 (requested by the product owner as “T030 — pilot UI fidelity and
operator customization”; repository number T030 was already assigned) the
product owner explicitly directed the implementation on 2026-08-29 to treat
three supplied images as canonical visual specifications:

- `new_reference.png`, SHA-256
  `0df8e3b5ac25e7f65c975d354e19b3e652b20da33e8d9bea10602fbf5b6e6c27`,
  for the main display, its original line icons, row highlight, arch/lantern
  watermark and shared QR frame/glyph;
- `design_item_in_the_menu.png`, SHA-256
  `542c4c2b083365549a594b18d195de485b7604fe711f4679be4443de582a2cc8`,
  for the Appearance and donation-image selector hierarchy;
- `layout_of_blocks_on_the_donation_screen.png`, SHA-256
  `e5ea06acc72131935c1a12a2963a3902dfd43f3f3d0a6941304ee0520982c8bb`,
  for the standalone donation-screen block composition.

The supplied screenshots are `CONFIRMED_PUBLIC` visual requirements. The
authorization covers visual hierarchy, proportions, decoration, icons and
wording. It does not authorize third-party branding or extracted resources.
NamazTime identity, authenticated schedule data, locally generated QR payload
and operator-local content remain independent. The raw references remain
outside Git.

On 2026-08-30 the product owner clarified the 16:9 interpretation of
`layout_of_blocks_on_the_donation_screen.png`: all standalone donation
foreground blocks belong to one right-anchored rail occupying no more than 30
percent of the viewport. The approximately 70-percent left region must remain
free of foreground blocks so the chosen image is visible. This clarification
supersedes the earlier full-width T033 translation without changing the
reference authorization or any schedule/content provenance boundary.

## Decision

An explicitly authorized, product-owner-supplied visual reference may be
reproduced accurately. The relevant UI or evidence document must record the
authorization basis and identify the reference without committing it unless
its redistribution is also authorized and needed.

This permission applies to visual outcomes only. It does not authorize copying
source or decompiled code, package-private names, full datasets, embedded
secrets or resources extracted from an APK. Implementations remain written in
this repository and must preserve accessibility, correctness and platform
constraints.

## Consequences

- future visual tasks can follow an authorized supplied reference closely
  without a blanket clean-room mismatch;
- the evidence trail distinguishes authorized visual use from APK/static
  analysis;
- authorization must be explicit rather than inferred from possession of a
  screenshot;
- code, data, secrets and APK-extracted resources remain prohibited.

## Rejected alternatives

- retain a blanket prohibition despite the owner's explicit permission;
- treat every internet or APK-derived image as implicitly licensed;
- commit raw references or extracted resources without a documented need and
  redistribution basis;
- let visual permission weaken schedule provenance or security invariants.
