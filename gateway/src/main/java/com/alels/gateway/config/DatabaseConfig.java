package com.alels.gateway.config;

import java.sql.Connection;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig {

    private static final HikariDataSource DATA_SOURCE = createDataSource();

    static {
        try {
            Class.forName("org.postgresql.Driver");

        } catch (Exception e) {

            System.err.println("[DATABASE] Driver Load Failed: "
                    + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {

        return DATA_SOURCE.getConnection();
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

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(required("ALELS_DB_URL"));
        config.setUsername(required("ALELS_DB_USER"));
        config.setPassword(required("ALELS_DB_PASSWORD"));
        config.setMaximumPoolSize(Integer.parseInt(System.getenv().getOrDefault("ALELS_GATEWAY_DB_POOL_MAX", "20")));
        config.setMinimumIdle(Integer.parseInt(System.getenv().getOrDefault("ALELS_GATEWAY_DB_POOL_MIN_IDLE", "2")));
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.setKeepaliveTime(30_000);
        config.setMaxLifetime(300_000);
        config.setPoolName("alels-gateway-pool");
        return new HikariDataSource(config);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value.trim();
    }
}
