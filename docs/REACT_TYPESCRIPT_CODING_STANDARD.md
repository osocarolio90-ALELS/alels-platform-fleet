# React and TypeScript Coding Standard

## Scope

This standard applies to future work in `web-react/` and does not authorize UI, routing, theme, login, or behavior changes.

## TypeScript

- Keep strict, explicit types at API, component, store, and utility boundaries.
- Avoid `any`; use `unknown` and narrow it safely when input is not trusted.
- Prefer immutable data and `const`; use unions for finite states.
- Separate wire DTOs from derived view state when their meanings differ.
- Do not use non-null assertions to bypass missing-state handling without evidence.

## React

- Use function components and hooks according to existing project conventions.
- Keep feature code under its established feature; reusable domain-neutral UI remains in shared components.
- Components render UI and coordinate interaction; API access belongs in established API adapters and shared infrastructure.
- Effects synchronize with external systems. Include correct dependencies and cleanup.
- Do not duplicate authentication, role-access, routing, query, or global-state logic.
- Represent loading, empty, error, success, and disabled states explicitly.
- Preserve keyboard access, labels, focus behavior, semantic markup, and responsive behavior.

## APIs and errors

Do not alter request/response contracts in frontend adapters. Treat server messages as untrusted, avoid exposing sensitive details, and use the established user-facing error pattern.

## Validation

Run scripts declared in `package.json`, including type/build checks applicable to the change. Dependency changes require the lockfile.

## Existing code

Do not mass-format or refactor existing components merely for conformance.

## Future Refactor Recommendation

Add component tests, accessibility automation, and visual regression baselines in a separately approved frontend-quality phase.
