package com.alels.backend.serverops.traffic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.traffic.service.TrafficMonitorService;

@RestController
@RequestMapping("/api/server-monitor/traffic")
public class TrafficMonitorController {

    private final TrafficMonitorService trafficMonitorService;

    public TrafficMonitorController(TrafficMonitorService trafficMonitorService) {
        this.trafficMonitorService = trafficMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(trafficMonitorService.getOverview());
    }
}
