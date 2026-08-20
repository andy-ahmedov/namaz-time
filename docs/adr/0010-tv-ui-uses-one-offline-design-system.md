# ADR 0010: TV UI uses one offline design system

- Status: Accepted
- Date: 2026-08-20

## Context

The first vertical slice proved the Room-backed prayer display and D-pad shell,
but screen-specific colors and geometry would make later settings, diagnostics
and recovery states visually inconsistent. The product owner supplied
`design.png` as a hierarchy and mood reference for Phase 4. The supplied file
also visibly contains third-party branding, imagery and decorative treatment,
which cannot become a project asset or a pixel-perfect implementation under the
repository's clean-room rules.

TV readability also has different constraints from a handheld UI: 16:9
overscan, viewing distance, stable changing digits, obvious focus and calm
failure states matter more than dense controls or decorative detail.

## Decision

The Android TV application has one Compose-owned design system for the main
display, settings, QR panels and safe recovery states. It defines semantic
dark-surface, text, accent, warning, separator and focus colors plus reusable
glass panels, radii and a static built-in atmospheric background. The
background is drawn by project code and remains available offline; no reference
image, competitor brand, icon, ornament or extracted resource is packaged.

All public-display content stays a projection of immutable local state. Room
supplies mosque, snapshot and daily values; the T006 engine supplies the
mosque-local date/time, event kind/time, resolved iqamah/Jumu'ah and countdown.
The UI does not reconstruct an iqamah countdown from wall time: it displays
that countdown only when T006 identifies iqamah as the next event. Missing
iqamah remains visibly unset.

The content frame uses approximately five-percent horizontal and at least
four-percent vertical insets across the local 720p, 1080p-density and
4K-density profiles. Full-bleed background is outside that frame. Changing
times use a fixed-width monospaced treatment, next-event rows use shape/border
plus color, and the only public-display control has an explicit initial D-pad
focus and visible focus perimeter.

## Consequences

Positive:

- future screens reuse semantic roles instead of inventing new visual styles;
- the display hierarchy resembles the supplied product direction without
  copying protected branding, imagery or geometry;
- missing/diagnostic data remains explicit and cannot be hidden by polish;
- palette contrast and multi-profile safe bounds have automated regression
  coverage.

Costs and limits:

- remote/custom backgrounds remain blocked on their separate signed asset
  pipeline and are not implemented here;
- Robolectric validates semantics, contrast tokens and geometry but local
  window screenshot capture is unavailable in the current environment;
- real overscan, viewing-distance readability, panel uniformity and QR scan
  distance still require the deferred physical-TV acceptance matrix;
- portrait and animated/video backgrounds remain out of scope.

## Rejected alternatives

- commit or crop `design.png` as an application asset;
- duplicate raw colors, radii and surfaces separately in every screen;
- calculate or infer missing iqamah values in presentation code;
- place network-backed media or API calls in the display composition;
- claim physical-TV visual acceptance from Robolectric bounds.
