package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class LoggingTelemetryPublisher implements TelemetryPublisher {

    @Override
    public CompletionStage<Long> publish(
            Long rawPacketId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        String imei =
                telemetryData != null
                        ? telemetryData.getImei()
                        : null;

        System.out.println("[TELEMETRY PUBLISHER] mode=LOGGING"
                + " label=" + logLabel
                + " imei=" + imei
                + " protocol=" + protocol
                + " channel=" + channel
                + " dictionary=" + dictionaryCode);

        return CompletableFuture.completedFuture(null);
    }
}
