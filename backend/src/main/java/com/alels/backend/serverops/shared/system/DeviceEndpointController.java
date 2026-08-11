package com.alels.backend.serverops.shared.system;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.config.GatewayPublicEndpoint;
import com.alels.backend.serverops.shared.config.GatewayPublicEndpoint.Endpoint;

@RestController
@RequestMapping("/api/system")
public class DeviceEndpointController {
    private final GatewayPublicEndpoint gatewayEndpoint;

    public DeviceEndpointController(GatewayPublicEndpoint gatewayEndpoint) {
        this.gatewayEndpoint = gatewayEndpoint;
    }

    @GetMapping("/device-endpoint")
    public Endpoint deviceEndpoint() {
        return gatewayEndpoint.configured().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Public gateway host/port belum dikonfigurasi."
        ));
    }
}
