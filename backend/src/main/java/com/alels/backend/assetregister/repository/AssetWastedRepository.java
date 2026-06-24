package com.alels.backend.assetregister.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.AssetWastedDtos.AssetWastedRow;

@Repository
public class AssetWastedRepository {
    private final JdbcTemplate jdbcTemplate;

    public AssetWastedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssetWastedRow> findWasted(Long actorCompanyId, String role, String itemType) {
        String normalizedRole = normalizeRole(role);
        String normalizedType = normalizeItemType(itemType);
        if (!tableAvailableFor(normalizedType)) return List.of();
        String sql = baseWastedSql(normalizedType);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(sql + " ORDER BY deleted_at DESC", wastedMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + sql + " AND " + tableAlias(normalizedType) + ".company_id IN (SELECT id FROM visible_companies) ORDER BY deleted_at DESC",
                wastedMapper(), actorCompanyId);
    }

    public Optional<Long> companyIdIncludingDeleted(String itemType, Long id) {
        String normalizedType = normalizeItemType(itemType);
        if (!tableAvailableFor(normalizedType)) return Optional.empty();
        String tableName = tableName(normalizedType);
        return jdbcTemplate.query("SELECT company_id FROM " + tableName + " WHERE id = ?", (rs, rowNum) -> rs.getLong("company_id"), id).stream().findFirst();
    }

    public void restore(String itemType, Long id, Long actorUserId) {
        String normalizedType = normalizeItemType(itemType);
        if (!tableAvailableFor(normalizedType)) return;
        String tableName = tableName(normalizedType);
        jdbcTemplate.update("""
                UPDATE %s
                SET deleted_at = NULL,
                    deleted_by = NULL,
                    delete_permanent_at = NULL,
                    deleted_reason = NULL,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NOT NULL
                """.formatted(tableName), actorUserId, id);
    }

    public void permanentDelete(String itemType, Long id) {
        String normalizedType = normalizeItemType(itemType);
        if (!tableAvailableFor(normalizedType)) return;
        String tableName = tableName(normalizedType);
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ? AND deleted_at IS NOT NULL", id);
    }

