package com.alels.gateway.netty;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GatewayHotPathArchitectureTest {

    @Test
    void telemetryHandlerDoesNotUseDatabaseRepositories() throws Exception {
        Path source = Path.of(
                "src/main/java/com/alels/gateway/netty/NettyDeviceChannelHandler.java"
        );
        String handler = Files.readString(source);

        assertFalse(handler.contains("TcpLogRepository"));
        assertFalse(handler.contains("RawPacketService"));
        assertFalse(handler.contains("DeviceRepository"));
        assertFalse(handler.contains("DeviceStatusRepository"));
        assertFalse(handler.contains("DevicePresenceRepository"));
        assertFalse(handler.contains("DeviceReceiveStatusRepository"));
        assertFalse(handler.contains("DeviceModelResolver"));
    }
}
