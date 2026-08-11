-- Align dictionary auto-import storage with DictionaryMappingGenerator.
-- This migration is idempotent and repairs partially initialized databases.

ALTER TABLE device_io_mappings
    ADD COLUMN IF NOT EXISTS mapping_source VARCHAR(40) NOT NULL DEFAULT 'MANUAL';

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_io_mappings_normalized_source
    ON device_io_mappings(device_model_id, source_protocol, source_io_id, normalized_field_id);

INSERT INTO normalized_fields (
    field_code, field_name, category, unit, target_unit, value_type, status
)
VALUES
    ('ignition', 'Ignition', 'vehicle', NULL, NULL, 'BOOLEAN', 'ACTIVE'),
    ('movement', 'Movement', 'vehicle', NULL, NULL, 'BOOLEAN', 'ACTIVE'),
    ('speed', 'Vehicle Speed', 'vehicle', 'km/h', 'km/h', 'NUMBER', 'ACTIVE'),
    ('gsm_signal', 'GSM Signal', 'connectivity', NULL, NULL, 'NUMBER', 'ACTIVE'),
    ('network_type', 'Network Type', 'connectivity', NULL, NULL, 'TEXT', 'ACTIVE'),
    ('external_voltage', 'External Voltage', 'power', 'V', 'V', 'NUMBER', 'ACTIVE'),
    ('battery_voltage', 'Battery Voltage', 'power', 'V', 'V', 'NUMBER', 'ACTIVE'),
    ('battery_current', 'Battery Current', 'power', 'A', 'A', 'NUMBER', 'ACTIVE'),
    ('pdop', 'GNSS PDOP', 'position', NULL, NULL, 'NUMBER', 'ACTIVE'),
    ('hdop', 'GNSS HDOP', 'position', NULL, NULL, 'NUMBER', 'ACTIVE'),
    ('driver_rfid', 'Driver RFID', 'driver', NULL, NULL, 'TEXT', 'ACTIVE'),
    ('engine_rpm', 'Engine RPM', 'engine', 'rpm', 'rpm', 'NUMBER', 'ACTIVE'),
    ('engine_load', 'Engine Load', 'engine', '%', '%', 'NUMBER', 'ACTIVE'),
    ('engine_temperature', 'Engine Temperature', 'engine', '°C', '°C', 'NUMBER', 'ACTIVE'),
    ('engine_hours', 'Engine Hours', 'engine', 'h', 'h', 'NUMBER', 'ACTIVE'),
    ('fuel_level', 'Fuel Level', 'fuel', '%', '%', 'NUMBER', 'ACTIVE'),
    ('fuel_rate', 'Fuel Rate', 'fuel', 'l/h', 'l/h', 'NUMBER', 'ACTIVE'),
    ('fuel_used', 'Fuel Used', 'fuel', 'l', 'l', 'NUMBER', 'ACTIVE'),
    ('odometer', 'Odometer', 'vehicle', 'm', 'm', 'NUMBER', 'ACTIVE'),
    ('trip_odometer', 'Trip Odometer', 'vehicle', 'm', 'm', 'NUMBER', 'ACTIVE'),
    ('pto_state', 'PTO State', 'vehicle', NULL, NULL, 'BOOLEAN', 'ACTIVE'),
    ('brake_switch', 'Brake Switch', 'vehicle', NULL, NULL, 'BOOLEAN', 'ACTIVE'),
    ('battery_soc', 'Battery State of Charge', 'ev', '%', '%', 'NUMBER', 'ACTIVE'),
    ('battery_soh', 'Battery State of Health', 'ev', '%', '%', 'NUMBER', 'ACTIVE'),
    ('ev_range', 'EV Range', 'ev', 'km', 'km', 'NUMBER', 'ACTIVE'),
    ('high_voltage_battery_voltage', 'High Voltage Battery Voltage', 'ev', 'V', 'V', 'NUMBER', 'ACTIVE'),
    ('high_voltage_battery_current', 'High Voltage Battery Current', 'ev', 'A', 'A', 'NUMBER', 'ACTIVE'),
    ('charging_state', 'Charging State', 'ev', NULL, NULL, 'TEXT', 'ACTIVE')
ON CONFLICT (field_code) DO UPDATE SET
    field_name = EXCLUDED.field_name,
    category = EXCLUDED.category,
    unit = EXCLUDED.unit,
    target_unit = EXCLUDED.target_unit,
    value_type = EXCLUDED.value_type,
    status = 'ACTIVE';

-- A previous failed bootstrap was incorrectly recorded as SUCCESS with zero mappings.
-- Mark it failed so the next gateway startup retries the same dictionary checksum.
UPDATE dictionary_import_history
SET import_status = 'FAILED'
WHERE import_status = 'SUCCESS'
  AND imported_mapping_count = 0;
