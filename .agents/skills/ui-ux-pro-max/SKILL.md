---
name: ui-ux-pro-max
description: "Search local design/UX catalogs for a specific style, accessibility or stack question; not every UI edit or an automatic redesign."
---

# Focused design research

Use this skill when a concrete design/UX decision benefits from the bundled
catalogs. Existing product requirements and authorized references take priority
over dataset recommendations. It is not an automatic checklist for all UI work.

## Query choice

Python 3 and the standard library are sufficient; searches are local/offline.
Commands below run from repository root.

- Specific UX concern:

      python3 .agents/skills/ui-ux-pro-max/scripts/search.py "keyboard focus modal" --domain ux

- Known implementation stack:

      python3 .agents/skills/ui-ux-pro-max/scripts/search.py "state remember" --stack jetpack-compose

- New product-wide visual direction, only when requested:

      python3 .agents/skills/ui-ux-pro-max/scripts/search.py "analytics dashboard dark" --design-system

Use one dominant intent per query. Check result identity, category and platform.
Retry an empty/off-topic result once with a narrower query; if still unsuitable,
report no verified match and use clearly identified general guidance. Do not
persist or treat irrelevant results as requirements.

For modes and available domains/stacks, use:

    python3 .agents/skills/ui-ux-pro-max/scripts/search.py --help

## Platform boundary

NamazTime is Android TV: use android-tv-screen and the existing TV design system.
Do not translate mobile-first widths, touch gestures, phone/tablet device matrices,
React icon imports or dynamic color into TV requirements. For web/mobile, use
only guidance applicable to the actual stack and interaction under review.

Preserve essential text, visible focus, semantic controls, appropriate contrast
and reduced-motion behavior. Evaluate only states/features touched by the task,
not every catalog category.

## Persistence and tools

Search is read-only unless --persist is supplied. Persist only a requested,
reviewed design-system artifact with an explicit --output-dir; existing files
are preserved by default. --force requires explicit authorization to overwrite.
An audit does not authorize persistence or application changes.

If Python is unavailable, do not install it automatically. Continue with
applicable project guidance or explain that local catalog search is unavailable.
Do not expose private project data in queries/output or fetch external catalogs
as a fallback.

## Package verification

Run make test-skills from the repository root. The runtime/search tests are local.
Two retained upstream maintenance test modules require catalog-refresh/relevance
tools that are not distributed with this runtime package. They report explicit
skips when those tools are absent; this does not verify catalog refreshes or an
upstream relevance benchmark. Do not claim those capabilities tested.
