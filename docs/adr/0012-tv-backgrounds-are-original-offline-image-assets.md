# ADR 0012: TV backgrounds are original offline image assets

- Status: Accepted
- Date: 2026-08-20
- Supersedes: the code-drawn background portion of ADR 0010
- Reference-use restriction amended by: ADR 0014

## Context

ADR 0010 established one offline Compose design system and deliberately used a
code-drawn background while the visual direction was still provisional. The
product owner has now required a real image background and supplied
`design.png` only as a composition and atmosphere reference. That reference
contains third-party branding and imagery and cannot be cropped, traced or
packaged under the clean-room rules.

The display must still cold-start without network access, Settings must share
the same visual language, and future custom media must not bypass the signed
asset/publication policy that remains unresolved.

## Decision

The Android TV app packages eight original/project-derived landscape WebP
backgrounds in `drawable-nodpi`: Golden dusk is the safe default. Compose
renders the selected asset full bleed with
`ContentScale.Crop` and a bounded flat dark scrim; foreground glass panels and
all controls stay inside the existing overscan-safe frame.

The selected background ID is a validated local operator preference. As amended
by T033, Appearance shows one large selected preview and one horizontally
scrolling D-pad filmstrip containing only the eight built-in assets. Custom is
not a ninth carousel item; it is a separate explicit action below the filmstrip.
As amended by ADR 0018, custom import first uses a resolvable Android document
picker, then the system Photo Picker, and only then an explicit permission-gated
MediaStore fallback for TV images without either picker. System-picker paths
request no storage/media permission. The fallback requests only image-read
access appropriate to the Android version after the operator presses the
action; it never requests write or all-files access. Every path accepts only
bounded JPEG/PNG/WebP input, validates byte size, decoded type,
dimensions and pixel count, then atomically writes a normalized app-local copy.
The URI itself is not retained. Unsupported/corrupt IDs or a missing/corrupt
custom copy fail safely to Golden dusk. This setting does not alter, fetch or infer prayer
data, and it does not create a network path from composables.

The background images, prayer glyphs and NamazTime mark are original project
assets/treatments. The product-owner reference and current-state screenshot are
not application resources and are not committed by this task.

## Consequences

Positive:

- the display has a photographic mosque/landscape atmosphere while remaining
  deterministic and offline;
- operators can change the app-wide background from an eight-item Appearance
  filmstrip or import one device-local custom image through a separate action;
- display, Settings and recovery surfaces retain one Material 3 token system;
- invalid persisted style IDs have an explicit fallback and cannot select an
  arbitrary file or URL.

Costs and limits:

- the APK grows by the eight compressed assets;
- crop and readability still need physical-TV/overscan acceptance;
- remote/fleet-distributed backgrounds remain blocked on custody, approval,
  signing and delivery policy; the device-local picker does not bypass that path;
- static images do not establish panel-retention safety.

## Rejected alternatives

- crop or imitate the supplied third-party screenshot/background;
- retain a Compose gradient after a real image background became a product
  requirement;
- allow arbitrary file paths or URLs from Appearance;
- connect the display composition to an asset download;
- silently replace a missing background with an unapproved remote source.
