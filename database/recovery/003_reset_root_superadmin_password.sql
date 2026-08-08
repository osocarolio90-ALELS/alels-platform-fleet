-- ALELS Emergency Recovery: reset root superadmin password
-- Required psql variable: new_password_hash (BCrypt generated with backend/BcryptTool.java).
-- Example: psql ... -v new_password_hash='<bcrypt-hash>' -f this-file.sql
-- Safe to rerun. This is not a migration.

\if :{?new_password_hash}
\else
\echo 'ERROR: new_password_hash psql variable is required'
\quit
\endif

BEGIN;

WITH target_user AS (
    UPDATE users
    SET password_hash = :'new_password_hash',
        must_change_password = TRUE,
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
