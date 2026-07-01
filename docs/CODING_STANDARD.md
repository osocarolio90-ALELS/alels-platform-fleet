# Coding Standard

## General rules

- Optimize for correctness, readability, explicit intent, and safe maintenance.
- Match the surrounding module unless doing so would violate security or an approved contract.
- Keep functions and classes cohesive; separate transport, business, persistence, and presentation concerns.
- Prefer clear control flow over clever abstraction.
- Validate untrusted input at system boundaries and preserve tenant/company enforcement.
- Handle null, empty, error, retry, concurrency, and partial-failure cases deliberately.
- Do not suppress errors, discard data, expose secrets, or fabricate fallback values.
- Comments explain why, invariants, or external constraints; code should explain what.
- Delete dead code only when removal is explicitly in scope and proven safe.

## Compatibility

Code style guidance does not authorize changing API shapes, database behavior, UI output, protocol semantics, authentication, authorization, telemetry, or runtime configuration. Legacy deviations may remain until separately approved.

## Dependencies and generated code

Add dependencies only with ownership, licensing/security review, lockfile or manifest updates, and demonstrated need. Never hand-edit generated output; change its source and regenerate through established tooling.

## Validation

Use language-specific static checks, tests, and builds already available in the repository. Review the diff after automated formatting; formatting-only churn is not acceptable in mixed changes.

## Future Refactor Recommendation

Automated formatting and static-analysis baselines should be adopted module by module after legacy-impact assessment.
