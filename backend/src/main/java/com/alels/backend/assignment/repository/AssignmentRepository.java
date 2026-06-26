package com.alels.backend.assignment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.assignment.dto.AssignmentDtos.AssetAssignmentRow;
import com.alels.backend.assignment.dto.AssignmentDtos.AssignmentLookupOption;
import com.alels.backend.assignment.dto.AssignmentDtos.DriverManualAssignmentRequest;
import com.alels.backend.assignment.dto.AssignmentDtos.DriverManualAssignmentRow;
import com.alels.backend.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRequest;
import com.alels.backend.assignment.dto.AssignmentDtos.VehicleDeviceAssignmentRow;

@Repository
public class AssignmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssetAssignmentRow> assetAssignmentList(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(assetAssignmentBaseSql() + " ORDER BY a.assigned_at DESC", assetAssignmentMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + assetAssignmentBaseSql() + " AND a.company_id IN (SELECT id FROM visible_companies) ORDER BY a.assigned_at DESC",
                assetAssignmentMapper(), actorCompanyId);
    }

    public Optional<Long> activeDriverManualIdByVehicleDevice(Long vehicleId, Long deviceId) {
        if (vehicleId == null || deviceId == null) return Optional.empty();
        return jdbcTemplate.query("""
                SELECT id FROM driver_manual_assignments
                WHERE vehicle_id = ? AND device_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL
                ORDER BY assigned_at DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), vehicleId, deviceId).stream().findFirst();
    }


    public List<VehicleDeviceAssignmentRow> vehicleDeviceList(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(vehicleDeviceBaseSql() + " ORDER BY a.assigned_at DESC", vehicleDeviceMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + vehicleDeviceBaseSql() + " AND a.company_id IN (SELECT id FROM visible_companies) ORDER BY a.assigned_at DESC",
                vehicleDeviceMapper(), actorCompanyId);
    }

    public List<DriverManualAssignmentRow> driverManualList(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(driverManualBaseSql() + " ORDER BY a.assigned_at DESC", driverManualMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + driverManualBaseSql() + " AND a.company_id IN (SELECT id FROM visible_companies) ORDER BY a.assigned_at DESC",
                driverManualMapper(), actorCompanyId);
    }

    public Long createVehicleDevice(VehicleDeviceAssignmentRequest request, Long actorUserId) {
        closeActiveVehicleDeviceByVehicle(request.vehicleId(), actorUserId);
        closeActiveVehicleDeviceByDevice(request.deviceId(), actorUserId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO vehicle_device_assignments(company_id, vehicle_id, device_id, assignment_status, notes, assigned_by, created_by, updated_by)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, request.companyId(), request.vehicleId(), request.deviceId(), clean(request.notes()), actorUserId, actorUserId, actorUserId);
    }

    public void updateVehicleDevice(Long id, VehicleDeviceAssignmentRequest request, Long actorUserId) {
        closeActiveVehicleDeviceByVehicleExcept(request.vehicleId(), id, actorUserId);
        closeActiveVehicleDeviceByDeviceExcept(request.deviceId(), id, actorUserId);
        jdbcTemplate.update("""
                UPDATE vehicle_device_assignments
                SET company_id = ?, vehicle_id = ?, device_id = ?, notes = ?, assignment_status = 'ACTIVE',
                    removed_at = NULL, removed_by = NULL, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, request.companyId(), request.vehicleId(), request.deviceId(), clean(request.notes()), actorUserId, id);
    }

    public void removeVehicleDevice(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE vehicle_device_assignments
                SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL AND assignment_status = 'ACTIVE'
                """, actorUserId, actorUserId, id);
        closeManualDriverByVehicleDeviceAssignment(id, actorUserId);
    }

    public Long createDriverManual(DriverManualAssignmentRequest request, Long actorUserId) {
        closeActiveDriverManualByDriver(request.driverId(), actorUserId);
        closeActiveDriverManualByVehicleDevice(request.vehicleId(), request.deviceId(), actorUserId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO driver_manual_assignments(company_id, vehicle_id, device_id, driver_id, assignment_status, notes, assigned_by, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, request.companyId(), request.vehicleId(), request.deviceId(), request.driverId(), clean(request.notes()), actorUserId, actorUserId, actorUserId);
    }

    public void updateDriverManual(Long id, DriverManualAssignmentRequest request, Long actorUserId) {
        closeActiveDriverManualByDriverExcept(request.driverId(), id, actorUserId);
        closeActiveDriverManualByVehicleDeviceExcept(request.vehicleId(), request.deviceId(), id, actorUserId);
        jdbcTemplate.update("""
                UPDATE driver_manual_assignments
                SET company_id = ?, vehicle_id = ?, device_id = ?, driver_id = ?, notes = ?, assignment_status = 'ACTIVE',
                    removed_at = NULL, removed_by = NULL, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, request.companyId(), request.vehicleId(), request.deviceId(), request.driverId(), clean(request.notes()), actorUserId, id);
    }

    public void removeDriverManual(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE driver_manual_assignments
                SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL AND assignment_status = 'ACTIVE'
                """, actorUserId, actorUserId, id);
    }

    public Optional<Long> assignmentCompanyId(String table, Long id) {
        return jdbcTemplate.query("SELECT company_id FROM " + table + " WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), id).stream().findFirst();
    }

    public Optional<Long> vehicleCompanyId(Long vehicleId) {
        return jdbcTemplate.query("SELECT company_id FROM vehicles WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), vehicleId).stream().findFirst();
    }

    public Optional<Long> deviceCompanyId(Long deviceId) {
        return jdbcTemplate.query("SELECT company_id FROM devices WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), deviceId).stream().findFirst();
    }

    public Optional<Long> driverCompanyId(Long driverId) {
        return jdbcTemplate.query("SELECT company_id FROM asset_drivers WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), driverId).stream().findFirst();
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

    public List<AssignmentLookupOption> companyOptions(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, company_name, company_code, status FROM companies WHERE deleted_at IS NULL ORDER BY company_name
                    """, (rs, rowNum) -> new AssignmentLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")));
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id, company_name, company_code, status FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id, c.company_name, c.company_code, c.status FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT id, company_name, company_code, status FROM visible_companies ORDER BY company_name
                """, (rs, rowNum) -> new AssignmentLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")), actorCompanyId);
    }

    public List<AssignmentLookupOption> vehicleOptions(Long actorCompanyId, String role) {
        String sql = """
                SELECT v.id,
                       COALESCE(NULLIF(v.vehicle_name, ''), v.vehicle_number, CAST(v.id AS VARCHAR)) AS label,
                       v.plate_number AS code, c.company_name AS extra
                FROM vehicles v JOIN companies c ON c.id = v.company_id
                WHERE v.deleted_at IS NULL AND c.deleted_at IS NULL
                """;
        if (isGlobalRole(role)) return jdbcTemplate.query(sql + " ORDER BY c.company_name, label", lookupMapper());
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + sql + " AND v.company_id IN (SELECT id FROM visible_companies) ORDER BY c.company_name, label", lookupMapper(), actorCompanyId);
    }

    public List<AssignmentLookupOption> deviceOptions(Long actorCompanyId, String role) {
        String sql = """
                SELECT d.id,
                       TRIM(CONCAT_WS(' ', NULLIF(db.brand_name, ''), COALESCE(NULLIF(dm.model_name, ''), NULLIF(d.device_model, '')))) AS label,
                       d.imei AS code, c.company_name AS extra
                FROM devices d JOIN companies c ON c.id = d.company_id
                LEFT JOIN device_brands db ON db.id = d.device_brand_id AND db.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id = d.device_model_id AND dm.deleted_at IS NULL
                WHERE d.deleted_at IS NULL AND c.deleted_at IS NULL
                """;
        if (isGlobalRole(role)) return jdbcTemplate.query(sql + " ORDER BY c.company_name, label", lookupMapper());
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + sql + " AND d.company_id IN (SELECT id FROM visible_companies) ORDER BY c.company_name, label", lookupMapper(), actorCompanyId);
    }

    public List<AssignmentLookupOption> driverOptions(Long actorCompanyId, String role) {
        String sql = """
                SELECT d.id, COALESCE(NULLIF(d.driver_name, ''), NULLIF(d.full_name, ''), d.driver_code) AS label,
                       d.driver_code AS code, COALESCE(d.rfid_ibutton, d.license_number, c.company_name) AS extra
                FROM asset_drivers d JOIN companies c ON c.id = d.company_id
                WHERE d.deleted_at IS NULL AND c.deleted_at IS NULL AND COALESCE(d.status, 'ACTIVE') = 'ACTIVE'
                """;
        if (isGlobalRole(role)) return jdbcTemplate.query(sql + " ORDER BY c.company_name, label", lookupMapper());
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + sql + " AND d.company_id IN (SELECT id FROM visible_companies) ORDER BY c.company_name, label", lookupMapper(), actorCompanyId);
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetType, targetId, action);
        } catch (DataAccessException ignored) {}
        try {
            jdbcTemplate.update("""
                    INSERT INTO assignment_events(actor_user_id, actor_company_id, target_type, target_id, action, source)
                    VALUES (?, ?, ?, ?, ?, 'WEB')
                    """, actorUserId, actorCompanyId, targetType, targetId, action);
        } catch (DataAccessException ignored) {}
    }

    private void closeActiveVehicleDeviceByVehicle(Long vehicleId, Long actorUserId) { closeActiveVehicleDeviceByVehicleExcept(vehicleId, null, actorUserId); }
    private void closeActiveVehicleDeviceByDevice(Long deviceId, Long actorUserId) { closeActiveVehicleDeviceByDeviceExcept(deviceId, null, actorUserId); }
    private void closeActiveVehicleDeviceByVehicleExcept(Long vehicleId, Long exceptId, Long actorUserId) {
        if (vehicleId == null) return;
        if (exceptId == null) {
            jdbcTemplate.update("""
                    UPDATE vehicle_device_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                    WHERE vehicle_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL
                    """, actorUserId, actorUserId, vehicleId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE vehicle_device_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE vehicle_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL AND id <> ?
                """, actorUserId, actorUserId, vehicleId, exceptId);
    }
    private void closeActiveVehicleDeviceByDeviceExcept(Long deviceId, Long exceptId, Long actorUserId) {
        if (deviceId == null) return;
        if (exceptId == null) {
            jdbcTemplate.update("""
                    UPDATE vehicle_device_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                    WHERE device_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL
                    """, actorUserId, actorUserId, deviceId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE vehicle_device_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE device_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL AND id <> ?
                """, actorUserId, actorUserId, deviceId, exceptId);
    }
    private void closeActiveDriverManualByDriver(Long driverId, Long actorUserId) { closeActiveDriverManualByDriverExcept(driverId, null, actorUserId); }
    private void closeActiveDriverManualByVehicleDevice(Long vehicleId, Long deviceId, Long actorUserId) { closeActiveDriverManualByVehicleDeviceExcept(vehicleId, deviceId, null, actorUserId); }
    private void closeActiveDriverManualByDriverExcept(Long driverId, Long exceptId, Long actorUserId) {
        if (driverId == null) return;
        if (exceptId == null) {
            jdbcTemplate.update("""
                    UPDATE driver_manual_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                    WHERE driver_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL
                    """, actorUserId, actorUserId, driverId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE driver_manual_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE driver_id = ? AND assignment_status = 'ACTIVE' AND deleted_at IS NULL AND id <> ?
                """, actorUserId, actorUserId, driverId, exceptId);
    }
    private void closeActiveDriverManualByVehicleDeviceExcept(Long vehicleId, Long deviceId, Long exceptId, Long actorUserId) {
        if (vehicleId == null || deviceId == null) return;
        if (exceptId == null) {
            jdbcTemplate.update("""
                    UPDATE driver_manual_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                    WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL AND vehicle_id = ? AND device_id = ?
                    """, actorUserId, actorUserId, vehicleId, deviceId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE driver_manual_assignments SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                WHERE assignment_status = 'ACTIVE' AND deleted_at IS NULL AND vehicle_id = ? AND device_id = ? AND id <> ?
                """, actorUserId, actorUserId, vehicleId, deviceId, exceptId);
    }
    private void closeManualDriverByVehicleDeviceAssignment(Long vehicleDeviceAssignmentId, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE driver_manual_assignments d
                SET assignment_status = 'REMOVED', removed_at = NOW(), removed_by = ?, updated_by = ?, updated_at = NOW()
                FROM vehicle_device_assignments vda
                WHERE vda.id = ? AND d.assignment_status = 'ACTIVE' AND d.deleted_at IS NULL
                  AND d.vehicle_id = vda.vehicle_id AND d.device_id = vda.device_id
                """, actorUserId, actorUserId, vehicleDeviceAssignmentId);
    }


    private String assetAssignmentBaseSql() {
        return """
                SELECT a.id, a.company_id, c.company_name,
                       a.vehicle_id, COALESCE(NULLIF(v.vehicle_name, ''), v.vehicle_number, CAST(v.id AS VARCHAR)) AS vehicle_name, v.plate_number,
                       a.device_id, TRIM(CONCAT_WS(' ', NULLIF(db.brand_name, ''), COALESCE(NULLIF(dm.model_name, ''), NULLIF(d.device_model, '')))) AS device_label, d.imei AS device_imei,
                       dma.id AS driver_assignment_id, dma.driver_id, drv.driver_code,
                       COALESCE(drv.driver_name, drv.full_name, drv.driver_code) AS driver_name,
                       drv.license_number, drv.rfid_ibutton,
                       a.assignment_status, a.assigned_at, COALESCE(u.email, '-') AS assigned_by_email
                FROM vehicle_device_assignments a
                JOIN companies c ON c.id = a.company_id
                JOIN vehicles v ON v.id = a.vehicle_id
                JOIN devices d ON d.id = a.device_id
                LEFT JOIN device_brands db ON db.id = d.device_brand_id AND db.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id = d.device_model_id AND dm.deleted_at IS NULL
                LEFT JOIN driver_manual_assignments dma
                       ON dma.vehicle_id = a.vehicle_id
                      AND dma.device_id = a.device_id
                      AND dma.assignment_status = 'ACTIVE'
                      AND dma.deleted_at IS NULL
                LEFT JOIN asset_drivers drv ON drv.id = dma.driver_id AND drv.deleted_at IS NULL
                LEFT JOIN users u ON u.id = a.assigned_by
                WHERE a.deleted_at IS NULL AND a.assignment_status = 'ACTIVE'
                  AND c.deleted_at IS NULL AND v.deleted_at IS NULL AND d.deleted_at IS NULL
                """;
    }

    private org.springframework.jdbc.core.RowMapper<AssetAssignmentRow> assetAssignmentMapper() {
        return (rs, rowNum) -> new AssetAssignmentRow(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"),
                rs.getLong("vehicle_id"), rs.getString("vehicle_name"), rs.getString("plate_number"),
                rs.getLong("device_id"), rs.getString("device_label"), rs.getString("device_imei"),
                rs.getObject("driver_assignment_id", Long.class), rs.getObject("driver_id", Long.class),
                rs.getString("driver_code"), rs.getString("driver_name"), rs.getString("license_number"), rs.getString("rfid_ibutton"),
                rs.getString("assignment_status"), rs.getString("assigned_at"), rs.getString("assigned_by_email")
        );
    }

    private String vehicleDeviceBaseSql() {
        return """
                SELECT a.id, a.company_id, c.company_name,
                       a.vehicle_id, COALESCE(NULLIF(v.vehicle_name, ''), v.vehicle_number, CAST(v.id AS VARCHAR)) AS vehicle_name, v.plate_number,
                       a.device_id, TRIM(CONCAT_WS(' ', NULLIF(db.brand_name, ''), COALESCE(NULLIF(dm.model_name, ''), NULLIF(d.device_model, '')))) AS device_label, d.imei AS device_imei,
                       a.assignment_status, a.assigned_at, COALESCE(u.email, '-') AS assigned_by_email
                FROM vehicle_device_assignments a
                JOIN companies c ON c.id = a.company_id
                JOIN vehicles v ON v.id = a.vehicle_id
                JOIN devices d ON d.id = a.device_id
                LEFT JOIN device_brands db ON db.id = d.device_brand_id AND db.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id = d.device_model_id AND dm.deleted_at IS NULL
                LEFT JOIN users u ON u.id = a.assigned_by
                WHERE a.deleted_at IS NULL AND a.assignment_status = 'ACTIVE'
                  AND c.deleted_at IS NULL AND v.deleted_at IS NULL AND d.deleted_at IS NULL
                """;
    }

    private String driverManualBaseSql() {
        return """
                SELECT a.id, a.company_id, c.company_name,
                       a.vehicle_id, COALESCE(NULLIF(v.vehicle_name, ''), v.vehicle_number, CAST(v.id AS VARCHAR)) AS vehicle_name, v.plate_number,
                       a.device_id, TRIM(CONCAT_WS(' ', NULLIF(db.brand_name, ''), COALESCE(NULLIF(dm.model_name, ''), NULLIF(dev.device_model, '')))) AS device_label, dev.imei AS device_imei,
                       a.driver_id, drv.driver_code, COALESCE(drv.driver_name, drv.full_name, drv.driver_code) AS driver_name,
                       drv.license_number, drv.rfid_ibutton,
                       a.assignment_status, a.assigned_at, COALESCE(u.email, '-') AS assigned_by_email
                FROM driver_manual_assignments a
                JOIN companies c ON c.id = a.company_id
                LEFT JOIN vehicles v ON v.id = a.vehicle_id
                LEFT JOIN devices dev ON dev.id = a.device_id
                LEFT JOIN device_brands db ON db.id = dev.device_brand_id AND db.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id = dev.device_model_id AND dm.deleted_at IS NULL
                JOIN asset_drivers drv ON drv.id = a.driver_id
                LEFT JOIN users u ON u.id = a.assigned_by
                WHERE a.deleted_at IS NULL AND a.assignment_status = 'ACTIVE'
                  AND c.deleted_at IS NULL AND drv.deleted_at IS NULL
                """;
    }

    private org.springframework.jdbc.core.RowMapper<VehicleDeviceAssignmentRow> vehicleDeviceMapper() {
        return (rs, rowNum) -> new VehicleDeviceAssignmentRow(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"), rs.getLong("vehicle_id"), rs.getString("vehicle_name"), rs.getString("plate_number"),
                rs.getLong("device_id"), rs.getString("device_label"), rs.getString("device_imei"), rs.getString("assignment_status"), rs.getString("assigned_at"), rs.getString("assigned_by_email")
        );
    }

    private org.springframework.jdbc.core.RowMapper<DriverManualAssignmentRow> driverManualMapper() {
        return (rs, rowNum) -> new DriverManualAssignmentRow(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"), rs.getObject("vehicle_id", Long.class), rs.getString("vehicle_name"), rs.getString("plate_number"),
                rs.getObject("device_id", Long.class), rs.getString("device_label"), rs.getString("device_imei"), rs.getLong("driver_id"), rs.getString("driver_code"), rs.getString("driver_name"),
                rs.getString("license_number"), rs.getString("rfid_ibutton"), rs.getString("assignment_status"), rs.getString("assigned_at"), rs.getString("assigned_by_email")
        );
    }

    private org.springframework.jdbc.core.RowMapper<AssignmentLookupOption> lookupMapper() {
        return (rs, rowNum) -> new AssignmentLookupOption(rs.getLong("id"), rs.getString("label"), rs.getString("code"), rs.getString("extra"));
    }

    private boolean isGlobalRole(String role) {
        String normalizedRole = normalizeRole(role);
        return "SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole);
    }
    private String normalizeRole(String role) { return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", ""); }
    private String clean(String value) { return value == null ? null : value.trim(); }
}
