package com.alels.backend.organization.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.alels.backend.organization.dto.OrganizationDtos.CompanyRow;
import com.alels.backend.organization.dto.OrganizationDtos.OptionRow;
import com.alels.backend.organization.dto.OrganizationDtos.UserRow;
import com.alels.backend.organization.dto.OrganizationDtos.WastedRow;
import com.alels.backend.serverops.shared.security.RoleNormalizer;

@Repository
public class OrganizationRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CompanyRow> findCompanies(Long userId, Long companyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("SELECT * FROM v_organization_companies WHERE deleted_at IS NULL ORDER BY id", companyMapper());
        }
        if ("ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("SELECT * FROM v_organization_companies WHERE deleted_at IS NULL AND company_code <> 'ALELS_TECH_INDONESIA' ORDER BY id", companyMapper());
        }
        if ("OWNER".equals(normalizedRole) || "MANAGER".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                        UNION ALL
                        SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.deleted_at IS NULL
                    )
                    SELECT v.* FROM v_organization_companies v JOIN tree t ON t.id = v.id
                    WHERE v.deleted_at IS NULL AND v.company_code <> 'ALELS_TECH_INDONESIA'
                    ORDER BY v.id
                    """, companyMapper(), companyId);
        }
        return List.of();
    }

    public List<UserRow> findUsers(Long companyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("SELECT * FROM v_organization_users WHERE deleted_at IS NULL ORDER BY id", userMapper());
        }
        if ("ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("SELECT * FROM v_organization_users WHERE deleted_at IS NULL AND role_normalized <> 'SUPERADMIN' ORDER BY id", userMapper());
        }
        if ("OWNER".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                        UNION ALL
                        SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.deleted_at IS NULL
                    )
                    SELECT v.* FROM v_organization_users v JOIN tree t ON t.id = v.company_id
                    WHERE v.deleted_at IS NULL AND v.role_normalized NOT IN ('SUPERADMIN', 'ADMIN')
                    ORDER BY v.id
                    """, userMapper(), companyId);
        }
        if ("MANAGER".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                        UNION ALL
                        SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.deleted_at IS NULL
                    )
                    SELECT v.* FROM v_organization_users v JOIN tree t ON t.id = v.company_id
                    WHERE v.deleted_at IS NULL AND v.role_normalized IN ('TECHUSER', 'CLIENTUSER')
                    ORDER BY v.id
                    """, userMapper(), companyId);
        }
        return List.of();
    }

    public List<WastedRow> findWasted(Long companyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("SELECT * FROM v_organization_wasted ORDER BY deleted_at DESC", wastedMapper());
        }
        if ("ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT w.*
                    FROM v_organization_wasted w
                    WHERE NOT (w.item_type = 'COMPANY' AND UPPER(COALESCE(w.name, '')) = 'ALELS TECH INDONESIA')
                      AND NOT (w.item_type = 'USER' AND UPPER(COALESCE(w.role_or_type, '')) = 'SUPERADMIN')
                    ORDER BY w.deleted_at DESC
                    """, wastedMapper());
        }
        if ("OWNER".equals(normalizedRole) || "MANAGER".equals(normalizedRole)) {
            String roleFilter = "MANAGER".equals(normalizedRole)
                    ? "AND (w.item_type = 'COMPANY' OR UPPER(COALESCE(w.role_or_type, '')) IN ('TECHUSER', 'TECH_USER', 'CLIENTUSER', 'CLIENT_USER'))"
                    : "AND NOT (w.item_type = 'USER' AND UPPER(COALESCE(w.role_or_type, '')) IN ('SUPERADMIN', 'ADMIN'))";
            return jdbcTemplate.query("""
                    WITH RECURSIVE tree AS (
                        SELECT id FROM companies WHERE id = ?
                        UNION ALL
                        SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id
                    )
                    SELECT w.* FROM v_organization_wasted w
                    LEFT JOIN companies c ON c.id = w.id AND w.item_type = 'COMPANY'
                    LEFT JOIN users u ON u.id = w.id AND w.item_type = 'USER'
                    WHERE COALESCE(c.id, u.company_id) IN (SELECT id FROM tree)
                    """ + roleFilter + """
                    ORDER BY w.deleted_at DESC
                    """, wastedMapper(), companyId);
        }
        return List.of();
    }

    public List<OptionRow> parentOptions(Long actorCompanyId, String role) {
        return companyOptions(actorCompanyId, role);
    }

    public List<OptionRow> companyOptions(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, UPPER(company_name) AS label
                    FROM companies
                    WHERE deleted_at IS NULL
                    ORDER BY company_name
                    """, (rs, rowNum) -> new OptionRow(rs.getLong("id"), rs.getString("label")));
        }
        if ("ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, UPPER(company_name) AS label
                    FROM companies
                    WHERE deleted_at IS NULL AND company_code <> 'ALELS_TECH_INDONESIA'
                    ORDER BY company_name
                    """, (rs, rowNum) -> new OptionRow(rs.getLong("id"), rs.getString("label")));
        }
        if ("OWNER".equals(normalizedRole) || "MANAGER".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    WITH RECURSIVE tree AS (
                        SELECT id, company_name FROM companies WHERE id = ? AND deleted_at IS NULL
                        UNION ALL
                        SELECT c.id, c.company_name FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.deleted_at IS NULL
                    )
                    SELECT id, UPPER(company_name) AS label FROM tree ORDER BY company_name
                    """, (rs, rowNum) -> new OptionRow(rs.getLong("id"), rs.getString("label")), actorCompanyId);
        }
        return List.of();
    }

    public boolean companyNameExists(String companyName, Long excludeId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM companies
                WHERE deleted_at IS NULL
                  AND UPPER(company_name) = UPPER(?)
                  AND (CAST(? AS BIGINT) IS NULL OR id <> CAST(? AS BIGINT))
                """, Integer.class, companyName, excludeId, excludeId);
        return count != null && count > 0;
    }


    public boolean userEmailExists(String email, Long excludeUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE deleted_at IS NULL
                  AND LOWER(email) = LOWER(?)
                  AND (CAST(? AS BIGINT) IS NULL OR id <> CAST(? AS BIGINT))
                """, Integer.class, email, excludeUserId, excludeUserId);
        return count != null && count > 0;
    }

    public Optional<String> roleForUser(Long userId) {
        return jdbcTemplate.query("SELECT role FROM users WHERE id = ?", (rs, rowNum) -> rs.getString("role"), userId)
                .stream()
                .findFirst();
    }

    public Optional<Long> rootCompanyId() {
        return jdbcTemplate.query("SELECT id FROM companies WHERE company_code = 'ALELS_TECH_INDONESIA' LIMIT 1", (rs, rowNum) -> rs.getLong("id")).stream().findFirst();
    }

    public boolean isRootCompany(Long companyId) {
        if (companyId == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies WHERE id = ? AND company_code = 'ALELS_TECH_INDONESIA'", Integer.class, companyId);
        return count != null && count > 0;
    }

    public boolean isRootSuperadmin(Long userId) {
        if (userId == null) return false;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                WHERE u.id = ?
                  AND UPPER(u.role) = 'SUPERADMIN'
                  AND COALESCE(c.company_code, 'ALELS_TECH_INDONESIA') = 'ALELS_TECH_INDONESIA'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public Long createCompany(Long parentCompanyId, String companyName, String companyType, String plan, Integer monthPacket, Long storageQuotaMb, String country, Long createdBy) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO companies (parent_company_id, company_name, company_code, company_type, plan, month_packet, storage_quota_mb, country, status, subscription_status, is_internal, created_by, created_at, updated_at)
                VALUES (?, UPPER(TRIM(?)), UPPER(REGEXP_REPLACE(TRIM(?), '[^A-Za-z0-9]+', '_', 'g')), UPPER(?), UPPER(?), ?, ?, ?, 'PROVISION', 'PROVISION', FALSE, ?, NOW(), NOW())
                RETURNING id
                """, Long.class, parentCompanyId, companyName, companyName, companyType, plan, monthPacket, storageQuotaMb, country, createdBy);
    }

    public Long createUser(Long companyId, String username, String fullName, String email, String role, String passwordHash, Long createdBy) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (company_id, username, full_name, email, role, status, password_hash, must_change_password, created_by, created_at, updated_at)
                VALUES (?, ?, ?, LOWER(?), ?, 'ACTIVE', ?, TRUE, ?, NOW(), NOW())
                RETURNING id
                """, Long.class, companyId, username, fullName, email, role, passwordHash, createdBy);
    }

    public void updateCompany(Long id, String companyName, Long parentCompanyId) {
        jdbcTemplate.update("""
                UPDATE companies
                SET company_name = UPPER(TRIM(?)), parent_company_id = ?, updated_at = NOW()
                WHERE id = ? AND company_code <> 'ALELS_TECH_INDONESIA'
                """, companyName, parentCompanyId, id);
    }

    public boolean isParentCycle(Long companyId, Long parentCompanyId) {
        if (parentCompanyId == null) return false;
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE descendants AS (
                    SELECT id FROM companies WHERE id = ?
                    UNION ALL
                    SELECT c.id FROM companies c JOIN descendants d ON c.parent_company_id = d.id
                )
                SELECT COUNT(*) FROM descendants WHERE id = ?
                """, Integer.class, companyId, parentCompanyId);
        return count != null && count > 0;
    }

    public void updateUser(Long id, String username, String fullName, String email, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            jdbcTemplate.update("UPDATE users SET username = ?, full_name = ?, email = LOWER(?), updated_at = NOW() WHERE id = ?", username, fullName, email, id);
        } else {
            jdbcTemplate.update("UPDATE users SET username = ?, full_name = ?, email = LOWER(?), password_hash = ?, must_change_password = TRUE, session_version = session_version + 1, updated_at = NOW() WHERE id = ?", username, fullName, email, passwordHash, id);
        }
    }

    public void softDeleteCompany(Long id, Long actorUserId, String reason) {
        jdbcTemplate.update("""
                UPDATE companies SET status = 'DELETED', deleted_at = NOW(), deleted_by = ?, delete_permanent_at = NOW() + INTERVAL '30 days', deleted_reason = ?, updated_at = NOW()
                WHERE id = ? AND company_code <> 'ALELS_TECH_INDONESIA'
                """, actorUserId, reason, id);
    }

    public void softDeleteUser(Long id, Long actorUserId, String reason) {
        jdbcTemplate.update("UPDATE users SET status = 'DELETED', deleted_at = NOW(), deleted_by = ?, delete_permanent_at = NOW() + INTERVAL '30 days', deleted_reason = ?, updated_at = NOW() WHERE id = ?", actorUserId, reason, id);
    }

    public void suspendCompany(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM companies WHERE id = ? AND company_code <> 'ALELS_TECH_INDONESIA'
                    UNION ALL
                    SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id WHERE c.company_code <> 'ALELS_TECH_INDONESIA'
                ), updated_companies AS (
                    UPDATE companies SET status = 'SUSPENDED', subscription_status = CASE WHEN subscription_status = 'ACTIVE' THEN 'SUSPENDED' ELSE subscription_status END, suspended_at = NOW(), suspended_by = ?, updated_at = NOW()
                    WHERE id IN (SELECT id FROM tree)
                    RETURNING id
                )
                UPDATE users SET status = 'SUSPENDED', suspended_at = NOW(), suspended_by = ?, session_version = session_version + 1, updated_at = NOW()
                WHERE company_id IN (SELECT id FROM updated_companies) AND deleted_at IS NULL
                """, id, actorUserId, actorUserId);
    }

    public void activateCompany(Long id) {
        jdbcTemplate.update("UPDATE companies SET status = 'ACTIVE', suspended_at = NULL, suspended_by = NULL, deleted_at = NULL, deleted_by = NULL, delete_permanent_at = NULL, deleted_reason = NULL, updated_at = NOW() WHERE id = ?", id);
    }

    public void suspendUser(Long id, Long actorUserId) {
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED', suspended_at = NOW(), suspended_by = ?, session_version = session_version + 1, updated_at = NOW() WHERE id = ?", actorUserId, id);
    }

    public void activateUser(Long id) {
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE', suspended_at = NULL, suspended_by = NULL, deleted_at = NULL, deleted_by = NULL, delete_permanent_at = NULL, deleted_reason = NULL, updated_at = NOW() WHERE id = ?", id);
    }

    public void permanentDeleteCompany(Long id) {
        jdbcTemplate.update("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM companies WHERE id = ? AND company_code <> 'ALELS_TECH_INDONESIA'
                    UNION ALL
                    SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id
                ), deleted_users AS (
                    DELETE FROM users WHERE company_id IN (SELECT id FROM tree) RETURNING id
                )
                DELETE FROM companies WHERE id IN (SELECT id FROM tree)
                """, id);
    }

    public void permanentDeleteUser(Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }

    public boolean canAccessCompany(Long companyId, Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) return true;
        if (!"OWNER".equals(normalizedRole) && !"MANAGER".equals(normalizedRole)) return false;
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE tree AS (
                    SELECT id FROM companies WHERE id = ?
                    UNION ALL
                    SELECT c.id FROM companies c JOIN tree t ON c.parent_company_id = t.id
                ) SELECT COUNT(*) FROM tree WHERE id = ?
                """, Integer.class, actorCompanyId, companyId);
        return count != null && count > 0;
    }

    public Optional<Long> companyIdForUser(Long userId) {
        return jdbcTemplate.query("SELECT company_id FROM users WHERE id = ?", (rs, rowNum) -> rs.getLong("company_id"), userId).stream().findFirst();
    }

    public long purgeExpiredWasted() {
        Long count = jdbcTemplate.queryForObject("SELECT COALESCE(deleted_companies, 0) + COALESCE(deleted_users, 0) FROM alels_purge_organization_wasted()", Long.class);
        return count == null ? 0L : count;
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, actorUserId, actorCompanyId, targetType, targetId, action, detailsJson == null ? "{}" : detailsJson);
    }

    private RowMapper<CompanyRow> companyMapper() {
        return (rs, rowNum) -> new CompanyRow(
                rs.getLong("id"), nullableLong(rs, "parent_company_id"), rs.getString("parent_company_name"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("company_type"), rs.getString("company_type"), rs.getString("plan"), nullableInt(rs, "month_packet"), nullableLong(rs, "storage_quota_mb"), nullableLong(rs, "storage_quota_mb"), nullableLong(rs, "storage_used_mb"), "MB", rs.getString("country"), rs.getString("status"), rs.getString("subscription_status"), rs.getBoolean("is_internal"), str(rs, "first_login_at"), str(rs, "started_at"), str(rs, "expired_at"), str(rs, "created_at"), nullableLong(rs, "created_by"), rs.getString("created_by_name"), rs.getString("created_by_email"), str(rs, "updated_at"), str(rs, "deleted_at"), str(rs, "delete_permanent_at"), nullableInt(rs, "remaining_days"), rs.getString("deleted_reason"), nullableLong(rs, "active_user_count"));
    }

    private RowMapper<UserRow> userMapper() {
        return (rs, rowNum) -> new UserRow(nullableLong(rs, "id"), nullableLong(rs, "company_id"), rs.getString("company_name"), rs.getString("parent_company_name"), rs.getString("username"), rs.getString("full_name"), rs.getString("email"), rs.getString("role"), rs.getString("status"), str(rs, "first_login_at"), str(rs, "last_login_at"), str(rs, "created_at"), nullableLong(rs, "created_by"), rs.getString("created_by_name"), rs.getString("created_by_email"), str(rs, "updated_at"), str(rs, "deleted_at"), str(rs, "delete_permanent_at"), nullableInt(rs, "remaining_days"), rs.getString("deleted_reason"), rs.getString("profile_photo_path"));
    }

    private RowMapper<WastedRow> wastedMapper() {
        return (rs, rowNum) -> new WastedRow(rs.getString("item_type"), nullableLong(rs, "id"), rs.getString("name"), rs.getString("company_name"), rs.getString("role_or_type"), str(rs, "deleted_at"), str(rs, "delete_permanent_at"), nullableInt(rs, "remaining_days"), rs.getString("deleted_reason"), nullableLong(rs, "deleted_by"), rs.getString("deleted_by_email"));
    }

    private String str(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static String normalizeRole(String role) {
        return RoleNormalizer.normalize(role);
    }
}
