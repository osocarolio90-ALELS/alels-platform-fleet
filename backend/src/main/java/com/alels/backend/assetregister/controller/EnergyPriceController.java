package com.alels.backend.assetregister.controller;

import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyCountryOption;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceRow;
import com.alels.backend.assetregister.dto.EnergyPriceDtos.EnergyPriceUpdateRequest;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assetregister.service.EnergyPriceService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/energy-prices")
public class EnergyPriceController {
    private final EnergyPriceService service;

    public EnergyPriceController(EnergyPriceService service) {
        this.service = service;
    }

    @GetMapping
    public List<EnergyPriceRow> list(
            Authentication authentication,
            @RequestParam(required = false) String countryCode
    ) {
        return service.list(user(authentication), countryCode);
    }

    @GetMapping("/countries")
    public List<EnergyCountryOption> countries(Authentication authentication) {
        return service.countries(user(authentication));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody EnergyPriceUpdateRequest request
    ) {
        service.update(user(authentication), id, request);
        return Map.of("success", true);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
