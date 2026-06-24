package com.alels.backend.serverops.auth.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private static final Logger log = LoggerFactory.getLogger(AuthRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthUserRow> findActiveByEmail(String email) {
        String sql = """
                SELECT u.id, u.company_id, c.company_name, c.status AS company_status, u.username, u.full_name, u.email, u.role, u.status, u.password_hash, u.must_change_password, u.profile_photo_path
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                WHERE LOWER(u.email) = LOWER(?) AND u.deleted_at IS NULL
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuthUserRow(
                rs.getLong("id"),
                (Long) rs.getObject("company_id"),
                rs.getString("company_name"),
                rs.getString("company_status"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("password_hash"),
                rs.getBoolean("must_change_password"),
                rs.getString("profile_photo_path")
        ), email).stream().findFirst();
    }

    public void updatePasswordHash(Long userId, String passwordHash, boolean mustChangePassword) {
        jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, must_change_password = ?, updated_at = NOW()
                WHERE id = ?
                """, passwordHash, mustChangePassword, userId);
    }

    public void updateLastLoginAt(Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbcTemplate.update("""
                    UPDATE users
                    SET last_login_at = ?,
                        first_login_at = COALESCE(first_login_at, ?),
                        updated_at = NOW()
                    WHERE id = ?
                    """, now, now, userId);
        } catch (DataAccessException ex) {
            log.warn("Failed to update extended login timestamp for userId={}, fallback to last_login_at only", userId, ex);
            jdbcTemplate.update("""
                    UPDATE users
                    SET last_login_at = ?,
                        updated_at = NOW()
                    WHERE id = ?
                    """, now, userId);
        }
    }

    public void activateProvisionCompanyOnFirstLogin(Long companyId) {
        if (companyId == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE companies
                    SET first_login_at = COALESCE(first_login_at, NOW()),
                        started_at = COALESCE(started_at, NOW()),
                        expired_at = CASE
                            WHEN expired_at IS NOT NULL THEN expired_at
                            WHEN month_packet IS NULL OR month_packet <= 0 THEN expired_at
                            ELSE NOW() + (month_packet::TEXT || ' months')::INTERVAL
                        END,
                        status = CASE WHEN status = 'PROVISION' THEN 'ACTIVE' ELSE status END,
                        subscription_status = CASE WHEN subscription_status = 'PROVISION' THEN 'ACTIVE' ELSE subscription_status END,
                        updated_at = NOW()
                    WHERE id = ?
                      AND company_code <> 'ALELS_TECH_INDONESIA'
                      AND deleted_at IS NULL
                      AND (first_login_at IS NULL OR status = 'PROVISION' OR subscription_status = 'PROVISION')
                    """, companyId);
        } catch (DataAccessException ex) {
            log.warn("Failed to activate provision company on login for companyId={}. Login will continue.", companyId, ex);
        }
    }

    public void insertLoginEvent(Long userId, Long companyId, String email, boolean success, String ipAddress, String userAgent, String failureReason) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO login_events (user_id, company_id, email, success, ip_address, user_agent, failure_reason)
                    VALUES (?, ?, ?, ?, CAST(NULLIF(?, '') AS inet), ?, ?)
                    """, userId, companyId, email, success, safeIp(ipAddress), userAgent, failureReason);
        } catch (DataAccessException ex) {
            log.warn("Failed to insert login event for email={}. Authentication flow will continue.", email, ex);
        }

        try {
            insertOrganizationActivity(
                    userId,
                    companyId,
                    "USER",
                    userId,
                    success ? "LOGIN_SUCCESS" : "LOGIN_FAILED",
                    "{\"email\":\"" + escapeJson(email) + "\",\"reason\":\"" + escapeJson(failureReason) + "\"}"
            );
        } catch (DataAccessException ex) {
            log.warn("Failed to insert organization login activity for email={}. Authentication flow will continue.", email, ex);
        }
    }

    public void insertLogoutEvent(Long userId, Long companyId, String email, String ipAddress, String userAgent) {
        try {
            insertOrganizationActivity(
                    userId,
                    companyId,
                    "USER",
                    userId,
                    "LOGOUT",
                    "{\"email\":\"" + escapeJson(email) + "\",\"ipAddress\":\"" + escapeJson(safeIp(ipAddress)) + "\",\"userAgent\":\"" + escapeJson(userAgent) + "\"}"
            );
        } catch (DataAccessException ex) {
            log.warn("Failed to insert logout activity for userId={}. Logout flow will continue.", userId, ex);
        }
    }

    public void insertOrganizationActivity(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, actorUserId, actorCompanyId, targetType, targetId, action, detailsJson == null ? "{}" : detailsJson);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String first = ipAddress.split(",")[0].trim();
        return first.length() > 64 ? null : first;
    }

    public record AuthUserRow(
            Long id,
            Long companyId,
            String companyName,
            String companyStatus,
            String username,
            String fullName,
            String email,
            String role,
            String status,
            String passwordHash,
            boolean mustChangePassword,
            String profilePhotoUrl
    ) {}
}
