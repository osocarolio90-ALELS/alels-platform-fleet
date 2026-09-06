package com.alels.backend.masterdata.repository;

import java.text.Normalizer;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRequest;
import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRow;

@Repository
public class LicenseMasterRepository {
    private static final Map<String, String> COUNTRY_CODES = Map.ofEntries(
            Map.entry("INDONESIA", "ID"), Map.entry("MALAYSIA", "MY"), Map.entry("SINGAPORE", "SG"), Map.entry("SINGAPURA", "SG"),
            Map.entry("THAILAND", "TH"), Map.entry("AUSTRALIA", "AU"), Map.entry("NEW ZEALAND", "NZ"), Map.entry("JAPAN", "JP"), Map.entry("JEPANG", "JP"),
            Map.entry("SOUTH KOREA", "KR"), Map.entry("KOREA", "KR"), Map.entry("UNITED KINGDOM", "GB"), Map.entry("INGGRIS", "GB"),
            Map.entry("UNITED STATES", "US"), Map.entry("AMERICA", "US"), Map.entry("AMERIKA SERIKAT", "US"), Map.entry("CANADA", "CA"), Map.entry("KANADA", "CA"),
            Map.entry("GERMANY", "DE"), Map.entry("JERMAN", "DE"), Map.entry("EUROPEAN UNION", "EU"), Map.entry("UNI EROPA", "EU")
    );

    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("ID", "Indonesia"), Map.entry("MY", "Malaysia"), Map.entry("SG", "Singapore"), Map.entry("TH", "Thailand"),
            Map.entry("AU", "Australia"), Map.entry("NZ", "New Zealand"), Map.entry("JP", "Japan"), Map.entry("KR", "South Korea"),
            Map.entry("GB", "United Kingdom"), Map.entry("US", "United States"), Map.entry("CA", "Canada"), Map.entry("DE", "Germany"), Map.entry("EU", "European Union")
    );

    private final JdbcTemplate jdbcTemplate;

    public LicenseMasterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LicenseMasterRow> list(boolean includeDeleted) {
        String where = includeDeleted ? "" : "WHERE lm.deleted_at IS NULL";
        return jdbcTemplate.query(Objects.requireNonNull("""
                SELECT lm.id, lm.country_code,
                       COALESCE(NULLIF(lm.country_name, ''), c.country_name, lm.country_code) AS country_name,
                       lm.code, lm.name, lm.is_active, lm.status,
                       lm.created_at, COALESCE(u.email, '-') AS created_by, lm.updated_at
                FROM license_master lm
                LEFT JOIN energy_reference_countries c ON c.country_code = lm.country_code
                LEFT JOIN users u ON u.id = lm.created_by
                %s
                ORDER BY country_name, lm.sort_order, lm.name
                """.formatted(where)), (rs, rowNum) -> new LicenseMasterRow(
                rs.getLong("id"), rs.getString("country_code"), rs.getString("country_name"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("is_active"), rs.getString("status"), rs.getString("created_at"), rs.getString("created_by"), rs.getString("updated_at")
        ));
    }

    public List<LicenseMasterRow> listActiveByCountry(String countryCode) {
        String cleaned = clean(countryCode);
        return jdbcTemplate.query("""
                SELECT lm.id, lm.country_code,
                       COALESCE(NULLIF(lm.country_name, ''), c.country_name, lm.country_code) AS country_name,
                       lm.code, lm.name, lm.is_active, lm.status,
                       lm.created_at, COALESCE(u.email, '-') AS created_by, lm.updated_at
                FROM license_master lm
                LEFT JOIN energy_reference_countries c ON c.country_code = lm.country_code
                LEFT JOIN users u ON u.id = lm.created_by
                WHERE lm.deleted_at IS NULL
                  AND lm.is_active = TRUE
                  AND (? IS NULL OR lm.country_code = UPPER(TRIM(?)) OR UPPER(COALESCE(lm.country_name, c.country_name, '')) = UPPER(TRIM(?)))
                ORDER BY country_name, lm.sort_order, lm.name
                """, (rs, rowNum) -> new LicenseMasterRow(
                rs.getLong("id"), rs.getString("country_code"), rs.getString("country_name"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("is_active"), rs.getString("status"), rs.getString("created_at"), rs.getString("created_by"), rs.getString("updated_at")
        ), cleaned, cleaned, cleaned);
    }

    public Long create(LicenseMasterRequest request, Long actorUserId) {
        String countryName = normalizeCountryName(request.countryName(), request.countryCode());
        String countryCode = normalizeCountryCode(request.countryCode(), countryName);
        String name = clean(request.name());
        String code = generateCode(countryCode, name);
        return jdbcTemplate.queryForObject("""
                INSERT INTO license_master(country_code, country_name, code, name, status, is_active, is_system, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, COALESCE(?, TRUE), FALSE, 1000, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    country_code = EXCLUDED.country_code,
                    country_name = EXCLUDED.country_name,
                    name = EXCLUDED.name,
                    status = EXCLUDED.status,
                    is_active = EXCLUDED.is_active,
                    deleted_at = NULL,
                    deleted_by = NULL,
                    delete_permanent_at = NULL,
                    deleted_reason = NULL,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                RETURNING id
                """, Long.class, countryCode, countryName, code, name, activeStatus(request.active()), request.active(), actorUserId, actorUserId);
    }

    public void update(Long id, LicenseMasterRequest request, Long actorUserId) {
        String countryName = normalizeCountryName(request.countryName(), request.countryCode());
        String countryCode = normalizeCountryCode(request.countryCode(), countryName);
        String name = clean(request.name());
        String code = generateCode(countryCode, name);
        jdbcTemplate.update("""
                UPDATE license_master
                SET country_code = ?,
                    country_name = ?,
                    code = ?,
                    name = ?,
                    status = ?,
                    is_active = COALESCE(?, is_active),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, countryCode, countryName, code, name, activeStatus(request.active()), request.active(), actorUserId, id);
    }

    public void softDelete(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE license_master
                SET deleted_at = NOW(), deleted_by = ?, deleted_reason = COALESCE(deleted_reason, 'Deleted from License Master'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    is_active = FALSE, status = 'INACTIVE', updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public void setActive(Long id, boolean active, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE license_master
                SET is_active = ?, status = ?, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, active, active ? "ACTIVE" : "INACTIVE", actorUserId, id);
    }

    public boolean existsActive(Long id) {
        if (id == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM license_master WHERE id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
    }

    public void log(Long actorUserId, Long actorCompanyId, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, 'LICENSE_MASTER', ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetId, action);
        } catch (DataAccessException ignored) {}
    }

    private String activeStatus(Boolean active) { return Boolean.FALSE.equals(active) ? "INACTIVE" : "ACTIVE"; }

    private String normalizeCountryCode(String value, String countryName) {
        String code = clean(value);
        if (code != null && code.matches("^[A-Za-z]{2,10}$")) return code.toUpperCase(Locale.ROOT);
        String known = COUNTRY_CODES.get(strip(countryName));
        if (known != null) return known;
        return fallbackCode(countryName);
    }

    private String normalizeCountryName(String countryName, String countryCode) {
        String name = clean(countryName);
        if (name != null) return titleCase(name);
        String code = clean(countryCode);
        if (code != null && COUNTRY_NAMES.containsKey(code.toUpperCase(Locale.ROOT))) return COUNTRY_NAMES.get(code.toUpperCase(Locale.ROOT));
        return code == null ? "Indonesia" : titleCase(code);
    }

    private String fallbackCode(String countryName) {
        String normalized = strip(countryName);
        if (normalized == null || normalized.isBlank()) return "ID";
        String[] words = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) if (!word.isBlank()) builder.append(word.charAt(0));
        String value = builder.length() >= 2 ? builder.toString() : normalized.replaceAll("[^A-Z0-9]", "");
        return value.substring(0, Math.min(value.length(), 6));
    }

    private String titleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private String strip(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String clean(String value) { return value == null ? null : value.trim(); }
    private String generateCode(String countryCode, String name) {
        return (countryCode + "_" + name).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
