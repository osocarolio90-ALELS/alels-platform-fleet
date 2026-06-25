package com.alels.backend.masterdata.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.masterdata.dto.MasterWastedDtos.MasterWastedRow;
import com.alels.backend.masterdata.service.MasterWastedService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/master-data/wasted")
public class MasterWastedController {
    private final MasterWastedService service;

    public MasterWastedController(MasterWastedService service) { this.service = service; }

    @GetMapping("/{itemType}") public List<MasterWastedRow> list(Authentication auth, @PathVariable String itemType) { return service.list(user(auth), itemType); }
    @PostMapping("/{itemType}/{id}/restore") public Map<String, Object> restore(Authentication auth, @PathVariable String itemType, @PathVariable Long id) { service.restore(user(auth), itemType, id); return Map.of("success", true); }
    @PostMapping("/{itemType}/{id}/permanent-delete") public Map<String, Object> permanentDelete(Authentication auth, @PathVariable String itemType, @PathVariable Long id) { service.permanentDelete(user(auth), itemType, id); return Map.of("success", true); }

    private JwtUserContext user(Authentication auth) { return (JwtUserContext) auth.getPrincipal(); }
}
