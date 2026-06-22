package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class CommandQueueRepository {

    public static void updateStatus(
            Long queueId,
            String status
    ) {
        if (queueId == null || status == null || status.isBlank()) {
            return;
        }

        String sql = """
                UPDATE command_queue
                SET
                    status = ?,
                    sent_at = CASE WHEN ? = 'SENT' THEN COALESCE(sent_at, NOW()) ELSE sent_at END,
                    acked_at = CASE WHEN ? = 'ACKED' THEN COALESCE(acked_at, NOW()) ELSE acked_at END,
                    failed_at = CASE WHEN ? = 'FAILED' THEN COALESCE(failed_at, NOW()) ELSE failed_at END,
                    updated_at = NOW()
                WHERE id = ?
                RETURNING legacy_command_id
                """;

        Long legacyCommandId = null;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, status);
            stmt.setString(2, status);
            stmt.setString(3, status);
            stmt.setString(4, status);
            stmt.setLong(5, queueId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong("legacy_command_id");
                    legacyCommandId = rs.wasNull() ? null : value;
                }
            }

            System.out.println(
                    "[DB COMMAND QUEUE] update id="
                            + queueId
                            + " status="
                            + status
                            + " legacyCommandId="
                            + legacyCommandId
            );

        } catch (Exception e) {
            System.err.println("[DB COMMAND QUEUE ERROR] updateStatus: " + e.getMessage());
            return;
        }

        if (legacyCommandId != null) {
            CommandRepository.updateStatus(legacyCommandId, status);
        }
    }

    public static void markLatestAcked(
            String imei,
            String commandName
    ) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        String sql = """
                WITH latest AS (
                    SELECT id, legacy_command_id
                    FROM command_queue
                    WHERE imei = ?
                    AND status = 'SENT'
                    AND (? IS NULL OR command_name = ?)
                    ORDER BY sent_at DESC NULLS LAST, created_at DESC
                    LIMIT 1
                )
                UPDATE command_queue q
                SET
                    status = 'ACKED',
                    acked_at = NOW(),
                    updated_at = NOW()
                FROM latest
                WHERE q.id = latest.id
                RETURNING latest.legacy_command_id
                """;

        Long legacyCommandId = null;
        String commandFilter =
                commandName != null && !commandName.isBlank()
                        ? commandName
                        : null;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.setString(2, commandFilter);
            stmt.setString(3, commandFilter);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong("legacy_command_id");
                    legacyCommandId = rs.wasNull() ? null : value;
                }
            }

            System.out.println(
                    "[DB COMMAND QUEUE] ack imei="
                            + imei
                            + " command="
                            + commandName
                            + " legacyCommandId="
                            + legacyCommandId
            );

        } catch (Exception e) {
            System.err.println("[DB COMMAND QUEUE ERROR] markLatestAcked: " + e.getMessage());
            return;
        }

        if (legacyCommandId != null) {
            CommandRepository.updateStatus(legacyCommandId, "ACKED");
        }
    }
}
