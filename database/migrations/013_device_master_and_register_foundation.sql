-- ALELS 1M FOUNDATION MODE
-- 013 Device Master + Device Register foundation
-- Idempotent, no duplicate tables. Reuses existing device_brands, device_models, devices.

-- Device master compatibility
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 1000;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
ALTER TABLE device_brands ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;

ALTER TABLE device_models ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 1000;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
ALTER TABLE device_models ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;

-- Device register compatibility
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_brand_id BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS gsm_number VARCHAR(40);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS tcp_host VARCHAR(255);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS tcp_port INTEGER;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS protocol_code VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS parser_code VARCHAR(120);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS dictionary_code VARCHAR(80);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS register_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_devices_device_brand') THEN
    ALTER TABLE devices ADD CONSTRAINT fk_devices_device_brand FOREIGN KEY (device_brand_id) REFERENCES device_brands(id) ON DELETE SET NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_devices_device_model') THEN
    ALTER TABLE devices ADD CONSTRAINT fk_devices_device_model FOREIGN KEY (device_model_id) REFERENCES device_models(id) ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_device_brands_active ON device_brands(is_active, deleted_at, brand_name);
CREATE INDEX IF NOT EXISTS idx_device_models_brand_active ON device_models(brand_id, is_active, deleted_at, model_name);
CREATE INDEX IF NOT EXISTS idx_devices_company_deleted ON devices(company_id, deleted_at, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_brand_model ON devices(device_brand_id, device_model_id);
CREATE INDEX IF NOT EXISTS idx_devices_imei_lower ON devices(LOWER(imei));
CREATE INDEX IF NOT EXISTS idx_devices_gsm_number ON devices(gsm_number) WHERE gsm_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_devices_tcp ON devices(tcp_host, tcp_port);

-- Keep old status columns aligned with new active flags.
UPDATE device_brands SET is_active = COALESCE(is_active, status = 'ACTIVE'), status = CASE WHEN COALESCE(is_active, TRUE) THEN 'ACTIVE' ELSE 'INACTIVE' END;
UPDATE device_models SET is_active = COALESCE(is_active, status = 'ACTIVE'), status = CASE WHEN COALESCE(is_active, TRUE) THEN 'ACTIVE' ELSE 'INACTIVE' END;

-- Deliberately no speculative brand/model seed here. A model becomes selectable only
-- after Master Device registers an active protocol, dictionary, and verified AVL IDs.
