# Frontend Structure Standard

## Scope

This standard governs `web-react/` while preserving the existing UI and behavior.

## Boundaries

- `src/app/` owns application composition and routing.
- `src/features/` owns feature-specific pages, components, and API adapters.
- `src/components/` owns reusable UI components.
- `src/layouts/` owns layout composition.
- `src/stores/` owns shared client state.
- `src/lib/` owns cross-cutting utilities and API infrastructure.
- `src/styles/` and established style entry points own styling.

Feature code must not bypass shared authentication, API, routing, or role-access mechanisms. Reusable components should remain domain-neutral.

## Compatibility

Existing routes, navigation, login flow, dashboard, sidebar, theme, responsive behavior, labels, and permission visibility are frozen unless separately approved. API clients must remain compatible with existing backend contracts. The lockfile must accompany intentional dependency changes.

## Quality

Changes require type checking/build validation through scripts already declared in `package.json`, plus focused tests when available. Accessibility and responsive behavior are acceptance criteria for approved UI work.

## Future Refactor Recommendation

Component consolidation, styling normalization, and broader frontend test coverage should be planned separately and must not alter frozen UI behavior incidentally.
