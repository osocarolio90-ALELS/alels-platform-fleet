# CI Pipeline Requirements

## Purpose

Define the secure continuous-integration pipeline required to enforce ALELS engineering certification automatically.

## Scope

Applies to future pull-request, branch, release, artifact and scheduled validation pipelines. This standard does not create or modify CI implementation.

## Rules

- Pipeline definitions are version-controlled, reviewed, protected and least-privileged.
- Untrusted changes run without production secrets or protected deployment identity.
- Use clean, ephemeral workers and pinned or verified tools/actions/images.
- Restore dependencies reproducibly from manifests and lockfiles.
- Build once; promote the same immutable artifact.
- Failures block merge/promotion and retain diagnostic evidence without secrets.
- Use concurrency controls and cancellation to prevent stale or conflicting runs.
- Protect approval, artifact, evidence and environment permissions with separation of duties.
- Record source revision, toolchain, commands, results, checksums, SBOM and approvers.

## Certification Criteria

The pipeline must provide:

1. Repository validator, allowed-file scope, diff hygiene and secret/generated-file checks.
2. Java builds and tests for all Maven modules.
3. Frontend dependency restore, typecheck, build and tests.
4. SQL/migration validation and disposable-database checks.
5. API/event/protocol/telemetry contract checks.
6. SAST, dependency vulnerability/license and secret scanning.
7. Code-quality gates with controlled legacy baselines.
8. Artifact checksum, SBOM and provenance.
9. Protected release approval and environment promotion.
10. Scheduled dependency, security, recovery and performance checks.

Branch protection must require the pipeline, current approvals and resolved review comments. No CI workflow currently exists, so CI certification is **NOT READY**.

## Validation

Test trusted/untrusted pull requests, deliberate compile/test/security failures, secret redaction, permission denial, artifact immutability, cancellation, concurrency, cache integrity, required-check enforcement and evidence retention.

## Future Implementation

Implement a non-deploying CI baseline first, then protected artifact publication, staging deployment, production approval, provenance and scheduled certification jobs.
