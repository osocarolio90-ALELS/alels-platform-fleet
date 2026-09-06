-- ALELS Telemetry Event + Trip & Route P1 foundation.
-- Goals:
--   1. Materialize Codec 8/8E/ALELS Event IO into one canonical event stream.
--   2. Keep rule alerts separate in telemetry_alerts while mirroring them into telemetry_events for UI consumption.
--   3. Guarantee parity for Direct DB and Kafka ingestion because both persist into telemetry.
--   4. Add only targeted indexes required by Trip & Route access patterns.

CREATE TABLE IF NOT EXISTS telemetry_events (
    id BIGSERIAL,
    company_id BIGINT,
    device_id BIGINT,
    telemetry_id BIGINT,
    source_record_id BIGINT,
    imei VARCHAR(32) NOT NULL,
    event_source VARCHAR(40) NOT NULL,
    event_code VARCHAR(120) NOT NULL,
    event_io_id VARCHAR(80),
    io_name VARCHAR(255),
    raw_value TEXT,
    numeric_value DOUBLE PRECISION,
    real_value DOUBLE PRECISION,
    unit VARCHAR(80),
    severity VARCHAR(40) NOT NULL DEFAULT 'INFO',
    title TEXT NOT NULL,
    message TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (created_at, id)
) PARTITION BY RANGE (created_at);

