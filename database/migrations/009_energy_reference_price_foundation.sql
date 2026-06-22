-- ALELS 1M FOUNDATION MODE
-- Energy Reference Price Foundation
-- Purpose:
--   Keep operational company price separated from objective reference price.
--   Reference prices are controlled by SUPERADMIN/ADMIN workflow and future provider sync.

CREATE TABLE IF NOT EXISTS energy_reference_prices (
    id BIGSERIAL PRIMARY KEY,
    energy_id BIGINT NOT NULL REFERENCES energy_types(id) ON DELETE CASCADE,
    energy_code VARCHAR(100) NOT NULL,
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
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_energy_reference_country UNIQUE (energy_id, country_code)
);

CREATE INDEX IF NOT EXISTS idx_energy_reference_country ON energy_reference_prices(country_code, energy_code);
CREATE INDEX IF NOT EXISTS idx_energy_reference_energy ON energy_reference_prices(energy_id);
CREATE INDEX IF NOT EXISTS idx_energy_reference_updated_at ON energy_reference_prices(updated_at DESC);

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
    provider_status,
    provider_last_update_at,
    last_sync_at
)
SELECT et.id,
       et.energy_code,
       'ID',
       'Indonesia',
       'IDR',
       et.unit,
       CASE et.energy_code
           WHEN 'GASOLINE_RON88' THEN 9000
           WHEN 'GASOLINE_RON90' THEN 10000
           WHEN 'GASOLINE_RON91' THEN 12500
           WHEN 'GASOLINE_RON92' THEN 12950
           WHEN 'GASOLINE_RON95' THEN 14000
           WHEN 'GASOLINE_RON98' THEN 15000
           WHEN 'GASOLINE_E10' THEN 12800
           WHEN 'GASOLINE_E20' THEN 12600
           WHEN 'GASOLINE_E85' THEN 11000
           WHEN 'DIESEL_B0' THEN 6800
           WHEN 'DIESEL_B7' THEN 7200
           WHEN 'DIESEL_B20' THEN 7600
           WHEN 'DIESEL_B30' THEN 7800
           WHEN 'DIESEL_B35' THEN 8000
           WHEN 'DIESEL_EURO5' THEN 18500
           WHEN 'BIODIESEL_B100' THEN 12000
           WHEN 'LPG_AUTOGAS' THEN 7000
           WHEN 'CNG' THEN 4500
           WHEN 'LNG' THEN 9000
           WHEN 'HYDROGEN' THEN 160000
           WHEN 'EV_AC' THEN 2467
           WHEN 'EV_DC' THEN 2467
           ELSE 0
       END,
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
           WHEN 'DIESEL_B7' THEN 0.4500
           WHEN 'DIESEL_B20' THEN 0.4750
           WHEN 'DIESEL_B30' THEN 0.4875
           WHEN 'DIESEL_B35' THEN 0.5000
           WHEN 'DIESEL_EURO5' THEN 1.1563
           WHEN 'BIODIESEL_B100' THEN 0.7500
           WHEN 'LPG_AUTOGAS' THEN 0.4375
           WHEN 'CNG' THEN 0.2813
           WHEN 'LNG' THEN 0.5625
           WHEN 'HYDROGEN' THEN 10.0000
           WHEN 'EV_AC' THEN 0.1542
           WHEN 'EV_DC' THEN 0.1542
           ELSE 0
       END,
       CASE et.energy_code
           WHEN 'GASOLINE_RON88' THEN 9000
           WHEN 'GASOLINE_RON90' THEN 10000
           WHEN 'GASOLINE_RON91' THEN 12500
           WHEN 'GASOLINE_RON92' THEN 12950
           WHEN 'GASOLINE_RON95' THEN 14000
           WHEN 'GASOLINE_RON98' THEN 15000
           WHEN 'GASOLINE_E10' THEN 12800
           WHEN 'GASOLINE_E20' THEN 12600
           WHEN 'GASOLINE_E85' THEN 11000
           WHEN 'DIESEL_B0' THEN 6800
           WHEN 'DIESEL_B7' THEN 7200
           WHEN 'DIESEL_B20' THEN 7600
           WHEN 'DIESEL_B30' THEN 7800
           WHEN 'DIESEL_B35' THEN 8000
           WHEN 'DIESEL_EURO5' THEN 18500
           WHEN 'BIODIESEL_B100' THEN 12000
           WHEN 'LPG_AUTOGAS' THEN 7000
           WHEN 'CNG' THEN 4500
           WHEN 'LNG' THEN 9000
           WHEN 'HYDROGEN' THEN 160000
           WHEN 'EV_AC' THEN 2467
           WHEN 'EV_DC' THEN 2467
           ELSE 0
       END,
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
           WHEN 'DIESEL_B7' THEN 0.4500
           WHEN 'DIESEL_B20' THEN 0.4750
           WHEN 'DIESEL_B30' THEN 0.4875
           WHEN 'DIESEL_B35' THEN 0.5000
           WHEN 'DIESEL_EURO5' THEN 1.1563
           WHEN 'BIODIESEL_B100' THEN 0.7500
           WHEN 'LPG_AUTOGAS' THEN 0.4375
           WHEN 'CNG' THEN 0.2813
           WHEN 'LNG' THEN 0.5625
           WHEN 'HYDROGEN' THEN 10.0000
           WHEN 'EV_AC' THEN 0.1542
           WHEN 'EV_DC' THEN 0.1542
           ELSE 0
       END,
       'ALELS_REFERENCE_PROVIDER',
       CONCAT('ALELS controlled seed for ', et.energy_name, '. Replace by official provider scheduler when API is configured.'),
       'SEEDED',
       NOW(),
       NOW()
FROM energy_types et
WHERE et.status = 'ACTIVE'
ON CONFLICT (energy_id, country_code) DO NOTHING;

UPDATE company_energy_prices cep
SET reference_price_country_idr = erp.reference_price_country,
    reference_price_global_usd = erp.reference_price_global_usd,
    reference_source = erp.source_name,
    last_reference_update_at = erp.last_sync_at,
    updated_at = NOW()
FROM energy_reference_prices erp
JOIN energy_types et ON et.id = erp.energy_id
WHERE cep.energy_id = erp.energy_id
  AND erp.country_code = 'ID'
  AND (cep.reference_source IS NULL OR cep.reference_source IN ('LAZY_SEED', 'REFERENCE_COUNTRY', 'ALELS_REFERENCE_PROVIDER'));
