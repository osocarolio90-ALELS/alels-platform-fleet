package com.alels.ingestion.observability.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngestionRuntimeMetricsTest {
    @Test
    void metricsExposeReadinessLagAndOutcomesWithoutHighCardinalityLabels() {
        IngestionRuntimeMetrics metrics = IngestionRuntimeMetrics.instance();
        metrics.markDraining();
        assertFalse(metrics.isReady());
        metrics.markReady();
        metrics.recordBatch(12, 10, 1, 1, 25, 40);

        String output = metrics.prometheus();
        assertTrue(output.contains("alels_ingestion_ready 1"));
        assertTrue(output.contains("alels_ingestion_records_polled_total 12"));
        assertTrue(output.contains("alels_ingestion_consumer_lag 25"));
        metrics.markDraining();
    }
}
