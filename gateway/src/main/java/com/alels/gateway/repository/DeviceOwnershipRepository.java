package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceOwnershipRepository {

    public static boolean assignDeviceToCompany(
            String imei,
            Long companyId
    ) {
        if (imei == null || imei.isBlank() || companyId == null) {
            return false;
        }

        String sql = """
                UPDATE devices
                SET company_id = ?
                WHERE imei = ?
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, companyId);
            stmt.setString(2, imei);

            int updated = stmt.executeUpdate();

            System.out.println("[DB OWNERSHIP] assign imei="
                    + imei
                    + " companyId=" + companyId
                    + " updated=" + updated);

            return updated > 0;

        } catch (Exception e) {
            System.err.println("[DB OWNERSHIP ERROR] assignDeviceToCompany: " + e.getMessage());
        }

        return false;
    }

    public static boolean isDeviceOwnedByCompany(
            String imei,
            Long companyId
    ) {
        if (imei == null || imei.isBlank() || companyId == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM devices
                WHERE imei = ?
                AND company_id = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.setLong(2, companyId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println("[DB OWNERSHIP ERROR] isDeviceOwnedByCompany: " + e.getMessage());
        }

        return false;
    }

    public static Long getCompanyIdByImei(String imei) {
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
                    long value = rs.getLong("company_id");
                    return rs.wasNull() ? null : value;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB OWNERSHIP ERROR] getCompanyIdByImei: " + e.getMessage());
        }

        return null;
    }
}