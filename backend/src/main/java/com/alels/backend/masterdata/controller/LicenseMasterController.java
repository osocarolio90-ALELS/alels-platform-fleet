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

import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRequest;
import com.alels.backend.masterdata.dto.LicenseMasterDtos.LicenseMasterRow;
import com.alels.backend.masterdata.service.LicenseMasterService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/license-master")
public class LicenseMasterController {
    private final LicenseMasterService service;

    public LicenseMasterController(LicenseMasterService service) {
        this.service = service;
    }

    @GetMapping
    public List<LicenseMasterRow> list(Authentication auth) { return service.list(user(auth)); }

    @GetMapping("/options")
    public List<LicenseMasterRow> options(Authentication auth, @RequestParam(required = false) String countryCode) { return service.options(user(auth), countryCode); }

    @PostMapping
    public Map<String, Object> create(Authentication auth, @RequestBody LicenseMasterRequest request) { return Map.of("success", true, "id", service.create(user(auth), request)); }

    @PutMapping("/{id}")
    public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody LicenseMasterRequest request) { service.update(user(auth), id, request); return Map.of("success", true); }

    @PutMapping("/{id}/active")
    public Map<String, Object> setActive(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Boolean> request) { service.setActive(user(auth), id, Boolean.TRUE.equals(request.get("active"))); return Map.of("success", true); }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
