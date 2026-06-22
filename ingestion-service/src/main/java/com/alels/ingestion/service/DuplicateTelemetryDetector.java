package com.alels.ingestion.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.ingestion.model.TelemetryMessage;
import com.alels.ingestion.repository.TelemetryRepository;

public class DuplicateTelemetryDetector {

    private static final int MAX_CACHE_SIZE =
            10_000;

    private final TelemetryRepository telemetryRepository;

    private final Map<String, Boolean> seenKeys =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                            return size() > MAX_CACHE_SIZE;
                        }
                    }
            );

    public DuplicateTelemetryDetector(TelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    public boolean isDuplicate(TelemetryMessage message) {
        String key =
                key(message);

        if (key == null) {
            return false;
        }

        if (seenKeys.containsKey(key)) {
            return true;
        }

        if (telemetryRepository.existsDuplicate(message)) {
            seenKeys.put(key, Boolean.TRUE);
            return true;
        }

        return false;
    }

    public void markSeen(TelemetryMessage message) {
        String key =
                key(message);

        if (key != null) {
            seenKeys.put(key, Boolean.TRUE);
        }
    }

    private String key(TelemetryMessage message) {
        if (message == null
                || message.imei == null
                || message.packetSequence == null) {
            return null;
        }

        return message.imei
                + "|"
                + message.protocol
                + "|"
                + message.channel
                + "|"
                + message.packetSequence;
    }
}
