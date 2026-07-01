# Java Backend Coding Standard

## Scope

This standard applies to future Java backend work. It preserves the current Spring, package, class, DTO, and API design.

## Java conventions

- Use the Java version declared by the module build.
- Use four-space indentation, braces for control blocks, one public top-level type per file, and explicit imports.
- Types use `PascalCase`; methods, variables, and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`.
- Prefer constructor injection and immutable dependencies.
- Keep methods cohesive and make side effects evident in names and boundaries.
- Use records for immutable transport/value shapes when consistent with the surrounding feature.
- Avoid wildcard imports, mutable public fields, hidden global state, catch-all swallowing, and `Optional` fields or parameters.
- Use `BigDecimal` for exact monetary values and `java.time` types for time. Make timezone and units explicit.

## Layer responsibilities

- Controller: HTTP mapping, boundary validation, authenticated context, and response selection.
- Service: use-case orchestration, business invariants, authorization coordination, and transaction boundary where established.
- Repository: parameterized persistence operations and row mapping.
- DTO: explicit request/response transport contract, not persistence or security behavior.

Dependencies flow controller → service → repository. Do not bypass tenant/company restrictions or expose internal entities through an API.

## Errors, logging, and tests

Use established centralized exception handling and SLF4J. Never return stack traces or log credentials, tokens, raw passwords, or sensitive personal data. New behavior requires focused tests at the lowest useful level plus boundary integration tests when applicable.

## Existing code

This standard is prospective. Do not reformat, rename, or refactor existing Java solely to conform.

## Future Refactor Recommendation

Assess automated architecture-boundary tests, nullness analysis, and shared test fixtures in a dedicated quality phase.
