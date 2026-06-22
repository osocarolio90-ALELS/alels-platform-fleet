-- ALELS Emergency Recovery: restore root company
-- Purpose: make ALELS TECH INDONESIA visible/active again after accidental suspend/delete.
-- Safe to rerun. This is not a migration.

BEGIN;

WITH target_company AS (
    UPDATE companies
    SET status = 'ACTIVE',
        subscription_status = CASE
            WHEN subscription_status IN ('SUSPENDED', 'DELETED', 'PROVISION') THEN 'INTERNAL'
            ELSE subscription_status
        END,
        deleted_at = NULL,
        deleted_by = NULL,
        deleted_reason = NULL,
        delete_permanent_at = NULL,
        suspended_at = NULL,
        suspended_by = NULL,
        updated_at = NOW()
    WHERE company_code = 'ALELS_TECH_INDONESIA'
    RETURNING id, company_name, company_code
)
INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
SELECT NULL,
       id,
       'COMPANY',
       id,
       'RECOVERY_ROOT_COMPANY_RESTORE',
       jsonb_build_object('companyName', company_name, 'companyCode', company_code, 'source', 'database/recovery/002_restore_root_company.sql')
FROM target_company;

COMMIT;
