package com.alels.backend.masterdata.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.MasterWastedDtos.MasterWastedRow;

@Repository
public class MasterWastedRepository {
    private final JdbcTemplate jdbcTemplate;

    public MasterWastedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MasterWastedRow> listVehicleMaster() {
        return jdbcTemplate.query("""
                SELECT id, 'vehicle' AS category, master_type, code, name, parent_name, status, deleted_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
                       COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') AS delete_permanent_at,
                       deleted_by_email, reason
                FROM (
                    SELECT vt.id, 'Vehicle Type' AS master_type, vt.type_code AS code, vt.type_name AS name, NULL::TEXT AS parent_name,
                           CASE WHEN vt.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status, vt.deleted_at, vt.delete_permanent_at,
                           u.email AS deleted_by_email, vt.deleted_reason AS reason
                    FROM vehicle_types vt LEFT JOIN users u ON u.id = vt.deleted_by WHERE vt.deleted_at IS NOT NULL
                    UNION ALL
                    SELECT vb.id, 'Vehicle Brand', vb.brand_code, vb.brand_name, NULL::TEXT,
                           CASE WHEN vb.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END, vb.deleted_at, vb.delete_permanent_at,
                           u.email, vb.deleted_reason
                    FROM vehicle_brands vb LEFT JOIN users u ON u.id = vb.deleted_by WHERE vb.deleted_at IS NOT NULL
                    UNION ALL
                    SELECT vm.id, 'Vehicle Model', vm.model_code, vm.model_name, vb.brand_name,
                           CASE WHEN vm.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END, vm.deleted_at, vm.delete_permanent_at,
                           u.email, vm.deleted_reason
                    FROM vehicle_models vm LEFT JOIN vehicle_brands vb ON vb.id = vm.brand_id LEFT JOIN users u ON u.id = vm.deleted_by WHERE vm.deleted_at IS NOT NULL
                    UNION ALL
                    SELECT vo.id, 'Ownership Type', vo.ownership_code, vo.ownership_name, NULL::TEXT,
                           CASE WHEN vo.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END, vo.deleted_at, vo.delete_permanent_at,
                           u.email, vo.deleted_reason
                    FROM vehicle_ownership_types vo LEFT JOIN users u ON u.id = vo.deleted_by WHERE vo.deleted_at IS NOT NULL
                    UNION ALL
                    SELECT cu.id, 'Capacity Unit', cu.unit_code, cu.unit_name, NULL::TEXT,
                           CASE WHEN cu.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END, cu.deleted_at, cu.delete_permanent_at,
                           u.email, cu.deleted_reason
                    FROM vehicle_capacity_units cu LEFT JOIN users u ON u.id = cu.deleted_by WHERE cu.deleted_at IS NOT NULL
                ) wasted
                ORDER BY deleted_at DESC, master_type, name
                """, (rs, rowNum) -> row(rs));
    }

    public List<MasterWastedRow> listDeviceMaster() {
        return jdbcTemplate.query("""
                SELECT id, 'device' AS category, master_type, code, name, parent_name, status, deleted_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
                       COALESCE(delete_permanent_at, deleted_at + INTERVAL '30 days') AS delete_permanent_at,
                       deleted_by_email, reason
                FROM (
                    SELECT db.id, 'Device Brand' AS master_type, db.brand_code AS code, db.brand_name AS name, NULL::TEXT AS parent_name,
                           CASE WHEN db.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status, db.deleted_at, db.delete_permanent_at,
                           u.email AS deleted_by_email, db.deleted_reason AS reason
                    FROM device_brands db LEFT JOIN users u ON u.id = db.deleted_by WHERE db.deleted_at IS NOT NULL
                    UNION ALL
                    SELECT dm.id, 'Device Model', dm.model_code, dm.model_name, db.brand_name,
                           CASE WHEN dm.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END, dm.deleted_at, dm.delete_permanent_at,
                           u.email, dm.deleted_reason
                    FROM device_models dm LEFT JOIN device_brands db ON db.id = dm.brand_id LEFT JOIN users u ON u.id = dm.deleted_by WHERE dm.deleted_at IS NOT NULL
                ) wasted
                ORDER BY deleted_at DESC, master_type, name
                """, (rs, rowNum) -> row(rs));
    }

