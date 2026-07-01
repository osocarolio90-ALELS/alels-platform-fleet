# Rollback Standard

## Purpose

Define how ALELS returns to a known-safe state when a release or operational change breaches acceptance or stop conditions.

## Scope

Applies to application artifacts, frontend assets, configuration, deployments, infrastructure, database changes, migrations, and multi-service releases.

## Rules

- Every production change requires a rollback or explicit forward-recovery strategy before execution.
- Define trigger, decision owner, maximum decision time, steps, dependencies, data implications, verification, and communication.
- Roll back immutable artifacts by redeploying a known-good version, not by patching production.
- Database rollback must respect migration immutability and data written after deployment; destructive reversal requires explicit approval.
- Configuration rollback uses a recorded prior version and secret-safe handling.
- Preserve logs, metrics, deployed identifiers, and incident evidence.

## Do

- Rehearse high-risk rollback paths.
- Keep compatible prior artifacts and required configuration available.
- Stop traffic or writes when rollback could corrupt or lose data.
- Verify service, API, security, tenant, gateway, and telemetry health afterward.

## Do Not

- Do not use `git reset`, ad hoc file edits, or manual database surgery as a production rollback plan.
- Do not reverse a shared migration by editing its SQL.
- Do not roll back one component without checking contract compatibility.
- Do not declare recovery before validation and monitoring stabilize.

## Validation

Confirm trigger and authorization, known-good artifact/configuration identity, command outcomes, database compatibility, health/smoke tests, telemetry reconciliation, monitoring window, and incident record.

## Future Recommendations

Automate versioned deployment rollback, retain signed prior artifacts, and add rollback drills and recovery-time metrics.
