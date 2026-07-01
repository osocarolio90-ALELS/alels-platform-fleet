# SQL Coding Standard

## General conventions

- Use PostgreSQL syntax compatible with the project’s declared environment.
- Write SQL keywords consistently in uppercase and use `snake_case` for identifiers.
- Name columns explicitly; avoid `SELECT *` in application queries and durable interfaces.
- Qualify ambiguous columns and use meaningful aliases.
- Parameterize application input. Never concatenate untrusted values into SQL.
- State ordering explicitly when callers depend on it.
- Treat `NULL` deliberately; do not confuse it with empty string or zero.

## Data integrity and performance

- Preserve tenant/company predicates in reads and writes.
- Use transactions for operations that must succeed atomically.
- Define constraints and indexes from data invariants and measured access patterns.
- Review join cardinality, duplicate multiplication, locking, execution time, and affected row counts.
- Monetary arithmetic uses exact numeric types; time and timezone semantics must be explicit.
- Destructive operations require explicit scope, verification, backup/recovery, and approval.

## Migrations

Follow `MIGRATION_STANDARD.md`. Existing migrations are immutable. New migrations use unique contiguous numbering and must remain compatible during deployment.

## Formatting

Prefer one selected expression per line for longer queries, aligned logical clauses, and comments for non-obvious business or operational constraints.

## Existing SQL

Do not rewrite existing schema, migration, or query text solely for style.

## Future Refactor Recommendation

Adopt automated SQL linting and disposable-database migration tests after dialect and legacy rules are baselined.
