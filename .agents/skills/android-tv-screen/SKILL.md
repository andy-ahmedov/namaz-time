---
name: android-tv-screen
description: "Implement or review NamazTime Android TV display/settings, including D-pad focus and offline schedule presentation."
---

# NamazTime Android TV screens

Use the existing TV design system and immutable local display state. For a review,
report findings without implementing them unless requested.

## Read for the affected concern

Repository documents below are relative to the repository root:

- UI states, geometry and focus: relevant sections of UI_UX_SPEC.md.
- State ownership or data access: the TV/local-state sections of ARCHITECTURE.md.
- Verification: Compose/UI and the applicable presentation section of TEST_STRATEGY.md.
- Authorized reference reproduction: docs/adr/0014-owner-authorized-visual-references-may-be-reproduced.md.

## Constraints

- Keep adhan and iqamah distinct; never invent missing values.
- Mosque IANA timezone and the existing engine drive dates and countdowns.
- UI reads local repositories only; network/asset failure cannot blank prayer times.
- Reuse androidx.tv.material3 components and TvDesignSystem.kt. Mobile Material3
  guidance is not a reason to replace TV components or add phone navigation.
- Preserve D-pad/select/back reachability, initial focus and focus restoration.
- Owner-authorized references may be reproduced under ADR 0014; permission does
  not cover decompiled code, datasets, secrets or APK-extracted resources.

## Verification

For changed layouts, cover 720p/1080p/4K, affected long/RTL text and real D-pad paths.
For changed state presentation, test the affected stale/expired/missing-data states.
Inspect runtime visuals when appearance changes; distinguish emulator evidence
from physical-TV acceptance. Keep reduced-motion behavior for affected animations.
Run the relevant repository gates through acceptance; report unavailable hardware.
