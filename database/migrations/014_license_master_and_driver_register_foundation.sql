-- ALELS 1M FOUNDATION MODE
-- 014 License Master + Driver Register foundation
-- Idempotent; reuses asset_drivers for Driver Register wasted lifecycle.

CREATE TABLE IF NOT EXISTS license_master (
    id BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(10) NOT NULL,
    country_name VARCHAR(160),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    delete_permanent_at TIMESTAMPTZ,
    deleted_reason TEXT
);

ALTER TABLE license_master ADD COLUMN IF NOT EXISTS country_name VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_license_master_country_active ON license_master(country_code, is_active, deleted_at, name);
CREATE INDEX IF NOT EXISTS idx_license_master_country_name_active ON license_master(country_name, is_active, deleted_at, name);
CREATE INDEX IF NOT EXISTS idx_license_master_deleted ON license_master(deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_license_master_country_name_active ON license_master(country_code, UPPER(TRIM(name))) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS asset_drivers (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    driver_code VARCHAR(80),
    driver_name VARCHAR(180),
    full_name VARCHAR(180),
    employee_id VARCHAR(80),
    license_number VARCHAR(120),
    country_code VARCHAR(10),
    license_master_id BIGINT REFERENCES license_master(id) ON DELETE SET NULL,
    phone_number VARCHAR(80),
    rfid_ibutton VARCHAR(120),
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

ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS employee_id VARCHAR(80);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS country_code VARCHAR(10);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS license_master_id BIGINT;
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS rfid_ibutton VARCHAR(120);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS full_name VARCHAR(180);
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;
ALTER TABLE asset_drivers ADD COLUMN IF NOT EXISTS deleted_reason TEXT;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_asset_drivers_license_master') THEN
    ALTER TABLE asset_drivers ADD CONSTRAINT fk_asset_drivers_license_master FOREIGN KEY (license_master_id) REFERENCES license_master(id) ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_asset_drivers_company_deleted_created ON asset_drivers(company_id, deleted_at, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_asset_drivers_company_status_active ON asset_drivers(company_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_license_master ON asset_drivers(license_master_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_drivers_search_active ON asset_drivers(company_id, LOWER(COALESCE(driver_code, employee_id, driver_name, license_number, phone_number))) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_drivers_company_driver_code_active ON asset_drivers(company_id, UPPER(TRIM(driver_code))) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_drivers_company_license_number_active ON asset_drivers(company_id, UPPER(TRIM(license_number))) WHERE deleted_at IS NULL;

WITH seed(country_code, country_name, license_name, sort_order) AS (
  VALUES
    ('ID','Indonesia','SIM A',10),('ID','Indonesia','SIM B1',11),('ID','Indonesia','SIM B2',12),('ID','Indonesia','SIM C',13),('ID','Indonesia','SIM CI',14),('ID','Indonesia','SIM CII',15),
    ('MY','Malaysia','CDL Class D',20),('MY','Malaysia','B2',21),('MY','Malaysia','B',22),('MY','Malaysia','E',23),('MY','Malaysia','GDL',24),('MY','Malaysia','PSV',25),
    ('SG','Singapore','Class 2A',30),('SG','Singapore','Class 2B',31),('SG','Singapore','Class 2',32),('SG','Singapore','Class 3',33),('SG','Singapore','Class 3A',34),('SG','Singapore','Class 4',35),('SG','Singapore','Class 4A',36),('SG','Singapore','Class 5',37),
    ('TH','Thailand','Private Car',40),('TH','Thailand','Public Car',41),('TH','Thailand','Motorcycle',42),
    ('AU','Australia','C',50),('AU','Australia','LR',51),('AU','Australia','MR',52),('AU','Australia','HR',53),('AU','Australia','HC',54),('AU','Australia','MC',55),
    ('NZ','New Zealand','Class 1',60),('NZ','New Zealand','Class 2',61),('NZ','New Zealand','Class 3',62),('NZ','New Zealand','Class 4',63),('NZ','New Zealand','Class 5',64),
    ('JP','Japan','Ordinary',70),('JP','Japan','Medium',71),('JP','Japan','Large',72),('JP','Japan','Motorcycle',73),
    ('KR','South Korea','Class 1 Large',80),('KR','South Korea','Class 1 Normal',81),('KR','South Korea','Class 2',82),
    ('GB','United Kingdom','Category AM',90),('GB','United Kingdom','Category A1',91),('GB','United Kingdom','Category A2',92),('GB','United Kingdom','Category A',93),('GB','United Kingdom','Category B',94),('GB','United Kingdom','Category C1',95),('GB','United Kingdom','Category C',96),('GB','United Kingdom','Category D1',97),('GB','United Kingdom','Category D',98),
    ('US','United States','Class A CDL',100),('US','United States','Class B CDL',101),('US','United States','Class C CDL',102),('US','United States','Class D',103),
    ('CA','Canada','G1',110),('CA','Canada','G2',111),('CA','Canada','G',112),('CA','Canada','Class A',113),('CA','Canada','Class B',114),('CA','Canada','Class C',115),('CA','Canada','Class D',116),
    ('DE','Germany','AM',120),('DE','Germany','A1',121),('DE','Germany','A2',122),('DE','Germany','A',123),('DE','Germany','B',124),('DE','Germany','BE',125),('DE','Germany','C1',126),('DE','Germany','C',127),('DE','Germany','D1',128),('DE','Germany','D',129),
    ('EU','European Union','A',130),('EU','European Union','B',131),('EU','European Union','C',132),('EU','European Union','D',133),('EU','European Union','BE',134),('EU','European Union','CE',135),('EU','European Union','DE',136)
)
INSERT INTO license_master(country_code, country_name, code, name, status, is_active, is_system, sort_order)
SELECT country_code,
       country_name,
       UPPER(country_code || '_' || REGEXP_REPLACE(license_name, '[^A-Za-z0-9]+', '_', 'g')),
       license_name,
       'ACTIVE', TRUE, TRUE, sort_order
FROM seed
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, country_code = EXCLUDED.country_code, country_name = EXCLUDED.country_name, status = 'ACTIVE', is_active = TRUE, updated_at = NOW();

UPDATE license_master lm
SET country_name = CASE lm.country_code
    WHEN 'ID' THEN 'Indonesia' WHEN 'MY' THEN 'Malaysia' WHEN 'SG' THEN 'Singapore' WHEN 'TH' THEN 'Thailand'
    WHEN 'AU' THEN 'Australia' WHEN 'NZ' THEN 'New Zealand' WHEN 'JP' THEN 'Japan' WHEN 'KR' THEN 'South Korea'
    WHEN 'GB' THEN 'United Kingdom' WHEN 'US' THEN 'United States' WHEN 'CA' THEN 'Canada' WHEN 'DE' THEN 'Germany'
    WHEN 'EU' THEN 'European Union' ELSE COALESCE(NULLIF(lm.country_name, ''), lm.country_code)
END
WHERE lm.country_name IS NULL OR lm.country_name = '' OR lm.country_name = lm.country_code;

DO $$
BEGIN
  IF to_regclass('public.vehicle_types') IS NOT NULL THEN
    ALTER TABLE vehicle_types ADD COLUMN IF NOT EXISTS status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';
    ALTER TABLE vehicle_types ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;
    ALTER TABLE vehicle_types ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
  END IF;
  IF to_regclass('public.energy_reference_prices') IS NOT NULL THEN
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE';
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS delete_permanent_at TIMESTAMPTZ;
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS deleted_reason TEXT;
    ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
  END IF;
END $$;
