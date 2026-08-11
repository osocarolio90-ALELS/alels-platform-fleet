package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class DevicePresenceRepository {
    private static final int DEFAULT_PRESENCE_TIMEOUT_SECONDS = 1800;

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
                    presence_status = 'OFFLINE',
                    online = FALSE,
                    status_updated_at = NOW()
                WHERE presence_status = 'ONLINE'
                  AND (last_seen IS NULL
                       OR last_seen < NOW() - (? * interval '1 second'))
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, DEFAULT_PRESENCE_TIMEOUT_SECONDS);
            int updated = stmt.executeUpdate();

            System.out.println("[DB PRESENCE] refreshed devices=" + updated);

            return updated;

        } catch (Exception e) {
            System.err.println("[DB PRESENCE ERROR] refreshPresenceStatus: " + e.getMessage());
            return 0;
        }
    }
}
