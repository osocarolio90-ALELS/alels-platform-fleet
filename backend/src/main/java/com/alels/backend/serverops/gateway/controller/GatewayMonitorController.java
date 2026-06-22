package com.alels.backend.serverops.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.gateway.service.GatewayMonitorService;

@RestController
@RequestMapping("/api/server-monitor/gateway")
public class GatewayMonitorController {

    private final GatewayMonitorService gatewayMonitorService;

    public GatewayMonitorController(GatewayMonitorService gatewayMonitorService) {
        this.gatewayMonitorService = gatewayMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(gatewayMonitorService.getOverview());
    }
}
