package com.alels.ingestion.repository;

import java.sql.Connection;
import java.sql.SQLException;

import com.alels.ingestion.config.IngestionConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class Database {

    private final HikariDataSource dataSource;

    public Database(IngestionConfig config) {
        HikariConfig hikariConfig =
                new HikariConfig();

        hikariConfig.setJdbcUrl(config.databaseUrl());
        hikariConfig.setUsername(config.databaseUser());
        hikariConfig.setPassword(config.databasePassword());
        hikariConfig.setMaximumPoolSize(config.databaseMaximumPoolSize());
        hikariConfig.setPoolName("alels-ingestion-pool");

        this.dataSource =
                new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }
}
