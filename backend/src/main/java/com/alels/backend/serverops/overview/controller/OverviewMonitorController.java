package com.alels.backend.serverops.overview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.overview.service.OverviewMonitorService;

@RestController
@RequestMapping("/api/server-monitor/overview")
public class OverviewMonitorController {

    private final OverviewMonitorService overviewMonitorService;

    public OverviewMonitorController(OverviewMonitorService overviewMonitorService) {
        this.overviewMonitorService = overviewMonitorService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(overviewMonitorService.getOverview());
    }
}
