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
                SELECT COALESCE(d.tcp_enabled, TRUE) AS tcp_enabled,
                       COALESCE(s.receive_status, 'ACTIVE') AS receive_status
                FROM devices d
                LEFT JOIN device_receive_status s ON s.imei = d.imei
                WHERE d.imei = ? AND d.deleted_at IS NULL
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Boolean.parseBoolean(
                            System.getenv().getOrDefault("ALELS_ALLOW_UNKNOWN_DEVICES", "false")
                    );
                }

                return rs.getBoolean("tcp_enabled")
                        && !"SUSPENDED".equalsIgnoreCase(rs.getString("receive_status"));
            }
        } catch (Exception e) {
            System.err.println("[DEVICE RECEIVE STATUS ERROR] " + e.getMessage());
            return false;
        }
    }
}
