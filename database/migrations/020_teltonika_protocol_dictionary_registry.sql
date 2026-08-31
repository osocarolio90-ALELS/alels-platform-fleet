-- Register the Teltonika protocols and the dictionary resources shipped in the gateway.
-- Idempotent and safe for databases that already contain the corresponding master rows.

INSERT INTO protocol_registry (
    protocol_code, protocol_name, brand_code, protocol_family,
    detector_code, parser_code, transport_type, direction, status
)
VALUES
    ('TELTONIKA_IMEI', 'Teltonika IMEI handshake', 'TELTONIKA', 'TELTONIKA',
     'TELTONIKA_AUTO', 'TELTONIKA_IMEI', 'TCP', 'INBOUND', 'ACTIVE'),
    ('TELTONIKA_CODEC8', 'Teltonika Codec 8', 'TELTONIKA', 'TELTONIKA',
     'TELTONIKA_AUTO', 'CODEC8', 'TCP_UDP', 'BIDIRECTIONAL', 'ACTIVE'),
    ('TELTONIKA_CODEC8E', 'Teltonika Codec 8 Extended', 'TELTONIKA', 'TELTONIKA',
     'TELTONIKA_AUTO', 'CODEC8E', 'TCP_UDP', 'BIDIRECTIONAL', 'ACTIVE'),
    ('TELTONIKA_CODEC12_RESPONSE', 'Teltonika Codec 12 response', 'TELTONIKA', 'TELTONIKA',
     'TELTONIKA_AUTO', 'CODEC12', 'TCP', 'INBOUND', 'ACTIVE'),
    ('ALELS_JSON', 'ALELS JSON telemetry', 'ALELS', 'ALELS_JSON',
     'ALELS_JSON', 'ALELS_JSON', 'TCP', 'BIDIRECTIONAL', 'ACTIVE')
ON CONFLICT (protocol_code) DO UPDATE SET
    protocol_name = EXCLUDED.protocol_name,
    brand_code = EXCLUDED.brand_code,
    protocol_family = EXCLUDED.protocol_family,
    detector_code = EXCLUDED.detector_code,
    parser_code = EXCLUDED.parser_code,
    transport_type = EXCLUDED.transport_type,
    direction = EXCLUDED.direction,
    status = 'ACTIVE';

INSERT INTO device_brands (
    brand_code, brand_name, status, is_active, is_system, sort_order, description
)
VALUES ('TELTONIKA', 'Teltonika', 'ACTIVE', TRUE, TRUE, 10, 'ALELS supported device brand')
ON CONFLICT (brand_code) DO UPDATE SET
    brand_name = EXCLUDED.brand_name,
    status = 'ACTIVE',
    is_active = TRUE,
    updated_at = NOW();

WITH model_seed(model_code, model_name, dictionary_code, sort_order) AS (
    VALUES
        ('FMB150', 'FMB150', 'FMB150', 10),
        ('FMC150', 'FMC150', 'FMC150', 20),
        ('FMC650', 'FMC650', 'FMC650', 30),
        ('FMB003', 'FMB003', 'FMB003', 40),
        ('FMC003', 'FMC003', 'FMC003', 50)
)
INSERT INTO device_models (
    brand_id, model_code, model_name, vendor, protocol_code, parser_code,
    dictionary_code, status, is_active, is_system, sort_order, description
)
SELECT brand.id, seed.model_code, seed.model_name, brand.brand_name,
       'TELTONIKA_CODEC8E', 'TELTONIKA_AUTO', seed.dictionary_code,
       'ACTIVE', TRUE, TRUE, seed.sort_order, 'Dictionary-backed Teltonika device model'
FROM model_seed seed
JOIN device_brands brand ON brand.brand_code = 'TELTONIKA'
ON CONFLICT (model_code) DO UPDATE SET
    brand_id = EXCLUDED.brand_id,
    model_name = EXCLUDED.model_name,
    vendor = EXCLUDED.vendor,
    protocol_code = EXCLUDED.protocol_code,
    parser_code = EXCLUDED.parser_code,
    dictionary_code = EXCLUDED.dictionary_code,
    status = 'ACTIVE',
    is_active = TRUE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW();

WITH dictionary_seed(model_code, dictionary_code, dictionary_name, dictionary_file) AS (
    VALUES
        ('FMB150', 'FMB150', 'Teltonika FMB150 AVL dictionary', 'fmb150.json'),
        ('FMC150', 'FMC150', 'Teltonika FMC150 AVL dictionary', 'fmc150.json'),
        ('FMC650', 'FMC650', 'Teltonika FMC650 AVL dictionary', 'fmc650.json'),
        ('FMB003', 'FMB003', 'Teltonika FMB003 AVL dictionary', 'fmb003.json'),
        ('FMC003', 'FMC003', 'Teltonika FMC003 AVL dictionary', 'fmc003.json')
)
INSERT INTO dictionary_registry (
    device_model_id, dictionary_code, dictionary_name, dictionary_file,
    dictionary_version, source_type, source_path, device_model, file_path,
    status, updated_at
)
SELECT model.id, seed.dictionary_code, seed.dictionary_name, seed.dictionary_file,
       '1', 'CLASSPATH', 'device-dictionary/' || seed.dictionary_file,
       seed.model_code, 'device-dictionary/' || seed.dictionary_file,
       'ACTIVE', NOW()
FROM dictionary_seed seed
JOIN device_models model ON model.model_code = seed.model_code
ON CONFLICT (dictionary_code) DO UPDATE SET
    device_model_id = EXCLUDED.device_model_id,
    dictionary_name = EXCLUDED.dictionary_name,
    dictionary_file = EXCLUDED.dictionary_file,
    dictionary_version = EXCLUDED.dictionary_version,
    source_type = EXCLUDED.source_type,
    source_path = EXCLUDED.source_path,
    device_model = EXCLUDED.device_model,
    file_path = EXCLUDED.file_path,
    status = 'ACTIVE',
    updated_at = NOW();
