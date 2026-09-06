package com.alels.backend.assetregister.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryCreateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceUpdateRequest;

@Repository
public class EnergyReferenceRepository {
    private final JdbcTemplate jdbcTemplate;

    public EnergyReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EnergyReferenceCountryRow> findCountries() {
        return jdbcTemplate.query("""
                SELECT id,
                       country_code,
                       country_name,
                       currency,
                       usd_to_local_rate,
                       source_name,
                       source_url,
                       provider_status,
                       provider_last_update_at,
                       status,
                       updated_at
                FROM energy_reference_countries
                ORDER BY country_name
                """, countryMapper());
    }

    public void createCountry(EnergyReferenceCountryCreateRequest request, Long actorUserId, Long actorCompanyId) {
        String code = request.countryCode().trim().toUpperCase();
        jdbcTemplate.update("""
                INSERT INTO energy_reference_countries (
                    country_code,
                    country_name,
                    currency,
                    usd_to_local_rate,
                    source_name,
                    source_url,
                    provider_status,
                    provider_last_update_at,
                    created_by,
                    updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, 'MANUAL_PROVIDER_LINK', NOW(), ?, ?)
                ON CONFLICT (country_code) DO UPDATE
                SET country_name = EXCLUDED.country_name,
                    currency = EXCLUDED.currency,
                    usd_to_local_rate = EXCLUDED.usd_to_local_rate,
                    source_name = EXCLUDED.source_name,
                    source_url = EXCLUDED.source_url,
                    provider_status = 'MANUAL_PROVIDER_LINK',
                    provider_last_update_at = NOW(),
                    status = 'ACTIVE',
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                """,
                code,
                request.countryName().trim(),
                request.currency().trim().toUpperCase(),
                request.usdToLocalRate(),
                safe(request.sourceName(), "MANUAL_PROVIDER_LINK"),
                safe(request.sourceUrl(), null),
                actorUserId,
                actorUserId);
        seedCountryReferenceRows(code);
        logOrganization(actorUserId, actorCompanyId, "ENERGY_REFERENCE_COUNTRY", null, "UPSERT_REFERENCE_COUNTRY", "{\"countryCode\":\"" + escape(code) + "\"}");
    }

    public List<EnergyReferenceRow> findAll(String countryCode) {
        return jdbcTemplate.query("""
                SELECT erp.id,
                       erp.energy_id,
                       COALESCE(erp.energy_code, et.energy_code) AS energy_code,
                       et.energy_name,
                       et.energy_group,
                       erp.unit,
                       erp.country_code,
                       erp.country_name,
                       erp.currency,
                       erp.reference_price_country,
                       erp.reference_price_global_usd,
                       erp.provider_reference_price_country,
                       erp.provider_reference_price_global_usd,
                       erp.source_name,
                       erp.source_detail,
                       erp.source_url,
                       erp.provider_status,
                       erp.provider_last_update_at,
                       erp.last_sync_at,
                       erp.updated_at,
                       u.email AS updated_by_email
                FROM energy_reference_prices erp
                JOIN energy_types et ON et.id = erp.energy_id
                LEFT JOIN users u ON u.id = erp.updated_by
                WHERE et.status = 'ACTIVE'
                  AND erp.deleted_at IS NULL
                  AND (? IS NULL OR erp.country_code = UPPER(?))
                ORDER BY erp.country_name, et.sort_order, et.energy_name
                """, rowMapper(), blankToNull(countryCode), blankToNull(countryCode));
    }

    public Optional<EnergyReferenceSnapshot> findSnapshot(Long id) {
        return jdbcTemplate.query("""
                SELECT id,
                       energy_id,
                       COALESCE(energy_code, '') AS energy_code,
                       country_code,
                       reference_price_country,
                       reference_price_global_usd,
                       source_name
                FROM energy_reference_prices
                WHERE id = ?
                """, (rs, rowNum) -> new EnergyReferenceSnapshot(
                rs.getLong("id"),
                rs.getLong("energy_id"),
                rs.getString("energy_code"),
                rs.getString("country_code"),
                rs.getBigDecimal("reference_price_country"),
                rs.getBigDecimal("reference_price_global_usd"),
                rs.getString("source_name")
        ), id).stream().findFirst();
    }

