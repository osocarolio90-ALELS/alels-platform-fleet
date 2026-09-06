package com.alels.backend.assetregister.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyCountryOption;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceRow;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceUpdateRequest;

@Repository
public class EnergyPriceRepository {
    private final JdbcTemplate jdbcTemplate;

    public EnergyPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EnergyCountryOption> findActiveCountries() {
        return jdbcTemplate.query("""
                SELECT country_code,
                       country_name,
                       currency,
                       usd_to_local_rate,
                       source_name,
                       source_url
                FROM energy_reference_countries
                WHERE status = 'ACTIVE'
                ORDER BY country_name
                """, (rs, rowNum) -> new EnergyCountryOption(
                rs.getString("country_code"),
                rs.getString("country_name"),
                rs.getString("currency"),
                rs.getBigDecimal("usd_to_local_rate"),
                rs.getString("source_name"),
                rs.getString("source_url")
        ));
    }

    public void ensureCompanyEnergyPrices(Long companyId) {
        ensureCompanyEnergyPrices(companyId, "ID");
    }

    public void ensureCompanyEnergyPrices(Long companyId, String countryCode) {
        jdbcTemplate.update("""
                WITH selected_country AS (
                    SELECT country_code,
                           country_name,
                           currency
                    FROM energy_reference_countries
                    WHERE country_code = UPPER(COALESCE(NULLIF(TRIM(?), ''), 'ID'))
                      AND status = 'ACTIVE'
                    LIMIT 1
                )
                INSERT INTO company_energy_prices (
                    company_id,
                    energy_id,
                    country_code,
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
                       e.id,
                       COALESCE(sc.country_code, 'ID'),
                       COALESCE(sc.country_name, 'Indonesia'),
                       COALESCE(sc.currency, 'IDR'),
                       COALESCE(erp.reference_price_country, 0),
                       COALESCE(erp.reference_price_country, 0),
                       COALESCE(erp.reference_price_global_usd, 0),
                       CASE WHEN COALESCE(sc.currency, 'IDR') = 'IDR' THEN 1 ELSE NULL END,
                       'REFERENCE_COUNTRY',
                       COALESCE(erp.source_name, 'ALELS_REFERENCE_PROVIDER'),
                       COALESCE(erp.last_sync_at, erp.provider_last_update_at, NOW()),
                       FALSE
                FROM companies c
                CROSS JOIN energy_types e
                LEFT JOIN selected_country sc ON TRUE
                LEFT JOIN energy_reference_prices erp
                       ON erp.energy_id = e.id
                      AND erp.country_code = COALESCE(sc.country_code, 'ID')
                WHERE c.id = ?
                  AND c.deleted_at IS NULL
                  AND e.status = 'ACTIVE'
                ON CONFLICT (company_id, energy_id, country) DO UPDATE
                SET country_code = EXCLUDED.country_code,
                    currency = EXCLUDED.currency,
                    reference_price_country_idr = EXCLUDED.reference_price_country_idr,
                    reference_price_global_usd = EXCLUDED.reference_price_global_usd,
                    reference_source = EXCLUDED.reference_source,
                    last_reference_update_at = EXCLUDED.last_reference_update_at,
                    price_energy = CASE
                        WHEN company_energy_prices.manual_override IS TRUE THEN company_energy_prices.price_energy
                        ELSE EXCLUDED.price_energy
                    END,
                    price_source = CASE
                        WHEN company_energy_prices.manual_override IS TRUE THEN company_energy_prices.price_source
                        ELSE 'REFERENCE_COUNTRY'
                    END,
                    updated_at = NOW()
                """, countryCode, companyId);
    }

    public List<EnergyPriceRow> findByCompany(Long companyId) {
        return findByCompany(companyId, "ID");
    }

    public List<EnergyPriceRow> findByCompany(Long companyId, String countryCode) {
        return jdbcTemplate.query("""
                SELECT cep.id,
                       cep.company_id,
                       c.company_name,
                       et.id AS energy_id,
                       et.energy_code,
                       et.energy_name,
                       et.energy_group,
                       et.unit,
                       et.vehicle_usage,
                       COALESCE(cep.country_code, 'ID') AS country_code,
                       COALESCE(erc.country_name, cep.country, 'Indonesia') AS country,
                       COALESCE(erc.currency, cep.currency, 'IDR') AS currency,
                       cep.price_energy,
                       COALESCE(erp.reference_price_country, cep.reference_price_country_idr) AS reference_price_country_idr,
                       COALESCE(erp.reference_price_global_usd, cep.reference_price_global_usd) AS reference_price_global_usd,
                       cep.fx_rate_to_idr,
                       cep.price_source,
                       COALESCE(erp.source_name, cep.reference_source) AS reference_source,
                       erp.source_url,
                       COALESCE(erp.last_sync_at, erp.provider_last_update_at, cep.last_reference_update_at) AS last_reference_update_at,
                       cep.manual_override,
                       cep.updated_at,
                       u.email AS updated_by_email
                FROM company_energy_prices cep
                JOIN energy_types et ON et.id = cep.energy_id
                JOIN companies c ON c.id = cep.company_id
                LEFT JOIN energy_reference_countries erc ON erc.country_code = COALESCE(cep.country_code, 'ID')
                LEFT JOIN energy_reference_prices erp ON erp.energy_id = cep.energy_id AND erp.country_code = COALESCE(cep.country_code, 'ID')
                LEFT JOIN users u ON u.id = cep.updated_by
                WHERE cep.company_id = ?
                  AND COALESCE(cep.country_code, 'ID') = UPPER(COALESCE(NULLIF(TRIM(?), ''), 'ID'))
                  AND c.deleted_at IS NULL
                  AND et.status = 'ACTIVE'
                ORDER BY et.sort_order, et.energy_name
                """, rowMapper(), companyId, countryCode);
    }

