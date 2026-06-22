package com.alels.backend.serverops.database.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.database.service.DatabaseMonitorService;

@RestController
@RequestMapping("/api/server-monitor/database")
public class DatabaseMonitorController {

    private final DatabaseMonitorService databaseMonitorService;

    public DatabaseMonitorController(DatabaseMonitorService databaseMonitorService) {
        this.databaseMonitorService = databaseMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(databaseMonitorService.getOverview());
    }
}
