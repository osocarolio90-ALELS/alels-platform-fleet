package com.alels.backend.assetregister.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverLookupOption;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRequest;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRow;

@Repository
public class DriverRegisterRepository {
    private final JdbcTemplate jdbcTemplate;

    public DriverRegisterRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<DriverRegisterRow> list(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(baseListSql() + " ORDER BY ad.created_at DESC", rowMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + baseListSql() + " AND ad.company_id IN (SELECT id FROM visible_companies) ORDER BY ad.created_at DESC", rowMapper(), actorCompanyId);
    }

    public List<DriverLookupOption> companyOptions(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, company_name, company_code, status
                    FROM companies
                    WHERE deleted_at IS NULL
                    ORDER BY company_name
                    """, (rs, rowNum) -> new DriverLookupOption(rs.getLong("id"), rs.getString("company_name"), String.valueOf(rs.getLong("id")), rs.getString("status")));
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id, company_name, company_code, status FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id, c.company_name, c.company_code, c.status FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT id, company_name, company_code, status FROM visible_companies ORDER BY company_name
                """, (rs, rowNum) -> new DriverLookupOption(rs.getLong("id"), rs.getString("company_name"), String.valueOf(rs.getLong("id")), rs.getString("status")), actorCompanyId);
    }

    public Long create(DriverRegisterRequest request, Long actorUserId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO asset_drivers(company_id, driver_code, employee_id, driver_name, full_name, license_number, country_code, license_master_id, phone_number, rfid_ibutton, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                request.companyId(), cleanUpper(request.driverId()), cleanUpper(request.employeeId()), cleanUpper(request.driverName()), cleanUpper(request.driverName()), cleanUpper(request.licenseNumber()), normalizeCountry(request.countryCode()),
                request.licenseMasterId(), clean(request.phoneNumber()), cleanUpper(request.rfidIbutton()), normalizeStatus(request.status()), actorUserId, actorUserId);
    }

    public void update(Long id, DriverRegisterRequest request, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE asset_drivers
                SET company_id = ?, driver_code = ?, employee_id = ?, driver_name = ?, full_name = ?, license_number = ?, country_code = ?, license_master_id = ?,
                    phone_number = ?, rfid_ibutton = ?, status = ?, updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, request.companyId(), cleanUpper(request.driverId()), cleanUpper(request.employeeId()), cleanUpper(request.driverName()), cleanUpper(request.driverName()), cleanUpper(request.licenseNumber()), normalizeCountry(request.countryCode()), request.licenseMasterId(), clean(request.phoneNumber()), cleanUpper(request.rfidIbutton()), normalizeStatus(request.status()), actorUserId, id);
    }

    public void softDelete(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE asset_drivers
                SET deleted_at = NOW(), deleted_by = ?, deleted_reason = COALESCE(deleted_reason, 'Deleted from Driver Register'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'), updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public void setStatus(Long id, String status, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE asset_drivers SET status = ?, updated_by = ?, updated_at = NOW() WHERE id = ? AND deleted_at IS NULL
                """, normalizeStatus(status), actorUserId, id);
    }

    public String photoFilename(Long id) {
        List<String> filenames = jdbcTemplate.query("""
                SELECT NULLIF(metadata ->> 'photo_filename', '') FROM asset_drivers
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getString(1), id);
        return filenames.isEmpty() ? null : filenames.get(0);
    }

    public String photoFilenameIncludingDeleted(Long id) {
        List<String> filenames = jdbcTemplate.query("""
                SELECT NULLIF(metadata ->> 'photo_filename', '') FROM asset_drivers
                WHERE id = ?
                """, (rs, rowNum) -> rs.getString(1), id);
        return filenames.isEmpty() ? null : filenames.get(0);
    }

    public void setPhotoFilename(Long id, String fileName, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE asset_drivers
                SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('photo_filename', ?),
                    updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, fileName, actorUserId, id);
    }

    public void removePhotoFilename(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE asset_drivers
                SET metadata = COALESCE(metadata, '{}'::jsonb) - 'photo_filename',
                    updated_by = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, id);
    }

    public Optional<Long> companyIdByDriver(Long id) {
        return jdbcTemplate.query("SELECT company_id FROM asset_drivers WHERE id = ?", (rs, rowNum) -> rs.getObject("company_id", Long.class), id).stream().findFirst();
    }

    public boolean driverIdExists(Long companyId, String driverId, Long excludeId) {
        if (companyId == null || driverId == null || driverId.isBlank()) return false;
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM asset_drivers WHERE company_id = ? AND UPPER(TRIM(driver_code)) = UPPER(TRIM(?)) AND deleted_at IS NULL", Integer.class, companyId, driverId);
        } else {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM asset_drivers WHERE company_id = ? AND UPPER(TRIM(driver_code)) = UPPER(TRIM(?)) AND id <> ? AND deleted_at IS NULL", Integer.class, companyId, driverId, excludeId);
        }
        return count != null && count > 0;
    }

    public boolean licenseNumberExists(Long companyId, String licenseNumber, Long excludeId) {
        if (companyId == null || licenseNumber == null || licenseNumber.isBlank()) return false;
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM asset_drivers WHERE company_id = ? AND UPPER(TRIM(license_number)) = UPPER(TRIM(?)) AND deleted_at IS NULL", Integer.class, companyId, licenseNumber);
        } else {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM asset_drivers WHERE company_id = ? AND UPPER(TRIM(license_number)) = UPPER(TRIM(?)) AND id <> ? AND deleted_at IS NULL", Integer.class, companyId, licenseNumber, excludeId);
        }
        return count != null && count > 0;
    }

    public boolean licenseMasterExists(Long id) {
        if (id == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM license_master WHERE id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
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
                    VALUES (?, ?, 'DRIVER_REGISTER', ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetId, action);
        } catch (DataAccessException ignored) {}
    }

    private String baseListSql() {
        return """
                SELECT ad.id, ad.company_id, c.company_name, ad.driver_code, ad.employee_id, ad.driver_name, ad.license_number,
                       ad.country_code, COALESCE(rc.country_name, ad.country_code) AS country_name, ad.license_master_id,
                       lm.name AS license_type, ad.phone_number, ad.rfid_ibutton,
                       CASE WHEN NULLIF(ad.metadata ->> 'photo_filename', '') IS NULL THEN NULL
                            ELSE '/api/asset-register/drivers/photo/' || (ad.metadata ->> 'photo_filename') END AS photo_url,
                       ad.status, ad.created_at, COALESCE(u.email, '-') AS created_by
                FROM asset_drivers ad
                LEFT JOIN companies c ON c.id = ad.company_id
                LEFT JOIN energy_reference_countries rc ON rc.country_code = ad.country_code
                LEFT JOIN license_master lm ON lm.id = ad.license_master_id
                LEFT JOIN users u ON u.id = ad.created_by
                WHERE ad.deleted_at IS NULL
                """;
    }

    private org.springframework.jdbc.core.RowMapper<DriverRegisterRow> rowMapper() {
        return (rs, rowNum) -> new DriverRegisterRow(
                rs.getLong("id"), rs.getObject("company_id", Long.class), rs.getString("company_name"), rs.getString("driver_code"), rs.getString("employee_id"), rs.getString("driver_name"),
                rs.getString("license_number"), rs.getString("country_code"), rs.getString("country_name"), rs.getObject("license_master_id", Long.class), rs.getString("license_type"),
                rs.getString("phone_number"), rs.getString("rfid_ibutton"), rs.getString("photo_url"), rs.getString("status"), rs.getString("created_at"), rs.getString("created_by")
        );
    }

    private String normalizeStatus(String status) { String s = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(); return "SUSPENDED".equals(s) ? "SUSPENDED" : "ACTIVE"; }
    private String normalizeCountry(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(); }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private String cleanUpper(String value) { String v = clean(value); return v == null ? null : v.toUpperCase(); }
    private String normalizeRole(String role) { return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", ""); }
}
