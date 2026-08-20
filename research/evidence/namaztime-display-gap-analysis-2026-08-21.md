# NamazTime main-display gap analysis — 2026-08-21

`design.png` is `CONFIRMED_PUBLIC` as a product-owner-supplied visual
specification. The T023 and T024 NamazTime screenshots below are
`CONFIRMED_RUNTIME` only for the controlled Android TV Emulator API 36 at
1920×1080/320 dpi. This comparison does not establish physical-TV overscan or
readability.

## Inputs

- target: `design.png`, 1672×941, SHA-256
  `afe3803c2fbedc6755bd49093a6854393c246bef547c7cad185b6f2502282ff6`;
- baseline: `artifacts/t023-visual-loop/namaztime-t023-final.png`, 1920×1080,
  SHA-256
  `bf21629145d61c8088f01a6727425f55ed90798da4cf468c7a69a07ab59353c0`;
- comparison method: both screens were normalized to 960×540 and inspected
  side by side. No target pixels, imagery, logo, icon or ornament were copied
  into the application.

## Measured gaps

| Area | `design.png` target | T023 baseline | T024 result |
|---|---:|---:|---:|
| centered foreground width | about 71% | about 90% | about 71% |
| body left edge at 960 px | 138 px | 50 px | 138 px |
| body top at 540 px | 120 px | 113 px | 122 px |
| body bottom at 540 px | 441 px | 442 px | 446 px |
| left/right column relationship | approximately equal | right visibly wider | equal within 4% |
| next/clock card heights at 540 px | about 185/128 px | about 214/106 px | about 187/128 px |
| bottom strip at 540 px | y=453, about 58 px high | y=453, about 61 px high | y=454, about 57 px high |
| Settings entry | absent/subordinate | dominant text pill | subordinate circular 48 dp target |

The baseline also had a heavy three-column table header, thick glyph strokes,
a hard rectangular active-row fill, redundant next-event labels, no decorated
location treatment, a date card without the reference-like calendar/divider
rhythm, and a technical approval/source block dominating the bottom strip.

## T024 clean-room response

- constrained the normal display composition to 79% of the existing safe
  frame, which produces about 71% of the full 16:9 viewport;
- made the two primary columns equal and set the left-card height split to the
  measured reference proportions;
- reduced the next card to label, event name, original diamond divider and
  countdown;
- added original line/diamond location ornaments and an original calendar
  glyph;
- replaced hard row treatment with soft horizontal tonal emphasis, thin
  outline and a short side marker, while preserving centered Sunrise geometry;
- thinned the original prayer/iqamah glyph strokes and softened row dividers,
  surfaces, borders and typography;
- converted the bottom strip to two calm event areas and hid normal approved
  provenance from public display; exceptional/unverified status remains
  visible;
- retained a 48 dp D-pad Settings target but reduced its visual weight to a
  dark circular control with a visible focus perimeter;
- kept Room/T006 prayer resolution, T022 pilot data, localization, background
  selection and offline behavior unchanged.

The remaining differences are intentional or data-driven: the mosque name and
address are longer, NamazTime shows separate adhan/iqamah values, Friday may
show its real Jumu'ah session, and all imagery/glyphs/ornaments remain original
rather than copied from the supplied target.
