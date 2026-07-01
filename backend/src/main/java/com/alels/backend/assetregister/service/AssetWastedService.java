package com.alels.backend.assetregister.service;

import java.util.List;
import java.util.Locale;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.AssetWastedDtos.AssetWastedRow;
import com.alels.backend.assetregister.repository.AssetWastedRepository;
import com.alels.backend.assetregister.repository.DriverRegisterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.shared.storage.SquarePhotoStorageService;

@Service
public class AssetWastedService {
    private final AssetWastedRepository repository;
    private final DriverRegisterRepository driverRepository;
    private final SquarePhotoStorageService photoStorage;
    private final Path driverPhotoDir;

    public AssetWastedService(
            AssetWastedRepository repository,
            DriverRegisterRepository driverRepository,
            SquarePhotoStorageService photoStorage,
            @Value("${alels.upload.driver-photo-dir:uploads/driver-photos}") String driverPhotoDir
    ) {
        this.repository = repository;
        this.driverRepository = driverRepository;
        this.photoStorage = photoStorage;
        this.driverPhotoDir = Path.of(driverPhotoDir).toAbsolutePath().normalize();
    }

    public List<AssetWastedRow> list(JwtUserContext user, String itemType) {
        return repository.findWasted(user.companyId(), user.normalizedRole(), normalizeItemType(itemType));
    }

    public void restore(JwtUserContext user, String itemType, Long id) {
        String normalizedType = normalizeItemType(itemType);
        Long companyId = repository.companyIdIncludingDeleted(normalizedType, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, normalizedType + " wasted item not found."));
        assertCompanyAccess(user, companyId);
        repository.restore(normalizedType, id, user.userId());
        repository.log(user.userId(), user.companyId(), id, normalizedType + "_RESTORE_FROM_WASTED");
    }

    public void permanentDelete(JwtUserContext user, String itemType, Long id) {
        String normalizedType = normalizeItemType(itemType);
        requirePermanentDeleteRole(user);
        Long companyId = repository.companyIdIncludingDeleted(normalizedType, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, normalizedType + " wasted item not found."));
        assertCompanyAccess(user, companyId);
        String driverPhoto = "DRIVER".equals(normalizedType) ? driverRepository.photoFilenameIncludingDeleted(id) : null;
        repository.permanentDelete(normalizedType, id);
        if ("DRIVER".equals(normalizedType)) photoStorage.deleteQuietly(driverPhotoDir, driverPhoto);
        repository.log(user.userId(), user.companyId(), id, normalizedType + "_PERMANENT_DELETE");
    }

    private void assertCompanyAccess(JwtUserContext user, Long companyId) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke wasted asset tersebut.");
        }
    }

    private void requirePermanentDeleteRole(JwtUserContext user) {
        String role = normalizeRole(user.normalizedRole());
        if (!"SUPERADMIN".equals(role) && !"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permanent delete hanya diizinkan untuk SUPERADMIN dan OWNER.");
        }
    }

    private String normalizeItemType(String itemType) {
        String normalized = itemType == null ? "VEHICLE" : itemType.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        return switch (normalized) {
            case "DEVICE" -> "DEVICE";
            case "DRIVER" -> "DRIVER";
            default -> "VEHICLE";
        };
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}
