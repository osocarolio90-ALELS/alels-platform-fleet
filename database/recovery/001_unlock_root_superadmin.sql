-- ALELS Emergency Recovery: unlock root superadmin
-- Purpose: restore login visibility for the root superadmin after accidental suspend/delete.
-- Safe to rerun. This is not a migration.

BEGIN;

WITH target_user AS (
    UPDATE users
    SET status = 'ACTIVE',
        deleted_at = NULL,
        deleted_by = NULL,
        deleted_reason = NULL,
        delete_permanent_at = NULL,
        suspended_at = NULL,
        suspended_by = NULL,
        must_change_password = FALSE,
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
       'RECOVERY_SUPERADMIN_UNLOCK',
       jsonb_build_object('email', email, 'role', role, 'source', 'database/recovery/001_unlock_root_superadmin.sql')
FROM target_user;

COMMIT;
