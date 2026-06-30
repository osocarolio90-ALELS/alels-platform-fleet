# API Compatibility Rules

## Contract

An API contract includes method, path, authentication, authorization, headers, query/path parameters, request and response fields, types, nullability, defaults, status codes, errors, pagination, ordering, and side effects.

## Rules

- Preserve existing contracts by default.
- Additive fields must not break strict consumers and must have stable semantics.
- Do not remove, rename, retype, repurpose, or newly require an existing field without versioning and migration.
- Do not silently change status/error behavior, defaults, ordering, pagination, identifiers, time format, or authorization.
- Accept old and new forms during an approved migration window.
- Server validation must distinguish malformed, unauthenticated, unauthorized, missing, and conflicting requests consistently with established behavior.

## Change evidence

Approved changes require affected consumers, before/after examples, compatibility classification, contract tests, rollout order, deprecation period, observability, and rollback.

## Future Refactor Recommendation

Generate and validate a versioned machine-readable API specification after the current endpoints are audited against actual behavior.
