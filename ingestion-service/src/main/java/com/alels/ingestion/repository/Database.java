package com.alels.ingestion.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alels.ingestion.config.IngestionConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class Database {

    private final HikariDataSource dataSource;
    private final AtomicBoolean closed = new AtomicBoolean();

    public Database(IngestionConfig config) {
        HikariConfig hikariConfig =
                new HikariConfig();

        hikariConfig.setJdbcUrl(config.databaseUrl());
        hikariConfig.setUsername(config.databaseUser());
        hikariConfig.setPassword(config.databasePassword());
        hikariConfig.setMaximumPoolSize(config.databaseMaximumPoolSize());
        hikariConfig.setMinimumIdle(Integer.parseInt(System.getenv().getOrDefault("DATABASE_POOL_MINIMUM_IDLE", "2")));
        hikariConfig.setConnectionTimeout(5_000);
        hikariConfig.setValidationTimeout(3_000);
        hikariConfig.setKeepaliveTime(30_000);
        hikariConfig.setMaxLifetime(300_000);
        hikariConfig.setPoolName("alels-ingestion-pool");

        this.dataSource =
                new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) dataSource.close();
    }
}
