# Telemetry Integrity Rules

## Protected chain

Integrity applies end to end: device bytes, framing, protocol detection, decode, acknowledgement, raw evidence, normalization, enrichment, publication, ingestion, persistence, query, and monitoring.

## Invariants

- Preserve source identity, device identity, event time, receive time, protocol, sequence/correlation data, raw evidence, units, and quality indicators.
- Never fabricate precision or silently substitute missing values.
- Unit, sign, scale, timestamp, coordinate, and identifier conversions must be explicit and tested.
- Retry and recovery must account for duplicates; deduplication must not discard distinct events.
- Ordering assumptions must be documented and bounded.
- Malformed/unknown fields are quarantined or surfaced through established handling, not silently coerced.
- Acknowledgement must reflect established protocol semantics and must not falsely confirm durable processing.
- Tenant/company/device ownership must remain correct throughout the chain.

## Change evidence

Approved changes need representative fixtures, boundary/malformed cases, raw-to-normalized traceability, loss/duplicate analysis, compatibility, throughput/latency impact, monitoring, replay, and rollback.

## Future Refactor Recommendation

Create golden end-to-end telemetry fixtures and automated reconciliation metrics in a dedicated integrity program.
