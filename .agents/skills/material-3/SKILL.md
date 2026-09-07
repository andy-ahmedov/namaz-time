---
name: material-3
description: "Implement or audit Material 3 themes/components when explicitly relevant; distinguish TV Material3 from mobile, Flutter and web."
---

# Material 3 by platform

Use the project's pinned libraries and design specification. Material compliance
guidance is not authorization for a redesign, dependency migration or new theme.

## Platform boundary

- NamazTime Android TV: retain androidx.tv.material3 and TvDesignSystem.kt.
  D-pad focus, 16:9 safe bounds, viewing distance and offline rendering take
  precedence over phone navigation, touch targets and wallpaper-driven color.
  Use android-tv-screen for screen behavior and TV verification.
- Mobile Compose: androidx.compose.material3, with APIs supported by the pinned BOM.
- Flutter: the project's ThemeData/ColorScheme.
- Web: existing components and CSS tokens; verify current library support before
  assuming a Material specification feature has an implementation.

Dynamic color, light mode, expressive motion and adaptive navigation apply only
when the product requires them. Do not add phone/tablet acceptance gates to TV.

## Read for the affected concern

- Color roles/contrast: references/color-system.md.
- Typography/shape: references/typography-and-shape.md.
- Components: references/component-catalog.md.
- Navigation: references/navigation-patterns.md.
- Layout/insets: references/layout-and-responsive.md.
- Theme configuration: references/theming-and-dynamic-color.md.

These references include mobile/web examples. Apply the platform boundary above
before translating them into code. Verify version-sensitive APIs against official
Android/Material/Flutter documentation; match the repository rather than upgrading.

## Review and delivery

For an audit, report actual file/line or runtime evidence, impact and suggested
correction. Treat an inapplicable feature as not applicable, not a failed score.
Do not grade intentional TV design choices against phone or web defaults.

For implementation, validate affected theme pairings in the composed result,
semantics, focus, disabled/pressed states and layout on the target platform.
Keep missing hardware evidence explicit. Finish the requested verification loop;
do not stop after the first code draft or invent additional acceptance criteria.
