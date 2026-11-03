package com.alels.gateway.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alels.gateway.normalizer.AlelsJsonTelemetryNormalizer;
import com.alels.gateway.config.DatabaseConfig;

/** Executes the real direct publisher. Requires an explicitly provisioned disposable PostgreSQL. */
class DirectDatabaseWifiRegressionPostgresTest {
    private static final String URL = "jdbc:postgresql://localhost:55443/postgres";
    private static final String IMEI = "863719066421737";
    @BeforeEach
    void disposableFixture() throws Exception {
        assumeTrue(URL.equals(System.getenv("ALELS_WIFI_REGRESSION_TEST_DB_URL"))
                && URL.equals(System.getenv("ALELS_DB_URL")), "Dedicated disposable regression database required");
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS telemetry_io, telemetry, raw_packets, devices, companies,
                        device_brands, device_models, alert_rules, kafka_processed_offsets,
                        device_presence_cache_shadow, device_latest_position;
                    """);
            statement.execute(Files.readString(Path.of("../database/tests/fixtures/wifi-telemetry-regression.sql")));
            statement.execute("""
                    INSERT INTO companies(id) VALUES(1);
                    INSERT INTO devices(id,company_id,imei) VALUES(11,1,'863719066421737');
                    """);
        }
    }

    @Test
    void legacyWifiDeliveryCompletesAndKeepsRawIo() throws Exception {
        String raw = sample();
        long auditId = audit(raw);
        var telemetry = AlelsJsonTelemetryNormalizer.normalize(raw.getBytes(StandardCharsets.UTF_8));
        Long telemetryId = new DirectDatabaseTelemetryPublisher().publish(auditId, telemetry,
                "ALELS_JSON", "WIFI", "fmc650", "ALELS_JSON").toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(telemetryId > 0);
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT raw_packet_id,io_data->>'1' FROM telemetry")) {
                assertTrue(result.next());
                assertEquals(auditId, result.getLong(1));
                assertEquals("7", result.getString(2));
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM telemetry_io WHERE io_id='1'")) {
                result.next(); assertEquals(1, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM raw_packets")) {
                result.next(); assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void regularTelemetryDoesNotWaitForAnAdditionalPoolConnectionDuringInsert() throws Exception {
        String raw = sample();
        long auditId = audit(raw);
        var telemetry = AlelsJsonTelemetryNormalizer.normalize(raw.getBytes(StandardCharsets.UTF_8));
        try (var warmup = DatabaseConfig.getConnection()) { assertTrue(warmup.isValid(1)); }
        // Run this suite with a one-connection pool. The regression waited 5s per nested borrow.
        assertTimeout(Duration.ofSeconds(4), () -> {
            Long id = new DirectDatabaseTelemetryPublisher(false).publish(auditId, telemetry,
                    "ALELS_JSON", "WIFI", "fmc650", "ALELS_JSON")
                    .toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(id > 0);
        });
        assertEquals(1, count("telemetry"));
    }

    @Test
    void disabledHistoryStillFailsPublicationWhenOrdinaryTelemetryCannotBeSaved() throws Exception {
        String raw = sample();
        long auditId = audit(raw);
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE telemetry ADD CHECK (packet_sequence < 0)");
        }
        var telemetry = AlelsJsonTelemetryNormalizer.normalize(raw.getBytes(StandardCharsets.UTF_8));
        assertThrows(ExecutionException.class, () -> new DirectDatabaseTelemetryPublisher(false)
                .publish(auditId, telemetry, "ALELS_JSON", "WIFI", "fmc650", "ALELS_JSON")
                .toCompletableFuture().get(15, TimeUnit.SECONDS));
        assertEquals(0, count("telemetry"));
        assertEquals(1, count("raw_packets"));
        assertEquals(0, count("telemetry_io"));
    }

    private String sample() {
        return "{\"T\":\"data\",\"Q\":68,\"A\":\"" + IMEI
                + "\",\"B\":\"2026-10-25 13:08:54\",\"K\":1,\"1\":7}";
    }

    private long audit(String raw) throws Exception {
        try (Connection connection = connect(); var statement = connection.prepareStatement(
                "INSERT INTO raw_packets(imei,raw_json) VALUES(?,?) RETURNING id")) {
            statement.setString(1, IMEI);
            statement.setString(2, raw);
            try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private int count(String fixtureTable) throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM " + fixtureTable)) {
            result.next(); return result.getInt(1);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(URL, "postgres", "test-only");
    }
}
