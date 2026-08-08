# P3 Cell Isolation Standard

## Purpose

P3 bounds the blast radius for the one-million-device tier. It does not certify capacity without representative load, failure and soak evidence.

## Distributed session ownership

Every TCP channel receives an unguessable fencing token. With more than one gateway replica, `ALELS_SESSION_OWNER_REDIS_URI` is mandatory and startup fails when it is absent. Before parsing side effects, publishing, or acknowledging a frame, the gateway asynchronously acquires or renews the device key with a bounded TTL. A conflicting owner closes the new channel without a success ACK. Disconnect release uses compare-and-delete so a stale channel cannot release a newer owner. The coordinator must be an authenticated, encrypted, highly available Redis-compatible endpoint dedicated to its cell.

## Autoscaling and cold archive

The autoscaling contracts are under `deploy/kubernetes/autoscaling`; gateway capacity is driven by active connections and ingestion capacity by cell consumer lag. Slow scale-down prevents reconnect and rebalance storms. The cold-archive contract under `deploy/archive` consumes the durable raw topic independently, so archive latency never enters the device ACK boundary. Production acceptance requires archive reconciliation and a restore/replay exercise.

## Implemented contract

- Device-to-cell assignment uses deterministic jump consistent hashing over a stable device identity.
- Single-cell defaults preserve the existing topic and accept legacy envelopes.
- Multi-cell startup fails unless ownership enforcement and topic isolation are both enabled.
- Gateway rejects a device assigned to another cell before telemetry publication.
- Gateway envelopes carry bounded `cellId` and `cellIndex` metadata.
- Raw topic, DLQ topic and ingestion consumer group are isolated per cell.
- Ingestion rejects a multi-cell envelope whose cell ID does not match the worker.
- Each ingestion cell may receive a separate HA database URL without changing application code.

## Invariants

- A device has exactly one configured cell for a given cell-count generation.
- A gateway publishes only traffic owned by its cell.
- An ingestion worker consumes and persists only its cell topics.
- Cell identifiers are configuration metadata, never user-controlled payload authority.
- Changing the number of cells is a controlled migration, not a live configuration edit.
- Single-cell operation remains backward-compatible.

## Required routing

The device registry/provisioning system stores or derives the assigned cell and returns a cell-specific DNS endpoint. Each endpoint routes only to gateway replicas in that cell. Rejected wrong-cell connections are measured and alerted; repeated rejection indicates stale provisioning or routing drift.

Create the isolated raw and DLQ topics before enabling multi-cell gateways:

```powershell
.\tools\capacity\initialize-cell-topics.ps1 -CellCount 2 -Brokers "broker-1:9092,broker-2:9092,broker-3:9092"
```

## Database placement

Begin with a shared HA PostgreSQL cluster only if measured isolation and IOPS remain within SLO. Move a cell to its own HA database endpoint when lock, WAL, storage or recovery evidence requires it. Database authority must never be split implicitly, and cell movement requires reconciliation and a rollback plan.

## Certification

For every cell and for the aggregate tier, prove connection/event capacity, zero unexplained reconciliation delta, 30% headroom, node loss, cell loss, reconnect control, backlog drain, command ownership, database recovery, 72-hour soak and seven-day soak. One-million certification additionally requires a large replay and cell isolation test on multi-node Linux infrastructure.
