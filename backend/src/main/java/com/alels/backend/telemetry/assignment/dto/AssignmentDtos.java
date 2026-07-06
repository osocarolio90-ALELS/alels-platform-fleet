package com.alels.backend.telemetry.assignment.dto;

public final class AssignmentDtos {
    private AssignmentDtos() {}

    public record AssignmentLookupOption(
            Long id,
            String label,
            String code,
            String extra,
            Long companyId,
            String companyName
    ) {}

    public record AssetAssignmentRow(
            Long id,
            Long companyId,
            String companyName,
            Long vehicleId,
            String vehicleName,
            String plateNumber,
            Long deviceId,
            String deviceLabel,
            String deviceImei,
            Long driverAssignmentId,
            Long driverId,
            String driverCode,
            String driverName,
            String licenseNumber,
            String rfidIbutton,
            String assignmentStatus,
            String assignedAt,
            String assignedBy
    ) {}

    public record AssetAssignmentRequest(
            Long companyId,
            Long vehicleId,
            Long deviceId,
            Long driverId,
            String notes
    ) {}

    public record VehicleDeviceAssignmentRow(
            Long id,
            Long companyId,
            String companyName,
            Long vehicleId,
            String vehicleName,
            String plateNumber,
            Long deviceId,
            String deviceLabel,
            String deviceImei,
            String assignmentStatus,
            String assignedAt,
            String assignedBy
    ) {}

    public record VehicleDeviceAssignmentRequest(
            Long companyId,
            Long vehicleId,
            Long deviceId,
            String notes
    ) {}

    public record DriverManualAssignmentRow(
            Long id,
            Long companyId,
            String companyName,
            Long vehicleId,
            String vehicleName,
            String plateNumber,
            Long deviceId,
            String deviceLabel,
            String deviceImei,
            Long driverId,
            String driverCode,
            String driverName,
            String licenseNumber,
            String rfidIbutton,
            String assignmentStatus,
            String assignedAt,
            String assignedBy
    ) {}

    public record DriverManualAssignmentRequest(
            Long companyId,
            Long vehicleId,
            Long deviceId,
            Long driverId,
            String notes
    ) {}
}
