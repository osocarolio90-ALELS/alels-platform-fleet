# Error Handling Standard

## Principles

Errors must be safe, actionable, consistent, observable, and compatible. Never convert a failure into apparent success or silently discard data.

## Classification

- Validation: malformed or semantically invalid input.
- Authentication: identity is absent or invalid.
- Authorization: authenticated identity lacks access.
- Not found: the scoped resource does not exist.
- Conflict: current state prevents the operation.
- Dependency: database, broker, filesystem, device, or upstream failure.
- Internal: unexpected implementation failure.

Use established HTTP statuses and error codes without changing existing contracts.

## Backend handling

- Validate at boundaries and preserve business context when propagating exceptions.
- Use the established centralized exception handler and `StandardErrorResponse`.
- Return stable, non-sensitive client messages; never return stack traces, SQL, secrets, or internal paths.
- Catch exceptions only to recover, translate at a boundary, add safe context, or release resources.
- Preserve interrupt status and causal exceptions where applicable.

## Frontend handling

Represent loading, empty, recoverable, validation, authorization, and terminal failure states explicitly. User messages should explain the next safe action without exposing internals.

## Data and telemetry

Do not acknowledge, retry, deduplicate, or quarantine failures in ways that alter established delivery semantics. Partial success must be explicit and traceable.

## Future Refactor Recommendation

Define a versioned error-code catalog and correlation-ID contract after current API behavior is audited.
