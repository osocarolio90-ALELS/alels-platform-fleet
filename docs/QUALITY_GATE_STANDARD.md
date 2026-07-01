# Quality Gate Standard

## Purpose

Define consistent pass/fail gates that prevent unsafe ALELS changes from advancing through review, build, release, and deployment.

## Scope

Applies to local readiness, pull requests, integration branches, release candidates, deployment promotion, and post-deployment verification.

## Rules

- Gates are deterministic, documented, automated where feasible, and fail closed.
- Required gates cannot be bypassed silently or converted into informational checks.
- Each gate defines trigger, inputs, command/control, threshold, evidence, owner, timeout, and failure action.
- New failures block promotion; accepted legacy baselines require an owner and may not worsen.
- Generated output, secrets, runtime data, unrelated formatting, and undocumented scope are blocking repository failures.
- Gates must not mutate production or rely on uncontrolled production data.
- Gate changes receive the same review as code they protect.

## Certification Criteria

Minimum gate sequence:

| Stage | Mandatory gates |
| --- | --- |
| Repository | scope allowlist, validator, secret/generated-file checks, `git diff --check` |
| Build | reproducible dependency restore, compile/typecheck/package |
| Code quality | formatting baseline, lint/static analysis, architecture and duplication controls |
| Test | unit, integration, contract, security/tenant, migration, protocol/telemetry as applicable |
| Security | SAST, dependency/license, secret, configuration and targeted dynamic validation |
| Performance | regression thresholds and workload certification for affected hot paths |
| Release | immutable artifact, SBOM/provenance, compatibility, rollback/restore readiness |
| Deploy | approval, preflight, health/smoke/reconciliation, observability and rollback trigger |

All applicable mandatory gates must pass. Critical/high security findings, test failures, migration inconsistency, contract breakage, tenant leakage, or telemetry loss/corruption are never waivable at ordinary approval level.

## Validation

Exercise gate success, intentional failure, timeout, cancellation, retry, evidence retention, permission boundaries, and waiver expiry. Confirm a failing mandatory gate prevents downstream promotion.

## Future Implementation

Implement gates incrementally in protected CI, beginning with repository/build checks, then tests/security, followed by performance, provenance, deployment, and recovery gates.
