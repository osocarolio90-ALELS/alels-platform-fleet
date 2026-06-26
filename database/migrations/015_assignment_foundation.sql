-- ALELS 1M FOUNDATION MODE
-- 015 Assignment foundation
-- Manual Vehicle-Device assignment and Manual Driver assignment.
-- TCP Gateway/Kafka/ingestion are intentionally not changed in this phase.

-- Compatibility hardening for existing ALELS schemas used by Assignment reads.
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_number VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_model VARCHAR(120);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_brand_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS driver_code VARCHAR(80);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS driver_name VARCHAR(180);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS full_name VARCHAR(180);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS license_number VARCHAR(120);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS rfid_ibutton VARCHAR(120);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Schema compatibility for lookup/list joins. These are idempotent and do not create duplicate tables.
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS brand_name VARCHAR(255);
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS model_name VARCHAR(255);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS imei VARCHAR(64);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_model VARCHAR(120);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_brand_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS status VARCHAR(40) DEFAULT 'ACTIVE';
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_name VARCHAR(180);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS plate_number VARCHAR(80);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_number VARCHAR(80);


CREATE TABLE IF NOT EXISTS vehicle_device_assignments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    assignment_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    removed_at TIMESTAMPTZ,
    removed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS driver_manual_assignments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE CASCADE,
    device_id BIGINT REFERENCES devices(id) ON DELETE CASCADE,
    driver_id BIGINT NOT NULL REFERENCES asset_drivers(id) ON DELETE CASCADE,
    assignment_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    removed_at TIMESTAMPTZ,
    removed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS driver_auto_sessions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    device_id BIGINT REFERENCES devices(id) ON DELETE SET NULL,
    driver_id BIGINT REFERENCES asset_drivers(id) ON DELETE SET NULL,
    device_imei VARCHAR(64) NOT NULL,
    rfid_ibutton VARCHAR(120) NOT NULL,
    session_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    ended_reason VARCHAR(120),
    source VARCHAR(40) NOT NULL DEFAULT 'AUTO_RFID_IBUTTON',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS assignment_events (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id BIGINT,
    action VARCHAR(120) NOT NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'WEB',
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE vehicle_device_assignments ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE vehicle_device_assignments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE vehicle_device_assignments ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE driver_manual_assignments ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE driver_manual_assignments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE driver_manual_assignments ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_device_assignments_vehicle_active
ON vehicle_device_assignments(vehicle_id)
WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_device_assignments_device_active
ON vehicle_device_assignments(device_id)
WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_vehicle_device_assignments_company_status
ON vehicle_device_assignments(company_id, assignment_status, assigned_at DESC)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_manual_assignments_driver_active
ON driver_manual_assignments(driver_id)
WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_manual_assignments_vehicle_device_active
ON driver_manual_assignments(vehicle_id, device_id)
WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_driver_manual_assignments_company_status
ON driver_manual_assignments(company_id, assignment_status, assigned_at DESC)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_driver_auto_sessions_imei_status
ON driver_auto_sessions(device_imei, session_status, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_driver_auto_sessions_company_status
ON driver_auto_sessions(company_id, session_status, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_driver_auto_sessions_rfid
ON driver_auto_sessions(company_id, rfid_ibutton, session_status, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_assignment_events_target_time
ON assignment_events(target_type, target_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assignment_events_company_time
ON assignment_events(actor_company_id, created_at DESC);
