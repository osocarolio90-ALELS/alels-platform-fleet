# High Availability Standard

## Purpose

Define the target availability architecture and evidence required to remove ALELS single points of failure. This is a standard, not an implementation claim.

## Availability principles

- Define service SLO, error budget, RTO, RPO and dependency map before selecting redundancy.
- Distribute replicas across independent failure domains.
- Prefer automated failover with bounded, observable behavior; retain safe manual control.
- Health checks must distinguish process, readiness, dependency and correctness.
- Replication is not backup, and HA is not disaster recovery.
- Eliminate synchronous dependencies from telemetry hot paths where practical.
- Test degradation, failover and failback under representative load.

## Redundancy and load balancing

- API/frontend: multiple immutable instances behind redundant L7 ingress; no local authoritative session state.
- Gateway: multiple nodes behind L4 ingress with explicit connection affinity, drain and reconnect controls.
- Ingestion: consumer-group workers distributed across zones with partition-aware capacity.
- Event backbone: odd-sized production cluster, replicated partitions, rack/zone awareness and controlled leader placement.
- Database: primary/standby across zones, monitored replication, fenced promotion and compatible read replicas.
- Observability/control systems: redundant enough that incidents remain diagnosable.

Load balancers enforce connection/time limits, health-based routing, TLS policy, DDoS/rate controls and graceful draining.

## Gateway clustering

- Maintain one authoritative live owner for a device session or define conflict resolution.
- Route commands to the owning node through durable state/events.
- Persist only necessary session metadata with expiry and fencing.
- On node loss, bound reconnect storms with jitter, rate limits and spare capacity.
- Drain nodes before maintenance and prove acknowledgement/command semantics remain correct.

## Database HA

- Use synchronous/asynchronous replication according to approved latency and RPO.
- Monitor replication lag, WAL/storage, connections, locks and failover readiness.
- Promotion uses quorum/fencing to prevent split brain.
- Applications use bounded connection/retry behavior and recover pools after role change.
- Backups and point-in-time recovery remain mandatory and are tested independently.

## Message broker HA

- Replication factor and minimum in-sync replicas match durability policy.
- Producers use compatible acknowledgements/idempotency; consumers commit only according to established processing guarantees.
- Capacity supports a node/zone loss plus backlog drain.
- Monitor under-replicated partitions, availability, latency, disk, lag and rebalance.
- Test broker loss without silent telemetry loss or uncontrolled duplication.

## Failover and failback

Each component requires detection, decision authority, trigger, fencing, promotion/routing, validation, communication and failback procedure. Failback is a separate controlled change after capacity and integrity stabilize.

## Disaster recovery

Cross-region recovery follows `DISASTER_RECOVERY_STANDARD.md`: independent backups, known artifacts/configuration, secure secrets recovery, ordered restoration, reconciliation and exercises. Regional HA must not share every failure dependency with its recovery environment.

## Validation

Run component, zone and dependency failure tests; verify SLO impact, data/telemetry reconciliation, command ownership, security controls, recovery time, alerting and stable failback.

## Future recommendations

Approve SLO/RPO/RTO tiers, select production failure domains, create HA runbooks, automate safe chaos tests and certify each tier before traffic growth.
