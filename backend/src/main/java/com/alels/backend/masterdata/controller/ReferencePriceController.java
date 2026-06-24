package com.alels.backend.masterdata.controller;

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
import com.alels.backend.masterdata.service.ReferencePriceService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/reference-prices")
public class ReferencePriceController {
    private final ReferencePriceService service;
    public ReferencePriceController(ReferencePriceService service) { this.service = service; }
    @GetMapping public List<EnergyReferenceRow> list(Authentication auth, @RequestParam(required = false) String countryCode) { return service.list(user(auth), countryCode); }
    @GetMapping("/countries") public List<EnergyReferenceCountryRow> countries(Authentication auth) { return service.countries(user(auth)); }
    @PostMapping("/countries") public Map<String, Object> createCountry(Authentication auth, @RequestBody EnergyReferenceCountryCreateRequest request) { service.createCountry(user(auth), request); return Map.of("success", true); }
    @PostMapping("/update-provider") public EnergyReferenceProviderUpdateResponse updateProvider(Authentication auth, @RequestBody(required = false) EnergyReferenceProviderUpdateRequest request) { return service.updateProvider(user(auth), request); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody EnergyReferenceUpdateRequest request) { service.updateManual(user(auth), id, request); return Map.of("success", true); }
    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
