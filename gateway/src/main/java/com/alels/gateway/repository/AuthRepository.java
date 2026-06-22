package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;

public class AuthRepository {

    public static UserAuthRecord findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    id,
                    company_id,
                    username,
                    email,
                    password_hash,
                    full_name,
                    role,
                    status,
                    must_change_password,
                    last_login_at,
                    password_changed_at
                FROM users
                WHERE email = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, email.trim().toLowerCase());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserAuthRecord user = new UserAuthRecord();

                    user.id = rs.getLong("id");

                    long companyId = rs.getLong("company_id");
                    user.companyId = rs.wasNull() ? null : companyId;

                    user.username = rs.getString("username");
                    user.email = rs.getString("email");
                    user.passwordHash = rs.getString("password_hash");
                    user.fullName = rs.getString("full_name");
                    user.role = rs.getString("role");
                    user.status = rs.getString("status");
                    user.mustChangePassword = rs.getBoolean("must_change_password");

                    Timestamp lastLogin = rs.getTimestamp("last_login_at");
                    user.lastLoginAt = lastLogin != null ? lastLogin.toString() : null;

                    Timestamp passwordChanged = rs.getTimestamp("password_changed_at");
                    user.passwordChangedAt = passwordChanged != null ? passwordChanged.toString() : null;

                    return user;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB AUTH ERROR] findByEmail: " + e.getMessage());
        }

        return null;
    }

    public static boolean existsByEmail(String email) {
        return findByEmail(email) != null;
    }

    public static void updateLastLogin(Long userId) {
        if (userId == null) {
            return;
        }

        String sql = """
                UPDATE users
                SET last_login_at = NOW()
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();

            System.out.println("[DB AUTH] last_login_at updated userId=" + userId);

        } catch (Exception e) {
            System.err.println("[DB AUTH ERROR] updateLastLogin: " + e.getMessage());
        }
    }

    public static class UserAuthRecord {
        public Long id;
        public Long companyId;

        public String username;
        public String email;
        public String passwordHash;
        public String fullName;
        public String role;
        public String status;

        public boolean mustChangePassword;

        public String lastLoginAt;
        public String passwordChangedAt;

        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status);
        }

        @Override
        public String toString() {
            return "UserAuthRecord{" +
                    "id=" + id +
                    ", companyId=" + companyId +
                    ", username='" + username + '\'' +
                    ", email='" + email + '\'' +
                    ", fullName='" + fullName + '\'' +
                    ", role='" + role + '\'' +
                    ", status='" + status + '\'' +
                    ", mustChangePassword=" + mustChangePassword +
                    '}';
        }
    }
}