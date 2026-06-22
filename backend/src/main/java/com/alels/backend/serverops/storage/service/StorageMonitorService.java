package com.alels.backend.serverops.storage.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.storage.model.StorageMonitorOverviewResponse;
import com.alels.backend.serverops.storage.model.StorageMonitorOverviewResponse.StorageInsight;
import com.alels.backend.serverops.storage.model.StorageMonitorOverviewResponse.StorageMetric;
import com.alels.backend.serverops.storage.repository.StorageMonitorRepository;

@Service
public class StorageMonitorService {

    private final StorageMonitorRepository repository;

    public StorageMonitorService(StorageMonitorRepository repository) {
        this.repository = repository;
    }

    public StorageMonitorOverviewResponse getOverview() {
        long totalDiskBytes = repository.diskTotalBytes();
        long usableDiskBytes = repository.diskUsableBytes();
        long usedDiskBytes = Math.max(0, totalDiskBytes - usableDiskBytes);
        long usagePercent = totalDiskBytes <= 0 ? 0 : Math.round(usedDiskBytes * 100.0 / totalDiskBytes);
        long databaseSizeBytes = repository.databaseSizeBytes();
        long rawPacketBytes = repository.rawPacketBytes();
        long telemetryBytes = repository.telemetryBytes();
        long indexBytes = repository.indexSizeBytes();
        long logBytes = repository.logBytes();
        long backupBytes = repository.backupBytes();

        boolean hasGrowthHistory = false;
        String dailyGrowth = hasGrowthHistory ? "0" : "Waiting Baseline";
        String weeklyGrowth = hasGrowthHistory ? "0" : "Waiting Baseline";
        String monthlyGrowth = hasGrowthHistory ? "0" : "Waiting Baseline";
        String predictedFull = hasGrowthHistory ? "9999" : "Waiting Baseline";

        List<StorageInsight> insights = new ArrayList<>();
        if (totalDiskBytes <= 0) {
            insights.add(insight("storage-filesystem-unavailable", "WARNING", "Filesystem", "Filesystem capacity belum terbaca", "AI Ops belum bisa menghitung risiko disk penuh dari host backend.", "Pastikan service backend memiliki permission membaca FileStore dan jalankan di host production yang sama dengan storage data.", "OPEN"));
        }
        if (usagePercent >= 90) {
            insights.add(insight("storage-disk-critical", "CRITICAL", "Capacity", "Disk usage kritis", "PostgreSQL, gateway log, dan telemetry insert bisa gagal jika disk penuh.", "Segera archive data lama, bersihkan log, tambah disk, dan validasi retention raw packet/telemetry.", "OPEN"));
        } else if (usagePercent >= 75) {
            insights.add(insight("storage-disk-warning", "WARNING", "Capacity", "Disk usage mulai tinggi", "Pertumbuhan telemetry dan raw packet bisa mempercepat risiko disk penuh.", "Aktifkan retention policy, cek table terbesar, dan siapkan archive sebelum device bertambah.", "OPEN"));
        }
        if (rawPacketBytes > 10L * 1024 * 1024 * 1024) {
            insights.add(insight("storage-raw-packet-growth", "WARNING", "Retention", "Raw packet storage mulai besar", "Raw packet biasanya tumbuh paling cepat pada platform telematics.", "Tetapkan retention raw packet 30-90 hari dan archive ke cold storage jika dibutuhkan audit.", "OPEN"));
        }
        if (databaseSizeBytes > 50L * 1024 * 1024 * 1024) {
            insights.add(insight("storage-db-large", "WARNING", "Database", "Database footprint mulai besar", "Backup, vacuum, dan restore akan makin lama jika retention belum disiplin.", "Partisi table telemetry/raw, gunakan retention, dan pisahkan analytical workload dari OLTP.", "OPEN"));
        }
        if (logBytes > 5L * 1024 * 1024 * 1024) {
            insights.add(insight("storage-log-growth", "WARNING", "Logs", "Log storage mulai besar", "Log gateway/backend bisa memenuhi disk dan mengganggu write database.", "Aktifkan log rotation dan hindari log per packet saat traffic tinggi.", "OPEN"));
        }
        insights.add(insight("storage-retention-policy", "WARNING", "Retention", "Retention policy belum dikunci", "Raw packet dan telemetry masih memakai rekomendasi draft, belum memiliki policy retention/archive resmi untuk skala besar.", "Tetapkan policy aktif: raw packet 30-90 hari, telemetry 24 bulan, command log 12 bulan, audit/security log 5 tahun.", "OPEN"));
        insights.add(insight("storage-growth-baseline", "NORMAL", "Growth", "Growth prediction masih baseline", "Belum ada histori snapshot harian minimal 7 hari untuk menghitung daily growth yang akurat.", "Gunakan status Waiting Baseline sampai snapshot storage harian tersedia.", "OPEN"));
        if (insights.stream().noneMatch(insight -> !"NORMAL".equals(insight.getSeverity()))) {
            insights.add(insight("storage-normal", "NORMAL", "Storage", "Storage dalam kondisi normal", "Belum ada risiko capacity melewati threshold awal.", "Lanjutkan monitoring. Aktifkan retention dan archive sebelum traffic device besar.", "OPEN"));
        }

        insights.sort(Comparator.comparingInt(this::severityRank));
        String status = insights.get(0).getSeverity();

        StorageMonitorOverviewResponse response = new StorageMonitorOverviewResponse();
        response.setStatus(status);
        response.setStorageScore(score(status, usagePercent, databaseSizeBytes, rawPacketBytes, logBytes));
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(status, usagePercent, usableDiskBytes));
        response.setRecommendation(recommendation(status));
        response.setMetrics(List.of(
                metricBytes("total_disk", "Total Disk", totalDiskBytes, "NORMAL", "Total kapasitas FileStore host backend saat ini."),
                metricBytes("used_disk", "Used Disk", usedDiskBytes, severityFor(usagePercent, 75, 90), "Estimasi disk terpakai pada FileStore host backend."),
                metricBytes("free_disk", "Free Disk", usableDiskBytes, severityFor(usagePercent, 75, 90), "Disk usable yang masih tersedia untuk proses backend/database/log."),
                metric("disk_usage", "Disk Usage", usagePercent, "%", severityFor(usagePercent, 75, 90), "Persentase penggunaan FileStore host backend."),
                metricBytes("database_storage", "Database Storage", databaseSizeBytes, severityForBytes(databaseSizeBytes, 20, 50), "Ukuran database PostgreSQL saat ini."),
                metricBytes("telemetry_storage", "Telemetry Storage", telemetryBytes, severityForBytes(telemetryBytes, 10, 30), "Ukuran telemetry, alerts, dan status receive."),
                metricBytes("raw_packet_storage", "Raw Packet Storage", rawPacketBytes, severityForBytes(rawPacketBytes, 10, 30), "Ukuran raw packet yang perlu retention ketat."),
                metricBytes("index_storage", "Index Storage", indexBytes, severityForBytes(indexBytes, 10, 25), "Total ukuran index user table."),
                metricBytes("log_storage", "Log Storage", logBytes, severityForBytes(logBytes, 2, 5), "Estimasi log di folder logs/backend/logs/gateway/logs."),
                metricBytes("backup_storage", "Backup Storage", backupBytes, severityForBytes(backupBytes, 10, 30), "Estimasi backup lokal jika folder backup tersedia."),
                metricText("daily_growth", "Daily Growth", dailyGrowth, hasGrowthHistory ? "MB/day" : "", "NORMAL", "Waiting Baseline sampai histori snapshot harian tersedia."),
                metricText("weekly_growth", "Weekly Growth", weeklyGrowth, hasGrowthHistory ? "MB/week" : "", "NORMAL", "Waiting Baseline sampai histori snapshot harian tersedia."),
                metricText("monthly_growth", "Monthly Growth", monthlyGrowth, hasGrowthHistory ? "MB/month" : "", "NORMAL", "Waiting Baseline sampai histori snapshot harian tersedia."),
                metricText("predicted_full", "Predicted Full", predictedFull, hasGrowthHistory ? "day" : "", "NORMAL", "Waiting Baseline sampai minimal 7 hari snapshot storage tersedia."),
                metricText("raw_retention", "Raw Retention", "90", "days draft", "WARNING", "Retention raw packet direkomendasikan 30-90 hari dan belum dikunci sebagai policy aktif."),
                metricText("telemetry_retention", "Telemetry Retention", "24", "months draft", "WARNING", "Retention telemetry direkomendasikan 24 bulan dan belum dikunci sebagai policy aktif.")
        ));
        response.setDataDistribution(repository.dataDistribution());
        response.setRetentionDistribution(repository.retentionDistribution());
        response.setInsights(insights);
        return response;
    }

    private StorageMetric metric(String key, String label, long value, String unit, String severity, String description) {
        return new StorageMetric(key, label, String.valueOf(value), unit, severity, description);
    }

    private StorageMetric metricText(String key, String label, String value, String unit, String severity, String description) {
        return new StorageMetric(key, label, value, unit, severity, description);
    }

    private StorageMetric metricBytes(String key, String label, long bytes, String severity, String description) {
        String[] parts = formatBytes(bytes).split(" ", 2);
        String value = parts.length > 0 ? parts[0] : "0";
        String unit = parts.length > 1 ? parts[1] : "B";
        return new StorageMetric(key, label, value, unit, severity, description);
    }

    private String severityFor(long value, long warning, long critical) {
        if (value >= critical) return "CRITICAL";
        if (value >= warning) return "WARNING";
        return "NORMAL";
    }

    private String severityForBytes(long bytes, long warningGb, long criticalGb) {
        long warningBytes = warningGb * 1024L * 1024L * 1024L;
        long criticalBytes = criticalGb * 1024L * 1024L * 1024L;
        if (bytes >= criticalBytes) return "CRITICAL";
        if (bytes >= warningBytes) return "WARNING";
        return "NORMAL";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return trim(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return trim(mb) + " MB";
        double gb = mb / 1024.0;
        if (gb < 1024) return trim(gb) + " GB";
        return trim(gb / 1024.0) + " TB";
    }

    private String trim(double value) {
        if (value >= 100) return String.format(java.util.Locale.US, "%.0f", value);
        if (value >= 10) return String.format(java.util.Locale.US, "%.1f", value);
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private int score(String status, long usagePercent, long databaseSizeBytes, long rawPacketBytes, long logBytes) {
        int score = switch (status) {
            case "EMERGENCY" -> 25;
            case "CRITICAL" -> 55;
            case "WARNING" -> 92;
            default -> 96;
        };
        if (usagePercent > 0) score -= Math.max(0, (int) usagePercent - 60) / 2;
        if (databaseSizeBytes > 20L * 1024 * 1024 * 1024) score -= 5;
        if (rawPacketBytes > 10L * 1024 * 1024 * 1024) score -= 5;
        if (logBytes > 2L * 1024 * 1024 * 1024) score -= 4;
        return Math.max(10, Math.min(100, score));
    }

    private String summary(String status, long usagePercent, long usableDiskBytes) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Storage membutuhkan tindakan cepat sebelum write telemetry/log/database terganggu.";
        }
        if ("WARNING".equals(status)) {
            return "Storage masih berjalan, tetapi retention policy dan growth baseline perlu dikunci sebelum scale naik.";
        }
        return "Storage dalam kondisi normal. Disk usage " + usagePercent + "% dengan free space " + formatBytes(usableDiskBytes) + ".";
    }

    private String recommendation(String status) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan archive raw packet/telemetry lama, aktifkan log rotation, dan tambah storage sebelum traffic device meningkat.";
        }
        if ("WARNING".equals(status)) {
            return "Finalisasi retention policy, mulai snapshot storage harian, dan pisahkan raw packet/archive agar PostgreSQL tetap ringan.";
        }
        return "Lanjutkan monitoring. Tahap berikutnya simpan snapshot storage harian untuk growth prediction dan kapasitas multi-node.";
    }

    private StorageInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new StorageInsight(id, severity, category, title, impact, action, status);
    }

    private int severityRank(StorageInsight insight) {
        return switch (insight.getSeverity()) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }
}
