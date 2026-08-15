package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto;

import java.util.List;

public final class DeviceWorkspaceTripRouteDtos {
    private DeviceWorkspaceTripRouteDtos() {}

    public record TripSummary(
            String id, String type, String status, String startTime, String endTime,
            long durationSeconds, double distanceKm, double averageSpeed,
            double maximumSpeed, String driverName,
            Double startLatitude, Double startLongitude,
            Double endLatitude, Double endLongitude
    ) {}
    public record TripPoint(Long telemetryId, String occurredAt, Double latitude, Double longitude,
                            Double speed, Integer angle, String movement) {}
    public record TripRouteOverlay(String tripId, List<TripPoint> track) {}
    public record TripListResponse(List<TripSummary> trips, List<TripRouteOverlay> routes) {}
    public record TripEvent(Long id, Long telemetryId, String title, String message, String severity,
                            String occurredAt, Double latitude, Double longitude, Double speed) {}
    public record ParameterValue(String fieldCode, String label, String value, String unit, String category) {}
    public record TripLogRow(Long telemetryId, String imei, String occurredAt, String protocol, String source,
                             Double latitude, Double longitude, Integer altitude, Integer angle,
                             Double speed, Integer satellites, Double hdop, List<ParameterValue> parameters) {}
    public record TripDetailResponse(TripSummary trip, List<TripPoint> track, List<TripEvent> events,
                                     List<TripLogRow> logs) {}
}
