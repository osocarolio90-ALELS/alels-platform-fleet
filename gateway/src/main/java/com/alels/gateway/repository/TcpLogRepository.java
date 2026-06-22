package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class TcpLogRepository {

    public static void insert(
            String imei,
            String remoteAddress,
            String channel,
            String protocol,
            String eventType,
            String message,
            Integer bytesIn,
            Integer bytesOut
    ) {
        String sql = """
                INSERT INTO tcp_logs (
                    imei,
                    remote_address,
                    channel,
                    protocol,
                    event_type,
                    message,
                    bytes_in,
                    bytes_out
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.setString(2, remoteAddress);
            stmt.setString(3, channel);
            stmt.setString(4, protocol);
            stmt.setString(5, eventType);
            stmt.setString(6, message);

            if (bytesIn == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, bytesIn);
            }

            if (bytesOut == null) {
                stmt.setNull(8, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(8, bytesOut);
            }

            stmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("[DB TCP LOG ERROR] " + e.getMessage());
        }
    }
}