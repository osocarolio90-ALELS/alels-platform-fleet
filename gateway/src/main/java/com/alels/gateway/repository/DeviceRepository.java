package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceRepository {

    public static void upsert(
            String imei,
            String deviceModel,
            String activeChannel,
            String lastProtocol
    ) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        String sql = """
                INSERT INTO devices (
                    imei,
                    device_model,
                    active_channel,
                    last_protocol,
                    last_seen
                )
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (imei)
                DO UPDATE SET
                    device_model = COALESCE(EXCLUDED.device_model, devices.device_model),
                    active_channel = EXCLUDED.active_channel,
                    last_protocol = EXCLUDED.last_protocol,
                    last_seen = NOW()
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.setString(2, deviceModel);
            stmt.setString(3, activeChannel);
            stmt.setString(4, lastProtocol);

            stmt.executeUpdate();

            System.out.println("[DB DEVICE] upsert imei=" + imei
                    + " model=" + deviceModel
                    + " channel=" + activeChannel
                    + " protocol=" + lastProtocol);

        } catch (Exception e) {
            System.err.println("[DB DEVICE ERROR] " + e.getMessage());
        }
    }
}