package com.alels.backend.assetregister.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryCreateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceCountryRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateRequest;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceProviderUpdateResponse;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceRow;
import com.alels.backend.assetregister.dto.EnergyReferenceDtos.EnergyReferenceUpdateRequest;
import com.alels.backend.assetregister.service.EnergyReferenceService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/energy-reference-prices")
public class EnergyReferenceController {
    private final EnergyReferenceService service;

    public EnergyReferenceController(EnergyReferenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<EnergyReferenceRow> list(
            Authentication authentication,
            @RequestParam(required = false) String countryCode
    ) {
        return service.list(user(authentication), countryCode);
    }

    @GetMapping("/countries")
    public List<EnergyReferenceCountryRow> countries(Authentication authentication) {
        return service.countries(user(authentication));
    }

    @PostMapping("/countries")
    public Map<String, Object> createCountry(
            Authentication authentication,
            @RequestBody EnergyReferenceCountryCreateRequest request
    ) {
        service.createCountry(user(authentication), request);
        return Map.of("success", true);
    }

    @PostMapping("/update-provider")
    public EnergyReferenceProviderUpdateResponse updateProvider(
            Authentication authentication,
            @RequestBody(required = false) EnergyReferenceProviderUpdateRequest request
    ) {
        return service.updateProvider(user(authentication), request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(Authentication authentication, @PathVariable Long id) {
        service.delete(user(authentication), id);
        return Map.of("success", true);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody EnergyReferenceUpdateRequest request
    ) {
        service.updateManual(user(authentication), id, request);
        return Map.of("success", true);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
