package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import java.util.concurrent.CompletionStage;

public interface TelemetryPublisher {

    CompletionStage<Long> publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    );
}
