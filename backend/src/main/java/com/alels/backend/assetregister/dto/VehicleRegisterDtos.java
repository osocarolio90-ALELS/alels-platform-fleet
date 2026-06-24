package com.alels.backend.assetregister.dto;

import java.math.BigDecimal;

public final class VehicleRegisterDtos {
    private VehicleRegisterDtos() {}

    public record VehicleRegisterRow(
            Long id,
            Long companyId,
            String companyName,
            String vehicleCode,
            String vehicleName,
            String plateNumber,
            Long vehicleTypeId,
            String vehicleType,
            Long brandId,
            String brand,
            Long modelId,
            String model,
            Integer yearManufacture,
            String energyCode,
            String energyName,
            BigDecimal energyPriceSnapshot,
            String energyCurrency,
            String countryCode,
            String countryName,
            Long ownershipTypeId,
            String ownership,
            BigDecimal capacityValue,
            Long capacityUnitId,
            String capacityUnit,
            String operationalStatus,
            String createdAt,
            String createdByEmail,
            String updatedAt
    ) {}

    public record VehicleRegisterRequest(
            Long companyId,
            String vehicleCode,
            String vehicleName,
            String plateNumber,
            Long vehicleTypeId,
            Long brandId,
            Long modelId,
            Integer yearManufacture,
            String countryCode,
            String energyCode,
            Long ownershipTypeId,
            BigDecimal capacityValue,
            Long capacityUnitId,
            String operationalStatus,
            String notes
    ) {}

    public record VehicleLookupOption(
            Long id,
            String label,
            String code,
            String extra
    ) {}
}
