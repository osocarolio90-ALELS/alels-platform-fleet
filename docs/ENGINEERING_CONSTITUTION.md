# ALELS Engineering Constitution

## Purpose

This constitution is the highest repository-level engineering policy. It protects production behavior while making future changes reviewable, testable, reversible, and compatible.

## Non-negotiable principles

1. Preserve observable behavior unless an approved requirement explicitly changes it.
2. Keep UI, API, database, RBAC, tenant, gateway, and telemetry contracts backward compatible.
3. Never commit secrets, generated output, runtime uploads, logs, or local configuration.
4. Make the smallest coherent change and keep unrelated cleanup separate.
5. Validate before merge and record evidence in the change description.
6. Treat migrations as immutable after they are shared or applied.
7. Prefer explicit ownership and module boundaries over cross-module coupling.
8. A reviewer must be able to identify impact, rollback, and operational risk.

## Authority and exceptions

The specialized standards in this directory implement this constitution. When policies conflict, the stricter compatibility or integrity rule wins. Exceptions require written scope, owner, risk, expiry or follow-up, rollback, and approval from the affected technical owner.

## Change classification

- Documentation-only: no executable or runtime configuration change.
- Internal: implementation changes with no external contract change.
- Contract: API, schema, event, protocol, security, or user-visible behavior change.
- Operational: deployment, configuration, observability, or recovery change.

Contract and operational changes require explicit compatibility and rollback review.

## Development standards

Day-to-day development is governed by `DEVELOPMENT_STANDARD.md`, `CODING_STANDARD.md`, the language-specific standards, and `CODE_REVIEW_CHECKLIST.md`. These standards refine this constitution; they do not authorize behavioral changes or mandatory refactoring of existing code.

## Future Refactor Recommendation

Architecture cleanup discovered during unrelated work must be recorded for a separately approved phase; it must not be smuggled into foundation or maintenance changes.
