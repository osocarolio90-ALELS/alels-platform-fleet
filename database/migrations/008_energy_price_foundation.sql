-- ALELS 1M FOUNDATION MODE
-- Asset Register - Harga Energy foundation
-- Purpose:
-- - master vehicle energy/fuel types
-- - company scoped operational price used by vehicle register
-- - read-only country/global reference price with clear source metadata

CREATE TABLE IF NOT EXISTS energy_types (
    id BIGSERIAL PRIMARY KEY,
    energy_code VARCHAR(80) NOT NULL UNIQUE,
    energy_name VARCHAR(160) NOT NULL,
    energy_group VARCHAR(60) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    vehicle_usage VARCHAR(255),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_energy_types_group_status ON energy_types (energy_group, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_energy_types_status_sort ON energy_types (status, sort_order, energy_name);

CREATE TABLE IF NOT EXISTS energy_reference_prices (
    id BIGSERIAL PRIMARY KEY,
    energy_id BIGINT NOT NULL REFERENCES energy_types(id) ON DELETE CASCADE,
    country VARCHAR(120) NOT NULL DEFAULT 'Indonesia',
    country_currency VARCHAR(10) NOT NULL DEFAULT 'IDR',
    reference_price_country NUMERIC(18,4),
    reference_price_country_idr NUMERIC(18,4),
    reference_price_global_usd NUMERIC(18,4),
    fx_rate_to_idr NUMERIC(18,6),
    source VARCHAR(255) NOT NULL DEFAULT 'ALELS_SEED_REFERENCE',
    source_url TEXT,
    provider_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (energy_id, country)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_energy_reference_energy_country ON energy_reference_prices (energy_id, country);
CREATE INDEX IF NOT EXISTS idx_energy_reference_country ON energy_reference_prices (country, energy_id);
CREATE INDEX IF NOT EXISTS idx_energy_reference_updated ON energy_reference_prices (updated_at DESC);

CREATE TABLE IF NOT EXISTS company_energy_prices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    energy_id BIGINT NOT NULL REFERENCES energy_types(id) ON DELETE CASCADE,
    country VARCHAR(120) NOT NULL DEFAULT 'Indonesia',
    currency VARCHAR(10) NOT NULL DEFAULT 'IDR',
    price_energy NUMERIC(18,4) NOT NULL DEFAULT 0,
    reference_price_country_idr NUMERIC(18,4),
    reference_price_global_usd NUMERIC(18,4),
    fx_rate_to_idr NUMERIC(18,6),
    price_source VARCHAR(120) NOT NULL DEFAULT 'REFERENCE_COUNTRY',
    reference_source VARCHAR(255) NOT NULL DEFAULT 'ALELS_SEED_REFERENCE',
    last_reference_update_at TIMESTAMPTZ,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, energy_id, country)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_energy_company_energy_country ON company_energy_prices (company_id, energy_id, country);
CREATE INDEX IF NOT EXISTS idx_company_energy_company ON company_energy_prices (company_id, energy_id);
CREATE INDEX IF NOT EXISTS idx_company_energy_country ON company_energy_prices (country, energy_id);
CREATE INDEX IF NOT EXISTS idx_company_energy_updated ON company_energy_prices (updated_at DESC);

-- Backward compatible ALTERs if table was created by an earlier patch.
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS reference_price_country_idr NUMERIC(18,4);
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS reference_price_global_usd NUMERIC(18,4);
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS fx_rate_to_idr NUMERIC(18,6);
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS price_source VARCHAR(120) NOT NULL DEFAULT 'REFERENCE_COUNTRY';
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS reference_source VARCHAR(255) NOT NULL DEFAULT 'ALELS_SEED_REFERENCE';
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS last_reference_update_at TIMESTAMPTZ;
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS manual_override BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

INSERT INTO energy_types (energy_code, energy_name, energy_group, unit, vehicle_usage, sort_order)
VALUES
('GASOLINE_RON88', 'Gasoline RON 88', 'GASOLINE', 'liter', 'Legacy gasoline vehicles in selected markets', 10),
('GASOLINE_RON90', 'Gasoline RON 90', 'GASOLINE', 'liter', 'Gasoline vehicles; Indonesia Pertalite equivalent', 20),
('GASOLINE_RON91', 'Gasoline RON 91', 'GASOLINE', 'liter', 'Gasoline vehicles in selected markets', 30),
('GASOLINE_RON92', 'Gasoline RON 92', 'GASOLINE', 'liter', 'Gasoline vehicles; Indonesia Pertamax equivalent', 40),
('GASOLINE_RON95', 'Gasoline RON 95', 'GASOLINE', 'liter', 'Premium gasoline vehicles', 50),
('GASOLINE_RON98', 'Gasoline RON 98', 'GASOLINE', 'liter', 'High performance gasoline vehicles', 60),
('GASOLINE_E10', 'Gasoline E10', 'GASOLINE', 'liter', 'Gasoline blended with 10% ethanol', 70),
('GASOLINE_E20', 'Gasoline E20', 'GASOLINE', 'liter', 'Gasoline blended with 20% ethanol', 80),
('GASOLINE_E85', 'Gasoline E85', 'GASOLINE', 'liter', 'Flexible fuel vehicles', 90),
('DIESEL_B0', 'Diesel B0', 'DIESEL', 'liter', 'Standard fossil diesel', 110),
('DIESEL_B7', 'Diesel B7', 'DIESEL', 'liter', 'Diesel blended with 7% biodiesel', 120),
('DIESEL_B10', 'Diesel B10', 'DIESEL', 'liter', 'Diesel blended with 10% biodiesel', 130),
('DIESEL_B20', 'Diesel B20', 'DIESEL', 'liter', 'Diesel blended with 20% biodiesel', 140),
('DIESEL_B30', 'Diesel B30', 'DIESEL', 'liter', 'Diesel blended with 30% biodiesel', 150),
('DIESEL_B35', 'Diesel B35', 'DIESEL', 'liter', 'Diesel blended with 35% biodiesel', 160),
('DIESEL_EURO4', 'Diesel Euro 4', 'DIESEL', 'liter', 'Low sulfur diesel for Euro 4 vehicles', 170),
('DIESEL_EURO5', 'Diesel Euro 5', 'DIESEL', 'liter', 'Low sulfur diesel for Euro 5 vehicles', 180),
('BIODIESEL_B100', 'Biodiesel B100', 'DIESEL', 'liter', 'Pure biodiesel vehicles or special fleets', 190),
('LPG_AUTOGAS', 'LPG Autogas', 'GAS', 'liter', 'LPG/autogas vehicles', 210),
('CNG', 'Compressed Natural Gas', 'GAS', 'Nm3', 'CNG vehicles', 220),
('LNG', 'Liquefied Natural Gas', 'GAS', 'kg', 'Heavy duty LNG vehicles', 230),
('HYDROGEN_350_BAR', 'Hydrogen 350 bar', 'HYDROGEN', 'kg', 'Hydrogen fuel cell heavy duty vehicles', 240),
('HYDROGEN_700_BAR', 'Hydrogen 700 bar', 'HYDROGEN', 'kg', 'Hydrogen fuel cell light vehicles', 250),
('ELECTRIC_AC', 'Electric Charging AC', 'ELECTRIC', 'kWh', 'Battery electric vehicles AC charging', 310),
('ELECTRIC_DC_FAST', 'Electric Charging DC Fast', 'ELECTRIC', 'kWh', 'Battery electric vehicles DC fast charging', 320),
('ELECTRIC_DEPOT', 'Electric Charging Depot', 'ELECTRIC', 'kWh', 'Fleet depot charging', 330),
('ETHANOL_E100', 'Ethanol E100', 'ALTERNATIVE', 'liter', 'Ethanol vehicles in selected markets', 410),
('METHANOL_M100', 'Methanol M100', 'ALTERNATIVE', 'liter', 'Methanol vehicles or industrial fleets', 420),
('HVO_RENEWABLE_DIESEL', 'HVO Renewable Diesel', 'ALTERNATIVE', 'liter', 'Renewable diesel fleets', 430),
('SAF_SYNTHETIC_DIESEL', 'Synthetic Diesel', 'ALTERNATIVE', 'liter', 'Synthetic diesel fleet use', 440)
ON CONFLICT (energy_code) DO UPDATE
SET energy_name = EXCLUDED.energy_name,
    energy_group = EXCLUDED.energy_group,
    unit = EXCLUDED.unit,
    vehicle_usage = EXCLUDED.vehicle_usage,
    sort_order = EXCLUDED.sort_order,
    status = 'ACTIVE',
    updated_at = NOW();

-- Reference values are seed/reference only, not a live market feed.
-- They are stored read-only for users and can later be refreshed by a backend scheduler/provider.
WITH seed AS (
    SELECT * FROM (VALUES
        ('GASOLINE_RON88', 'Indonesia', 'IDR', 9000.0000, 9000.0000, 0.5600, 16000.000000, 'ALELS_SEED_REFERENCE: conservative historical/local benchmark'),
        ('GASOLINE_RON90', 'Indonesia', 'IDR', 10000.0000, 10000.0000, 0.6250, 16000.000000, 'ALELS_SEED_REFERENCE: Indonesia Pertalite benchmark'),
        ('GASOLINE_RON91', 'Indonesia', 'IDR', 12500.0000, 12500.0000, 0.7813, 16000.000000, 'ALELS_SEED_REFERENCE: regional gasoline benchmark'),
        ('GASOLINE_RON92', 'Indonesia', 'IDR', 12950.0000, 12950.0000, 0.8094, 16000.000000, 'ALELS_SEED_REFERENCE: Indonesia Pertamax benchmark'),
        ('GASOLINE_RON95', 'Indonesia', 'IDR', 14000.0000, 14000.0000, 0.8750, 16000.000000, 'ALELS_SEED_REFERENCE: premium gasoline benchmark'),
        ('GASOLINE_RON98', 'Indonesia', 'IDR', 15000.0000, 15000.0000, 0.9375, 16000.000000, 'ALELS_SEED_REFERENCE: high-octane gasoline benchmark'),
        ('GASOLINE_E10', 'Indonesia', 'IDR', 12800.0000, 12800.0000, 0.8000, 16000.000000, 'ALELS_SEED_REFERENCE: ethanol blend benchmark'),
        ('GASOLINE_E20', 'Indonesia', 'IDR', 12600.0000, 12600.0000, 0.7875, 16000.000000, 'ALELS_SEED_REFERENCE: ethanol blend benchmark'),
        ('GASOLINE_E85', 'Indonesia', 'IDR', 11000.0000, 11000.0000, 0.6875, 16000.000000, 'ALELS_SEED_REFERENCE: flexible fuel benchmark'),
        ('DIESEL_B0', 'Indonesia', 'IDR', 6800.0000, 6800.0000, 0.4250, 16000.000000, 'ALELS_SEED_REFERENCE: diesel benchmark'),
        ('DIESEL_B7', 'Indonesia', 'IDR', 7000.0000, 7000.0000, 0.4375, 16000.000000, 'ALELS_SEED_REFERENCE: diesel blend benchmark'),
        ('DIESEL_B10', 'Indonesia', 'IDR', 7100.0000, 7100.0000, 0.4438, 16000.000000, 'ALELS_SEED_REFERENCE: diesel blend benchmark'),
        ('DIESEL_B20', 'Indonesia', 'IDR', 7200.0000, 7200.0000, 0.4500, 16000.000000, 'ALELS_SEED_REFERENCE: diesel blend benchmark'),
        ('DIESEL_B30', 'Indonesia', 'IDR', 7300.0000, 7300.0000, 0.4563, 16000.000000, 'ALELS_SEED_REFERENCE: diesel blend benchmark'),
        ('DIESEL_B35', 'Indonesia', 'IDR', 7500.0000, 7500.0000, 0.4688, 16000.000000, 'ALELS_SEED_REFERENCE: Indonesia biodiesel benchmark'),
        ('DIESEL_EURO4', 'Indonesia', 'IDR', 13500.0000, 13500.0000, 0.8438, 16000.000000, 'ALELS_SEED_REFERENCE: low sulfur diesel benchmark'),
        ('DIESEL_EURO5', 'Indonesia', 'IDR', 14500.0000, 14500.0000, 0.9063, 16000.000000, 'ALELS_SEED_REFERENCE: low sulfur diesel benchmark'),
        ('BIODIESEL_B100', 'Indonesia', 'IDR', 15000.0000, 15000.0000, 0.9375, 16000.000000, 'ALELS_SEED_REFERENCE: biodiesel benchmark'),
        ('LPG_AUTOGAS', 'Indonesia', 'IDR', 7000.0000, 7000.0000, 0.4375, 16000.000000, 'ALELS_SEED_REFERENCE: LPG/autogas benchmark'),
        ('CNG', 'Indonesia', 'IDR', 4500.0000, 4500.0000, 0.2813, 16000.000000, 'ALELS_SEED_REFERENCE: CNG benchmark per Nm3'),
        ('LNG', 'Indonesia', 'IDR', 12000.0000, 12000.0000, 0.7500, 16000.000000, 'ALELS_SEED_REFERENCE: LNG benchmark per kg'),
        ('HYDROGEN_350_BAR', 'Indonesia', 'IDR', 160000.0000, 160000.0000, 10.0000, 16000.000000, 'ALELS_SEED_REFERENCE: hydrogen benchmark per kg'),
        ('HYDROGEN_700_BAR', 'Indonesia', 'IDR', 180000.0000, 180000.0000, 11.2500, 16000.000000, 'ALELS_SEED_REFERENCE: hydrogen benchmark per kg'),
        ('ELECTRIC_AC', 'Indonesia', 'IDR', 2500.0000, 2500.0000, 0.1563, 16000.000000, 'ALELS_SEED_REFERENCE: EV AC charging benchmark per kWh'),
        ('ELECTRIC_DC_FAST', 'Indonesia', 'IDR', 3500.0000, 3500.0000, 0.2188, 16000.000000, 'ALELS_SEED_REFERENCE: EV DC fast charging benchmark per kWh'),
        ('ELECTRIC_DEPOT', 'Indonesia', 'IDR', 1700.0000, 1700.0000, 0.1063, 16000.000000, 'ALELS_SEED_REFERENCE: depot electricity tariff benchmark per kWh'),
        ('ETHANOL_E100', 'Indonesia', 'IDR', 13000.0000, 13000.0000, 0.8125, 16000.000000, 'ALELS_SEED_REFERENCE: ethanol benchmark'),
        ('METHANOL_M100', 'Indonesia', 'IDR', 10000.0000, 10000.0000, 0.6250, 16000.000000, 'ALELS_SEED_REFERENCE: methanol benchmark'),
        ('HVO_RENEWABLE_DIESEL', 'Indonesia', 'IDR', 24000.0000, 24000.0000, 1.5000, 16000.000000, 'ALELS_SEED_REFERENCE: HVO benchmark'),
        ('SAF_SYNTHETIC_DIESEL', 'Indonesia', 'IDR', 30000.0000, 30000.0000, 1.8750, 16000.000000, 'ALELS_SEED_REFERENCE: synthetic diesel benchmark')
    ) AS t(energy_code, country, country_currency, reference_price_country, reference_price_country_idr, reference_price_global_usd, fx_rate_to_idr, source)
)
INSERT INTO energy_reference_prices (
    energy_id,
    country,
    country_currency,
    reference_price_country,
    reference_price_country_idr,
    reference_price_global_usd,
    fx_rate_to_idr,
    source,
    provider_updated_at
)
SELECT et.id,
       seed.country,
       seed.country_currency,
       seed.reference_price_country,
       seed.reference_price_country_idr,
       seed.reference_price_global_usd,
       seed.fx_rate_to_idr,
       seed.source,
       NOW()
FROM seed
JOIN energy_types et ON et.energy_code = seed.energy_code
ON CONFLICT (energy_id, country) DO UPDATE
SET country_currency = EXCLUDED.country_currency,
    reference_price_country = EXCLUDED.reference_price_country,
    reference_price_country_idr = EXCLUDED.reference_price_country_idr,
    reference_price_global_usd = EXCLUDED.reference_price_global_usd,
    fx_rate_to_idr = EXCLUDED.fx_rate_to_idr,
    source = EXCLUDED.source,
    provider_updated_at = EXCLUDED.provider_updated_at,
    updated_at = NOW();

-- Backfill company operational prices for existing active companies.
INSERT INTO company_energy_prices (
    company_id,
    energy_id,
    country,
    currency,
    price_energy,
    reference_price_country_idr,
    reference_price_global_usd,
    fx_rate_to_idr,
    price_source,
    reference_source,
    last_reference_update_at,
    manual_override
)
SELECT c.id,
       et.id,
       COALESCE(NULLIF(c.country, ''), 'Indonesia'),
       COALESCE(er.country_currency, 'IDR'),
       COALESCE(er.reference_price_country_idr, 0),
       er.reference_price_country_idr,
       er.reference_price_global_usd,
       er.fx_rate_to_idr,
       'REFERENCE_COUNTRY',
       COALESCE(er.source, 'ALELS_SEED_REFERENCE'),
       COALESCE(er.provider_updated_at, NOW()),
       FALSE
FROM companies c
CROSS JOIN energy_types et
LEFT JOIN energy_reference_prices er
       ON er.energy_id = et.id
      AND er.country = COALESCE(NULLIF(c.country, ''), 'Indonesia')
WHERE c.deleted_at IS NULL
  AND et.status = 'ACTIVE'
ON CONFLICT (company_id, energy_id, country) DO UPDATE
SET reference_price_country_idr = COALESCE(EXCLUDED.reference_price_country_idr, company_energy_prices.reference_price_country_idr),
    reference_price_global_usd = COALESCE(EXCLUDED.reference_price_global_usd, company_energy_prices.reference_price_global_usd),
    fx_rate_to_idr = COALESCE(EXCLUDED.fx_rate_to_idr, company_energy_prices.fx_rate_to_idr),
    reference_source = COALESCE(EXCLUDED.reference_source, company_energy_prices.reference_source),
    last_reference_update_at = COALESCE(EXCLUDED.last_reference_update_at, company_energy_prices.last_reference_update_at),
    price_energy = CASE
        WHEN company_energy_prices.manual_override = FALSE THEN COALESCE(EXCLUDED.price_energy, company_energy_prices.price_energy)
        ELSE company_energy_prices.price_energy
    END,
    updated_at = NOW();
