package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DeviceInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DataParameter;
import com.alels.backend.telemetry.device.deviceworkspace.tab.configuration.datasource.repository.DeviceWorkspaceDataSourceRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository.LatestPacket;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository.ScopedDevice;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

class DeviceWorkspaceTelemetryServiceTest {
    private final TelemetryDeviceRepository deviceRepository = mock(TelemetryDeviceRepository.class);
    private final DeviceWorkspaceTelemetryRepository workspaceRepository = mock(DeviceWorkspaceTelemetryRepository.class);
    private final DeviceWorkspaceDataSourceRepository dataSourceRepository = mock(DeviceWorkspaceDataSourceRepository.class);
    private final DeviceWorkspaceTelemetryService service = new DeviceWorkspaceTelemetryService(
            deviceRepository, workspaceRepository, new ObjectMapper(), dataSourceRepository
    );
    private final JwtUserContext user = new JwtUserContext(7L, 20L, "CLIENTUSER", "user@example.test", 1L);

    @Test
    void resolvesAuthorizedImeiBeforeReadingAnyWorkspaceData() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER")).thenReturn(Optional.of(new DeviceAccess(11L,"359000000000011",20L)));
        when(workspaceRepository.device(11L, "359000000000011",20L)).thenReturn(Optional.of(scopedDevice()));
        when(workspaceRepository.vehicle(11L,20L)).thenReturn(Optional.empty());
        when(workspaceRepository.driver(11L,20L)).thenReturn(Optional.empty());
        when(workspaceRepository.latestPacket("359000000000011")).thenReturn(Optional.empty());
        when(workspaceRepository.configuration(11L, 7L)).thenReturn(Optional.empty());

        var result = service.telemetry(user, 11L);

        assertEquals("359000000000011", result.device().imei());
        assertFalse(result.driver().paired());
        assertFalse(result.vehicle().paired());
        verify(workspaceRepository).latestPacket("359000000000011");
    }

    @Test
    void rejectsADeviceOutsideTheAuthenticatedTenantScope() {
        when(deviceRepository.accessById(99L, 20L, "CLIENTUSER")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.telemetry(user, 99L));
    }

    @Test
    void returnsPersistedVehicleStatusFromTheLatestPacket() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER")).thenReturn(Optional.of(new DeviceAccess(11L,"359000000000011",20L)));
        when(workspaceRepository.device(11L, "359000000000011",20L)).thenReturn(Optional.of(scopedDevice()));
        when(workspaceRepository.vehicle(11L,20L)).thenReturn(Optional.empty());
        when(workspaceRepository.driver(11L,20L)).thenReturn(Optional.empty());
        when(workspaceRepository.latestPacket("359000000000011")).thenReturn(Optional.of(new LatestPacket(
                101L, 7L, "2026-09-06T03:00:00Z", "2026-09-06T03:00:00Z", "CODEC8E", "TCP", "FMC650",
                -6.2, 106.8, 20.0, 90, 10, 12, 0.8, 0, 239, "TRIP", "{}"
        )));
        when(workspaceRepository.configuration(11L, 7L)).thenReturn(Optional.empty());

        var result = service.telemetry(user, 11L);

        assertEquals("TRIP", result.vehicleStatus());
    }

    @Test
    void loadsRecentEventsUsingTheAuthorizedDeviceImei() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER")).thenReturn(Optional.of(new DeviceAccess(11L,"359000000000011",20L)));
        when(workspaceRepository.device(11L, "359000000000011",20L)).thenReturn(Optional.of(scopedDevice()));
        when(workspaceRepository.events("359000000000011", 20L, null, 51)).thenReturn(List.of());

        var result = service.events(user, 11L, null, 50);

        assertEquals(0, result.events().size());
        verify(workspaceRepository).events("359000000000011", 20L, null, 51);
    }

    @Test
    void sparsePacketKeepsTheLastKnownStatusInsteadOfSwitchingToStop() {
        prepareSparsePacket();
        when(workspaceRepository.lastKnownVehicleStatus("359000000000011",
                "2026-09-06T03:00:00Z", "2026-09-06T03:00:01Z")).thenReturn(Optional.of("IDLE"));

        assertEquals("IDLE", service.telemetry(user, 11L).vehicleStatus());
    }

    @Test
    void absenceOfAnyIgnitionHistoryIsExplicitlyUnknown() {
        prepareSparsePacket();
        when(workspaceRepository.lastKnownVehicleStatus("359000000000011",
                "2026-09-06T03:00:00Z", "2026-09-06T03:00:01Z")).thenReturn(Optional.empty());

        assertEquals("UNKNOWN", service.telemetry(user, 11L).vehicleStatus());
    }

    private void prepareSparsePacket() {
        when(deviceRepository.accessById(11L, 20L, "CLIENTUSER"))
                .thenReturn(Optional.of(new DeviceAccess(11L, "359000000000011", 20L)));
        when(workspaceRepository.device(11L, "359000000000011", 20L)).thenReturn(Optional.of(scopedDevice()));
        when(workspaceRepository.latestPacket("359000000000011")).thenReturn(Optional.of(new LatestPacket(
                102L, 8L, "2026-09-06T03:00:01Z", "2026-09-06T03:00:00Z", "ALELS_JSON", "WIFI", "FMC650",
                -6.2, 106.8, 0.0, 90, 10, 12, 0.8, 0, 10500, "UNKNOWN", "{\"10500\":\"11\"}")));
    }

    @Test
    void sparsePacketIncludesLatestPersistedIgnitionEvenIfBrowserMissedTheFullPacket() {
        prepareSparsePacket();
        DataParameter ignition = ignition(0.0);
        when(workspaceRepository.latestIgnitionParameter("359000000000011",
                "2026-09-06T03:00:00Z", "2026-09-06T03:00:01Z")).thenReturn(Optional.of(ignition));

        var result = service.telemetry(user, 11L);

        assertEquals(0.0, result.dataReceived().stream()
                .filter(parameter -> "ignition".equals(parameter.fieldCode())).findFirst().orElseThrow().numericValue());
        assertEquals("STOP", result.vehicleStatus());
    }

    @Test
    void currentRawIgnitionIsNeverReplacedByOlderNormalizedIgnition() {
        prepareSparsePacket();
        when(workspaceRepository.latestPacket("359000000000011")).thenReturn(Optional.of(new LatestPacket(
                102L, 8L, "2026-09-06T03:00:01Z", "2026-09-06T03:00:00Z", "ALELS_JSON", "WIFI", "FMC650",
                -6.2, 106.8, 0.0, 90, 10, 12, 0.8, 0, 239, "STOP", "{\"239\":0}")));
        when(workspaceRepository.latestIgnitionParameter("359000000000011",
                "2026-09-06T03:00:00Z", "2026-09-06T03:00:01Z")).thenReturn(Optional.of(ignition(1.0)));

        var ignitionParameters = service.telemetry(user, 11L).dataReceived().stream()
                .filter(parameter -> "239".equals(parameter.parameterId())).toList();

        assertEquals(1, ignitionParameters.size());
        assertEquals(0.0, ignitionParameters.getFirst().numericValue());
    }

    private DataParameter ignition(double value) {
        return new DataParameter("ignition", "Ignition", String.valueOf(value), value, null,
                null, "239", "OTHER", "ALELS_JSON", "FMC650", 1L);
    }

    private ScopedDevice scopedDevice() {
        return new ScopedDevice(
                new DeviceInfo(11L, "359000000000011", "Teltonika", "FMC650", "ALELS", true, true, null),
                20L
        );
    }
}
