package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class CommandRepository {

    public static Long insertCommand(
            String imei,
            String commandName,
            String route,
            String payload,
            String status
    ) {
        if (imei == null || imei.isBlank()) {
            return null;
        }

        Long companyId = DeviceOwnershipRepository.getCompanyIdByImei(imei);

        String sql = """
                INSERT INTO commands (
                    company_id,
                    imei,
                    command_name,
                    route,
                    payload,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
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

            stmt.setString(2, imei);
            stmt.setString(3, commandName);
            stmt.setString(4, route);
            stmt.setString(5, payload);
            stmt.setString(6, status);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");

                    System.out.println("[DB COMMAND] inserted id="
                            + id
                            + " companyId=" + companyId
                            + " imei=" + imei
                            + " command=" + commandName
                            + " status=" + status);

                    return id;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB COMMAND ERROR] insertCommand: " + e.getMessage());
        }

        return null;
    }

    public static void updateStatus(
            Long commandId,
            String status
    ) {
        if (commandId == null) {
            return;
        }

        String sql = """
                UPDATE commands
                SET status = ?
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, status);
            stmt.setLong(2, commandId);

            int updated = stmt.executeUpdate();

            System.out.println("[DB COMMAND] update id="
                    + commandId
                    + " status=" + status
                    + " updated=" + updated);

        } catch (Exception e) {
            System.err.println("[DB COMMAND ERROR] updateStatus: " + e.getMessage());
        }
    }
}