package com.alels.backend.serverops.traffic.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.traffic.model.TrafficMonitorOverviewResponse;
import com.alels.backend.serverops.traffic.model.TrafficMonitorOverviewResponse.TrafficInsight;
import com.alels.backend.serverops.traffic.model.TrafficMonitorOverviewResponse.TrafficMetric;
import com.alels.backend.serverops.traffic.repository.TrafficMonitorRepository;

@Service
public class TrafficMonitorService {

    private final TrafficMonitorRepository repository;

    public TrafficMonitorService(TrafficMonitorRepository repository) {
        this.repository = repository;
    }

    public TrafficMonitorOverviewResponse getOverview() {
        long rawPacketsMinute = repository.countRawPacketsLastMinute();
        long rawPacketsHour = repository.countRawPacketsLastHour();
        long telemetryMinute = repository.countTelemetryLastMinute();
        long invalidPacketsHour = repository.countInvalidPacketsLastHour();
        long unknownIoCount = repository.countUnknownIoRegistry();
        long avgPacketSize = repository.averageRawPacketSizeLastHour();
        String packetsPerSecond = String.format(Locale.US, "%.2f", rawPacketsMinute / 60.0);
        long validPacketHour = Math.max(0, rawPacketsHour - invalidPacketsHour);
        long validRate = rawPacketsHour <= 0 ? 100 : Math.round(validPacketHour * 100.0 / rawPacketsHour);

        List<TrafficInsight> insights = new ArrayList<>();
        if (rawPacketsMinute > 120_000) {
            insights.add(insight("traffic-high-packet-rate", "CRITICAL", "Traffic", "Packet rate sangat tinggi", "Gateway, ingestion service, dan database bisa tertinggal jika pipeline belum memakai queue/batch.", "Pastikan Kafka pipeline aktif, ingestion consumer diskalakan, dan direct insert gateway tidak dipakai untuk production.", "OPEN"));
        } else if (rawPacketsMinute > 30_000) {
            insights.add(insight("traffic-elevated-packet-rate", "WARNING", "Traffic", "Packet rate mulai tinggi", "Sistem masih bisa berjalan, tetapi baseline kapasitas harus divalidasi sebelum menambah device.", "Pantau CPU gateway, Kafka lag, DB insert latency, dan aktifkan alert threshold per minute.", "OPEN"));
        }
        if (invalidPacketsHour > 0) {
            insights.add(insight("traffic-invalid-packet", "WARNING", "Parser", "Invalid packet terdeteksi", "Sebagian telemetry bisa gagal diproses atau tidak masuk dashboard.", "Ambil sample raw packet, cek parser Codec 8/8E/ALELS JSON, dan validasi device/IMEI pengirim.", "OPEN"));
        }
        if (unknownIoCount > 0) {
            insights.add(insight("traffic-unknown-io", "NORMAL", "IO Registry", "Unknown IO tersimpan", "IO numeric belum dimapping, tetapi tetap aman karena disimpan untuk dianalisa bertahap.", "Tambahkan mapping IO berdasarkan prioritas device/model yang paling sering muncul.", "OPEN"));
        }
        if (avgPacketSize > 4096) {
            insights.add(insight("traffic-large-payload", "WARNING", "Payload", "Average payload besar", "Raw packet storage dan network I/O bisa naik lebih cepat dari estimasi.", "Cek payload device, retention raw packet, dan strategi kompresi/archive sebelum production besar.", "OPEN"));
        }
        if (insights.isEmpty()) {
            insights.add(insight("traffic-normal", "NORMAL", "Traffic", "Traffic dalam kondisi normal", "Belum ada anomali traffic melewati threshold tahap awal.", "Lanjutkan monitoring. Tambahkan baseline setelah device aktif dan traffic nyata bertambah.", "OPEN"));
        }

        insights.sort(Comparator.comparingInt(this::severityRank));
        String status = insights.get(0).getSeverity();

        TrafficMonitorOverviewResponse response = new TrafficMonitorOverviewResponse();
        response.setStatus(status);
        response.setTrafficScore(score(status, rawPacketsMinute, invalidPacketsHour, avgPacketSize));
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(status, rawPacketsMinute, invalidPacketsHour));
        response.setRecommendation(recommendation(status));
        response.setMetrics(List.of(
                metric("packets_second", "Packet / Second", packetsPerSecond, "pkt/sec", rawPacketsMinute > 120_000 ? "CRITICAL" : rawPacketsMinute > 30_000 ? "WARNING" : "NORMAL", "Estimasi packet per second dari raw packet 1 menit terakhir."),
                metric("packets_minute", "Packet / Minute", rawPacketsMinute, "pkt/min", rawPacketsMinute > 120_000 ? "CRITICAL" : rawPacketsMinute > 30_000 ? "WARNING" : "NORMAL", "Raw packet yang diterima 1 menit terakhir."),
                metric("telemetry_minute", "Telemetry / Minute", telemetryMinute, "pkt/min", "NORMAL", "Telemetry parsed yang masuk 1 menit terakhir."),
                metric("raw_packets_hour", "Raw Packet / Hour", rawPacketsHour, "pkt/hour", "NORMAL", "Raw packet yang diterima 1 jam terakhir."),
                metric("invalid_packets_hour", "Invalid Packet / Hour", invalidPacketsHour, "pkt/hour", invalidPacketsHour > 0 ? "WARNING" : "NORMAL", "Telemetry parse_status bukan VALID dalam 1 jam terakhir."),
                metric("unknown_io_count", "Unknown IO", unknownIoCount, "io", "NORMAL", "Jumlah IO numeric yang belum dimapping dalam registry."),
                metric("avg_packet_size", "Average Packet Size", avgPacketSize, "bytes", avgPacketSize > 4096 ? "WARNING" : "NORMAL", "Rata-rata ukuran payload raw packet 1 jam terakhir."),
                metric("parser_valid_rate", "Parser Valid Rate", validRate, "%", validRate < 98 ? "WARNING" : "NORMAL", "Rasio packet valid terhadap raw packet 1 jam terakhir.")
        ));
        response.setChannelDistribution(repository.channelDistribution(rawPacketsHour));
        response.setProtocolDistribution(repository.protocolDistribution(rawPacketsHour));
        response.setInsights(insights);
        return response;
    }

    private TrafficMetric metric(String key, String label, long value, String unit, String severity, String description) {
        return new TrafficMetric(key, label, String.valueOf(value), unit, severity, description);
    }

    private TrafficMetric metric(String key, String label, String value, String unit, String severity, String description) {
        return new TrafficMetric(key, label, value, unit, severity, description);
    }

    private TrafficInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new TrafficInsight(id, severity, category, title, impact, action, status);
    }

    private int severityRank(TrafficInsight insight) {
        return switch (insight.getSeverity()) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }

    private int score(String status, long rawPacketsMinute, long invalidPacketsHour, long avgPacketSize) {
        int base = switch (status) {
            case "EMERGENCY" -> 25;
            case "CRITICAL" -> 50;
            case "WARNING" -> 82;
            default -> 96;
        };
        int penalty = 0;
        if (rawPacketsMinute > 30_000) penalty += 5;
        if (invalidPacketsHour > 0) penalty += 5;
        if (avgPacketSize > 4096) penalty += 5;
        return Math.max(1, Math.min(100, base - penalty));
    }

    private String summary(String status, long rawPacketsMinute, long invalidPacketsHour) {
        if (!"NORMAL".equals(status)) {
            return "Traffic membutuhkan perhatian. Packet/min " + rawPacketsMinute + ", invalid/hour " + invalidPacketsHour + ".";
        }
        return "Traffic normal. Packet/min " + rawPacketsMinute + ", parser dalam batas aman tahap awal.";
    }

    private String recommendation(String status) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan queue ingestion, batch insert, parser sampling, dan scaling gateway sebelum menambah device.";
        }
        if ("WARNING".equals(status)) {
            return "Cek finding traffic, validasi parser, dan mulai buat baseline packet/sec berdasarkan traffic nyata.";
        }
        return "Lanjutkan monitoring. Baseline traffic akan semakin akurat setelah device aktif dan telemetry nyata bertambah.";
    }
}
