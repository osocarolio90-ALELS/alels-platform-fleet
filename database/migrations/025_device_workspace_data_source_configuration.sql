BEGIN;

ALTER TABLE trip_route_instrument_source_profiles
    ADD COLUMN IF NOT EXISTS workspace_configuration JSONB NOT NULL DEFAULT '{"instruments":[],"bottomItems":[]}'::jsonb,
    ADD COLUMN IF NOT EXISTS unit_preferences JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE trip_route_instrument_source_profiles
    ADD CONSTRAINT chk_trip_route_workspace_configuration_object
    CHECK (jsonb_typeof(workspace_configuration) = 'object');

ALTER TABLE trip_route_instrument_source_profiles
    ADD CONSTRAINT chk_trip_route_unit_preferences_object
    CHECK (jsonb_typeof(unit_preferences) = 'object');

COMMENT ON COLUMN trip_route_instrument_source_profiles.workspace_configuration IS
    'Canonical Device Workspace Data Source Display mapping shared by Telemetry and Trip & Route.';
COMMENT ON COLUMN trip_route_instrument_source_profiles.unit_preferences IS
    'Display-unit preferences. Calculations retain canonical engineering units.';

COMMIT;
