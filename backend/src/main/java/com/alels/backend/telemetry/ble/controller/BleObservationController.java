package com.alels.backend.telemetry.ble.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.ble.service.BleObservationService;
import com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.Page;

@RestController
@RequestMapping("/api/ble")
public class BleObservationController {
    private final BleObservationService service;
    public BleObservationController(BleObservationService service) { this.service = service; }
    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
    @GetMapping("/gateways/{id}/received-beacons")
    public com.alels.backend.telemetry.ble.dto.EyeBeaconDtos.ReceivedPage receivedCards(
            Authentication auth, @PathVariable long id, @RequestParam(defaultValue = "0") long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.receivedCards(user(auth),id,beforeId,limit);
    }
    @GetMapping("/gateways/{id}/beacons")
    public Page cards(Authentication auth, @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.cards(user(auth), id, afterId, limit);
    }
    @GetMapping("/beacons")
    public List<Map<String, Object>> beacons(Authentication auth,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.beacons(user(auth), afterId, limit);
    }
    @GetMapping("/beacons/{id}/observations")
    public List<Map<String, Object>> history(Authentication auth, @PathVariable long id,
            @RequestParam(defaultValue = "0") long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.history(user(auth), id, beforeId, limit);
    }
    @GetMapping("/gateways/{id}/observations")
    public List<Map<String, Object>> gateway(Authentication auth, @PathVariable long id,
            @RequestParam(defaultValue = "0") long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.gatewayHistory(user(auth), id, beforeId, limit);
    }
    @GetMapping("/measurements/latest")
    public List<Map<String, Object>> latest(Authentication auth,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.latest(user(auth), afterId, limit);
    }
}
