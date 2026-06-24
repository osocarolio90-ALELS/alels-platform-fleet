package com.alels.backend.assetregister.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryCreateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateResponse;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceUpdateRequest;
import com.alels.backend.assetregister.repository.EnergyReferenceRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.security.RoleNormalizer;

@Service
public class EnergyReferenceService {
    private final EnergyReferenceRepository repository;

    public EnergyReferenceService(EnergyReferenceRepository repository) {
        this.repository = repository;
    }

    public List<EnergyReferenceRow> list(JwtUserContext actor, String countryCode) {
        requireAdminOrSuperAdmin(actor);
        return repository.findAll(countryCode);
    }

    public List<EnergyReferenceCountryRow> countries(JwtUserContext actor) {
        requireAdminOrSuperAdmin(actor);
        return repository.findCountries();
    }

    public void createCountry(JwtUserContext actor, EnergyReferenceCountryCreateRequest request) {
        requireSuperAdmin(actor);
        validateCountry(request);
        repository.createCountry(request, actor.userId(), actor.companyId());
    }

    public EnergyReferenceProviderUpdateResponse updateProvider(JwtUserContext actor, EnergyReferenceProviderUpdateRequest request) {
        requireAdminOrSuperAdmin(actor);
        String countryCode = request == null ? null : request.countryCode();
        int rows = repository.syncFromProvider(countryCode, actor.userId(), actor.companyId());
        return new EnergyReferenceProviderUpdateResponse(true, rows, "Harga referensi berhasil disinkronkan dari provider reference store.");
    }

    public void updateManual(JwtUserContext actor, Long id, EnergyReferenceUpdateRequest request) {
        requireSuperAdmin(actor);
        validateReference(request);
        repository.updateManual(id, request, actor.userId(), actor.companyId());
    }

    public void delete(JwtUserContext actor, Long id) {
        requireSuperAdmin(actor);
        repository.softDelete(id, actor.userId(), actor.companyId());
    }

    private void validateCountry(EnergyReferenceCountryCreateRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request wajib diisi.");
        if (isBlank(request.countryCode()) || request.countryCode().trim().length() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country code tidak valid.");
        if (isBlank(request.countryName()) || request.countryName().trim().length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country name tidak valid.");
        if (isBlank(request.currency()) || request.currency().trim().length() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency tidak valid.");
        if (request.usdToLocalRate() == null || isNegative(request.usdToLocalRate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USD to local rate tidak valid.");
        if (request.sourceUrl() != null && request.sourceUrl().trim().length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL terlalu panjang.");
    }

    private void validateReference(EnergyReferenceUpdateRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request wajib diisi.");
        if (request.referencePriceCountry() != null && isNegative(request.referencePriceCountry())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reference country tidak boleh negatif.");
        if (request.referencePriceGlobalUsd() != null && isNegative(request.referencePriceGlobalUsd())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reference global tidak boleh negatif.");
        if (request.providerReferencePriceCountry() != null && isNegative(request.providerReferencePriceCountry())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider reference country tidak boleh negatif.");
        if (request.providerReferencePriceGlobalUsd() != null && isNegative(request.providerReferencePriceGlobalUsd())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider reference global tidak boleh negatif.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    private void requireAdminOrSuperAdmin(JwtUserContext actor) {
        String role = role(actor);
        if (!List.of("SUPERADMIN", "ADMIN").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Harga Referensi hanya untuk SUPERADMIN dan ADMIN.");
        }
    }

    private void requireSuperAdmin(JwtUserContext actor) {
        if (!"SUPERADMIN".equals(role(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN yang boleh mengubah master Harga Referensi.");
        }
    }

    private String role(JwtUserContext actor) {
        return RoleNormalizer.normalize(actor.role());
    }
}
