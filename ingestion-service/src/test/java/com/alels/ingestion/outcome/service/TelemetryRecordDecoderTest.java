package com.alels.ingestion.outcome.service;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.outcome.model.DecodedTelemetryBatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRecordDecoderTest {

    @Test
    void separatesOnlyInvalidRecordFromValidBatch() {
        IngestionRecord valid = record(10, """
                {"imei":"123456789012345","protocol":"TELTONIKA_CODEC8E","channel":"GSM",
                 "deviceTime":"2026-08-08T05:00:00Z","latitude":-6.2,"longitude":106.8}
                """);
        IngestionRecord invalid = record(11, "{broken-json");

        DecodedTelemetryBatch result = TelemetryRecordDecoder.decode(List.of(valid, invalid));

        assertEquals(List.of(valid), result.records());
        assertEquals(1, result.messages().size());
        assertEquals(1, result.failures().size());
        assertEquals(invalid, result.failures().getFirst().record());
    }

    @Test
    void rejectsInvalidImeiAndCoordinatesWithoutRejectingNeighbor() {
        IngestionRecord invalid = record(20, """
                {"imei":"123","protocol":"ALELS_JSON","channel":"WIFI","latitude":91}
                """);
        IngestionRecord valid = record(21, """
                {"imei":"123456789012345","protocol":"ALELS_JSON","channel":"WIFI","latitude":0,"longitude":0}
                """);

        DecodedTelemetryBatch result = TelemetryRecordDecoder.decode(List.of(invalid, valid));

        assertEquals(List.of(valid), result.records());
        assertEquals(1, result.failures().size());
        assertEquals(invalid, result.failures().getFirst().record());
    }

    private IngestionRecord record(long offset, String payload) {
        return new IngestionRecord("telemetry.raw", 0, offset, "123456789012345", payload);
    }
}
