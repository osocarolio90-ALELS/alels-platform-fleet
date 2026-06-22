package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceReceiveStatusRepository {

    public static boolean isReceiveAllowed(String imei) {
        if (imei == null || imei.isBlank()) {
            return true;
        }

        String sql = """
                SELECT COALESCE(receive_status, 'ACTIVE') AS receive_status
                FROM device_receive_status
                WHERE imei = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return true;
                }

                return !"SUSPENDED".equalsIgnoreCase(rs.getString("receive_status"));
            }
        } catch (Exception e) {
            System.err.println("[DEVICE RECEIVE STATUS ERROR] " + e.getMessage());
            return true;
        }
    }
}
