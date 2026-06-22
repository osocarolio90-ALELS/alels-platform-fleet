package com.alels.backend.serverops.aiops.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.aiops.service.AiOpsMonitorService;

@RestController
@RequestMapping("/api/server-monitor/ai-ops")
public class AiOpsMonitorController {

    private final AiOpsMonitorService aiOpsMonitorService;

    public AiOpsMonitorController(AiOpsMonitorService aiOpsMonitorService) {
        this.aiOpsMonitorService = aiOpsMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(aiOpsMonitorService.getOverview());
    }
}
