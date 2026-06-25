package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRequest;
import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRow;
import com.alels.backend.masterdata.repository.LicenseMasterRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class LicenseMasterService {
    private final LicenseMasterRepository repository;

    public LicenseMasterService(LicenseMasterRepository repository) {
        this.repository = repository;
    }

    public List<LicenseMasterRow> list(JwtUserContext user) {
        assertAuthenticated(user);
        return repository.list(false);
    }

    public List<LicenseMasterRow> options(JwtUserContext user, String countryCode) {
        assertAuthenticated(user);
        return repository.listActiveByCountry(countryCode);
    }

    public Long create(JwtUserContext user, LicenseMasterRequest request) {
        assertCanEdit(user);
        validate(request);
        Long id = repository.create(request, user.userId());
        repository.log(user.userId(), user.companyId(), id, "LICENSE_MASTER_CREATE");
        return id;
    }

    public void update(JwtUserContext user, Long id, LicenseMasterRequest request) {
        assertCanEdit(user);
        validate(request);
        repository.update(id, request, user.userId());
        repository.log(user.userId(), user.companyId(), id, "LICENSE_MASTER_UPDATE");
    }

    public void delete(JwtUserContext user, Long id) {
        assertCanEdit(user);
        repository.softDelete(id, user.userId());
        repository.log(user.userId(), user.companyId(), id, "LICENSE_MASTER_DELETE");
    }

    public void setActive(JwtUserContext user, Long id, boolean active) {
        assertCanEdit(user);
        repository.setActive(id, active, user.userId());
        repository.log(user.userId(), user.companyId(), id, active ? "LICENSE_MASTER_ACTIVATE" : "LICENSE_MASTER_INACTIVATE");
    }

    private void validate(LicenseMasterRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "License request wajib diisi.");
        if ((request.countryName() == null || request.countryName().isBlank()) && (request.countryCode() == null || request.countryCode().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country wajib diisi.");
        if (request.name() == null || request.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "License name wajib diisi.");
    }

    private void assertAuthenticated(JwtUserContext user) {
        if (user == null || user.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized.");
    }

    private void assertCanEdit(JwtUserContext user) {
        String role = user == null ? "" : user.normalizedRole();
        if (!"SUPERADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN yang bisa mengubah License Master.");
    }
}
