# Update an established brand

Apply only the requested changes to the actual brand source. Use supplied
decisions and existing guidelines; ask only about missing material choices.

For NamazTime TV, edit the relevant UI specification and Kotlin token source
through its normal screen/design-system workflow. Do not create JSON/CSS merely
to satisfy this optional web workflow.

## Existing web token pipeline only

Before using the sync helper, confirm docs/brand-guidelines.md and
assets/design-tokens.json are the intended sources and that writing the generated
assets/design-tokens.css is in scope. These files are not supplied by this skill.

From repository root, after editing the requested brand fields:

    node .agents/skills/brand/scripts/sync-brand-to-tokens.cjs
    node .agents/skills/brand/scripts/inject-brand-context.cjs --json

Inspect the diff and the actual consuming UI; successful script exit is not
proof of visual correctness. Preserve unrelated tokens. Report changed files
and validation, and keep public rebranding/publication approval separate.
