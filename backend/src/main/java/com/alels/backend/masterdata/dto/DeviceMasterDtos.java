package com.alels.backend.masterdata.dto;

public final class DeviceMasterDtos {
    private DeviceMasterDtos() {}

    public record DeviceBrandRow(
            Long id,
            String brandCode,
            String brandName,
            String description,
            Boolean active,
            Boolean system,
            Integer sortOrder,
            String createdAt,
            String createdBy,
            String updatedAt
    ) {}

    public record DeviceModelRow(
            Long id,
            Long brandId,
            String brandCode,
            String brandName,
            String modelCode,
            String modelName,
            String protocolCode,
            String parserCode,
            String dictionaryCode,
            String description,
            Boolean active,
            Boolean system,
            Integer sortOrder,
            String createdAt,
            String createdBy,
            String updatedAt
    ) {}

    public record DeviceMasterRequest(
            Long brandId,
            String brandCode,
            String brandName,
            String modelCode,
            String modelName,
            String protocolCode,
            String parserCode,
            String dictionaryCode,
            String description,
            Boolean active,
            Integer sortOrder
    ) {}
}
