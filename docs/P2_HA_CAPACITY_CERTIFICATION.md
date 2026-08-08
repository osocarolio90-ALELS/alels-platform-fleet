# P2 HA and Capacity Certification

## Status

P2 is implemented as measurable runtime safety and a certification workflow. Repository builds alone do not certify a device tier. A tier becomes certified only after its evidence bundle passes on representative multi-node infrastructure.

## Implemented foundation

- Gateway connection and pending-publish limits are explicit and fail closed.
- Gateway exposes `/live`, `/ready`, and low-cardinality Prometheus `/metrics` on `ALELS_GATEWAY_HEALTH_PORT`.
- Health endpoints bind to loopback by default; expose them only on a protected internal network for remote balancing/scraping.
- Readiness becomes false during drain or saturation so an L4 balancer can stop new connections.
- Current-state database writes are collapsed to one update per IMEI per ingestion batch; telemetry history remains append-only.
- `deploy/ha/haproxy.cfg` defines two gateway and two backend targets.
- `deploy/ha/docker-compose.capacity-lab.yml` provides a three-node broker lab with RF=3 and minimum ISR=2 for the raw topic.
- `deploy/ha/prometheus-rules.yml` alerts on lost readiness, low headroom, publish failures and growing ingestion lag.
- `tools/database/check-p2-ha.sql` rejects a database without WAL archive and a streaming standby.
- `tools/capacity/GatewayCapacityProbe.java` generates real TCP traffic and fails when connection or ACK reconciliation is incomplete.
- `tools/capacity/run-telemetry-reconciliation.ps1` rejects processed Kafka offsets without matching raw and normalized database records.

## Required environment

Use representative Linux hosts. A single Windows laptop is not a valid 100K/1M certification environment because ephemeral ports, file descriptors, CPU, memory, NIC and the load generator itself become the bottleneck. Distribute generators across independent hosts and preserve at least 30% measured headroom after one node is removed.

Production HA additionally requires TLS/SASL/ACL for the broker, a fenced PostgreSQL failover manager, independent backup storage, PgBouncer, secret management, multi-zone placement and an approved SLO/RPO/RTO. The capacity-lab Compose file is not a production manifest.

## Ordered gates

1. Run the repository quality gate.
2. Apply migrations through `018` and run the P0/P1 database check.
3. Run `tools/database/run-p2-ha-check.ps1` against the primary.
4. Validate three broker nodes, topic RF=3, min ISR=2 and producer `acks=all`.
5. Run 10K for at least five minutes, then 50K, 100K, 72-hour soak and one-million target sequentially.
6. At every tier capture gateway metrics, broker metrics, consumer lag, PostgreSQL latency/WAL/locks/storage, host saturation and load-generator saturation.
7. Reconcile probe sent/ACK counts with durable broker offsets and database committed plus durable DLQ counts.
8. Repeat while killing one gateway, one ingestion worker and one broker; perform database failover, reconnect storm, partition rollover and backlog drain.
9. Run a seven-day soak for the final target and complete backup/PITR restore plus failback drills.

Example baseline command:

```powershell
.\tools\capacity\run-gateway-capacity-probe.ps1 `
  -HostName "gateway-lb.internal" `
  -Connections 10000 `
  -DurationSeconds 300 `
  -SendIntervalSeconds 30
```

Database-side reconciliation after each test window:

```powershell
.\tools\capacity\run-telemetry-reconciliation.ps1 -WindowHours 24
```

## Pass criteria

- All requested connections establish and remain bounded by the declared workload.
- `sent = acknowledged`; every ACK maps to durable broker acceptance.
- Durable broker input equals database committed plus durable DLQ, with only explained idempotent replays.
- No unbounded heap, direct memory, thread, queue, consumer lag, WAL, lock, bloat or disk growth.
- ACK latency and database/broker percentiles remain inside the approved SLO.
- Node/dependency failure recovers within RTO/RPO without silent loss or split brain.
- At least 30% capacity headroom remains at peak after the required failure-domain loss.
- No known open Sev-1/critical defect, data-loss path or critical security finding.

Until all evidence exists, report the exact highest certified tier; never label the platform “1M certified” from configuration or unit tests alone.
