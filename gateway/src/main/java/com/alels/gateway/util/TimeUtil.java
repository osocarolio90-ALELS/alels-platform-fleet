package com.alels.gateway.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * Parses the canonical device timestamp into an absolute UTC instant.
     * ALELS JSON field B uses a timezone-less UTC value (yyyy-MM-dd HH:mm:ss),
     * while native codec records are normally emitted as ISO-8601 with Z.
     */
    public static Instant parseDeviceTimeUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (Exception ignoredOffset) {
                try {
                    return LocalDateTime.parse(trimmed.replace(' ', 'T'))
                            .toInstant(ZoneOffset.UTC);
                } catch (Exception ignoredLocal) {
                    return null;
                }
            }
        }
    }

    public static Timestamp parseDeviceTimestampUtc(String value) {
        Instant instant = parseDeviceTimeUtc(value);
        return instant == null ? null : Timestamp.from(instant);
    }
}
