package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRow;
import com.alels.backend.masterdata.repository.MasterOptionRepository;
import com.alels.backend.masterdata.repository.VehicleModelRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class VehicleModelService {
    private final VehicleModelRepository repository;
    private final MasterOptionService guard;
    private final MasterOptionRepository auditRepository;

    public VehicleModelService(VehicleModelRepository repository, MasterOptionService guard, MasterOptionRepository auditRepository) {
        this.repository = repository;
        this.guard = guard;
        this.auditRepository = auditRepository;
    }

    public List<VehicleModelRow> list(JwtUserContext user, Long brandId) {
        guard.assertCanView(user);
        return repository.list(brandId);
    }

    public void create(JwtUserContext user, VehicleModelRequest request) {
        guard.assertCanEdit(user);
        validate(request);
        repository.create(request, user.userId());
        auditRepository.log(user.userId(), user.companyId(), "MASTER_VEHICLE_MODEL", null, "MASTER_VEHICLE_MODEL_CREATE");
    }

    public void update(JwtUserContext user, Long id, VehicleModelRequest request) {
        guard.assertCanEdit(user);
        validate(request);
        repository.update(id, request, user.userId());
        auditRepository.log(user.userId(), user.companyId(), "MASTER_VEHICLE_MODEL", id, "MASTER_VEHICLE_MODEL_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) {
        guard.assertCanEdit(user);
        repository.softDelete(id, user.userId());
        auditRepository.log(user.userId(), user.companyId(), "MASTER_VEHICLE_MODEL", id, "MASTER_VEHICLE_MODEL_DELETE");
    }

    private void validate(VehicleModelRequest request) {
        if (request == null || request.brandId() == null || request.brandId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle Brand wajib dipilih.");
        }
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle Model Name wajib diisi.");
        }
    }
}
