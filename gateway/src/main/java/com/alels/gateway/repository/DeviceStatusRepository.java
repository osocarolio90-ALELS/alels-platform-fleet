package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceStatusRepository {

    public static void markChannelOnline(
            String imei,
            String channel,
            String protocol
    ) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        String sql = """
                UPDATE devices
                SET
                    online = TRUE,
                    active_channel = ?,
                    last_protocol = ?,
                    last_seen = NOW(),
                    status_updated_at = NOW(),
                    gsm_connected = CASE
                        WHEN ? = 'GSM' THEN TRUE
                        ELSE gsm_connected
                    END,
                    wifi_connected = CASE
                        WHEN ? = 'WIFI' THEN TRUE
                        ELSE wifi_connected
                    END
                WHERE imei = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, channel);
            stmt.setString(2, protocol);
            stmt.setString(3, channel);
            stmt.setString(4, channel);
            stmt.setString(5, imei);

            stmt.executeUpdate();

            System.out.println("[DB DEVICE STATUS] CHANNEL ONLINE imei="
                    + imei
                    + " channel=" + channel
                    + " protocol=" + protocol);

        } catch (Exception e) {
            System.err.println("[DB DEVICE STATUS ERROR] markChannelOnline: " + e.getMessage());
        }
    }

    public static void markChannelOffline(
            String imei,
            String channel
    ) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        if (channel == null || channel.isBlank()) {
            return;
        }

        String sql = """
                UPDATE devices
                SET
                    gsm_connected = CASE
                        WHEN ? = 'GSM' THEN FALSE
                        ELSE gsm_connected
                    END,
                    wifi_connected = CASE
                        WHEN ? = 'WIFI' THEN FALSE
                        ELSE wifi_connected
                    END,
                    online = last_seen IS NOT NULL
                        AND last_seen >= NOW() - interval '30 minutes',
                    last_disconnect = NOW(),
                    status_updated_at = NOW()
                WHERE imei = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, channel);
            stmt.setString(2, channel);
            stmt.setString(3, imei);

            stmt.executeUpdate();

            System.out.println("[DB DEVICE STATUS] CHANNEL OFFLINE imei="
                    + imei
                    + " channel=" + channel);

        } catch (Exception e) {
            System.err.println("[DB DEVICE STATUS ERROR] markChannelOffline: " + e.getMessage());
        }
    }
}
