package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRow;
import com.alels.backend.masterdata.repository.MasterOptionRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class MasterOptionService {
    private final MasterOptionRepository repository;

    public MasterOptionService(MasterOptionRepository repository) {
        this.repository = repository;
    }

    public List<MasterOptionRow> list(JwtUserContext user, MasterConfig config) {
        assertCanView(user);
        return repository.list(config.table(), config.codeColumn(), config.nameColumn());
    }

    public void create(JwtUserContext user, MasterConfig config, MasterOptionRequest request) {
        assertCanEdit(user);
        repository.create(config.table(), config.codeColumn(), config.nameColumn(), request, user.userId());
        repository.log(user.userId(), user.companyId(), config.targetType(), null, config.targetType() + "_CREATE");
    }

    public void update(JwtUserContext user, MasterConfig config, Long id, MasterOptionRequest request) {
        assertCanEdit(user);
        repository.update(config.table(), config.codeColumn(), config.nameColumn(), id, request, user.userId());
        repository.log(user.userId(), user.companyId(), config.targetType(), id, config.targetType() + "_UPDATE");
    }

    public void delete(JwtUserContext user, MasterConfig config, Long id) {
        assertCanEdit(user);
        repository.softDelete(config.table(), id, user.userId());
        repository.log(user.userId(), user.companyId(), config.targetType(), id, config.targetType() + "_DELETE");
    }

    public void assertCanView(JwtUserContext user) {
        if (user == null || user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User tidak valid.");
        }
        // Reference master data is global read-only data used by registration forms.
        // All authenticated roles may read it; write access remains restricted in assertCanEdit().
    }

    public void assertCanEdit(JwtUserContext user) {
        if (!"SUPERADMIN".equals(user.normalizedRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN yang bisa mengubah Master Data.");
        }
    }

    public record MasterConfig(String table, String codeColumn, String nameColumn, String targetType) {}
}
