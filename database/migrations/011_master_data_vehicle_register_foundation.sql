-- ALELS 1M FOUNDATION MODE
-- Master Data + Asset Register Vehicle Register Foundation
-- Rules:
-- - no duplicate table if migration rerun
-- - master data is global source data; visible only to SUPERADMIN/ADMIN in API/UI
-- - vehicle register is tenant scoped by company visibility
-- - no device/driver assignment columns here; assignment is a separate menu

CREATE TABLE IF NOT EXISTS vehicle_types (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(80) NOT NULL UNIQUE,
    type_name VARCHAR(160) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_vehicle_types_active_sort ON vehicle_types(is_active, sort_order, type_name) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicle_types_search ON vehicle_types(LOWER(type_name)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS vehicle_brands (
    id BIGSERIAL PRIMARY KEY,
    brand_code VARCHAR(120) NOT NULL UNIQUE,
    brand_name VARCHAR(180) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_vehicle_brands_active_sort ON vehicle_brands(is_active, sort_order, brand_name) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicle_brands_search ON vehicle_brands(LOWER(brand_name)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS vehicle_models (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT REFERENCES vehicle_brands(id) ON DELETE SET NULL,
    model_code VARCHAR(160) NOT NULL,
    model_name VARCHAR(220) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_vehicle_models_brand_model UNIQUE (brand_id, model_code)
);
CREATE INDEX IF NOT EXISTS idx_vehicle_models_brand_active ON vehicle_models(brand_id, is_active, sort_order, model_name) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicle_models_search ON vehicle_models(LOWER(model_name)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS vehicle_ownership_types (
    id BIGSERIAL PRIMARY KEY,
    ownership_code VARCHAR(80) NOT NULL UNIQUE,
    ownership_name VARCHAR(160) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_vehicle_ownership_active_sort ON vehicle_ownership_types(is_active, sort_order, ownership_name) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS vehicle_capacity_units (
    id BIGSERIAL PRIMARY KEY,
    unit_code VARCHAR(40) NOT NULL UNIQUE,
    unit_name VARCHAR(120) NOT NULL,
    unit_group VARCHAR(80) NOT NULL DEFAULT 'CAPACITY',
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_vehicle_capacity_units_active_sort ON vehicle_capacity_units(is_active, sort_order, unit_name) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    vehicle_code VARCHAR(80) NOT NULL,
    vehicle_name VARCHAR(180),
    plate_number VARCHAR(80) NOT NULL,
    vehicle_type_id BIGINT REFERENCES vehicle_types(id) ON DELETE SET NULL,
    brand_id BIGINT REFERENCES vehicle_brands(id) ON DELETE SET NULL,
    model_id BIGINT REFERENCES vehicle_models(id) ON DELETE SET NULL,
    year_manufacture INTEGER,
    country_code VARCHAR(10) NOT NULL DEFAULT 'ID',
    energy_id BIGINT REFERENCES energy_types(id) ON DELETE SET NULL,
    energy_code VARCHAR(100),
    energy_price_snapshot NUMERIC(18,4) NOT NULL DEFAULT 0,
    energy_currency VARCHAR(10) NOT NULL DEFAULT 'IDR',
    ownership_type_id BIGINT REFERENCES vehicle_ownership_types(id) ON DELETE SET NULL,
    capacity_value NUMERIC(18,2),
    capacity_unit_id BIGINT REFERENCES vehicle_capacity_units(id) ON DELETE SET NULL,
    operational_status VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    maintenance_at TIMESTAMPTZ,
    maintenance_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_vehicles_company_vehicle_code UNIQUE(company_id, vehicle_code),
    CONSTRAINT uq_vehicles_company_plate_number UNIQUE(company_id, plate_number)
);

-- 003 may already have created the smaller runtime vehicles shape.
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS vehicle_code VARCHAR(80);
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
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_company_status ON vehicles(company_id, operational_status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_company_created ON vehicles(company_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_type_brand_model ON vehicles(vehicle_type_id, brand_id, model_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_energy_country ON vehicles(energy_id, country_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_vehicles_plate_search ON vehicles(LOWER(plate_number)) WHERE deleted_at IS NULL;

INSERT INTO vehicle_types(type_code, type_name, sort_order) VALUES
('PASSENGER_CAR', 'Passenger Car', 10),
('MOTORCYCLE', 'Motorcycle', 20),
('PICKUP', 'Pickup', 30),
('VAN', 'Van', 40),
('BUS', 'Bus', 50),
('TRUCK', 'Truck', 60),
('TRAILER', 'Trailer', 70),
('TANKER', 'Tanker', 80),
('DUMP_TRUCK', 'Dump Truck', 90),
('EXCAVATOR', 'Excavator', 100),
('BULLDOZER', 'Bulldozer', 110),
('WHEEL_LOADER', 'Wheel Loader', 120),
('CRANE', 'Crane', 130),
('FORKLIFT', 'Forklift', 140),
('TRACTOR', 'Tractor', 150),
('HARVESTER', 'Harvester', 160),
('GENERATOR', 'Generator', 170),
('MARINE_VESSEL', 'Marine Vessel', 180),
('TRAIN', 'Train', 190),
('AIRCRAFT', 'Aircraft', 200)
ON CONFLICT(type_code) DO UPDATE SET type_name = EXCLUDED.type_name, sort_order = EXCLUDED.sort_order, is_active = TRUE, updated_at = NOW();

INSERT INTO vehicle_ownership_types(ownership_code, ownership_name, sort_order) VALUES
('OWNED', 'Owned', 10),
('LEASED', 'Leased', 20),
('RENTAL', 'Rental', 30),
('CUSTOMER', 'Customer', 40),
('PARTNER', 'Partner', 50),
('OTHER', 'Other', 100)
ON CONFLICT(ownership_code) DO UPDATE SET ownership_name = EXCLUDED.ownership_name, sort_order = EXCLUDED.sort_order, is_active = TRUE, updated_at = NOW();

INSERT INTO vehicle_capacity_units(unit_code, unit_name, unit_group, sort_order) VALUES
('LITER', 'Liter', 'FUEL_TANK', 10),
('KWH', 'kWh', 'BATTERY', 20),
('KG', 'Kg', 'PAYLOAD', 30),
('TON', 'Ton', 'PAYLOAD', 40),
('M3', 'm3', 'VOLUME', 50),
('NM3', 'Nm3', 'GAS', 60),
('HOUR', 'Hour', 'GENERATOR', 70),
('UNIT', 'Unit', 'GENERAL', 100)
ON CONFLICT(unit_code) DO UPDATE SET unit_name = EXCLUDED.unit_name, unit_group = EXCLUDED.unit_group, sort_order = EXCLUDED.sort_order, is_active = TRUE, updated_at = NOW();

INSERT INTO vehicle_brands(brand_code, brand_name, sort_order) VALUES
('TOYOTA', 'Toyota', 10),('HONDA', 'Honda', 20),('MITSUBISHI', 'Mitsubishi', 30),('ISUZU', 'Isuzu', 40),('HINO', 'Hino', 50),('DAIHATSU', 'Daihatsu', 60),('SUZUKI', 'Suzuki', 70),('NISSAN', 'Nissan', 80),('HYUNDAI', 'Hyundai', 90),('KIA', 'Kia', 100),('MERCEDES_BENZ', 'Mercedes-Benz', 110),('BMW', 'BMW', 120),('VOLVO', 'Volvo', 130),('SCANIA', 'Scania', 140),('MAN', 'MAN', 150),('DAF', 'DAF', 160),('IVECO', 'Iveco', 170),('FUSO', 'Fuso', 180),('UD_TRUCKS', 'UD Trucks', 190),('CATERPILLAR', 'Caterpillar', 200),('KOMATSU', 'Komatsu', 210),('HITACHI', 'Hitachi', 220),('DOOSAN', 'Doosan', 230),('KOBELCO', 'Kobelco', 240),('JCB', 'JCB', 250),('JOHN_DEERE', 'John Deere', 260),('CASE', 'CASE', 270),('NEW_HOLLAND', 'New Holland', 280),('YAMAHA', 'Yamaha', 290),('KAWASAKI', 'Kawasaki', 300),('TESLA', 'Tesla', 310),('BYD', 'BYD', 320),('WULING', 'Wuling', 330),('CUSTOM', 'Custom', 1000)
ON CONFLICT(brand_code) DO UPDATE SET brand_name = EXCLUDED.brand_name, sort_order = EXCLUDED.sort_order, is_active = TRUE, updated_at = NOW();

WITH brand AS (SELECT id, brand_code FROM vehicle_brands)
INSERT INTO vehicle_models(brand_id, model_code, model_name, sort_order)
SELECT b.id, v.model_code, v.model_name, v.sort_order
FROM (VALUES
('TOYOTA','AVANZA','Avanza',10),('TOYOTA','HILUX','Hilux',20),('TOYOTA','FORTUNER','Fortuner',30),('TOYOTA','HIACE','HiAce',40),
('HONDA','BRIO','Brio',10),('HONDA','HRV','HR-V',20),('HONDA','CRV','CR-V',30),
('MITSUBISHI','XPANDER','Xpander',10),('MITSUBISHI','L300','L300',20),('MITSUBISHI','TRITON','Triton',30),
('ISUZU','PANTHER','Panther',10),('ISUZU','ELF','Elf',20),('ISUZU','GIGA','Giga',30),
('HINO','DUTRO','Dutro',10),('HINO','RANGER','Ranger',20),('HINO','PROFIA','Profia',30),
('FUSO','CANTER','Canter',10),('FUSO','FIGHTER','Fighter',20),
('CATERPILLAR','EXCAVATOR_SERIES','Excavator Series',10),('KOMATSU','PC_SERIES','PC Series',10),('HITACHI','ZX_SERIES','ZX Series',10),
('TESLA','MODEL_3','Model 3',10),('BYD','ATTO_3','Atto 3',10),('WULING','AIR_EV','Air EV',10),('CUSTOM','CUSTOM_MODEL','Custom Model',10)
) AS v(brand_code, model_code, model_name, sort_order)
JOIN brand b ON b.brand_code = v.brand_code
ON CONFLICT(brand_id, model_code) DO UPDATE SET model_name = EXCLUDED.model_name, sort_order = EXCLUDED.sort_order, is_active = TRUE, updated_at = NOW();
