package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripDetailResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripListResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service.DeviceWorkspaceTripRouteService;

@RestController
@RequestMapping("/api/telemetry/devices/{deviceId}/workspace/trip-route")
public class DeviceWorkspaceTripRouteController {
    private final DeviceWorkspaceTripRouteService service;
    public DeviceWorkspaceTripRouteController(DeviceWorkspaceTripRouteService service) { this.service = service; }

    @GetMapping
    public TripListResponse trips(Authentication authentication, @PathVariable Long deviceId,
                                  @RequestParam String from, @RequestParam String to) {
        return service.trips(user(authentication), deviceId, from, to);
    }

    @GetMapping("/detail")
    public TripDetailResponse detail(Authentication authentication, @PathVariable Long deviceId,
                                     @RequestParam String from, @RequestParam String to) {
        return service.detail(user(authentication), deviceId, from, to);
    }

    private JwtUserContext user(Authentication authentication) { return (JwtUserContext) authentication.getPrincipal(); }
}
