---
name: design-system
description: "Define or refine shared design tokens and component states; use existing platform tokens rather than creating a new theme by default."
license: MIT
metadata:
  author: claudekit
  version: "1.0.0"
---

# Design systems

Start from the product's current token source and requested scope. NamazTime TV
uses TvDesignSystem.kt and UI_UX_SPEC.md; CSS variables are not its runtime theme.
Preserve its semantic roles, offline assets and D-pad focus contract.

## Read by concern

- Token layering: references/token-architecture.md.
- Raw/semantic/component values: references/primitive-tokens.md,
  references/semantic-tokens.md, references/component-tokens.md.
- Components and states: references/component-specs.md and references/states-and-variants.md.
- Tailwind integration, only for web: references/tailwind-integration.md.
- New JSON token source, only when needed: templates/design-tokens-starter.json.

Use primitive → semantic → component layers where they clarify ownership.
Represent platform-appropriate focused/pressed/disabled states; hover is not a
TV interaction. Keep decorative values separate from meaningful contrast roles.
Validate composed colors and changed geometry, not just raw token values.

## Optional web tools

From repository root, using explicit task-owned input/output paths:

    node .agents/skills/design-system/scripts/generate-tokens.cjs --config <tokens.json>
    node .agents/skills/design-system/scripts/validate-tokens.cjs --dir <web-source>

The generator prints CSS unless an output file is requested. The validator scans
web source extensions, not Kotlin; a clean result is not TV-token verification.
Inspect generated output and run relevant application checks before adopting it.

## Presentations

Use slides for presentation creation. Token/catalog helpers here are optional
resources for that workflow, not a mandate to use Chart.js, center all content,
persuade an audience or introduce nonexistent brand files.
