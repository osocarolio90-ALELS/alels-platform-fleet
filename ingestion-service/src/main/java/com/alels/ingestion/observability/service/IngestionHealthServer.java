package com.alels.ingestion.observability.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class IngestionHealthServer implements AutoCloseable {
    private final HttpServer server;

    public IngestionHealthServer(String host, int port, IngestionRuntimeMetrics metrics) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 64);
        server.createContext("/live", exchange -> respond(exchange, 200, "UP\n"));
        server.createContext("/ready", exchange -> respond(
                exchange, metrics.isReady() ? 200 : 503,
                metrics.isReady() ? "READY\n" : "NOT_READY\n"
        ));
        server.createContext("/metrics", exchange -> respond(exchange, 200, metrics.prometheus()));
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ingestion-health-http");
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

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
