# UI Freeze Rules

## Frozen surface

Until an explicitly approved UI phase, do not change login, dashboard, sidebar, theme, layout, routes, navigation, labels, icons, assets, spacing, typography, color, responsive behavior, accessibility behavior, or React interaction behavior.

## Enforcement

- Documentation and validation work must not touch `web-react/src/` or UI assets.
- Dependency, formatter, or generated-file updates that alter rendered output are UI changes.
- A claim of “cleanup only” does not bypass visual and behavioral review.
- Approved exceptions must name affected screens, provide before/after evidence, test relevant viewport and role states, and include rollback.

## Review checklist

Confirm no changed frontend source/assets, no route or navigation drift, no theme/login drift, no role-visibility drift, and no unexpected bundle/runtime configuration change.

## Future Refactor Recommendation

Visual regression baselines and an approved design-system inventory should be created in a future UI-focused phase.
