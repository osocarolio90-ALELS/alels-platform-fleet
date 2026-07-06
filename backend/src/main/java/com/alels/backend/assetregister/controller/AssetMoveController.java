package com.alels.backend.assetregister.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alels.backend.assetregister.dto.AssetMoveDtos.AssetMoveRequest;
import com.alels.backend.assetregister.service.AssetMoveService;
import com.alels.backend.serverops.shared.security.JwtUserContext;

@RestController
@RequestMapping("/api/asset-register/move")
public class AssetMoveController {
    private final AssetMoveService service;

    public AssetMoveController(AssetMoveService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> move(Authentication authentication, @RequestBody AssetMoveRequest request) {
        return Map.of("success", true, "moved", service.move(user(authentication), request));
    }

    private JwtUserContext user(Authentication authentication) {
        return (JwtUserContext) authentication.getPrincipal();
    }
}
