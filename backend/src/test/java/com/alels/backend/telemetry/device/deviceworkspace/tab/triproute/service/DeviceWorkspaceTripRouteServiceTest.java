package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TelemetryPoint;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;

class DeviceWorkspaceTripRouteServiceTest {
    private final TelemetryDeviceRepository deviceRepository = mock(TelemetryDeviceRepository.class);
    private final DeviceWorkspaceTripRouteRepository repository = mock(DeviceWorkspaceTripRouteRepository.class);
    private final DeviceWorkspaceTripRouteService service = new DeviceWorkspaceTripRouteService(deviceRepository, repository);
    private final JwtUserContext user = new JwtUserContext(7L, 20L, "CLIENTUSER", "user@example.test", 1L);

    @Test
    void rejectsDeviceOutsideAuthenticatedScope() {
        when(deviceRepository.accessById(99L, 20L, "CLIENTUSER")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.trips(
                user, 99L, "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z"));
    }

    @Test
    void rejectsRangesLongerThanThirtyOneDays() {
        authorized();
        assertThrows(ResponseStatusException.class, () -> service.trips(
                user, 11L, "2026-06-01T00:00:00Z", "2026-08-02T00:00:00Z"));
    }

    @Test
    void segmentsTripFromConfirmedIgnitionOnUntilIgnitionOff() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        List<TelemetryPoint> points = List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(11), 20.0, 1.0),
                point(3L, start.plusSeconds(60), 30.0, 1.0),
                point(4L, start.plusSeconds(120), 0.0, 0.0)
        );
        when(repository.points(eq("359000000000011"), any(), any())).thenReturn(points);

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(1, result.trips().size());
        assertEquals("1-4", result.trips().get(0).id());
        assertEquals("TRIP", result.trips().get(0).type());
        assertEquals("COMPLETED", result.trips().get(0).status());
        assertEquals(1, result.routes().size());
        assertEquals(4, result.routes().get(0).track().size());
    }

    @Test
    void createsStopFromIgnitionOffUntilNextIgnitionOnUsingDeviceTimeOrder() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        when(repository.points(eq("359000000000011"), any(), any())).thenReturn(List.of(
                point(1L, start, 10.0, 1.0),
                point(2L, start.plusSeconds(11), 20.0, 1.0),
                point(3L, start.plusSeconds(30), 0.0, 0.0),
                point(4L, start.plusSeconds(90), 0.0, 0.0),
                point(5L, start.plusSeconds(120), 0.0, 1.0),
                point(6L, start.plusSeconds(131), 10.0, 1.0),
                point(7L, start.plusSeconds(180), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(3, result.trips().size());
        assertEquals("TRIP", result.trips().get(0).type());
        assertEquals("STOP", result.trips().get(1).type());
        assertEquals("3-5", result.trips().get(1).id());
        assertEquals(90, result.trips().get(1).durationSeconds());
        assertEquals("TRIP", result.trips().get(2).type());
    }

    @Test
    void ignoresIgnitionPulseThatDoesNotExceedTenSeconds() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        when(repository.points(eq("359000000000011"), any(), any())).thenReturn(List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(5), 0.0, 1.0),
                point(3L, start.plusSeconds(10), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(0, result.trips().size());
    }

    private void authorized() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER"))
                .thenReturn(Optional.of(new DeviceAccess(11L, "359000000000011", 20L)));
    }

    private TelemetryPoint point(Long id, Instant time, double speed, double ignition) {
        return new TelemetryPoint(id, time, -6.19 + id / 10_000.0, 106.82 + id / 10_000.0,
                speed, 10, 12, 10, 0.8, "TELTONIKA_CODEC8E", "GSM", "Karina",
                speed > 0 ? "MOVING" : "STOP", ignition, speed > 0 ? 1.0 : 0.0);
    }
}
