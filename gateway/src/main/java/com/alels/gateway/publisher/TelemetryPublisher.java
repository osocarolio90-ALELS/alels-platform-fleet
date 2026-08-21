package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import java.util.concurrent.CompletionStage;

public interface TelemetryPublisher {

    default CompletionStage<Long> publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        return publish(null, telemetryData, protocol, channel, dictionaryCode, logLabel);
    }

    CompletionStage<Long> publish(
            Long rawPacketId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    );
}
