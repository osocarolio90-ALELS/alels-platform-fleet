package com.alels.backend.masterdata.dto;

public final class LicenseMasterDtos {
    private LicenseMasterDtos() {}

    public record LicenseMasterRow(
            Long id,
            String countryCode,
            String countryName,
            String code,
            String name,
            Boolean active,
            String status,
            String createdAt,
            String createdBy,
            String updatedAt
    ) {}

    public record LicenseMasterRequest(
            String countryCode,
            String countryName,
            String name,
            Boolean active
    ) {}
}
