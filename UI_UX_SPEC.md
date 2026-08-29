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

T028 additionally provides a standalone donation display mode. It replaces the
schedule composition only after a complete local configuration passes the same
HTTPS campaign validation and QR generator used by the optional column. The
T029 composition treats the authorized `qr_page.png` as its exact visual
specification: the chosen image is full bleed; a centered NamazTime glass pill
and compact top-right gear sit above one tall right-side donation card; a
separate long glass gratitude panel sits at the bottom. The left side has no
title/card and remains primarily photographic. The donation card contains the
localized donation heading and scan instruction, a large locally generated QR,
a fading gold diamond divider, a details heading and five equal
`icon → label → value` rows for recipient, bank, card number, SBP/phone and
collection link. The footer uses the exact localized gratitude sentence. All
screen-specific icons and ornaments are Compose Canvas/vector drawings;
decorative lines fade to transparency instead of ending abruptly.

The QR uses dark-navy modules on a rounded warm-white surface, a thin
champagne frame and a NamazTime center badge while retaining a four-module
quiet zone, high error correction and decode regressions for the actual badge
ratio. Runtime values come only from the operator's local fields; sample
banking values from the reference are never defaults. Five packaged image
choices and one validated app-local custom-photo slot remain available. A
focused Settings control is always visible; Settings exposes an explicit
action to return to the prayer schedule. No payment flow is present.

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

The Phase 4 built-in backgrounds are eight original/project-derived static
landscape WebP images. Appearance renders a D-pad preview gallery and persists
only allowlisted IDs. A ninth custom slot imports JPEG/PNG/WebP through the
system document picker into a validated app-local copy without broad storage
permissions. Missing/corrupt custom media and unknown persisted IDs fall back
to Golden dusk; all backgrounds use the same bounded dark scrim. For the T024 main-display pass, the
product-owner-supplied `design.png` is the geometric visual specification for
composition, proportion, hierarchy, spacing and decorative rhythm. T025 also
uses the explicitly authorized product-owner-supplied `main_with_qr.png` as the
visual specification for the QR composition, frame, support icon and lower
ornament under ADR 0014. Neither raw reference is packaged. Custom/remote media remains
outside this device-local pipeline and requires its separately approved signed
asset pipeline.

T028 uses the separately authorized `new_main_page_with_setting_icon.png` as
the exact Settings-control specification: at the normalized 960×540 profile it
is a 40 dp rounded-square glass surface, 20 dp from the right and 27 dp from the
top, with a white outline gear and a restrained neutral border. D-pad focus
replaces that border with one gold outline and does not scale the control.
Settings navigation likewise uses one gold focused fill with no white inner or
outer frame; selected-but-unfocused state remains a softer gold surface.

T029 uses the separately authorized `qr_page.png` for the standalone donation
screen under ADR 0014. At the controlled 1920×1080 profile the principal
anchors are approximately: brand `(838,33,242,70)`, Settings visual
`(1808,29,76,76)`, donation card `(1193,119,590,827)`, QR
`(1328,250,308,308)`, details rows `(1233,631,510,290)` and footer
`(194,970,1530,81)`. The same normalized 960×540 coordinate system scales
uniformly for 720p/1080p/4K. Focus keeps the gear size stable and substitutes a
thin gold outline/glow rather than a filled yellow surface.

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
rounded warm-white surface with dark-navy modules and a decorative NamazTime
center badge. T033 supersedes the old asymmetric frame with four identical
short L-shaped corners, mirrored around the QR with equal arms. These decorative layers do
not read data, accept focus, or introduce network work into display state.

T033 uses the owner-authorized `new_reference.png` as the canonical main-screen
visual contract under ADR 0014. At normalized 960×540 its principal campaign
anchors are approximately: next card `(110,122,268,190)`, local-clock card
`(110,320,268,127)`, prayer card `(385,122,267,325)`, event strip
`(110,460,542,52)` and QR panel `(660,122,201,390)`. Real text length and the
bounded ±2 dp retention shift may move glyph bounds, but structural anchors
target 4–8 px tolerance. The active prayer highlight spans the prayer-card
interior with about a 4 dp inset, translucent warm fill, thin outline and a
separate thicker left accent. Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha,
collective prayer and phone/support use original rounded-cap champagne line
drawings. The next-event watermark is a pointed Islamic arch with a suspended,
pane-detailed lantern. Next-event/date content uses explicit top anchors rather
than centered column arrangements. The shared QR primitive uses the same four
corners and crescent/two-star center glyph on main and standalone donation
screens.

T033 also changes the Iqamah editor contract. Fajr, Asr, Maghrib and Isha show
bounded `adhan + N minutes` values; Dhuhr shows a fixed mosque-local `HH:mm`
value from the signed 13:15 base policy. Every +/− press changes one minute.
Changing Dhuhr updates both its iqamah (including Friday) and Jumu'ah as one
local setting. Sunrise remains absent. Returning to “Use schedule” clears only
the device-local projection and reveals the signed base policy.

For mosque ID `second-cathedral-mosque-ulyanovsk`, the public main display and
Mosque settings summary use the concise presentation identity `Вторая Соборная
Мечеть` / `Ульяновск`. This is a UI-only alias: the canonical signed snapshot,
source scope, approval evidence and stored locality remain unchanged and remain
available to provenance/diagnostic flows.

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
3. optionally set four device-local `adhan + N minutes` offsets and one linked
   fixed Dhuhr/Jumu'ah time using one-minute D-pad +/− controls; Sunrise has
   none, and every value remains visibly and architecturally separate from
   signed snapshot provenance;
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
