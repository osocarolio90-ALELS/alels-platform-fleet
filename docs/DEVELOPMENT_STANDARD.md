# Development Standard

## Purpose

This standard defines how ALELS changes move from requirement to review without weakening existing behavior, contracts, security, or data integrity.

## Development workflow

1. State the objective, scope, exclusions, owner, and acceptance criteria.
2. Audit affected code, contracts, tests, configuration, and documentation before editing.
3. Classify impact: UI, runtime, API, database, migration, gateway, telemetry, security, RBAC, tenant, operations, and performance.
4. Implement the smallest coherent change in the existing module and structure.
5. Add validation proportional to risk and run only repository-supported commands.
6. Review the final diff for unrelated changes, secrets, generated output, and compatibility.
7. Record results, residual risks, rollout, monitoring, and rollback.

## Change discipline

- Preserve existing observable behavior unless the approved requirement explicitly changes it.
- Do not combine features, refactors, dependency upgrades, formatting, or cleanup.
- Do not rename or move existing packages, classes, files, routes, fields, database objects, or configuration keys without an approved compatibility plan.
- Follow established module boundaries and the specialized standards in `docs/`.
- Keep credentials, production data, runtime uploads, logs, caches, and generated artifacts out of commits.
- If required work is outside scope, stop and record a Future Refactor Recommendation.

## Definition of ready

Work is ready to begin when its required behavior, frozen boundaries, dependencies, compatibility expectations, validation approach, and acceptance owner are clear.

## Definition of done

Use `DEFINITION_OF_DONE.md` and `CODE_REVIEW_CHECKLIST.md`. Passing compilation alone is not sufficient evidence.

## Future Refactor Recommendation

Introduce issue and pull-request templates only in a separately approved repository-tooling phase.
