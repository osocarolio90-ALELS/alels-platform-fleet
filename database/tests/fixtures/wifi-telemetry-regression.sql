-- Disposable localhost:55443 test database only; this is not a deployment migration.
CREATE TABLE IF NOT EXISTS companies (id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT PRIMARY KEY, company_id BIGINT REFERENCES companies(id), imei VARCHAR(32) UNIQUE,
    deleted_at TIMESTAMPTZ, device_model_id BIGINT, online BOOLEAN, active_channel VARCHAR(40),
    last_protocol VARCHAR(80), last_seen TIMESTAMPTZ, status_updated_at TIMESTAMPTZ,
    gsm_connected BOOLEAN, wifi_connected BOOLEAN, presence_status VARCHAR(40), updated_at TIMESTAMPTZ,
    UNIQUE(id,company_id));
CREATE TABLE IF NOT EXISTS device_brands (
    id BIGINT PRIMARY KEY, brand_code TEXT, brand_name TEXT, status TEXT);
CREATE TABLE IF NOT EXISTS device_models (
    id BIGINT PRIMARY KEY, brand_id BIGINT, model_code TEXT, model_name TEXT,
    protocol_code TEXT, parser_code TEXT, dictionary_code TEXT, status TEXT);
CREATE TABLE IF NOT EXISTS raw_packets (
    id BIGSERIAL PRIMARY KEY, imei VARCHAR(32), raw_hex TEXT, raw_json TEXT, payload TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), protocol VARCHAR(80), channel VARCHAR(40),
    payload_hash VARCHAR(64), parse_status TEXT, kafka_topic TEXT, kafka_partition INTEGER,
    kafka_offset BIGINT, created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS telemetry (
    id BIGSERIAL PRIMARY KEY, raw_packet_id BIGINT, imei VARCHAR(32), protocol VARCHAR(80),
    channel VARCHAR(40), dictionary_code VARCHAR(80), source_protocol VARCHAR(80), device_time TIMESTAMPTZ,
    server_time TIMESTAMPTZ NOT NULL DEFAULT NOW(), packet_sequence BIGINT,
    latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, altitude INTEGER, angle INTEGER,
    satellites INTEGER, speed DOUBLE PRECISION, hdop DOUBLE PRECISION, priority INTEGER,
    event_io_id INTEGER, driver_id BIGINT, driver_name TEXT, driver_rfid TEXT, vehicle_status TEXT,
    io_data JSONB, io JSONB, parse_status TEXT, payload_hash VARCHAR(64), ingestion_status TEXT,
    kafka_topic TEXT, kafka_partition INTEGER, kafka_offset BIGINT, created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS telemetry_io (
    telemetry_id BIGINT, raw_packet_id BIGINT, imei TEXT, protocol TEXT, channel TEXT,
    io_id TEXT, io_name TEXT, io_category TEXT, raw_value TEXT, numeric_value DOUBLE PRECISION,
    real_value DOUBLE PRECISION, unit TEXT, multiplier DOUBLE PRECISION, is_event BOOLEAN,
    validation_status TEXT, validation_message TEXT);
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGINT, company_id BIGINT, device_id BIGINT, imei TEXT, dictionary_code TEXT, protocol TEXT,
    io_id TEXT, alert_type TEXT, rule_code TEXT, title TEXT, severity TEXT,
    condition_operator TEXT, threshold_value DOUBLE PRECISION, match_value TEXT, enabled BOOLEAN);
CREATE TABLE IF NOT EXISTS kafka_processed_offsets (
    topic_name TEXT, partition_no INTEGER, offset_no BIGINT, consumer_group TEXT,
    message_key TEXT, payload_hash TEXT, status TEXT, first_seen_at TIMESTAMPTZ, processed_at TIMESTAMPTZ,
    PRIMARY KEY(topic_name,partition_no,offset_no,consumer_group));
CREATE TABLE IF NOT EXISTS device_presence_cache_shadow (
    imei VARCHAR(32) PRIMARY KEY, presence_status TEXT, active_channel TEXT, last_protocol TEXT,
    last_seen_at TIMESTAMPTZ, offline_after TIMESTAMPTZ, latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION, speed INTEGER, angle INTEGER, satellites INTEGER, device_time TIMESTAMPTZ,
    server_time TIMESTAMPTZ, redis_key TEXT, redis_ttl_seconds INTEGER, updated_at TIMESTAMPTZ);
CREATE TABLE IF NOT EXISTS device_latest_position (
    imei VARCHAR(32) PRIMARY KEY, latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, speed INTEGER,
    angle INTEGER, altitude INTEGER, satellites INTEGER, hdop DOUBLE PRECISION, vehicle_status TEXT,
    device_time TIMESTAMPTZ, server_time TIMESTAMPTZ, updated_at TIMESTAMPTZ);
