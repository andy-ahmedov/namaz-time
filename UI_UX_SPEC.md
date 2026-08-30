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
historical T029 composition treated the authorized `qr_page.png` as its exact visual
specification: the chosen image is full bleed; a centered NamazTime glass pill
and compact top-right gear sit above one tall right-side donation card; a
separate long glass gratitude panel sits at the bottom. The left side has no
title/card and remains primarily photographic. The donation card contains the
localized donation heading and scan instruction, a large locally generated QR,
a fading gold diamond divider, a details heading and five equal
`icon → label → value` rows for recipient, bank, card number, SBP/phone and
collection link. The footer uses the bounded operator gratitude message when
non-blank; blank uses the exact localized gratitude sentence for the active
RU/EN UI language. All
screen-specific icons and ornaments are Compose Canvas/vector drawings;
decorative lines fade to transparency instead of ending abruptly.

T033 supersedes that historical T029 block composition with the
owner-authorized `layout_of_blocks_on_the_donation_screen.png`. The portrait
coordinates are not copied to 16:9. Instead, the selected image remains full
bleed behind one TV-safe rail anchored to the right edge. The rail occupies no
more than 30 percent of the full 16:9 viewport; the approximately 70-percent
left area contains no foreground block so the selected image remains visible.
Inside the rail, a compact top status block contains localized date/weekday, a
vertical divider, mosque-local `HH:mm`, a second divider and the current prayer;
an equal-height Settings gear-card sits beside it. One tall navy/champagne glass
card stacks the title, fading diamond line, scan instruction, shared framed QR
and five transfer rows. A separate bottom gratitude card contains mirrored
arch/lantern drawings. The old NamazTime top pill is absent. Date, time and
current prayer are projected from the same active Room schedule and
`PrayerTimeEngine` resolution as the prayer display. Donation mode performs no
independent calculation and fails closed to the unavailable screen when no
valid active schedule exists; it remains network-free and offline-capable with
the last-known-good Room snapshot. The engine chooses the latest resolved
obligatory-prayer adhan, or a later Friday Jumu'ah salah, as the current prayer;
Sunrise is explicitly excluded and presentation only localizes that result.

The QR uses dark-navy modules on a rounded warm-white surface, a thin
champagne frame and a NamazTime center badge while retaining a four-module
quiet zone, high error correction and decode regressions for the actual badge
ratio. Runtime values come only from the operator's local fields; sample
banking values from the reference are never defaults. Five packaged image
choices remain available in one D-pad LazyRow below a large selected-image
preview. The app-local custom photo is deliberately not a sixth thumbnail; a
separate `Choose custom image` action below the filmstrip uses the validated
system document picker. A
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
landscape WebP images. T033 replaces the old three-column gallery with the
owner-authorized `design_item_in_the_menu.png` hierarchy: one large 16:9 preview
of the selected background followed by one horizontally scrolling filmstrip of
the eight built-in choices. Left/right moves thumbnail focus, the selected or
focused thumbnail has a restrained champagne outline, and focus-driven
scrolling keeps all eight entries reachable. The custom image is not a ninth
thumbnail. `Choose image from TV` is an explicit action below the filmstrip and
continues to use the system document picker. Only allowlisted IDs are persisted.
The picker imports bounded JPEG/PNG/WebP into a validated app-local copy without
broad storage permissions. Missing/corrupt custom media and unknown persisted
IDs fall back to Golden dusk; all backgrounds use the same bounded dark scrim.
For the T024 main-display pass, the
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

T029 used the separately authorized `qr_page.png` for the historical standalone donation
screen under ADR 0014. At the controlled 1920×1080 profile the principal
anchors are approximately: brand `(838,33,242,70)`, Settings visual
`(1808,29,76,76)`, donation card `(1193,119,590,827)`, QR
`(1328,250,308,308)`, details rows `(1233,631,510,290)` and footer
`(194,970,1530,81)`. The same normalized 960×540 coordinate system scales
uniformly for 720p/1080p/4K. Focus keeps the gear size stable and substitutes a
thin gold outline/glow rather than a filled yellow surface.

T033 replaces those T029 anchors. At normalized 960×540 its standalone
donation anchors, as clarified by the product owner on 2026-08-30, are: status
`(656,28,206,50)`, Settings visual `(870,28,50,50)`, central card
`(656,88,264,348)`, shared QR `(715,156,146,146)`, transfer rows
`(671,306,234,120)` and gratitude block `(656,446,264,66)`. The combined
foreground rail is `x=656…920`, or 27.5 percent of the viewport, and begins
after 68.3 percent of unobstructed background. Geometry tests allow 4 dp, or
6 dp around QR/row internals, across the 720p, 1080p-density and 4K-density
profiles. The bounded ±2 dp retention shift is additive and remains within the
screen-safe margins.

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

Diagnostics shows the installed application version and integer Android code,
followed by build variant, the first 12 characters of the exact Git commit and
clean/dirty state. These are support/provenance labels only; they do not imply
that prayer data changed. Snapshot ID, parser, approval and signature-key fields
remain separately visible so application and schedule identities cannot be
confused. The release/pilot variants show their own identity, not a generic
display version.

The Mosque/location page provides two explicit local fields for the displayed
mosque name and displayed address. Blank means the canonical/pilot fallback.
The current canonical locality, authority/source and IANA timezone remain
read-only active-schedule context. A separate `Change city or schedule` action
opens the T041 review-request flow; it never edits the active city, timezone,
mosque ID or provenance locally.

The T041 flow is:

1. focus the canonical-city search field and use the system Android TV IME;
2. search the T035 catalog after a short debounce, cancelling a superseded
   request;
3. render every canonical candidate with federal subject, settlement type and
   IANA timezone, requiring an explicit choice even for duplicate names;
4. render the complete T040 eligible schedule-choice set for that city;
5. require an explicit choice even when only one schedule is available; and
6. submit only a non-authoritative `pending_review` proposal.

Zero choices show `No approved schedule is available for this city yet` and no
calculation or neighboring-region fallback. Two or more choices use a
scrollable D-pad list without top-N, preference or implicit first-item
selection; focus color communicates navigation only. Every row shows the
authority label, geographic scope, source identity, effective range, schedule
kind and policy/source identity so duplicate authority labels remain
distinguishable. List order never implies religious priority.

The current signed last-known-good schedule remains visible in the left
context panel and stays active after a failed or pending request. A successful
request names the chosen authority and says `Awaiting review`; it does not
approve, publish, sign, assign or alter Room. `Back` from choices returns to the
preserved city query. While the system IME is visible, the first `Back` closes
only the keyboard and keeps meaningful focus on the search field; the next
`Back` follows normal navigation.

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
5. activate;
6. later city/schedule changes use the device-scoped T041 discovery flow and
   remain pending until the existing operator approval/publication pipeline
   completes.

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
