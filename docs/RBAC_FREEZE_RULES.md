# RBAC Freeze Rules

## Frozen contract

Roles, permissions, normalization, inheritance, authorization checks, route guards, visibility rules, tenant/company scoping, session claims, and JWT semantics are frozen until separately approved.

## Enforcement

- Do not add, remove, rename, alias, broaden, or narrow a role or permission.
- Do not move enforcement from server to client or treat hidden UI as authorization.
- Do not alter JWT/session claim names, meaning, lifetime, or validation.
- Do not weaken tenant/company predicates in controllers, services, repositories, gateway policy, or database access.
- Documentation must describe current obligations without asserting unverified entitlements.

Approved RBAC work requires an access matrix, allowed and denied tests, cross-tenant tests, token/session compatibility, security review, staged rollout, audit evidence, and rollback.

## Future Refactor Recommendation

Create a versioned, machine-testable authorization matrix after current effective permissions and tenant inheritance are formally audited.
