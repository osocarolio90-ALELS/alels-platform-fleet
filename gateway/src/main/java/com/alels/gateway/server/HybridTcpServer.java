package com.alels.gateway.server;

import java.net.ServerSocket;
import java.net.Socket;

public class HybridTcpServer {
    private final int port;

    public HybridTcpServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[ALELS-GATEWAY] Listening on TCP port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientSession(socket)).start();
            }
        }
    }
}