-- Keep the shared runtime partition maintenance job aware of this high-volume
-- read model. The existing helper remains the single partition authority.
CREATE OR REPLACE FUNCTION alels_ensure_runtime_partitions(months_ahead integer DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    i integer;
    m timestamptz;
    months_to_create integer := COALESCE(months_ahead, alels_setting_int('partition.months_ahead', 12));
BEGIN
    FOR i IN 0..GREATEST(months_to_create, 0) LOOP
        m := alels_month_start(NOW() + (i || ' month')::interval);
        IF alels_is_partitioned('public.telemetry') THEN
            PERFORM alels_ensure_month_partition('telemetry'::regclass, 'telemetry_p', 'server_time', m);
        END IF;
        IF alels_is_partitioned('public.raw_packets') THEN
            PERFORM alels_ensure_month_partition('raw_packets'::regclass, 'raw_packets_p', 'received_at', m);
        END IF;
        IF alels_is_partitioned('public.tcp_logs') THEN
            PERFORM alels_ensure_month_partition('tcp_logs'::regclass, 'tcp_logs_p', 'created_at', m);
        END IF;
        IF alels_is_partitioned('public.telemetry_events') THEN
            PERFORM alels_ensure_month_partition('telemetry_events'::regclass, 'telemetry_events_p', 'created_at', m);
        END IF;
    END LOOP;
END;
$$;

-- New events must always have future partitions before ingestion starts.
SELECT alels_run_partition_maintenance(12);

-- Historical Event IO/Alert backfill can predate the current month. Create only
-- the months that actually contain source rows, then backfill below.
DO $$
DECLARE
    first_month timestamptz;
    last_month timestamptz := alels_month_start(NOW());
    month_cursor timestamptz;
BEGIN
    SELECT alels_month_start(MIN(source_time))
      INTO first_month
      FROM (
          SELECT server_time AS source_time
          FROM telemetry
          WHERE event_io_id IS NOT NULL AND event_io_id <> 0
          UNION ALL
          SELECT created_at AS source_time
          FROM telemetry_alerts
          WHERE imei IS NOT NULL
      ) source_rows;

    IF first_month IS NOT NULL THEN
        month_cursor := first_month;
        WHILE month_cursor <= last_month LOOP
            PERFORM alels_ensure_month_partition('telemetry_events'::regclass, 'telemetry_events_p', 'created_at', month_cursor);
            month_cursor := month_cursor + INTERVAL '1 month';
        END LOOP;
    END IF;
END;
$$;

INSERT INTO data_retention_policies(table_name, retention_months, retention_action, archive_required, enabled, notes)
VALUES ('telemetry_events', alels_setting_int('retention.telemetry_months', 12), 'DROP_PARTITION', TRUE, FALSE,
        'Canonical telemetry event stream. Enable only after event archive/retention policy is approved.')
ON CONFLICT (table_name) DO UPDATE SET
    retention_months = EXCLUDED.retention_months,
    retention_action = EXCLUDED.retention_action,
    archive_required = EXCLUDED.archive_required,
    notes = EXCLUDED.notes,
    updated_at = NOW();

CREATE INDEX IF NOT EXISTS idx_telemetry_events_id
    ON telemetry_events(id DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_imei_id
    ON telemetry_events(imei, id DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_imei_occurred
    ON telemetry_events(imei, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_company_occurred
    ON telemetry_events(company_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_events_telemetry
    ON telemetry_events(telemetry_id)
    WHERE telemetry_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_events_device_io
    ON telemetry_events(telemetry_id, event_source, event_io_id, created_at)
    WHERE event_source = 'DEVICE_IO' AND telemetry_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_events_alert_source
    ON telemetry_events(event_source, source_record_id, created_at)
    WHERE event_source = 'ALERT' AND source_record_id IS NOT NULL;

-- Query path used by Trip & Route: IMEI + actual event time + telemetry id.
CREATE INDEX IF NOT EXISTS pidx_telemetry_imei_occurred_id
    ON telemetry(imei, (COALESCE(device_time, server_time)), id);

-- The Trip/Stop segmenter only requires two normalized state fields. Keep this
-- index partial so telemetry ingestion is not burdened by a large generic index.
CREATE INDEX IF NOT EXISTS idx_telemetry_normalized_trip_state
    ON telemetry_normalized(imei, telemetry_id, field_code)
    WHERE field_code IN ('ignition', 'movement');

-- Dictionary metadata rows preserve human-readable IO names even when an AVL IO
-- does not map to one of ALELS normalized fields. They are read-only metadata
-- for event/report presentation and are intentionally excluded from normalization.
CREATE UNIQUE INDEX IF NOT EXISTS uq_device_io_mappings_dictionary_metadata
    ON device_io_mappings(device_model_id, source_protocol, source_io_id)
    WHERE normalized_field_id IS NULL AND mapping_source = 'DICTIONARY_METADATA';

CREATE OR REPLACE FUNCTION alels_materialize_device_io_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_id BIGINT;
    v_company_id BIGINT;
    v_io_id TEXT;
    v_io_name TEXT;
    v_unit TEXT;
    v_raw_value TEXT;
BEGIN
    IF NEW.event_io_id IS NULL OR NEW.event_io_id = 0 THEN
        RETURN NEW;
    END IF;

    v_io_id := NEW.event_io_id::TEXT;
    v_raw_value := COALESCE(NEW.io_data ->> v_io_id, NEW.io ->> v_io_id);

    SELECT d.id, d.company_id
      INTO v_device_id, v_company_id
      FROM devices d
     WHERE d.imei = NEW.imei
     LIMIT 1;

    SELECT COALESCE(NULLIF(m.source_name, ''), NULLIF(m.field_name, '')), COALESCE(NULLIF(m.source_unit, ''), NULLIF(m.unit, ''))
      INTO v_io_name, v_unit
      FROM device_io_mappings m
     WHERE m.status = 'ACTIVE'
       AND m.source_io_id = v_io_id
       AND (m.dictionary_code = NEW.dictionary_code OR m.dictionary_code IS NULL)
       AND (m.source_protocol = NEW.source_protocol OR m.source_protocol IS NULL)
     ORDER BY CASE WHEN m.dictionary_code = NEW.dictionary_code THEN 0 ELSE 1 END,
              CASE WHEN m.source_protocol = NEW.source_protocol THEN 0 ELSE 1 END,
              m.id DESC
     LIMIT 1;

    INSERT INTO telemetry_events (
        company_id, device_id, telemetry_id, imei,
        event_source, event_code, event_io_id, io_name,
        raw_value, unit, severity, title, message,
        latitude, longitude, speed, occurred_at, metadata, created_at
    ) VALUES (
        v_company_id, v_device_id, NEW.id, NEW.imei,
        'DEVICE_IO', 'IO:' || v_io_id, v_io_id, v_io_name,
        v_raw_value, v_unit, 'INFO',
        COALESCE(NULLIF(v_io_name, ''), 'Event IO ' || v_io_id),
        COALESCE(NULLIF(v_io_name, ''), 'Event IO ' || v_io_id)
            || ' = ' || COALESCE(v_raw_value, '-'),
        NEW.latitude, NEW.longitude, NEW.speed,
        COALESCE(NEW.device_time, NEW.server_time, NOW()),
        jsonb_strip_nulls(jsonb_build_object(
            'dictionaryCode', NEW.dictionary_code,
            'protocol', NEW.protocol,
            'channel', NEW.channel,
            'sourceProtocol', NEW.source_protocol
        )),
        NEW.server_time
    )
    ON CONFLICT (telemetry_id, event_source, event_io_id, created_at)
        WHERE event_source = 'DEVICE_IO' AND telemetry_id IS NOT NULL
    DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION alels_enrich_device_io_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_company_id BIGINT;
    v_device_id BIGINT;
    v_occurred_at TIMESTAMPTZ;
    v_latitude DOUBLE PRECISION;
    v_longitude DOUBLE PRECISION;
    v_speed DOUBLE PRECISION;
    v_created_at TIMESTAMPTZ;
    v_dictionary_code VARCHAR(80);
    v_source_protocol VARCHAR(80);
BEGIN
    IF NOT NEW.is_event OR NEW.telemetry_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT d.company_id, d.id,
           COALESCE(t.device_time, t.server_time, NEW.created_at),
           t.latitude, t.longitude, t.speed, t.server_time, t.dictionary_code, t.source_protocol
      INTO v_company_id, v_device_id, v_occurred_at,
           v_latitude, v_longitude, v_speed, v_created_at, v_dictionary_code, v_source_protocol
      FROM telemetry t
      LEFT JOIN devices d ON d.imei = t.imei
     WHERE t.id = NEW.telemetry_id AND t.imei = NEW.imei
     ORDER BY t.server_time DESC
     LIMIT 1;

    INSERT INTO telemetry_events (
        company_id, device_id, telemetry_id, imei,
        event_source, event_code, event_io_id, io_name,
        raw_value, numeric_value, real_value, unit,
        severity, title, message,
        latitude, longitude, speed, occurred_at, metadata, created_at
    ) VALUES (
        v_company_id, v_device_id, NEW.telemetry_id, NEW.imei,
        'DEVICE_IO', 'IO:' || NEW.io_id, NEW.io_id, NEW.io_name,
        NEW.raw_value, NEW.numeric_value, NEW.real_value, NEW.unit,
        'INFO', COALESCE(NULLIF(NEW.io_name, ''), 'Event IO ' || NEW.io_id),
        COALESCE(NULLIF(NEW.io_name, ''), 'Event IO ' || NEW.io_id)
            || ' = ' || COALESCE(NEW.real_value::TEXT, NEW.numeric_value::TEXT, NEW.raw_value, '-'),
        v_latitude, v_longitude, v_speed, COALESCE(v_occurred_at, NEW.created_at),
        jsonb_strip_nulls(jsonb_build_object(
            'protocol', NEW.protocol,
            'channel', NEW.channel,
            'dictionaryCode', v_dictionary_code,
            'sourceProtocol', v_source_protocol,
            'category', NEW.io_category
        )),
        COALESCE(v_created_at, NEW.created_at)
    )
    ON CONFLICT (telemetry_id, event_source, event_io_id, created_at)
        WHERE event_source = 'DEVICE_IO' AND telemetry_id IS NOT NULL
    DO UPDATE SET
        company_id = COALESCE(EXCLUDED.company_id, telemetry_events.company_id),
        device_id = COALESCE(EXCLUDED.device_id, telemetry_events.device_id),
        io_name = COALESCE(NULLIF(EXCLUDED.io_name, ''), telemetry_events.io_name),
        raw_value = COALESCE(EXCLUDED.raw_value, telemetry_events.raw_value),
        numeric_value = COALESCE(EXCLUDED.numeric_value, telemetry_events.numeric_value),
        real_value = COALESCE(EXCLUDED.real_value, telemetry_events.real_value),
        unit = COALESCE(NULLIF(EXCLUDED.unit, ''), telemetry_events.unit),
        title = EXCLUDED.title,
        message = EXCLUDED.message,
        latitude = COALESCE(EXCLUDED.latitude, telemetry_events.latitude),
        longitude = COALESCE(EXCLUDED.longitude, telemetry_events.longitude),
        speed = COALESCE(EXCLUDED.speed, telemetry_events.speed),
        occurred_at = EXCLUDED.occurred_at,
        metadata = telemetry_events.metadata || EXCLUDED.metadata;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION alels_materialize_alert_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_id BIGINT;
    v_company_id BIGINT;
    v_occurred_at TIMESTAMPTZ;
    v_latitude DOUBLE PRECISION;
    v_longitude DOUBLE PRECISION;
    v_speed DOUBLE PRECISION;
BEGIN
    IF NEW.imei IS NULL OR BTRIM(NEW.imei) = '' THEN
        RETURN NEW;
    END IF;

    SELECT d.id,
           COALESCE(NEW.company_id, d.company_id),
           COALESCE(t.device_time, t.server_time, NEW.created_at),
           t.latitude, t.longitude, t.speed
      INTO v_device_id, v_company_id, v_occurred_at, v_latitude, v_longitude, v_speed
      FROM (SELECT 1) seed
      LEFT JOIN telemetry t ON t.id = NEW.telemetry_id AND t.imei = NEW.imei
      LEFT JOIN devices d ON d.imei = NEW.imei
     LIMIT 1;

    INSERT INTO telemetry_events (
        company_id, device_id, telemetry_id, source_record_id, imei,
        event_source, event_code, severity, title, message,
        latitude, longitude, speed, occurred_at, metadata, created_at
    ) VALUES (
        v_company_id, v_device_id, NEW.telemetry_id, NEW.id, NEW.imei,
        'ALERT', COALESCE(NULLIF(NEW.rule_code, ''), 'ALERT:' || NEW.id),
        COALESCE(NULLIF(NEW.severity, ''), 'WARNING'),
        COALESCE(NULLIF(NEW.title, ''), NULLIF(NEW.rule_code, ''), 'Telemetry alert'),
        COALESCE(NEW.message, ''),
        v_latitude, v_longitude, v_speed, COALESCE(v_occurred_at, NEW.created_at),
        COALESCE(NEW.metadata, '{}'::jsonb) || jsonb_strip_nulls(jsonb_build_object(
            'alertStatus', NEW.status,
            'resolvedAt', NEW.resolved_at
        )),
        NEW.created_at
    )
    ON CONFLICT (event_source, source_record_id, created_at)
        WHERE event_source = 'ALERT' AND source_record_id IS NOT NULL
    DO UPDATE SET
        company_id = EXCLUDED.company_id,
        device_id = EXCLUDED.device_id,
        telemetry_id = EXCLUDED.telemetry_id,
        event_code = EXCLUDED.event_code,
        severity = EXCLUDED.severity,
        title = EXCLUDED.title,
        message = EXCLUDED.message,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        speed = EXCLUDED.speed,
        occurred_at = EXCLUDED.occurred_at,
        metadata = EXCLUDED.metadata;

    RETURN NEW;
END;
$$;

-- Backfill already received device-originated Event IO records. Prefer the
-- direct-pipeline telemetry_io metadata when available, otherwise use the
-- dictionary mapping or a deterministic Event IO label.
INSERT INTO telemetry_events (
    company_id, device_id, telemetry_id, imei,
    event_source, event_code, event_io_id, io_name,
    raw_value, numeric_value, real_value, unit,
    severity, title, message,
    latitude, longitude, speed, occurred_at, metadata, created_at
)
SELECT d.company_id,
       d.id,
       t.id,
       t.imei,
       'DEVICE_IO',
       'IO:' || t.event_io_id::TEXT,
       t.event_io_id::TEXT,
       COALESCE(NULLIF(io.io_name, ''), NULLIF(mapping.source_name, '')),
       COALESCE(io.raw_value, t.io_data ->> t.event_io_id::TEXT, t.io ->> t.event_io_id::TEXT),
       io.numeric_value,
       io.real_value,
       COALESCE(NULLIF(io.unit, ''), NULLIF(mapping.source_unit, '')),
       'INFO',
       COALESCE(NULLIF(io.io_name, ''), NULLIF(mapping.source_name, ''), 'Event IO ' || t.event_io_id::TEXT),
       COALESCE(NULLIF(io.io_name, ''), NULLIF(mapping.source_name, ''), 'Event IO ' || t.event_io_id::TEXT)
           || ' = ' || COALESCE(io.real_value::TEXT, io.numeric_value::TEXT, io.raw_value,
                                t.io_data ->> t.event_io_id::TEXT, t.io ->> t.event_io_id::TEXT, '-'),
       t.latitude,
       t.longitude,
       t.speed,
       COALESCE(t.device_time, t.server_time, t.created_at),
       jsonb_strip_nulls(jsonb_build_object(
           'dictionaryCode', t.dictionary_code,
           'protocol', t.protocol,
           'channel', t.channel,
           'sourceProtocol', t.source_protocol,
           'backfilled', TRUE
       )),
       t.server_time
FROM telemetry t
LEFT JOIN devices d ON d.imei = t.imei
LEFT JOIN LATERAL (
    SELECT i.io_name, i.raw_value, i.numeric_value, i.real_value, i.unit
    FROM telemetry_io i
    WHERE i.telemetry_id = t.id
      AND i.imei = t.imei
      AND i.io_id = t.event_io_id::TEXT
      AND i.is_event = TRUE
    ORDER BY i.id DESC
    LIMIT 1
) io ON TRUE
LEFT JOIN LATERAL (
    SELECT COALESCE(NULLIF(m.source_name, ''), NULLIF(m.field_name, '')) AS source_name,
           COALESCE(NULLIF(m.source_unit, ''), NULLIF(m.unit, '')) AS source_unit
    FROM device_io_mappings m
    WHERE m.status = 'ACTIVE'
      AND m.source_io_id = t.event_io_id::TEXT
      AND (m.dictionary_code = t.dictionary_code OR m.dictionary_code IS NULL)
      AND (m.source_protocol = t.source_protocol OR m.source_protocol IS NULL)
    ORDER BY CASE WHEN m.dictionary_code = t.dictionary_code THEN 0 ELSE 1 END,
             CASE WHEN m.source_protocol = t.source_protocol THEN 0 ELSE 1 END,
             m.id DESC
    LIMIT 1
) mapping ON TRUE
WHERE t.event_io_id IS NOT NULL
  AND t.event_io_id <> 0
ON CONFLICT (telemetry_id, event_source, event_io_id, created_at)
    WHERE event_source = 'DEVICE_IO' AND telemetry_id IS NOT NULL
DO NOTHING;

-- Backfill existing rule alerts into the same read model without changing the
-- telemetry_alerts alert-rule domain table.
INSERT INTO telemetry_events (
    company_id, device_id, telemetry_id, source_record_id, imei,
    event_source, event_code, severity, title, message,
    latitude, longitude, speed, occurred_at, metadata, created_at
)
SELECT COALESCE(a.company_id, d.company_id),
       d.id,
       a.telemetry_id,
       a.id,
       a.imei,
       'ALERT',
       COALESCE(NULLIF(a.rule_code, ''), 'ALERT:' || a.id),
       COALESCE(NULLIF(a.severity, ''), 'WARNING'),
       COALESCE(NULLIF(a.title, ''), NULLIF(a.rule_code, ''), 'Telemetry alert'),
       COALESCE(a.message, ''),
       t.latitude,
       t.longitude,
       t.speed,
       COALESCE(t.device_time, t.server_time, a.created_at),
       COALESCE(a.metadata, '{}'::jsonb) || jsonb_build_object('backfilled', TRUE),
       a.created_at
FROM telemetry_alerts a
LEFT JOIN telemetry t ON t.id = a.telemetry_id AND t.imei = a.imei
LEFT JOIN devices d ON d.imei = a.imei
WHERE a.imei IS NOT NULL
ON CONFLICT (event_source, source_record_id, created_at)
    WHERE event_source = 'ALERT' AND source_record_id IS NOT NULL
DO NOTHING;

DROP TRIGGER IF EXISTS trg_telemetry_materialize_device_io_event ON telemetry;
CREATE TRIGGER trg_telemetry_materialize_device_io_event
AFTER INSERT ON telemetry
FOR EACH ROW
WHEN (NEW.event_io_id IS NOT NULL AND NEW.event_io_id <> 0)
EXECUTE FUNCTION alels_materialize_device_io_event();

CREATE OR REPLACE FUNCTION alels_materialize_vehicle_status_event()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE previous_status TEXT;
BEGIN
  IF NEW.vehicle_status IS NULL OR NEW.vehicle_status NOT IN ('STOP','IDLE','TRIP') THEN RETURN NEW; END IF;
  SELECT vehicle_status INTO previous_status FROM telemetry WHERE imei=NEW.imei AND id<>NEW.id ORDER BY server_time DESC,id DESC LIMIT 1;
  IF previous_status IS DISTINCT FROM NEW.vehicle_status THEN
    INSERT INTO telemetry_events(company_id,device_id,telemetry_id,imei,event_source,event_code,event_io_id,io_name,raw_value,severity,title,message,latitude,longitude,speed,occurred_at,metadata,created_at)
    SELECT d.company_id,d.id,NEW.id,NEW.imei,'VEHICLE_STATUS','STATUS:'||NEW.vehicle_status,NULL,'Vehicle Status',NEW.vehicle_status,'INFO','Vehicle Status '||NEW.vehicle_status,'Vehicle Status = '||NEW.vehicle_status,NEW.latitude,NEW.longitude,NEW.speed,COALESCE(NEW.device_time,NEW.server_time,NOW()),jsonb_build_object('status',NEW.vehicle_status),NEW.server_time FROM devices d WHERE d.imei=NEW.imei LIMIT 1;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_telemetry_materialize_vehicle_status_event ON telemetry;
CREATE TRIGGER trg_telemetry_materialize_vehicle_status_event AFTER INSERT ON telemetry FOR EACH ROW EXECUTE FUNCTION alels_materialize_vehicle_status_event();

DROP TRIGGER IF EXISTS trg_telemetry_io_enrich_device_event ON telemetry_io;
CREATE TRIGGER trg_telemetry_io_enrich_device_event
AFTER INSERT OR UPDATE OF io_name, raw_value, numeric_value, real_value, unit, is_event ON telemetry_io
FOR EACH ROW
WHEN (NEW.is_event = TRUE)
EXECUTE FUNCTION alels_enrich_device_io_event();

DROP TRIGGER IF EXISTS trg_telemetry_alert_materialize_event ON telemetry_alerts;
CREATE TRIGGER trg_telemetry_alert_materialize_event
AFTER INSERT OR UPDATE OF company_id, telemetry_id, imei, rule_code, severity, status, title, message, metadata, resolved_at
ON telemetry_alerts
FOR EACH ROW
EXECUTE FUNCTION alels_materialize_alert_event();
