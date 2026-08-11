-- ALELS Core Runtime Schema for 1M Active Device Pipeline
-- Scope:
-- 1. Align database contract used by gateway and ingestion-service.
-- 2. Convert high-volume runtime tables to time partitioned parents where possible.
-- 3. Add operational tables for DLQ replay and Kafka consumer lag monitoring.
-- Notes:
-- - This migration is designed for development/early foundation stage.
-- - If telemetry/raw_packets/tcp_logs already contain data, existing tables are renamed to *_legacy_unpartitioned
--   and their rows are copied into the new partitioned parents.
-- - For very large production data, migrate with a dedicated online migration plan instead of running this directly.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------
-- Shared helpers
-- -----------------------------
CREATE OR REPLACE FUNCTION alels_month_start(value timestamptz)
RETURNS timestamptz
LANGUAGE sql
STABLE
AS $$ SELECT date_trunc('month', value)::timestamptz $$;

CREATE OR REPLACE FUNCTION alels_partition_suffix(value timestamptz)
RETURNS text
LANGUAGE sql
STABLE
AS $$ SELECT to_char(value, 'YYYYMM') $$;

CREATE OR REPLACE FUNCTION alels_ensure_month_partition(
    parent_table regclass,
    partition_prefix text,
    partition_column text,
    month_start timestamptz
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    p_name text := partition_prefix || '_' || alels_partition_suffix(month_start);
    p_from timestamptz := alels_month_start(month_start);
    p_to timestamptz := alels_month_start(month_start + interval '1 month');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
        p_name,
        parent_table,
        p_from,
        p_to
    );
END;
$$;

CREATE OR REPLACE FUNCTION alels_ensure_runtime_partitions(months_ahead integer DEFAULT 3)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    i integer;
    m timestamptz;
