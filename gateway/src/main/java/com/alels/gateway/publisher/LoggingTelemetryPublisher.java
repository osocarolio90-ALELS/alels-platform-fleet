package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;

public class LoggingTelemetryPublisher implements TelemetryPublisher {

    @Override
    public Long publish(
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

        return null;
    }
}
