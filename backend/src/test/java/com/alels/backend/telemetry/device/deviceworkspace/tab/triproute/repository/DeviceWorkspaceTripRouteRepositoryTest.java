package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository;

import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TelemetryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DeviceWorkspaceTripRouteRepositoryTest {
    @Test
    void logsUseEachHistoricalPacketsStatusRatherThanTheCurrentDeviceStatus() {
        var repository = new DeviceWorkspaceTripRouteRepository(mock(JdbcTemplate.class));
        Instant at = Instant.parse("2026-10-18T03:00:00Z");
        var rows = repository.logs("863719066421737", at, at.plusSeconds(3), List.of(
                point(1L, at, "STOP"), point(2L, at.plusSeconds(1), "IDLE"),
                point(3L, at.plusSeconds(2), "TRIP"), point(4L, at.plusSeconds(3), null)));

        assertEquals(List.of("STOP", "IDLE", "TRIP", "UNKNOWN"),
                rows.stream().map(row -> row.vehicleStatus()).toList());
    }

    private TelemetryPoint point(long id, Instant at, String status) {
        return new TelemetryPoint(id, at, at, -6.2, 106.8, 0.0, 0, 10, 12, 0.8,
                "ALELS_JSON", "WIFI", null, status, null, null);
    }
}
