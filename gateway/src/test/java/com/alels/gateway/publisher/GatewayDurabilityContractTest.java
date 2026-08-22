package com.alels.gateway.publisher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GatewayDurabilityContractTest {
    @Test
    void kafkaPublisherRequiresDurableIdempotentAcknowledgement() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/alels/gateway/publisher/KafkaTelemetryPublisher.java"));
        assertTrue(source.contains("ProducerConfig.ACKS_CONFIG, \"all\""));
        assertTrue(source.contains("ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, \"true\""));
        assertTrue(source.contains("result.complete(metadata.offset())"));
        assertTrue(source.contains("result.completeExceptionally"));
    }

    @Test
    void deviceAckIsCompletedFromPublishCallback() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/alels/gateway/netty/NettyDeviceChannelHandler.java"));
        int publish=source.indexOf("publish(rawPacketId, telemetry");
        int completion=source.indexOf(".whenComplete",publish);
        int ack=source.indexOf("sendAlelsAck(ctx)",completion);
        assertTrue(publish>=0&&completion>publish&&ack>completion);
    }

    @Test
    void nonKafkaPublisherRequiresExplicitUnsafeDevelopmentOptIn() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/alels/gateway/publisher/TelemetryPublisherFactory.java"));
        assertTrue(source.contains("ALELS_ALLOW_UNSAFE_PUBLISHER_MODE"));
        assertTrue(source.contains("forbidden"));
    }
}
