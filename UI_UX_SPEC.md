# UI_UX_SPEC.md

## TV design constraints

- primary viewing distance is roughly 3 meters or more;
- input is D-pad, select and back—not touch;
- focused control must always be visually obvious;
- critical information cannot depend only on color;
- settings must not cover the main display with tiny desktop-style forms;
- 720p, 1080p and 4K layouts must preserve hierarchy;
- animations are subtle and optional; no distracting looping motion.
- the screen-on hint is active only in public display mode (including its safe
  unavailable state), and is released while settings are active.

## Main screen information hierarchy

1. **Current/next prayer and countdown** — largest emphasis.
2. **Daily adhan and iqamah grid** — readable from the hall.
3. **Current date and mosque-local time.**
4. **Mosque name / location.**
5. **Optional QR campaign or announcement.**
6. **Minimal diagnostic indicator only when action is required.**

The source/provenance is available in diagnostics/settings; a concise badge may appear on the main screen only for calculated/stale/unverified states.

## Main screen states

### Normal

- all six daily rows;
- next prayer highlighted;
- separate labels/columns for adhan and iqamah;
- countdown with stable width so digits do not shift layout;
- QR/announcement optional.

### Between adhan and iqamah

- emphasize current prayer and iqamah countdown;
- avoid changing the underlying daily table;
- optional mosque-configured reminder content.

### Prayer in progress

Post-MVP: dim/hide secondary content for configured duration. Never obscure emergency content without policy.

### Stale warning

Prayer screen continues from valid cached data. Show a small operator-action indicator. Do not show a full-screen error while current-day data is valid.

### Expired/no valid data

Show a safe diagnostic screen with mosque name, local clock, clear “schedule unavailable” message and support code. Do not fabricate calculated times.

## Prayer grid

Recommended columns:

```text
Prayer | Adhan | Iqamah
```

Sunrise has no iqamah. Jumu'ah is represented as one or more separate Friday sessions, not a mutation of stored Dhuhr.

## QR campaign panel

Fields:

- QR image generated locally;
- short title;
- optional two/three-line subtitle;
- optional campaign type label;
- no raw long URL on public display unless operator chooses it.

Requirements:

- preview at target TV resolution;
- real-phone scan test from representative distance;
- hide panel if URL invalid/expired;
- HTTPS by default;
- stable quiet zone and sufficient contrast;
- campaign must not reduce prayer text below readability threshold.

## Theme/background

- all screens use the T017 semantic dark/amber Compose tokens and shared
  translucent card/focus language; screen-local palettes are not allowed;
- background media is full bleed, while text and controls remain inside the
  overscan-safe content frame;
- clock, countdown and prayer-time digits use stable-width treatment;
- warm accent means current/next time-sensitive state, while warnings retain a
  separate semantic role; neither state depends on color alone;
- built-in themes available offline;
- custom image cropped separately for landscape/portrait;
- adjustable dark overlay;
- contrast validation warning;
- built-in fallback if asset is missing/corrupt;
- avoid detailed imagery behind small text;
- no video background in MVP.

The Phase 4 built-in background is an original static Compose drawing. The
product-owner-supplied `design.png` is a hierarchy/mood reference only and is
not packaged, cropped or copied as an application asset.

In public display mode, the foreground safe-frame content uses a deterministic
six-position cycle every ten minutes, bounded to ±2 dp per axis. The full-bleed
background and settings route remain fixed, and the shift does not animate or
change semantic/focus order. This is a conservative screen-retention measure,
not evidence that a particular panel cannot retain or burn in an image.

## Settings navigation

Suggested left navigation:

1. Mosque/location
2. Prayer source (read-only summary for normal operator)
3. Iqamah & Jumu'ah
4. Appearance
5. QR/announcements
6. Language
7. Autostart/kiosk
8. Diagnostics

Each page:

- one primary action;
- explicit focus order;
- preview where visual;
- destructive/reset actions require confirmation;
- unsaved changes are visible;
- back either saves explicitly or asks, never silently discards critical time changes.

## First-run flows

### Local pilot mode

1. start in Russian with the authenticated preconfigured mosque snapshot;
2. review mosque, timezone, source approval and coverage in settings;
3. review approved iqamah/Jumu'ah policy without editing snapshot data on TV;
4. optionally switch the whole UI to English; persist the choice locally;
5. preview an approved QR campaign only when one exists in the snapshot;
6. return to the main screen with D-pad focus restored.

### Remote mode

1. show pairing code and QR;
2. wait with retry/expiry state;
3. receive mosque/config snapshot;
4. show source and today's values for confirmation;
5. activate.

## Accessibility and focus tests

- every action reachable with D-pad only;
- no focus traps;
- initial focus explicitly defined;
- focus retained/recovered after dialog and process recreation;
- focused/pressed/disabled states distinguishable;
- TalkBack/semantics reasonable where supported;
- minimum contrast checked on each theme;
- reduced-motion option respected;
- Arabic/Russian text shaping and RTL/LTR mixtures tested.

## Content tone

Use calm, factual labels. Avoid marketing claims such as “official” or “most accurate” unless source metadata and approval justify them.
