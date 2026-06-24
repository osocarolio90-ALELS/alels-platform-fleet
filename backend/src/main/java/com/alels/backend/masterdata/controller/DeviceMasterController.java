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

import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceBrandRow;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceMasterRequest;
import com.alels.backend.masterdata.dto.DeviceMasterDtos.DeviceModelRow;
import com.alels.backend.masterdata.service.DeviceMasterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/device-master")
public class DeviceMasterController {
    private final DeviceMasterService service;
    public DeviceMasterController(DeviceMasterService service) { this.service = service; }

    @GetMapping("/brands") public List<DeviceBrandRow> brands(Authentication auth) { return service.listBrands(user(auth)); }
    @PostMapping("/brands") public Map<String, Object> createBrand(Authentication auth, @RequestBody DeviceMasterRequest request) { return Map.of("success", true, "id", service.createBrand(user(auth), request)); }
    @PutMapping("/brands/{id}") public Map<String, Object> updateBrand(Authentication auth, @PathVariable Long id, @RequestBody DeviceMasterRequest request) { service.updateBrand(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/brands/{id}") public Map<String, Object> deleteBrand(Authentication auth, @PathVariable Long id) { service.deleteBrand(user(auth), id); return Map.of("success", true); }

    @GetMapping("/models") public List<DeviceModelRow> models(Authentication auth, @RequestParam(required = false) Long brandId) { return service.listModels(user(auth), brandId); }
    @PostMapping("/models") public Map<String, Object> createModel(Authentication auth, @RequestBody DeviceMasterRequest request) { return Map.of("success", true, "id", service.createModel(user(auth), request)); }
    @PutMapping("/models/{id}") public Map<String, Object> updateModel(Authentication auth, @PathVariable Long id, @RequestBody DeviceMasterRequest request) { service.updateModel(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/models/{id}") public Map<String, Object> deleteModel(Authentication auth, @PathVariable Long id) { service.deleteModel(user(auth), id); return Map.of("success", true); }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
