-- Phase C: tenant-scoped BLE discovery and durable source-block history.
-- Rollback after exporting retained BLE data: DROP FUNCTION alels_store_ble_observations;
-- DROP TABLE ble_observations; DROP TABLE ble_beacons; then drop uq_devices_id_company
-- only if no other schema object uses it. Never run this rollback automatically.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_devices_id_company') THEN
        ALTER TABLE devices ADD CONSTRAINT uq_devices_id_company UNIQUE (id, company_id);
    END IF;
END $$;
-- The direct publisher resolves an already audited raw frame by its id.
CREATE INDEX IF NOT EXISTS pidx_raw_packets_id ON raw_packets(id);
CREATE INDEX IF NOT EXISTS pidx_telemetry_id ON telemetry(id);

CREATE TABLE IF NOT EXISTS ble_beacons (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    beacon_protocol VARCHAR(32) NOT NULL,
    identity_kind VARCHAR(40) NOT NULL,
    identity_value TEXT NOT NULL,
    namespace_hex CHAR(20),
    instance_hex CHAR(12),
    uuid_hex CHAR(32),
    major INTEGER,
    minor INTEGER,
    verified_mac CHAR(12),
    first_seen TIMESTAMPTZ NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL,
    UNIQUE (company_id, beacon_protocol, identity_kind, identity_value),
    UNIQUE (id, company_id)
);
CREATE INDEX IF NOT EXISTS idx_ble_beacons_company_last_seen ON ble_beacons(company_id, last_seen DESC, id DESC);

