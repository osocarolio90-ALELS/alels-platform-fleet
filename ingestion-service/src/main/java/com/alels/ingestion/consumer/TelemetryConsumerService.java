package com.alels.ingestion.consumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.outcome.model.IngestionFailure;
import com.alels.ingestion.producer.DeadLetterPublisher;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.service.TelemetryIngestionService;

public class TelemetryConsumerService {
    private static final Logger log=LoggerFactory.getLogger(TelemetryConsumerService.class);

    private final IngestionConfig config;
    private final TelemetryIngestionService ingestionService;
    private long lastLagSnapshotAt;
    private long lastKnownLag;

    public TelemetryConsumerService(
            IngestionConfig config,
            TelemetryIngestionService ingestionService
    ) {
        this.config = config;
        this.ingestionService = ingestionService;
    }

    public void start() {
        Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.consumerGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(config.maxPollRecords()));
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        if (config.dlqReplayOnStartup()) {
            int replayed = ingestionService.replayOpenDlq(config.dlqReplayMaxRecords());
            log.info("event=dlq_startup_replay replayed={}",replayed);
        }

        try (
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
                DeadLetterPublisher deadLetterPublisher = new DeadLetterPublisher(config)
        ) {
            consumer.subscribe(List.of(config.telemetryRawTopic()));

            log.info("event=ingestion_consumer_start topic={} bootstrap={} group={} max_poll_records={}",
                    config.telemetryRawTopic(),config.kafkaBootstrapServers(),config.consumerGroupId(),config.maxPollRecords());

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(config.pollMillis()));

                BatchIngestionResult result = processBatch(records, deadLetterPublisher);
                long lag = calculateAndRecordLag(consumer);

                if (!records.isEmpty()) {
                    consumer.commitSync();
                }

                ingestionService.recordConsumerHeartbeat(result.processed, result.failed, lag);
            }
        }
    }

    private BatchIngestionResult processBatch(
            ConsumerRecords<String, String> records,
            DeadLetterPublisher deadLetterPublisher
    ) {
        if (records == null || records.isEmpty()) {
            return new BatchIngestionResult(0, 0);
        }

        List<IngestionRecord> batch = new ArrayList<>();

        for (ConsumerRecord<String, String> record : records) {
            batch.add(new IngestionRecord(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    record.value()
            ));
        }

        BatchIngestionResult result = new BatchIngestionResult(0, 0);
        String lastError = null;

        for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
            try {
                result = ingestionService.ingestBatch(batch);
                publishFailures(result.failures, deadLetterPublisher);
                return result;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName();
            }

            if (attempt < config.maxRetries()) {
                backoff(attempt);
            }
        }

        List<IngestionFailure> batchFailures = new ArrayList<>();
        for (IngestionRecord record : batch) {
            batchFailures.add(new IngestionFailure(
                    record,
                    lastError == null ? "database ingestion failure" : lastError
            ));
        }
        publishFailures(batchFailures, deadLetterPublisher);
        return new BatchIngestionResult(0, batchFailures.size(), 0, batchFailures);
    }

    private void publishFailures(
            List<IngestionFailure> failures,
            DeadLetterPublisher deadLetterPublisher
    ) {
        for (IngestionFailure failure : failures) {
            IngestionRecord record = failure.record();
            if (record == null) continue;
            deadLetterPublisher.publish(
                    record.key,
                    record.topic,
                    record.partition,
                    record.offset,
                    record.payload,
                    failure.reason()
            );
            ingestionService.recordDeadLetter(
                    record.topic,
                    record.partition,
                    record.offset,
                    record.key,
                    record.payload,
                    failure.reason()
            );
        }
    }

    private long calculateAndRecordLag(KafkaConsumer<String, String> consumer) {
        long now = System.currentTimeMillis();
        if (now - lastLagSnapshotAt < config.lagSnapshotIntervalMillis()) {
            return lastKnownLag;
        }

        long totalLag = 0;
        try {
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(consumer.assignment());
            for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
                TopicPartition partition = entry.getKey();
                long currentOffset = consumer.position(partition);
                long endOffset = entry.getValue();
                long lag = Math.max(endOffset - currentOffset, 0);
                totalLag += lag;
                ingestionService.recordLagSnapshot(
                        config.consumerGroupId(),
                        partition.topic(),
                        partition.partition(),
                        currentOffset,
                        endOffset,
                        lag
                );
            }
            lastKnownLag = totalLag;
            lastLagSnapshotAt = now;
        } catch (Exception e) {
            log.warn("event=ingestion_lag_snapshot_error error={}",e.getClass().getSimpleName());
        }
        return lastKnownLag;
    }

    private void backoff(int attempt) {
        long base = Math.max(config.retryBackoffMillis(), 1L);
        long exponential = Math.min(base * (1L << Math.min(attempt - 1, 10)), 30_000L);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(exponential / 4L, 1L));
        try {
            Thread.sleep(exponential + jitter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ingestion retry interrupted", interrupted);
        }
    }
}
