package com.alels.backend.assetregister.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceLookupOption;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRequest;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRow;

@Repository
public class DeviceRegisterRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeviceRegisterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeviceRegisterRow> list(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(baseListSql() + " ORDER BY d.created_at DESC", rowMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + baseListSql() + " AND d.company_id IN (SELECT id FROM visible_companies) ORDER BY d.created_at DESC", rowMapper(), actorCompanyId);
    }

    public List<DeviceLookupOption> companyOptions(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, company_name, company_code, status
                    FROM companies
                    WHERE deleted_at IS NULL
                    ORDER BY company_name
                    """, (rs, rowNum) -> new DeviceLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")));
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id, company_name, company_code, status FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id, c.company_name, c.company_code, c.status FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT id, company_name, company_code, status FROM visible_companies ORDER BY company_name
                """, (rs, rowNum) -> new DeviceLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")), actorCompanyId);
    }

    public List<DeviceLookupOption> brandOptions() {
        return jdbcTemplate.query("""
                SELECT id, brand_name, brand_code
                FROM device_brands
                WHERE is_active = TRUE AND deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM device_models m WHERE m.brand_id=device_brands.id AND m.is_active=TRUE AND m.deleted_at IS NULL
                    AND EXISTS (SELECT 1 FROM protocol_registry p WHERE p.protocol_code=m.protocol_code AND p.status='ACTIVE')
                    AND EXISTS (SELECT 1 FROM dictionary_registry dr WHERE dr.device_model_id=m.id AND dr.dictionary_code=m.dictionary_code AND dr.status='ACTIVE')
                    AND EXISTS (SELECT 1 FROM device_io_mappings io WHERE io.device_model_id=m.id AND io.source_protocol IN (m.protocol_code, m.parser_code) AND io.status='ACTIVE'))
                ORDER BY sort_order, brand_name
                """, (rs, rowNum) -> new DeviceLookupOption(rs.getLong("id"), rs.getString("brand_name"), rs.getString("brand_code"), null));
    }

    public Long create(DeviceRegisterRequest request, Long actorUserId) {
        ModelProtocol protocol = protocolByModel(request.deviceModelId()).orElse(new ModelProtocol(null, null, null, null, null));
        try {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO devices (company_id, imei, gsm_number, device_brand_id, device_model_id, device_model,
                                         tcp_host, tcp_port, protocol_code, parser_code, dictionary_code,
                                         register_status, notes, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, request.companyId(), normalizeImei(request.imei()), clean(request.gsmNumber()), effectiveBrandId(request, protocol), request.deviceModelId(),
                    protocol.modelName(), clean(request.tcpHost()), request.tcpPort(), protocol.protocolCode(), protocol.parserCode(), protocol.dictionaryCode(),
                    normalizeRegisterStatus(request.registerStatus()), request.notes(), actorUserId, actorUserId);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateDeviceException(ex);
        }
    }

    public void update(Long id, DeviceRegisterRequest request, Long actorUserId) {
        ModelProtocol protocol = protocolByModel(request.deviceModelId()).orElse(new ModelProtocol(null, null, null, null, null));
        try {
            jdbcTemplate.update("""
                    UPDATE devices
                    SET company_id = ?, imei = ?, gsm_number = ?, device_brand_id = ?, device_model_id = ?, device_model = ?,
                        tcp_host = ?, tcp_port = ?, protocol_code = ?, parser_code = ?, dictionary_code = ?,
                        register_status = ?, notes = ?, updated_by = ?, updated_at = NOW()
                    WHERE id = ? AND deleted_at IS NULL
                    """, request.companyId(), normalizeImei(request.imei()), clean(request.gsmNumber()), effectiveBrandId(request, protocol), request.deviceModelId(), protocol.modelName(),
                    clean(request.tcpHost()), request.tcpPort(), protocol.protocolCode(), protocol.parserCode(), protocol.dictionaryCode(),
                    normalizeRegisterStatus(request.registerStatus()), request.notes(), actorUserId, id);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateDeviceException(ex);
        }
    }

    public void softDelete(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE devices
                SET deleted_at = NOW(), deleted_by = ?, deleted_reason = COALESCE(deleted_reason, 'Deleted from Device Register'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'), updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public Optional<Long> companyIdByDevice(Long id) {
        return jdbcTemplate.query("SELECT company_id FROM devices WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), id).stream().findFirst();
    }

    public boolean canAccessCompany(Long targetCompanyId, Long actorCompanyId, String role) {
        if (targetCompanyId == null || actorCompanyId == null) return false;
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) return true;
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT COUNT(*) FROM visible_companies WHERE id = ?
                """, Integer.class, actorCompanyId, targetCompanyId);
        return count != null && count > 0;
    }

    public boolean brandExists(Long id) { return exists("device_brands", id); }
    public boolean modelExists(Long id) {
        if (id == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_models m WHERE m.id=? AND m.is_active=TRUE AND m.deleted_at IS NULL AND " + verifiedModelSql(), Integer.class, id);
        return count != null && count > 0;
    }
    public boolean modelBelongsToBrand(Long modelId, Long brandId) {
        if (modelId == null || brandId == null) return true;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_models WHERE id = ? AND brand_id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, modelId, brandId);
        return count != null && count > 0;
    }
    public boolean imeiExists(String imei, Long excludeId) {
        if (imei == null || imei.isBlank()) return false;
        Integer count = excludeId == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM devices WHERE LOWER(imei) = LOWER(?) AND deleted_at IS NULL", Integer.class, imei)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM devices WHERE LOWER(imei) = LOWER(?) AND deleted_at IS NULL AND id <> ?", Integer.class, imei, excludeId);
        return count != null && count > 0;
    }

    public void log(Long actorUserId, Long actorCompanyId, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, 'DEVICE', ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetId, action);
        } catch (DataAccessException ignored) {}
    }

    private String baseListSql() {
        return """
                SELECT d.id, d.company_id, c.company_name, d.device_brand_id, b.brand_name, d.device_model_id, m.model_name,
                       d.imei, d.gsm_number, d.tcp_host, d.tcp_port,
                       COALESCE(d.protocol_code, m.protocol_code) AS protocol_code,
                       COALESCE(d.parser_code, m.parser_code) AS parser_code,
                       COALESCE(d.dictionary_code, m.dictionary_code) AS dictionary_code,
                       CASE WHEN COALESCE(d.online, FALSE) = TRUE OR COALESCE(p.presence_status, d.presence_status) = 'ONLINE' THEN 'ONLINE' ELSE 'OFFLINE' END AS status,
                       d.created_at, COALESCE(u.email, '-') AS created_by_email, d.updated_at
                FROM devices d
                JOIN companies c ON c.id = d.company_id
                LEFT JOIN device_brands b ON b.id = d.device_brand_id
                LEFT JOIN device_models m ON m.id = d.device_model_id
                LEFT JOIN device_presence_cache_shadow p ON p.imei = d.imei
                LEFT JOIN users u ON u.id = d.created_by
                WHERE d.deleted_at IS NULL AND c.deleted_at IS NULL
                """;
    }

    private org.springframework.jdbc.core.RowMapper<DeviceRegisterRow> rowMapper() {
        return (rs, rowNum) -> new DeviceRegisterRow(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"), rs.getObject("device_brand_id", Long.class), rs.getString("brand_name"),
                rs.getObject("device_model_id", Long.class), rs.getString("model_name"), rs.getString("imei"), rs.getString("gsm_number"), rs.getString("tcp_host"), rs.getObject("tcp_port", Integer.class),
                rs.getString("protocol_code"), rs.getString("parser_code"), rs.getString("dictionary_code"), rs.getString("status"), rs.getString("created_at"),
                rs.getString("created_by_email"), rs.getString("updated_at")
        );
    }

    private Optional<ModelProtocol> protocolByModel(Long modelId) {
        if (modelId == null) return Optional.empty();
        return jdbcTemplate.query("""
                SELECT brand_id, model_name, protocol_code, parser_code, dictionary_code
                FROM device_models
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> new ModelProtocol(rs.getObject("brand_id", Long.class), rs.getString("model_name"), rs.getString("protocol_code"), rs.getString("parser_code"), rs.getString("dictionary_code")), modelId).stream().findFirst();
    }

    private boolean exists(String table, Long id) {
        if (id == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
    }
    private String verifiedModelSql() { return """
            EXISTS (SELECT 1 FROM protocol_registry p WHERE p.protocol_code=m.protocol_code AND p.status='ACTIVE')
            AND EXISTS (SELECT 1 FROM dictionary_registry dr WHERE dr.device_model_id=m.id AND dr.dictionary_code=m.dictionary_code AND dr.status='ACTIVE')
            AND EXISTS (SELECT 1 FROM device_io_mappings io WHERE io.device_model_id=m.id AND io.source_protocol IN (m.protocol_code, m.parser_code) AND io.status='ACTIVE')
            """; }
    private Long effectiveBrandId(DeviceRegisterRequest request, ModelProtocol protocol) { return request.deviceBrandId() != null ? request.deviceBrandId() : protocol.brandId(); }
    private String normalizeImei(String value) { return clean(value) == null ? null : clean(value).replaceAll("\\s+", ""); }
    private String normalizeRegisterStatus(String value) { return value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase().replaceAll("[\\s_-]+", "_"); }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private String normalizeRole(String role) { return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", ""); }
    private ResponseStatusException duplicateDeviceException(DataIntegrityViolationException ex) { return new ResponseStatusException(HttpStatus.CONFLICT, "Device IMEI sudah terdaftar."); }
    private record ModelProtocol(Long brandId, String modelName, String protocolCode, String parserCode, String dictionaryCode) {}
}
