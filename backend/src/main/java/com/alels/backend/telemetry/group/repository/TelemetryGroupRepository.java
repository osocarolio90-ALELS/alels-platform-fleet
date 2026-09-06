package com.alels.backend.telemetry.group.repository;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.DeviceOption;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.GroupRow;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.LogRow;

@Repository
public class TelemetryGroupRepository {
    private final JdbcTemplate jdbc;

    public TelemetryGroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<GroupRow> list(Long companyId, String role, boolean wasted) {
        String scope = scope(role, "g");
        String sql = """
            WITH RECURSIVE visible_companies AS (
              SELECT id FROM companies WHERE id = ?
              UNION ALL
              SELECT c.id FROM companies c
              JOIN visible_companies v ON c.parent_company_id = v.id
              WHERE c.deleted_at IS NULL
            )
            SELECT
              g.id,
              g.company_id,
              c.company_name,
              g.group_name,
              g.description,
              COUNT(m.id) device_count,
              COALESCE(cu.email, '-') created_by,
              g.created_at,
              COALESCE(du.email, '-') deleted_by,
              g.deleted_at,
              g.delete_permanent_at,
              COALESCE(array_agg(m.device_id) FILTER (WHERE m.device_id IS NOT NULL), '{}') device_ids
            FROM telemetry_groups g
            JOIN companies c ON c.id = g.company_id
            LEFT JOIN telemetry_group_devices m ON m.group_id = g.id
            LEFT JOIN users cu ON cu.id = g.created_by
            LEFT JOIN users du ON du.id = g.deleted_by
            WHERE g.deleted_at IS %s %s
            GROUP BY g.id, c.company_name, cu.email, du.email
            ORDER BY %s
            """.formatted(
                wasted ? "NOT NULL" : "NULL",
                scope,
                wasted ? "g.deleted_at DESC" : "g.created_at DESC"
            );

        return jdbc.query(
            Objects.requireNonNull(sql),
            (rs, n) -> new GroupRow(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getString("group_name"),
                rs.getString("description"),
                rs.getInt("device_count"),
                rs.getString("created_by"),
                rs.getString("created_at"),
                rs.getString("deleted_by"),
                rs.getString("deleted_at"),
                rs.getString("delete_permanent_at"),
                List.of((Long[]) rs.getArray("device_ids").getArray())
            ),
            companyId
        );
    }

    public Optional<GroupRow> find(Long id, Long companyId, String role, boolean wasted) {
        return list(companyId, role, wasted)
            .stream()
            .filter(g -> g.id().equals(id))
            .findFirst();
    }

