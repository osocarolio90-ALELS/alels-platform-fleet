package com.alels.backend.telemetry.device.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TelemetryDeviceDtos {
    private TelemetryDeviceDtos() {}

    public record DeviceRow(
            Long id, String imei, String brand, String model,
            String vehicleModel, String vehicleType, String plateNumber,
            String energyType, BigDecimal referencePrice,
            String driverName, String driverPhone, String driverLicense,
            String vehicleStatus, boolean tcpEnabled, boolean connected,
            Long groupId, String groupName, boolean groupDeleted,
            String company, String lastUpdated
    ) {}

    public record GroupFolder(Long id, String name, int deviceCount, boolean deleted) {}
    public record Overview(
            List<GroupFolder> groups, List<GroupFolder> wastedGroups, List<DeviceRow> devices,
            long totalDevices, long filteredDevices, long ungroupedDevices, Long nextCursor, boolean hasMore
    ) {}
    public record TcpRequest(boolean enabled) {}
}
