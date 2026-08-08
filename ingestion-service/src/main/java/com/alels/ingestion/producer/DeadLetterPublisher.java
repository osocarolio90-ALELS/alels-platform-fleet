package com.alels.ingestion.producer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.alels.ingestion.config.IngestionConfig;
import com.alels.ingestion.config.KafkaSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeadLetterPublisher implements AutoCloseable {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final KafkaProducer<String, String> producer;
    private final String topic;

    public DeadLetterPublisher(IngestionConfig config) {
        this.topic = config.telemetryDlqTopic();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        KafkaSecurityConfig.apply(props);

        this.producer = new KafkaProducer<>(props);
    }

    public void publish(
            String key,
            String originalTopic,
            int partition,
            long offset,
            String payload,
            String failureReason
    ) {
        try {
            Map<String, Object> dlq = new LinkedHashMap<>();
            dlq.put("originalTopic", originalTopic);
            dlq.put("originalPartition", partition);
            dlq.put("originalOffset", offset);
            dlq.put("failureReason", failureReason);
            dlq.put("payload", payload);
            dlq.put("createdAt", java.time.Instant.now().toString());

            producer.send(new ProducerRecord<>(topic, key, mapper.writeValueAsString(dlq))).get();
        } catch (Exception e) {
            throw new IllegalStateException("DLQ durable publish failed for topic=" + topic, e);
        }
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }
}
