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
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRow;
import com.alels.backend.masterdata.service.MasterOptionService;
import com.alels.backend.masterdata.service.MasterOptionService.MasterConfig;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/vehicle-types")
public class VehicleTypeController {
    private static final MasterConfig CONFIG = new MasterConfig("vehicle_types", "type_code", "type_name", "MASTER_VEHICLE_TYPE");
    private final MasterOptionService service;
    public VehicleTypeController(MasterOptionService service) { this.service = service; }
    @GetMapping public List<MasterOptionRow> list(Authentication auth) { return service.list(user(auth), CONFIG); }
    @PostMapping public Map<String, Object> create(Authentication auth, @RequestBody MasterOptionRequest request) { service.create(user(auth), CONFIG, request); return Map.of("success", true); }
    @PutMapping("/{id}") public Map<String, Object> update(Authentication auth, @PathVariable Long id, @RequestBody MasterOptionRequest request) { service.update(user(auth), CONFIG, id, request); return Map.of("success", true); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable Long id) { service.delete(user(auth), CONFIG, id); return Map.of("success", true); }
    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
