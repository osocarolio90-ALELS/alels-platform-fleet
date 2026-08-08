INSERT INTO companies (
    id, company_code, company_name, company_type, plan, country, status,
    subscription_status, month_packet, device_limit, max_users, storage_size,
    storage_unit, is_internal, activated_at, start_at, expired_at
)
VALUES (
    1, 'ALELS_TECH_INDONESIA', 'ALELS TECH INDONESIA', 'OWNER', 'ENTERPRISE',
    'Indonesia', 'ACTIVE', 'ACTIVE', 0, 1000000, 100000, 1024,
    'GB', TRUE, NOW(), NOW(), NOW() + INTERVAL '10 years'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (
    id, company_id, username, email, password_hash, full_name, role, status, must_change_password
)
VALUES (
    1, 1, 'superadmin', 'superadmin@alels.local', 'TEMP_OWNER_PASSWORD', 'ALELS Super Admin', 'SUPERADMIN', 'ACTIVE', TRUE
)
ON CONFLICT (id) DO UPDATE SET
    company_id = EXCLUDED.company_id,
    username = EXCLUDED.username,
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    must_change_password = EXCLUDED.must_change_password;

INSERT INTO company_roles (company_id, role_code, enabled)
VALUES
    (1, 'SUPERADMIN', TRUE),
    (1, 'ADMIN', TRUE),
    (1, 'OWNER', TRUE),
    (1, 'MANAGER', TRUE),
    (1, 'TECHUSER', TRUE),
    (1, 'CLIENT_USER', TRUE)
ON CONFLICT (company_id, role_code) DO UPDATE SET enabled = EXCLUDED.enabled;

INSERT INTO device_models (brand_code, brand_name, model_code, model_name, protocol_code, dictionary_code)
VALUES
    ('TELTONIKA', 'Teltonika', 'FMC650', 'FMC650', 'TELTONIKA_CODEC8E', 'TELTONIKA_FMS'),
    ('TELTONIKA', 'Teltonika', 'FTC921', 'FTC921', 'TELTONIKA_CODEC8E', 'TELTONIKA_BASIC'),
    ('ALELS', 'ALELS', 'ALELS_JSON', 'ALELS JSON Device', 'ALELS_JSON', 'ALELS_COMPACT_JSON')
ON CONFLICT (model_code) DO NOTHING;

SELECT setval('companies_id_seq', GREATEST((SELECT MAX(id) FROM companies), 1));
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 1));
