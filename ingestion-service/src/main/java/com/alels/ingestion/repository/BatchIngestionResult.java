package com.alels.ingestion.repository;

public class BatchIngestionResult {
    public int processed;
    public int failed;

    public BatchIngestionResult(int processed, int failed) {
        this.processed = processed;
        this.failed = failed;
    }
}
