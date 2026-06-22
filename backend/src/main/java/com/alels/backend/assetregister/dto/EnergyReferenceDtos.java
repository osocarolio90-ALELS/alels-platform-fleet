package com.alels.backend.assetregister.dto;

import java.math.BigDecimal;

public final class EnergyReferenceDtos {
    private EnergyReferenceDtos() {}

    public record EnergyReferenceCountryRow(
            Long id,
            String countryCode,
            String countryName,
            String currency,
            BigDecimal usdToLocalRate,
            String sourceName,
            String sourceUrl,
            String providerStatus,
            String providerLastUpdateAt,
            String status,
            String updatedAt
    ) {}

    public record EnergyReferenceCountryCreateRequest(
            String countryCode,
            String countryName,
            String currency,
            BigDecimal usdToLocalRate,
            String sourceName,
            String sourceUrl
    ) {}

    public record EnergyReferenceRow(
            Long id,
            Long energyId,
            String energyCode,
            String energyName,
            String energyGroup,
            String unit,
            String countryCode,
            String countryName,
            String currency,
            BigDecimal referencePriceCountry,
            BigDecimal referencePriceGlobalUsd,
            BigDecimal providerReferencePriceCountry,
            BigDecimal providerReferencePriceGlobalUsd,
            String sourceName,
            String sourceDetail,
            String sourceUrl,
            String providerStatus,
            String providerLastUpdateAt,
            String lastSyncAt,
            String updatedAt,
            String updatedByEmail
    ) {}

    public record EnergyReferenceUpdateRequest(
            BigDecimal referencePriceCountry,
            BigDecimal referencePriceGlobalUsd,
            BigDecimal providerReferencePriceCountry,
            BigDecimal providerReferencePriceGlobalUsd,
            String sourceName,
            String sourceDetail,
            String sourceUrl,
            String providerStatus
    ) {}

    public record EnergyReferenceProviderUpdateRequest(
            String countryCode
    ) {}

    public record EnergyReferenceProviderUpdateResponse(
            boolean success,
            int updatedRows,
            String message
    ) {}
}
