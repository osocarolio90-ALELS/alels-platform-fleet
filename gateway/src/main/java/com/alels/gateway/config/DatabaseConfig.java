package com.alels.gateway.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/alels_db";

    private static final String USER =
            "postgres";

    private static final String PASSWORD =
            "123456";

    static {
        try {
            Class.forName("org.postgresql.Driver");

            System.out.println("[DATABASE] PostgreSQL Driver Loaded");

        } catch (Exception e) {

            System.err.println("[DATABASE] Driver Load Failed: "
                    + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(
                URL,
                USER,
                PASSWORD
        );
    }

    public static boolean testConnection() {

        try (Connection conn = getConnection()) {

            System.out.println(
                    "[DATABASE] Connected: "
                            + conn.getMetaData().getDatabaseProductName()
                            + " "
                            + conn.getMetaData().getDatabaseProductVersion()
            );

            return true;

        } catch (Exception e) {

            System.err.println(
                    "[DATABASE] Connection Failed: "
                            + e.getMessage()
            );

            return false;
        }
    }
}