    public List<DeviceOption> devices(
            Long companyId,
            String role,
            Long currentGroupId,
            boolean availableOnly
    ) {
        String normalizedRole = normalizeRole(role);

        boolean global = "SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole);
        boolean ownOnly = "CLIENTUSER".equals(normalizedRole) || "TECHUSER".equals(normalizedRole);

        String companyScope = "";
        if (ownOnly) {
            companyScope = " AND d.company_id = ? ";
        } else if (!global) {
            companyScope = " AND d.company_id IN (SELECT id FROM visible_companies) ";
        }

        String membershipFilter = "";
        if (availableOnly) {
            if (currentGroupId == null) {
                membershipFilter = """
                    AND NOT EXISTS (
                      SELECT 1
                      FROM telemetry_group_devices m
                      WHERE m.device_id = d.id
                    )
                    """;
            } else {
                membershipFilter = """
                    AND (
                      EXISTS (
                        SELECT 1
                        FROM telemetry_group_devices m
                        WHERE m.device_id = d.id
                          AND m.group_id = ?
                      )
                      OR NOT EXISTS (
                        SELECT 1
                        FROM telemetry_group_devices m
                        WHERE m.device_id = d.id
                      )
                    )
                    """;
            }
        }

        String sql = """
            WITH RECURSIVE visible_companies AS (
              SELECT id FROM companies WHERE id = ?
              UNION ALL
              SELECT c.id
              FROM companies c
              JOIN visible_companies v ON c.parent_company_id = v.id
              WHERE c.deleted_at IS NULL
            )
            SELECT
              d.id,
              d.imei,
              COALESCE(av.vehicle_name, av.plate_number, v.vehicle_name, v.plate_number, '-') vehicle,
              d.company_id,
              c.company_name,
              COALESCE(dm.model_name, d.device_model, '-') device_model
            FROM devices d
            JOIN companies c ON c.id = d.company_id
            LEFT JOIN vehicles v ON v.id = d.vehicle_id
            LEFT JOIN LATERAL (
              SELECT assigned.vehicle_name, assigned.plate_number
              FROM vehicle_device_assignments a
              JOIN vehicles assigned ON assigned.id = a.vehicle_id
              WHERE a.device_id = d.id
                AND a.deleted_at IS NULL
                AND a.assignment_status = 'ACTIVE'
              ORDER BY a.assigned_at DESC
              LIMIT 1
            ) av ON TRUE
            LEFT JOIN device_models dm ON dm.id = d.device_model_id
            WHERE d.deleted_at IS NULL
              AND c.deleted_at IS NULL
              %s
              %s
            ORDER BY c.company_name, d.imei
            """.formatted(companyScope, membershipFilter);

        List<Object> params = new ArrayList<>();
        params.add(companyId);

        if (ownOnly) {
            params.add(companyId);
        }

        if (availableOnly && currentGroupId != null) {
            params.add(currentGroupId);
        }

        return jdbc.query(
            Objects.requireNonNull(sql),
            (rs, n) -> new DeviceOption(
                rs.getLong("id"),
                rs.getString("imei"),
                rs.getString("vehicle"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getString("device_model")
            ),
            params.toArray()
        );
    }

    public Long create(Long companyId, String name, String description, Long userId) {
        return jdbc.queryForObject(
            """
            INSERT INTO telemetry_groups(company_id, group_name, description, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            companyId,
            name,
            description,
            userId,
            userId
        );
    }

    public void update(Long id, String name, String description, Long userId) {
        jdbc.update(
            """
            UPDATE telemetry_groups
            SET group_name = ?,
                description = ?,
                updated_by = ?,
                updated_at = NOW()
            WHERE id = ?
              AND deleted_at IS NULL
            """,
            name,
            description,
            userId,
            id
        );
    }

    public void replaceDevices(Long id, List<Long> ids, Long userId) {
        jdbc.update("DELETE FROM telemetry_group_devices WHERE group_id = ?", id);

        for (Long deviceId : ids) {
            jdbc.update(
                """
                INSERT INTO telemetry_group_devices(group_id, device_id, created_by)
                VALUES (?, ?, ?)
                """,
                id,
                deviceId,
                userId
            );
        }
    }

    public void lockDevices(List<Long> ids) {
        if (ids.isEmpty()) return;

        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));

        jdbc.queryForList(
            "SELECT id FROM devices WHERE id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
            ids.toArray()
        );
    }

    public Optional<DeviceAssignment> activeAssignment(
        Long deviceId,
        Long excludeGroupId
    ) {

        String sql;

        List<Object> params = new ArrayList<>();

        params.add(deviceId);


        if (excludeGroupId == null) {

            sql = """
                SELECT
                    d.imei,
                    g.group_name

                FROM telemetry_group_devices membership

                JOIN telemetry_groups g
                    ON g.id = membership.group_id

                JOIN devices d
                    ON d.id = membership.device_id

                WHERE membership.device_id = ?
                """;

        } else {

            sql = """
                SELECT
                    d.imei,
                    g.group_name

                FROM telemetry_group_devices membership

                JOIN telemetry_groups g
                    ON g.id = membership.group_id

                JOIN devices d
                    ON d.id = membership.device_id

                WHERE membership.device_id = ?
                AND membership.group_id <> ?
                """;

            params.add(excludeGroupId);
        }


        return jdbc.query(
            sql,
            (rs, n) -> new DeviceAssignment(
                rs.getString("imei"),
                rs.getString("group_name")
            ),
            params.toArray()
        ).stream().findFirst();
    }

    public void softDelete(Long id, Long userId) {
        jdbc.update(
            """
            UPDATE telemetry_groups
            SET deleted_at = NOW(),
                deleted_by = ?,
                delete_permanent_at = NOW() + INTERVAL '30 days',
                updated_at = NOW()
            WHERE id = ?
              AND deleted_at IS NULL
            """,
            userId,
            id
        );
    }

    public void restore(Long id, Long userId) {
        jdbc.update(
            """
            UPDATE telemetry_groups
            SET deleted_at = NULL,
                deleted_by = NULL,
                delete_permanent_at = NULL,
                deleted_reason = NULL,
                updated_by = ?,
                updated_at = NOW()
            WHERE id = ?
              AND deleted_at IS NOT NULL
            """,
            userId,
            id
        );
    }

    public void permanentDelete(Long id) {
        jdbc.update(
            "DELETE FROM telemetry_groups WHERE id = ? AND deleted_at IS NOT NULL",
            id
        );
    }

    public boolean duplicate(Long companyId, String name, Long exclude) {
        Integer count;

        if (exclude == null) {
            count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM telemetry_groups
                WHERE company_id = ?
                  AND UPPER(TRIM(group_name)) = ?
                  AND deleted_at IS NULL
                """,
                Integer.class,
                companyId,
                name
            );
        } else {
            count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM telemetry_groups
                WHERE company_id = ?
                  AND UPPER(TRIM(group_name)) = ?
                  AND deleted_at IS NULL
                  AND id <> ?
                """,
                Integer.class,
                companyId,
                name,
                exclude
            );
        }

        return count != null && count > 0;
    }

    public void log(GroupRow group, Long userId, String action, String details) {
        jdbc.update(
            """
            INSERT INTO telemetry_group_logs(
              group_id,
              company_id,
              group_name,
              actor_user_id,
              action,
              device_count,
              details
            )
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
            """,
            group.id(),
            group.companyId(),
            group.groupName(),
            userId,
            action,
            group.deviceCount(),
            details
        );
    }

    public List<LogRow> logs(Long companyId, String role) {
        String sql = """
            WITH RECURSIVE visible_companies AS (
              SELECT id FROM companies WHERE id = ?
              UNION ALL
              SELECT c.id
              FROM companies c
              JOIN visible_companies v ON c.parent_company_id = v.id
              WHERE c.deleted_at IS NULL
            )
            SELECT
              l.created_at,
              l.action,
              c.company_name,
              l.group_name,
              COALESCE(u.email, '-') actor,
              l.device_count,
              l.details::text details
            FROM telemetry_group_logs l
            LEFT JOIN companies c ON c.id = l.company_id
            LEFT JOIN users u ON u.id = l.actor_user_id
            WHERE 1 = 1 %s
            ORDER BY l.created_at DESC
            """.formatted(scope(role, "l"));

        return jdbc.query(
            Objects.requireNonNull(sql),
            (rs, n) -> new LogRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getInt(6),
                rs.getString(7)
            ),
            companyId
        );
    }

    private String scope(String role, String alias) {
        String normalized = normalizeRole(role);

        if ("SUPERADMIN".equals(normalized) || "ADMIN".equals(normalized)) {
            return "";
        }

        if ("CLIENTUSER".equals(normalized) || "TECHUSER".equals(normalized)) {
            return "AND " + alias + ".company_id = ?";
        }

        return "AND " + alias + ".company_id IN (SELECT id FROM visible_companies)";
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.replace("_", "").replace(" ", "").toUpperCase();
    }

    public record DeviceAssignment(String imei, String groupName) {}
}
