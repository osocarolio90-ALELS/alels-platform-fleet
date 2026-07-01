# Code Quality Standard

## Purpose

Define measurable quality expectations that make ALELS source safe to review, test, evolve and operate without forcing unrelated legacy refactors.

## Scope

Applies to Java, TypeScript/React, SQL, CSS, scripts, configuration templates and future approved source changes.

## Rules

- Correctness, security, readability and explicit behavior outrank stylistic novelty.
- Follow existing module/layer boundaries and specialized coding standards.
- Keep changes focused; prohibit unrelated formatting, renaming, dead-code cleanup and optimization.
- Validate untrusted input and handle failure, null, boundary, concurrency and partial-success cases deliberately.
- Do not duplicate authentication, authorization, tenant, telemetry or contract logic.
- Comments explain decisions and invariants; code and names express behavior.
- Static-analysis suppressions require narrow scope, rationale and review.
- Legacy findings may be baselined, but new or modified code must not worsen them.

## Certification Criteria

- Compilation/typecheck and applicable format/lint/static-analysis gates pass.
- No blocking correctness, security, resource-leak, concurrency, injection or data-integrity finding remains.
- Changed code has focused tests and requirement traceability.
- Complexity, duplication and dependency-boundary thresholds meet approved baselines.
- Public/API/event/schema behavior is documented and compatibility-reviewed.
- Logging/error handling are safe, bounded and observable.
- Code review checklist and Definition of Done are complete.
- Generated files, secrets, runtime data and unexplained warnings are absent.

Current builds pass, but automated static-analysis baselines and tests are not demonstrated; code-quality certification is **PARTIAL**.

## Validation

Run repository-supported compiler/type checks, future lint/static-analysis tools, focused tests and architecture checks; inspect diff and suppressions manually; record results against the exact revision.

## Future Implementation

Adopt formatters and analyzers module by module after baselining, then add architecture tests, nullness checks, complexity/duplication budgets, review analytics and controlled technical-debt reduction.
