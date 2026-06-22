package com.alels.gateway;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.netty.NettyTcpServer;
import com.alels.gateway.poller.PendingCommandPoller;
import com.alels.gateway.server.HybridTcpServer;
import com.alels.gateway.service.DevicePresenceScheduler;
import com.alels.gateway.service.DictionaryStartupImporter;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("========================================");
        System.out.println("ALELS TECH INDONESIA");
        System.out.println("========================================");

        DatabaseConfig.testConnection();

        DictionaryStartupImporter.runStartupCheck();

        DevicePresenceScheduler.start();

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

        String serverMode =
                System.getenv()
                        .getOrDefault(
                                "ALELS_TCP_SERVER",
                                "legacy"
                        );

        System.out.println(
                "[CONFIG] TCP Server = " + serverMode
        );

        if ("netty".equalsIgnoreCase(serverMode)) {
            NettyTcpServer server =
                    new NettyTcpServer(port);

            server.start();
            return;
        }

        HybridTcpServer server =
                new HybridTcpServer(port);

        server.start();
    }
}
