package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class DevicePresenceRepository {

    public static void markPresentOnData(String imei) {
        if (imei == null || imei.isBlank()) {
            return;
        }

        String sql = """
                UPDATE devices
                SET
                    presence_status = 'ONLINE',
                    last_seen = NOW(),
                    status_updated_at = NOW()
                WHERE imei = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.executeUpdate();

            System.out.println("[DB PRESENCE] ONLINE imei=" + imei);

        } catch (Exception e) {
            System.err.println("[DB PRESENCE ERROR] markPresentOnData: " + e.getMessage());
        }
    }

    public static int refreshPresenceStatus() {
        String sql = """
                UPDATE devices
                SET
                    presence_status = CASE
                        WHEN last_seen IS NOT NULL
                             AND EXTRACT(EPOCH FROM (NOW() - last_seen)) <= presence_timeout_seconds
                        THEN 'ONLINE'
                        ELSE 'OFFLINE'
                    END,
                    status_updated_at = NOW()
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            int updated = stmt.executeUpdate();

            System.out.println("[DB PRESENCE] refreshed devices=" + updated);

            return updated;

        } catch (Exception e) {
            System.err.println("[DB PRESENCE ERROR] refreshPresenceStatus: " + e.getMessage());
            return 0;
        }
    }
}