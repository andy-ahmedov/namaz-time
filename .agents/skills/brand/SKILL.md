---
name: brand
description: "Define or review brand voice, visual identity and asset guidelines; not routine component styling."
metadata:
  author: claudekit
  version: "1.0.0"
---

# Brand

Identify the project's actual brand source before changing it. For NamazTime TV,
UI_UX_SPEC.md and TvDesignSystem.kt govern the existing visual system; do not
create a parallel web token pipeline just because this skill includes one.

## Guidance by task

- Voice: references/voice-framework.md; messaging: references/messaging-framework.md.
- Identity: references/visual-identity.md; logo use: references/logo-usage-rules.md.
- Audit: references/consistency-checklist.md and references/approval-checklist.md.
- Assets: references/asset-organization.md.
- Palette/type: references/color-palette-management.md and references/typography-specifications.md.
- New guidelines: templates/brand-guidelines-starter.md.
- Explicit brand update using the JSON/CSS pipeline: references/update.md.

Ask only for brand decisions the brief and existing sources do not resolve.
An audit reports discrepancies; it does not authorize a rebrand or token rewrite.

## Optional web helpers

Run from repository root with Node.js:

    node .agents/skills/brand/scripts/inject-brand-context.cjs --json
    node .agents/skills/brand/scripts/validate-asset.cjs <asset-path>

The context helper defaults to docs/brand-guidelines.md. The sync helper uses
assets/design-tokens.json and writes JSON/CSS. Check those inputs exist and belong
to the requested workflow before running it. If absent, use existing project
sources or propose setup; do not invent brand approval or overwrite a TV theme.
Report changed artifacts, validation and any approval still needed.
