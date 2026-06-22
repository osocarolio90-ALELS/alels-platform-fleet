package com.alels.gateway.publisher;

public class TelemetryPublisherFactory {

    private TelemetryPublisherFactory() {
    }

    public static TelemetryPublisher create() {
        String mode =
                TelemetryPublisherConfig.publisherMode()
                        .toLowerCase();

        if ("kafka".equals(mode)) {
            System.out.println("[TELEMETRY PUBLISHER] mode=KAFKA");
            return new KafkaTelemetryPublisher(
                    TelemetryPublisherConfig.kafkaBootstrapServers(),
                    TelemetryPublisherConfig.kafkaTelemetryRawTopic()
            );
        }

        if ("log".equals(mode)) {
            System.out.println("[TELEMETRY PUBLISHER] mode=LOG");
            return new LoggingTelemetryPublisher();
        }

        System.out.println("[TELEMETRY PUBLISHER] mode=DIRECT");
        return new DirectDatabaseTelemetryPublisher();
    }
}