    public List<MasterWastedRow> listHargaMaster() {
        return jdbcTemplate.query("""
                SELECT erp.id, 'harga' AS category, 'Harga Master' AS master_type,
                       COALESCE(erp.energy_code, et.energy_code) AS code,
                       CONCAT(et.energy_name, ' - ', erp.country_name, ' (', erp.currency, ')') AS name,
                       erp.country_name AS parent_name,
                       erp.provider_status AS status,
                       erp.deleted_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(erp.delete_permanent_at, erp.deleted_at + INTERVAL '30 days') - NOW())) / 86400.0))::INT AS remaining_days,
                       COALESCE(erp.delete_permanent_at, erp.deleted_at + INTERVAL '30 days') AS delete_permanent_at,
                       u.email AS deleted_by_email,
                       erp.deleted_reason AS reason
                FROM energy_reference_prices erp
                JOIN energy_types et ON et.id = erp.energy_id
                LEFT JOIN users u ON u.id = erp.deleted_by
                WHERE erp.deleted_at IS NOT NULL
                ORDER BY erp.deleted_at DESC, erp.country_name, et.energy_name
                """, (rs, rowNum) -> row(rs));
    }

    public void restore(String category, String masterType, Long id, Long actorUserId) {
        String table = tableName(category, masterType);
        if ("energy_reference_prices".equals(table)) {
            jdbcTemplate.update("""
                    UPDATE energy_reference_prices
                    SET deleted_at = NULL, deleted_by = NULL, deleted_reason = NULL, delete_permanent_at = NULL,
                        updated_by = ?, updated_at = NOW()
                    WHERE id = ? AND deleted_at IS NOT NULL
                    """, actorUserId, id);
            return;
        }
        jdbcTemplate.update("""
                UPDATE %s
                SET deleted_at = NULL, deleted_by = NULL, deleted_reason = NULL, delete_permanent_at = NULL,
                    is_active = TRUE, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NOT NULL
                """.formatted(table), actorUserId, id);
    }

    public void permanentDelete(String category, String masterType, Long id) {
        jdbcTemplate.update("DELETE FROM %s WHERE id = ? AND deleted_at IS NOT NULL".formatted(tableName(category, masterType)), id);
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                """, actorUserId, actorCompanyId, targetType, targetId, action);
    }

    private String tableName(String category, String masterType) {
        String type = masterType == null ? "" : masterType.trim().toLowerCase();
        String cat = category == null ? "" : category.trim().toLowerCase();
        if ("vehicle".equals(cat)) {
            return switch (type) {
                case "vehicle type" -> "vehicle_types";
                case "vehicle brand" -> "vehicle_brands";
                case "vehicle model" -> "vehicle_models";
                case "ownership type" -> "vehicle_ownership_types";
                case "capacity unit" -> "vehicle_capacity_units";
                default -> throw new IllegalArgumentException("Invalid vehicle master type");
            };
        }
        if ("device".equals(cat)) {
            return switch (type) {
                case "device brand" -> "device_brands";
                case "device model" -> "device_models";
                default -> throw new IllegalArgumentException("Invalid device master type");
            };
        }
        if ("harga".equals(cat)) return "energy_reference_prices";
        throw new IllegalArgumentException("Invalid master category");
    }

    private MasterWastedRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MasterWastedRow(
                rs.getLong("id"), rs.getString("category"), rs.getString("master_type"), rs.getString("code"), rs.getString("name"),
                rs.getString("parent_name"), rs.getString("status"), rs.getString("deleted_at"), rs.getInt("remaining_days"),
                rs.getString("delete_permanent_at"), rs.getString("deleted_by_email"), rs.getString("reason")
        );
    }
}
