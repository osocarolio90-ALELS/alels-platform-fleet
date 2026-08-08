package com.alels.gateway.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryPublisherFactory {
    private static final Logger log=LoggerFactory.getLogger(TelemetryPublisherFactory.class);

    private TelemetryPublisherFactory() {
    }

    public static TelemetryPublisher create() {
        String mode =
                TelemetryPublisherConfig.publisherMode()
                        .toLowerCase();

        if ("kafka".equals(mode)) {
            log.info("event=telemetry_publisher_start mode=kafka");
            return new KafkaTelemetryPublisher(
                    TelemetryPublisherConfig.kafkaBootstrapServers(),
                    TelemetryPublisherConfig.kafkaTelemetryRawTopic()
            );
        }

        if (!Boolean.parseBoolean(System.getenv().getOrDefault("ALELS_ALLOW_UNSAFE_PUBLISHER_MODE","false"))) {
            throw new IllegalStateException("Publisher mode '"+mode+"' is forbidden without ALELS_ALLOW_UNSAFE_PUBLISHER_MODE=true");
        }

        if ("log".equals(mode)) {
            log.warn("event=telemetry_publisher_start mode=log unsafe_mode=true");
            return new LoggingTelemetryPublisher();
        }

        if (!"direct".equals(mode)) throw new IllegalStateException("Unsupported telemetry publisher mode: "+mode);
        log.warn("event=telemetry_publisher_start mode=direct unsafe_mode=true");
        return new DirectDatabaseTelemetryPublisher();
    }
}
