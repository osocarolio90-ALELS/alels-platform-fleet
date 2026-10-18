package com.alels.gateway.service;

import com.alels.gateway.repository.NormalizedFieldRepository.VehicleStatusSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleStatusResolverTest {
    private static final List<VehicleStatusSource> SOURCES = List.of(
            new VehicleStatusSource("239", "ignition", 1, 0));

    @Test
    void ignitionTransitionsUseGpsSpeedWithoutRequiringASpeedIo() {
        assertEquals("STOP", VehicleStatusResolver.resolve(Map.of("239", 0), 0, SOURCES));
        assertEquals("IDLE", VehicleStatusResolver.resolve(Map.of("239", 1), 0, SOURCES));
        assertEquals("IDLE", VehicleStatusResolver.resolve(Map.of("239", 1), 5, SOURCES));
        assertEquals("TRIP", VehicleStatusResolver.resolve(Map.of("239", 1), 6, SOURCES));
        assertEquals("STOP", VehicleStatusResolver.resolve(Map.of("239", 0), 0, SOURCES));
    }

    @Test
    void configuredSourcesAndScalingRemainAuthoritative() {
        var sources = List.of(new VehicleStatusSource("custom_ignition", "ignition", 1, 0),
                new VehicleStatusSource("custom_speed", "speed", 0.1, 0));
        assertEquals("TRIP", VehicleStatusResolver.resolve(
                Map.of("custom_ignition", true, "custom_speed", 70, "239", 0), 0, sources));
        assertEquals("IDLE", VehicleStatusResolver.resolve(
                Map.of("custom_ignition", true, "custom_speed", 0), 70, sources));
    }

    @Test
    void sparseOrInvalidIgnitionDoesNotFabricateAStop() {
        assertEquals("UNKNOWN", VehicleStatusResolver.resolve(Map.of("10500", "11"), 0, SOURCES));
        assertEquals("UNKNOWN", VehicleStatusResolver.resolve(Map.of("239", "NaN"), 0, SOURCES));
        assertEquals("UNKNOWN", VehicleStatusResolver.resolve(Map.of("239", 0.5), 0, SOURCES));
        assertEquals("IDLE", VehicleStatusResolver.resolve(Map.of("239", true), 0, SOURCES));
        assertEquals("STOP", VehicleStatusResolver.resolve(Map.of("239", false), 0, SOURCES));
    }
}
