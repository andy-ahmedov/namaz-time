---
name: ui-styling
description: "Implement or review React/shadcn/Tailwind web styling and accessibility; not native Compose, Android TV or general graphic design."
license: MIT
metadata:
  author: claudekit
  version: "1.0.0"
---

# Web UI styling

Match the existing framework and Tailwind/shadcn versions. Do not initialize a
new stack or install packages for an audit or a styling-only change.

## Read only relevant guidance

- Components: references/shadcn-components.md.
- Theming: references/shadcn-theming.md.
- Keyboard/ARIA/accessibility: references/shadcn-accessibility.md.
- Utilities: references/tailwind-utilities.md.
- Responsive layout: references/tailwind-responsive.md.
- Tailwind configuration: references/tailwind-customization.md.
- Explicit canvas/poster work: references/canvas-design-system.md.

Use current official documentation when API/version behavior is uncertain:
https://ui.shadcn.com/llms.txt and https://tailwindcss.com/docs.

## Implementation and verification

Preserve semantic controls, keyboard focus, accessible names, validation feedback
and existing design tokens. Inspect changed UI at relevant widths and states;
complete in-scope fixes and affected tests before delivery. Review requests
produce findings, not edits.

## Optional helpers

From repository root:

    python3 .agents/skills/ui-styling/scripts/shadcn_add.py --help
    python3 .agents/skills/ui-styling/scripts/tailwind_config_gen.py --help

The first helper installs components; the second emits configuration. Inspect
their version compatibility and output targets before using them. Prefer the
existing project configuration; do not treat a generated config as permission
to replace it or install a new Tailwind version.
