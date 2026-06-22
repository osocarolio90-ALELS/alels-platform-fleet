package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.security.CompanyAccessPolicy;

public class UserRepository {

    public static Long createUser(
            Long companyId,
            String username,
            String passwordHash,
            String fullName,
            String email,
            String role
    ) {
        if (username == null || username.isBlank()) {
            return null;
        }

        if (role == null || role.isBlank()) {
            return null;
        }

        if (!CompanyAccessPolicy.isAlelsSuperAdmin(role)
                && companyId == null) {
            System.err.println("[DB USER] company_id required for role=" + role);
            return null;
        }

        if (companyId != null && isUserLimitReached(companyId)) {
            System.err.println("[DB USER] user limit reached companyId=" + companyId);
            return null;
        }

        String sql = """
                INSERT INTO users (
                    company_id,
                    username,
                    password_hash,
                    full_name,
                    email,
                    role,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                RETURNING id
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            if (companyId == null) {
                stmt.setNull(1, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(1, companyId);
            }

            stmt.setString(2, username);
            stmt.setString(3, passwordHash);
            stmt.setString(4, fullName);
            stmt.setString(5, email);
            stmt.setString(6, role);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");

                    System.out.println("[DB USER] created id="
                            + id
                            + " username=" + username
                            + " role=" + role
                            + " companyId=" + companyId);

                    return id;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB USER ERROR] createUser: " + e.getMessage());
        }

        return null;
    }

    public static boolean isUserLimitReached(Long companyId) {
        if (companyId == null) {
            return false;
        }

        String sql = """
                SELECT
                    c.max_users,
                    COUNT(u.id) AS active_users
                FROM companies c
                LEFT JOIN users u
                       ON u.company_id = c.id
                      AND u.status = 'ACTIVE'
                WHERE c.id = ?
                GROUP BY c.max_users
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, companyId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int maxUsers = rs.getInt("max_users");
                    int activeUsers = rs.getInt("active_users");

                    return activeUsers >= maxUsers;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB USER ERROR] isUserLimitReached: " + e.getMessage());
        }

        return true;
    }

    public static int countActiveUsers(Long companyId) {
        if (companyId == null) {
            return 0;
        }

        String sql = """
                SELECT COUNT(*) AS total
                FROM users
                WHERE company_id = ?
                AND status = 'ACTIVE'
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, companyId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }

        } catch (Exception e) {
            System.err.println("[DB USER ERROR] countActiveUsers: " + e.getMessage());
        }

        return 0;
    }
}