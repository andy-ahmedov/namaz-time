# Social image composition

Use the requested platforms, content, brand and quantity; do not automatically
add extra formats, concepts, agents or approval checkpoints. Use available image
tools or HTML/CSS plus supported browser capture, whichever fits the deliverable.

## Platform Sizes

| Platform | Type | Size (px) | Aspect |
|----------|------|-----------|--------|
| Instagram | Post | 1080 x 1080 | 1:1 |
| Instagram | Story/Reel | 1080 x 1920 | 9:16 |
| Instagram | Carousel | 1080 x 1350 | 4:5 |
| Facebook | Post | 1200 x 630 | ~1.9:1 |
| Facebook | Story | 1080 x 1920 | 9:16 |
| Twitter/X | Post | 1200 x 675 | 16:9 |
| Twitter/X | Card | 800 x 418 | ~1.91:1 |
| LinkedIn | Post | 1200 x 627 | ~1.91:1 |
| LinkedIn | Article | 1200 x 644 | ~1.86:1 |
| Pinterest | Pin | 1000 x 1500 | 2:3 |
| YouTube | Thumbnail | 1280 x 720 | 16:9 |
| TikTok | Cover | 1080 x 1920 | 9:16 |
| Threads | Post | 1080 x 1080 | 1:1 |


These are starting dimensions, not current platform-policy guarantees. Verify
requirements when delivery depends on a specific platform.

## Create and export

- Reuse authorized artwork and brand sources; select additional skills only for
  a concrete missing capability, never randomly.
- Keep content inside placement-specific safe zones, with a clear reading order
  and text legible at the final display size.
- For HTML output, use local/embedded licensed fonts and images when offline or
  self-contained delivery is requested. A CDN stylesheet is not embedded.
- Use an available browser, wait for document.fonts.ready and image decoding,
  then capture the requested pixel dimensions. A 2x scale factor doubles exported
  pixels; do not enable it when exact-size 1x output is required.
- Inspect exports, correct clipping/fallback fonts/contrast and re-export.
  Do not disable the browser sandbox or install tools merely for screenshots.
- Report output paths/dimensions; keep source files unless cleanup was requested.
  No publishing, scheduling or asset relocation outside the requested scope.
