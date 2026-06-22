package com.alels.backend.serverops.database.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.database.model.DatabaseMonitorOverviewResponse;
import com.alels.backend.serverops.database.model.DatabaseMonitorOverviewResponse.DatabaseInsight;
import com.alels.backend.serverops.database.model.DatabaseMonitorOverviewResponse.DatabaseMetric;
import com.alels.backend.serverops.database.repository.DatabaseMonitorRepository;

@Service
public class DatabaseMonitorService {

    private final DatabaseMonitorRepository repository;

    public DatabaseMonitorService(DatabaseMonitorRepository repository) {
        this.repository = repository;
    }

    public DatabaseMonitorOverviewResponse getOverview() {
        boolean dbHealthy = repository.isDatabaseHealthy();
        long activeConnections = repository.activeConnections();
        long totalConnections = repository.totalConnections();
        long idleConnections = Math.max(0, repository.idleConnections());
        long maxConnections = Math.max(1, repository.maxConnections());
        long connectionUsage = Math.round(totalConnections * 100.0 / maxConnections);
        long databaseSizeBytes = repository.databaseSizeBytes();
        long indexSizeBytes = repository.indexSizeBytes();
        long slowQueries = repository.slowQueries();
        long blockedLocks = repository.blockedLocks();
        long deadTuples = repository.deadTuples();
        long tableCount = repository.tableCount();
        long oldestVacuumHours = repository.oldestVacuumHours();

        List<DatabaseInsight> insights = new ArrayList<>();
        if (!dbHealthy) {
            insights.add(insight("db-down", "EMERGENCY", "Database", "PostgreSQL tidak merespons", "Backend API, login, telemetry insert, dan dashboard bisa gagal total.", "Cek service PostgreSQL, disk, port 5432, credential, dan restart service jika diperlukan.", "OPEN"));
        }
        if (connectionUsage >= 90) {
            insights.add(insight("db-connection-critical", "CRITICAL", "Connection", "Connection PostgreSQL hampir penuh", "API dan ingestion bisa gagal membuka koneksi baru saat traffic device naik.", "Cek connection pool backend/gateway, slow query, idle connection, dan naikkan kapasitas secara terukur.", "OPEN"));
        } else if (connectionUsage >= 70) {
            insights.add(insight("db-connection-warning", "WARNING", "Connection", "Connection PostgreSQL mulai tinggi", "Response API bisa melambat jika user dan ingestion bertambah bersamaan.", "Pantau pool Hikari, batasi query list tanpa pagination, dan siapkan read replica untuk query berat.", "OPEN"));
        }
        if (slowQueries > 0) {
            insights.add(insight("db-slow-query", "WARNING", "Query", "Slow query aktif terdeteksi", "Endpoint bisa terasa lambat dan menahan koneksi database lebih lama.", "Identifikasi query aktif lebih dari 2 detik, cek index, dan pastikan list memakai server-side pagination.", "OPEN"));
        }
        if (blockedLocks > 0) {
            insights.add(insight("db-lock", "CRITICAL", "Lock", "Blocked lock terdeteksi", "Write telemetry atau update data master bisa tertahan.", "Cek pg_locks/pg_stat_activity, hentikan transaksi bermasalah, dan evaluasi query update/delete besar.", "OPEN"));
        }
        if (deadTuples > 100_000) {
            insights.add(insight("db-dead-tuples", "WARNING", "Vacuum", "Dead tuples tinggi", "Table scan dan index bisa membesar sehingga query lebih lambat.", "Pastikan autovacuum aktif, cek table terbesar, dan jadwalkan vacuum/analyze saat traffic rendah.", "OPEN"));
        }
        if (databaseSizeBytes > 50L * 1024 * 1024 * 1024) {
            insights.add(insight("db-size-growth", "WARNING", "Storage", "Database mulai besar", "Backup, query, dan vacuum akan makin berat jika retention belum jelas.", "Aktifkan retention raw packet/telemetry, partisi table besar, dan rencanakan archive cold storage.", "OPEN"));
        }
        if (insights.isEmpty()) {
            insights.add(insight("db-normal", "NORMAL", "Database", "Database dalam kondisi normal", "Belum ada anomali database melewati threshold tahap awal.", "Lanjutkan monitoring. Tambahkan baseline latency, table growth, dan slow query setelah traffic nyata bertambah.", "OPEN"));
        }

        insights.sort(Comparator.comparingInt(this::severityRank));
        String status = insights.get(0).getSeverity();

        DatabaseMonitorOverviewResponse response = new DatabaseMonitorOverviewResponse();
        response.setStatus(status);
        response.setDatabaseScore(score(status, connectionUsage, slowQueries, blockedLocks, deadTuples));
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(status, totalConnections, connectionUsage, slowQueries));
        response.setRecommendation(recommendation(status));
        response.setMetrics(List.of(
                metric("active_connections", "Active Connections", activeConnections, "conn", severityFor(activeConnections, 50, 200), "Koneksi database yang sedang aktif menjalankan query."),
                metric("idle_connections", "Idle Connections", idleConnections, "conn", "NORMAL", "Koneksi database yang idle dan masih terbuka."),
                metric("total_connections", "Total Connections", totalConnections, "conn", severityFor(connectionUsage, 70, 90), "Total koneksi ke database ALELS."),
                metric("max_connections", "Max Connections", maxConnections, "conn", "NORMAL", "Batas max_connections PostgreSQL saat ini."),
                metric("connection_usage", "Connection Usage", connectionUsage, "%", severityFor(connectionUsage, 70, 90), "Persentase pemakaian max_connections PostgreSQL."),
                metricBytes("database_size", "Database Size", databaseSizeBytes, severityForBytes(databaseSizeBytes, 20, 50), "Ukuran total database saat ini."),
                metric("slow_queries", "Slow Queries", slowQueries, "query", slowQueries > 0 ? "WARNING" : "NORMAL", "Query aktif lebih dari 2 detik."),
                metric("blocked_locks", "Blocked Locks", blockedLocks, "lock", blockedLocks > 0 ? "CRITICAL" : "NORMAL", "Lock PostgreSQL yang belum granted."),
                metric("dead_tuples", "Dead Tuples", deadTuples, "row", severityFor(deadTuples, 100_000, 1_000_000), "Estimasi row mati yang perlu vacuum."),
                metric("oldest_vacuum_hours", "Vacuum Age", oldestVacuumHours, "hour", severityFor(oldestVacuumHours, 168, 720), "Umur vacuum tertua dari user table."),
                metric("table_count", "Table Count", tableCount, "table", "NORMAL", "Jumlah table public saat ini."),
                metricBytes("index_size", "Index Size", indexSizeBytes, severityForBytes(indexSizeBytes, 10, 25), "Total ukuran index user table." )
        ));
        response.setTableDistribution(repository.topTablesBySize());
        response.setIndexDistribution(repository.topIndexesBySize());
        response.setInsights(insights);
        return response;
    }

    private DatabaseMetric metric(String key, String label, long value, String unit, String severity, String description) {
        return new DatabaseMetric(key, label, String.valueOf(value), unit, severity, description);
    }

    private DatabaseMetric metricBytes(String key, String label, long bytes, String severity, String description) {
        String[] parts = formatBytes(bytes).split(" ", 2);
        String value = parts.length > 0 ? parts[0] : "0";
        String unit = parts.length > 1 ? parts[1] : "B";
        return new DatabaseMetric(key, label, value, unit, severity, description);
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

    private DatabaseInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new DatabaseInsight(id, severity, category, title, impact, action, status);
    }

    private String severityFor(long value, long warning, long critical) {
        if (value >= critical) return "CRITICAL";
        if (value >= warning) return "WARNING";
        return "NORMAL";
    }

    private int severityRank(DatabaseInsight insight) {
        return switch (insight.getSeverity()) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }

    private int score(String status, long connectionUsage, long slowQueries, long blockedLocks, long deadTuples) {
        int base = switch (status) {
            case "EMERGENCY" -> 20;
            case "CRITICAL" -> 52;
            case "WARNING" -> 82;
            default -> 96;
        };
        int penalty = 0;
        if (connectionUsage >= 70) penalty += 5;
        if (slowQueries > 0) penalty += 4;
        if (blockedLocks > 0) penalty += 12;
        if (deadTuples > 100_000) penalty += 4;
        return Math.max(1, Math.min(100, base - penalty));
    }

    private String summary(String status, long totalConnections, long connectionUsage, long slowQueries) {
        if (!"NORMAL".equals(status)) {
            return "Database butuh perhatian. Connection " + totalConnections + " (" + connectionUsage + "%), slow query " + slowQueries + ".";
        }
        return "Database normal. Connection " + totalConnections + " (" + connectionUsage + "%), slow query " + slowQueries + ".";
    }

    private String recommendation(String status) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan recovery PostgreSQL, cek lock/connection, hentikan query bermasalah, dan lindungi ingestion dari direct insert berlebih.";
        }
        if ("WARNING".equals(status)) {
            return "Cek finding database, validasi index dan pagination, serta mulai baseline latency untuk traffic device nyata.";
        }
        return "Lanjutkan monitoring. Siapkan baseline connection, slow query, vacuum, dan table growth sebelum traffic device meningkat.";
    }
}
