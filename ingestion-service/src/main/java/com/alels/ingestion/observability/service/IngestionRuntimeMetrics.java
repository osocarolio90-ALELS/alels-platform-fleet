package com.alels.ingestion.observability.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class IngestionRuntimeMetrics {
    private static final IngestionRuntimeMetrics INSTANCE = new IngestionRuntimeMetrics();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicLong polled = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong consumerLag = new AtomicLong();
    private final AtomicLong lastBatchDurationMs = new AtomicLong();
    private final AtomicLong lastSuccessfulBatchEpochMs = new AtomicLong();

    private IngestionRuntimeMetrics() {
    }

    public static IngestionRuntimeMetrics instance() {
        return INSTANCE;
    }

    public void markReady() {
        ready.set(true);
    }

    public void markDraining() {
        ready.set(false);
    }

    public boolean isReady() {
        return ready.get();
    }

    public void recordBatch(int pollCount, int processedCount, int failedCount,
                            int duplicateCount, long lag, long durationMs) {
        polled.addAndGet(Math.max(pollCount, 0));
        processed.addAndGet(Math.max(processedCount, 0));
        failed.addAndGet(Math.max(failedCount, 0));
        duplicates.addAndGet(Math.max(duplicateCount, 0));
        consumerLag.set(Math.max(lag, 0L));
        lastBatchDurationMs.set(Math.max(durationMs, 0L));
        if (failedCount == 0) lastSuccessfulBatchEpochMs.set(System.currentTimeMillis());
    }

    public String prometheus() {
        return """
                # TYPE alels_ingestion_ready gauge
                alels_ingestion_ready %d
                # TYPE alels_ingestion_records_polled_total counter
                alels_ingestion_records_polled_total %d
                # TYPE alels_ingestion_records_processed_total counter
                alels_ingestion_records_processed_total %d
                # TYPE alels_ingestion_records_failed_total counter
                alels_ingestion_records_failed_total %d
                # TYPE alels_ingestion_records_duplicate_total counter
                alels_ingestion_records_duplicate_total %d
                # TYPE alels_ingestion_consumer_lag gauge
                alels_ingestion_consumer_lag %d
                # TYPE alels_ingestion_last_batch_duration_milliseconds gauge
                alels_ingestion_last_batch_duration_milliseconds %d
                # TYPE alels_ingestion_last_success_epoch_milliseconds gauge
                alels_ingestion_last_success_epoch_milliseconds %d
                """.formatted(ready.get() ? 1 : 0, polled.get(), processed.get(), failed.get(),
                duplicates.get(), consumerLag.get(), lastBatchDurationMs.get(),
                lastSuccessfulBatchEpochMs.get());
    }
}
