package com.alels.backend.assetregister.service;

import java.util.List;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverLookupOption;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRequest;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRow;
import com.alels.backend.assetregister.repository.DriverRegisterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.shared.storage.SquarePhotoStorageService;

@Service
public class DriverRegisterService {
    private final DriverRegisterRepository repository;
    private final SquarePhotoStorageService photoStorage;
    private final Path uploadDir;

    public DriverRegisterService(
            DriverRegisterRepository repository,
            SquarePhotoStorageService photoStorage,
            @Value("${alels.upload.driver-photo-dir:uploads/driver-photos}") String uploadDir
    ) {
        this.repository = repository;
        this.photoStorage = photoStorage;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

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
        validate(new DriverRegisterRequest(currentCompanyId, request.driverId(), request.employeeId(), request.driverName(), request.licenseNumber(), request.countryCode(), request.licenseMasterId(), request.phoneNumber(), request.rfidIbutton(), request.status()), id);
        repository.update(id, new DriverRegisterRequest(currentCompanyId, request.driverId(), request.employeeId(), request.driverName(), request.licenseNumber(), request.countryCode(), request.licenseMasterId(), request.phoneNumber(), request.rfidIbutton(), request.status()), user.userId());
        repository.log(user.userId(), user.companyId(), id, "DRIVER_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) {
        assertDriverAccess(user, id);
        repository.softDelete(id, user.userId());
        repository.log(user.userId(), user.companyId(), id, "DRIVER_DELETE");
    }
    public void setStatus(JwtUserContext user, Long id, String status) { assertDriverAccess(user, id); repository.setStatus(id, status, user.userId()); repository.log(user.userId(), user.companyId(), id, "DRIVER_" + status); }

    public String updatePhoto(JwtUserContext user, Long id, MultipartFile photo) {
        assertDriverAccess(user, id);
        String previous = repository.photoFilename(id);
        var stored = photoStorage.store(uploadDir, "driver-" + id, photo, 5_000_000L);
        try {
            repository.setPhotoFilename(id, stored.fileName(), user.userId());
        } catch (RuntimeException ex) {
            photoStorage.deleteQuietly(uploadDir, stored.fileName());
            throw ex;
        }
        photoStorage.deleteQuietly(uploadDir, previous);
        repository.log(user.userId(), user.companyId(), id, "DRIVER_PHOTO_UPDATE");
        return stored.fileName();
    }

    public void removePhoto(JwtUserContext user, Long id) {
        assertDriverAccess(user, id);
        String previous = repository.photoFilename(id);
        repository.removePhotoFilename(id, user.userId());
        photoStorage.deleteQuietly(uploadDir, previous);
        repository.log(user.userId(), user.companyId(), id, "DRIVER_PHOTO_REMOVE");
    }

    public Path uploadDir() { return uploadDir; }

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
