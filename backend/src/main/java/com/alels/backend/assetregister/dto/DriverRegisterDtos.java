package com.alels.backend.assetregister.dto;

public final class DriverRegisterDtos {
    private DriverRegisterDtos() {}

    public record DriverRegisterRow(
            Long id,
            Long companyId,
            String companyName,
            String driverId,
            String employeeId,
            String driverName,
            String licenseNumber,
            String countryCode,
            String countryName,
            Long licenseMasterId,
            String licenseType,
            String phoneNumber,
            String rfidIbutton,
            String photoUrl,
            String status,
            String createdAt,
            String createdBy
    ) {}

    public record DriverRegisterRequest(
            Long companyId,
            String driverId,
            String employeeId,
            String driverName,
            String licenseNumber,
            String countryCode,
            Long licenseMasterId,
            String phoneNumber,
            String rfidIbutton,
            String status
    ) {}

    public record DriverLookupOption(
            Long id,
            String label,
            String value,
            String extra
    ) {}
}
