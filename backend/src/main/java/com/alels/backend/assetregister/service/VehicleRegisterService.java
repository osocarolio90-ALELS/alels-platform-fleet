package com.alels.backend.assetregister.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleLookupOption;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRequest;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRow;
import com.alels.backend.assetregister.repository.VehicleRegisterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class VehicleRegisterService {
    private final VehicleRegisterRepository repository;

    public VehicleRegisterService(VehicleRegisterRepository repository) {
        this.repository = repository;
    }

    public List<VehicleRegisterRow> list(JwtUserContext user) {
        return repository.list(user.companyId(), user.normalizedRole());
    }

    public List<VehicleLookupOption> companyOptions(JwtUserContext user) {
        return repository.companyOptions(user.companyId(), user.normalizedRole());
    }

    public Long create(JwtUserContext user, VehicleRegisterRequest request) {
        validateRequest(request, null);
        Long targetCompanyId = request.companyId() == null ? user.companyId() : request.companyId();
        assertCompanyAccess(user, targetCompanyId);
        validateDuplicatePlate(targetCompanyId, request.plateNumber(), null);
        VehicleRegisterRequest normalized = new VehicleRegisterRequest(
                targetCompanyId,
                request.vehicleCode(),
                request.vehicleName(),
                request.plateNumber(),
                request.vehicleTypeId(),
                request.brandId(),
                request.modelId(),
                request.yearManufacture(),
                request.countryCode(),
                request.energyCode(),
                request.ownershipTypeId(),
                request.capacityValue(),
                request.capacityUnitId(),
                request.operationalStatus(),
                request.notes()
        );
        Long id = repository.create(normalized, user.userId());
        repository.log(user.userId(), user.companyId(), id, "VEHICLE_CREATE");
        return id;
    }

    public void update(JwtUserContext user, Long id, VehicleRegisterRequest request) {
        validateRequest(request, id);
        Long targetCompanyId = repository.companyIdByVehicle(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
        assertCompanyAccess(user, targetCompanyId);
        validateDuplicatePlate(targetCompanyId, request.plateNumber(), id);
        VehicleRegisterRequest normalized = new VehicleRegisterRequest(
                targetCompanyId,
                request.vehicleCode(),
                request.vehicleName(),
                request.plateNumber(),
                request.vehicleTypeId(),
                request.brandId(),
                request.modelId(),
                request.yearManufacture(),
                request.countryCode(),
                request.energyCode(),
                request.ownershipTypeId(),
                request.capacityValue(),
                request.capacityUnitId(),
                request.operationalStatus(),
                request.notes()
        );
        repository.update(id, normalized, user.userId());
        repository.log(user.userId(), user.companyId(), id, "VEHICLE_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) {
        assertVehicleAccess(user, id);
        repository.softDelete(id, user.userId());
        repository.log(user.userId(), user.companyId(), id, "VEHICLE_DELETE");
    }

    public void maintenance(JwtUserContext user, Long id, boolean maintenance) {
        assertVehicleAccess(user, id);
        repository.setMaintenance(id, maintenance, user.userId());
        repository.log(user.userId(), user.companyId(), id, maintenance ? "VEHICLE_MAINTENANCE_ON" : "VEHICLE_MAINTENANCE_OFF");
    }


    private void validateRequest(VehicleRegisterRequest request, Long currentVehicleId) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request vehicle wajib diisi.");
        }
        if (isBlank(request.vehicleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle Name wajib diisi.");
        }
        if (request.vehicleName().trim().length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle Name terlalu panjang.");
        }
        if (isBlank(request.plateNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plat Number wajib diisi.");
        }
        if (request.plateNumber().trim().length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plat Number terlalu panjang.");
        }
        if (request.capacityValue() != null && request.capacityValue().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tank Capacity tidak boleh negatif.");
        }
        if (request.yearManufacture() != null && (request.yearManufacture() < 1970 || request.yearManufacture() > java.time.Year.now().getValue() + 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Years tidak valid.");
        }
        if (!repository.existsActive("vehicle_types", request.vehicleTypeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle Type tidak valid.");
        }
        if (!repository.existsActive("vehicle_brands", request.brandId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brand tidak valid.");
        }
        if (!repository.existsActive("vehicle_models", request.modelId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model tidak valid.");
        }
        if (!repository.modelBelongsToBrand(request.modelId(), request.brandId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model tidak sesuai dengan Brand yang dipilih.");
        }
        if (!repository.existsActive("vehicle_ownership_types", request.ownershipTypeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ownership tidak valid.");
        }
        if (!repository.existsActive("vehicle_capacity_units", request.capacityUnitId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit tidak valid.");
        }
    }

    private void validateDuplicatePlate(Long companyId, String plateNumber, Long excludeVehicleId) {
        if (repository.plateExists(companyId, plateNumber, excludeVehicleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plat Number sudah terdaftar di company tersebut.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void assertVehicleAccess(JwtUserContext user, Long vehicleId) {
        Long companyId = repository.companyIdByVehicle(vehicleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
        assertCompanyAccess(user, companyId);
    }

    private void assertCompanyAccess(JwtUserContext user, Long companyId) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke company vehicle tersebut.");
        }
    }
}
