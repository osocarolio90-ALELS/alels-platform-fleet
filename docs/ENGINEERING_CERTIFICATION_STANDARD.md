# Engineering Certification Standard

## Purpose

Define the evidence ALELS must produce before source-code, architecture, deployment, or contract evolution is considered engineering-certified.

## Scope

Applies to releases and material changes affecting frontend, backend, gateway, ingestion, database, migrations, APIs, telemetry, security, tenancy, infrastructure, dependencies, performance, availability, or recovery.

## Rules

- Certification is evidence-based, time-bounded, revision-specific, and environment-specific.
- Documentation and successful compilation are necessary but not sufficient.
- Every applicable quality gate must have an owner, result, artifact, date, revision, and reviewer.
- A failure in security, tenant isolation, data integrity, telemetry integrity, migration safety, rollback, or required tests is blocking.
- Waivers require owner, justification, risk, compensating controls, approver, expiry, and remediation issue.
- Evidence must be reproducible and protected from alteration.
- A source, dependency, configuration, migration, artifact, or environment change invalidates affected evidence.

Certification states:

- **CERTIFIED:** all mandatory gates pass with no expired waivers.
- **CONDITIONALLY CERTIFIED:** only approved, non-critical, time-bounded waivers remain.
- **NOT CERTIFIED:** mandatory evidence is absent or a blocking gate fails.

## Certification Criteria

An ALELS candidate is certified only when:

1. Repository scope and provenance are clean and traceable.
2. Required builds, static checks, tests, contract checks, migration checks, security validation, and dependency controls pass.
3. Performance and capacity meet the declared workload/SLO envelope.
4. Deployment, rollback, backup/restore, observability, and operational ownership are validated.
5. UI, API, RBAC, tenant, database, protocol, and telemetry compatibility are reviewed.
6. The exact immutable artifacts and checksums are recorded.
7. Independent reviewers approve the evidence package.

Current baseline on 2026-06-30: **42/100 — NOT CERTIFIED**. Builds and documentation are present, but automated tests, CI enforcement, security certification, performance evidence, and recovery certification are not demonstrated.

## Validation

Review the certification matrix against the exact revision; verify immutable evidence links, gate results, waiver status, artifact identity, environment, reviewers, and expiry. Re-run mandatory gates when inputs change.

## Future Implementation

Create a machine-readable certification manifest, protected evidence store, release attestation, approval workflow, waiver register, and dashboard only through separately approved implementation phases.
