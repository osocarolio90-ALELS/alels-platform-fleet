package com.alels.backend.masterdata.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.MasterWastedDtos.MasterWastedRow;

@Repository
public class MasterWastedRepository {
    private final JdbcTemplate jdbcTemplate;

    public MasterWastedRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<MasterWastedRow> findWasted(String itemType) {
        String type = normalizeType(itemType);
        if (!tableExists(tableName(type))) return List.of();
        return jdbcTemplate.query(baseSql(type) + " ORDER BY deleted_at DESC", mapper());
    }

    public void restore(String itemType, Long id, Long actorUserId) {
        String table = tableName(normalizeType(itemType));
        if (!tableExists(table)) return;
        jdbcTemplate.update("""
                UPDATE %s
                SET deleted_at = NULL, deleted_by = NULL, deleted_reason = NULL, delete_permanent_at = NULL,
                    is_active = TRUE, status = 'ACTIVE', updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NOT NULL
                """.formatted(table), actorUserId, id);
    }

    public void permanentDelete(String itemType, Long id) {
        String table = tableName(normalizeType(itemType));
        if (!tableExists(table)) return;
        jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ? AND deleted_at IS NOT NULL", id);
    }

    private String baseSql(String type) {
        if ("LICENSE".equals(type)) {
            return """
                    SELECT 'LICENSE' AS item_type, lm.id, lm.name, lm.code, COALESCE(c.country_name, lm.country_code) AS extra,
                           lm.status, lm.deleted_at, lm.deleted_by, u.email AS deleted_by_email,
                           lm.delete_permanent_at, GREATEST(0, CEIL(EXTRACT(EPOCH FROM (lm.delete_permanent_at - NOW())) / 86400.0))::INT AS remaining_days,
                           lm.deleted_reason
                    FROM license_master lm
                    LEFT JOIN energy_reference_countries c ON c.country_code = lm.country_code
                    LEFT JOIN users u ON u.id = lm.deleted_by
                    WHERE lm.deleted_at IS NOT NULL
                    """;
        }
        if ("DEVICE".equals(type)) {
            return """
                    SELECT 'DEVICE' AS item_type, dm.id, dm.model_name AS name, dm.model_code AS code, db.brand_name AS extra,
                           dm.status, dm.deleted_at, dm.deleted_by, u.email AS deleted_by_email,
                           dm.delete_permanent_at, GREATEST(0, CEIL(EXTRACT(EPOCH FROM (dm.delete_permanent_at - NOW())) / 86400.0))::INT AS remaining_days,
                           dm.deleted_reason
                    FROM device_models dm
                    LEFT JOIN device_brands db ON db.id = dm.brand_id
                    LEFT JOIN users u ON u.id = dm.deleted_by
                    WHERE dm.deleted_at IS NOT NULL
                    """;
        }
        if ("PRICE".equals(type)) {
            return """
                    SELECT 'PRICE' AS item_type, erp.id, COALESCE(et.energy_name, erp.energy_code) AS name, erp.energy_code AS code, erp.country_code AS extra,
                           COALESCE(erp.status, 'INACTIVE') AS status, erp.deleted_at, erp.deleted_by, u.email AS deleted_by_email,
                           erp.delete_permanent_at, GREATEST(0, CEIL(EXTRACT(EPOCH FROM (erp.delete_permanent_at - NOW())) / 86400.0))::INT AS remaining_days,
                           erp.deleted_reason
                    FROM energy_reference_prices erp
                    LEFT JOIN energy_types et ON et.id = erp.energy_id
                    LEFT JOIN users u ON u.id = erp.deleted_by
                    WHERE erp.deleted_at IS NOT NULL
                    """;
        }
        return """
                SELECT 'VEHICLE' AS item_type, vt.id, vt.type_name AS name, vt.type_code AS code, 'Vehicle Type' AS extra,
                       COALESCE(vt.status, CASE WHEN vt.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END) AS status, vt.deleted_at, vt.deleted_by, u.email AS deleted_by_email,
                       vt.delete_permanent_at, GREATEST(0, CEIL(EXTRACT(EPOCH FROM (vt.delete_permanent_at - NOW())) / 86400.0))::INT AS remaining_days,
                       vt.deleted_reason
                FROM vehicle_types vt
                LEFT JOIN users u ON u.id = vt.deleted_by
                WHERE vt.deleted_at IS NOT NULL
                """;
    }

    private org.springframework.jdbc.core.RowMapper<MasterWastedRow> mapper() {
        return (rs, rowNum) -> new MasterWastedRow(rs.getString("item_type"), rs.getLong("id"), rs.getString("name"), rs.getString("code"), rs.getString("extra"), rs.getString("status"), rs.getString("deleted_at"), rs.getObject("deleted_by", Long.class), rs.getString("deleted_by_email"), rs.getString("delete_permanent_at"), rs.getObject("remaining_days", Integer.class), rs.getString("deleted_reason"));
    }

    private String normalizeType(String type) {
        String t = type == null ? "LICENSE" : type.trim().toUpperCase().replaceAll("[\\s_-]+", "");
        return switch (t) { case "DEVICE" -> "DEVICE"; case "PRICE", "HARGA" -> "PRICE"; case "VEHICLE" -> "VEHICLE"; default -> "LICENSE"; };
    }

    private String tableName(String type) { return switch (type) { case "DEVICE" -> "device_models"; case "PRICE" -> "energy_reference_prices"; case "VEHICLE" -> "vehicle_types"; default -> "license_master"; }; }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?", Integer.class, table);
        return count != null && count > 0;
    }
}
