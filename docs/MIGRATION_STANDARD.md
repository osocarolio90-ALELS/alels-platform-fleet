# Migration Standard

## Naming and ordering

Migration files live in `database/migrations/` and use `NNN_description.sql`, with a unique three-digit positive sequence. Numbers must be contiguous in the established history unless an exception documents why a reserved number is absent.

## Immutability

Never edit, rename, reorder, or replace a migration that may have been shared or applied. Correct it with a new migration. Existing migration SQL is historical evidence.

## Design

- Make intent and affected objects explicit.
- Prefer backward-compatible, staged changes.
- Account for existing data, nullability, defaults, indexes, locks, and transaction behavior.
- Do not mix unrelated schema or data changes.
- Make rerun/idempotency behavior explicit where the existing runner requires it.
- Include verification and rollback or forward-recovery instructions in the change record.

## Review

Reviewers verify unique numbering, dependency order, application compatibility, tenant integrity, operational duration, backup/recovery, and non-production execution evidence.

Recovery scripts are not migrations and must remain under `database/recovery/`.

## Future Refactor Recommendation

Automated migration application tracking and disposable-database verification should be evaluated in a later database tooling phase.
