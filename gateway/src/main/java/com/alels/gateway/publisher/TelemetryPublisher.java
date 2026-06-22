package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;

public interface TelemetryPublisher {

    Long publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    );
}
