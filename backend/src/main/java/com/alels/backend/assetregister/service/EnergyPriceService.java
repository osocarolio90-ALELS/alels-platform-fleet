package com.alels.backend.assetregister.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyCountryOption;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceRow;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceUpdateRequest;
import com.alels.backend.assetregister.repository.EnergyPriceRepository;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.security.RoleNormalizer;

@Service
public class EnergyPriceService {
    private final EnergyPriceRepository repository;

    public EnergyPriceService(EnergyPriceRepository repository) {
        this.repository = repository;
    }

    public List<EnergyPriceRow> list(JwtUserContext actor, String countryCode) {
        requireAssetRegisterAccess(actor);
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        repository.ensureCompanyEnergyPrices(actor.companyId(), normalizedCountryCode);
        return repository.findByCompany(actor.companyId(), normalizedCountryCode);
    }

    public List<EnergyCountryOption> countries(JwtUserContext actor) {
        requireAssetRegisterAccess(actor);
        return repository.findActiveCountries();
    }

    public void update(JwtUserContext actor, Long id, EnergyPriceUpdateRequest request) {
        requireAssetRegisterAccess(actor);
        requireAdminOrSuperAdmin(actor);
        Long targetCompanyId = repository.companyIdForPrice(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Harga energy tidak ditemukan."));
        if (!repository.canAccessCompany(targetCompanyId, actor.companyId(), role(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses harga energy ditolak.");
        }
        validate(request);
        repository.updatePrice(id, request, actor.userId());
        repository.log(actor.userId(), actor.companyId(), "ENERGY_PRICE", id, "UPDATE_ENERGY_PRICE", "{\"source\":\"MANUAL\"}");
    }

    private void validate(EnergyPriceUpdateRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request wajib diisi.");
        if (request.priceEnergy() != null && isNegative(request.priceEnergy())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price Energy tidak boleh negatif.");
        if (request.countryCode() != null && request.countryCode().trim().length() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country code terlalu panjang.");
        if (request.currency() != null && request.currency().trim().length() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency terlalu panjang.");
        if (request.country() != null && request.country().trim().length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country terlalu panjang.");
    }

    private boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "ID";
        }
        String normalized = countryCode.trim().toUpperCase();
        if (normalized.length() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country code terlalu panjang.");
        }
        return normalized;
    }

    private void requireAssetRegisterAccess(JwtUserContext actor) {
        String role = role(actor);
        if (!List.of("SUPERADMIN", "ADMIN", "OWNER", "MANAGER", "TECHUSER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role tidak memiliki akses Asset Register.");
        }
        if (actor.companyId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company user tidak valid.");
        }
    }

    private void requireAdminOrSuperAdmin(JwtUserContext actor) {
        String role = role(actor);
        if (!List.of("SUPERADMIN", "ADMIN").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya SUPERADMIN dan ADMIN yang boleh mengubah Harga Energy.");
        }
    }

    private String role(JwtUserContext actor) {
        return RoleNormalizer.normalize(actor.role());
    }
}
