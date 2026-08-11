package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceBrandRow;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceMasterRequest;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceModelRow;
import com.alels.backend.masterdata.repository.DeviceMasterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class DeviceMasterService {
    private final DeviceMasterRepository repository;

    public DeviceMasterService(DeviceMasterRepository repository) {
        this.repository = repository;
    }

    public List<DeviceBrandRow> listBrands(JwtUserContext user) { assertCanView(user); return repository.listBrands(); }
    public List<DeviceModelRow> listModels(JwtUserContext user, Long brandId) { assertCanView(user); return repository.listModels(brandId); }

    public Long createBrand(JwtUserContext user, DeviceMasterRequest request) {
        assertCanEdit(user); validateBrand(request); Long id = repository.createBrand(request, user.userId()); repository.log(user.userId(), user.companyId(), "DEVICE_BRAND", id, "DEVICE_BRAND_CREATE"); return id;
    }
    public void updateBrand(JwtUserContext user, Long id, DeviceMasterRequest request) {
        assertCanEdit(user); validateBrand(request); repository.updateBrand(id, request, user.userId()); repository.log(user.userId(), user.companyId(), "DEVICE_BRAND", id, "DEVICE_BRAND_UPDATE");
    }
    public void deleteBrand(JwtUserContext user, Long id) {
        assertCanEdit(user); repository.softDeleteBrand(id, user.userId()); repository.log(user.userId(), user.companyId(), "DEVICE_BRAND", id, "DEVICE_BRAND_DELETE");
    }
    @Transactional
    public Long createModel(JwtUserContext user, DeviceMasterRequest request) {
        assertCanEdit(user);
        validateModel(request);
        Long id = repository.createModel(request, user.userId());
        assertHasActiveDictionary(id);
        repository.log(user.userId(), user.companyId(), "DEVICE_MODEL", id, "DEVICE_MODEL_CREATE");
        return id;
    }
    @Transactional
    public void updateModel(JwtUserContext user, Long id, DeviceMasterRequest request) {
        assertCanEdit(user);
        validateModel(request);
        repository.updateModel(id, request, user.userId());
        assertHasActiveDictionary(id);
        repository.log(user.userId(), user.companyId(), "DEVICE_MODEL", id, "DEVICE_MODEL_UPDATE");
    }
    public void deleteModel(JwtUserContext user, Long id) {
        assertCanEdit(user); repository.softDeleteModel(id, user.userId()); repository.log(user.userId(), user.companyId(), "DEVICE_MODEL", id, "DEVICE_MODEL_DELETE");
    }

    private void assertCanView(JwtUserContext user) {
        if (user == null || user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User tidak valid.");
        }
        // Device master options are global read-only data used by Device Register.
        // All authenticated roles may read them; write access remains restricted in assertCanEdit().
    }
    private void assertCanEdit(JwtUserContext user) {
        if (!"SUPERADMIN".equals(user.normalizedRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN yang bisa mengubah Device Master.");
    }
    private void validateBrand(DeviceMasterRequest request) {
        if (request == null || request.brandName() == null || request.brandName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device Brand wajib diisi.");
    }
    private void validateModel(DeviceMasterRequest request) {
        if (request == null || request.brandId() == null || !repository.brandExists(request.brandId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device Brand tidak valid.");
        if (request.modelName() == null || request.modelName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device Model wajib diisi.");
    }
    private void assertHasActiveDictionary(Long deviceModelId) {
        if (!repository.hasActiveDictionary(deviceModelId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Device Model belum memiliki dictionary aktif. Tambahkan dictionary yang didukung sebelum menyimpan Device Master.");
        }
    }
}
