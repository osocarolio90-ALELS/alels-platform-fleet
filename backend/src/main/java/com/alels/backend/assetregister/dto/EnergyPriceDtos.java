package com.alels.backend.assetregister.dto;

import java.math.BigDecimal;

public final class EnergyPriceDtos {
    private EnergyPriceDtos() {}

    public record EnergyCountryOption(
            String countryCode,
            String countryName,
            String currency,
            BigDecimal usdToLocalRate,
            String sourceName,
            String sourceUrl
    ) {}

    public record EnergyPriceRow(
            Long id,
            Long companyId,
            String companyName,
            Long energyId,
            String energyCode,
            String energyName,
            String energyGroup,
            String unit,
            String vehicleUsage,
            String countryCode,
            String country,
            String currency,
            BigDecimal priceEnergy,
            BigDecimal referencePriceCountryIdr,
            BigDecimal referencePriceGlobalUsd,
            BigDecimal fxRateToIdr,
            String priceSource,
            String referenceSource,
            String sourceUrl,
            String lastReferenceUpdateAt,
            Boolean manualOverride,
            String updatedAt,
            String updatedByEmail
    ) {}

    public record EnergyPriceUpdateRequest(
            String countryCode,
            String country,
            String currency,
            BigDecimal priceEnergy,
            BigDecimal referencePriceCountryIdr,
            BigDecimal referencePriceGlobalUsd,
            BigDecimal fxRateToIdr,
            String referenceSource,
            Boolean manualOverride
    ) {}
}
