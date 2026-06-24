package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.masterdata.dto.MasterDataDtos.MasterCountryRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.MasterCountryRow;
import com.alels.backend.masterdata.repository.MasterCountryRepository;
import com.alels.backend.masterdata.repository.MasterOptionRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class MasterCountryService {
    private final MasterCountryRepository repository;
    private final MasterOptionService guard;
    private final MasterOptionRepository auditRepository;

    public MasterCountryService(MasterCountryRepository repository, MasterOptionService guard, MasterOptionRepository auditRepository) {
        this.repository = repository;
        this.guard = guard;
        this.auditRepository = auditRepository;
    }

    public List<MasterCountryRow> list(JwtUserContext user) { guard.assertCanView(user); return repository.list(); }
    public void create(JwtUserContext user, MasterCountryRequest request) { guard.assertCanEdit(user); repository.create(request, user.userId()); auditRepository.log(user.userId(), user.companyId(), "MASTER_COUNTRY", null, "MASTER_COUNTRY_CREATE"); }
    public void update(JwtUserContext user, Long id, MasterCountryRequest request) { guard.assertCanEdit(user); repository.update(id, request); auditRepository.log(user.userId(), user.companyId(), "MASTER_COUNTRY", id, "MASTER_COUNTRY_UPDATE"); }
    public void delete(JwtUserContext user, Long id) { guard.assertCanEdit(user); repository.softDelete(id); auditRepository.log(user.userId(), user.companyId(), "MASTER_COUNTRY", id, "MASTER_COUNTRY_DELETE"); }
}
