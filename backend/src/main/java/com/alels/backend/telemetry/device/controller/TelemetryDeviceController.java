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
    @GetMapping public Overview overview(
            Authentication authentication,
            @RequestParam(defaultValue="0") Long afterId,
            @RequestParam(defaultValue="50") int limit,
            @RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="ALL") String folder
    ) { return service.overview(user(authentication),afterId,limit,search,folder); }
    @PutMapping("/{id}/tcp") public Map<String,Object> tcp(Authentication authentication,@PathVariable Long id,@RequestBody TcpRequest request) {
        service.setTcp(user(authentication),id,request.enabled());
        return Map.of("success",true);
    }
    private JwtUserContext user(Authentication authentication) { return (JwtUserContext)authentication.getPrincipal(); }
}
