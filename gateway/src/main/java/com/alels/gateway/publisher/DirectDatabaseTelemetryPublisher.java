package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.TelemetryIoRepository;
import com.alels.gateway.repository.TelemetryRepository;
import com.alels.gateway.service.NormalizationEngine;
import com.alels.gateway.service.TelemetryAlertEvaluator;

public class DirectDatabaseTelemetryPublisher implements TelemetryPublisher {

    @Override
    public Long publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        Long telemetryId = TelemetryRepository.insert(
                null,
                telemetryData,
                protocol,
                channel
        );

        TelemetryIoRepository.insertAll(
                telemetryId,
                null,
                telemetryData,
                protocol,
                channel,
                dictionaryCode
        );

        NormalizationEngine.normalizeAndStore(
                telemetryId,
                null,
                null,
                null,
                telemetryData,
                protocol,
                dictionaryCode
        );

        TelemetryAlertEvaluator.evaluateAndStore(
                telemetryId,
                telemetryData,
                protocol,
                channel,
                dictionaryCode
        );

        System.out.println("[DB TELEMETRY] " + logLabel + " telemetryId=" + telemetryId);

        return telemetryId;
    }
}
