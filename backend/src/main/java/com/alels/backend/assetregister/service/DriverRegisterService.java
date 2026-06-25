package com.alels.backend.assetregister.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverLookupOption;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRequest;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRow;
import com.alels.backend.assetregister.repository.DriverRegisterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class DriverRegisterService {
    private final DriverRegisterRepository repository;

    public DriverRegisterService(DriverRegisterRepository repository) { this.repository = repository; }

    public List<DriverRegisterRow> list(JwtUserContext user) { return repository.list(user.companyId(), user.normalizedRole()); }
    public List<DriverLookupOption> companyOptions(JwtUserContext user) { return repository.companyOptions(user.companyId(), user.normalizedRole()); }

    public Long create(JwtUserContext user, DriverRegisterRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request driver wajib diisi.");
        Long targetCompanyId = request.companyId() == null ? user.companyId() : request.companyId();
        DriverRegisterRequest normalized = new DriverRegisterRequest(targetCompanyId, request.driverId(), request.employeeId(), request.driverName(), request.licenseNumber(), request.countryCode(), request.licenseMasterId(), request.phoneNumber(), request.rfidIbutton(), request.status());
        validate(normalized, null);
        assertCompanyAccess(user, targetCompanyId);
        Long id = repository.create(normalized, user.userId());
        repository.log(user.userId(), user.companyId(), id, "DRIVER_CREATE");
        return id;
    }

    public void update(JwtUserContext user, Long id, DriverRegisterRequest request) {
        Long currentCompanyId = repository.companyIdByDriver(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        assertCompanyAccess(user, currentCompanyId);
        Long targetCompanyId = request.companyId() == null ? currentCompanyId : request.companyId();
        validate(new DriverRegisterRequest(targetCompanyId, request.driverId(), request.employeeId(), request.driverName(), request.licenseNumber(), request.countryCode(), request.licenseMasterId(), request.phoneNumber(), request.rfidIbutton(), request.status()), id);
        assertCompanyAccess(user, targetCompanyId);
        repository.update(id, new DriverRegisterRequest(targetCompanyId, request.driverId(), request.employeeId(), request.driverName(), request.licenseNumber(), request.countryCode(), request.licenseMasterId(), request.phoneNumber(), request.rfidIbutton(), request.status()), user.userId());
        repository.log(user.userId(), user.companyId(), id, "DRIVER_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) { assertDriverAccess(user, id); repository.softDelete(id, user.userId()); repository.log(user.userId(), user.companyId(), id, "DRIVER_DELETE"); }
    public void setStatus(JwtUserContext user, Long id, String status) { assertDriverAccess(user, id); repository.setStatus(id, status, user.userId()); repository.log(user.userId(), user.companyId(), id, "DRIVER_" + status); }

    private void validate(DriverRegisterRequest request, Long excludeId) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request driver wajib diisi.");
        if (request.companyId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company wajib diisi.");
        if (blank(request.driverId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver ID wajib diisi.");
        if (blank(request.driverName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver Name wajib diisi.");
        if (blank(request.licenseNumber())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "License Number wajib diisi.");
        if (blank(request.countryCode())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country wajib diisi.");
        if (!repository.licenseMasterExists(request.licenseMasterId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "License Type tidak valid.");
        if (blank(request.phoneNumber())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone Number wajib diisi.");
        if (repository.driverIdExists(request.companyId(), request.driverId(), excludeId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver ID sudah terdaftar.");
        if (repository.licenseNumberExists(request.companyId(), request.licenseNumber(), excludeId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "License Number sudah terdaftar.");
    }

    private void assertDriverAccess(JwtUserContext user, Long id) {
        Long companyId = repository.companyIdByDriver(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        assertCompanyAccess(user, companyId);
    }

    private void assertCompanyAccess(JwtUserContext user, Long companyId) {
        if (!repository.canAccessCompany(companyId, user.companyId(), user.normalizedRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke company driver tersebut.");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
