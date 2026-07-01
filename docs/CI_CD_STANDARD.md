# CI/CD Standard

## Purpose

Define secure, reproducible automation for validating, packaging, approving, and deploying ALELS changes.

## Scope

Applies to future continuous integration and delivery pipelines for repository validation, builds, tests, artifacts, migrations, deployment, and release evidence. This document does not add or change a pipeline.

## Rules

- Pipelines run from reviewed version-controlled definitions with least-privilege identities.
- Required gates include repository validation, diff hygiene, relevant builds/tests, secret/dependency checks, and artifact integrity.
- Build once and promote the same immutable artifact between environments.
- Protect production environments with explicit authorization, separation of duties, and auditable approvals.
- Keep secrets in an approved secret store; mask logs and restrict forks/untrusted contexts.
- Pin or verify actions, images, tools, and dependencies.
- Database and contract changes require compatibility and recovery gates.
- Pipeline failure blocks promotion; bypass requires documented owner, reason, risk, expiry, and follow-up.

## Do

- Use ephemeral workers where possible.
- Retain revision, toolchain, results, artifact checksum, approver, and deployment evidence.
- Add concurrency controls to prevent conflicting deployments.
- Define rollback and incident escalation for deployment jobs.

## Do Not

- Do not rebuild per environment.
- Do not place long-lived production credentials in repository variables or logs.
- Do not allow unreviewed code to execute with protected secrets.
- Do not auto-deploy destructive migrations without explicit safeguards.

## Validation

Review pipeline permissions and provenance; test success, failure, cancellation, retry, approval, secret redaction, artifact promotion, deployment verification, and rollback paths.

## Future Recommendations

Implement CI in stages, then add signed artifacts, SBOMs, policy-as-code, protected environments, deployment attestations, and disaster-recovery pipeline exercises.
