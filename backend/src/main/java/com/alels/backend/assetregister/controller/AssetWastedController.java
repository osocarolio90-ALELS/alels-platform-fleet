package com.alels.backend.assetregister.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assetregister.dto.AssetWastedDtos.AssetWastedRow;
import com.alels.backend.assetregister.service.AssetWastedService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/wasted")
public class AssetWastedController {
    private final AssetWastedService service;

    public AssetWastedController(AssetWastedService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssetWastedRow> list(Authentication authentication, @RequestParam(defaultValue = "VEHICLE") String type) {
        return service.list(user(authentication), type);
    }

    @PostMapping("/vehicles/{id}/restore")
    public Map<String, Object> restoreVehicle(Authentication authentication, @PathVariable Long id) {
        service.restore(user(authentication), "VEHICLE", id);
        return Map.of("success", true);
    }

    @PostMapping("/vehicles/{id}/permanent-delete")
    public Map<String, Object> permanentDeleteVehicle(Authentication authentication, @PathVariable Long id) {
        service.permanentDelete(user(authentication), "VEHICLE", id);
        return Map.of("success", true);
    }

    @PostMapping("/devices/{id}/restore")
    public Map<String, Object> restoreDevice(Authentication authentication, @PathVariable Long id) {
        service.restore(user(authentication), "DEVICE", id);
        return Map.of("success", true);
    }

    @PostMapping("/devices/{id}/permanent-delete")
    public Map<String, Object> permanentDeleteDevice(Authentication authentication, @PathVariable Long id) {
        service.permanentDelete(user(authentication), "DEVICE", id);
        return Map.of("success", true);
    }

    @PostMapping("/drivers/{id}/restore")
    public Map<String, Object> restoreDriver(Authentication authentication, @PathVariable Long id) {
        service.restore(user(authentication), "DRIVER", id);
        return Map.of("success", true);
    }

    @PostMapping("/drivers/{id}/permanent-delete")
    public Map<String, Object> permanentDeleteDriver(Authentication authentication, @PathVariable Long id) {
        service.permanentDelete(user(authentication), "DRIVER", id);
        return Map.of("success", true);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
