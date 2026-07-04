-- ALELS 1M DEVICE DATA PIPELINE FOUNDATION
-- Fixed for PostgreSQL partitioned tables.
-- Purpose: Gateway -> Kafka/Redpanda -> Ingestion -> PostgreSQL contract.
-- IMPORTANT:
-- PostgreSQL requires every UNIQUE/PRIMARY KEY on a partitioned table to include
-- the partition key. Therefore Kafka idempotency must NOT be enforced as a unique
-- index directly on partitioned raw_packets/telemetry.
-- Idempotency is enforced in kafka_processed_offsets, a compact non-partitioned
-- guard table keyed by Kafka topic/partition/offset.

-- Raw packet ingestion contract.
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS parse_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED';
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS kafka_topic VARCHAR(120);
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS kafka_partition INTEGER;
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS kafka_offset BIGINT;
ALTER TABLE raw_packets ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_raw_packets_imei_received_at ON raw_packets(imei, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_raw_packets_channel_received_at ON raw_packets(channel, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_raw_packets_payload_hash ON raw_packets(payload_hash);
CREATE INDEX IF NOT EXISTS idx_raw_packets_kafka_lookup
    ON raw_packets(kafka_topic, kafka_partition, kafka_offset, received_at DESC)
    WHERE kafka_topic IS NOT NULL AND kafka_partition IS NOT NULL AND kafka_offset IS NOT NULL;

-- Defensive cleanup if an earlier non-partitioned/dev schema had these indexes.
-- They are invalid for ALELS partitioned telemetry/raw_packets and should not be recreated.
DROP INDEX IF EXISTS uq_raw_packets_kafka_offset;
DROP INDEX IF EXISTS uq_telemetry_kafka_offset;
DROP INDEX IF EXISTS uq_telemetry_idempotency;

-- Parsed telemetry contract used by ingestion-service.
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS source_protocol VARCHAR(80);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS io_data JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS ingestion_status VARCHAR(30) NOT NULL DEFAULT 'VALID';
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS ingestion_error TEXT;
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS kafka_topic VARCHAR(120);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS kafka_partition INTEGER;
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS kafka_offset BIGINT;
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS protocol VARCHAR(80);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS channel VARCHAR(40);
ALTER TABLE telemetry ADD COLUMN IF NOT EXISTS packet_sequence BIGINT;

CREATE INDEX IF NOT EXISTS idx_telemetry_imei_server_time ON telemetry(imei, server_time DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_device_time ON telemetry(device_time DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_telemetry_protocol_channel_time ON telemetry(protocol, channel, server_time DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_io_data_gin ON telemetry USING GIN(io_data);
CREATE INDEX IF NOT EXISTS idx_telemetry_payload_hash ON telemetry(payload_hash);
CREATE INDEX IF NOT EXISTS idx_telemetry_kafka_lookup
    ON telemetry(kafka_topic, kafka_partition, kafka_offset, server_time DESC)
    WHERE kafka_topic IS NOT NULL AND kafka_partition IS NOT NULL AND kafka_offset IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_telemetry_packet_sequence_lookup
    ON telemetry(imei, protocol, channel, packet_sequence, server_time DESC)
    WHERE packet_sequence IS NOT NULL;

-- Kafka offset guard table.
-- This table is intentionally NOT partitioned so PostgreSQL can enforce global
-- idempotency across all telemetry/raw packet partitions.
CREATE TABLE IF NOT EXISTS kafka_processed_offsets (
    topic_name VARCHAR(120) NOT NULL,
    partition_no INTEGER NOT NULL,
    offset_no BIGINT NOT NULL,
    consumer_group VARCHAR(120) NOT NULL DEFAULT 'alels-ingestion-service',
    message_key VARCHAR(120),
    payload_hash VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSED',
    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (topic_name, partition_no, offset_no, consumer_group)
);

CREATE INDEX IF NOT EXISTS idx_kafka_processed_offsets_processed_at
    ON kafka_processed_offsets(processed_at DESC);
CREATE INDEX IF NOT EXISTS idx_kafka_processed_offsets_status_time
    ON kafka_processed_offsets(status, processed_at DESC);

-- Dead-letter table for messages that fail parsing/validation/DB insertion.
CREATE TABLE IF NOT EXISTS telemetry_ingestion_dlq (
    id BIGSERIAL PRIMARY KEY,
    topic_name VARCHAR(120) NOT NULL,
    partition_no INTEGER NOT NULL,
    offset_no BIGINT NOT NULL,
    message_key VARCHAR(120),
    payload TEXT,
    failure_reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    CONSTRAINT uq_telemetry_ingestion_dlq_offset UNIQUE(topic_name, partition_no, offset_no)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_ingestion_dlq_status_time
    ON telemetry_ingestion_dlq(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_ingestion_dlq_topic_offset
    ON telemetry_ingestion_dlq(topic_name, partition_no, offset_no);

-- Runtime health used by Server Operations / AI Ops monitor.
CREATE TABLE IF NOT EXISTS ingestion_pipeline_health (
    service_name VARCHAR(120) PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    last_heartbeat_at TIMESTAMP,
    processed_last_poll INTEGER NOT NULL DEFAULT 0,
    failed_last_poll INTEGER NOT NULL DEFAULT 0,
    consumer_lag BIGINT NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ingestion_pipeline_health_status
    ON ingestion_pipeline_health(status, updated_at DESC);

INSERT INTO ingestion_pipeline_health(service_name, status, notes)
VALUES ('ingestion-service', 'BOOTSTRAP', 'Waiting for Kafka/Redpanda consumer heartbeat')
ON CONFLICT (service_name) DO NOTHING;

-- Topic contract registry. This keeps topic names/partition targets visible in DB and ServerOps.
CREATE TABLE IF NOT EXISTS kafka_topic_contracts (
    topic_name VARCHAR(120) PRIMARY KEY,
    purpose TEXT NOT NULL,
    recommended_partitions INTEGER NOT NULL,
    retention_hours INTEGER NOT NULL,
    cleanup_policy VARCHAR(40) NOT NULL DEFAULT 'delete',
    owner_module VARCHAR(80) NOT NULL DEFAULT 'gateway-ingestion',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO kafka_topic_contracts(topic_name, purpose, recommended_partitions, retention_hours, cleanup_policy)
VALUES
    ('telemetry.raw', 'Raw normalized telemetry events from Java Netty Gateway before DB ingestion', 96, 72, 'delete'),
    ('telemetry.dead-letter', 'Failed telemetry messages requiring replay/review', 24, 720, 'delete'),
    ('telemetry.parsed', 'Optional parsed telemetry event stream for future analytics consumers', 96, 72, 'delete')
ON CONFLICT (topic_name) DO UPDATE SET
    purpose = EXCLUDED.purpose,
    recommended_partitions = EXCLUDED.recommended_partitions,
    retention_hours = EXCLUDED.retention_hours,
    cleanup_policy = EXCLUDED.cleanup_policy,
    updated_at = NOW();
