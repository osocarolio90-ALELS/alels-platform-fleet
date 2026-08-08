# ALELS Cell Deployment

Each cell is an independent failure and capacity domain with its own gateway endpoint, raw/DLQ topics, ingestion consumer group and optional database cluster. Do not place different cell configurations on the same gateway instance.

Device provisioning must assign the endpoint produced by the same `CellRoutingConfig` algorithm used by the gateway. A generic TCP load balancer cannot inspect an IMEI before accepting a connection and therefore cannot replace provisioning-time cell routing.

Within one cell, deploy at least two gateways behind an L4 balancer and multiple ingestion workers in the cell-specific consumer group. A production cell uses a replicated broker and a PostgreSQL HA endpoint. Secrets remain in the environment secret manager and must never be added to these examples.

Start with one cell using the existing defaults. Multi-cell activation requires all of:

- `ALELS_CELL_ID`
- `ALELS_CELL_INDEX`
- `ALELS_CELL_COUNT`
- `ALELS_CELL_ENFORCE=true`
- `ALELS_KAFKA_TOPIC_PER_CELL=true`

Gateway and ingestion instances in a cell must use the same cell ID/count. Roll out topics and ingestion first, then gateway endpoints, then update device provisioning. Never change `ALELS_CELL_COUNT` without an approved migration/evacuation plan because device ownership changes.
