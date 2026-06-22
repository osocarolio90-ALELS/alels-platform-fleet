package com.alels.backend.serverops.storage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.storage.service.StorageMonitorService;

@RestController
@RequestMapping("/api/server-monitor/storage")
public class StorageMonitorController {

    private final StorageMonitorService storageMonitorService;

    public StorageMonitorController(StorageMonitorService storageMonitorService) {
        this.storageMonitorService = storageMonitorService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(storageMonitorService.getOverview());
    }
}
