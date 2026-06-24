-- ALELS 1M FOUNDATION MODE
-- Consolidated development migration 006 for Organization registration, RBAC visibility, lifecycle, wasted, and storage quota.
-- Safe to re-run while ALELS is still local/development.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE companies ADD COLUMN IF NOT EXISTS plan VARCHAR(80) NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS month_packet INTEGER NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS country VARCHAR(120) NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS storage_quota_mb BIGINT NOT NULL DEFAULT 0;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS first_login_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS suspended_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS created_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS deleted_reason TEXT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS first_login_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_reason TEXT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_path VARCHAR(512) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_content_type VARCHAR(120) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS company_types (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(80) NOT NULL UNIQUE,
    type_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO company_types (type_code, type_name)
VALUES
    ('RENT', 'Rent'),
    ('MINING', 'Mining'),
    ('PLANTATION', 'Plantation'),
    ('MARINE', 'Marine'),
    ('FNB', 'FnB'),
    ('MANUFACTURE', 'Manufacture'),
    ('LOGISTIC', 'Logistic')
ON CONFLICT (type_code) DO UPDATE SET type_name = EXCLUDED.type_name, status = 'ACTIVE', updated_at = NOW();

CREATE TABLE IF NOT EXISTS organization_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    actor_company_id BIGINT NULL REFERENCES companies(id) ON DELETE SET NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_org_activity_actor_time ON organization_activity_logs(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_org_activity_target_time ON organization_activity_logs(target_type, target_id, created_at DESC);

UPDATE companies
SET company_name = UPPER(TRIM(company_name)),
    company_type = CASE WHEN company_type IS NULL THEN company_type ELSE UPPER(TRIM(company_type)) END,
    plan = CASE WHEN plan IS NULL THEN plan ELSE UPPER(TRIM(plan)) END,
    storage_quota_mb = CASE
        WHEN company_code = 'ALELS_TECH_INDONESIA' THEN 0
        WHEN UPPER(COALESCE(plan, '')) = 'SAMPLE' THEN 100
        WHEN storage_quota_mb IS NULL OR storage_quota_mb < 0 THEN 0
        ELSE storage_quota_mb
    END,
    month_packet = CASE
        WHEN UPPER(COALESCE(plan, '')) = 'SAMPLE' THEN 1
        ELSE month_packet
    END,
    updated_at = NOW()
WHERE company_name IS NOT NULL;

UPDATE companies
SET company_type = 'ALELS',
    plan = 'ALELS',
    month_packet = NULL,
    storage_quota_mb = 0,
    started_at = NULL,
    expired_at = NULL,
    country = COALESCE(country, 'Indonesia'),
    parent_company_id = NULL,
    status = 'ACTIVE',
    subscription_status = 'INTERNAL',
    is_internal = TRUE,
    updated_at = NOW()
WHERE company_code = 'ALELS_TECH_INDONESIA';

CREATE INDEX IF NOT EXISTS idx_companies_parent_status ON companies(parent_company_id, status);
CREATE INDEX IF NOT EXISTS idx_companies_plan ON companies(plan);
CREATE INDEX IF NOT EXISTS idx_companies_country ON companies(country);
CREATE INDEX IF NOT EXISTS idx_companies_month_packet ON companies(month_packet);
CREATE INDEX IF NOT EXISTS idx_companies_storage_quota_mb ON companies(storage_quota_mb);
CREATE INDEX IF NOT EXISTS idx_companies_company_type_upper ON companies(UPPER(company_type));
CREATE INDEX IF NOT EXISTS idx_companies_deleted_permanent_at ON companies(delete_permanent_at) WHERE deleted_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_companies_company_name_global_active ON companies(UPPER(company_name)) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_company_role_status ON users(company_id, role, status);
CREATE INDEX IF NOT EXISTS idx_users_deleted_permanent_at ON users(delete_permanent_at) WHERE deleted_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_global_active ON users(LOWER(email)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS company_storage_usage (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    storage_used_mb BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_company_storage_usage_calculated_at ON company_storage_usage(calculated_at DESC);


CREATE OR REPLACE FUNCTION alels_company_storage_used_mb(target_company_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    total_bytes NUMERIC := 0;
    table_bytes NUMERIC := 0;
BEGIN
    IF target_company_id IS NULL THEN
        RETURN 0;
    END IF;

    WITH RECURSIVE company_tree AS (
        SELECT id FROM companies WHERE id = target_company_id
        UNION ALL
        SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
    )
    SELECT COALESCE(SUM(pg_column_size(c.*)), 0) INTO table_bytes
    FROM companies c WHERE c.id IN (SELECT id FROM company_tree);
    total_bytes := total_bytes + COALESCE(table_bytes, 0);

    WITH RECURSIVE company_tree AS (
        SELECT id FROM companies WHERE id = target_company_id
        UNION ALL
        SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
    )
    SELECT COALESCE(SUM(pg_column_size(u.*)), 0) INTO table_bytes
    FROM users u WHERE u.company_id IN (SELECT id FROM company_tree);
    total_bytes := total_bytes + COALESCE(table_bytes, 0);

    IF to_regclass('public.devices') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'company_id') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        )
        SELECT COALESCE(SUM(pg_column_size(d.*)), 0) INTO table_bytes
        FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.vehicles') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'vehicles' AND column_name = 'company_id') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        )
        SELECT COALESCE(SUM(pg_column_size(v.*)), 0) INTO table_bytes
        FROM vehicles v WHERE v.company_id IN (SELECT id FROM company_tree);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.telemetry') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'telemetry' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(t.*)), 0) INTO table_bytes
        FROM telemetry t WHERE t.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.raw_packets') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'raw_packets' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(r.*)), 0) INTO table_bytes
        FROM raw_packets r WHERE r.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.tcp_logs') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'tcp_logs' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(l.*)), 0) INTO table_bytes
        FROM tcp_logs l WHERE l.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.command_queue') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'command_queue' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(q.*)), 0) INTO table_bytes
        FROM command_queue q WHERE q.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.command_responses') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'command_responses' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(cr.*)), 0) INTO table_bytes
        FROM command_responses cr WHERE cr.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    IF to_regclass('public.device_receive_status') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'device_receive_status' AND column_name = 'imei') THEN
        WITH RECURSIVE company_tree AS (
            SELECT id FROM companies WHERE id = target_company_id
            UNION ALL
            SELECT c.id FROM companies c JOIN company_tree t ON c.parent_company_id = t.id
        ), device_imeis AS (
            SELECT d.imei FROM devices d WHERE d.company_id IN (SELECT id FROM company_tree)
        )
        SELECT COALESCE(SUM(pg_column_size(s.*)), 0) INTO table_bytes
        FROM device_receive_status s WHERE s.imei IN (SELECT imei FROM device_imeis);
        total_bytes := total_bytes + COALESCE(table_bytes, 0);
    END IF;

    RETURN CEIL(total_bytes / 1000000.0)::BIGINT;
END;
$$;

CREATE OR REPLACE FUNCTION alels_refresh_company_storage_usage(target_company_id BIGINT DEFAULT NULL)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    refreshed_count BIGINT := 0;
BEGIN
    INSERT INTO company_storage_usage (company_id, storage_used_mb, calculated_at)
    SELECT c.id, alels_company_storage_used_mb(c.id), NOW()
    FROM companies c
    WHERE target_company_id IS NULL OR c.id = target_company_id
    ON CONFLICT (company_id) DO UPDATE SET
        storage_used_mb = EXCLUDED.storage_used_mb,
        calculated_at = EXCLUDED.calculated_at;

    GET DIAGNOSTICS refreshed_count = ROW_COUNT;
    RETURN refreshed_count;
END;
$$;

CREATE OR REPLACE FUNCTION alels_purge_organization_wasted()
RETURNS TABLE(deleted_companies BIGINT, deleted_users BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    company_count BIGINT := 0;
    user_count BIGINT := 0;
BEGIN
    WITH doomed_users AS (
        DELETE FROM users
        WHERE deleted_at IS NOT NULL
          AND delete_permanent_at IS NOT NULL
          AND delete_permanent_at <= NOW()
        RETURNING id
    )
    SELECT COUNT(*) INTO user_count FROM doomed_users;

    WITH RECURSIVE doomed_companies AS (
        SELECT id FROM companies
        WHERE deleted_at IS NOT NULL
          AND delete_permanent_at IS NOT NULL
          AND delete_permanent_at <= NOW()
          AND company_code <> 'ALELS_TECH_INDONESIA'
        UNION ALL
        SELECT c.id FROM companies c JOIN doomed_companies d ON c.parent_company_id = d.id
    ), deleted_child_users AS (
        DELETE FROM users WHERE company_id IN (SELECT id FROM doomed_companies) RETURNING id
    ), deleted_companies_rows AS (
        DELETE FROM companies WHERE id IN (SELECT id FROM doomed_companies) RETURNING id
    )
    SELECT COUNT(*) INTO company_count FROM deleted_companies_rows;

    deleted_companies := company_count;
    deleted_users := user_count;
    RETURN NEXT;
END;
$$;

DROP VIEW IF EXISTS public.v_organization_companies CASCADE;
DROP VIEW IF EXISTS public.v_organization_users CASCADE;
DROP VIEW IF EXISTS public.v_organization_wasted CASCADE;

CREATE VIEW v_organization_companies AS
SELECT
    c.id,
    c.parent_company_id,
    COALESCE(p.company_name, CASE WHEN c.company_code = 'ALELS_TECH_INDONESIA' THEN c.company_name ELSE 'ALELS TECH INDONESIA' END)::VARCHAR(255) AS parent_company_name,
    UPPER(c.company_name)::VARCHAR(255) AS company_name,
    c.company_code,
    (CASE WHEN c.company_code = 'ALELS_TECH_INDONESIA' THEN 'ALELS' ELSE COALESCE(c.company_type, '-') END)::VARCHAR(120) AS company_type,
    c.plan,
    c.month_packet,
    COALESCE(c.storage_quota_mb, 0)::BIGINT AS storage_quota_mb,
    COALESCE(su.storage_used_mb, 0)::BIGINT AS storage_used_mb,
    c.country,
    c.status,
    c.subscription_status,
    c.is_internal,
    c.first_login_at,
    c.started_at,
    c.expired_at,
    c.created_at,
    c.created_by,
    cu.email::VARCHAR(255) AS created_by_email,
    COALESCE(cu.full_name, cu.username)::VARCHAR(255) AS created_by_name,
    c.updated_at,
    c.deleted_at,
    c.deleted_by,
    c.delete_permanent_at,
    GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(c.delete_permanent_at, c.deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
    c.deleted_reason,
    COUNT(u.id) FILTER (WHERE u.deleted_at IS NULL) AS active_user_count
FROM companies c
LEFT JOIN companies p ON p.id = c.parent_company_id
LEFT JOIN users u ON u.company_id = c.id
LEFT JOIN users cu ON cu.id = c.created_by
LEFT JOIN company_storage_usage su ON su.company_id = c.id
GROUP BY c.id, p.company_name, cu.email, cu.full_name, cu.username, su.storage_used_mb;

CREATE VIEW v_organization_users AS
SELECT
    u.id,
    u.company_id,
    UPPER(c.company_name)::VARCHAR(255) AS company_name,
    UPPER(COALESCE(p.company_name, CASE WHEN c.company_code = 'ALELS_TECH_INDONESIA' THEN c.company_name ELSE 'ALELS TECH INDONESIA' END))::VARCHAR(255) AS parent_company_name,
    u.username,
    u.full_name,
    u.email,
    UPPER(REPLACE(COALESCE(u.role, 'CLIENTUSER'), '_', '')) AS role_normalized,
    u.role,
    u.status,
    u.first_login_at,
    u.last_login_at,
    u.created_at,
    u.created_by,
    cu.email::VARCHAR(255) AS created_by_email,
    COALESCE(cu.full_name, cu.username)::VARCHAR(255) AS created_by_name,
    u.updated_at,
    u.deleted_at,
    u.deleted_by,
    u.delete_permanent_at,
    GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(u.delete_permanent_at, u.deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
    u.deleted_reason,
    u.profile_photo_path,
    u.profile_photo_content_type,
    u.session_version
FROM users u
LEFT JOIN companies c ON c.id = u.company_id
LEFT JOIN companies p ON p.id = c.parent_company_id
LEFT JOIN users cu ON cu.id = u.created_by;

CREATE VIEW v_organization_wasted AS
SELECT
    'COMPANY'::VARCHAR(30) AS item_type,
    c.id,
    c.company_name::VARCHAR(255) AS name,
    c.company_name::VARCHAR(255) AS company_name,
    c.company_type::VARCHAR(120) AS role_or_type,
    c.deleted_at,
    c.delete_permanent_at,
    GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(c.delete_permanent_at, c.deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
    c.deleted_reason,
    c.deleted_by,
    du.email::VARCHAR(255) AS deleted_by_email
FROM companies c
LEFT JOIN users du ON du.id = c.deleted_by
WHERE c.deleted_at IS NOT NULL
UNION ALL
SELECT
    'USER'::VARCHAR(30) AS item_type,
    u.id,
    COALESCE(u.full_name, u.username)::VARCHAR(255) AS name,
    c.company_name::VARCHAR(255) AS company_name,
    u.role::VARCHAR(120) AS role_or_type,
    u.deleted_at,
    u.delete_permanent_at,
    GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(u.delete_permanent_at, u.deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
    u.deleted_reason,
    u.deleted_by,
    du.email::VARCHAR(255) AS deleted_by_email
FROM users u
LEFT JOIN companies c ON c.id = u.company_id
LEFT JOIN users du ON du.id = u.deleted_by
WHERE u.deleted_at IS NOT NULL;

SELECT alels_refresh_company_storage_usage();
