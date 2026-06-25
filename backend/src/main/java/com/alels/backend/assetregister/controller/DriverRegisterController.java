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
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverLookupOption;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRequest;
import com.alels.backend.assetregister.dto.DriverRegisterDtos.DriverRegisterRow;
import com.alels.backend.assetregister.service.DriverRegisterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/drivers")
public class DriverRegisterController {
    private final DriverRegisterService service;

    public DriverRegisterController(DriverRegisterService service) { this.service = service; }

    @GetMapping public List<DriverRegisterRow> list(Authentication auth) { return service.list(user(auth)); }
    @GetMapping("/company-options") public List<DriverLookupOption> companyOptions(Authentication auth) { return service.companyOptions(user(auth)); }
    @PostMapping public Map<String, Object> create(Authentication auth, @RequestBody DriverRegisterRequest request) { return Map.of("success", true, "id", service.create(user(auth), request)); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody DriverRegisterRequest request) { service.update(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }
    @PutMapping("/{id}/suspend") public Map<String, Object> suspend(Authentication auth, @PathVariable Long id) { service.setStatus(user(auth), id, "SUSPENDED"); return Map.of("success", true); }
    @PutMapping("/{id}/activate") public Map<String, Object> activate(Authentication auth, @PathVariable Long id) { service.setStatus(user(auth), id, "ACTIVE"); return Map.of("success", true); }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
