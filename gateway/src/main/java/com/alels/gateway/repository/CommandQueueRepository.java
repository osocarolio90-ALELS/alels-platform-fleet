package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.poller.PendingCommandDto;

public class CommandQueueRepository {

    public static List<PendingCommandDto> findDispatchCandidates(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 1_000));
        String sql = """
                SELECT id, company_id, imei, command_name, route, payload, status, created_at
                FROM command_queue
                WHERE status IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= NOW()
                  AND (expires_at IS NULL OR expires_at > NOW())
                  AND (lease_until IS NULL OR lease_until < NOW())
                ORDER BY priority ASC, created_at ASC
                LIMIT ?
                """;
        List<PendingCommandDto> commands = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    PendingCommandDto command = new PendingCommandDto();
                    command.id = result.getLong("id");
                    long companyId = result.getLong("company_id");
                    command.companyId = result.wasNull() ? null : companyId;
                    command.imei = result.getString("imei");
                    command.commandName = result.getString("command_name");
                    command.route = result.getString("route");
                    command.payload = result.getString("payload");
                    command.status = result.getString("status");
                    command.createdAt = String.valueOf(result.getObject("created_at"));
                    commands.add(command);
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read command dispatch candidates", error);
        }
        return commands;
    }

    public static boolean tryLease(Long queueId, String owner, int leaseSeconds) {
        if (queueId == null || owner == null || owner.isBlank()) return false;
        String sql = """
                UPDATE command_queue
                SET status = 'SENDING', lease_owner = ?,
                    lease_until = NOW() + (? * interval '1 second'),
                    attempt_count = attempt_count + 1, updated_at = NOW()
                WHERE id = ?
                  AND status IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= NOW()
                  AND (expires_at IS NULL OR expires_at > NOW())
                  AND (lease_until IS NULL OR lease_until < NOW())
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner);
            statement.setInt(2, Math.max(leaseSeconds, 5));
            statement.setLong(3, queueId);
            return statement.executeUpdate() == 1;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to lease command id=" + queueId, error);
        }
    }

    public static boolean completeLease(Long queueId, String owner, boolean sent) {
        String sql = """
                UPDATE command_queue
                SET status = CASE
                        WHEN ? THEN 'SENT'
                        WHEN attempt_count >= max_attempts THEN 'FAILED'
                        ELSE 'RETRY'
                    END,
                    sent_at = CASE WHEN ? THEN NOW() ELSE sent_at END,
                    failed_at = CASE WHEN NOT ? AND attempt_count >= max_attempts THEN NOW() ELSE failed_at END,
                    next_attempt_at = CASE WHEN ? THEN next_attempt_at ELSE NOW() + interval '10 seconds' END,
                    lease_owner = NULL, lease_until = NULL, updated_at = NOW()
                WHERE id = ? AND status = 'SENDING' AND lease_owner = ?
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, sent);
            statement.setBoolean(2, sent);
            statement.setBoolean(3, sent);
            statement.setBoolean(4, sent);
            statement.setLong(5, queueId);
            statement.setString(6, owner);
            return statement.executeUpdate() == 1;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to complete command lease id=" + queueId, error);
        }
    }

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
