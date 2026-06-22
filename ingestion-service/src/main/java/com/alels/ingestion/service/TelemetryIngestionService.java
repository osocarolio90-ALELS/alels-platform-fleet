package com.alels.ingestion.service;

import java.util.ArrayList;
import java.util.List;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.model.TelemetryMessage;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.repository.DlqItem;
import com.alels.ingestion.repository.TelemetryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TelemetryIngestionService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final TelemetryRepository telemetryRepository;
    private final LatestTelemetryStore latestTelemetryStore;
    private final DuplicateTelemetryDetector duplicateDetector;

    public TelemetryIngestionService(
            TelemetryRepository telemetryRepository,
            LatestTelemetryStore latestTelemetryStore,
            DuplicateTelemetryDetector duplicateDetector
    ) {
        this.telemetryRepository = telemetryRepository;
        this.latestTelemetryStore = latestTelemetryStore;
        this.duplicateDetector = duplicateDetector;
    }

    public boolean ingest(String payload) {
        return ingest(new IngestionRecord(null, 0, 0, null, payload));
    }

    public boolean ingest(IngestionRecord record) {
        BatchIngestionResult result = ingestBatch(List.of(record));
        return result.failed == 0;
    }

    public BatchIngestionResult ingestBatch(List<IngestionRecord> records) {
        if (records == null || records.isEmpty()) {
            return new BatchIngestionResult(0, 0);
        }

        List<IngestionRecord> acceptedRecords = new ArrayList<>();
        List<TelemetryMessage> acceptedMessages = new ArrayList<>();
        int failed = 0;

        for (IngestionRecord record : records) {
            if (record == null || record.payload == null || record.payload.isBlank()) {
                continue;
            }

            try {
                TelemetryMessage message = mapper.readValue(record.payload, TelemetryMessage.class);

                if (message.imei == null || message.imei.isBlank()) {
                    failed++;
                    continue;
                }

                if (duplicateDetector.isDuplicate(message)) {
                    duplicateDetector.markSeen(message);
                    continue;
                }

                acceptedRecords.add(record);
                acceptedMessages.add(message);
            } catch (Exception e) {
                failed++;
                System.err.println("[INGESTION PARSE ERROR] " + e.getMessage());
            }
        }

        BatchIngestionResult dbResult = telemetryRepository.insertBatch(acceptedRecords, acceptedMessages);

        for (TelemetryMessage message : acceptedMessages) {
            latestTelemetryStore.update(message);
            duplicateDetector.markSeen(message);
        }

        dbResult.failed += failed;
        return dbResult;
    }

    public void recordConsumerHeartbeat(int processed, int failed, long consumerLag) {
        telemetryRepository.upsertIngestionHealth(processed, failed, consumerLag);
    }

    public void recordLagSnapshot(String groupId, String topic, int partition, long currentOffset, long endOffset, long lag) {
        telemetryRepository.insertLagSnapshot(groupId, topic, partition, currentOffset, endOffset, lag);
    }

    public void recordDeadLetter(
            String topic,
            int partition,
            long offset,
            String key,
            String payload,
            String failureReason
    ) {
        telemetryRepository.insertDeadLetter(topic, partition, offset, key, payload, failureReason);
    }

    public int replayOpenDlq(int maxRecords) {
        int success = 0;
        List<DlqItem> items = telemetryRepository.fetchOpenDlqItems(maxRecords);

        for (DlqItem item : items) {
            IngestionRecord record = new IngestionRecord(
                    item.topicName,
                    item.partitionNo,
                    item.offsetNo,
                    item.messageKey,
                    item.payload
            );

            if (ingest(record)) {
                telemetryRepository.markDlqResolved(item.id);
                success++;
            } else {
                telemetryRepository.markDlqRetryFailed(item.id);
            }
        }

        return success;
    }
}
