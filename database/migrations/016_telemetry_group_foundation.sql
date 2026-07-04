CREATE TABLE IF NOT EXISTS telemetry_groups (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    group_name VARCHAR(160) NOT NULL,
    description TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    delete_permanent_at TIMESTAMPTZ,
    deleted_reason TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_groups_company_name_active
    ON telemetry_groups(company_id, UPPER(TRIM(group_name))) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_telemetry_groups_company_deleted
    ON telemetry_groups(company_id, deleted_at, created_at DESC);

CREATE TABLE IF NOT EXISTS telemetry_group_devices (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES telemetry_groups(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, device_id),
    UNIQUE(device_id)
);

ALTER TABLE telemetry_group_devices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE telemetry_group_devices ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE telemetry_group_devices DROP CONSTRAINT IF EXISTS telemetry_group_devices_device_id_key;
DROP INDEX IF EXISTS uq_telemetry_group_devices_device_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_group_devices_device_any
    ON telemetry_group_devices(device_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_group_devices_group ON telemetry_group_devices(group_id);

CREATE TABLE IF NOT EXISTS telemetry_group_logs (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    group_name VARCHAR(160) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(60) NOT NULL,
    device_count INTEGER NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_telemetry_group_logs_time ON telemetry_group_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_group_logs_company ON telemetry_group_logs(company_id, created_at DESC);
