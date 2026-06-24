package com.alels.backend.masterdata.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.MasterDataDtos.MasterCountryRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.MasterCountryRow;

@Repository
public class MasterCountryRepository {
    private final JdbcTemplate jdbcTemplate;
    public MasterCountryRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<MasterCountryRow> list() {
        return jdbcTemplate.query("""
                SELECT id, country_code, country_name, currency, usd_to_local_rate, source_name, source_url, status, updated_at
                FROM energy_reference_countries
                ORDER BY country_name
                """, (rs, rowNum) -> new MasterCountryRow(
                rs.getLong("id"), rs.getString("country_code"), rs.getString("country_name"), rs.getString("currency"),
                rs.getBigDecimal("usd_to_local_rate"), rs.getString("source_name"), rs.getString("source_url"), rs.getString("status"), rs.getString("updated_at")
        ));
    }

    public void create(MasterCountryRequest request, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO energy_reference_countries(country_code, country_name, currency, usd_to_local_rate, source_name, source_url, status)
                VALUES (UPPER(TRIM(?)), TRIM(?), UPPER(TRIM(?)), COALESCE(?, 1), COALESCE(NULLIF(TRIM(?), ''), 'MANUAL_PROVIDER_LINK'), NULLIF(TRIM(?), ''), COALESCE(NULLIF(TRIM(?), ''), 'ACTIVE'))
                ON CONFLICT(country_code) DO UPDATE
                SET country_name = EXCLUDED.country_name,
                    currency = EXCLUDED.currency,
                    usd_to_local_rate = EXCLUDED.usd_to_local_rate,
                    source_name = EXCLUDED.source_name,
                    source_url = EXCLUDED.source_url,
                    status = EXCLUDED.status,
                    updated_at = NOW()
                """, request.countryCode(), request.countryName(), request.currency(), request.usdToLocalRate(), request.sourceName(), request.sourceUrl(), request.status());
    }

    public void update(Long id, MasterCountryRequest request) {
        jdbcTemplate.update("""
                UPDATE energy_reference_countries
                SET country_code = COALESCE(NULLIF(UPPER(TRIM(?)), ''), country_code),
                    country_name = COALESCE(NULLIF(TRIM(?), ''), country_name),
                    currency = COALESCE(NULLIF(UPPER(TRIM(?)), ''), currency),
                    usd_to_local_rate = COALESCE(?, usd_to_local_rate),
                    source_name = COALESCE(NULLIF(TRIM(?), ''), source_name),
                    source_url = NULLIF(TRIM(?), ''),
                    status = COALESCE(NULLIF(TRIM(?), ''), status),
                    updated_at = NOW()
                WHERE id = ?
                """, request.countryCode(), request.countryName(), request.currency(), request.usdToLocalRate(), request.sourceName(), request.sourceUrl(), request.status(), id);
    }

    public void softDelete(Long id) {
        jdbcTemplate.update("UPDATE energy_reference_countries SET status = 'INACTIVE', updated_at = NOW() WHERE id = ?", id);
    }
}
