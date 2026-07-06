package com.alels.backend.assetregister.dto;

import java.util.List;

public final class AssetMoveDtos {
    private AssetMoveDtos() {}

    public record AssetMoveRequest(String assetType, List<Long> assetIds, Long targetCompanyId) {}
    public record AssetMoveRecord(Long id, Long companyId, String identifier) {}
}
