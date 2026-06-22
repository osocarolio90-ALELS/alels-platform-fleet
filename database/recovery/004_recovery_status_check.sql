-- ALELS Emergency Recovery: status check
-- This script does not change data.

SELECT current_database() AS database_name;

SELECT id,
       company_name,
       company_code,
       status,
       subscription_status,
       deleted_at,
       suspended_at
FROM companies
WHERE company_code = 'ALELS_TECH_INDONESIA';

SELECT id,
       username,
       email,
       role,
       status,
       deleted_at,
       suspended_at,
       must_change_password,
       session_version,
       company_id
FROM users
WHERE lower(email) = lower('osocarolio90@gmail.com');

SELECT action,
       target_type,
       target_id,
       details,
       created_at
FROM organization_activity_logs
WHERE action LIKE 'RECOVERY_%'
ORDER BY created_at DESC
LIMIT 20;
