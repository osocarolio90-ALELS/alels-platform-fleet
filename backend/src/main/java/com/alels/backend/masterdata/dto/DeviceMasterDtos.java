package com.alels.backend.masterdata.dto;

import java.util.List;

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
            String updatedAt,
            List<AvlDefinitionRequest> avlDefinitions
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
            Integer sortOrder,
            List<AvlDefinitionRequest> avlDefinitions
    ) {}

    public record AvlDefinitionRequest(
            String avlId,
            String name,
            String unit,
            String valueType,
            Double multiplier,
            String category
    ) {}
}
