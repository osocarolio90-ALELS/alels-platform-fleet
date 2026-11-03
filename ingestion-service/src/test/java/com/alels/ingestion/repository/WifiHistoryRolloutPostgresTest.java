package com.alels.ingestion.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.service.TelemetryIngestionService;

/** Real repository transactions on the dedicated disposable regression database, without a Kafka broker. */
class WifiHistoryRolloutPostgresTest {
    private static final String URL = "jdbc:postgresql://localhost:55443/postgres";
    private static Database database;

    @BeforeAll
    static void connectDisposable() {
        assumeTrue(URL.equals(System.getenv("ALELS_WIFI_REGRESSION_TEST_DB_URL"))
                && URL.equals(System.getenv("ALELS_DB_URL")), "Dedicated disposable regression database required");
        database = new Database(IngestionConfig.load());
    }

    @AfterAll
    static void close() { if (database != null) database.close(); }

    @BeforeEach
    void fixture() throws Exception {
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
    void disabledHistoryPreservesOrdinaryTelemetryAndExactKafkaReplayWithoutNewSchema() throws Exception {
        var service = new TelemetryIngestionService(new TelemetryRepository(database));
        var first = service.ingestBatch(List.of(record()));
        assertEquals(1, first.processed);
        assertEquals(1, count("telemetry"));
        assertEquals(1, count("raw_packets"));
        assertEquals(1, count("kafka_processed_offsets"));
        assertEquals(1, service.ingestBatch(List.of(record())).duplicate);
        assertEquals(1, count("telemetry"));
        try (Connection connection = connect(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT io_data->>'1' FROM telemetry")) {
            result.next(); assertEquals("7", result.getString(1));
        }
    }

    private IngestionRecord record() {
        String payload = """
                {"gatewayRawPacketId":101,"imei":"863719066421737","protocol":"ALELS_JSON",
                 "sourceProtocol":"ALELS_JSON","channel":"WIFI","dictionaryCode":"fmc650",
                 "deviceTime":"2026-10-25T13:08:54Z","packetSequence":68,"latitude":-6.2,
                 "longitude":107.0,"altitude":26,"angle":0,"speed":0,"satellites":12,"hdop":0.5,
                 "priority":0,"eventIoId":1,"ioData":{"1":7}}
                """;
        return new IngestionRecord("telemetry.raw", 0, 10, "863719066421737", payload);
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
