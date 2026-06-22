package com.alels.ingestion.repository;

public class DlqItem {
    public long id;
    public String topicName;
    public int partitionNo;
    public long offsetNo;
    public String messageKey;
    public String payload;
    public int retryCount;
}
