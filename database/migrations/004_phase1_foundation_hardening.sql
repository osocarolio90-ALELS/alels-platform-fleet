-- ALELS Phase-1 Foundation Hardening
-- Goal: make the runtime pipeline safe to operate before adding new feature menus.
-- Scope:
-- 1. Clean partition helper behaviour from 003 so it never tries to partition non-partitioned tables.
-- 2. Maintain rolling future partitions for telemetry, raw_packets and tcp_logs.
-- 3. Add retention policy metadata and safe retention function.
-- 4. Add durable presence/latest-position tables used together with Redis cache.
-- 5. Add command queue retry/lease hardening.
-- 6. Add Kafka topic strategy metadata for 1M-device planning.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------
-- Runtime settings
-- -----------------------------
CREATE TABLE IF NOT EXISTS alels_runtime_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO alels_runtime_settings (setting_key, setting_value, description)
VALUES
    ('partition.months_ahead', '12', 'How many monthly partitions must exist ahead of current month.'),
    ('retention.telemetry_months', '12', 'Default hot telemetry retention in PostgreSQL before archive/drop.'),
    ('retention.raw_packets_months', '3', 'Default raw packet retention in PostgreSQL.'),
    ('retention.tcp_logs_months', '3', 'Default TCP log retention in PostgreSQL.'),
    ('presence.timeout_seconds', '420', 'Device offline threshold. 420 seconds = 7 minutes.'),
    ('redis.presence.ttl_seconds', '420', 'Redis key TTL for device presence/latest position cache.'),
    ('kafka.telemetry.raw.partitions.target_1m', '384', 'Recommended starting target for telemetry.raw at 1M active devices; tune after benchmark.'),
    ('kafka.telemetry.parsed.partitions.target_1m', '384', 'Recommended starting target for telemetry.parsed at 1M active devices; tune after benchmark.'),
    ('kafka.telemetry.dlq.partitions.target_1m', '48', 'Recommended target for DLQ topic partitions at scale.')
ON CONFLICT (setting_key)
DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    description = EXCLUDED.description,
    updated_at = NOW();

CREATE OR REPLACE FUNCTION alels_setting_int(key text, fallback integer)
RETURNS integer
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    value_text text;
BEGIN
    SELECT setting_value INTO value_text
    FROM alels_runtime_settings
    WHERE setting_key = key;

    IF value_text IS NULL OR value_text !~ '^[0-9]+$' THEN
        RETURN fallback;
    END IF;

    RETURN value_text::integer;
END;
$$;

-- -----------------------------
-- Safe partition helpers
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

CREATE OR REPLACE FUNCTION alels_is_partitioned(table_name text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    table_oid oid;
BEGIN
    table_oid := to_regclass(table_name);
    IF table_oid IS NULL THEN
        RETURN FALSE;
    END IF;

    RETURN EXISTS (
        SELECT 1
        FROM pg_partitioned_table
        WHERE partrelid = table_oid
    );
END;
$$;

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
    IF NOT EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = parent_table) THEN
        RAISE NOTICE 'Skip %. Parent table is not partitioned.', parent_table::text;
        RETURN;
    END IF;

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
        p_name,
        parent_table,
        p_from,
        p_to
    );
END;
$$;

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
    END LOOP;
END;
$$;

