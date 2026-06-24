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

import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRow;
import com.alels.backend.masterdata.service.VehicleModelService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/vehicle-models")
public class VehicleModelController {
    private final VehicleModelService service;
    public VehicleModelController(VehicleModelService service) { this.service = service; }
    @GetMapping public List<VehicleModelRow> list(Authentication auth, @RequestParam(required = false) Long brandId) { return service.list(user(auth), brandId); }
    @PostMapping public Map<String, Object> create(Authentication auth, @RequestBody VehicleModelRequest request) { service.create(user(auth), request); return Map.of("success", true); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody VehicleModelRequest request) { service.update(user(auth), id, request); return Map.of("success", true); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), id); return Map.of("success", true); }
    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
