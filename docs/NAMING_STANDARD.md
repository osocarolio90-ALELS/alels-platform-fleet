# Naming Standard

## Principles

Names communicate domain meaning, ownership, units, lifecycle, and scope. Prefer established ALELS terminology over synonyms. Renaming an existing contract is a compatibility change, not cleanup.

## Conventions

| Item | Convention | Example shape |
| --- | --- | --- |
| Java package | lowercase dot-separated | `com.alels.backend.feature` |
| Java/React type or component | `PascalCase` | `VehicleResponse` |
| Java/TypeScript method or variable | `camelCase` | `findVehicle` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_BATCH_SIZE` |
| SQL object or column | `snake_case` | `company_id` |
| Migration | `NNN_description.sql` | `016_add_example.sql` |
| Markdown standard | `UPPER_SNAKE_CASE.md` | `LOGGING_STANDARD.md` |
| React source file | existing lowercase kebab-case | `vehicle-page.tsx` |
| CSS class | existing project convention; descriptive and scoped | `vehicle-card` |

## Semantic suffixes

- `Request` is inbound transport data.
- `Response` is outbound transport data.
- `Dto` is a transport shape only when request/response direction is not meaningful.
- `Service`, `Repository`, and `Controller` identify their established layers.
- Include units in ambiguous numeric names, such as `timeoutMs` or `distanceKm`.
- Boolean names should read as predicates: `isActive`, `hasAccess`, `canRetry`.

Avoid unexplained abbreviations, misleading generic names, type-encoded prefixes, and names that expose implementation rather than domain intent.

## Compatibility

Do not rename existing API fields, database objects, routes, roles, claims, packages, classes, files, protocol fields, or telemetry keys merely to conform.

## Future Refactor Recommendation

Create a reviewed domain glossary before undertaking any terminology normalization.