CREATE TABLE IF NOT EXISTS partition_maintenance_runs (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    months_ahead INTEGER,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE OR REPLACE FUNCTION alels_run_partition_maintenance(months_ahead integer DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    run_id bigint;
BEGIN
    INSERT INTO partition_maintenance_runs(job_name, status, months_ahead, started_at)
    VALUES ('runtime_monthly_partition_create', 'RUNNING', COALESCE(months_ahead, alels_setting_int('partition.months_ahead', 12)), NOW())
    RETURNING id INTO run_id;

    PERFORM alels_ensure_runtime_partitions(months_ahead);

    UPDATE partition_maintenance_runs
    SET status = 'SUCCESS', finished_at = NOW()
    WHERE id = run_id;
EXCEPTION WHEN OTHERS THEN
    UPDATE partition_maintenance_runs
    SET status = 'FAILED', finished_at = NOW(), error_message = SQLERRM
    WHERE id = run_id;
    RAISE;
END;
$$;

-- Create 12 months ahead now. This is idempotent.
SELECT alels_run_partition_maintenance(12);

-- -----------------------------
-- Retention policy foundation
-- -----------------------------
CREATE TABLE IF NOT EXISTS data_retention_policies (
    table_name VARCHAR(120) PRIMARY KEY,
    retention_months INTEGER NOT NULL,
    retention_action VARCHAR(40) NOT NULL DEFAULT 'DROP_PARTITION',
    archive_required BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO data_retention_policies (table_name, retention_months, retention_action, archive_required, enabled, notes)
VALUES
    ('telemetry', alels_setting_int('retention.telemetry_months', 12), 'DROP_PARTITION', TRUE, FALSE, 'Keep disabled until cold archive/export is implemented and verified.'),
    ('raw_packets', alels_setting_int('retention.raw_packets_months', 3), 'DROP_PARTITION', TRUE, FALSE, 'Enable after raw packet audit/archive policy is approved.'),
    ('tcp_logs', alels_setting_int('retention.tcp_logs_months', 3), 'DROP_PARTITION', TRUE, FALSE, 'Enable after operations log retention policy is approved.')
ON CONFLICT (table_name)
DO UPDATE SET
    retention_months = EXCLUDED.retention_months,
    retention_action = EXCLUDED.retention_action,
    archive_required = EXCLUDED.archive_required,
    notes = EXCLUDED.notes,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS data_retention_runs (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(120) NOT NULL,
    partition_name VARCHAR(120),
    action VARCHAR(40) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(40) NOT NULL,
    cutoff_before TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE OR REPLACE FUNCTION alels_partition_month_from_name(partition_name text)
RETURNS timestamptz
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    suffix text;
BEGIN
    suffix := substring(partition_name from '([0-9]{6})$');
    IF suffix IS NULL THEN
        RETURN NULL;
    END IF;

    RETURN to_timestamp(suffix || '01', 'YYYYMMDD')::timestamptz;
END;
$$;

CREATE OR REPLACE FUNCTION alels_apply_retention(target_table text, dry_run boolean DEFAULT TRUE)
RETURNS TABLE(partition_name text, action_taken text)
LANGUAGE plpgsql
AS $$
DECLARE
    policy record;
    child record;
    cutoff timestamptz;
    partition_month timestamptz;
BEGIN
    SELECT * INTO policy
    FROM data_retention_policies
    WHERE table_name = target_table;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No retention policy found for %', target_table;
    END IF;

    cutoff := alels_month_start(NOW() - (policy.retention_months || ' month')::interval);

    FOR child IN
        SELECT c.relname AS child_name
        FROM pg_inherits i
        JOIN pg_class p ON p.oid = i.inhparent
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE p.relname = target_table
        ORDER BY c.relname
    LOOP
        partition_month := alels_partition_month_from_name(child.child_name);
        IF partition_month IS NULL OR partition_month >= cutoff THEN
            CONTINUE;
        END IF;

        INSERT INTO data_retention_runs(table_name, partition_name, action, dry_run, status, cutoff_before, started_at)
        VALUES(target_table, child.child_name, policy.retention_action, dry_run, 'RUNNING', cutoff, NOW());

        IF dry_run OR NOT policy.enabled THEN
            UPDATE data_retention_runs
            SET status = CASE WHEN policy.enabled THEN 'DRY_RUN' ELSE 'POLICY_DISABLED' END,
                finished_at = NOW()
            WHERE id = currval('data_retention_runs_id_seq');
            partition_name := child.child_name;
            action_taken := CASE WHEN policy.enabled THEN 'DRY_RUN' ELSE 'POLICY_DISABLED' END;
            RETURN NEXT;
        ELSE
            EXECUTE format('DROP TABLE IF EXISTS %I', child.child_name);
            UPDATE data_retention_runs
            SET status = 'SUCCESS', finished_at = NOW()
            WHERE id = currval('data_retention_runs_id_seq');
            partition_name := child.child_name;
            action_taken := 'DROPPED';
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------
-- Presence/latest-position foundation
-- -----------------------------
CREATE TABLE IF NOT EXISTS device_presence_cache_shadow (
    imei VARCHAR(32) PRIMARY KEY,
    company_id BIGINT,
    vehicle_id BIGINT,
    presence_status VARCHAR(40) NOT NULL DEFAULT 'OFFLINE',
    active_channel VARCHAR(40),
    last_protocol VARCHAR(80),
    last_seen_at TIMESTAMPTZ,
    offline_after TIMESTAMPTZ,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    angle INTEGER,
    satellites INTEGER,
    device_time TIMESTAMPTZ,
    server_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    redis_key VARCHAR(160),
    redis_ttl_seconds INTEGER NOT NULL DEFAULT 420,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_presence_shadow_status ON device_presence_cache_shadow(presence_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_presence_shadow_company ON device_presence_cache_shadow(company_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_presence_shadow_offline_after ON device_presence_cache_shadow(offline_after);

CREATE TABLE IF NOT EXISTS device_latest_position (
    imei VARCHAR(32) PRIMARY KEY,
    company_id BIGINT,
    vehicle_id BIGINT,
    telemetry_id BIGINT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    angle INTEGER,
    altitude INTEGER,
    satellites INTEGER,
    hdop DOUBLE PRECISION,
    vehicle_status VARCHAR(40),
    device_time TIMESTAMPTZ,
    server_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_latest_position_company ON device_latest_position(company_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_latest_position_server_time ON device_latest_position(server_time DESC);

CREATE OR REPLACE FUNCTION alels_mark_stale_devices_offline(now_value timestamptz DEFAULT NOW())
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    changed integer := 0;
BEGIN
    UPDATE devices
    SET online = FALSE,
        presence_status = 'OFFLINE',
        status_updated_at = now_value,
        updated_at = now_value
    WHERE online = TRUE
      AND last_seen IS NOT NULL
      AND last_seen < now_value - (COALESCE(presence_timeout_seconds, alels_setting_int('presence.timeout_seconds', 420)) || ' second')::interval;

    GET DIAGNOSTICS changed = ROW_COUNT;

    UPDATE device_presence_cache_shadow shadow
    SET presence_status = 'OFFLINE',
        updated_at = now_value
    WHERE shadow.presence_status <> 'OFFLINE'
      AND shadow.offline_after IS NOT NULL
      AND shadow.offline_after <= now_value;

    RETURN changed;
END;
$$;

-- -----------------------------
-- Command queue hardening
-- -----------------------------
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 3;
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(120);
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE command_queue ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_command_queue_dispatch_ready
ON command_queue(status, next_attempt_at, priority, created_at)
WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX IF NOT EXISTS idx_command_queue_lease
ON command_queue(lease_until)
WHERE lease_until IS NOT NULL;

CREATE OR REPLACE FUNCTION alels_requeue_expired_command_leases(now_value timestamptz DEFAULT NOW())
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    changed integer := 0;
BEGIN
    UPDATE command_queue
    SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'RETRY' END,
        lease_owner = NULL,
        lease_until = NULL,
        next_attempt_at = CASE WHEN attempt_count >= max_attempts THEN next_attempt_at ELSE now_value + interval '10 seconds' END,
        updated_at = now_value
    WHERE status = 'SENDING'
      AND lease_until IS NOT NULL
      AND lease_until < now_value;

    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END;
$$;

-- -----------------------------
-- Kafka topic contract hardening
-- -----------------------------
ALTER TABLE kafka_topic_contracts ADD COLUMN IF NOT EXISTS target_partitions_1m INTEGER;
ALTER TABLE kafka_topic_contracts ADD COLUMN IF NOT EXISTS min_retention_ms BIGINT;
ALTER TABLE kafka_topic_contracts ADD COLUMN IF NOT EXISTS compaction_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kafka_topic_contracts ADD COLUMN IF NOT EXISTS notes TEXT;

INSERT INTO kafka_topic_contracts(topic_name, purpose, recommended_partitions, retention_hours, cleanup_policy, owner_module, target_partitions_1m, min_retention_ms, notes)
VALUES
    ('telemetry.raw', 'Raw normalized telemetry events from Java Netty Gateway before DB ingestion. Keep key=imei for per-device order.', 96, 72, 'delete', 'gateway', 384, 259200000, 'Increase partitions before high-scale rollout and validate broker CPU/disk/network.'),
    ('telemetry.parsed', 'Optional parsed telemetry event stream for future analytics consumers.', 96, 72, 'delete', 'ingestion-service', 384, 259200000, 'Use for downstream stream processing after baseline ingestion is stable.'),
    ('telemetry.dead-letter', 'Failed telemetry messages requiring replay/review.', 24, 720, 'delete', 'ingestion-service', 48, 2592000000, 'Longer retention for replay and audit.')
ON CONFLICT (topic_name)
DO UPDATE SET
    purpose = EXCLUDED.purpose,
    recommended_partitions = EXCLUDED.recommended_partitions,
    retention_hours = EXCLUDED.retention_hours,
    cleanup_policy = EXCLUDED.cleanup_policy,
    owner_module = EXCLUDED.owner_module,
    target_partitions_1m = EXCLUDED.target_partitions_1m,
    min_retention_ms = EXCLUDED.min_retention_ms,
    notes = EXCLUDED.notes,
    updated_at = NOW();

-- -----------------------------
-- Operational health views
-- -----------------------------
CREATE OR REPLACE VIEW v_alels_partition_status AS
SELECT
    p.relname AS parent_table,
    c.relname AS partition_name,
    alels_partition_month_from_name(c.relname) AS partition_month
FROM pg_inherits i
JOIN pg_class p ON p.oid = i.inhparent
JOIN pg_class c ON c.oid = i.inhrelid
WHERE p.relname IN ('telemetry', 'raw_packets', 'tcp_logs')
ORDER BY p.relname, c.relname;

CREATE OR REPLACE VIEW v_alels_pipeline_health AS
SELECT
    iph.service_name,
    iph.status,
    iph.last_heartbeat_at,
    iph.consumer_lag,
    iph.processed_last_poll,
    iph.failed_last_poll,
    COALESCE(MAX(kcls.captured_at), iph.updated_at) AS latest_lag_snapshot_at
FROM ingestion_pipeline_health iph
LEFT JOIN kafka_consumer_lag_snapshots kcls
    ON kcls.service_name = iph.service_name
GROUP BY iph.service_name, iph.status, iph.last_heartbeat_at, iph.consumer_lag,
         iph.processed_last_poll, iph.failed_last_poll, iph.updated_at;

CREATE OR REPLACE VIEW v_alels_presence_summary AS
SELECT
    presence_status,
    COUNT(*) AS total_devices,
    MAX(updated_at) AS latest_update_at
FROM device_presence_cache_shadow
GROUP BY presence_status;

