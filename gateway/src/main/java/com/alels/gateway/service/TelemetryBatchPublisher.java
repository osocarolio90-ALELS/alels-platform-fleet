package com.alels.gateway.service;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import com.alels.gateway.parser.ParserResult;
import com.alels.gateway.publisher.TelemetryPublisher;
import com.alels.gateway.server.ChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Publishes a parsed AVL batch and reports the accepted record count only after persistence succeeds. */
public final class TelemetryBatchPublisher {
    private final TelemetryPublisher publisher;
    private final GatewayRuntimeMetrics metrics;

    public TelemetryBatchPublisher(TelemetryPublisher publisher, GatewayRuntimeMetrics metrics) {
        this.publisher = publisher;
        this.metrics = metrics;
    }

    public CompletionStage<Integer> publish(
            ParserResult parsed,
            String imei,
            ProtocolType protocol,
            ChannelType channel,
            String dictionaryCode,
            String parserCode
    ) {
        if (parsed == null || !parsed.isValid() || imei == null || imei.isBlank()) {
            return CompletableFuture.completedFuture(0);
        }
        if (dictionaryCode == null || dictionaryCode.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Device model has no active dictionary")
            );
        }

        List<CompletableFuture<Long>> pending = new ArrayList<>();
        for (TelemetryData telemetry : parsed.getTelemetryList()) {
            if (!metrics.beginPublish()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Gateway publish capacity is saturated")
                );
            }
            telemetry.setImei(imei);
            CompletableFuture<Long> future = publisher.publish(
                    telemetry, protocol.name(), channel.name(), dictionaryCode, parserCode
            ).toCompletableFuture();
            future.whenComplete((ignored, error) -> metrics.publishCompleted(error == null));
            pending.add(future);
        }

        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> parsed.getRecordCount());
    }
}