    public int syncFromProvider(String countryCode, Long actorUserId, Long actorCompanyId) {
        List<EnergyReferenceSnapshot> before = jdbcTemplate.query("""
                SELECT id,
                       energy_id,
                       COALESCE(energy_code, '') AS energy_code,
                       country_code,
                       reference_price_country,
                       reference_price_global_usd,
                       source_name
                FROM energy_reference_prices
                WHERE (? IS NULL OR country_code = UPPER(?))
                ORDER BY id
                """, (rs, rowNum) -> new EnergyReferenceSnapshot(
                rs.getLong("id"),
                rs.getLong("energy_id"),
                rs.getString("energy_code"),
                rs.getString("country_code"),
                rs.getBigDecimal("reference_price_country"),
                rs.getBigDecimal("reference_price_global_usd"),
                rs.getString("source_name")
        ), blankToNull(countryCode), blankToNull(countryCode));

        int updated = jdbcTemplate.update("""
                UPDATE energy_reference_prices erp
                SET reference_price_country = erp.provider_reference_price_country,
                    reference_price_global_usd = erp.provider_reference_price_global_usd,
                    source_name = COALESCE(NULLIF(erc.source_name, ''), erp.source_name),
                    source_url = COALESCE(NULLIF(erc.source_url, ''), erp.source_url),
                    provider_status = 'SYNCED',
                    provider_last_update_at = NOW(),
                    last_sync_at = NOW(),
                    updated_by = ?,
                    updated_at = NOW()
                FROM energy_reference_countries erc
                WHERE erp.country_code = erc.country_code
                  AND (? IS NULL OR erp.country_code = UPPER(?))
                """, actorUserId, blankToNull(countryCode), blankToNull(countryCode));

        for (EnergyReferenceSnapshot snapshot : before) {
            EnergyReferenceSnapshot after = findSnapshot(snapshot.id()).orElse(snapshot);
            insertReferenceLog(actorUserId, actorCompanyId, snapshot.id(), snapshot.energyCode(), snapshot.countryCode(),
                    "UPDATE_PROVIDER", snapshot.referencePriceCountry(), after.referencePriceCountry(),
                    snapshot.referencePriceGlobalUsd(), after.referencePriceGlobalUsd(), after.sourceName(),
                    "{\"mode\":\"PROVIDER_PLACEHOLDER_SYNC\"}");
        }

        syncCompanyEnergyPriceReferences(countryCode);
        logOrganization(actorUserId, actorCompanyId, "ENERGY_REFERENCE_PRICE", null, "UPDATE_PROVIDER", "{\"updatedRows\":" + updated + "}");
        return updated;
    }


    public void softDelete(Long id, Long actorUserId, Long actorCompanyId) {
        jdbcTemplate.update("""
                UPDATE energy_reference_prices
                SET deleted_at = NOW(),
                    deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Harga Master'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
        logOrganization(actorUserId, actorCompanyId, "ENERGY_REFERENCE_PRICE", id, "HARGA_MASTER_DELETE", "{}");
    }

    public void updateManual(Long id, EnergyReferenceUpdateRequest request, Long actorUserId, Long actorCompanyId) {
        EnergyReferenceSnapshot before = findSnapshot(id).orElseThrow();
        jdbcTemplate.update("""
                UPDATE energy_reference_prices
                SET reference_price_country = COALESCE(?, reference_price_country),
                    reference_price_global_usd = COALESCE(?, reference_price_global_usd),
                    provider_reference_price_country = COALESCE(?, provider_reference_price_country),
                    provider_reference_price_global_usd = COALESCE(?, provider_reference_price_global_usd),
                    source_name = COALESCE(NULLIF(TRIM(?), ''), source_name),
                    source_detail = COALESCE(NULLIF(TRIM(?), ''), source_detail),
                    source_url = COALESCE(NULLIF(TRIM(?), ''), source_url),
                    provider_status = COALESCE(NULLIF(TRIM(?), ''), 'MANUAL_OVERRIDE'),
                    provider_last_update_at = NOW(),
                    last_sync_at = NOW(),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                request.referencePriceCountry(),
                request.referencePriceGlobalUsd(),
                request.providerReferencePriceCountry(),
                request.providerReferencePriceGlobalUsd(),
                request.sourceName(),
                request.sourceDetail(),
                request.sourceUrl(),
                request.providerStatus(),
                actorUserId,
                id);
        EnergyReferenceSnapshot after = findSnapshot(id).orElse(before);
        insertReferenceLog(actorUserId, actorCompanyId, id, after.energyCode(), after.countryCode(),
                "EDIT_REFERENCE", before.referencePriceCountry(), after.referencePriceCountry(),
                before.referencePriceGlobalUsd(), after.referencePriceGlobalUsd(), after.sourceName(),
                "{\"mode\":\"SUPERADMIN_MANUAL_EDIT\"}");
        syncCompanyEnergyPriceReferences(after.countryCode());
        logOrganization(actorUserId, actorCompanyId, "ENERGY_REFERENCE_PRICE", id, "EDIT_REFERENCE", "{\"mode\":\"SUPERADMIN_MANUAL_EDIT\"}");
    }

