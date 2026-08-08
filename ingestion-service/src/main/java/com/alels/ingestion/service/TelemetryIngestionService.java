package com.alels.ingestion.service;

import java.util.List;

import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.outcome.model.DecodedTelemetryBatch;
import com.alels.ingestion.outcome.service.TelemetryRecordDecoder;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.repository.DlqItem;
import com.alels.ingestion.repository.TelemetryRepository;

public class TelemetryIngestionService {

    private final TelemetryRepository telemetryRepository;
    public TelemetryIngestionService(TelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
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

        DecodedTelemetryBatch decoded = TelemetryRecordDecoder.decode(records);
        BatchIngestionResult dbResult = telemetryRepository.insertBatch(decoded.records(), decoded.messages());

        return new BatchIngestionResult(
                dbResult.processed,
                decoded.failures().size(),
                dbResult.duplicate,
                decoded.failures()
        );
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
