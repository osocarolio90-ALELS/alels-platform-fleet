-- ALELS 1M FOUNDATION MODE
-- Asset Register - ASEAN Energy Country Reference Foundation
-- Purpose:
-- - add country/provider reference master
-- - seed ASEAN countries
-- - allow Harga Energy to follow selected reference country
-- - keep operational Price Energy editable but reference prices controlled/read-only

CREATE TABLE IF NOT EXISTS energy_reference_countries (
    id BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(10) NOT NULL UNIQUE,
    country_name VARCHAR(120) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    usd_to_local_rate NUMERIC(18,6) NOT NULL DEFAULT 1,
    source_name VARCHAR(120) NOT NULL DEFAULT 'ALELS_REFERENCE_PROVIDER',
    source_url TEXT,
    provider_status VARCHAR(40) NOT NULL DEFAULT 'SEEDED',
    provider_last_update_at TIMESTAMPTZ,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_energy_reference_countries_status ON energy_reference_countries(status, country_name);

INSERT INTO energy_reference_countries (country_code, country_name, currency, usd_to_local_rate, source_name, source_url, provider_status, provider_last_update_at)
VALUES
('ID', 'Indonesia', 'IDR', 16000.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Indonesia/', 'SEEDED', NOW()),
('MY', 'Malaysia', 'MYR', 4.700000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Malaysia/', 'SEEDED', NOW()),
('SG', 'Singapore', 'SGD', 1.350000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Singapore/', 'SEEDED', NOW()),
('TH', 'Thailand', 'THB', 36.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Thailand/', 'SEEDED', NOW()),
('VN', 'Vietnam', 'VND', 25000.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Vietnam/', 'SEEDED', NOW()),
('PH', 'Philippines', 'PHP', 58.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Philippines/', 'SEEDED', NOW()),
('BN', 'Brunei Darussalam', 'BND', 1.350000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Brunei-Darussalam/', 'SEEDED', NOW()),
('KH', 'Cambodia', 'KHR', 4100.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Cambodia/', 'SEEDED', NOW()),
('LA', 'Laos', 'LAK', 22000.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Laos/', 'SEEDED', NOW()),
('MM', 'Myanmar', 'MMK', 2100.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Myanmar/', 'SEEDED', NOW()),
('TL', 'Timor-Leste', 'USD', 1.000000, 'ALELS_ASEAN_SEED', 'https://www.globalpetrolprices.com/Timor-Leste/', 'SEEDED', NOW())
ON CONFLICT (country_code) DO UPDATE
SET country_name = EXCLUDED.country_name,
    currency = EXCLUDED.currency,
    usd_to_local_rate = EXCLUDED.usd_to_local_rate,
    source_name = EXCLUDED.source_name,
    source_url = EXCLUDED.source_url,
    provider_status = EXCLUDED.provider_status,
    provider_last_update_at = EXCLUDED.provider_last_update_at,
    status = 'ACTIVE',
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS energy_reference_prices (
    id BIGSERIAL PRIMARY KEY,
    energy_id BIGINT NOT NULL REFERENCES energy_types(id) ON DELETE CASCADE,
    energy_code VARCHAR(100),
    country_code VARCHAR(10) NOT NULL DEFAULT 'ID',
    country_name VARCHAR(120) NOT NULL DEFAULT 'Indonesia',
    currency VARCHAR(10) NOT NULL DEFAULT 'IDR',
    unit VARCHAR(40) NOT NULL DEFAULT 'liter',
    reference_price_country NUMERIC(18,4) NOT NULL DEFAULT 0,
    reference_price_global_usd NUMERIC(18,4) NOT NULL DEFAULT 0,
    provider_reference_price_country NUMERIC(18,4) NOT NULL DEFAULT 0,
    provider_reference_price_global_usd NUMERIC(18,4) NOT NULL DEFAULT 0,
    source_name VARCHAR(120) NOT NULL DEFAULT 'ALELS_REFERENCE_PROVIDER',
    source_detail TEXT NOT NULL DEFAULT 'ALELS controlled reference baseline. Replace with official provider integration when configured.',
    source_url TEXT,
    provider_status VARCHAR(40) NOT NULL DEFAULT 'SEEDED',
    provider_last_update_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS energy_code VARCHAR(100);
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS country_code VARCHAR(10) NOT NULL DEFAULT 'ID';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS country_name VARCHAR(120) NOT NULL DEFAULT 'Indonesia';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'IDR';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS unit VARCHAR(40) NOT NULL DEFAULT 'liter';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS reference_price_global_usd NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS provider_reference_price_country NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS provider_reference_price_global_usd NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS source_name VARCHAR(120) NOT NULL DEFAULT 'ALELS_REFERENCE_PROVIDER';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS source_detail TEXT NOT NULL DEFAULT 'ALELS controlled reference baseline. Replace with official provider integration when configured.';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS source_url TEXT;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS provider_status VARCHAR(40) NOT NULL DEFAULT 'SEEDED';
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS provider_last_update_at TIMESTAMPTZ;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMPTZ;
ALTER TABLE energy_reference_prices ADD COLUMN IF NOT EXISTS updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

UPDATE energy_reference_prices erp
SET energy_code = COALESCE(erp.energy_code, et.energy_code),
    country_code = CASE
        WHEN UPPER(COALESCE(erp.country_code, '')) <> '' THEN UPPER(erp.country_code)
        WHEN LOWER(COALESCE(erp.country, '')) = 'indonesia' THEN 'ID'
        WHEN LOWER(COALESCE(erp.country, '')) = 'malaysia' THEN 'MY'
        WHEN LOWER(COALESCE(erp.country, '')) = 'singapore' THEN 'SG'
        WHEN LOWER(COALESCE(erp.country, '')) = 'thailand' THEN 'TH'
        WHEN LOWER(COALESCE(erp.country, '')) = 'vietnam' THEN 'VN'
        WHEN LOWER(COALESCE(erp.country, '')) = 'philippines' THEN 'PH'
        ELSE 'ID'
    END,
    country_name = COALESCE(NULLIF(erp.country_name, ''), COALESCE(erp.country, 'Indonesia')),
    currency = COALESCE(NULLIF(erp.currency, ''), COALESCE(erp.country_currency, 'IDR')),
    unit = COALESCE(NULLIF(erp.unit, ''), et.unit),
    source_name = COALESCE(NULLIF(erp.source_name, ''), COALESCE(erp.source, 'ALELS_REFERENCE_PROVIDER')),
    source_detail = COALESCE(NULLIF(erp.source_detail, ''), COALESCE(erp.source, 'ALELS controlled seed reference')),
    provider_last_update_at = COALESCE(erp.provider_last_update_at, erp.provider_updated_at, NOW()),
    last_sync_at = COALESCE(erp.last_sync_at, erp.provider_updated_at, NOW())
FROM energy_types et
WHERE erp.energy_id = et.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_energy_reference_prices_energy_country_code ON energy_reference_prices(energy_id, country_code);
CREATE INDEX IF NOT EXISTS idx_energy_reference_prices_country_code ON energy_reference_prices(country_code, energy_id);
CREATE INDEX IF NOT EXISTS idx_energy_reference_prices_updated_at ON energy_reference_prices(updated_at DESC);

CREATE TABLE IF NOT EXISTS energy_reference_update_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    reference_id BIGINT REFERENCES energy_reference_prices(id) ON DELETE SET NULL,
    energy_code VARCHAR(100),
    country_code VARCHAR(10),
    action VARCHAR(80) NOT NULL,
    old_reference_price_country NUMERIC(18,4),
    new_reference_price_country NUMERIC(18,4),
    old_reference_price_global_usd NUMERIC(18,4),
    new_reference_price_global_usd NUMERIC(18,4),
    source_name VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_energy_reference_log_time ON energy_reference_update_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_energy_reference_log_actor ON energy_reference_update_logs(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_energy_reference_log_reference ON energy_reference_update_logs(reference_id, created_at DESC);

ALTER TABLE company_energy_prices ADD COLUMN IF NOT EXISTS country_code VARCHAR(10) NOT NULL DEFAULT 'ID';

UPDATE company_energy_prices
SET country_code = CASE
    WHEN UPPER(COALESCE(country_code, '')) <> '' THEN UPPER(country_code)
    WHEN LOWER(COALESCE(country, '')) = 'indonesia' THEN 'ID'
    WHEN LOWER(COALESCE(country, '')) = 'malaysia' THEN 'MY'
    WHEN LOWER(COALESCE(country, '')) = 'singapore' THEN 'SG'
    WHEN LOWER(COALESCE(country, '')) = 'thailand' THEN 'TH'
    WHEN LOWER(COALESCE(country, '')) = 'vietnam' THEN 'VN'
    WHEN LOWER(COALESCE(country, '')) = 'philippines' THEN 'PH'
    ELSE 'ID'
END;

WITH global_price AS (
    SELECT et.id AS energy_id,
           et.energy_code,
           et.energy_name,
           et.unit,
           CASE et.energy_code
               WHEN 'GASOLINE_RON88' THEN 0.5600
               WHEN 'GASOLINE_RON90' THEN 0.6250
               WHEN 'GASOLINE_RON91' THEN 0.7813
               WHEN 'GASOLINE_RON92' THEN 0.8094
               WHEN 'GASOLINE_RON95' THEN 0.8750
               WHEN 'GASOLINE_RON98' THEN 0.9375
               WHEN 'GASOLINE_E10' THEN 0.8000
               WHEN 'GASOLINE_E20' THEN 0.7875
               WHEN 'GASOLINE_E85' THEN 0.6875
               WHEN 'DIESEL_B0' THEN 0.4250
               WHEN 'DIESEL_B7' THEN 0.4375
               WHEN 'DIESEL_B10' THEN 0.4438
               WHEN 'DIESEL_B20' THEN 0.4500
               WHEN 'DIESEL_B30' THEN 0.4563
               WHEN 'DIESEL_B35' THEN 0.4688
               WHEN 'DIESEL_EURO4' THEN 0.8438
               WHEN 'DIESEL_EURO5' THEN 0.9063
               WHEN 'BIODIESEL_B100' THEN 0.9375
               WHEN 'LPG_AUTOGAS' THEN 0.4375
               WHEN 'CNG' THEN 0.2813
               WHEN 'LNG' THEN 0.7500
               WHEN 'HYDROGEN_350_BAR' THEN 10.0000
               WHEN 'HYDROGEN_700_BAR' THEN 11.2500
               WHEN 'ELECTRIC_AC' THEN 0.1563
               WHEN 'ELECTRIC_DC_FAST' THEN 0.2188
               WHEN 'ELECTRIC_DEPOT' THEN 0.1063
               WHEN 'ETHANOL_E100' THEN 0.8125
               WHEN 'METHANOL_M100' THEN 0.6250
               WHEN 'HVO_RENEWABLE_DIESEL' THEN 1.5000
               WHEN 'SAF_SYNTHETIC_DIESEL' THEN 1.8750
               ELSE 0.0000
           END AS global_usd
    FROM energy_types et
    WHERE et.status = 'ACTIVE'
)
INSERT INTO energy_reference_prices (
    energy_id,
    energy_code,
    country_code,
    country_name,
    currency,
    unit,
    reference_price_country,
    reference_price_global_usd,
    provider_reference_price_country,
    provider_reference_price_global_usd,
    source_name,
    source_detail,
    source_url,
    provider_status,
    provider_last_update_at,
    last_sync_at
)
SELECT gp.energy_id,
       gp.energy_code,
       c.country_code,
       c.country_name,
       c.currency,
       gp.unit,
       ROUND(gp.global_usd * c.usd_to_local_rate, 4),
       gp.global_usd,
       ROUND(gp.global_usd * c.usd_to_local_rate, 4),
       gp.global_usd,
       c.source_name,
       CONCAT('ALELS ASEAN seed reference for ', gp.energy_name, ' in ', c.country_name, '. Provider URL can be updated by SUPERADMIN.'),
       c.source_url,
       c.provider_status,
       COALESCE(c.provider_last_update_at, NOW()),
       NOW()
FROM global_price gp
CROSS JOIN energy_reference_countries c
WHERE c.status = 'ACTIVE'
ON CONFLICT (energy_id, country_code) DO UPDATE
SET energy_code = EXCLUDED.energy_code,
    country_name = EXCLUDED.country_name,
    currency = EXCLUDED.currency,
    unit = EXCLUDED.unit,
    provider_reference_price_country = EXCLUDED.provider_reference_price_country,
    provider_reference_price_global_usd = EXCLUDED.provider_reference_price_global_usd,
    source_name = EXCLUDED.source_name,
    source_detail = EXCLUDED.source_detail,
    source_url = EXCLUDED.source_url,
    provider_status = EXCLUDED.provider_status,
    provider_last_update_at = EXCLUDED.provider_last_update_at,
    updated_at = NOW();

UPDATE company_energy_prices cep
SET country = c.country_name,
    currency = c.currency,
    reference_price_country_idr = erp.reference_price_country,
    reference_price_global_usd = erp.reference_price_global_usd,
    fx_rate_to_idr = CASE WHEN c.currency = 'IDR' THEN 1 ELSE NULL END,
    reference_source = erp.source_name,
    last_reference_update_at = erp.last_sync_at,
    price_energy = CASE WHEN cep.manual_override = FALSE THEN erp.reference_price_country ELSE cep.price_energy END,
    updated_at = NOW()
FROM energy_reference_prices erp
JOIN energy_reference_countries c ON c.country_code = erp.country_code
WHERE cep.energy_id = erp.energy_id
  AND cep.country_code = erp.country_code;
