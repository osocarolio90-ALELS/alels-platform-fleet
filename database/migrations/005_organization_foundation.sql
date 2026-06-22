-- ALELS 1M FOUNDATION MODE
-- Organization domain foundation: company hierarchy, user roles, status views.
-- This migration is intentionally additive and idempotent.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE companies ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS created_by BIGINT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS updated_by BIGINT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS deleted_reason TEXT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS approved_by BIGINT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS rejected_by BIGINT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS rejected_reason TEXT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_reason TEXT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_by BIGINT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejected_by BIGINT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejected_reason TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_companies_started_expired ON companies(started_at, expired_at);
CREATE INDEX IF NOT EXISTS idx_companies_subscription_status ON companies(subscription_status);
CREATE INDEX IF NOT EXISTS idx_companies_delete_permanent_at ON companies(delete_permanent_at);
CREATE INDEX IF NOT EXISTS idx_users_delete_permanent_at ON users(delete_permanent_at);

INSERT INTO companies (company_name, company_code, company_type, status, subscription_status, is_internal, parent_company_id, started_at, expired_at)
VALUES ('ALELS TECH INDONESIA', 'ALELS_TECH_INDONESIA', 'ROOT', 'ACTIVE', 'INTERNAL', TRUE, NULL, NULL, NULL)
ON CONFLICT (company_code) DO UPDATE SET
    company_name = EXCLUDED.company_name,
    company_type = 'ROOT',
    parent_company_id = NULL,
    status = 'ACTIVE',
    subscription_status = 'INTERNAL',
    is_internal = TRUE,
    started_at = NULL,
    expired_at = NULL,
    updated_at = NOW();

UPDATE companies
SET parent_company_id = (SELECT id FROM companies WHERE company_code = 'ALELS_TECH_INDONESIA'),
    updated_at = NOW()
WHERE company_code <> 'ALELS_TECH_INDONESIA'
  AND parent_company_id IS NULL
  AND deleted_at IS NULL;

UPDATE users u
SET company_id = (SELECT id FROM companies WHERE company_code = 'ALELS_TECH_INDONESIA'),
    updated_at = NOW()
WHERE UPPER(COALESCE(u.role, '')) IN ('SUPERADMIN', 'ADMIN', 'ALELS_SUPER_ADMIN')
  AND u.deleted_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role_alels_standard'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_role_alels_standard
        CHECK (UPPER(role) IN ('SUPERADMIN', 'ADMIN', 'OWNER', 'MANAGER', 'TECHUSER', 'TECH_USER', 'CLIENTUSER', 'CLIENT_USER'));
    END IF;
END $$;

DROP VIEW IF EXISTS v_organization_users CASCADE;
DROP VIEW IF EXISTS v_organization_companies CASCADE;

CREATE VIEW v_organization_companies AS
SELECT
    c.id,
    c.parent_company_id,
    COALESCE(p.company_name, CASE WHEN c.company_code = 'ALELS_TECH_INDONESIA' THEN 'ROOT' ELSE 'ALELS TECH INDONESIA' END) AS parent_company_name,
    c.company_name,
    c.company_code,
    c.company_type,
    c.status,
    c.subscription_status,
    c.is_internal,
    c.started_at,
    c.expired_at,
    c.created_at,
    c.updated_at,
    c.deleted_at,
    c.delete_permanent_at,
    c.deleted_reason,
    COUNT(u.id) FILTER (WHERE u.deleted_at IS NULL) AS active_user_count
FROM companies c
LEFT JOIN companies p ON p.id = c.parent_company_id
LEFT JOIN users u ON u.company_id = c.id
GROUP BY c.id, p.company_name;

CREATE VIEW v_organization_users AS
SELECT
    u.id,
    u.company_id,
    c.company_name,
    p.company_name AS parent_company_name,
    u.username,
    u.full_name,
    u.email,
    UPPER(REPLACE(COALESCE(u.role, 'CLIENTUSER'), '_', '')) AS role_normalized,
    u.role,
    u.status,
    u.last_login_at,
    u.created_at,
    u.updated_at,
    u.deleted_at,
    u.delete_permanent_at,
    u.deleted_reason
FROM users u
LEFT JOIN companies c ON c.id = u.company_id
LEFT JOIN companies p ON p.id = c.parent_company_id;
