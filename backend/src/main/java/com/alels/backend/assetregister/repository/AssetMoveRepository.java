package com.alels.backend.assetregister.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.AssetMoveDtos.AssetMoveRecord;

@Repository
public class AssetMoveRepository {
    private final JdbcTemplate jdbc;

    public AssetMoveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssetMoveRecord> findAssets(String type, List<Long> ids) {
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = switch (type) {
            case "DEVICE" -> "SELECT id, company_id, imei AS identifier FROM devices WHERE deleted_at IS NULL AND id IN (" + placeholders + ")";
            case "VEHICLE" -> "SELECT id, company_id, COALESCE(NULLIF(plate_number,''), vehicle_name, id::text) AS identifier FROM vehicles WHERE deleted_at IS NULL AND id IN (" + placeholders + ")";
            case "DRIVER" -> "SELECT id, company_id, COALESCE(NULLIF(driver_name,''), driver_code, id::text) AS identifier FROM asset_drivers WHERE deleted_at IS NULL AND id IN (" + placeholders + ")";
            default -> throw new IllegalArgumentException("Unsupported asset type");
        };
        return jdbc.query(sql, (rs, row) -> new AssetMoveRecord(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("identifier")
        ), ids.toArray());
    }

    public boolean canAccessCompany(Long targetCompanyId, Long actorCompanyId, String role) {
        String normalized = normalizeRole(role);
        if ("SUPERADMIN".equals(normalized) || "ADMIN".equals(normalized)) return companyExists(targetCompanyId);
        if (!"OWNER".equals(normalized) && !"MANAGER".equals(normalized)) return false;
        Integer count = jdbc.queryForObject("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id FROM companies child
                    JOIN visible_companies parent ON child.parent_company_id = parent.id
                    WHERE child.deleted_at IS NULL
                )
                SELECT COUNT(*) FROM visible_companies WHERE id = ?
                """, Integer.class, actorCompanyId, targetCompanyId);
        return count != null && count > 0;
    }

    public List<String> dependencies(String type, Long id) {
        return switch (type) {
            case "DEVICE" -> jdbc.queryForList("""
                    SELECT detail FROM (
                        SELECT 'Telemetry Group ' || g.group_name ||
                               CASE WHEN g.deleted_at IS NULL THEN ' (active)' ELSE ' (wasted)' END AS detail
                        FROM telemetry_group_devices membership
                        JOIN telemetry_groups g ON g.id = membership.group_id
                        WHERE membership.device_id = ?
                        UNION ALL
                        SELECT 'Assignment vehicle ' || COALESCE(NULLIF(v.plate_number,''), v.vehicle_name, v.id::text)
                        FROM vehicle_device_assignments assignment
                        JOIN vehicles v ON v.id = assignment.vehicle_id
                        WHERE assignment.device_id = ? AND assignment.assignment_status = 'ACTIVE'
                          AND assignment.deleted_at IS NULL
                    ) dependency
                    """, String.class, id, id);
            case "VEHICLE" -> jdbc.queryForList("""
                    SELECT 'Assignment device IMEI ' || d.imei
                    FROM vehicle_device_assignments assignment
                    JOIN devices d ON d.id = assignment.device_id
                    WHERE assignment.vehicle_id = ? AND assignment.assignment_status = 'ACTIVE'
                      AND assignment.deleted_at IS NULL
                    """, String.class, id);
            case "DRIVER" -> jdbc.queryForList("""
                    SELECT detail FROM (
                        SELECT 'Manual Assignment vehicle ' || COALESCE(NULLIF(v.plate_number,''), v.vehicle_name, v.id::text) AS detail
                        FROM driver_manual_assignments assignment
                        LEFT JOIN vehicles v ON v.id = assignment.vehicle_id
                        WHERE assignment.driver_id = ? AND assignment.assignment_status = 'ACTIVE'
                          AND assignment.deleted_at IS NULL
                        UNION ALL
                        SELECT 'RFID/automatic driver session IMEI ' || COALESCE(d.imei, '-')
                        FROM driver_auto_sessions session
                        LEFT JOIN devices d ON d.id = session.device_id
                        WHERE session.driver_id = ? AND session.session_status = 'ACTIVE'
                    ) dependency
                    """, String.class, id, id);
            default -> List.of();
        };
    }

    public void move(String type, Long id, Long targetCompanyId, Long actorUserId) {
        String table = switch (type) {
            case "DEVICE" -> "devices";
            case "VEHICLE" -> "vehicles";
            case "DRIVER" -> "asset_drivers";
            default -> throw new IllegalArgumentException("Unsupported asset type");
        };
        jdbc.update("UPDATE " + table + " SET company_id = ?, updated_by = ?, updated_at = NOW() WHERE id = ? AND deleted_at IS NULL",
                targetCompanyId, actorUserId, id);
    }

    public void logMove(Long actorUserId, Long actorCompanyId, String type, Long id, Long oldCompanyId, Long newCompanyId) {
        jdbc.update("""
                INSERT INTO organization_activity_logs(
                    actor_user_id, actor_company_id, target_type, target_id, action, details
                )
                VALUES (?, ?, ?, ?, 'ASSET_MOVE',
                        jsonb_build_object(
                            'assetType', ?, 'assetId', ?, 'oldCompanyId', ?,
                            'newCompanyId', ?, 'movedBy', ?, 'movedAt', NOW()
                        ))
                """, actorUserId, actorCompanyId, type, id, type, id, oldCompanyId,
                newCompanyId, actorUserId);
    }

    private boolean companyExists(Long companyId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE id = ? AND deleted_at IS NULL",
                Integer.class, companyId
        );
        return count != null && count > 0;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", "");
    }
}