    public long purgeExpiredWasted() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(deleted_rows), 0) FROM alels_purge_asset_wasted()", Long.class);
            return count == null ? 0L : count;
        } catch (DataAccessException ignored) {
            return 0L;
        }
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

    public void log(Long actorUserId, Long actorCompanyId, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, 'ASSET_WASTED', ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetId, action);
        } catch (DataAccessException ignored) {
            // Audit log must not break asset wasted operations.
        }
    }

    private String baseWastedSql(String itemType) {
        if ("DEVICE".equals(itemType)) return deviceWastedSql();
        if ("DRIVER".equals(itemType)) return driverWastedSql();
        return vehicleWastedSql();
    }

    private String deviceWastedSql() {
        String table = "devices";
        String deletedByExpr = columnExists(table, "deleted_by") ? "d.deleted_by" : "NULL::BIGINT";
        String deletedByJoin = columnExists(table, "deleted_by") ? "LEFT JOIN users du ON du.id = d.deleted_by" : "LEFT JOIN users du ON false";
        String purgeExpr = columnExists(table, "delete_permanent_at") ? "d.delete_permanent_at" : "(d.deleted_at + INTERVAL '30 days')";
        String reasonExpr = columnExists(table, "deleted_reason") ? "d.deleted_reason" : "NULL::TEXT";
        String statusExpr = statusExpr(table, "d", "COALESCE(d.presence_status, CASE WHEN COALESCE(d.online, false) THEN 'ONLINE' ELSE 'OFFLINE' END)", "DELETED");
        String nameExpr = coalesceTrim(List.of(expr(table, "d", "imei")), "'DEVICE-' || d.id::text");
        String extraExpr = firstExistingText(table, "d", List.of("device_name", "device_model", "model_name", "model"), "NULL::TEXT");
        return """
                SELECT 'DEVICE' AS item_type,
                       d.id,
                       d.company_id,
                       c.company_name,
                       %s AS name,
                       %s AS code,
                       %s AS extra,
                       %s AS status,
                       d.deleted_at,
                       %s AS deleted_by,
                       du.email AS deleted_by_email,
                       %s AS delete_permanent_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM ((%s) - NOW())) / 86400.0))::INT AS remaining_days,
                       %s AS deleted_reason
                FROM devices d
                LEFT JOIN companies c ON c.id = d.company_id
                %s
                WHERE d.deleted_at IS NOT NULL
                """.formatted(nameExpr, expr(table, "d", "imei"), extraExpr, statusExpr, deletedByExpr, purgeExpr, purgeExpr, reasonExpr, deletedByJoin);
    }

    private String driverWastedSql() {
        String table = "asset_drivers";
        String deletedByExpr = columnExists(table, "deleted_by") ? "ad.deleted_by" : "NULL::BIGINT";
        String deletedByJoin = columnExists(table, "deleted_by") ? "LEFT JOIN users du ON du.id = ad.deleted_by" : "LEFT JOIN users du ON false";
        String purgeExpr = columnExists(table, "delete_permanent_at") ? "ad.delete_permanent_at" : "(ad.deleted_at + INTERVAL '30 days')";
        String reasonExpr = columnExists(table, "deleted_reason") ? "ad.deleted_reason" : "NULL::TEXT";
        String statusExpr = statusExpr(table, "ad", "ad.status", "DELETED");
        String nameExpr = coalesceTrim(List.of(expr(table, "ad", "driver_name"), expr(table, "ad", "full_name"), expr(table, "ad", "name")), "'DRIVER-' || ad.id::text");
        String codeExpr = firstExistingText(table, "ad", List.of("driver_code", "license_number", "employee_number"), "NULL::TEXT");
        String extraExpr = firstExistingText(table, "ad", List.of("phone_number", "phone", "gsm_number"), "NULL::TEXT");
        return """
                SELECT 'DRIVER' AS item_type,
                       ad.id,
                       ad.company_id,
                       c.company_name,
                       %s AS name,
                       %s AS code,
                       %s AS extra,
                       %s AS status,
                       ad.deleted_at,
                       %s AS deleted_by,
                       du.email AS deleted_by_email,
                       %s AS delete_permanent_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM ((%s) - NOW())) / 86400.0))::INT AS remaining_days,
                       %s AS deleted_reason
                FROM asset_drivers ad
                LEFT JOIN companies c ON c.id = ad.company_id
                %s
                WHERE ad.deleted_at IS NOT NULL
                """.formatted(nameExpr, codeExpr, extraExpr, statusExpr, deletedByExpr, purgeExpr, purgeExpr, reasonExpr, deletedByJoin);
    }

    private String vehicleWastedSql() {
        String table = "vehicles";
        String deletedByExpr = columnExists(table, "deleted_by") ? "v.deleted_by" : "NULL::BIGINT";
        String deletedByJoin = columnExists(table, "deleted_by") ? "LEFT JOIN users du ON du.id = v.deleted_by" : "LEFT JOIN users du ON false";
        String purgeExpr = columnExists(table, "delete_permanent_at") ? "v.delete_permanent_at" : "(v.deleted_at + INTERVAL '30 days')";
        String reasonExpr = columnExists(table, "deleted_reason") ? "v.deleted_reason" : "NULL::TEXT";
        String statusExpr = statusExpr(table, "v", "COALESCE(v.operational_status, v.status)", "DELETED");
        String nameExpr = coalesceTrim(List.of(expr(table, "v", "vehicle_name"), expr(table, "v", "plate_number"), expr(table, "v", "vehicle_code")), "'VEHICLE-' || v.id::text");
        String codeExpr = firstExistingText(table, "v", List.of("plate_number", "vehicle_code"), "NULL::TEXT");
        String extraExpr = vehicleExtraExpr();
        String brandJoin = vehicleBrandJoin();
        String modelJoin = vehicleModelJoin();
        return """
                SELECT 'VEHICLE' AS item_type,
                       v.id,
                       v.company_id,
                       c.company_name,
                       %s AS name,
                       %s AS code,
                       %s AS extra,
                       %s AS status,
                       v.deleted_at,
                       %s AS deleted_by,
                       du.email AS deleted_by_email,
                       %s AS delete_permanent_at,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM ((%s) - NOW())) / 86400.0))::INT AS remaining_days,
                       %s AS deleted_reason
                FROM vehicles v
                LEFT JOIN companies c ON c.id = v.company_id
                %s
                %s
                %s
                WHERE v.deleted_at IS NOT NULL
                """.formatted(nameExpr, codeExpr, extraExpr, statusExpr, deletedByExpr, purgeExpr, purgeExpr, reasonExpr, brandJoin, modelJoin, deletedByJoin);
    }

    private String vehicleBrandJoin() {
        return tableExists("vehicle_brands") && columnExists("vehicles", "brand_id") ? "LEFT JOIN vehicle_brands vb ON vb.id = v.brand_id" : "";
    }

    private String vehicleModelJoin() {
        return tableExists("vehicle_models") && columnExists("vehicles", "model_id") ? "LEFT JOIN vehicle_models vm ON vm.id = v.model_id" : "";
    }

    private String vehicleExtraExpr() {
        List<String> parts = new ArrayList<>();
        if (tableExists("vehicle_brands") && columnExists("vehicles", "brand_id") && columnExists("vehicle_brands", "brand_name")) parts.add("vb.brand_name");
        if (tableExists("vehicle_models") && columnExists("vehicles", "model_id") && columnExists("vehicle_models", "model_name")) parts.add("vm.model_name");
        if (parts.isEmpty()) return "NULL::TEXT";
        return "CONCAT_WS(' / ', " + String.join(", ", parts) + ")";
    }

    private String statusExpr(String table, String alias, String preferred, String fallback) {
        boolean hasPresence = columnExists(table, "presence_status");
        boolean hasOnline = columnExists(table, "online");
        boolean hasOperational = columnExists(table, "operational_status");
        boolean hasStatus = columnExists(table, "status");
        if (preferred.contains("presence_status") && hasPresence && hasOnline) return preferred;
        if (preferred.contains("operational_status") && hasOperational && hasStatus) return preferred;
        if (preferred.endsWith(".status") && hasStatus) return "COALESCE(" + alias + ".status, '" + fallback + "')";
        if (hasStatus) return "COALESCE(" + alias + ".status, '" + fallback + "')";
        return "'" + fallback + "'";
    }

    private org.springframework.jdbc.core.RowMapper<AssetWastedRow> wastedMapper() {
        return (rs, rowNum) -> new AssetWastedRow(
                rs.getString("item_type"),
                rs.getLong("id"),
                rs.getObject("company_id", Long.class),
                rs.getString("company_name"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("extra"),
                rs.getString("status"),
                rs.getString("deleted_at"),
                rs.getObject("deleted_by", Long.class),
                rs.getString("deleted_by_email"),
                rs.getString("delete_permanent_at"),
                rs.getObject("remaining_days", Integer.class),
                rs.getString("deleted_reason")
        );
    }

    private boolean tableAvailableFor(String itemType) {
        String tableName = tableName(itemType);
        return tableExists(tableName)
                && columnExists(tableName, "id")
                && columnExists(tableName, "company_id")
                && columnExists(tableName, "deleted_at");
    }

    private String tableName(String itemType) {
        return switch (normalizeItemType(itemType)) {
            case "DEVICE" -> "devices";
            case "DRIVER" -> "asset_drivers";
            default -> "vehicles";
        };
    }

    private String tableAlias(String itemType) {
        return switch (normalizeItemType(itemType)) {
            case "DEVICE" -> "d";
            case "DRIVER" -> "ad";
            default -> "v";
        };
    }

    private String normalizeItemType(String itemType) {
        String normalized = itemType == null ? "VEHICLE" : itemType.trim().toUpperCase().replaceAll("[\\s_-]+", "");
        return switch (normalized) {
            case "DEVICE" -> "DEVICE";
            case "DRIVER" -> "DRIVER";
            default -> "VEHICLE";
        };
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", "");
    }

    private String expr(String table, String alias, String column) {
        return columnExists(table, column) ? alias + "." + column : "NULL::TEXT";
    }

    private String firstExistingText(String table, String alias, List<String> columns, String fallback) {
        for (String column : columns) {
            if (columnExists(table, column)) return alias + "." + column;
        }
        return fallback;
    }

    private String coalesceTrim(List<String> expressions, String fallback) {
        List<String> parts = expressions.stream()
                .filter(expression -> expression != null && !expression.startsWith("NULL"))
                .map(expression -> "NULLIF(TRIM(" + expression + "), '')")
                .toList();
        if (parts.isEmpty()) return fallback;
        return "COALESCE(" + String.join(", ", parts) + ", " + fallback + ")";
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
