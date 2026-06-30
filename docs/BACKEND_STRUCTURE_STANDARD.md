# Backend Structure Standard

## Scope

This standard governs `backend/` without changing its current package or runtime design.

## Boundaries

- Controllers own transport concerns and delegate business work.
- Services own use-case orchestration and business rules.
- Repositories own persistence access and must not expose transport concerns.
- DTOs define transport shapes; persistence and security objects must not leak unintentionally.
- Shared security, audit, configuration, and exception components remain under their established shared packages.
- Dependencies flow from controller to service to repository. Cross-feature access should use an explicit service boundary.

## Compatibility and security

- Existing endpoints, status codes, payload fields, authentication, authorization, tenant filtering, and role behavior are frozen unless separately approved.
- Validate input at the boundary and enforce authorization server-side.
- Do not log credentials, tokens, personal data, or security-sensitive payloads.
- Runtime properties must use existing configuration mechanisms; secrets stay outside version control.

## Quality

New behavior requires focused unit or integration tests appropriate to its risk. Error handling must preserve the established response contract. Package and class renames require an approved migration plan.

## Future Refactor Recommendation

Potential package normalization, shared-module extraction, and dependency-boundary enforcement should be assessed in a dedicated refactor phase.
