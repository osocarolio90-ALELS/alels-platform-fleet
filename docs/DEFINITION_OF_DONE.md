# Definition of Done

A change is done only when every applicable item is evidenced or explicitly marked not applicable with rationale.

## Scope and review

- Requirement and exclusions are clear; unrelated work is absent.
- Correct owners reviewed security, API, database, UI, gateway, telemetry, or operations impact.
- Files and behavior changed are documented.

## Compatibility and integrity

- Runtime, UI, API, database, migration, RBAC, tenant, gateway, and telemetry impacts are assessed.
- Frozen surfaces remain unchanged unless an approved exception exists.
- Secrets, production data, generated output, logs, and local configuration are absent.

## Validation

- Relevant tests and existing build commands pass.
- Repository validation, `git diff --check`, diff/name review, and status review pass.
- Failures, warnings, skipped checks, and residual risks are recorded.

## Delivery

- Documentation matches behavior.
- Deployment, monitoring, rollback/recovery, and data compatibility are ready where applicable.
- The change is reviewable, reversible where feasible, and traceable to an owner and requirement.

## Documentation-only Phase 1

Phase 1 additionally requires only documentation and the read-only validator in its authored diff, zero application/runtime configuration changes, all required standards present, and validator exit code zero.

## Future Refactor Recommendation

Automate applicable Definition of Done evidence in CI only after checks and ownership are agreed.
