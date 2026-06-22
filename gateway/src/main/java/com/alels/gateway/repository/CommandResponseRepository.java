package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class CommandResponseRepository {

    public static void insertResponse(
            String imei,
            String commandName,
            String status,
            String message,
            String responsePayload
    ) {

        Long companyId =
                DeviceOwnershipRepository.getCompanyIdByImei(imei);

        String sql = """
                INSERT INTO command_responses (
                    company_id,
                    imei,
                    command_name,
                    status,
                    message,
                    response_payload
                )
                VALUES (?, ?, ?, ?, ?, ?)
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
            stmt.setString(4, status);
            stmt.setString(5, message);
            stmt.setString(6, responsePayload);

            stmt.executeUpdate();

            System.out.println(
                    "[DB COMMAND RESPONSE] companyId="
                            + companyId
                            + " imei="
                            + imei
                            + " command="
                            + commandName
            );

        } catch (Exception e) {

            System.err.println(
                    "[DB COMMAND RESPONSE ERROR] "
                            + e.getMessage()
            );
        }
    }
}