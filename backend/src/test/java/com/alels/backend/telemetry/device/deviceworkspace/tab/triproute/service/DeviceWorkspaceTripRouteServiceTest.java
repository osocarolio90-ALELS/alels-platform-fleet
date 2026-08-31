package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.audit.AuditLogService;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedEventsRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRouteRange;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRoutesRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedTimeRange;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TelemetryPoint;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;

class DeviceWorkspaceTripRouteServiceTest {
    private final TelemetryDeviceRepository deviceRepository = mock(TelemetryDeviceRepository.class);
    private final DeviceWorkspaceTripRouteRepository repository = mock(DeviceWorkspaceTripRouteRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DeviceWorkspaceTripRouteService service = new DeviceWorkspaceTripRouteService(deviceRepository, repository, auditLogService);
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
        mockTripPoints(points);

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(1, result.trips().size());
        assertEquals("TRIP-1", result.trips().get(0).id());
        assertEquals("TRIP", result.trips().get(0).type());
        assertEquals("COMPLETED", result.trips().get(0).status());
        assertEquals(0, result.routes().size());
    }

    @Test
    void summarizesFuelFromCanonicalFuelUsedAndFuelLevelFields() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        mockTripPoints(List.of(
                fuelPoint(1L, start, 0.0, 1.0, 80.0, 6.0, 100.0),
                fuelPoint(2L, start.plusSeconds(11), 20.0, 1.0, 79.9, 6.0, 100.02),
                fuelPoint(3L, start.plusSeconds(60), 30.0, 1.0, 79.6, 6.0, 100.2),
                fuelPoint(4L, start.plusSeconds(120), 0.0, 0.0, 79.5, 6.0, 100.3)
        ));

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(1, result.trips().size());
        assertEquals(0.3, result.trips().getFirst().fuelConsumption(), 0.001);
        assertEquals(80.0, result.trips().getFirst().fuelStart(), 0.001);
        assertEquals(79.5, result.trips().getFirst().fuelFinish(), 0.001);
    }

    @Test
    void fallsBackToFuelRateWhenFuelUsedCounterResetsInsideTrip() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        mockTripPoints(List.of(
                fuelPoint(1L, start, 0.0, 1.0, 80.0, 6.0, 10.0),
                fuelPoint(2L, start.plusSeconds(11), 20.0, 1.0, 79.9, 6.0, 9.9),
                fuelPoint(3L, start.plusSeconds(60), 30.0, 1.0, 79.6, 6.0, 9.95),
                fuelPoint(4L, start.plusSeconds(120), 0.0, 0.0, 79.5, 6.0, 10.0)
        ));

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(0.2, result.trips().getFirst().fuelConsumption(), 0.001);
    }

    @Test
    void createsStopFromIgnitionOffUntilNextIgnitionOnUsingDeviceTimeOrder() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        mockTripPoints(List.of(
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
        assertEquals("STOP-3", result.trips().get(1).id());
        assertEquals(90, result.trips().get(1).durationSeconds());
        assertEquals("TRIP", result.trips().get(2).type());
    }

    @Test
    void ignoresIgnitionPulseThatDoesNotExceedTenSeconds() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        mockTripPoints(List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(5), 0.0, 1.0),
                point(3L, start.plusSeconds(10), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T00:00:00Z");

        assertEquals(0, result.trips().size());
    }

    @Test
    void preservesSegmentsSeparatedByMoreThanThirtyMinutes() {
        authorized();
        Instant start = Instant.parse("2025-08-15T08:00:00Z");
        mockTripPoints(List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(11), 20.0, 1.0),
                point(3L, start.plusSeconds(60), 0.0, 1.0),
                point(4L, start.plusSeconds(32 * 60), 0.0, 0.0),
                point(5L, start.plusSeconds(33 * 60), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2025-08-15T00:00:00Z", "2025-08-16T00:00:00Z");

        assertEquals(2, result.trips().size());
        assertEquals("STOP-4", result.trips().get(0).id());
        assertEquals("STOP", result.trips().get(0).type());
        assertEquals("COMPLETED", result.trips().get(0).status());
        assertEquals("TRIP-1", result.trips().get(1).id());
        assertEquals("TRIP", result.trips().get(1).type());
        assertEquals("COMPLETED", result.trips().get(1).status());
    }

    @Test
    void preservesDelayedFinalSegmentInsteadOfHidingIt() {
        authorized();
        Instant start = Instant.parse("2025-08-15T08:00:00Z");
        mockTripPoints(List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(11), 20.0, 1.0),
                point(3L, start.plusSeconds(60), 0.0, 1.0)
        ));

        var result = service.trips(user, 11L, "2025-08-15T00:00:00Z", "2025-08-16T00:00:00Z");

        assertEquals(1, result.trips().size());
        assertEquals("TRIP-1", result.trips().get(0).id());
        assertEquals("COMPLETED", result.trips().get(0).status());
    }

    @Test
    void exposesFirstStopPacketImmediatelyAfterLongGap() {
        authorized();
        Instant start = Instant.parse("2025-08-15T08:00:00Z");
        mockTripPoints(List.of(
                point(1L, start, 0.0, 1.0),
                point(2L, start.plusSeconds(11), 20.0, 1.0),
                point(3L, start.plusSeconds(60), 0.0, 1.0),
                point(4L, start.plusSeconds(32 * 60), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2025-08-15T00:00:00Z", "2025-08-16T00:00:00Z");

        assertEquals(2, result.trips().size());
        assertEquals("STOP-4", result.trips().get(0).id());
        assertEquals("STOP", result.trips().get(0).type());
        assertEquals(0, result.trips().get(0).durationSeconds());
    }

    @Test
    void streamsMoreThanLegacyFiftyThousandPointLimitWithoutPayloadTooLarge() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        List<TelemetryPoint> points = LongStream.rangeClosed(1, 60_001)
                .mapToObj(id -> point(id, start.plusSeconds(id), id == 60_001 ? 0.0 : 20.0, id == 60_001 ? 0.0 : 1.0))
                .toList();
        mockTripPoints(points);

        var result = service.trips(user, 11L, "2026-08-11T00:00:00Z", "2026-08-12T23:59:59Z");

        assertEquals(1, result.trips().size());
        assertEquals("TRIP", result.trips().getFirst().type());
        assertEquals(0, result.routes().size());
    }

    @Test
    void keepsInProgressTripIdentityStableWhenNewTelemetryArrives() {
        authorized();
        Instant start = Instant.now().minusSeconds(120);
        mockTripPoints(List.of(
                point(100L, start, 0.0, 1.0),
                point(101L, start.plusSeconds(11), 20.0, 1.0),
                point(102L, start.plusSeconds(60), 25.0, 1.0)
        ));
        var first = service.trips(user, 11L, start.minusSeconds(10).toString(), Instant.now().plusSeconds(10).toString());

        mockTripPoints(List.of(
                point(100L, start, 0.0, 1.0),
                point(101L, start.plusSeconds(11), 20.0, 1.0),
                point(102L, start.plusSeconds(60), 25.0, 1.0),
                point(103L, Instant.now().minusSeconds(1), 22.0, 1.0)
        ));
        var refreshed = service.trips(user, 11L, start.minusSeconds(10).toString(), Instant.now().plusSeconds(10).toString());

        assertEquals("TRIP-100", first.trips().getFirst().id());
        assertEquals("TRIP-100", refreshed.trips().getFirst().id());
        assertEquals("IN_PROGRESS", refreshed.trips().getFirst().status());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsOnlySelectedRouteGeometryAndKeepsItBounded() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        doAnswer(invocation -> {
            BiConsumer<String, TelemetryPoint> consumer = invocation.getArgument(2);
            LongStream.rangeClosed(1, 5_000).forEach(id -> consumer.accept(
                    "TRIP-1", point(id, start.plusSeconds(id), 20.0, 1.0)));
            return null;
        }).when(repository).scanSelectedRoutePoints(eq("359000000000011"), any(), any());

        var result = service.selectedRoutes(user, 11L, new SelectedRoutesRequest(List.of(
                new SelectedRouteRange("TRIP-1", start.toString(), start.plusSeconds(6_000).toString())
        )));

        assertEquals(1, result.routes().size());
        assertEquals("TRIP-1", result.routes().getFirst().tripId());
        assertEquals(true, result.routes().getFirst().track().size() <= 2_000);
    }

    @Test
    void returnsEventsAcrossAllSelectedTripAndStopRanges() {
        authorized();
        Instant start = Instant.parse("2026-08-11T08:00:00Z");
        List<TripEvent> events = List.of(
                new TripEvent(1L, 10L, "Ignition", "Ignition = 1", "INFO", start.plusSeconds(10).toString(), -6.2, 106.8, 0.0),
                new TripEvent(2L, 20L, "Door", "Door = 1", "INFO", start.plusSeconds(120).toString(), -6.3, 106.9, 0.0)
        );
        when(repository.selectedEvents(eq("359000000000011"), eq(20L), any())).thenReturn(events);

        var result = service.selectedEvents(user, 11L, new SelectedEventsRequest(List.of(
                new SelectedTimeRange(start.toString(), start.plusSeconds(60).toString()),
                new SelectedTimeRange(start.plusSeconds(90).toString(), start.plusSeconds(180).toString())
        )));

        assertEquals(2, result.size());
        assertEquals("Ignition", result.getFirst().title());
        assertEquals("Door", result.getLast().title());
    }

    @Test
    void paginatesRawDeviceLogsWithoutTripSegmentation() {
        authorized();
        Instant received = Instant.parse("2026-08-15T10:00:00Z");
        List<TelemetryPoint> points = LongStream.rangeClosed(1, 11)
                .mapToObj(id -> point(id, received.minusSeconds(id), 0.0, 0.0))
                .toList();
        when(repository.logPoints(eq("359000000000011"), any(), any(), eq(null), eq(null), eq(11)))
                .thenReturn(points);
        when(repository.latestTelemetryId(eq("359000000000011"), any(), any())).thenReturn(99L);

        var result = service.logs(user, 11L, "2026-08-15T00:00:00Z", "2026-08-16T00:00:00Z",
                null, null, 10);

        assertEquals(true, result.hasMore());
        assertEquals(10L, result.nextBeforeId());
        assertEquals(points.get(9).receivedAt().toString(), result.nextBeforeTime());
        assertEquals(99L, result.latestTelemetryId());
    }

    @Test
    void preservesTripBeforeOfflineGapWhenPacketsArriveOutOfOrder() {
        authorized();
        Instant start = Instant.parse("2026-08-15T08:40:07Z");
        mockTripPoints(List.of(
                point(699L, start, 0.0, 0.0),
                pointWithNullableIgnition(737L, start.plusSeconds(9 * 60 + 9), 0.0, null),
                point(751L, start.plusSeconds(9 * 60 + 17), 0.0, 1.0),
                point(800L, start.plusSeconds(14 * 60), 0.0, 1.0),
                point(837L, start.plusSeconds(16 * 60 + 21), 0.0, 0.0),
                pointWithNullableIgnition(838L, start.plusSeconds(16 * 60 + 21), 0.0, null),
                point(738L, start.plusSeconds(67 * 60 + 54), 0.0, 0.0)
        ));

        var result = service.trips(user, 11L, "2026-08-15T08:00:00Z", "2026-08-15T10:00:00Z");

        assertEquals(3, result.trips().size());
        assertEquals("TRIP-751", result.trips().get(1).id());
        assertEquals("TRIP", result.trips().get(1).type());
        assertEquals("COMPLETED", result.trips().get(1).status());
    }

    @Test
    void rejectsPermanentHistoryDeletionForNonAdmin() {
        assertThrows(ResponseStatusException.class, () -> service.deleteAllHistory(
                user, 11L, new DeleteDeviceHistoryRequest("359000000000011")));
        verify(repository, never()).deleteAllHistory(any(), any());
    }

    @Test
    void rejectsPermanentHistoryDeletionWhenImeiConfirmationDoesNotMatch() {
        JwtUserContext admin = new JwtUserContext(8L, 20L, "ADMIN", "admin@example.test", 1L);
        when(deviceRepository.accessById(11L, 20L, "ADMIN"))
                .thenReturn(Optional.of(new DeviceAccess(11L, "359000000000011", 20L)));
        assertThrows(ResponseStatusException.class, () -> service.deleteAllHistory(
                admin, 11L, new DeleteDeviceHistoryRequest("wrong-imei")));
        verify(repository, never()).deleteAllHistory(any(), any());
    }

    @Test
    void permanentlyDeletesScopedHistoryForAdminAndWritesAuditLog() {
        JwtUserContext admin = new JwtUserContext(8L, 20L, "ADMIN", "admin@example.test", 1L);
        when(deviceRepository.accessById(11L, 20L, "ADMIN"))
                .thenReturn(Optional.of(new DeviceAccess(11L, "359000000000011", 20L)));
        var deletion = new DeleteDeviceHistoryResponse("359000000000011", "2026-08-15T10:00:00Z",
                10, 20, 30, 2, 4, 10, 1, 1);
        when(repository.deleteAllHistory(eq("359000000000011"), any())).thenReturn(deletion);

        var result = service.deleteAllHistory(admin, 11L, new DeleteDeviceHistoryRequest("359000000000011"));

        assertEquals(78, result.totalDeletedRows());
        verify(auditLogService).logTelemetryHistoryDeletion(8L, 20L, "359000000000011", 78);
    }


    @SuppressWarnings("unchecked")
    private void mockTripPoints(List<TelemetryPoint> points) {
        doAnswer(invocation -> {
            Consumer<TelemetryPoint> consumer = invocation.getArgument(3);
            points.forEach(consumer);
            return null;
        }).when(repository).scanPoints(eq("359000000000011"), any(), any(), any());
    }

    private void authorized() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER"))
                .thenReturn(Optional.of(new DeviceAccess(11L, "359000000000011", 20L)));
    }

    private TelemetryPoint point(Long id, Instant time, double speed, double ignition) {
        return pointWithNullableIgnition(id, time, speed, ignition);
    }

    private TelemetryPoint fuelPoint(Long id, Instant time, double speed, Double ignition,
                                     Double fuelLevel, Double fuelRate, Double fuelUsed) {
        return new TelemetryPoint(id, time, time, -6.19 + id / 10_000.0, 106.82 + id / 10_000.0,
                speed, 10, 12, 10, 0.8, "TELTONIKA_CODEC8E", "GSM", "Karina",
                speed > 0 ? "MOVING" : "STOP", ignition, speed > 0 ? 1.0 : 0.0,
                fuelLevel, fuelRate, fuelUsed);
    }

    private TelemetryPoint pointWithNullableIgnition(Long id, Instant time, double speed, Double ignition) {
        return new TelemetryPoint(id, time, time, -6.19 + id / 10_000.0, 106.82 + id / 10_000.0,
                speed, 10, 12, 10, 0.8, "TELTONIKA_CODEC8E", "GSM", "Karina",
                speed > 0 ? "MOVING" : "STOP", ignition, speed > 0 ? 1.0 : 0.0);
    }
}
