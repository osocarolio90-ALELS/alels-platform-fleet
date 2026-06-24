package com.alels.backend.assetregister.dto;

public final class AssetWastedDtos {
    private AssetWastedDtos() {}

    public record AssetWastedRow(
            String itemType,
            Long id,
            Long companyId,
            String companyName,
            String name,
            String code,
            String extra,
            String status,
            String deletedAt,
            Long deletedBy,
            String deletedByEmail,
            String deletePermanentAt,
            Integer remainingDays,
            String deletedReason
    ) {}
}
