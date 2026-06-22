package com.alels.backend.serverops.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.security.service.SecurityMonitorService;

@RestController
@RequestMapping("/api/server-monitor/security")
public class SecurityMonitorController {

    private final SecurityMonitorService securityMonitorService;

    public SecurityMonitorController(SecurityMonitorService securityMonitorService) {
        this.securityMonitorService = securityMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(securityMonitorService.getOverview());
    }
}
