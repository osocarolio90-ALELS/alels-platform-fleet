package com.alels.backend.serverops.database.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.serverops.database.model.DatabaseMonitorOverviewResponse.DatabaseDistribution;

@Repository
public class DatabaseMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDatabaseHealthy() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public long activeConnections() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active'");
    }

    public long totalConnections() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()");
    }

    public long idleConnections() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'idle'");
    }

    public long maxConnections() {
        return count("SELECT setting::bigint FROM pg_settings WHERE name = 'max_connections'");
    }

    public long databaseSizeBytes() {
        return count("SELECT pg_database_size(current_database())");
    }

    public long indexSizeBytes() {
        return count("SELECT COALESCE(SUM(pg_indexes_size(format('%I.%I', schemaname, relname)::regclass)), 0)::bigint FROM pg_stat_user_tables");
    }

    public long slowQueries() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active' AND NOW() - query_start > INTERVAL '2 seconds'");
    }

    public long blockedLocks() {
        return count("SELECT COUNT(*) FROM pg_locks WHERE NOT granted");
    }

    public long deadTuples() {
        return count("SELECT COALESCE(SUM(n_dead_tup), 0)::bigint FROM pg_stat_user_tables");
    }

    public long tableCount() {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'");
    }

    public long oldestVacuumHours() {
        return count("SELECT COALESCE(EXTRACT(EPOCH FROM (NOW() - MIN(COALESCE(last_autovacuum, last_vacuum, NOW())))) / 3600, 0)::bigint FROM pg_stat_user_tables");
    }

    public List<DatabaseDistribution> topTablesBySize() {
        return distribution("SELECT relname AS name, pg_total_relation_size(relid)::bigint AS count FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 6");
    }

    public List<DatabaseDistribution> topIndexesBySize() {
        return distribution("SELECT indexrelname AS name, pg_relation_size(indexrelid)::bigint AS count FROM pg_catalog.pg_statio_user_indexes ORDER BY pg_relation_size(indexrelid) DESC LIMIT 6");
    }

    private List<DatabaseDistribution> distribution(String sql) {
        try {
            List<DatabaseDistribution> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                long bytes = rs.getLong("count");
                return new DatabaseDistribution(
                        rs.getString("name"),
                        bytes,
                        0,
                        formatBytes(bytes)
                );
            });
            long total = rows.stream().mapToLong(DatabaseDistribution::getCount).sum();
            rows.forEach(row -> row.setPercent(total <= 0 ? 0 : row.getCount() * 100.0 / total));
            return rows;
        } catch (Exception e) {
            return List.of();
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

    private long count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }
}
