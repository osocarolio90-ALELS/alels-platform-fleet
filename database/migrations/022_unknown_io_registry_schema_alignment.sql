-- Align unknown AVL/IO observation storage with UnknownIoRepository.
-- The legacy io_id key cannot represent the same IO identifier across models/protocols.

ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS imei VARCHAR(32);
ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS device_model_id BIGINT;
ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);
ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS source_protocol VARCHAR(80);
ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS source_io_id VARCHAR(80);
ALTER TABLE unknown_io_registry ADD COLUMN IF NOT EXISTS raw_value TEXT;
ALTER TABLE unknown_io_registry ALTER COLUMN seen_count SET DEFAULT 1;

UPDATE unknown_io_registry
SET seen_count = 1
WHERE seen_count < 1;

UPDATE unknown_io_registry
SET source_io_id = io_id
WHERE source_io_id IS NULL;

ALTER TABLE unknown_io_registry ALTER COLUMN io_id DROP NOT NULL;
ALTER TABLE unknown_io_registry DROP CONSTRAINT IF EXISTS unknown_io_registry_io_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_unknown_io_registry_source
    ON unknown_io_registry(COALESCE(device_model_id, 0), source_protocol, source_io_id);

CREATE INDEX IF NOT EXISTS idx_unknown_io_registry_imei_time
    ON unknown_io_registry(imei, last_seen_at DESC);