    private void seedCountryReferenceRows(String countryCode) {
        jdbcTemplate.update("""
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
                               WHEN 'DIESEL_B0' THEN 0.4250
                               WHEN 'DIESEL_B35' THEN 0.4688
                               WHEN 'DIESEL_EURO5' THEN 0.9063
                               WHEN 'LPG_AUTOGAS' THEN 0.4375
                               WHEN 'CNG' THEN 0.2813
                               WHEN 'LNG' THEN 0.7500
                               WHEN 'HYDROGEN_350_BAR' THEN 10.0000
                               WHEN 'HYDROGEN_700_BAR' THEN 11.2500
                               WHEN 'ELECTRIC_AC' THEN 0.1563
                               WHEN 'ELECTRIC_DC_FAST' THEN 0.2188
                               WHEN 'ELECTRIC_DEPOT' THEN 0.1063
                               ELSE 0.5000
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
                       CONCAT('Provider seed for ', gp.energy_name, ' in ', c.country_name),
                       c.source_url,
                       c.provider_status,
                       NOW(),
                       NOW()
                FROM global_price gp
                JOIN energy_reference_countries c ON c.country_code = UPPER(?)
                ON CONFLICT (energy_id, country_code) DO UPDATE
                SET country_name = EXCLUDED.country_name,
                    currency = EXCLUDED.currency,
                    source_name = EXCLUDED.source_name,
                    source_url = EXCLUDED.source_url,
                    provider_status = EXCLUDED.provider_status,
                    updated_at = NOW()
                """, countryCode);
    }

    private void syncCompanyEnergyPriceReferences(String countryCode) {
        jdbcTemplate.update("""
                UPDATE company_energy_prices cep
                SET country = erp.country_name,
                    currency = erp.currency,
                    reference_price_country_idr = erp.reference_price_country,
                    reference_price_global_usd = erp.reference_price_global_usd,
                    reference_source = erp.source_name,
                    last_reference_update_at = erp.last_sync_at,
                    updated_at = NOW()
                FROM energy_reference_prices erp
                WHERE cep.energy_id = erp.energy_id
                  AND cep.country_code = erp.country_code
                  AND (? IS NULL OR cep.country_code = UPPER(?))
                """, blankToNull(countryCode), blankToNull(countryCode));
    }

    private void insertReferenceLog(Long actorUserId, Long actorCompanyId, Long referenceId, String energyCode, String countryCode,
                                    String action, BigDecimal oldCountry, BigDecimal newCountry,
                                    BigDecimal oldGlobal, BigDecimal newGlobal, String sourceName, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO energy_reference_update_logs (
                    actor_user_id,
                    actor_company_id,
                    reference_id,
                    energy_code,
                    country_code,
                    action,
                    old_reference_price_country,
                    new_reference_price_country,
                    old_reference_price_global_usd,
                    new_reference_price_global_usd,
                    source_name,
                    details
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """, actorUserId, actorCompanyId, referenceId, energyCode, countryCode, action,
                oldCountry, newCountry, oldGlobal, newGlobal, sourceName, detailsJson == null ? "{}" : detailsJson);
    }

    private void logOrganization(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB))
                """, actorUserId, actorCompanyId, targetType, targetId, action, detailsJson == null ? "{}" : detailsJson);
    }

    @NonNull
    private RowMapper<EnergyReferenceCountryRow> countryMapper() {
        return (rs, rowNum) -> new EnergyReferenceCountryRow(
                rs.getLong("id"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("currency"),
                rs.getBigDecimal("usd_to_local_rate"),
                rs.getString("source_name"),
                rs.getString("source_url"),
                rs.getString("provider_status"),
                stringOrNull(rs, "provider_last_update_at"),
                rs.getString("status"),
                stringOrNull(rs, "updated_at")
        );
    }

    @NonNull
    private RowMapper<EnergyReferenceRow> rowMapper() {
        return (rs, rowNum) -> new EnergyReferenceRow(
                rs.getLong("id"),
                rs.getLong("energy_id"),
                rs.getString("energy_code"),
                rs.getString("energy_name"),
                rs.getString("energy_group"),
                rs.getString("unit"),
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("currency"),
                rs.getBigDecimal("reference_price_country"),
                rs.getBigDecimal("reference_price_global_usd"),
                rs.getBigDecimal("provider_reference_price_country"),
                rs.getBigDecimal("provider_reference_price_global_usd"),
                rs.getString("source_name"),
                rs.getString("source_detail"),
                rs.getString("source_url"),
                rs.getString("provider_status"),
                stringOrNull(rs, "provider_last_update_at"),
                stringOrNull(rs, "last_sync_at"),
                stringOrNull(rs, "updated_at"),
                rs.getString("updated_by_email")
        );
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase();
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String stringOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }

    private record EnergyReferenceSnapshot(
            Long id,
            Long energyId,
            String energyCode,
            String countryCode,
            BigDecimal referencePriceCountry,
            BigDecimal referencePriceGlobalUsd,
            String sourceName
    ) {}
}
