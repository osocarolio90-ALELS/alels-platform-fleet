# Observability Standard

## Purpose

Define how ALELS exposes system state so engineers can detect, explain and safely resolve failures across devices, telemetry, services, data and security.

## Principles

- Observe user/device outcomes and data integrity, not only host uptime.
- Use consistent service, environment, version, tenant-safe and correlation attributes.
- Never place credentials, tokens or unrestricted personal/location/packet data in telemetry.
- Bound cardinality, volume, sampling and retention.
- Separate operational logs, security/business audit records and product telemetry.
- Dashboards without alerts/runbooks and alerts without owners are incomplete.

## Logging

- Follow `LOGGING_STANDARD.md`; use structured events and parameterized messages.
- Centralize collection with service, instance, version, severity, event code and safe correlation identifiers.
- Redact secrets and sensitive payloads at source.
- Define retention, access and tamper protection based on classification.
- Avoid duplicate exception logging and uncontrolled per-packet logs.

## Metrics

Minimum service signals: request/rate, errors, duration, saturation, process health, resource use and dependency latency.

Domain signals include:

- gateway connections, reconnects, packets/sec, parse/ack failures, command success/latency;
- broker availability, produce errors, partitions, replication, disk and consumer lag;
- ingestion throughput, batch size/latency, duplicates, retries, DLQ and backlog drain;
- database connections, query latency, locks, replication lag, storage and growth;
- API latency/error/auth failures by safe dimensions;
- telemetry received/persisted/reconciled rates and freshness;
- backup age/result and restore/DR exercise outcomes.

Metric labels must avoid unbounded device/user/token values.

## Tracing

- Propagate trace/correlation context across HTTP and asynchronous event boundaries where protocols permit.
- Represent produce, consume, database and external calls as spans without raw sensitive payloads.
- Use sampling appropriate to volume while retaining errors and high-value workflows.
- Link device packet/event identifiers through logs or governed exemplars when full tracing is impractical.

## Health checks

- Liveness: process can continue.
- Readiness: instance can accept its assigned work.
- Dependency: required downstream status, exposed without causing cascading checks.
- Correctness: synthetic or reconciliation checks prove meaningful outcomes.

Health endpoints are authenticated or minimally exposed as appropriate, fast, bounded and excluded from noisy logs.

## Alerting

- Alerts map to SLO/customer/device impact, data loss/corruption, security, capacity or recovery risk.
- Each alert has owner, severity, threshold rationale, deduplication, runbook and escalation.
- Use multi-window burn-rate or sustained thresholds where practical.
- Test alert delivery and avoid paging on unactionable symptoms.

## Audit

Record security and privileged business actions with actor, tenant/company, action, target, outcome, time and safe request context. Protect integrity and retention; restrict access. Audit records must not be silently dropped and are not ordinary debug logs.

## Validation

For each critical flow, demonstrate detection, correlation, dashboard visibility, alert delivery, runbook action and recovery confirmation. Test redaction and cardinality before production.

## Future recommendations

Adopt OpenTelemetry-compatible context, a centralized logs/metrics/traces platform, SLO catalog, alert ownership, telemetry reconciliation dashboards and quarterly observability game days.
