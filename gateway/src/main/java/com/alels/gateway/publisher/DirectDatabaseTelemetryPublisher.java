package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.TelemetryIoRepository;
import com.alels.gateway.repository.TelemetryRepository;
import com.alels.gateway.service.NormalizationEngine;
import com.alels.gateway.service.TelemetryAlertEvaluator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DirectDatabaseTelemetryPublisher implements TelemetryPublisher {

    @Override
    public CompletionStage<Long> publish(
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        try {
            Long telemetryId = TelemetryRepository.insert(null, telemetryData, protocol, channel);

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

            return CompletableFuture.completedFuture(telemetryId);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
