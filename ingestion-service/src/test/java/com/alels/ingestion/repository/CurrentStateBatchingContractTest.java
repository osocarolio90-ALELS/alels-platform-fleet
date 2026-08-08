package com.alels.ingestion.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurrentStateBatchingContractTest {
    @Test
    void currentStateWritesAreCollapsedPerDeviceWhileHistoryRemainsPerRecord() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alels/ingestion/repository/TelemetryRepository.java"
        ));
        assertTrue(source.contains("Map<String, TelemetryMessage> latestByDevice"));
        assertTrue(source.contains("latestByDevice.put(message.imei, message)"));
        assertTrue(source.contains("for (TelemetryMessage message : latestByDevice.values())"));
        assertTrue(source.indexOf("telemetryStmt.addBatch()")
                < source.indexOf("latestByDevice.put(message.imei, message)"));
    }
}
