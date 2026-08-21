package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.TelemetryIoRepository;
import com.alels.gateway.repository.TelemetryRepository;
import com.alels.gateway.repository.DevicePresenceRepository;
import com.alels.gateway.repository.DeviceStatusRepository;
import com.alels.gateway.service.NormalizationEngine;
import com.alels.gateway.service.TelemetryAlertEvaluator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DirectDatabaseTelemetryPublisher implements TelemetryPublisher {

    @Override
    public CompletionStage<Long> publish(
            Long rawPacketId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        try {
            Long telemetryId = TelemetryRepository.insert(
                    rawPacketId, telemetryData, protocol, channel, dictionaryCode
            );
            if (telemetryId == null) {
                throw new IllegalStateException("Telemetry persistence did not return an id");
            }

        DeviceStatusRepository.markChannelOnline(telemetryData.getImei(), channel, protocol);
        DevicePresenceRepository.markPresentOnData(telemetryData.getImei());

        TelemetryIoRepository.insertAll(
                telemetryId,
                rawPacketId,
                telemetryData,
                protocol,
                channel,
                dictionaryCode
        );

        NormalizationEngine.normalizeAndStore(
                telemetryId,
                rawPacketId,
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

        System.out.println("[DB TELEMETRY] persisted imei=" + telemetryData.getImei()
                + " telemetryId=" + telemetryId
                + " protocol=" + protocol
                + " dictionary=" + dictionaryCode
                + " parser=" + logLabel);

            return CompletableFuture.completedFuture(telemetryId);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
