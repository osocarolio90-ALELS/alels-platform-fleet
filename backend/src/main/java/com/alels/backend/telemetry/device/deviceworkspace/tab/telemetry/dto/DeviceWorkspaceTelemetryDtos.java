package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto;

import java.util.List;

public final class DeviceWorkspaceTelemetryDtos {
    private DeviceWorkspaceTelemetryDtos() {}

    public record DeviceInfo(
            Long id, String imei, String brand, String model, String company,
            boolean online, boolean tcpEnabled, String lastSeen
    ) {}

    public record DriverInfo(
            boolean paired, String driverName, String driverId, String employeeId,
            String licenseNumber, String licenseType, String country, String phoneNumber,
            String rfidIbutton, String photoUrl, String status
    ) {}

    public record VehicleInfo(
            boolean paired, String vehicleName, String vehicleCode, String plateNumber,
            String type, String brand, String model, Integer year, String energy,
            String ownership, String capacity, String country, String operationalStatus
    ) {}

    public record PositionInfo(
            Double latitude, Double longitude, Double speed, Integer angle, Integer altitude,
            Integer satellites, Double hdop, String deviceTime, String serverTime
    ) {}

    public record TrackPoint(
            Double latitude, Double longitude, Integer angle, Double speed, String occurredAt
    ) {}

    public record ConnectionInfo(
            Integer signalStrength, Integer satellitesUsed, String gnssStatus, String tcpStatus,
            String protocol, String channel
    ) {}

    public record PacketInfo(Long id, Long sequence, String receivedAt) {}

    public record DataParameter(
            String fieldCode, String label, String value, Double numericValue,
            Boolean booleanValue, String unit, String parameterId, String category,
            String sourceProtocol, String dictionaryCode, Long deviceModelId
    ) {}

    public record RecentEvent(
            Long id, String title, String message, String severity, String occurredAt
    ) {}

    public record InstrumentMapping(
            String slot, String label,
            String primaryFieldCode, String primaryParameterId,
            String fallbackFieldCode, String fallbackParameterId,
            String unit, String icon
    ) {}

    public record WorkspaceConfiguration(
            List<InstrumentMapping> instruments, List<InstrumentMapping> bottomItems
    ) {}

    public record WorkspaceTelemetryResponse(
            DeviceInfo device, DriverInfo driver, VehicleInfo vehicle, PositionInfo position,
            ConnectionInfo connection, PacketInfo packet, List<DataParameter> dataReceived,
            WorkspaceConfiguration configuration, List<TrackPoint> track
    ) {}

    public record EventPage(List<RecentEvent> events, Long nextBeforeId, boolean hasMore) {}
}
