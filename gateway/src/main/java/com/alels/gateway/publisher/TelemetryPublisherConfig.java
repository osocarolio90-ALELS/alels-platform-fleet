package com.alels.gateway.publisher;

import java.io.InputStream;
import java.util.Properties;
import com.alels.gateway.cell.config.CellRoutingConfig;

public class TelemetryPublisherConfig {

    private static final String CONFIG_RESOURCE =
            "gateway.properties";

    private static final Properties properties =
            loadProperties();

    private TelemetryPublisherConfig() {
    }

    public static String publisherMode() {
        return get(
                "telemetry.publisher.mode",
                "TELEMETRY_PUBLISHER_MODE",
                "kafka"
        );
    }

    public static String kafkaBootstrapServers() {
        return get(
                "kafka.bootstrap.servers",
                "KAFKA_BOOTSTRAP_SERVERS",
                "localhost:9092"
        );
    }

    public static String kafkaTelemetryRawTopic() {
        String baseTopic = get(
                "kafka.topic.telemetry.raw",
                "KAFKA_TOPIC_TELEMETRY_RAW",
                "telemetry.raw"
        );
        return CellRoutingConfig.current().topic(baseTopic);
    }

    private static String get(
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

    private static Properties loadProperties() {
        Properties loaded =
                new Properties();

        try (
                InputStream in =
                        TelemetryPublisherConfig.class
                                .getClassLoader()
                                .getResourceAsStream(CONFIG_RESOURCE)
        ) {
            if (in != null) {
                loaded.load(in);
            }
        } catch (Exception e) {
            System.err.println(
                    "[TELEMETRY PUBLISHER CONFIG] failed to load "
                            + CONFIG_RESOURCE
                            + ": "
                            + e.getMessage()
            );
        }

        return loaded;
    }
}
