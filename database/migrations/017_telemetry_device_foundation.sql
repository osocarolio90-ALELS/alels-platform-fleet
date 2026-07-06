ALTER TABLE devices ADD COLUMN IF NOT EXISTS tcp_enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS idx_devices_company_tcp_enabled
    ON devices(company_id, tcp_enabled) WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION alels_disable_wasted_group_tcp()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
        UPDATE devices d
        SET tcp_enabled = FALSE,
            updated_at = NOW()
        FROM telemetry_group_devices membership
        WHERE membership.group_id = NEW.id
          AND membership.device_id = d.id
          AND d.deleted_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_telemetry_group_disable_tcp ON telemetry_groups;
CREATE TRIGGER trg_telemetry_group_disable_tcp
AFTER UPDATE OF deleted_at ON telemetry_groups
FOR EACH ROW EXECUTE FUNCTION alels_disable_wasted_group_tcp();
