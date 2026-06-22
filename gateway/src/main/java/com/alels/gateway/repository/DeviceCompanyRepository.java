package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceCompanyRepository {

    public static Long findCompanyIdByImei(String imei) {

        if (imei == null || imei.isBlank()) {
            return null;
        }

        String sql = """
                SELECT company_id
                FROM devices
                WHERE imei = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    long companyId = rs.getLong("company_id");
                    return rs.wasNull() ? null : companyId;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[DEVICE COMPANY ERROR] "
                            + e.getMessage()
            );
        }

        return null;
    }

    public static Long findDeviceIdByImei(String imei) {

        if (imei == null || imei.isBlank()) {
            return null;
        }

        String sql = """
                SELECT id
                FROM devices
                WHERE imei = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long deviceId = rs.getLong("id");
                    return rs.wasNull() ? null : deviceId;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[DEVICE ID ERROR] "
                            + e.getMessage()
            );
        }

        return null;
    }
}
