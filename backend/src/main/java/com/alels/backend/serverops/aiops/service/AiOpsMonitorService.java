package com.alels.backend.serverops.aiops.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.aiops.model.AiOpsOverviewResponse;
import com.alels.backend.serverops.aiops.repository.AiOpsMonitorRepository;

@Service
public class AiOpsMonitorService {

    private final AiOpsMonitorRepository repository;

    public AiOpsMonitorService(AiOpsMonitorRepository repository) {
        this.repository = repository;
    }

    public AiOpsOverviewResponse getOverview() {
        boolean dbHealthy = repository.isDatabaseHealthy();
        long activeDbConnections = repository.countActiveDatabaseConnections();
        long totalDbConnections = repository.countTotalDatabaseConnections();
        long connectedDevices = repository.countConnectedDevices();
        long offlineDevices = repository.countOfflineDevices();
        long telemetryPerMinute = repository.countTelemetryLastMinute();
        long invalidPacketsLastHour = repository.countInvalidPacketsLastHour();
        long unknownIo = repository.countUnknownIo();
        long openAlerts = repository.countOpenAiOpsAlerts();

        double heapUsedPercent = heapUsedPercent();
        double diskUsedPercent = diskUsedPercent();
        double systemLoad = systemLoadAverage();
        long uptimeMinutes = ManagementFactory.getRuntimeMXBean().getUptime() / 60_000;

        List<AiOpsOverviewResponse.MetricCard> metrics = List.of(
                metric("db_status", "PostgreSQL", dbHealthy ? "UP" : "DOWN", "", dbHealthy ? "NORMAL" : "EMERGENCY", "Koneksi database utama backend."),
                metric("heap_used", "Backend Heap", format(heapUsedPercent), "%", severityByPercent(heapUsedPercent), "Memori JVM backend yang sedang dipakai."),
                metric("disk_used", "Storage", format(diskUsedPercent), "%", severityByPercent(diskUsedPercent), "Pemakaian storage pada disk aplikasi."),
                metric("system_load", "System Load", format(systemLoad), "", systemLoad > 8 ? "WARNING" : "NORMAL", "Load average server dari operating system."),
                metric("db_connections", "DB Connections", String.valueOf(totalDbConnections), "conn", totalDbConnections > 80 ? "WARNING" : "NORMAL", "Total koneksi PostgreSQL database ALELS."),
                metric("connected_devices", "Connected Devices", String.valueOf(connectedDevices), "device", "NORMAL", "Device yang sedang online/terhubung."),
                metric("telemetry_minute", "Telemetry / Minute", String.valueOf(telemetryPerMinute), "packet", "NORMAL", "Jumlah telemetry masuk dalam 1 menit terakhir."),
                metric("open_ai_alerts", "Open AI Alerts", String.valueOf(openAlerts), "alert", openAlerts > 0 ? "WARNING" : "NORMAL", "Alert AI Ops yang belum diselesaikan.")
        );

        List<AiOpsOverviewResponse.AiOpsFinding> findings = new ArrayList<>();

        if (!dbHealthy) {
            findings.add(finding("database-down", "EMERGENCY", "Database", "PostgreSQL tidak terhubung", "Backend tidak bisa menjalankan query SELECT 1 ke PostgreSQL.", "Login, company, vehicle register, dan telemetry read/write bisa gagal.", "Cek service PostgreSQL, koneksi database, user/password, port 5432, dan log PostgreSQL.", "OPEN"));
        }
        if (diskUsedPercent >= 90) {
            findings.add(finding("storage-critical", "CRITICAL", "Storage", "Storage hampir penuh", "Storage server sudah melewati 90%.", "Insert telemetry dan log gateway berisiko gagal.", "Bersihkan log lama, cek tabel terbesar, aktifkan retention, dan tambah disk bila tren naik terus.", "OPEN"));
        } else if (diskUsedPercent >= 80) {
            findings.add(finding("storage-warning", "WARNING", "Storage", "Storage mulai tinggi", "Storage server sudah melewati 80%.", "Jika dibiarkan, database dan log bisa memenuhi disk.", "Cek pertumbuhan raw packet, telemetry, backup, dan log aplikasi.", "OPEN"));
        }
        if (heapUsedPercent >= 90) {
            findings.add(finding("backend-memory-critical", "CRITICAL", "Server", "Memori backend tinggi", "Heap JVM backend melewati 90%.", "API bisa lambat atau restart karena out-of-memory.", "Cek endpoint berat, pagination, object besar, dan pertimbangkan naikkan heap secara terukur.", "OPEN"));
        } else if (heapUsedPercent >= 80) {
            findings.add(finding("backend-memory-warning", "WARNING", "Server", "Memori backend mulai tinggi", "Heap JVM backend melewati 80%.", "Response API bisa mulai tidak stabil saat traffic naik.", "Pantau memory leak, cache, dan query yang mengembalikan data terlalu besar.", "OPEN"));
        }
        if (totalDbConnections >= 80) {
            findings.add(finding("db-connections-high", "WARNING", "Database", "Koneksi database tinggi", "Total koneksi PostgreSQL untuk database ALELS tinggi.", "Backend dan ingestion bisa menunggu koneksi kosong.", "Cek pool size, query lambat, transaksi menggantung, dan pg_stat_activity.", "OPEN"));
        }
        if (invalidPacketsLastHour > 0) {
            findings.add(finding("invalid-packets", "WARNING", "Gateway", "Invalid packet terdeteksi", "Ada packet telemetry dengan parse_status bukan VALID dalam 1 jam terakhir.", "Sebagian data device bisa tidak masuk dashboard.", "Cek parser Codec 8/8E/ALELS JSON, IMEI bermasalah, dan sample raw packet.", "OPEN"));
        }
        if (unknownIo > 0) {
            findings.add(finding("unknown-io", "WARNING", "Gateway", "Unknown IO terdeteksi", "Ada IO telemetry yang belum punya mapping.", "Data penting dari device bisa belum tampil dengan nama yang benar.", "Tambahkan mapping IO bertahap tanpa membuang numeric IO asli.", "OPEN"));
        }
        if (connectedDevices == 0 && offlineDevices > 0) {
            findings.add(finding("all-devices-offline", "WARNING", "Gateway", "Semua device tercatat offline", "Tidak ada device online, sementara ada device terdaftar offline.", "Monitoring real-time dan command routing tidak aktif.", "Cek gateway Netty, firewall port TCP, SIM/APN, dan device destination host/port.", "OPEN"));
        }

        if (findings.isEmpty()) {
            findings.add(finding("system-normal", "NORMAL", "AI Ops", "Tidak ada masalah kritis", "Rule-based analyzer tidak menemukan kondisi melewati threshold tahap awal.", "Sistem bisa dilanjutkan untuk test UI dan fitur menu aktif.", "Lanjutkan monitoring dan tambahkan threshold baru sesuai traffic nyata.", "OPEN"));
        }

        findings.sort(Comparator.comparingInt(f -> severityRank(f.getSeverity())));

        AiOpsOverviewResponse.AiOpsFinding main = findings.get(0);
        AiOpsOverviewResponse response = new AiOpsOverviewResponse();
        response.setSystemStatus(main.getSeverity());
        response.setMainIssue(main.getTitle());
        response.setImpact(main.getImpact());
        response.setRecommendedAction(main.getRecommendedAction());
        response.setGeneratedAt(Instant.now().toString());
        response.setMetrics(metrics);
        response.setFindings(findings);
        return response;
    }

    private AiOpsOverviewResponse.MetricCard metric(String key, String label, String value, String unit, String severity, String description) {
        return new AiOpsOverviewResponse.MetricCard(key, label, value, unit, severity, description);
    }

    private AiOpsOverviewResponse.AiOpsFinding finding(String id, String severity, String category, String title, String problem, String impact, String action, String status) {
        return new AiOpsOverviewResponse.AiOpsFinding(id, severity, category, title, problem, impact, action, status);
    }

    private double heapUsedPercent() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        return max <= 0 ? 0 : used * 100.0 / max;
    }

    private double diskUsedPercent() {
        File root = new File(System.getProperty("user.dir"));
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        return total <= 0 ? 0 : (total - free) * 100.0 / total;
    }

    private double systemLoadAverage() {
        double value = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        return value < 0 ? 0 : value;
    }

    private String severityByPercent(double value) {
        if (value >= 90) return "CRITICAL";
        if (value >= 80) return "WARNING";
        return "NORMAL";
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }

    private String format(double value) {
        return String.format("%.1f", value);
    }
}
