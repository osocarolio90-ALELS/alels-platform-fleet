package com.alels.backend.masterdata.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceBrandRow;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceMasterRequest;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceModelRow;

@Repository
public class DeviceMasterRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeviceMasterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeviceBrandRow> listBrands() {
        return jdbcTemplate.query("""
                SELECT b.id, b.brand_code, b.brand_name, b.description, b.is_active, b.is_system, b.sort_order,
                       b.created_at, COALESCE(u.email, '-') AS created_by, b.updated_at
                FROM device_brands b
                LEFT JOIN users u ON u.id = b.created_by
                WHERE b.deleted_at IS NULL
                ORDER BY b.sort_order, b.brand_name
                """, (rs, rowNum) -> new DeviceBrandRow(
                rs.getLong("id"), rs.getString("brand_code"), rs.getString("brand_name"), rs.getString("description"),
                rs.getBoolean("is_active"), rs.getBoolean("is_system"), rs.getInt("sort_order"), rs.getString("created_at"),
                rs.getString("created_by"), rs.getString("updated_at")
        ));
    }

    public List<DeviceModelRow> listModels(Long brandId) {
        if (brandId == null) {
            return jdbcTemplate.query(baseModelSql() + " ORDER BY b.brand_name, m.sort_order, m.model_name", modelMapper());
        }
        return jdbcTemplate.query(baseModelSql() + " AND m.brand_id = ? ORDER BY b.brand_name, m.sort_order, m.model_name", modelMapper(), brandId);
    }

    public Long createBrand(DeviceMasterRequest request, Long actorUserId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO device_brands (brand_code, brand_name, description, status, is_active, is_system, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, COALESCE(?, TRUE), FALSE, COALESCE(?, 1000), ?, ?)
                ON CONFLICT (brand_code) DO UPDATE SET
                    brand_name = EXCLUDED.brand_name,
                    description = EXCLUDED.description,
                    is_active = EXCLUDED.is_active,
                    status = EXCLUDED.status,
                    sort_order = EXCLUDED.sort_order,
                    deleted_at = NULL,
                    deleted_by = NULL,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                RETURNING id
                """, Long.class,
                normalizeCode(request.brandCode(), request.brandName()), clean(request.brandName()), request.description(), activeStatus(request.active()), request.active(), request.sortOrder(), actorUserId, actorUserId);
    }

    public void updateBrand(Long id, DeviceMasterRequest request, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE device_brands
                SET brand_code = COALESCE(NULLIF(TRIM(?), ''), brand_code),
                    brand_name = COALESCE(NULLIF(TRIM(?), ''), brand_name),
                    description = ?,
                    is_active = COALESCE(?, is_active),
                    status = CASE WHEN COALESCE(?, is_active) THEN 'ACTIVE' ELSE 'INACTIVE' END,
                    sort_order = COALESCE(?, sort_order),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, normalizeCode(request.brandCode(), request.brandName()), clean(request.brandName()), request.description(), request.active(), request.active(), request.sortOrder(), actorUserId, id);
    }

    public Long createModel(DeviceMasterRequest request, Long actorUserId) {
        DeviceMasterRequest normalized = normalizeProtocolFields(request);
        return jdbcTemplate.queryForObject("""
                INSERT INTO device_models (brand_id, model_code, model_name, vendor, protocol_code, parser_code, dictionary_code, description, status, is_active, is_system, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, (SELECT brand_name FROM device_brands WHERE id = ?), ?, ?, ?, ?, ?, COALESCE(?, TRUE), FALSE, COALESCE(?, 1000), ?, ?)
                ON CONFLICT (model_code) DO UPDATE SET
                    brand_id = EXCLUDED.brand_id,
                    model_name = EXCLUDED.model_name,
                    vendor = EXCLUDED.vendor,
                    protocol_code = EXCLUDED.protocol_code,
                    parser_code = EXCLUDED.parser_code,
                    dictionary_code = EXCLUDED.dictionary_code,
                    description = EXCLUDED.description,
                    is_active = EXCLUDED.is_active,
                    status = EXCLUDED.status,
                    sort_order = EXCLUDED.sort_order,
                    deleted_at = NULL,
                    deleted_by = NULL,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                RETURNING id
                """, Long.class,
                normalized.brandId(), normalizeCode(normalized.modelCode(), normalized.modelName()), clean(normalized.modelName()), normalized.brandId(),
                normalized.protocolCode(), normalized.parserCode(), normalized.dictionaryCode(), normalized.description(), activeStatus(normalized.active()), normalized.active(), normalized.sortOrder(), actorUserId, actorUserId);
    }

    public void updateModel(Long id, DeviceMasterRequest request, Long actorUserId) {
        DeviceMasterRequest normalized = normalizeProtocolFields(request);
        jdbcTemplate.update("""
                UPDATE device_models
                SET brand_id = COALESCE(?, brand_id),
                    model_code = COALESCE(NULLIF(TRIM(?), ''), model_code),
                    model_name = COALESCE(NULLIF(TRIM(?), ''), model_name),
                    vendor = (SELECT brand_name FROM device_brands WHERE id = COALESCE(?, brand_id)),
                    protocol_code = COALESCE(NULLIF(TRIM(?), ''), protocol_code),
                    parser_code = COALESCE(NULLIF(TRIM(?), ''), parser_code),
                    dictionary_code = COALESCE(NULLIF(TRIM(?), ''), dictionary_code),
                    description = ?,
                    is_active = COALESCE(?, is_active),
                    status = CASE WHEN COALESCE(?, is_active) THEN 'ACTIVE' ELSE 'INACTIVE' END,
                    sort_order = COALESCE(?, sort_order),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, normalized.brandId(), normalizeCode(normalized.modelCode(), normalized.modelName()), clean(normalized.modelName()), normalized.brandId(),
                normalized.protocolCode(), normalized.parserCode(), normalized.dictionaryCode(), normalized.description(), normalized.active(), normalized.active(), normalized.sortOrder(), actorUserId, id);
    }

    public void softDeleteBrand(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE device_brands
                SET is_active = FALSE, status = 'INACTIVE', deleted_at = NOW(), deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Device Master'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public void softDeleteModel(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE device_models
                SET is_active = FALSE, status = 'INACTIVE', deleted_at = NOW(), deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Device Master'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public boolean brandExists(Long brandId) {
        if (brandId == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_brands WHERE id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, brandId);
        return count != null && count > 0;
    }

    public boolean hasActiveDictionary(Long deviceModelId) {
        if (deviceModelId == null) return false;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM device_models model
                JOIN dictionary_registry dictionary
                  ON dictionary.device_model_id = model.id
                 AND dictionary.status = 'ACTIVE'
                 AND LOWER(dictionary.dictionary_code) = LOWER(model.dictionary_code)
                 AND NULLIF(TRIM(dictionary.dictionary_file), '') IS NOT NULL
                WHERE model.id = ?
                  AND model.deleted_at IS NULL
                  AND model.is_active = TRUE
                """, Integer.class, deviceModelId);
        return count != null && count > 0;
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetType, targetId, action);
        } catch (DataAccessException ignored) {}
    }

    private String baseModelSql() {
        return """
                SELECT m.id, m.brand_id, b.brand_code, b.brand_name, m.model_code, m.model_name,
                       m.protocol_code, m.parser_code, m.dictionary_code, m.description, m.is_active, m.is_system, m.sort_order,
                       m.created_at, COALESCE(u.email, '-') AS created_by, m.updated_at
                FROM device_models m
                JOIN device_brands b ON b.id = m.brand_id AND b.deleted_at IS NULL
                LEFT JOIN users u ON u.id = m.created_by
                WHERE m.deleted_at IS NULL
                  AND m.is_active = TRUE
                  AND NULLIF(TRIM(m.dictionary_code), '') IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM dictionary_registry dictionary
                      WHERE dictionary.device_model_id = m.id
                        AND dictionary.status = 'ACTIVE'
                        AND NULLIF(TRIM(dictionary.dictionary_file), '') IS NOT NULL
                        AND LOWER(dictionary.dictionary_code) = LOWER(m.dictionary_code)
                  )
                """;
    }

    private org.springframework.jdbc.core.RowMapper<DeviceModelRow> modelMapper() {
        return (rs, rowNum) -> new DeviceModelRow(
                rs.getLong("id"), rs.getObject("brand_id", Long.class), rs.getString("brand_code"), rs.getString("brand_name"),
                rs.getString("model_code"), rs.getString("model_name"), rs.getString("protocol_code"), rs.getString("parser_code"),
                rs.getString("dictionary_code"), rs.getString("description"), rs.getBoolean("is_active"), rs.getBoolean("is_system"),
                rs.getInt("sort_order"), rs.getString("created_at"), rs.getString("created_by"), rs.getString("updated_at")
        );
    }

    private DeviceMasterRequest normalizeProtocolFields(DeviceMasterRequest request) {
        String protocol = clean(request.protocolCode());
        String parser = clean(request.parserCode());
        String dictionary = clean(request.dictionaryCode());
        if (protocol == null || protocol.isBlank()) protocol = normalizeCode(request.modelCode(), request.modelName());
        if (parser == null || parser.isBlank()) parser = protocol + "_PARSER";
        if (dictionary == null || dictionary.isBlank()) dictionary = protocol + "_AVL";
        return new DeviceMasterRequest(request.brandId(), request.brandCode(), request.brandName(), request.modelCode(), request.modelName(), protocol, parser, dictionary, request.description(), request.active(), request.sortOrder());
    }

    private String activeStatus(Boolean active) { return Boolean.FALSE.equals(active) ? "INACTIVE" : "ACTIVE"; }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private String normalizeCode(String code, String fallback) {
        String source = code == null || code.isBlank() ? fallback : code;
        if (source == null || source.isBlank()) return null;
        return source.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
