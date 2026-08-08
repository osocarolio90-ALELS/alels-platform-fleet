package com.alels.gateway.observability.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class GatewayHealthServer implements AutoCloseable {
    private final HttpServer server;
    private final GatewayRuntimeMetrics metrics;

    public GatewayHealthServer(String host, int port, GatewayRuntimeMetrics metrics) throws IOException {
        this.metrics = metrics;
        server = HttpServer.create(new InetSocketAddress(host, port), 64);
        server.createContext("/live", exchange -> respond(exchange, 200, "text/plain", "UP\n"));
        server.createContext("/ready", exchange -> respond(
                exchange,
                metrics.isReady() ? 200 : 503,
                "text/plain",
                metrics.isReady() ? "READY\n" : "NOT_READY\n"
        ));
        server.createContext("/metrics", exchange -> respond(
                exchange, 200, "text/plain; version=0.0.4", metrics.prometheus()
        ));
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gateway-health-http");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(1);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