    public Optional<Long> companyIdForPrice(Long priceId) {
        return jdbcTemplate.query("SELECT company_id FROM company_energy_prices WHERE id = ?", (rs, rowNum) -> rs.getLong("company_id"), priceId)
                .stream()
                .findFirst();
    }

    public void updatePrice(Long priceId, EnergyPriceUpdateRequest request, Long actorUserId) {
        jdbcTemplate.update("""
                WITH selected_country AS (
                    SELECT country_code,
                           country_name,
                           currency
                    FROM energy_reference_countries
                    WHERE country_code = UPPER(COALESCE(NULLIF(TRIM(?), ''), (
                        SELECT country_code FROM company_energy_prices WHERE id = ?
                    )))
                      AND status = 'ACTIVE'
                    LIMIT 1
                ), selected_reference AS (
                    SELECT erp.reference_price_country,
                           erp.reference_price_global_usd,
                           erp.source_name,
                           erp.last_sync_at
                    FROM energy_reference_prices erp
                    JOIN company_energy_prices cep ON cep.energy_id = erp.energy_id
                    JOIN selected_country sc ON sc.country_code = erp.country_code
                    WHERE cep.id = ?
                    LIMIT 1
                )
                UPDATE company_energy_prices cep
                SET country_code = COALESCE((SELECT country_code FROM selected_country), cep.country_code),
                    country = COALESCE((SELECT country_name FROM selected_country), cep.country),
                    currency = COALESCE((SELECT currency FROM selected_country), cep.currency),
                    price_energy = COALESCE(?, CASE WHEN NULLIF(TRIM(?), '') IS NOT NULL THEN (SELECT reference_price_country FROM selected_reference) ELSE cep.price_energy END),
                    reference_price_country_idr = COALESCE((SELECT reference_price_country FROM selected_reference), cep.reference_price_country_idr),
                    reference_price_global_usd = COALESCE((SELECT reference_price_global_usd FROM selected_reference), cep.reference_price_global_usd),
                    reference_source = COALESCE((SELECT source_name FROM selected_reference), cep.reference_source),
                    last_reference_update_at = COALESCE((SELECT last_sync_at FROM selected_reference), cep.last_reference_update_at),
                    manual_override = CASE WHEN ? IS NULL THEN cep.manual_override ELSE TRUE END,
                    price_source = 'MANUAL',
                    updated_by = ?,
                    updated_at = NOW()
                WHERE cep.id = ?
                """,
                request.countryCode(),
                priceId,
                priceId,
                request.priceEnergy(),
                request.countryCode(),
                request.priceEnergy(),
                actorUserId,
                priceId);
    }

    public boolean canAccessCompany(Long targetCompanyId, Long actorCompanyId, String role) {
        if (targetCompanyId == null || actorCompanyId == null) return false;
        String normalized = role == null ? "" : role.toUpperCase().replaceAll("[\\s_-]+", "");
        if ("SUPERADMIN".equals(normalized)) return true;
        if ("ADMIN".equals(normalized)) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM companies
                    WHERE id = ?
                      AND deleted_at IS NULL
                      AND company_code <> 'ALELS_TECH_INDONESIA'
                    """, Integer.class, targetCompanyId);
            return count != null && count > 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.deleted_at IS NULL
                )
                SELECT COUNT(*) FROM tree WHERE id = ?
                """, Integer.class, actorCompanyId, targetCompanyId);
        return count != null && count > 0;
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB))
                """, actorUserId, actorCompanyId, targetType, targetId, action, detailsJson == null ? "{}" : detailsJson);
    }

    @NonNull
    private RowMapper<EnergyPriceRow> rowMapper() {
        return (rs, rowNum) -> new EnergyPriceRow(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getLong("energy_id"),
                rs.getString("energy_code"),
                rs.getString("energy_name"),
                rs.getString("energy_group"),
                rs.getString("unit"),
                rs.getString("vehicle_usage"),
                rs.getString("country_code"),
                rs.getString("country"),
                rs.getString("currency"),
                rs.getBigDecimal("price_energy"),
                rs.getBigDecimal("reference_price_country_idr"),
                rs.getBigDecimal("reference_price_global_usd"),
                rs.getBigDecimal("fx_rate_to_idr"),
                rs.getString("price_source"),
                rs.getString("reference_source"),
                rs.getString("source_url"),
                stringOrNull(rs, "last_reference_update_at"),
                rs.getBoolean("manual_override"),
                stringOrNull(rs, "updated_at"),
                rs.getString("updated_by_email")
        );
    }

    private String stringOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }
}