BEGIN
    FOR i IN 0..GREATEST(months_ahead, 0) LOOP
        m := alels_month_start(NOW() + (i || ' month')::interval);
        IF to_regclass('public.telemetry') IS NOT NULL
           AND EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = to_regclass('public.telemetry')) THEN
            PERFORM alels_ensure_month_partition(to_regclass('public.telemetry'), 'telemetry_p', 'server_time', m);
        END IF;
        IF to_regclass('public.raw_packets') IS NOT NULL
           AND EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = to_regclass('public.raw_packets')) THEN
            PERFORM alels_ensure_month_partition(to_regclass('public.raw_packets'), 'raw_packets_p', 'received_at', m);
        END IF;
        IF to_regclass('public.tcp_logs') IS NOT NULL
           AND EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = to_regclass('public.tcp_logs')) THEN
            PERFORM alels_ensure_month_partition(to_regclass('public.tcp_logs'), 'tcp_logs_p', 'created_at', m);
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------
-- Core company/user/device alignment
-- -----------------------------
ALTER TABLE companies ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE devices ADD COLUMN IF NOT EXISTS vehicle_number VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS sim_number VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS receive_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE devices ADD COLUMN IF NOT EXISTS presence_timeout_seconds INTEGER NOT NULL DEFAULT 1800;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS model_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS protocol_code VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_devices_receive_status ON devices(receive_status);
CREATE INDEX IF NOT EXISTS idx_devices_status_updated_at ON devices(status_updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_presence_timeout ON devices(presence_timeout_seconds);
CREATE INDEX IF NOT EXISTS idx_devices_vehicle_id ON devices(vehicle_id);

CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    vehicle_number VARCHAR(80),
    plate_number VARCHAR(80),
    vehicle_name VARCHAR(255),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_vehicles_company ON vehicles(company_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_number ON vehicles(vehicle_number);
CREATE INDEX IF NOT EXISTS idx_vehicles_status ON vehicles(status);

CREATE TABLE IF NOT EXISTS device_receive_status (
    imei VARCHAR(32) PRIMARY KEY,
    receive_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------
-- Partitioned raw_packets
-- -----------------------------
DO $$
BEGIN
    IF to_regclass('public.raw_packets') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = 'public.raw_packets'::regclass) THEN
        ALTER TABLE raw_packets RENAME TO raw_packets_legacy_unpartitioned;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS raw_packets (
    id BIGSERIAL,
    imei VARCHAR(32),
    protocol VARCHAR(80),
    channel VARCHAR(40),
    remote_address VARCHAR(120),
    direction VARCHAR(40),
    payload TEXT,
    raw_json TEXT,
    raw_hex TEXT,
    bytes_count INTEGER,
    payload_hash VARCHAR(64),
    parse_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    kafka_topic VARCHAR(120),
    kafka_partition INTEGER,
    kafka_offset BIGINT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (received_at);

SELECT alels_ensure_runtime_partitions(3);

DO $$
BEGIN
    IF to_regclass('public.raw_packets_legacy_unpartitioned') IS NOT NULL THEN
        INSERT INTO raw_packets (id, imei, channel, protocol, payload, received_at, created_at)
        SELECT id, imei, channel, protocol, payload, received_at, COALESCE(received_at, NOW())
        FROM raw_packets_legacy_unpartitioned
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS pidx_raw_packets_imei_received_at ON raw_packets(imei, received_at DESC);
CREATE INDEX IF NOT EXISTS pidx_raw_packets_channel_received_at ON raw_packets(channel, received_at DESC);
CREATE INDEX IF NOT EXISTS pidx_raw_packets_payload_hash ON raw_packets(payload_hash);
CREATE UNIQUE INDEX IF NOT EXISTS puq_raw_packets_kafka_offset ON raw_packets(kafka_topic, kafka_partition, kafka_offset, received_at)
    WHERE kafka_topic IS NOT NULL AND kafka_partition IS NOT NULL AND kafka_offset IS NOT NULL;

-- -----------------------------
-- Partitioned telemetry
-- -----------------------------
DO $$
BEGIN
    IF to_regclass('public.telemetry') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = 'public.telemetry'::regclass) THEN
        ALTER TABLE telemetry RENAME TO telemetry_legacy_unpartitioned;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS telemetry (
    id BIGSERIAL,
    raw_packet_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    protocol VARCHAR(80),
    channel VARCHAR(40),
    dictionary_code VARCHAR(80),
    source_protocol VARCHAR(80),
    device_time TIMESTAMPTZ NULL,
    server_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    packet_sequence BIGINT,
    latitude DOUBLE PRECISION NULL,
    longitude DOUBLE PRECISION NULL,
    altitude INTEGER NULL,
    angle INTEGER NULL,
    satellites INTEGER NULL,
    speed DOUBLE PRECISION NULL,
    hdop DOUBLE PRECISION NULL,
    priority INTEGER NULL,
    event_io_id INTEGER NULL,
    driver_id BIGINT NULL,
    driver_name VARCHAR(255),
    driver_rfid VARCHAR(120),
    vehicle_status VARCHAR(40),
    io JSONB NOT NULL DEFAULT '{}'::jsonb,
    io_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_hash VARCHAR(64),
    parse_status VARCHAR(40) NOT NULL DEFAULT 'VALID',
    ingestion_status VARCHAR(30) NOT NULL DEFAULT 'VALID',
    ingestion_error TEXT,
    kafka_topic VARCHAR(120),
    kafka_partition INTEGER,
    kafka_offset BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (server_time);

SELECT alels_ensure_runtime_partitions(3);

DO $$
BEGIN
    IF to_regclass('public.telemetry_legacy_unpartitioned') IS NOT NULL THEN
        INSERT INTO telemetry (id, imei, device_time, server_time, latitude, longitude, speed, parse_status, io, io_data, created_at)
        SELECT id, imei, device_time, server_time, latitude, longitude, speed, parse_status, io, COALESCE(io, '{}'::jsonb), COALESCE(server_time, NOW())
        FROM telemetry_legacy_unpartitioned
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS pidx_telemetry_imei_time ON telemetry(imei, device_time DESC NULLS LAST, server_time DESC);
CREATE INDEX IF NOT EXISTS pidx_telemetry_server_time ON telemetry(server_time DESC);
CREATE INDEX IF NOT EXISTS pidx_telemetry_parse_status_time ON telemetry(parse_status, server_time DESC);
CREATE INDEX IF NOT EXISTS pidx_telemetry_protocol_channel_time ON telemetry(protocol, channel, server_time DESC);
CREATE INDEX IF NOT EXISTS pidx_telemetry_packet_sequence ON telemetry(imei, packet_sequence, server_time DESC);
CREATE INDEX IF NOT EXISTS pidx_telemetry_io_data_gin ON telemetry USING GIN(io_data);
CREATE INDEX IF NOT EXISTS pidx_telemetry_io_gin ON telemetry USING GIN(io);
CREATE INDEX IF NOT EXISTS pidx_telemetry_payload_hash ON telemetry(payload_hash);
CREATE UNIQUE INDEX IF NOT EXISTS puq_telemetry_kafka_offset ON telemetry(kafka_topic, kafka_partition, kafka_offset, server_time)
    WHERE kafka_topic IS NOT NULL AND kafka_partition IS NOT NULL AND kafka_offset IS NOT NULL;

-- -----------------------------
-- Partitioned tcp_logs
-- -----------------------------
DO $$
BEGIN
    IF to_regclass('public.tcp_logs') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = to_regclass('public.tcp_logs')) THEN
        ALTER TABLE tcp_logs RENAME TO tcp_logs_legacy_unpartitioned;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS tcp_logs (
    id BIGSERIAL,
    imei VARCHAR(32),
    remote_address VARCHAR(120),
    channel VARCHAR(40),
    protocol VARCHAR(80),
    event_type VARCHAR(80),
    message TEXT,
    bytes_in INTEGER,
    bytes_out INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

SELECT alels_ensure_runtime_partitions(3);

DO $$
BEGIN
    IF to_regclass('public.tcp_logs_legacy_unpartitioned') IS NOT NULL THEN
        INSERT INTO tcp_logs (id, imei, remote_address, channel, protocol, event_type, message, bytes_in, bytes_out, created_at)
        SELECT id, imei, remote_address, channel, protocol, event_type, message, bytes_in, bytes_out, created_at
        FROM tcp_logs_legacy_unpartitioned
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS pidx_tcp_logs_imei_time ON tcp_logs(imei, created_at DESC);
CREATE INDEX IF NOT EXISTS pidx_tcp_logs_event_time ON tcp_logs(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS pidx_tcp_logs_protocol_channel_time ON tcp_logs(protocol, channel, created_at DESC);

-- -----------------------------
-- Telemetry detail/runtime normalized tables
-- -----------------------------
CREATE TABLE IF NOT EXISTS telemetry_io (
    id BIGSERIAL PRIMARY KEY,
    telemetry_id BIGINT,
    raw_packet_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    protocol VARCHAR(80),
    channel VARCHAR(40),
    io_id VARCHAR(80) NOT NULL,
    io_name VARCHAR(255),
    io_category VARCHAR(120),
    raw_value TEXT,
    numeric_value DOUBLE PRECISION,
    real_value DOUBLE PRECISION,
    unit VARCHAR(80),
    multiplier DOUBLE PRECISION DEFAULT 1,
    is_event BOOLEAN NOT NULL DEFAULT FALSE,
    validation_status VARCHAR(40) NOT NULL DEFAULT 'VALID',
    validation_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_telemetry_io_telemetry ON telemetry_io(telemetry_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_io_imei_created ON telemetry_io(imei, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_io_io_id ON telemetry_io(io_id);

CREATE TABLE IF NOT EXISTS telemetry_normalized (
    id BIGSERIAL PRIMARY KEY,
    telemetry_id BIGINT,
    raw_packet_id BIGINT,
    company_id BIGINT,
    vehicle_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    field_code VARCHAR(120) NOT NULL,
    field_name VARCHAR(255),
    category VARCHAR(120),
    raw_value TEXT,
    numeric_value DOUBLE PRECISION,
    text_value TEXT,
    boolean_value BOOLEAN,
    unit VARCHAR(80),
    source_protocol VARCHAR(80),
    source_io_id VARCHAR(80),
    value_type VARCHAR(80),
    source_name VARCHAR(255),
    dictionary_code VARCHAR(80),
    device_model_id BIGINT,
    mapping_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_telemetry_normalized_telemetry ON telemetry_normalized(telemetry_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_normalized_company_field ON telemetry_normalized(company_id, field_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_normalized_imei_field ON telemetry_normalized(imei, field_code, created_at DESC);

CREATE TABLE IF NOT EXISTS telemetry_alerts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    vehicle_id BIGINT,
    telemetry_id BIGINT,
    imei VARCHAR(32),
    rule_code VARCHAR(120),
    severity VARCHAR(40) NOT NULL DEFAULT 'WARNING',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    title TEXT,
    message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_telemetry_alerts_status_time ON telemetry_alerts(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_alerts_imei_time ON telemetry_alerts(imei, created_at DESC);

-- -----------------------------
-- Commands / command queue
-- -----------------------------
CREATE TABLE IF NOT EXISTS commands (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    command_name VARCHAR(120) NOT NULL,
    route VARCHAR(80),
    payload TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_commands_imei_status ON commands(imei, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_commands_company_time ON commands(company_id, created_at DESC);

CREATE TABLE IF NOT EXISTS command_queue (
    id BIGSERIAL PRIMARY KEY,
    legacy_command_id BIGINT,
    company_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    command_name VARCHAR(120) NOT NULL,
    route VARCHAR(80),
    payload TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ NULL,
    acked_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_command_queue_imei_status ON command_queue(imei, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_command_queue_status_priority ON command_queue(status, priority, created_at);

CREATE TABLE IF NOT EXISTS command_responses (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    command_name VARCHAR(120),
    status VARCHAR(40),
    message TEXT,
    response_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_command_responses_imei_time ON command_responses(imei, created_at DESC);

-- -----------------------------
-- Dictionaries / protocols / mappings used by gateway
-- -----------------------------
CREATE TABLE IF NOT EXISTS device_brands (
    id BIGSERIAL PRIMARY KEY,
    brand_code VARCHAR(120) NOT NULL UNIQUE,
    brand_name VARCHAR(255),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS device_models (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT REFERENCES device_brands(id) ON DELETE SET NULL,
    model_code VARCHAR(120) NOT NULL UNIQUE,
    model_name VARCHAR(255),
    vendor VARCHAR(120),
    protocol_code VARCHAR(80),
    parser_code VARCHAR(120),
    dictionary_code VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS protocol_registry (
    id BIGSERIAL PRIMARY KEY,
    protocol_code VARCHAR(80) NOT NULL UNIQUE,
    protocol_name VARCHAR(255),
    brand_code VARCHAR(120),
    protocol_family VARCHAR(120),
    detector_code VARCHAR(120),
    parser_code VARCHAR(120),
    transport_type VARCHAR(80),
    direction VARCHAR(40),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dictionary_registry (
    id BIGSERIAL PRIMARY KEY,
    device_model_id BIGINT,
    dictionary_code VARCHAR(80) NOT NULL UNIQUE,
    dictionary_name VARCHAR(255),
    dictionary_file TEXT,
    dictionary_version VARCHAR(80),
    source_type VARCHAR(80),
    source_path TEXT,
    device_model VARCHAR(120),
    file_path TEXT,
    checksum VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dictionary_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    dictionary_registry_id BIGINT,
    device_model_id BIGINT,
    dictionary_code VARCHAR(80),
    dictionary_file TEXT,
    file_name TEXT,
    import_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dictionary_import_history (
    id BIGSERIAL PRIMARY KEY,
    dictionary_registry_id BIGINT,
    dictionary_code VARCHAR(80),
    dictionary_file TEXT,
    file_checksum VARCHAR(120),
    checksum VARCHAR(120),
    file_path TEXT,
    import_status VARCHAR(40),
    imported_mapping_count INTEGER NOT NULL DEFAULT 0,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_dictionary_import_history_code_time ON dictionary_import_history(dictionary_code, imported_at DESC);

CREATE TABLE IF NOT EXISTS normalized_fields (
    id BIGSERIAL PRIMARY KEY,
    field_code VARCHAR(120) NOT NULL UNIQUE,
    field_name VARCHAR(255),
    category VARCHAR(120),
    unit VARCHAR(80),
    target_unit VARCHAR(80),
    value_type VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS device_io_mappings (
    id BIGSERIAL PRIMARY KEY,
    normalized_field_id BIGINT REFERENCES normalized_fields(id) ON DELETE SET NULL,
    device_model_id BIGINT,
    dictionary_code VARCHAR(80),
    source_protocol VARCHAR(80),
    source_io_id VARCHAR(80) NOT NULL,
    source_name VARCHAR(255),
    source_unit VARCHAR(80),
    field_code VARCHAR(120) NOT NULL,
    field_name VARCHAR(255),
    category VARCHAR(120),
    unit VARCHAR(80),
    target_unit VARCHAR(80),
    multiplier DOUBLE PRECISION NOT NULL DEFAULT 1,
    offset_value DOUBLE PRECISION NOT NULL DEFAULT 0,
    value_type VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_device_io_mappings_lookup ON device_io_mappings(dictionary_code, source_protocol, source_io_id);
CREATE INDEX IF NOT EXISTS idx_device_io_mappings_field ON device_io_mappings(field_code);

CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    device_id BIGINT,
    imei VARCHAR(32),
    dictionary_code VARCHAR(80),
    protocol VARCHAR(80),
    io_id VARCHAR(80),
    alert_type VARCHAR(80),
    title TEXT,
    rule_code VARCHAR(120),
    rule_name VARCHAR(255),
    field_code VARCHAR(120),
    operator VARCHAR(40),
    condition_operator VARCHAR(40),
    threshold_value DOUBLE PRECISION,
    match_value TEXT,
    severity VARCHAR(40) NOT NULL DEFAULT 'WARNING',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_alert_rules_company_enabled ON alert_rules(company_id, enabled);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens(user_id, created_at DESC);

-- -----------------------------
-- DLQ, replay, consumer lag
-- -----------------------------
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_retry_at TIMESTAMPTZ NULL,
    resolved_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_telemetry_ingestion_dlq_offset UNIQUE(topic_name, partition_no, offset_no)
);
CREATE INDEX IF NOT EXISTS idx_telemetry_ingestion_dlq_status_time ON telemetry_ingestion_dlq(status, created_at DESC);

CREATE TABLE IF NOT EXISTS dlq_replay_jobs (
    id BIGSERIAL PRIMARY KEY,
    requested_by VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    max_records INTEGER NOT NULL DEFAULT 1000,
    processed_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_dlq_replay_jobs_status_time ON dlq_replay_jobs(status, created_at DESC);

CREATE TABLE IF NOT EXISTS ingestion_pipeline_health (
    service_name VARCHAR(120) PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    last_heartbeat_at TIMESTAMPTZ,
    processed_last_poll INTEGER NOT NULL DEFAULT 0,
    failed_last_poll INTEGER NOT NULL DEFAULT 0,
    consumer_lag BIGINT NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS kafka_consumer_lag_snapshots (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(120) NOT NULL,
    group_id VARCHAR(120) NOT NULL,
    topic_name VARCHAR(120) NOT NULL,
    partition_no INTEGER NOT NULL,
    current_offset BIGINT NOT NULL DEFAULT 0,
    end_offset BIGINT NOT NULL DEFAULT 0,
    lag BIGINT NOT NULL DEFAULT 0,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kafka_lag_service_time ON kafka_consumer_lag_snapshots(service_name, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_kafka_lag_topic_partition_time ON kafka_consumer_lag_snapshots(topic_name, partition_no, captured_at DESC);

CREATE TABLE IF NOT EXISTS kafka_topic_contracts (
    topic_name VARCHAR(120) PRIMARY KEY,
    purpose TEXT NOT NULL,
    recommended_partitions INTEGER NOT NULL,
    retention_hours INTEGER NOT NULL,
    cleanup_policy VARCHAR(40) NOT NULL DEFAULT 'delete',
    owner_module VARCHAR(80) NOT NULL DEFAULT 'gateway-ingestion',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
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

INSERT INTO ingestion_pipeline_health(service_name, status, notes)
VALUES ('ingestion-service', 'BOOTSTRAP', 'Waiting for Kafka/Redpanda consumer heartbeat')
ON CONFLICT (service_name) DO NOTHING;

SELECT alels_ensure_runtime_partitions(3);

-- -----------------------------
-- Idempotent column alignment for partially existing development schemas
-- -----------------------------
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS brand_code VARCHAR(120);
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS brand_name VARCHAR(255);
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE device_models ADD COLUMN IF NOT EXISTS brand_id BIGINT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS protocol_code VARCHAR(80);
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS parser_code VARCHAR(120);
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);

ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS brand_code VARCHAR(120);
ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS protocol_family VARCHAR(120);
ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS detector_code VARCHAR(120);
ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS parser_code VARCHAR(120);
ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS transport_type VARCHAR(80);
ALTER TABLE protocol_registry ADD COLUMN IF NOT EXISTS direction VARCHAR(40);

ALTER TABLE dictionary_registry ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE dictionary_registry ADD COLUMN IF NOT EXISTS dictionary_file TEXT;
ALTER TABLE dictionary_registry ADD COLUMN IF NOT EXISTS dictionary_version VARCHAR(80);
ALTER TABLE dictionary_registry ADD COLUMN IF NOT EXISTS source_type VARCHAR(80);
ALTER TABLE dictionary_registry ADD COLUMN IF NOT EXISTS source_path TEXT;

ALTER TABLE dictionary_import_jobs ADD COLUMN IF NOT EXISTS dictionary_registry_id BIGINT;
ALTER TABLE dictionary_import_jobs ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE dictionary_import_jobs ADD COLUMN IF NOT EXISTS dictionary_file TEXT;
ALTER TABLE dictionary_import_jobs ADD COLUMN IF NOT EXISTS import_status VARCHAR(40) NOT NULL DEFAULT 'PENDING';

ALTER TABLE dictionary_import_history ADD COLUMN IF NOT EXISTS dictionary_registry_id BIGINT;
ALTER TABLE dictionary_import_history ADD COLUMN IF NOT EXISTS dictionary_file TEXT;
ALTER TABLE dictionary_import_history ADD COLUMN IF NOT EXISTS file_checksum VARCHAR(120);
ALTER TABLE dictionary_import_history ADD COLUMN IF NOT EXISTS import_status VARCHAR(40);
ALTER TABLE dictionary_import_history ADD COLUMN IF NOT EXISTS imported_mapping_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE device_io_mappings ADD COLUMN IF NOT EXISTS normalized_field_id BIGINT;
ALTER TABLE device_io_mappings ADD COLUMN IF NOT EXISTS source_unit VARCHAR(80);
ALTER TABLE device_io_mappings ADD COLUMN IF NOT EXISTS multiplier DOUBLE PRECISION NOT NULL DEFAULT 1;
ALTER TABLE device_io_mappings ADD COLUMN IF NOT EXISTS offset_value DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS device_id BIGINT;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS imei VARCHAR(32);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS protocol VARCHAR(80);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS io_id VARCHAR(80);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS alert_type VARCHAR(80);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS condition_operator VARCHAR(40);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS match_value TEXT;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS title TEXT;

CREATE INDEX IF NOT EXISTS idx_dictionary_registry_device_model ON dictionary_registry(device_model_id, status);
CREATE INDEX IF NOT EXISTS idx_dictionary_import_jobs_status ON dictionary_import_jobs(import_status, id);
CREATE INDEX IF NOT EXISTS idx_alert_rules_runtime_match ON alert_rules(company_id, device_id, imei, dictionary_code, protocol, enabled);
