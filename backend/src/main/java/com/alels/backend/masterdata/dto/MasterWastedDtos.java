package com.alels.backend.masterdata.dto;

public final class MasterWastedDtos {
    private MasterWastedDtos() {}

    public record MasterWastedRow(
            String itemType,
            Long id,
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
