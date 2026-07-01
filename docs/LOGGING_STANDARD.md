# Logging Standard

## Purpose

Logs support operations, security investigation, and debugging without becoming a secret store, personal-data archive, or substitute for metrics and audit records.

## Rules

- Use the module’s established logging framework; Java uses SLF4J.
- Log structured, concise events with stable event meaning.
- Include safe operational context such as component, operation, outcome, duration, and established correlation identifiers.
- Use parameterized logging, not string concatenation.
- Log an exception once at the boundary that owns handling; avoid duplicate stack traces across layers.
- Never log passwords, tokens, session cookies, authorization headers, private keys, connection strings, or secret configuration.
- Minimize personal, tenant, company, device, packet, and location data; mask identifiers where operationally sufficient.
- Do not emit raw request bodies, database rows, or telemetry packets by default.

## Levels

- `ERROR`: operation failed and needs attention.
- `WARN`: abnormal/recoverable condition or material risk.
- `INFO`: significant lifecycle or business-operation outcome.
- `DEBUG`: diagnostic detail disabled in normal production use.
- `TRACE`: exceptional deep diagnosis with explicit operational control.

Logging must not change execution behavior. High-volume paths require volume and performance review.

## Audit distinction

Security/business audit events use the established audit mechanism and retention policy; ordinary logs are not an audit substitute.

## Future Refactor Recommendation

Define organization-wide event fields, redaction tests, retention, and correlation propagation in an observability phase.
