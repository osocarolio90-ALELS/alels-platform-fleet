package com.alels.ingestion.model;

public class IngestionRecord {
    public final String topic;
    public final int partition;
    public final long offset;
    public final String key;
    public final String payload;

    public IngestionRecord(String topic, int partition, long offset, String key, String payload) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.payload = payload;
    }
}
