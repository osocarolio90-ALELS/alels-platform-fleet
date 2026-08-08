package com.alels.ingestion.config;

import java.io.InputStream;
import java.util.Properties;
import com.alels.ingestion.cell.config.IngestionCellConfig;

public class IngestionConfig {

    private static final String CONFIG_RESOURCE =
            "application.properties";

    private final Properties properties;

    private IngestionConfig(Properties properties) {
        this.properties = properties;
    }

    public static IngestionConfig load() {
        Properties loaded =
                new Properties();

        try (
                InputStream in =
                        IngestionConfig.class
                                .getClassLoader()
                                .getResourceAsStream(CONFIG_RESOURCE)
        ) {
            if (in != null) {
                loaded.load(in);
            }
        } catch (Exception e) {
            System.err.println("[INGESTION CONFIG] failed to load application.properties: " + e.getMessage());
        }

        return new IngestionConfig(loaded);
    }

    public String kafkaBootstrapServers() {
        return get("kafka.bootstrap.servers", "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    }

    public String telemetryRawTopic() {
        return IngestionCellConfig.current().topic(
                get("kafka.topic.telemetry.raw", "KAFKA_TOPIC_TELEMETRY_RAW", "telemetry.raw")
        );
    }

    public String telemetryDlqTopic() {
        return IngestionCellConfig.current().topic(
                get("kafka.topic.telemetry.dlq", "KAFKA_TOPIC_TELEMETRY_DLQ", "telemetry.dead-letter")
        );
    }

    public String consumerGroupId() {
        return IngestionCellConfig.current().consumerGroup(
                get("kafka.consumer.group.id", "KAFKA_CONSUMER_GROUP_ID", "alels-ingestion-service")
        );
    }

    public long pollMillis() {
        return Long.parseLong(get("kafka.consumer.poll.ms", "KAFKA_CONSUMER_POLL_MS", "1000"));
    }

    public int maxPollRecords() {
        return Integer.parseInt(get("kafka.consumer.max.poll.records", "KAFKA_CONSUMER_MAX_POLL_RECORDS", "500"));
    }

    public int maxRetries() {
        return Integer.parseInt(get("ingestion.max.retries", "INGESTION_MAX_RETRIES", "3"));
    }

    public long retryBackoffMillis() {
        return Long.parseLong(get("ingestion.retry.backoff.ms", "INGESTION_RETRY_BACKOFF_MS", "250"));
    }

    public long lagSnapshotIntervalMillis() {
        return Long.parseLong(get(
                "ingestion.lag.snapshot.interval.ms",
                "INGESTION_LAG_SNAPSHOT_INTERVAL_MS",
                "30000"
        ));
    }

    public int dlqReplayMaxRecords() {
        return Integer.parseInt(get("ingestion.dlq.replay.max.records", "INGESTION_DLQ_REPLAY_MAX_RECORDS", "1000"));
    }

    public boolean dlqReplayOnStartup() {
        return Boolean.parseBoolean(get("ingestion.dlq.replay.on.startup", "INGESTION_DLQ_REPLAY_ON_STARTUP", "false"));
    }

    public String databaseUrl() {
        return requiredEnvironment("ALELS_DB_URL");
    }

    public String databaseUser() {
        return requiredEnvironment("ALELS_DB_USER");
    }

    public String databasePassword() {
        return requiredEnvironment("ALELS_DB_PASSWORD");
    }

    public int databaseMaximumPoolSize() {
        return Integer.parseInt(get("database.pool.maximumSize", "DATABASE_POOL_MAXIMUM_SIZE", "20"));
    }

    private String get(
            String propertyKey,
            String envKey,
            String defaultValue
    ) {
        String envValue =
                System.getenv(envKey);

        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String propertyValue =
                properties.getProperty(propertyKey);

        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        return defaultValue;
    }

    private String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be configured");
        }
        return value.trim();
    }
}
