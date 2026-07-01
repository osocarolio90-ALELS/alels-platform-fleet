# CSS and Theme Coding Standard

## Freeze boundary

The current UI, theme, login, layout, navigation, responsive behavior, and assets remain governed by `UI_FREEZE_RULES.md`. This document guides only separately approved future styling work.

## Rules

- Reuse established theme tokens, variables, spacing, typography, radii, shadows, and responsive breakpoints.
- Do not hardcode a color or dimension when an appropriate established token exists.
- Keep selectors local and low-specificity; avoid `!important` except for a documented compatibility constraint.
- Do not style by unstable DOM depth or generated class names.
- Keep component styles with the established stylesheet organization.
- Support all existing themes and interaction states: hover, focus-visible, active, disabled, loading, error, and selected.
- Preserve readable contrast, visible focus, zoom behavior, text reflow, and reduced-motion preferences.
- Avoid broad global selectors that can alter unrelated screens.

## Change evidence

Approved CSS/theme work requires affected-screen inventory, before/after evidence, supported viewport checks, theme checks, accessibility review, and regression review for shared selectors.

## Existing styles

Do not normalize, reorder, format, or deduplicate existing CSS outside an approved UI change.

## Future Refactor Recommendation

Inventory theme tokens and selector ownership before any design-system consolidation.
