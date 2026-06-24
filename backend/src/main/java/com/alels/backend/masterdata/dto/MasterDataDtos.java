package com.alels.backend.masterdata.dto;

public final class MasterDataDtos {
    private MasterDataDtos() {}

    public record MasterOptionRow(
            Long id,
            String code,
            String name,
            String description,
            Boolean active,
            Boolean system,
            Integer sortOrder,
            String createdAt,
            String updatedAt
    ) {}

    public record MasterOptionRequest(
            String code,
            String name,
            String description,
            Boolean active,
            Integer sortOrder
    ) {}

    public record VehicleModelRow(
            Long id,
            Long brandId,
            String brandName,
            String modelCode,
            String modelName,
            String description,
            Boolean active,
            Boolean system,
            Integer sortOrder,
            String createdAt,
            String updatedAt
    ) {}

    public record VehicleModelRequest(
            Long brandId,
            String modelCode,
            String modelName,
            String description,
            Boolean active,
            Integer sortOrder
    ) {}

    public record MasterCountryRow(
            Long id,
            String countryCode,
            String countryName,
            String currency,
            java.math.BigDecimal usdToLocalRate,
            String sourceName,
            String sourceUrl,
            String status,
            String updatedAt
    ) {}

    public record MasterCountryRequest(
            String countryCode,
            String countryName,
            String currency,
            java.math.BigDecimal usdToLocalRate,
            String sourceName,
            String sourceUrl,
            String status
    ) {}
}
