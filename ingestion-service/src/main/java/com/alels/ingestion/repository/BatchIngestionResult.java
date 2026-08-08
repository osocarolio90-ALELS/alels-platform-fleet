package com.alels.ingestion.repository;

import com.alels.ingestion.outcome.model.IngestionFailure;

import java.util.List;

public class BatchIngestionResult {
    public int processed;
    public int failed;
    public int duplicate;
    public final List<IngestionFailure> failures;

    public BatchIngestionResult(int processed, int failed) {
        this(processed, failed, 0, List.of());
    }

    public BatchIngestionResult(
            int processed,
            int failed,
            int duplicate,
            List<IngestionFailure> failures
    ) {
        this.processed = processed;
        this.failed = failed;
        this.duplicate = duplicate;
        this.failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
