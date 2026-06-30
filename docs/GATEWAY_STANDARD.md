# Gateway Standard

## Integrity boundary

The gateway accepts device traffic, detects protocols, decodes packets, normalizes telemetry, acknowledges devices, routes commands, and publishes or persists data. Changes can affect fleet-wide ingestion and therefore require conservative review.

## Rules

- Preserve wire protocol, framing, acknowledgement timing, decoder behavior, device session semantics, and command routing.
- Unknown or malformed input must fail safely without corrupting subsequent frames.
- Parsers must be deterministic and bounded; never trust device-provided lengths blindly.
- Raw packet evidence and normalized output must remain traceable through established identifiers.
- Dictionary changes require source provenance, validation, compatibility review, and representative fixtures.
- Publisher and database changes must preserve ordering, duplication, retry, and failure semantics.
- Never log secrets; restrict sensitive device identifiers according to operational policy.

## Validation

Approved gateway changes require protocol fixtures, malformed-input cases, build/tests already supplied by the module, telemetry integrity review, and a rollback plan.

## Future Refactor Recommendation

Parser fuzzing, protocol conformance suites, and explicit performance baselines should be introduced as separately scoped work.
