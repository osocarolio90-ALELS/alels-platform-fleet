-- ALELS 1M Foundation - user profile, avatar metadata, session guard, and scalable user/company indexes.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_photo_path VARCHAR(512),
    ADD COLUMN IF NOT EXISTS profile_photo_content_type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_users_company_status_role ON users (company_id, status, role) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_email_active_lower ON users (LOWER(email)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_companies_parent_status ON companies (parent_company_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_org_activity_actor_time ON organization_activity_logs (actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_org_activity_target_time ON organization_activity_logs (target_type, target_id, created_at DESC);

DROP VIEW IF EXISTS v_organization_users CASCADE;

CREATE VIEW v_organization_users AS
SELECT u.id,
       u.company_id,
       c.company_name,
       pc.company_name AS parent_company_name,
       u.username,
       u.full_name,
       u.email,
       u.role,
       UPPER(REPLACE(REPLACE(COALESCE(u.role, ''), '_', ''), ' ', '')) AS role_normalized,
       u.status,
       u.first_login_at,
       u.last_login_at,
       u.created_at,
       u.created_by,
       cu.full_name AS created_by_name,
       cu.email AS created_by_email,
       u.updated_at,
       u.deleted_at,
       u.delete_permanent_at,
       u.deleted_reason,
       u.profile_photo_path,
       u.profile_photo_content_type,
       u.session_version
FROM users u
LEFT JOIN companies c ON c.id = u.company_id
LEFT JOIN companies pc ON pc.id = c.parent_company_id
LEFT JOIN users cu ON cu.id = u.created_by;
