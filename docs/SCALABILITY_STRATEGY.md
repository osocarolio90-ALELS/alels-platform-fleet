# Scalability Strategy

## Purpose

Define evidence-driven scaling principles for ALELS from 10,000 to 10,000,000 connected devices. Device counts are planning tiers, not current capacity claims.

## Shared principles

- Model messages per device, payload size, connection concurrency, burst factor, command rate, retention, queries and tenant distribution.
- Separate connection handling, event buffering, ingestion, transactional APIs and analytics.
- Partition by stable device identity where per-device ordering matters.
- Design idempotency, deduplication, backpressure, admission control, retry budgets and dead-letter handling.
- Keep services stateless where possible; externalize only necessary distributed state.
- Preserve raw-to-normalized traceability and tenant/device ownership.
- Scale from measured saturation signals, not device count alone.
- Maintain tested headroom and cost per device/event.

## Capacity model

For each tier, calculate:

- concurrent and reconnecting device sessions;
- average, peak and burst messages/second;
- ingress and egress bandwidth;
- broker partitions, replication, retention and replay throughput;
- ingestion batch/commit throughput and consumer lag;
- database write/read IOPS, index size, hot-data window and archive growth;
- API concurrency and latency;
- logs, metrics and traces volume;
- recovery backlog and time after an outage.

## 10K devices — baseline production tier

### Strategy

- Establish one representative workload model and golden telemetry fixtures.
- Separate gateway publication from durable ingestion for normal production flow.
- Use at least redundant service instances where production availability requires it.
- Baseline database indexes, batches, connection pools, retention and backup duration.
- Instrument connection count, packets/sec, parse failures, broker lag, ingest latency, database latency, API latency and data reconciliation.

### Exit evidence

Peak plus reconnect-storm load, multi-day soak, dependency failure, replay, restore, tenant isolation and rollback tests meet approved SLO/RPO/RTO with at least 30% planned headroom.

## 100K devices — horizontal service tier

### Strategy

- Use L4 gateway load distribution with deterministic device affinity or distributed session ownership.
- Increase broker partitioning based on measured consumer parallelism and ordering needs.
- Horizontally scale ingestion and stateless backend instances.
- Partition telemetry by time and measured access dimensions; separate operational queries from heavy analytics.
- Apply tenant/device quotas and automated scaling signals.
- Use multi-zone database and broker topology with tested failover.

### Exit evidence

Partition rebalance, gateway-node loss, broker-node loss, database failover, backlog drain, command routing and rolling deployment pass at representative load.

## 1M devices — distributed data platform tier

### Strategy

- Divide gateway/session ownership into explicit shards or cells to bound failure and rebalance.
- Separate telemetry, command, alert and operational event domains and consumer groups.
- Use tiered hot/warm/cold retention and object storage archives.
- Introduce read replicas or purpose-built query/analytics stores without making them transaction authorities.
- Automate capacity forecasting, partition management and noisy-neighbor controls.
- Consider regional data placement according to latency, sovereignty and recovery requirements.

### Exit evidence

Cell isolation, cross-zone loss, large replay, regional dependency degradation, archive/retrieval, cost envelope and reconciliation are proven by repeatable tests.

## 10M devices — multi-region cellular tier

### Strategy

- Use independent regional cells with bounded blast radius and global control-plane governance.
- Keep telemetry ingestion regional; avoid synchronous cross-region dependencies on hot paths.
- Federate metadata deliberately and define conflict/ownership semantics.
- Route devices regionally with controlled evacuation and reconnect-rate protection.
- Use regional event/data planes, replicated configuration catalogs, and tested disaster recovery.
- Apply automated anomaly/capacity management while retaining human approval for security/data-impacting actions.

### Exit evidence

Regional isolation, evacuation limits, cross-region recovery, consistency, sovereignty, global command ownership, multi-day soak and sustainable cost are demonstrated. No single global service may be an undocumented failure dependency.

## Scaling gates

Advancement requires approved SLOs, workload/fixture version, successful load and failure tests, reconciliation, security/tenant validation, capacity headroom, cost forecast, operations staffing, backup/restore evidence and rollback.

## Future recommendations

Build a capacity laboratory and living model, certify tiers sequentially, introduce cell architecture only when measurements justify complexity, and publish quarterly capacity/error-budget reviews.
