-- ALELS Emergency Recovery: reset root superadmin password
-- Default email: osocarolio90@gmail.com
-- Default password for the hash below: alels1234567
-- Generate a new hash with backend/BcryptTool.java if you need a different password.
-- Safe to rerun. This is not a migration.

BEGIN;

WITH target_user AS (
    UPDATE users
    SET password_hash = '$2a$12$11WIGjtlvg82AJqFKZVZ8e7SGcS2v8//N8uTW.ropBp0yoNzTZzOe',
        must_change_password = FALSE,
        status = 'ACTIVE',
        deleted_at = NULL,
        deleted_by = NULL,
        deleted_reason = NULL,
        delete_permanent_at = NULL,
        suspended_at = NULL,
        suspended_by = NULL,
        session_version = session_version + 1,
        updated_at = NOW()
    WHERE lower(email) = lower('osocarolio90@gmail.com')
      AND upper(role) = 'SUPERADMIN'
    RETURNING id, company_id, email, role
)
INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
SELECT NULL,
       company_id,
       'USER',
       id,
       'RECOVERY_SUPERADMIN_PASSWORD_RESET',
       jsonb_build_object('email', email, 'role', role, 'source', 'database/recovery/003_reset_root_superadmin_password.sql')
FROM target_user;

COMMIT;
