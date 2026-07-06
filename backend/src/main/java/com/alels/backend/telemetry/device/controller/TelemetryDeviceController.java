package com.alels.backend.telemetry.device.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.Overview;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.TcpRequest;
import com.alels.backend.telemetry.device.service.TelemetryDeviceService;

@RestController
@RequestMapping("/api/telemetry/devices")
public class TelemetryDeviceController {
    private final TelemetryDeviceService service;
    public TelemetryDeviceController(TelemetryDeviceService service) { this.service=service; }
    @GetMapping public Overview overview(Authentication authentication) { return service.overview(user(authentication)); }
    @PutMapping("/{id}/tcp") public Map<String,Object> tcp(Authentication authentication,@PathVariable Long id,@RequestBody TcpRequest request) {
        service.setTcp(user(authentication),id,request.enabled());
        return Map.of("success",true);
    }
    private JwtUserContext user(Authentication authentication) { return (JwtUserContext)authentication.getPrincipal(); }
}
