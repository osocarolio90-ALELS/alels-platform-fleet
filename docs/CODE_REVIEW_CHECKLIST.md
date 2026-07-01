# Code Review Checklist

Mark each applicable item and explain every exception.

## Scope

- [ ] Requirement, acceptance criteria, and exclusions are clear.
- [ ] Diff contains no unrelated refactor, formatting, generated output, secrets, logs, uploads, or local configuration.
- [ ] Existing package, class, file, folder, and contract locations are preserved unless explicitly approved.

## Design and code

- [ ] Change follows module and controller/service/repository boundaries.
- [ ] Names express domain meaning and units.
- [ ] Input, null, empty, boundary, concurrency, and partial-failure cases are handled.
- [ ] Dependencies and abstractions are justified; no dead or duplicate logic was introduced.

## Compatibility and security

- [ ] UI, API, database, migration, gateway, ingestion, telemetry, auth, RBAC, tenant, and runtime impacts are stated.
- [ ] Authentication and authorization remain server-enforced.
- [ ] Tenant/company scope cannot be bypassed.
- [ ] Responses and logs expose no secrets or sensitive internals.
- [ ] Error, retry, acknowledgement, ordering, and duplication semantics remain compatible.

## Data and operations

- [ ] SQL is parameterized and preserves integrity.
- [ ] Migration immutability, numbering, locking, compatibility, and recovery are addressed.
- [ ] Logging is appropriately leveled, redacted, and bounded in volume.
- [ ] Rollout, monitoring, rollback, and recovery are proportionate to risk.

## Validation

- [ ] Focused tests cover allowed, denied, error, and boundary cases.
- [ ] Existing relevant build/static checks pass.
- [ ] Repository validator and `git diff --check` pass, or failures are explicitly owned.
- [ ] Final file list and diff were manually reviewed.
- [ ] Documentation and Definition of Done are complete.

## Future Refactor Recommendation

Automate only stable checklist items after repository-wide tooling and ownership are approved.
