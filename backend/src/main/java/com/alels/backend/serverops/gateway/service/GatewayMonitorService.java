package com.alels.backend.serverops.gateway.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.gateway.model.GatewayMonitorOverviewResponse;
import com.alels.backend.serverops.gateway.model.GatewayMonitorOverviewResponse.GatewayInsight;
import com.alels.backend.serverops.gateway.model.GatewayMonitorOverviewResponse.GatewayMetric;
import com.alels.backend.serverops.gateway.repository.GatewayMonitorRepository;

@Service
public class GatewayMonitorService {

    private final GatewayMonitorRepository repository;

    public GatewayMonitorService(GatewayMonitorRepository repository) {
        this.repository = repository;
    }

    public GatewayMonitorOverviewResponse getOverview() {
        long totalDevices = repository.countTotalDevices();
        long onlineDevices = repository.countOnlineDevices();
        long offlineDevices = repository.countOfflineDevices();
        long gsmConnected = repository.countGsmConnected();
        long wifiConnected = repository.countWifiConnected();
        long telemetryPerMinute = repository.countTelemetryLastMinute();
        long invalidPacketsLastHour = repository.countInvalidPacketsLastHour();
        long staleDevices = repository.countStaleDevices();

        List<GatewayInsight> insights = new ArrayList<>();

        if (totalDevices > 0 && onlineDevices == 0) {
            insights.add(insight("gateway-no-online-device", "WARNING", "Gateway", "Tidak ada device online", "Semua device terdaftar terlihat offline sehingga monitoring real-time dan command routing tidak aktif.", "Cek service gateway Netty, firewall port TCP, SIM/APN, dan destination host/port device.", "OPEN"));
        }
        if (invalidPacketsLastHour > 0) {
            insights.add(insight("gateway-invalid-packet", "WARNING", "Parser", "Invalid packet terdeteksi", "Sebagian telemetry bisa gagal masuk karena packet tidak lolos parser.", "Ambil sample raw packet, cek Codec 8/8E/ALELS JSON parser, dan validasi IMEI bermasalah.", "OPEN"));
        }
        if (staleDevices > 0) {
            insights.add(insight("gateway-stale-device", "WARNING", "Presence", "Device stale melewati 7 menit", "Sebagian device tidak update last_seen sesuai threshold ALELS 7 menit.", "Cek interval device, power saving, GSM/WiFi signal, dan presence update gateway.", "OPEN"));
        }
        if (telemetryPerMinute > 100_000) {
            insights.add(insight("gateway-high-traffic", "CRITICAL", "Traffic", "Traffic telemetry sangat tinggi", "Traffic tinggi bisa menekan gateway, ingestion, dan database.", "Pastikan Kafka pipeline aktif, ingestion batch insert berjalan, dan direct DB insert gateway tidak dipakai untuk production.", "OPEN"));
        }
        if (insights.isEmpty()) {
            insights.add(insight("gateway-normal", "NORMAL", "Gateway", "Gateway dalam kondisi normal", "Belum ada anomali gateway melewati threshold tahap awal.", "Lanjutkan monitoring. Tambahkan baseline setelah device aktif bertambah.", "OPEN"));
        }

        insights.sort(Comparator.comparingInt(this::severityRank));
        String status = insights.get(0).getSeverity();
        int gatewayScore = gatewayScore(status, invalidPacketsLastHour, staleDevices, totalDevices, onlineDevices);

        GatewayMonitorOverviewResponse response = new GatewayMonitorOverviewResponse();
        response.setStatus(status);
        response.setGatewayScore(gatewayScore);
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(status, totalDevices, onlineDevices, telemetryPerMinute));
        response.setRecommendation(recommendation(status));
        response.setMetrics(List.of(
                metric("total_devices", "Total Devices", totalDevices, "device", "NORMAL", "Total device terdaftar di database."),
                metric("online_devices", "Online Devices", onlineDevices, "device", "NORMAL", "Device dengan online=true atau presence ONLINE."),
                metric("offline_devices", "Offline Devices", offlineDevices, "device", offlineDevices > 0 && totalDevices > 0 ? "WARNING" : "NORMAL", "Device yang tidak berada dalam status ONLINE."),
                metric("gsm_connected", "GSM Connected", gsmConnected, "session", "NORMAL", "Device/session aktif melalui channel GSM."),
                metric("wifi_connected", "WiFi Connected", wifiConnected, "session", "NORMAL", "Device/session aktif melalui channel WiFi."),
                metric("telemetry_minute", "Telemetry / Minute", telemetryPerMinute, "pkt/min", telemetryPerMinute > 100_000 ? "CRITICAL" : "NORMAL", "Telemetry yang masuk 1 menit terakhir."),
                metric("invalid_packets_hour", "Invalid Packet / Hour", invalidPacketsLastHour, "pkt/hour", invalidPacketsLastHour > 0 ? "WARNING" : "NORMAL", "Packet parse_status bukan VALID dalam 1 jam terakhir."),
                metric("stale_devices", "Stale Devices", staleDevices, "device", staleDevices > 0 ? "WARNING" : "NORMAL", "Device dengan last_seen lebih lama dari 7 menit.")
        ));
        response.setChannelDistribution(repository.channelDistribution(totalDevices));
        response.setProtocolDistribution(repository.protocolDistribution(totalDevices));
        response.setInsights(insights);
        return response;
    }

    private GatewayMetric metric(String key, String label, long value, String unit, String severity, String description) {
        return new GatewayMetric(key, label, String.valueOf(value), unit, severity, description);
    }

    private GatewayInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new GatewayInsight(id, severity, category, title, impact, action, status);
    }

    private int severityRank(GatewayInsight insight) {
        return switch (insight.getSeverity()) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }

    private int gatewayScore(String status, long invalidPacketsLastHour, long staleDevices, long totalDevices, long onlineDevices) {
        int base = switch (status) {
            case "EMERGENCY" -> 25;
            case "CRITICAL" -> 50;
            case "WARNING" -> 82;
            default -> 96;
        };
        int penalty = 0;
        if (invalidPacketsLastHour > 0) penalty += 5;
        if (staleDevices > 0) penalty += 5;
        if (totalDevices > 0 && onlineDevices == 0) penalty += 7;
        return Math.max(1, Math.min(100, base - penalty));
    }

    private String summary(String status, long totalDevices, long onlineDevices, long telemetryPerMinute) {
        if (!"NORMAL".equals(status)) {
            return "Gateway membutuhkan perhatian. Ada kondisi yang melewati threshold awal dan perlu dicek sebelum traffic device bertambah.";
        }
        return "Gateway normal. Total device " + totalDevices + ", online " + onlineDevices + ", telemetry " + telemetryPerMinute + " pkt/min.";
    }

    private String recommendation(String status) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan cek gateway Netty, Kafka/ingestion pipeline, parser packet, dan koneksi database sebelum menambah device.";
        }
        if ("WARNING".equals(status)) {
            return "Cek finding gateway, validasi device offline/stale, dan pastikan parser menerima packet valid.";
        }
        return "Lanjutkan monitoring. Baseline gateway akan semakin akurat setelah device aktif dan traffic nyata bertambah.";
    }
}
