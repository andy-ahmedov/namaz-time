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

The Android TV app packages two original landscape WebP backgrounds in
`drawable-nodpi`: Golden dusk is the safe default and Blue hour is the second
built-in choice. Compose renders the selected asset full bleed with
`ContentScale.Crop` and a bounded flat dark scrim; foreground glass panels and
all controls stay inside the existing overscan-safe frame.

The selected built-in ID is a validated local operator preference. Unsupported
or corrupt IDs fail safely to Golden dusk. Appearance may cycle only through
the packaged allowlist. This setting does not alter, fetch or infer prayer
data, and it does not create a network path from composables.

The background images, prayer glyphs and NamazTime mark are original project
assets/treatments. The product-owner reference and current-state screenshot are
not application resources and are not committed by this task.

## Consequences

Positive:

- the display has a photographic mosque/landscape atmosphere while remaining
  deterministic and offline;
- operators can change the app-wide background from Appearance;
- display, Settings and recovery surfaces retain one Material 3 token system;
- invalid persisted style IDs have an explicit fallback and cannot select an
  arbitrary file or URL.

Costs and limits:

- the APK grows by the two compressed assets;
- crop and readability still need physical-TV/overscan acceptance;
- custom/remote backgrounds remain blocked on custody, type/size, approval,
  signing and delivery policy and are not implemented here;
- static images do not establish panel-retention safety.

## Rejected alternatives

- crop or imitate the supplied third-party screenshot/background;
- retain a Compose gradient after a real image background became a product
  requirement;
- allow arbitrary file paths or URLs from Appearance;
- connect the display composition to an asset download;
- silently replace a missing background with an unapproved remote source.
