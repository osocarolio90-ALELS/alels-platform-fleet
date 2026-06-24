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

import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleLookupOption;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRequest;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRow;
import com.alels.backend.assetregister.service.VehicleRegisterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/vehicles")
public class VehicleRegisterController {
    private final VehicleRegisterService service;

    public VehicleRegisterController(VehicleRegisterService service) {
        this.service = service;
    }

    @GetMapping
    public List<VehicleRegisterRow> list(Authentication authentication) {
        return service.list(user(authentication));
    }

    @GetMapping("/company-options")
    public List<VehicleLookupOption> companyOptions(Authentication authentication) {
        return service.companyOptions(user(authentication));
    }

    @PostMapping
    public Map<String, Object> create(Authentication authentication, @RequestBody VehicleRegisterRequest request) {
        Long id = service.create(user(authentication), request);
        return Map.of("success", true, "id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(Authentication authentication, @PathVariable Long id, @RequestBody VehicleRegisterRequest request) {
        service.update(user(authentication), id, request);
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(Authentication authentication, @PathVariable Long id) {
        service.delete(user(authentication), id);
        return Map.of("success", true);
    }

    @PostMapping("/{id}/maintenance")
    public Map<String, Object> maintenance(Authentication authentication, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean active) {
        service.maintenance(user(authentication), id, active);
        return Map.of("success", true);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
