package com.alels.backend.serverops.storage.repository;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.alels.backend.serverops.storage.model.StorageMonitorOverviewResponse.StorageDistribution;

@Repository
public class StorageMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public StorageMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long diskTotalBytes() {
        return fileStoreValue(fileStore -> fileStore.getTotalSpace());
    }

    public long diskUsableBytes() {
        return fileStoreValue(fileStore -> fileStore.getUsableSpace());
    }

    public long databaseSizeBytes() {
        return count("SELECT pg_database_size(current_database())");
    }

    public long tableSizeBytes(String tableName) {
        return count("SELECT COALESCE(pg_total_relation_size(to_regclass('public." + tableName + "')), 0)::bigint");
    }

    public long indexSizeBytes() {
        return count("SELECT COALESCE(SUM(pg_indexes_size(format('%I.%I', schemaname, relname)::regclass)), 0)::bigint FROM pg_stat_user_tables");
    }

    public long rawPacketBytes() {
        return tableSizeBytes("raw_packets");
    }

    public long telemetryBytes() {
        return tableSizeBytes("telemetry") + tableSizeBytes("telemetry_alerts") + tableSizeBytes("device_receive_status");
    }

    public long commandBytes() {
        return tableSizeBytes("command_queue");
    }

    public long aiOpsBytes() {
        return tableSizeBytes("ai_ops_alerts") + tableSizeBytes("ai_ops_metric_snapshots");
    }

    public long masterDataBytes() {
        return tableSizeBytes("companies")
                + tableSizeBytes("users")
                + tableSizeBytes("company_roles")
                + tableSizeBytes("vehicles")
                + tableSizeBytes("devices")
                + tableSizeBytes("vehicle_devices")
                + tableSizeBytes("device_models")
                + tableSizeBytes("unknown_io_registry");
    }

    public long knownDataBytes() {
        return rawPacketBytes() + telemetryBytes() + commandBytes() + aiOpsBytes() + masterDataBytes() + indexSizeBytes();
    }

    public long logBytes() {
        return sumDirectoryBytes(Path.of("logs"))
                + sumDirectoryBytes(Path.of("backend", "logs"))
                + sumDirectoryBytes(Path.of("gateway", "logs"))
                + sumDirectoryBytes(Path.of("ingestion-service", "logs"));
    }

    public long backupBytes() {
        return sumDirectoryBytes(Path.of("backups"))
                + sumDirectoryBytes(Path.of("database", "backups"))
                + sumDirectoryBytes(Path.of("deploy", "backups"));
    }

    public List<StorageDistribution> dataDistribution() {
        List<StorageDistribution> rows = new ArrayList<>();
        add(rows, "Telemetry", telemetryBytes());
        add(rows, "Raw Packets", rawPacketBytes());
        add(rows, "Commands", commandBytes());
        add(rows, "AI Ops", aiOpsBytes());
        add(rows, "Master Data", masterDataBytes());
        add(rows, "Indexes", indexSizeBytes());
        add(rows, "Logs", logBytes());
        add(rows, "Backups", backupBytes());
        normalizePercent(rows);
        return rows;
    }

    public List<StorageDistribution> retentionDistribution() {
        List<StorageDistribution> rows = new ArrayList<>();
        rows.add(new StorageDistribution("Raw Packet", 90, 0, "90 days draft"));
        rows.add(new StorageDistribution("Telemetry", 730, 0, "24 months draft"));
        rows.add(new StorageDistribution("Command Log", 365, 0, "12 months draft"));
        rows.add(new StorageDistribution("Audit/Security Log", 1825, 0, "5 years draft"));
        normalizePercent(rows);
        return rows;
    }

    private void add(List<StorageDistribution> rows, String name, long bytes) {
        rows.add(new StorageDistribution(name, Math.max(0, bytes), 0, formatBytes(Math.max(0, bytes))));
    }

    private void normalizePercent(List<StorageDistribution> rows) {
        long total = rows.stream().mapToLong(storageDistribution -> storageDistribution.getCount()).sum();
        rows.forEach(row -> row.setPercent(total <= 0 ? 0 : row.getCount() * 100.0 / total));
    }

    private long fileStoreValue(FileStoreReader reader) {
        try {
            FileStore fileStore = Files.getFileStore(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath());
            return Math.max(0, reader.read(fileStore));
        } catch (Exception e) {
            return 0L;
        }
    }

    private long sumDirectoryBytes(Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            if (!Files.exists(absolute)) {
                return 0L;
            }
            try (var stream = Files.walk(absolute, 4)) {
                return stream
                        .filter(Files::isRegularFile)
                        .mapToLong(file -> {
                            try {
                                return Files.size(file);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();
            }
        } catch (Exception e) {
            return 0L;
        }
    }

    private long count(@NonNull String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
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

    @FunctionalInterface
    private interface FileStoreReader {
        long read(FileStore fileStore) throws IOException;
    }
}
