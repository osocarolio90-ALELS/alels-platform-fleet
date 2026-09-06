package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto;

import java.util.List;

public final class DeviceWorkspaceTripRouteDtos {
    private DeviceWorkspaceTripRouteDtos() {}

    public record TripSummary(
            String id, String type, String status, String startTime, String endTime,
            long durationSeconds, double distanceKm, double averageSpeed,
            double maximumSpeed, String driverName, String vehiclePlateNumber,
            Double operationCost, Double fuelCost, Double roadCost, String costCurrency,
            Double fuelConsumption, Double fuelStart, Double fuelFinish,
            Double startLatitude, Double startLongitude,
            Double endLatitude, Double endLongitude
    ) {
        public TripSummary(
                String id, String type, String status, String startTime, String endTime,
                long durationSeconds, double distanceKm, double averageSpeed,
                double maximumSpeed, String driverName,
                Double operationCost, Double fuelCost, Double roadCost, String costCurrency,
                Double fuelConsumption, Double fuelStart, Double fuelFinish,
                Double startLatitude, Double startLongitude,
                Double endLatitude, Double endLongitude
        ) {
            this(id, type, status, startTime, endTime, durationSeconds, distanceKm, averageSpeed,
                    maximumSpeed, driverName, "-", operationCost, fuelCost, roadCost, costCurrency,
                    fuelConsumption, fuelStart, fuelFinish, startLatitude, startLongitude, endLatitude, endLongitude);
        }
    }
    public record TripInstrumentSnapshot(
            Double engineRpm, Double fuelLevel, Double fuelConsumption, String fuelConsumptionUnit,
            Double batterySoc, Double batteryConsumptionKw,
            Double gasLevelPressure, String gasLevelPressureUnit,
            Double gasConsumption, String gasConsumptionUnit
    ) {}
    public record TripPoint(Long telemetryId, String occurredAt, Double latitude, Double longitude,
                            Double speed, Integer angle, String movement, TripInstrumentSnapshot instruments) {}
    public record TripRouteOverlay(String tripId, List<TripPoint> track) {}
    public record TripListResponse(List<TripSummary> trips, List<TripRouteOverlay> routes, String energyGroup) {}
    public record SelectedRouteRange(String tripId, String from, String to) {}
    public record SelectedRoutesRequest(List<SelectedRouteRange> ranges) {}
    public record SelectedRoutesResponse(List<TripRouteOverlay> routes) {}
    public record TripEvent(Long id, Long telemetryId, String title, String message, String severity,
                            String occurredAt, Double latitude, Double longitude, Double speed,
                            String locationSource) {
        public TripEvent(Long id, Long telemetryId, String title, String message, String severity,
                         String occurredAt, Double latitude, Double longitude, Double speed) {
            this(id, telemetryId, title, message, severity, occurredAt, latitude, longitude, speed,
                    telemetryId == null ? "EVENT_RECORDED_GPS" : "TRIGGER_TELEMETRY_GPS");
        }
    }
    public record ParameterValue(String fieldCode, String label, String value, String unit, String category) {}
    public record TripLogRow(Long telemetryId, String imei, String occurredAt, String receivedAt, String protocol, String source,
                             String driverName, String vehiclePlateNumber,
                             Double latitude, Double longitude, Integer altitude, Integer angle,
                             Double speed, Integer satellites, Double hdop, List<ParameterValue> parameters) {}
    public record SelectedTimeRange(String from, String to) {}
    public record SelectedEventsRequest(List<SelectedTimeRange> ranges) {}
    public record SelectedLogsRequest(List<SelectedTimeRange> ranges, Integer page, Integer size, List<Long> telemetryIds) {}
    public record SelectedDeviceLogPage(List<TripLogRow> rows, int page, int size,
                                        long totalRows, int totalPages, Long latestTelemetryId) {}
    public record PlaybackTelemetryRow(Long telemetryId, Double speed, List<ParameterValue> parameters) {}
    public record DeviceLogPage(List<TripLogRow> rows, boolean hasMore,
                                String nextBeforeTime, Long nextBeforeId, Long latestTelemetryId) {}
    public record LatestDeviceLog(Long latestTelemetryId) {}
    public record DeleteDeviceHistoryRequest(String imeiConfirmation) {}
    public record DeleteDeviceHistoryResponse(
            String imei, String resetAt, int telemetryRows, int ioRows,
            int normalizedRows, int alertRows, int eventRows, int rawPacketRows, int latestPositionRows, int presenceRows
    ) {
        public int totalDeletedRows() {
            return telemetryRows + ioRows + normalizedRows + alertRows + eventRows + rawPacketRows + latestPositionRows + presenceRows;
        }
    }
    public record TripDetailResponse(TripSummary trip, List<TripPoint> track, List<TripEvent> events,
                                     List<TripLogRow> logs) {}

    public record InstrumentSourceProfile(
            Long id, String name, String rpmSource, String speedSource, String levelSource,
            String consumptionSource, String odometerSource, boolean companyDefault
    ) {}
    public record InstrumentSourceProfileList(List<InstrumentSourceProfile> profiles, Long effectiveProfileId) {}
    public record InstrumentSourceOption(String fieldCode, String label, String unit, String category) {}
    public record SaveInstrumentSourceProfileRequest(
            String name, String rpmSource, String speedSource, String levelSource,
            String consumptionSource, String odometerSource, Boolean applyAll
    ) {}
    public record ApplyInstrumentSourceProfileRequest(Long profileId, Boolean applyAll) {}
}
