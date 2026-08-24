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
