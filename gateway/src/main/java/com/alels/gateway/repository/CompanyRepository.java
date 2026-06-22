package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class CompanyRepository {

    public static Long createCompany(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return null;
        }

        String sql = """
                INSERT INTO companies (
                    company_name,
                    status
                )
                VALUES (?, 'ACTIVE')
                RETURNING id
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, companyName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("id");

                    System.out.println("[DB COMPANY] created id="
                            + id
                            + " name=" + companyName);

                    return id;
                }
            }

        } catch (Exception e) {
            System.err.println("[DB COMPANY ERROR] createCompany: " + e.getMessage());
        }

        return null;
    }

    public static boolean exists(Long companyId) {
        if (companyId == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM companies
                WHERE id = ?
                AND status = 'ACTIVE'
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, companyId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println("[DB COMPANY ERROR] exists: " + e.getMessage());
        }

        return false;
    }
}