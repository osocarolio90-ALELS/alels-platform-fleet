# ALELS 1M Device Data Pipeline Foundation

Target pipeline:

```text
Device
→ Java Netty Gateway
→ Redpanda/Kafka topic telemetry.raw
→ Ingestion Service consumer group alels-ingestion-service
→ PostgreSQL telemetry/raw_packets
→ Backend API / Server Operations
→ React Frontend
```

## Production-scale rules

1. Gateway must not write telemetry directly to PostgreSQL in scale mode.
2. Gateway publishes normalized telemetry to `telemetry.raw` using IMEI as Kafka key.
3. Ingestion Service owns parsing-to-DB, idempotency, DLQ, and latest-device update.
4. DLQ messages go to both Kafka `telemetry.dead-letter` and PostgreSQL `telemetry_ingestion_dlq`.
5. Server Operations reads `ingestion_pipeline_health` and `kafka_topic_contracts` for visibility.

## Topics

| Topic | Purpose | Dev partitions | Production guidance |
| --- | --- | ---: | ---: |
| telemetry.raw | Gateway telemetry ingress | 96 | 96–256+ |
| telemetry.dead-letter | Failed ingest/replay | 24 | 24–64 |
| telemetry.parsed | Future analytics fanout | 96 | 96–256+ |

## PostgreSQL scale note

The patch adds DB contract columns and indexes for ingestion. Before real high-volume onboarding, rebuild telemetry storage as monthly partitioned tables or convert during a maintenance window. Do not wait until the tables contain large data.

Recommended partition policy:

```text
raw_packets: monthly partitions, retain 30–90 days hot, archive older data
telemetry: monthly partitions, retain 90 days hot, 24 months warm/archive
audit/security/login logs: yearly/monthly partitions, retain by compliance policy
```

## Deployment defaults

Local development can use Docker Compose:

```powershell
cd D:\alels-platform-work\deploy
docker compose up -d postgres redpanda redpanda-console redpanda-topic-init
```

Gateway default publisher mode after this patch:

```properties
telemetry.publisher.mode=kafka
```

For emergency local debugging only:

```powershell
$env:TELEMETRY_PUBLISHER_MODE="direct"
```

## Validation

```powershell
# Redpanda console
http://localhost:8088

# Gateway build
cd D:\alels-platform-work\gateway
mvn clean package

# Ingestion build
cd D:\alels-platform-work\ingestion-service
mvn clean package
```

Database checks:

```sql
SELECT * FROM kafka_topic_contracts;
SELECT * FROM ingestion_pipeline_health;
SELECT * FROM telemetry_ingestion_dlq ORDER BY id DESC LIMIT 10;
```

## Patch 003 - Core Runtime Schema

Migration `database/migrations/003_core_runtime_schema.sql` is the baseline runtime contract before adding new menus. It aligns the Gateway, Kafka ingestion-service, ServerOps monitor, and future business modules.

### High-volume partitioned tables

The following tables are partitioned by month:

- `telemetry` by `server_time`
- `raw_packets` by `received_at`
- `tcp_logs` by `created_at`

The migration creates current and next 3 monthly partitions using `alels_ensure_runtime_partitions(3)`. For production, call this function from a scheduled job before every month changes, for example daily:

```sql
SELECT alels_ensure_runtime_partitions(6);
```

### Runtime contract added

The migration adds or aligns tables required by Gateway/Ingestion:

- `vehicles`
- `device_receive_status`
- `telemetry_io`
- `telemetry_normalized`
- `telemetry_alerts`
- `commands`
- `command_queue`
- `command_responses`
- `device_models`
- `protocol_registry`
- `dictionary_registry`
- `dictionary_import_jobs`
- `dictionary_import_history`
- `normalized_fields`
- `device_io_mappings`
- `alert_rules`
- `password_reset_tokens`
- `telemetry_ingestion_dlq`
- `dlq_replay_jobs`
- `ingestion_pipeline_health`
- `kafka_consumer_lag_snapshots`
- `kafka_topic_contracts`

### Ingestion-service changes

The ingestion-service now processes Kafka records in poll-sized batches instead of one DB transaction per message. For every batch it writes:

- raw payload metadata into `raw_packets`
- parsed data into `telemetry`
- device online/presence update into `devices`
- consumer lag snapshot into `kafka_consumer_lag_snapshots`
- health summary into `ingestion_pipeline_health`

DLQ replay foundation is available with:

```properties
ingestion.dlq.replay.on.startup=true
ingestion.dlq.replay.max.records=1000
```

Keep this disabled by default in production until a controlled operator UI/API is added.

### Development warning

If old unpartitioned `telemetry`, `raw_packets`, or `tcp_logs` tables already exist, migration 003 renames them to `*_legacy_unpartitioned`, creates partitioned parents, and copies existing rows. This is acceptable for the current foundation phase. For a large production database, use an online migration process instead.
