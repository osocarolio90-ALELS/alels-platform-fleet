package com.alels.ingestion.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alels.ingestion.model.TelemetryMessage;

public class LatestTelemetryStore {

    private final Map<String, TelemetryMessage> latestByImei =
            new ConcurrentHashMap<>();

    public void update(TelemetryMessage message) {
        if (message == null || message.imei == null || message.imei.isBlank()) {
            return;
        }

        latestByImei.put(message.imei, message);
    }

    public TelemetryMessage findLatest(String imei) {
        return latestByImei.get(imei);
    }
}
