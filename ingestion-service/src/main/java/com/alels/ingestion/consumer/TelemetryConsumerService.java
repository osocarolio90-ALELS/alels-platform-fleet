package com.alels.ingestion.consumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.model.IngestionRecord;
import com.alels.ingestion.producer.DeadLetterPublisher;
import com.alels.ingestion.repository.BatchIngestionResult;
import com.alels.ingestion.service.TelemetryIngestionService;

public class TelemetryConsumerService {

    private final IngestionConfig config;
    private final TelemetryIngestionService ingestionService;

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
            System.out.println("[INGESTION DLQ REPLAY] startup replayed=" + replayed);
        }

        try (
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
                DeadLetterPublisher deadLetterPublisher = new DeadLetterPublisher(config)
        ) {
            consumer.subscribe(List.of(config.telemetryRawTopic()));

            System.out.println("[INGESTION] consuming topic=" + config.telemetryRawTopic()
                    + " bootstrap=" + config.kafkaBootstrapServers()
                    + " group=" + config.consumerGroupId()
                    + " maxPollRecords=" + config.maxPollRecords());

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
                if (result.failed == 0) {
                    return result;
                }
                lastError = "batch ingestion partially failed processed=" + result.processed + " failed=" + result.failed;
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }

        for (ConsumerRecord<String, String> record : records) {
            deadLetterPublisher.publish(
                    record.key(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.value(),
                    lastError == null ? "unknown batch ingestion failure" : lastError
            );

            ingestionService.recordDeadLetter(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    record.value(),
                    lastError
            );
        }

        return result;
    }

    private long calculateAndRecordLag(KafkaConsumer<String, String> consumer) {
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
        } catch (Exception e) {
            System.err.println("[INGESTION LAG ERROR] " + e.getMessage());
        }
        return totalLag;
    }
}
