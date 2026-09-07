# Create or revise an HTML deck

Establish audience, purpose, supplied content, delivery format and any brand
constraints. Ask only about choices that materially affect the requested result.
Ordinary language is enough to select this workflow; do not reinvoke this skill.

## Build

- Choose a narrative appropriate to the material: technical/informational decks
  need not use sales formulas. Read layout-patterns.md for composition choices
  and slide-strategies.md when narrative structure needs work.
- Adapt html-template.md only if a new HTML scaffold is useful. Its sample
  centering, Chart.js, colors and remote resources are examples, not requirements.
- Reuse existing tokens when present; otherwise define only the styles needed
  for this deck. Do not import nonexistent design-tokens.css or fabricate metrics.
- Use charts only when they clarify data; label sources/units and provide a
  readable alternative. Do not install Chart.js unless it is actually needed.
- Provide operable keyboard navigation and labeled controls. Navigation handlers
  must not consume input intended for links, forms or other interactive content.
- For offline delivery, package permitted assets locally and remove CDN
  dependencies. Wait for fonts/images to finish loading before capture.

## Verify and hand off

Inspect each slide at the target size, check clipping, contrast, chart labels,
navigation, reduced motion and the required offline behavior. Fix defects within
scope and recheck. Report output paths and unresolved delivery constraints;
additional concepts and review pauses are optional, not completion prerequisites.
