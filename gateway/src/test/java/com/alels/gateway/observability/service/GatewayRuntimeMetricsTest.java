package com.alels.gateway.observability.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GatewayRuntimeMetricsTest {
    @Test
    void readinessAndPrometheusMetricsReflectLifecycle() {
        GatewayRuntimeMetrics metrics = GatewayRuntimeMetrics.instance();
        metrics.markDraining();
        assertFalse(metrics.isReady());

        metrics.markAccepting();
        assertTrue(metrics.isReady());
        assertTrue(metrics.admitConnection());
        metrics.frameReceived(128);
        assertTrue(metrics.beginPublish());
        metrics.publishCompleted(true);
        metrics.connectionClosed();

        String prometheus = metrics.prometheus();
        assertTrue(prometheus.contains("alels_gateway_ready 1"));
        assertTrue(prometheus.contains("alels_gateway_connections_active 0"));
        assertTrue(prometheus.contains("alels_gateway_publishes_pending 0"));
        metrics.markDraining();
    }
}
