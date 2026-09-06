package com.alels.backend.user.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.alels.backend.user.dto.UserProfileDtos.UserProfileResponse;

@Repository
public class UserProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfileResponse> findProfile(Long userId) {
        return jdbcTemplate.query("""
                SELECT u.id, u.company_id, c.company_name, u.username, u.full_name, u.email, u.role, u.status, u.created_at, u.profile_photo_path
                FROM users u
                LEFT JOIN companies c ON c.id = u.company_id
                WHERE u.id = ? AND u.deleted_at IS NULL
                LIMIT 1
                """, profileMapper(), userId).stream().findFirst();
    }

    public Optional<String> findPreviousPhotoPath(Long userId, String currentPhotoPath) {
        return jdbcTemplate.query("""
                SELECT profile_photo_path
                FROM users
                WHERE id = ? AND deleted_at IS NULL AND profile_photo_path IS NOT NULL AND profile_photo_path <> ?
                """, (rs, rowNum) -> rs.getString("profile_photo_path"), userId, currentPhotoPath).stream().findFirst();
    }

    public void updateProfile(Long userId, String username, String fullName, String email, String profilePhotoPath, String contentType) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (username != null) {
            sets.add("username = ?");
            params.add(username);
        }
        if (fullName != null) {
            sets.add("full_name = ?");
            params.add(fullName);
        }
        if (email != null) {
            sets.add("email = LOWER(?)");
            params.add(email);
        }
        if (profilePhotoPath != null) {
            sets.add("profile_photo_path = ?");
            params.add(profilePhotoPath);
        }
        if (contentType != null) {
            sets.add("profile_photo_content_type = ?");
            params.add(contentType);
        }
        if (sets.isEmpty()) return;

        sets.add("updated_at = NOW()");
        params.add(userId);
        jdbcTemplate.update("UPDATE users SET " + String.join(", ", sets) + " WHERE id = ? AND deleted_at IS NULL", params.toArray());
    }

    public void updatePassword(Long userId, String passwordHash) {
        jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, session_version = session_version + 1, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, passwordHash, userId);
    }

    public boolean usernameExists(String username, Long excludeUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE deleted_at IS NULL AND LOWER(username) = LOWER(?) AND id <> ?
                """, Integer.class, username, excludeUserId);
        return count != null && count > 0;
    }

    public boolean emailExists(String email, Long excludeUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE deleted_at IS NULL AND LOWER(email) = LOWER(?) AND id <> ?
                """, Integer.class, email, excludeUserId);
        return count != null && count > 0;
    }

    public void logBestEffort(Long actorUserId, Long actorCompanyId, String action, String detailsJson) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs (actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, 'USER', ?, ?, CAST(? AS jsonb))
                    """, actorUserId, actorCompanyId, actorUserId, action, detailsJson == null ? "{}" : detailsJson);
        } catch (RuntimeException ignored) {
            // Profile update must not fail only because audit logging table is temporarily different during migration hardening.
        }
    }

    @NonNull
    private RowMapper<UserProfileResponse> profileMapper() {
        return (rs, rowNum) -> new UserProfileResponse(
                rs.getLong("id"),
                nullableLong(rs, "company_id"),
                rs.getString("company_name"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                str(rs, "created_at"),
                rs.getString("profile_photo_path")
        );
    }

    private String str(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
