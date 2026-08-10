package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.EventPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.WorkspaceConfiguration;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.WorkspaceTelemetryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.service.DeviceWorkspaceTelemetryService;

@RestController
@RequestMapping("/api/telemetry/devices/{deviceId}/workspace/telemetry")
public class DeviceWorkspaceTelemetryController {
    private final DeviceWorkspaceTelemetryService service;

    public DeviceWorkspaceTelemetryController(DeviceWorkspaceTelemetryService service) {
        this.service = service;
    }

    @GetMapping
    public WorkspaceTelemetryResponse telemetry(Authentication authentication, @PathVariable Long deviceId) {
        return service.telemetry(user(authentication), deviceId);
    }

    @GetMapping("/events")
    public EventPage events(
            Authentication authentication,
            @PathVariable Long deviceId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return service.events(user(authentication), deviceId, beforeId, limit);
    }

    @PutMapping("/configuration")
    public WorkspaceConfiguration saveConfiguration(
            Authentication authentication,
            @PathVariable Long deviceId,
            @RequestBody WorkspaceConfiguration configuration
    ) {
        return service.saveConfiguration(user(authentication), deviceId, configuration);
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
