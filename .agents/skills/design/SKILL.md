---
name: design
description: "Route multi-deliverable brand/visual-design work or create logos, identity mockups and icons; not routine UI code changes."
license: MIT
metadata:
  author: claudekit
  version: "2.1.0"
---

# Visual design routing

Preserve the requested medium, existing brand and authorized references.
Choose only the relevant branch; do not load every design skill or rebuild an
existing design system for a small edit. NamazTime TV layout work belongs to
android-tv-screen; web/mobile recipes do not override its specification.

## Routes

- Brand voice or identity standards: brand.
- Shared tokens/component specifications: design-system.
- React/shadcn/Tailwind implementation: ui-styling.
- Product style research using local catalogs: ui-ux-pro-max.
- Presentations: slides; banners: banner-design.
- Logo: references/logo-design.md.
- Corporate identity mockups: references/cip-design.md.
- Icons: references/icon-design.md.
- Social images: references/social-photos-design.md.

For logo/CIP choices, local search needs only Python 3. From repository root:

    python3 .agents/skills/design/scripts/logo/search.py "minimal technology" --design-brief -p "Example"
    python3 .agents/skills/design/scripts/cip/search.py "business card" --cip-brief -b "Example"

References contain style/prompt guidance and optional provider-specific scripts.
Check catalog results against the requested subject and deliverable; a successful
search command does not prove its recommendations are relevant or approved.
Use available image-generation tools for requested raster work. Gemini helpers
are optional: they require a configured provider/dependencies and may incur cost.
Do not install providers, expose keys, or upload private references as a fallback.
If a provider is unavailable, continue with supported tooling or explain the
specific blocked deliverable; repairing unrelated tooling is not automatic scope.

## Delivery

Use dimensions, content, quantity and format supplied by the user. Ask only about
missing choices that materially affect the result. HTML galleries, multiple
concepts and additional deliverables are optional, not automatic review gates.
Inspect/export requested output, correct defects in scope and report file paths.
Preserve licensing/attribution; brand permission does not authorize copying code
or extracted third-party assets.
