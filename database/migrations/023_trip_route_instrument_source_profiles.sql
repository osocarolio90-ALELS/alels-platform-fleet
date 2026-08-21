BEGIN;

CREATE TABLE IF NOT EXISTS trip_route_instrument_source_profiles (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    profile_name VARCHAR(120) NOT NULL,
    rpm_source VARCHAR(160) NOT NULL DEFAULT '',
    speed_source VARCHAR(160) NOT NULL DEFAULT '__gps_speed__',
    level_source VARCHAR(160) NOT NULL DEFAULT '',
    consumption_source VARCHAR(160) NOT NULL DEFAULT '',
    odometer_source VARCHAR(160) NOT NULL DEFAULT '',
    is_company_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trip_route_source_profile_name CHECK (BTRIM(profile_name) <> '')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trip_route_source_profile_company_name
    ON trip_route_instrument_source_profiles(company_id, LOWER(profile_name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_trip_route_source_profile_company_default
    ON trip_route_instrument_source_profiles(company_id)
    WHERE is_company_default;

CREATE INDEX IF NOT EXISTS idx_trip_route_source_profile_company_updated
    ON trip_route_instrument_source_profiles(company_id, updated_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS trip_route_instrument_source_assignments (
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES trip_route_instrument_source_profiles(id) ON DELETE CASCADE,
    assigned_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (company_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_route_source_assignment_profile
    ON trip_route_instrument_source_assignments(profile_id, company_id);

COMMENT ON TABLE trip_route_instrument_source_profiles IS
    'Company-scoped named Trip & Route playback instrument source configurations.';
COMMENT ON TABLE trip_route_instrument_source_assignments IS
    'Per-device Trip & Route instrument source profile assignment. Company default profile applies when no device assignment exists.';

COMMIT;
