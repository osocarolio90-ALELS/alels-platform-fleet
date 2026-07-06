package com.alels.backend.assetregister.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.AssetMoveDtos.AssetMoveRecord;
import com.alels.backend.assetregister.dto.AssetMoveDtos.AssetMoveRequest;
import com.alels.backend.assetregister.repository.AssetMoveRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class AssetMoveService {
    private static final String MIXED_COMPANY_MESSAGE =
            "Bulk move hanya dapat dilakukan untuk asset dari company yang sama. Silakan filter berdasarkan company terlebih dahulu.";

    private final AssetMoveRepository repository;

    public AssetMoveService(AssetMoveRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int move(JwtUserContext user, AssetMoveRequest request) {
        assertMoveRole(user);
        if (request == null || request.assetIds() == null || request.assetIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset wajib dipilih.");
        }
        if (request.targetCompanyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target company wajib dipilih.");
        }

        String type = normalizeType(request.assetType());
        List<Long> ids = new LinkedHashSet<>(request.assetIds()).stream().toList();
        List<AssetMoveRecord> assets = repository.findAssets(type, ids);
        if (assets.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Satu atau lebih asset tidak ditemukan atau sudah dihapus.");
        }
        if (assets.stream().map(AssetMoveRecord::companyId).distinct().count() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MIXED_COMPANY_MESSAGE);
        }

        for (AssetMoveRecord asset : assets) {
            assertCompanyAccess(user, asset.companyId(), "source");
        }
        assertCompanyAccess(user, request.targetCompanyId(), "target");

        for (AssetMoveRecord asset : assets) {
            List<String> dependencies = repository.dependencies(type, asset.id());
            if (!dependencies.isEmpty()) {
                String detail = String.join(", ", dependencies);
                String message = switch (type) {
                    case "DEVICE" -> "Device IMEI " + asset.identifier() + " tidak dapat dipindahkan karena masih memiliki dependency aktif: " + detail + ". Lepaskan dependency terlebih dahulu.";
                    case "VEHICLE" -> "Vehicle " + asset.identifier() + " tidak dapat dipindahkan karena masih memiliki dependency aktif: " + detail + ". Lepaskan dependency terlebih dahulu.";
                    default -> "Driver " + asset.identifier() + " tidak dapat dipindahkan karena masih memiliki dependency aktif: " + detail + ". Lepaskan dependency terlebih dahulu.";
                };
                throw new ResponseStatusException(HttpStatus.CONFLICT, message);
            }
        }

        for (AssetMoveRecord asset : assets) {
            repository.move(type, asset.id(), request.targetCompanyId(), user.userId());
            repository.logMove(user.userId(), user.companyId(), type, asset.id(), asset.companyId(), request.targetCompanyId());
        }
        return assets.size();
    }

    private void assertMoveRole(JwtUserContext user) {
        String role = user.normalizedRole();
        if (!List.of("SUPERADMIN", "ADMIN", "OWNER", "MANAGER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role Anda tidak diizinkan memindahkan asset.");
        }
    }

    private void assertCompanyAccess(JwtUserContext user, Long companyId, String direction) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke " + direction + " company.");
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DEVICE", "VEHICLE", "DRIVER").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset type tidak valid.");
        }
        return normalized;
    }
}
