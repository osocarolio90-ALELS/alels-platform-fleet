-- ALELS 1M Foundation - Vehicle Register schema and options hardening
-- Purpose:
-- - Fix Request Failed / 500 when saving Vehicle Register on databases where vehicles was created by older migrations.
-- - 003_core_runtime_schema.sql may already have created a minimal vehicles table.
-- - 011_master_data_vehicle_register_foundation.sql uses CREATE TABLE IF NOT EXISTS, so it cannot add missing columns when vehicles already exists.
-- - This file is additive, idempotent, backward compatible, and does not create duplicate vehicle tables.

ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_code VARCHAR(80);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_name VARCHAR(180);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_type_id BIGINT REFERENCES vehicle_types(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS brand_id BIGINT REFERENCES vehicle_brands(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS model_id BIGINT REFERENCES vehicle_models(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS year_manufacture INTEGER;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS country_code VARCHAR(10) NOT NULL DEFAULT 'ID';
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS energy_id BIGINT REFERENCES energy_types(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS energy_code VARCHAR(100);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS energy_price_snapshot NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS energy_currency VARCHAR(10) NOT NULL DEFAULT 'IDR';
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS ownership_type_id BIGINT REFERENCES vehicle_ownership_types(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS capacity_value NUMERIC(18,2);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS capacity_unit_id BIGINT REFERENCES vehicle_capacity_units(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS operational_status VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS maintenance_at TIMESTAMPTZ;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS maintenance_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
DO $$
BEGIN
    IF to_regclass('public.devices') IS NOT NULL THEN
        ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
        ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
        ALTER TABLE devices ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;
        ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
        ALTER TABLE devices ADD COLUMN IF NOT EXISTS updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS asset_drivers (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    driver_code VARCHAR(80),
    driver_name VARCHAR(180),
    full_name VARCHAR(180),
    license_number VARCHAR(120),
    phone_number VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    delete_permanent_at TIMESTAMPTZ,
    deleted_reason TEXT
);

-- Backfill from older runtime columns without deleting old data.
UPDATE vehicles
SET vehicle_code = UPPER(REGEXP_REPLACE(COALESCE(NULLIF(TRIM(vehicle_code), ''), NULLIF(TRIM(vehicle_number), ''), NULLIF(TRIM(plate_number), ''), 'VH_' || id::text), '[^A-Za-z0-9]+', '_', 'g'))
WHERE vehicle_code IS NULL OR TRIM(vehicle_code) = '';

UPDATE vehicles
SET vehicle_name = COALESCE(NULLIF(TRIM(vehicle_name), ''), plate_number, vehicle_code)
WHERE vehicle_name IS NULL OR TRIM(vehicle_name) = '';

UPDATE vehicles
SET country_code = UPPER(COALESCE(NULLIF(TRIM(country_code), ''), 'ID'))
WHERE country_code IS NULL OR TRIM(country_code) = '';

UPDATE vehicles
SET operational_status = COALESCE(NULLIF(TRIM(operational_status), ''), NULLIF(TRIM(status), ''), 'UNKNOWN')
WHERE operational_status IS NULL OR TRIM(operational_status) = '';

ALTER TABLE vehicles ALTER COLUMN vehicle_code SET NOT NULL;
ALTER TABLE vehicles ALTER COLUMN plate_number SET NOT NULL;
ALTER TABLE vehicles ALTER COLUMN operational_status SET DEFAULT 'UNKNOWN';
ALTER TABLE vehicles ALTER COLUMN country_code SET DEFAULT 'ID';
ALTER TABLE vehicles ALTER COLUMN energy_price_snapshot SET DEFAULT 0;
ALTER TABLE vehicles ALTER COLUMN energy_currency SET DEFAULT 'IDR';

-- Searchable and list-safe indexes for large tenant data.
CREATE INDEX IF NOT EXISTS idx_vehicles_company_status ON vehicles(company_id, operational_status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_company_created ON vehicles(company_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_type_brand_model ON vehicles(vehicle_type_id, brand_id, model_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_energy_country ON vehicles(energy_id, country_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_plate_search ON vehicles(LOWER(plate_number)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_vehicle_name_search ON vehicles(company_id, LOWER(vehicle_name)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_company_plate_upper ON vehicles(company_id, UPPER(TRIM(plate_number))) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_wasted_company_deleted ON vehicles(company_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_devices_wasted_company_deleted ON devices(company_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_company_status ON asset_drivers(company_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_wasted_company_deleted ON asset_drivers(company_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_search ON asset_drivers(company_id, LOWER(COALESCE(driver_name, full_name, driver_code, license_number))) WHERE deleted_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_vehicles_company_vehicle_code'
    ) THEN
        ALTER TABLE vehicles ADD CONSTRAINT uq_vehicles_company_vehicle_code UNIQUE(company_id, vehicle_code);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_vehicles_company_plate_number'
    ) THEN
        ALTER TABLE vehicles ADD CONSTRAINT uq_vehicles_company_plate_number UNIQUE(company_id, plate_number);
    END IF;
END $$;

-- Enrich searchable Brand/Model options without changing existing data contracts.
INSERT INTO vehicle_brands(brand_code, brand_name, sort_order) VALUES
('MAZDA', 'Mazda', 340),
('SUBARU', 'Subaru', 350),
('FORD', 'Ford', 360),
('CHEVROLET', 'Chevrolet', 370),
('RENAULT', 'Renault', 380),
('PEUGEOT', 'Peugeot', 390),
('VOLKSWAGEN', 'Volkswagen', 400),
('AUDI', 'Audi', 410),
('LEXUS', 'Lexus', 420),
('MORRIS_GARAGES', 'MG', 430),
('CHERY', 'Chery', 440),
('GREAT_WALL_MOTOR', 'Great Wall Motor', 450),
('TATA', 'Tata', 460),
('ASHOK_LEYLAND', 'Ashok Leyland', 470),
('RENAULT_TRUCKS', 'Renault Trucks', 480),
('MACK', 'Mack', 490),
('SINOTRUK', 'Sinotruk', 500),
('FAW', 'FAW', 510),
('SHACMAN', 'Shacman', 520),
('XCMG', 'XCMG', 530),
('SANY', 'Sany', 540),
('LIUGONG', 'LiuGong', 550),
('ZOOMLION', 'Zoomlion', 560),
('HYUNDAI_CONSTRUCTION', 'Hyundai Construction', 570),
('MERCEDES_BENZ_TRUCKS', 'Mercedes-Benz Trucks', 580)
ON CONFLICT(brand_code) DO UPDATE
SET brand_name = EXCLUDED.brand_name,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    updated_at = NOW(),
    deleted_at = NULL,
    deleted_by = NULL;

WITH brand AS (SELECT id, brand_code FROM vehicle_brands)
INSERT INTO vehicle_models(brand_id, model_code, model_name, sort_order)
SELECT b.id, v.model_code, v.model_name, v.sort_order
FROM (VALUES
('TOYOTA','AGYA','Agya',50),('TOYOTA','CALYA','Calya',60),('TOYOTA','INNOVA','Innova',70),('TOYOTA','RUSH','Rush',80),('TOYOTA','RAIZE','Raize',90),('TOYOTA','DYNA','Dyna',100),('TOYOTA','COASTER','Coaster',110),
('HONDA','BRV','BR-V',40),('HONDA','CITY','City',50),('HONDA','CIVIC','Civic',60),('HONDA','ACCORD','Accord',70),
('MITSUBISHI','PAJERO_SPORT','Pajero Sport',40),('MITSUBISHI','COLT_DIESEL','Colt Diesel',50),('MITSUBISHI','FUSO_FIGHTER','Fuso Fighter',60),
('ISUZU','TRAGA','Traga',40),('ISUZU','NLR','NLR',50),('ISUZU','NMR','NMR',60),('ISUZU','FRR','FRR',70),('ISUZU','FVR','FVR',80),
('HINO','DUTRO_110','Dutro 110',40),('HINO','DUTRO_130','Dutro 130',50),('HINO','DUTRO_300','Dutro 300',60),('HINO','RANGER_500','Ranger 500',70),('HINO','PROFIA_700','Profia 700',80),
('DAIHATSU','GRAN_MAX','Gran Max',10),('DAIHATSU','TERIOS','Terios',20),('DAIHATSU','SIGRA','Sigra',30),
('SUZUKI','CARRY','Carry',10),('SUZUKI','ERTIGA','Ertiga',20),('SUZUKI','APV','APV',30),
('NISSAN','NAVARA','Navara',10),('NISSAN','LIVINA','Livina',20),('NISSAN','URVAN','Urvan',30),
('HYUNDAI','STARGAZER','Stargazer',10),('HYUNDAI','H1','H-1',20),('HYUNDAI','IONIQ_5','IONIQ 5',30),('HYUNDAI','COUNTY','County',40),('HYUNDAI','MIGHTY','Mighty',50),
('KIA','SELTOS','Seltos',10),('KIA','CARNIVAL','Carnival',20),
('MERCEDES_BENZ','SPRINTER','Sprinter',10),('MERCEDES_BENZ','ACTROS','Actros',20),('MERCEDES_BENZ_TRUCKS','AXOR','Axor',10),('MERCEDES_BENZ_TRUCKS','AROCS','Arocs',20),
('VOLVO','FMX','FMX',10),('VOLVO','FH','FH',20),('VOLVO','B11R','B11R',30),
('SCANIA','P_SERIES','P Series',10),('SCANIA','G_SERIES','G Series',20),('SCANIA','R_SERIES','R Series',30),('SCANIA','K_SERIES','K Series',40),
('MAN','TGS','TGS',10),('MAN','TGX','TGX',20),('DAF','CF','CF',10),('DAF','XF','XF',20),('IVECO','DAILY','Daily',10),('IVECO','STRALIS','Stralis',20),
('UD_TRUCKS','QUESTER','Quester',10),('UD_TRUCKS','CRONER','Croner',20),
('BYD','SEAL','Seal',20),('BYD','DOLPHIN','Dolphin',30),('BYD','M6','M6',40),('BYD','T3','T3',50),
('WULING','ALMAZ','Almaz',20),('WULING','BINGUO_EV','Binguo EV',30),('WULING','CLOUD_EV','Cloud EV',40),
('CHERY','OMODA_5','Omoda 5',10),('CHERY','TIGGO','Tiggo',20),('MORRIS_GARAGES','ZS','ZS',10),('MORRIS_GARAGES','MG4_EV','MG4 EV',20),
('FORD','RANGER','Ranger',10),('FORD','EVEREST','Everest',20),('CHEVROLET','COLORADO','Colorado',10),
('CATERPILLAR','320','320 Excavator',20),('CATERPILLAR','D6','D6 Dozer',30),('CATERPILLAR','950','950 Wheel Loader',40),
('KOMATSU','PC200','PC200',20),('KOMATSU','HD785','HD785',30),('KOMATSU','WA380','WA380',40),
('HITACHI','ZX200','ZX200',20),('HITACHI','EH3500','EH3500',30),
('KOBELCO','SK200','SK200',20),('JCB','3CX','3CX',20),('JOHN_DEERE','6M','6M Series',20),('CASE','580N','580N',20),('NEW_HOLLAND','TT4','TT4',20),
('SINOTRUK','HOWO','HOWO',10),('FAW','JH6','JH6',10),('SHACMAN','F3000','F3000',10),('XCMG','XE215','XE215',10),('SANY','SY215','SY215',10),('LIUGONG','CLG856','CLG856',10),('ZOOMLION','ZE215','ZE215',10)
) AS v(brand_code, model_code, model_name, sort_order)
JOIN brand b ON b.brand_code = v.brand_code
ON CONFLICT(brand_id, model_code) DO UPDATE
SET model_name = EXCLUDED.model_name,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    updated_at = NOW(),
    deleted_at = NULL,
    deleted_by = NULL;


-- Wasted retention hardening: every deleted asset is purged automatically after 30 days.
UPDATE vehicles
SET delete_permanent_at = deleted_at + INTERVAL '30 days'
WHERE deleted_at IS NOT NULL
  AND (delete_permanent_at IS NULL OR delete_permanent_at > deleted_at + INTERVAL '30 days');

UPDATE vehicles
SET deleted_reason = COALESCE(deleted_reason, 'Deleted from Vehicle Register')
WHERE deleted_at IS NOT NULL;

DO $$
BEGIN
    IF to_regclass('public.devices') IS NOT NULL THEN
        UPDATE devices
        SET delete_permanent_at = deleted_at + INTERVAL '30 days'
        WHERE deleted_at IS NOT NULL
          AND (delete_permanent_at IS NULL OR delete_permanent_at > deleted_at + INTERVAL '30 days');
    END IF;
END $$;

UPDATE asset_drivers
SET delete_permanent_at = deleted_at + INTERVAL '30 days'
WHERE deleted_at IS NOT NULL
  AND (delete_permanent_at IS NULL OR delete_permanent_at > deleted_at + INTERVAL '30 days');

CREATE INDEX IF NOT EXISTS idx_vehicles_wasted_company_purge ON vehicles(company_id, delete_permanent_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_wasted_company_purge ON asset_drivers(company_id, delete_permanent_at) WHERE deleted_at IS NOT NULL;

DO $$
BEGIN
    IF to_regclass('public.devices') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_devices_wasted_company_purge ON devices(company_id, delete_permanent_at) WHERE deleted_at IS NOT NULL;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION alels_purge_asset_wasted()
RETURNS TABLE(item_type TEXT, deleted_rows BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    vehicle_count BIGINT := 0;
    device_count BIGINT := 0;
    driver_count BIGINT := 0;
BEGIN
    WITH deleted_vehicle_rows AS (
        DELETE FROM vehicles
        WHERE deleted_at IS NOT NULL
          AND COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') <= NOW()
        RETURNING id
    )
    SELECT COUNT(*) INTO vehicle_count FROM deleted_vehicle_rows;

    IF to_regclass('public.devices') IS NOT NULL THEN
        EXECUTE $sql$
            WITH deleted_device_rows AS (
                DELETE FROM devices
                WHERE deleted_at IS NOT NULL
                  AND COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') <= NOW()
                RETURNING id
            )
            SELECT COUNT(*) FROM deleted_device_rows
        $sql$ INTO device_count;
    END IF;

    IF to_regclass('public.asset_drivers') IS NOT NULL THEN
        WITH deleted_driver_rows AS (
            DELETE FROM asset_drivers
            WHERE deleted_at IS NOT NULL
              AND COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') <= NOW()
            RETURNING id
        )
        SELECT COUNT(*) INTO driver_count FROM deleted_driver_rows;
    END IF;

    item_type := 'VEHICLE'; deleted_rows := vehicle_count; RETURN NEXT;
    item_type := 'DEVICE'; deleted_rows := device_count; RETURN NEXT;
    item_type := 'DRIVER'; deleted_rows := driver_count; RETURN NEXT;
END;
$$;
