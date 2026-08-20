---
name: android-tv-screen
description: Implement or review NamazTime Android TV display and settings screens while preserving offline local-state, D-pad, timezone, and responsive-layout invariants.
---

# Android TV screen skill

Use this skill for main display/settings work.

## Required reading

- `AGENTS.md`
- `UI_UX_SPEC.md`
- `ARCHITECTURE.md`
- `TEST_STRATEGY.md`

## Invariants

- UI reads local repositories only;
- D-pad/select/back path is complete;
- adhan and iqamah are visibly distinct;
- missing data is not invented;
- mosque timezone drives date/countdown;
- network/asset failure cannot blank prayer times;
- no competitor assets or pixel-copy.

## Workflow

1. Define screen states and initial focus.
2. Implement smallest landscape state from synthetic fixture.
3. Add 720p/1080p/4K and long/RTL text coverage.
4. Test D-pad reachability/back/focus restoration.
5. Test stale/expired/missing-iqamah states.
6. Keep animations subtle and reduced-motion compatible.
7. Run tests and update specs only for actual behavior.
