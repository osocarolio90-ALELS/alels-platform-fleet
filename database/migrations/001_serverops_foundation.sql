-- ALELS serverops-only production foundation
-- Scope: minimum stable schema required by Server Operations backend after platform restart.

CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    parent_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    company_name VARCHAR(255) NOT NULL,
    company_code VARCHAR(120) UNIQUE,
    company_type VARCHAR(60) DEFAULT 'ROOT',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    subscription_status VARCHAR(40) NOT NULL DEFAULT 'INTERNAL',
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_companies_parent ON companies(parent_company_id);
CREATE INDEX IF NOT EXISTS idx_companies_status ON companies(status);
CREATE INDEX IF NOT EXISTS idx_companies_deleted_at ON companies(deleted_at);
CREATE INDEX IF NOT EXISTS idx_companies_name_lower ON companies(LOWER(company_name));

INSERT INTO companies (company_name, company_code, company_type, status, subscription_status, is_internal)
VALUES ('ALELS TECH INDONESIA', 'ALELS_TECH_INDONESIA', 'ROOT', 'ACTIVE', 'INTERNAL', TRUE)
ON CONFLICT (company_code) DO UPDATE SET
    company_name = EXCLUDED.company_name,
    company_type = EXCLUDED.company_type,
    status = EXCLUDED.status,
    subscription_status = EXCLUDED.subscription_status,
    is_internal = EXCLUDED.is_internal,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    username VARCHAR(120) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(60) NOT NULL DEFAULT 'SUPERADMIN',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    password_hash TEXT NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_users_company ON users(company_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at);
CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users(LOWER(email));

INSERT INTO users (company_id, username, full_name, email, role, status, password_hash, must_change_password)
SELECT c.id, 'alels-root', 'ALELS Root Superadmin', 'osocarolio90@gmail.com', 'SUPERADMIN', 'ACTIVE', 'TEMP_OWNER_PASSWORD', TRUE
FROM companies c
WHERE c.company_code = 'ALELS_TECH_INDONESIA'
ON CONFLICT (email) DO UPDATE SET
    role = 'SUPERADMIN',
    status = 'ACTIVE',
    company_id = EXCLUDED.company_id,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    imei VARCHAR(32) NOT NULL UNIQUE,
    vehicle_id BIGINT NULL,
    device_model VARCHAR(120),
    online BOOLEAN NOT NULL DEFAULT FALSE,
    presence_status VARCHAR(40) NOT NULL DEFAULT 'OFFLINE',
    gsm_connected BOOLEAN NOT NULL DEFAULT FALSE,
    wifi_connected BOOLEAN NOT NULL DEFAULT FALSE,
    active_channel VARCHAR(40),
    last_protocol VARCHAR(80),
    last_seen TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_devices_company ON devices(company_id);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen DESC);
CREATE INDEX IF NOT EXISTS idx_devices_presence ON devices(presence_status);
CREATE INDEX IF NOT EXISTS idx_devices_active_channel ON devices(active_channel);

CREATE TABLE IF NOT EXISTS raw_packets (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(32),
    channel VARCHAR(40),
    protocol VARCHAR(80),
    payload TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_raw_packets_received_at ON raw_packets(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_raw_packets_imei_received_at ON raw_packets(imei, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_raw_packets_channel_received_at ON raw_packets(channel, received_at DESC);

CREATE TABLE IF NOT EXISTS telemetry (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(32) NOT NULL,
    device_time TIMESTAMPTZ NULL,
    server_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    latitude DOUBLE PRECISION NULL,
    longitude DOUBLE PRECISION NULL,
    speed DOUBLE PRECISION NULL,
    parse_status VARCHAR(40) NOT NULL DEFAULT 'VALID',
    io JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_telemetry_imei_time ON telemetry(imei, device_time DESC NULLS LAST, server_time DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_server_time ON telemetry(server_time DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_parse_status_time ON telemetry(parse_status, server_time DESC);

CREATE TABLE IF NOT EXISTS unknown_io_registry (
    id BIGSERIAL PRIMARY KEY,
    io_id VARCHAR(40) NOT NULL UNIQUE,
    seen_count BIGINT NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_unknown_io_last_seen ON unknown_io_registry(last_seen_at DESC);

CREATE TABLE IF NOT EXISTS ai_ops_metric_snapshots (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(80) NOT NULL,
    metric_key VARCHAR(120) NOT NULL,
    metric_value NUMERIC(18, 4) NOT NULL DEFAULT 0,
    severity VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ai_ops_metric_snapshots_category_time ON ai_ops_metric_snapshots(category, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_ops_metric_snapshots_metric_time ON ai_ops_metric_snapshots(metric_key, captured_at DESC);

CREATE TABLE IF NOT EXISTS ai_ops_alerts (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(80) NOT NULL,
    severity VARCHAR(40) NOT NULL DEFAULT 'WARNING',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    title TEXT NOT NULL,
    problem TEXT,
    recommendation TEXT,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ai_ops_alerts_status_time ON ai_ops_alerts(status, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_ops_alerts_category_time ON ai_ops_alerts(category, detected_at DESC);

CREATE TABLE IF NOT EXISTS login_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NULL,
    company_id BIGINT NULL,
    email VARCHAR(255),
    success BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address INET NULL,
    user_agent TEXT NULL,
    failure_reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_login_events_created_at ON login_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_events_success_created ON login_events(success, created_at DESC);

CREATE TABLE IF NOT EXISTS security_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NULL,
    company_id BIGINT NULL,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(40) NOT NULL DEFAULT 'WARNING',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    ip_address INET NULL,
    message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_security_events_created_at ON security_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_events_status_severity ON security_events(status, severity, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_events_type_created ON security_events(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_events_metadata_gin ON security_events USING GIN (metadata);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NULL,
    company_id BIGINT NULL,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120),
    entity_id VARCHAR(120),
    ip_address INET NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created ON audit_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created ON audit_logs(action, created_at DESC);
