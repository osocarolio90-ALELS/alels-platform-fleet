package com.alels.ingestion.validation.service;

import com.alels.ingestion.model.TelemetryMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.alels.ingestion.cell.config.IngestionCellConfig;

public final class TelemetryMessageValidator {

    private static final long MAX_FUTURE_SKEW_SECONDS = 24L * 60L * 60L;

    private TelemetryMessageValidator() {
    }

    public static String validate(TelemetryMessage message) {
        if (message == null) return "message is required";
        if (message.imei == null || !message.imei.matches("[0-9]{15}")) {
            return "imei must contain exactly 15 digits";
        }
        if (blank(message.protocol)) return "protocol is required";
        if (blank(message.channel)) return "channel is required";
        if (!IngestionCellConfig.current().accepts(message.cellId)) {
            return "message cell does not match ingestion cell";
        }
        if (message.latitude != null && (message.latitude < -90 || message.latitude > 90)) {
            return "latitude is outside -90..90";
        }
        if (message.longitude != null && (message.longitude < -180 || message.longitude > 180)) {
            return "longitude is outside -180..180";
        }
        if (message.speed != null && message.speed < 0) return "speed cannot be negative";
        if (message.satellites != null && message.satellites < 0) return "satellites cannot be negative";
        if (message.deviceTime != null && !message.deviceTime.isBlank()) {
            Instant deviceTime = parseUtc(message.deviceTime);
            if (deviceTime == null) return "deviceTime must be ISO-8601";
            if (deviceTime.isAfter(Instant.now().plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
                return "deviceTime exceeds maximum future clock skew";
            }
        }
        return null;
    }

    public static Instant parseUtc(String value) {
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toInstant();
            } catch (Exception ignoredOffset) {
                try {
                    return LocalDateTime.parse(value.trim().replace(' ', 'T')).toInstant(ZoneOffset.UTC);
                } catch (Exception ignoredLocal) {
                    return null;
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
