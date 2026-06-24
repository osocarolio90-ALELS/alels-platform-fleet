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

-- Seed non-Teltonika/Ruptela/Concox brands and representative models.
WITH brand_seed(brand_code, brand_name, sort_order) AS (
  VALUES
    ('QUECLINK','Queclink',10),('SUNTECH','Suntech',20),('CALAMP','CalAmp',30),('MEITRACK','Meitrack',40),('JIMI_IOT','Jimi IoT',50),
    ('FLESPI_GENERIC','flespi Generic',60),('DIGITAL_MATTER','Digital Matter',70),('RUPTELA_ALT','Ruptela Compatible',80),
    ('TOPFLY','Topflytech',90),('XIRGO','Xirgo',100),('MUNICH','Munic',110),('NAVTELECOM','Navtelecom',120),
    ('SEEWORLD','Seeworld',130),('COBAN','Coban',140),('TKSTAR','TKSTAR',150),('SMARTWITNESS','SmartWitness',160),
    ('DCT','DCT',170),('ENFORA','Enfora',180),('FALCOM','Falcom',190),('GLOBALSAT','Globalsat',200),
    ('GOSAFE','GoSafe',210),('HIKVISION','Hikvision Mobile',220),('HOWEN','Howen',230),('STREAMAX','Streamax',240)
)
INSERT INTO device_brands (brand_code, brand_name, status, is_active, is_system, sort_order, description)
SELECT brand_code, brand_name, 'ACTIVE', TRUE, TRUE, sort_order, 'ALELS seeded device brand'
FROM brand_seed
ON CONFLICT (brand_code) DO UPDATE SET brand_name = EXCLUDED.brand_name, is_active = TRUE, status = 'ACTIVE', updated_at = NOW();

WITH model_seed(brand_code, model_code, model_name, protocol_code, parser_code, dictionary_code, sort_order) AS (
  VALUES
    ('QUECLINK','GV300','GV300','QUECLINK','QUECLINK_PARSER','QUECLINK_AVL',10),('QUECLINK','GV500','GV500','QUECLINK','QUECLINK_PARSER','QUECLINK_AVL',11),('QUECLINK','GL300','GL300','QUECLINK','QUECLINK_PARSER','QUECLINK_AVL',12),
    ('SUNTECH','ST300','ST300','SUNTECH','SUNTECH_PARSER','SUNTECH_AVL',20),('SUNTECH','ST340','ST340','SUNTECH','SUNTECH_PARSER','SUNTECH_AVL',21),('SUNTECH','ST4500','ST4500','SUNTECH','SUNTECH_PARSER','SUNTECH_AVL',22),
    ('CALAMP','LMU_2630','LMU-2630','CALAMP','CALAMP_PARSER','CALAMP_AVL',30),('CALAMP','LMU_3030','LMU-3030','CALAMP','CALAMP_PARSER','CALAMP_AVL',31),('CALAMP','LMU_4230','LMU-4230','CALAMP','CALAMP_PARSER','CALAMP_AVL',32),
    ('MEITRACK','T366','T366','MEITRACK','MEITRACK_PARSER','MEITRACK_AVL',40),('MEITRACK','T633L','T633L','MEITRACK','MEITRACK_PARSER','MEITRACK_AVL',41),('MEITRACK','TC68S','TC68S','MEITRACK','MEITRACK_PARSER','MEITRACK_AVL',42),
    ('JIMI_IOT','JC400','JC400','JIMI','JIMI_PARSER','JIMI_AVL',50),('JIMI_IOT','VL502','VL502','JIMI','JIMI_PARSER','JIMI_AVL',51),('JIMI_IOT','LL303','LL303','JIMI','JIMI_PARSER','JIMI_AVL',52),
    ('DIGITAL_MATTER','OYSTER3','Oyster3','DIGITAL_MATTER','DIGITAL_MATTER_PARSER','DIGITAL_MATTER_AVL',70),('DIGITAL_MATTER','YABBY3','Yabby3','DIGITAL_MATTER','DIGITAL_MATTER_PARSER','DIGITAL_MATTER_AVL',71),
    ('TOPFLY','TLP1_SF','TLP1-SF','TOPFLY','TOPFLY_PARSER','TOPFLY_AVL',90),('TOPFLY','TLW2_12B','TLW2-12B','TOPFLY','TOPFLY_PARSER','TOPFLY_AVL',91),
    ('XIRGO','XT6300','XT6300','XIRGO','XIRGO_PARSER','XIRGO_AVL',100),('XIRGO','XT6372','XT6372','XIRGO','XIRGO_PARSER','XIRGO_AVL',101),
    ('HOWEN','Hero_MDT','Hero-MDT','HOWEN','HOWEN_PARSER','HOWEN_AVL',230),('STREAMAX','X3N','X3N','STREAMAX','STREAMAX_PARSER','STREAMAX_AVL',240),('HIKVISION','AE_DN2016','AE-DN2016','HIKVISION','HIKVISION_PARSER','HIKVISION_AVL',220)
)
INSERT INTO device_models (brand_id, model_code, model_name, vendor, protocol_code, parser_code, dictionary_code, status, is_active, is_system, sort_order, description)
SELECT b.id, m.model_code, m.model_name, b.brand_name, m.protocol_code, m.parser_code, m.dictionary_code, 'ACTIVE', TRUE, TRUE, m.sort_order, 'ALELS seeded device model'
FROM model_seed m
JOIN device_brands b ON b.brand_code = m.brand_code
ON CONFLICT (model_code) DO UPDATE SET
  brand_id = EXCLUDED.brand_id,
  model_name = EXCLUDED.model_name,
  vendor = EXCLUDED.vendor,
  protocol_code = EXCLUDED.protocol_code,
  parser_code = EXCLUDED.parser_code,
  dictionary_code = EXCLUDED.dictionary_code,
  status = 'ACTIVE',
  is_active = TRUE,
  updated_at = NOW();
