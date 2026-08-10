BEGIN;

CREATE TABLE IF NOT EXISTS telemetry_device_workspace_configs (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    company_id BIGINT REFERENCES companies(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    configuration JSONB NOT NULL DEFAULT '{"instruments":[],"bottomItems":[]}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_telemetry_workspace_device_user UNIQUE (device_id, user_id),
    CONSTRAINT chk_telemetry_workspace_configuration_object CHECK (jsonb_typeof(configuration) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_telemetry_workspace_config_company
    ON telemetry_device_workspace_configs(company_id, updated_at DESC);

COMMENT ON TABLE telemetry_device_workspace_configs IS
    'Per-user, per-device instrument mappings for the standalone telemetry workspace.';

COMMIT;
