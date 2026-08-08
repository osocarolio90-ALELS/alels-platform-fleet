package com.alels.ingestion.cell.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngestionCellConfigTest {
    @Test
    void singleCellAcceptsLegacyMessagesAndPreservesTopic() {
        IngestionCellConfig config = new IngestionCellConfig("cell-0", 1, true, false);
        assertTrue(config.accepts(null));
        assertEquals("telemetry.raw", config.topic("telemetry.raw"));
        assertEquals("alels-ingestion-service", config.consumerGroup("alels-ingestion-service"));
    }

    @Test
    void multiCellRequiresMatchingEnvelopeAndIsolatedTopic() {
        IngestionCellConfig config = new IngestionCellConfig("cell-3", 8, true, true);
        assertTrue(config.accepts("cell-3"));
        assertFalse(config.accepts("cell-2"));
        assertEquals("telemetry.raw.cell-3", config.topic("telemetry.raw"));
        assertEquals("alels-ingestion-service.cell-3", config.consumerGroup("alels-ingestion-service"));
    }

    @Test
    void unsafeMultiCellConfigurationFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngestionCellConfig("cell-0", 4, false, true));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestionCellConfig("cell-0", 4, true, false));
    }
}
