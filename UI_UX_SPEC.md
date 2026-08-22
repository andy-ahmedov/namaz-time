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

When a complete device-local sadaqah QR configuration exists, display mode
uses the authorized `main_with_qr.png` three-column composition: the existing
left next/time stack and prayer panel remain a single group with their bottom
event strip, while a tall right glass panel spans from the prayer-body top to
the strip bottom. The panel contains `Садака`, an original line/diamond
divider, the operator-entered purpose, a high-contrast locally generated QR in
the supplied-reference corner frame, the supplied-reference support-icon
treatment beside the operator-entered motivation, a small NamazTime center
badge protected by high QR error correction, and a subdued Islamic geometric
lower ornament. Empty/invalid settings remove the third column and
restore the normal T024 composition. No technical preview label appears in
public display mode.

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

The Phase 4 built-in backgrounds are original static landscape WebP images.
Golden dusk is the safe default and Blue hour is selectable from Appearance;
both remain offline and use the same bounded dark scrim. Unknown persisted IDs
fall back to Golden dusk. For the T024 main-display pass, the
product-owner-supplied `design.png` is the geometric visual specification for
composition, proportion, hierarchy, spacing and decorative rhythm. T025 also
uses the explicitly authorized product-owner-supplied `main_with_qr.png` as the
visual specification for the QR composition, frame, support icon and lower
ornament under ADR 0014. Neither raw reference is packaged. Custom/remote media remains
outside this local allowlist and requires its separately approved signed asset
pipeline.

The main display uses a centered NamazTime pill, mosque identity, a left
next-event/countdown and local-clock stack, a right six-row prayer table and a
bottom iqamah/next-event strip with exceptional status only when required.
Every prayer has a distinct original gold line
glyph. Sunrise displays its single adhan value centered across the shared
adhan/iqamah time area. The next row uses a side marker, border and translucent
accent surface so its meaning does not depend only on color.

The normal T024 display composition occupies about 71 percent of the complete
16:9 viewport after its existing overscan-safe frame is applied. Its two main
columns are equal. At the controlled 960×540 dp-profile comparison, the body
starts at about y=120, the next/clock cards are about 185/128 high and the
bottom strip starts at about y=453. The next card contains only the next-prayer
label, event name, original line/diamond divider and fixed-width countdown.
The clock card uses an original calendar glyph plus date, weekday, divider and
local time. Location is flanked by original line/diamond ornaments. Normal
approved provenance stays in Settings; only status requiring attention may add
a compact public-display marker. The Settings action remains a 48 dp focusable
target but is visually subordinate and icon-only with an accessible name.

In public display mode, the foreground safe-frame content uses a deterministic
six-position cycle every ten minutes, bounded to ±2 dp per axis. The full-bleed
background and settings route remain fixed, and the shift does not animate or
change semantic/focus order. This is a conservative screen-retention measure,
not evidence that a particular panel cannot retain or burn in an image.

T026 keeps that structure and refines the `main_with_qr.png` composition as a
presentation-only layer. The image background receives a stronger bounded navy
treatment; high-opacity matte navy/blue-gray cards use thin neutral outlines,
muted champagne accents and gray secondary text. Large clock/countdown values
use lighter weights, and gold is no longer a card-level selection border.

Decorative horizontal lines use a shared thin, softly glowing treatment whose
opacity fades toward both ends around a small diamond. The next-prayer card has
a non-semantic low-contrast arch and hanging-lantern watermark. A connected
star/diamond lattice is reused below the QR and at the right of the bottom
strip. The QR keeps its local payload and error correction but renders on a
rounded warm-white surface with dark-navy modules, a decorative NamazTime
center badge and an asymmetric corner/bottom frame. These decorative layers do
not read data, accept focus, or introduce network work into display state.

## Settings navigation

Suggested left navigation:

1. Mosque/location
2. Prayer source (read-only summary for normal operator)
3. Iqamah
4. Appearance
5. QR code
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
3. optionally enter five device-local iqamah values; these remain visibly and
   architecturally separate from signed snapshot provenance;
4. optionally switch the whole UI to English; persist the choice locally;
5. optionally enter a validated HTTPS QR link, sadaqah purpose and motivation;
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
