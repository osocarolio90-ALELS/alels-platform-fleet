package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;

public class PasswordResetRepository {

    public static Long createResetToken(
            Long userId,
            String tokenHash,
            Timestamp expiresAt,
            String requestedIp,
            String userAgent
    ) {
        if (userId == null || tokenHash == null || tokenHash.isBlank() || expiresAt == null) {
            return null;
        }

        String sql = """
                INSERT INTO password_reset_tokens (
                    user_id,
                    token_hash,
                    expires_at,
                    requested_ip,
                    user_agent
                )
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, userId);
            stmt.setString(2, tokenHash);
            stmt.setTimestamp(3, expiresAt);
            stmt.setString(4, requestedIp);
            stmt.setString(5, userAgent);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");

                    System.out.println("[DB PASSWORD RESET] token created id="
                            + id
                            + " userId=" + userId);

                    return id;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB PASSWORD RESET ERROR] createResetToken: " + e.getMessage());
        }

        return null;
    }

    public static PasswordResetRecord findValidToken(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    id,
                    user_id,
                    token_hash,
                    expires_at,
                    used_at,
                    created_at
                FROM password_reset_tokens
                WHERE token_hash = ?
                AND used_at IS NULL
                AND expires_at > NOW()
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, tokenHash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PasswordResetRecord record = new PasswordResetRecord();

                    record.id = rs.getLong("id");
                    record.userId = rs.getLong("user_id");
                    record.tokenHash = rs.getString("token_hash");

                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    record.expiresAt = expiresAt != null ? expiresAt.toString() : null;

                    Timestamp usedAt = rs.getTimestamp("used_at");
                    record.usedAt = usedAt != null ? usedAt.toString() : null;

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    record.createdAt = createdAt != null ? createdAt.toString() : null;

                    return record;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB PASSWORD RESET ERROR] findValidToken: " + e.getMessage());
        }

        return null;
    }

    public static void markUsed(Long tokenId) {
        if (tokenId == null) {
            return;
        }

        String sql = """
                UPDATE password_reset_tokens
                SET used_at = NOW()
                WHERE id = ?
                AND used_at IS NULL
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, tokenId);
            stmt.executeUpdate();

            System.out.println("[DB PASSWORD RESET] token used id=" + tokenId);

        } catch (Exception e) {
            System.err.println("[DB PASSWORD RESET ERROR] markUsed: " + e.getMessage());
        }
    }

    public static void updateUserPassword(
            Long userId,
            String newPasswordHash
    ) {
        if (userId == null || newPasswordHash == null || newPasswordHash.isBlank()) {
            return;
        }

        String sql = """
                UPDATE users
                SET
                    password_hash = ?,
                    password_changed_at = NOW(),
                    must_change_password = FALSE
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, newPasswordHash);
            stmt.setLong(2, userId);

            stmt.executeUpdate();

            System.out.println("[DB PASSWORD RESET] password updated userId=" + userId);

        } catch (Exception e) {
            System.err.println("[DB PASSWORD RESET ERROR] updateUserPassword: " + e.getMessage());
        }
    }

    public static class PasswordResetRecord {
        public Long id;
        public Long userId;
        public String tokenHash;
        public String expiresAt;
        public String usedAt;
        public String createdAt;

        @Override
        public String toString() {
            return "PasswordResetRecord{" +
                    "id=" + id +
                    ", userId=" + userId +
                    ", expiresAt='" + expiresAt + '\'' +
                    ", usedAt='" + usedAt + '\'' +
                    ", createdAt='" + createdAt + '\'' +
                    '}';
        }
    }
}