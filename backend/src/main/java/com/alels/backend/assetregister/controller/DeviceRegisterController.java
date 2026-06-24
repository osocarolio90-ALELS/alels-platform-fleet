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

import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceLookupOption;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRequest;
import com.alels.backend.assetregister.dto.DeviceRegisterDtos.DeviceRegisterRow;
import com.alels.backend.assetregister.service.DeviceRegisterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/devices")
public class DeviceRegisterController {
    private final DeviceRegisterService service;
    public DeviceRegisterController(DeviceRegisterService service) { this.service = service; }

    @GetMapping public List<DeviceRegisterRow> list(Authentication auth) { return service.list(user(auth)); }
    @GetMapping("/company-options") public List<DeviceLookupOption> companyOptions(Authentication auth) { return service.companyOptions(user(auth)); }
    @GetMapping("/brand-options") public List<DeviceLookupOption> brandOptions() { return service.brandOptions(); }
    @GetMapping("/model-options") public List<DeviceLookupOption> modelOptions(@RequestParam(required = false) Long brandId) { return service.modelOptions(brandId); }
    @PostMapping public Map<String, Object> create(Authentication auth, @RequestBody DeviceRegisterRequest request) { return Map.of("success", true, "id", service.create(user(auth), request)); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody DeviceRegisterRequest request) { service.update(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