CREATE TABLE IF NOT EXISTS ble_observations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    beacon_id BIGINT REFERENCES ble_beacons(id) ON DELETE CASCADE,
    gateway_device_id BIGINT NOT NULL,
    gateway_imei VARCHAR(32) NOT NULL,
    source_record_key CHAR(64) NOT NULL,
    source_telemetry_id BIGINT NOT NULL,
    source_telemetry_server_time TIMESTAMPTZ NOT NULL,
    source_protocol VARCHAR(80) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    avl_timestamp TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    io_id INTEGER NOT NULL,
    block_index INTEGER NOT NULL,
    beacon_protocol VARCHAR(32),
    raw_identifier TEXT,
    rssi_dbm INTEGER,
    battery_millivolts INTEGER,
    temperature_raw INTEGER,
    gateway_latitude DOUBLE PRECISION,
    gateway_longitude DOUBLE PRECISION,
    gateway_altitude INTEGER,
    gateway_hdop DOUBLE PRECISION,
    gateway_satellites INTEGER,
    gps_valid BOOLEAN NOT NULL,
    raw_block_hex TEXT NOT NULL,
    parser_status VARCHAR(40) NOT NULL,
    parser_error JSONB,
    CONSTRAINT fk_ble_observation_gateway_tenant FOREIGN KEY (gateway_device_id, company_id)
        REFERENCES devices(id, company_id) ON DELETE CASCADE,
    CONSTRAINT fk_ble_observation_beacon_tenant FOREIGN KEY (beacon_id, company_id)
        REFERENCES ble_beacons(id, company_id) ON DELETE CASCADE,
    CONSTRAINT uq_ble_observation_source_block UNIQUE (company_id, source_record_key, io_id, block_index)
);
CREATE INDEX IF NOT EXISTS idx_ble_observations_beacon_time ON ble_observations(company_id, beacon_id, avl_timestamp DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_ble_observations_gateway_time ON ble_observations(company_id, gateway_device_id, avl_timestamp DESC, id DESC);

-- Called inside the caller's existing telemetry transaction. A negative block index
-- denotes one parser-error remainder, while decoded blocks use zero-based indexes.
CREATE OR REPLACE FUNCTION alels_store_ble_observations(
    p_imei VARCHAR, p_source_record_key CHAR(64), p_telemetry_id BIGINT,
    p_telemetry_server_time TIMESTAMPTZ, p_source_protocol VARCHAR, p_channel VARCHAR,
    p_avl_timestamp TIMESTAMPTZ, p_received_at TIMESTAMPTZ,
    p_latitude DOUBLE PRECISION, p_longitude DOUBLE PRECISION, p_altitude INTEGER,
    p_hdop DOUBLE PRECISION, p_satellites INTEGER, p_results JSONB
) RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
    gateway_row RECORD;
    io_result JSONB;
    beacon JSONB;
    parse_error JSONB;
    io_number INTEGER;
    block_number INTEGER;
    v_identity_protocol VARCHAR(32);
    v_identity_kind VARCHAR(40);
    v_identity_value TEXT;
    v_identifier TEXT;
    v_mac CHAR(12);
    beacon_key BIGINT;
    inserted_id BIGINT;
    observed_at TIMESTAMPTZ;
    valid_gps BOOLEAN;
    inserted_count INTEGER := 0;
BEGIN
    IF p_results IS NULL OR jsonb_array_length(p_results) = 0 THEN RETURN 0; END IF;
    SELECT id, company_id INTO gateway_row FROM devices WHERE imei = p_imei AND deleted_at IS NULL;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'BLE gateway is unavailable: %', p_imei;
    END IF;
    IF gateway_row.company_id IS NULL THEN
        RAISE EXCEPTION 'BLE gateway has no active tenant: %', p_imei;
    END IF;
    IF p_telemetry_id IS NULL OR p_telemetry_server_time IS NULL OR p_source_record_key IS NULL THEN
        RAISE EXCEPTION 'BLE source telemetry identity is required';
    END IF;
    observed_at := COALESCE(p_avl_timestamp, p_received_at);
    valid_gps := COALESCE(p_latitude BETWEEN -90 AND 90 AND p_longitude BETWEEN -180 AND 180
        AND NOT (p_latitude = 0 AND p_longitude = 0)
        AND COALESCE(p_satellites, 0) > 0, FALSE);

    FOR io_result IN SELECT value FROM jsonb_array_elements(p_results) LOOP
        io_number := (io_result ->> 'ioId')::integer;
        block_number := 0;
        FOR beacon IN SELECT value FROM jsonb_array_elements(COALESCE(io_result -> 'beacons', '[]'::jsonb)) LOOP
            IF EXISTS (SELECT 1 FROM ble_observations
                       WHERE company_id = gateway_row.company_id AND source_record_key = p_source_record_key
                         AND io_id = io_number AND block_index = block_number) THEN
                block_number := block_number + 1;
                CONTINUE;
            END IF;
            v_identifier := upper(beacon ->> 'id');
            v_identity_protocol := beacon ->> 'type';
            v_identity_kind := NULL;
            v_identity_value := NULL;
            v_mac := NULL;
            IF beacon ->> 'mac' ~ '^[0-9A-Fa-f]{12}$' THEN v_mac := upper(beacon ->> 'mac'); END IF;
            IF v_identity_protocol = 'EDDYSTONE' AND v_identifier ~ '^[0-9A-F]{32}$' THEN
                v_identity_kind := 'EDDYSTONE_UID';
                v_identity_value := substring(v_identifier, 1, 20) || ':' || substring(v_identifier, 21, 12);
            ELSIF v_identity_protocol = 'IBEACON' AND v_identifier ~ '^[0-9A-F]{40}$' THEN
                v_identity_kind := 'IBEACON_UUID_MAJOR_MINOR';
                v_identity_value := substring(v_identifier, 1, 32) || ':' || substring(v_identifier, 33, 4)
                    || ':' || substring(v_identifier, 37, 4);
            ELSIF v_identifier ~ '^[0-9A-F]+$' THEN
                v_identity_protocol := 'CUSTOM';
                v_identity_kind := 'GATEWAY_CONFIGURED_ID';
                v_identity_value := gateway_row.id::text || ':' || io_number::text || ':' || v_identifier;
            END IF;
            beacon_key := NULL;
            IF v_identity_value IS NOT NULL THEN
                INSERT INTO ble_beacons(company_id, beacon_protocol, identity_kind, identity_value,
                    namespace_hex, instance_hex, uuid_hex, major, minor, verified_mac, first_seen, last_seen)
                VALUES (gateway_row.company_id, v_identity_protocol, v_identity_kind, v_identity_value,
                    CASE WHEN v_identity_kind = 'EDDYSTONE_UID' THEN substring(v_identifier, 1, 20) END,
                    CASE WHEN v_identity_kind = 'EDDYSTONE_UID' THEN substring(v_identifier, 21, 12) END,
                    CASE WHEN v_identity_kind = 'IBEACON_UUID_MAJOR_MINOR' THEN substring(v_identifier, 1, 32) END,
                    CASE WHEN v_identity_kind = 'IBEACON_UUID_MAJOR_MINOR' THEN ('x' || substring(v_identifier, 33, 4))::bit(16)::integer END,
                    CASE WHEN v_identity_kind = 'IBEACON_UUID_MAJOR_MINOR' THEN ('x' || substring(v_identifier, 37, 4))::bit(16)::integer END,
                    v_mac, observed_at, observed_at)
                ON CONFLICT (company_id, beacon_protocol, identity_kind, identity_value)
                DO UPDATE SET last_seen = GREATEST(ble_beacons.last_seen, EXCLUDED.last_seen),
                              first_seen = LEAST(ble_beacons.first_seen, EXCLUDED.first_seen),
                              verified_mac = COALESCE(ble_beacons.verified_mac, EXCLUDED.verified_mac)
                RETURNING id INTO beacon_key;
            END IF;
            inserted_id := NULL;
            INSERT INTO ble_observations(company_id, beacon_id, gateway_device_id, gateway_imei,
                source_record_key, source_telemetry_id, source_telemetry_server_time,
                source_protocol, channel, avl_timestamp, received_at, io_id, block_index,
                beacon_protocol, raw_identifier, rssi_dbm, battery_millivolts, temperature_raw,
                gateway_latitude, gateway_longitude, gateway_altitude, gateway_hdop, gateway_satellites,
                gps_valid, raw_block_hex, parser_status)
            VALUES (gateway_row.company_id, beacon_key, gateway_row.id, p_imei,
                p_source_record_key, p_telemetry_id, p_telemetry_server_time,
                p_source_protocol, p_channel, p_avl_timestamp, p_received_at, io_number, block_number,
                v_identity_protocol, v_identifier, (beacon ->> 'rssiDbm')::integer,
                (beacon ->> 'batteryMillivolts')::integer, (beacon ->> 'temperatureRaw')::integer,
                p_latitude, p_longitude, p_altitude, p_hdop, p_satellites, valid_gps,
                COALESCE(beacon ->> 'rawBlockHex', ''), 'VALID')
            ON CONFLICT (company_id, source_record_key, io_id, block_index) DO NOTHING
            RETURNING id INTO inserted_id;
            block_number := block_number + 1;
            IF inserted_id IS NOT NULL THEN inserted_count := inserted_count + 1; END IF;
        END LOOP;
        parse_error := io_result -> 'errors' -> 0;
        IF parse_error IS NOT NULL THEN
            inserted_id := NULL;
            INSERT INTO ble_observations(company_id, gateway_device_id, gateway_imei,
                source_record_key, source_telemetry_id, source_telemetry_server_time,
                source_protocol, channel, avl_timestamp, received_at, io_id, block_index,
                gateway_latitude, gateway_longitude, gateway_altitude, gateway_hdop, gateway_satellites,
                gps_valid, raw_block_hex, parser_status, parser_error)
            VALUES (gateway_row.company_id, gateway_row.id, p_imei,
                p_source_record_key, p_telemetry_id, p_telemetry_server_time,
                p_source_protocol, p_channel, p_avl_timestamp, p_received_at, io_number, -1,
                p_latitude, p_longitude, p_altitude, p_hdop, p_satellites,
                valid_gps, COALESCE(io_result ->> 'undecodedHex', ''), 'ERROR', parse_error)
            ON CONFLICT (company_id, source_record_key, io_id, block_index) DO NOTHING
            RETURNING id INTO inserted_id;
            IF inserted_id IS NOT NULL THEN inserted_count := inserted_count + 1; END IF;
        END IF;
    END LOOP;
    RETURN inserted_count;
END $$;

-- Complete parsed BLE measurement snapshot, retained alongside the original source block.
-- Migration026 was not installed in the verified local database before this extension.
ALTER TABLE ble_observations ADD COLUMN IF NOT EXISTS decoded_payload JSONB;
ALTER TABLE ble_observations ADD COLUMN IF NOT EXISTS decoder_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ble_observations ADD COLUMN IF NOT EXISTS decoded_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_ble_decoded_source ON ble_observations
    (company_id,source_telemetry_id,source_telemetry_server_time,decoder_revision);

-- Same audited source-block identity used by the existing Direct Database writer.
-- Bytea separators preserve NUL bytes, which PostgreSQL text values cannot contain.
CREATE OR REPLACE FUNCTION alels_ble_source_key(p_imei TEXT,p_protocol TEXT,p_channel TEXT,p_sequence BIGINT,p_raw TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(sha256(convert_to(COALESCE(p_imei,'null'),'UTF8') || decode('00','hex') ||
        convert_to(COALESCE(p_protocol,'null'),'UTF8') || decode('00','hex') ||
        convert_to(COALESCE(p_channel,'null'),'UTF8') || decode('00','hex') ||
        convert_to(COALESCE(p_sequence,0)::text,'UTF8') || decode('00','hex') || convert_to(p_raw,'UTF8')),'hex')
$$;
