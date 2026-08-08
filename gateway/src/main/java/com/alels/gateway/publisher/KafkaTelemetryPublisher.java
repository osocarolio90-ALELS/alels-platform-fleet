package com.alels.gateway.publisher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.config.KafkaSecurityConfig;
import com.alels.gateway.cell.config.CellRoutingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KafkaTelemetryPublisher implements TelemetryPublisher {
    private static final Logger log=LoggerFactory.getLogger(KafkaTelemetryPublisher.class);

    private static final ObjectMapper mapper =
            new ObjectMapper();
    private static final AtomicLong NEXT_ERROR_LOG_MS = new AtomicLong();

    private final KafkaProducer<String, String> producer;
    private final String topic;

    public KafkaTelemetryPublisher(
            String bootstrapServers,
            String topic
    ) {
        this.topic = topic;

        Properties props =
                new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 1M-device foundation: prefer delivery safety over direct DB writes.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(64 * 1024));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,
                System.getenv().getOrDefault("ALELS_KAFKA_MAX_BLOCK_MS", "0"));
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG,
                System.getenv().getOrDefault("ALELS_KAFKA_BUFFER_MEMORY_BYTES", "67108864"));
        KafkaSecurityConfig.apply(props);

        this.producer =
                new KafkaProducer<>(props);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                producer.flush();
                producer.close();
            } catch (Exception ignored) {
                // shutdown best effort
            }
        }));
    }

    @Override
    public CompletionStage<Long> publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        if (telemetryData == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("telemetryData is required"));
        }

        CompletableFuture<Long> result = new CompletableFuture<>();
        try {
            String payload =
                    mapper.writeValueAsString(
                            toPayload(
                                    telemetryData,
                                    protocol,
                                    channel,
                                    dictionaryCode,
                                    logLabel
                            )
                    );

            producer.send(
                    new ProducerRecord<>(
                            topic,
                            telemetryData.getImei(),
                            payload
                    ),
                    (metadata, exception) -> {
                        if (exception != null) {
                            logPublishError(exception);
                            result.completeExceptionally(exception);
                            return;
                        }

                        result.complete(metadata.offset());
                    }
            );

        } catch (Exception e) {
            logPublishError(e);
            result.completeExceptionally(e);
        }

        return result;
    }

    private void logPublishError(Throwable error) {
        long now = System.currentTimeMillis();
        long next = NEXT_ERROR_LOG_MS.get();
        if (now >= next && NEXT_ERROR_LOG_MS.compareAndSet(next, now + 10_000L)) {
            log.error("event=kafka_telemetry_error topic={} error={}",topic,error.getClass().getSimpleName());
        }
    }

    private Map<String, Object> toPayload(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put("label", logLabel);
        payload.put("cellId", CellRoutingConfig.current().cellId());
        payload.put("cellIndex", CellRoutingConfig.current().cellIndex());
        payload.put("imei", telemetryData.getImei());
        payload.put("protocol", protocol);
        payload.put("channel", channel);
        payload.put("dictionaryCode", dictionaryCode);
        payload.put("sourceProtocol", telemetryData.getSourceProtocol());
        payload.put("deviceTime", telemetryData.getDeviceTime());
        payload.put("packetSequence", telemetryData.getPacketSequence());
        payload.put("latitude", telemetryData.getLatitude());
        payload.put("longitude", telemetryData.getLongitude());
        payload.put("altitude", telemetryData.getAltitude());
        payload.put("angle", telemetryData.getAngle());
        payload.put("satellites", telemetryData.getSatellites());
        payload.put("speed", telemetryData.getSpeed());
        payload.put("priority", telemetryData.getPriority());
        payload.put("eventIoId", telemetryData.getEventIoId());
        payload.put("hdop", telemetryData.getHdop());
        payload.put("ioData", telemetryData.getIoData());

        return payload;
    }
}
