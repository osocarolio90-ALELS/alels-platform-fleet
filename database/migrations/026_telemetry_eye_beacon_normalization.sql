-- Parsed beacon lists stay on their source telemetry row. The existing
-- partitioning and IMEI/time indexes provide per-device history access.
ALTER TABLE telemetry
    ADD COLUMN IF NOT EXISTS eye_beacons JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Beacon lists are high-volume telemetry, not human-facing recent events.
-- Existing event rows remain available for historical audit; API queries hide them.
DROP TRIGGER IF EXISTS trg_telemetry_materialize_device_io_event ON telemetry;
CREATE TRIGGER trg_telemetry_materialize_device_io_event
AFTER INSERT ON telemetry
FOR EACH ROW
WHEN (NEW.event_io_id IS NOT NULL AND NEW.event_io_id <> 0
      AND NEW.event_io_id NOT IN (385, 548, 10500, 10828, 10829, 10831))
EXECUTE FUNCTION alels_materialize_device_io_event();

DROP TRIGGER IF EXISTS trg_telemetry_io_enrich_device_event ON telemetry_io;
CREATE TRIGGER trg_telemetry_io_enrich_device_event
AFTER INSERT OR UPDATE OF io_name, raw_value, numeric_value, real_value, unit, is_event ON telemetry_io
FOR EACH ROW
WHEN (NEW.is_event = TRUE
      AND NEW.io_id NOT IN ('385', '548', '10500', '10828', '10829', '10831'))
EXECUTE FUNCTION alels_enrich_device_io_event();
