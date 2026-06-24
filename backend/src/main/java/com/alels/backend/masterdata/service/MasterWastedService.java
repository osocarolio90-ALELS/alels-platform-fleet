package com.alels.backend.masterdata.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.masterdata.dto.MasterWastedDtos.MasterWastedRow;
import com.alels.backend.masterdata.repository.MasterWastedRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@Service
public class MasterWastedService {
    private final MasterWastedRepository repository;

    public MasterWastedService(MasterWastedRepository repository) {
        this.repository = repository;
    }

    public List<MasterWastedRow> list(JwtUserContext user, String category) {
        assertCanView(user);
        return switch (normalize(category)) {
            case "vehicle" -> repository.listVehicleMaster();
            case "device" -> repository.listDeviceMaster();
            case "harga" -> repository.listHargaMaster();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid master wasted category.");
        };
    }

    public void restore(JwtUserContext user, String category, String masterType, Long id) {
        assertCanEdit(user);
        repository.restore(normalize(category), masterType, id, user.userId());
        repository.log(user.userId(), user.companyId(), "MASTER_WASTED", id, "MASTER_WASTED_RESTORE");
    }

    public void permanentDelete(JwtUserContext user, String category, String masterType, Long id) {
        assertCanEdit(user);
        repository.permanentDelete(normalize(category), masterType, id);
        repository.log(user.userId(), user.companyId(), "MASTER_WASTED", id, "MASTER_WASTED_PERMANENT_DELETE");
    }

    private void assertCanView(JwtUserContext user) {
        String role = user.normalizedRole();
        if (!"SUPERADMIN".equals(role) && !"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Master Data Wasted hanya untuk SUPERADMIN dan ADMIN.");
    }

    private void assertCanEdit(JwtUserContext user) {
        if (!"SUPERADMIN".equals(user.normalizedRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permanent action Master Data Wasted hanya untuk SUPERADMIN.");
    }

    private String normalize(String category) { return category == null ? "" : category.trim().toLowerCase(); }
}
