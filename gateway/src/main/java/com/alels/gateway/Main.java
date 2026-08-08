package com.alels.gateway;

import com.alels.gateway.admission.service.DeviceAdmissionRegistry;
import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.netty.NettyTcpServer;
import com.alels.gateway.observability.service.GatewayHealthServer;
import com.alels.gateway.observability.service.GatewayRuntimeMetrics;
import com.alels.gateway.poller.PendingCommandPoller;
import com.alels.gateway.service.DictionaryStartupImporter;
import com.alels.gateway.service.ProtocolRegistryResolver;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("========================================");
        System.out.println("ALELS TECH INDONESIA");
        System.out.println("========================================");

        if (!DatabaseConfig.testConnection()) {
            throw new IllegalStateException("Database startup check failed");
        }

        DictionaryStartupImporter.runStartupCheck();
        DeviceAdmissionRegistry.start();
        ProtocolRegistryResolver.preload();

        Thread commandPollerThread =
                new Thread(
                        new PendingCommandPoller()
                );

        commandPollerThread.setName(
                "pending-command-poller"
        );

        commandPollerThread.setDaemon(true);

        commandPollerThread.start();

        System.out.println(
                "[STARTUP] PendingCommandPoller started"
        );

        int port = Integer.parseInt(
                System.getenv()
                        .getOrDefault(
                                "ALELS_GATEWAY_PORT",
                                "5050"
                        )
        );

        System.out.println(
                "[CONFIG] TCP Port = " + port
        );

        String serverMode = System.getenv().getOrDefault("ALELS_TCP_SERVER", "netty");
        if (!"netty".equalsIgnoreCase(serverMode)) {
            throw new IllegalStateException("ALELS_TCP_SERVER must be 'netty' in the production gateway");
        }

        int healthPort = Integer.parseInt(
                System.getenv().getOrDefault("ALELS_GATEWAY_HEALTH_PORT", "9090")
        );
        String healthHost = System.getenv().getOrDefault("ALELS_GATEWAY_HEALTH_HOST", "127.0.0.1");
        GatewayRuntimeMetrics metrics = GatewayRuntimeMetrics.instance();
        try (GatewayHealthServer healthServer = new GatewayHealthServer(healthHost, healthPort, metrics)) {
            healthServer.start();
            System.out.println("[CONFIG] TCP Server = netty");
            System.out.println("[CONFIG] Health Port = " + healthPort);
            new NettyTcpServer(port, metrics).start();
        }
    }
}
