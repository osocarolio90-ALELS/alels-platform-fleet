package com.alels.gateway.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeUtilTest {

    @Test
    void parsesAlelsJsonTimestampWithoutOffsetAsUtc() {
        assertEquals(
                Instant.parse("2026-08-11T15:25:00Z"),
                TimeUtil.parseDeviceTimeUtc("2026-08-11 15:25:00")
        );
    }

    @Test
    void preservesCodecTimestampWithUtcOffset() {
        assertEquals(
                Instant.parse("2026-08-11T15:25:00Z"),
                TimeUtil.parseDeviceTimeUtc("2026-08-11T15:25:00Z")
        );
    }

    @Test
    void rejectsInvalidTimestamp() {
        assertNull(TimeUtil.parseDeviceTimeUtc("not-a-timestamp"));
    }
}
