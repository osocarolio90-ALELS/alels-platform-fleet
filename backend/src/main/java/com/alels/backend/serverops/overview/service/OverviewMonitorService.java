package com.alels.backend.serverops.overview.service;

import com.alels.backend.serverops.aiops.service.AiOpsMonitorService;
import com.alels.backend.serverops.gateway.service.GatewayMonitorService;
import com.alels.backend.serverops.traffic.service.TrafficMonitorService;
import com.alels.backend.serverops.database.service.DatabaseMonitorService;
import com.alels.backend.serverops.storage.service.StorageMonitorService;
import com.alels.backend.serverops.security.service.SecurityMonitorService;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.aiops.model.AiOpsOverviewResponse;
import com.alels.backend.serverops.database.model.DatabaseMonitorOverviewResponse;
import com.alels.backend.serverops.gateway.model.GatewayMonitorOverviewResponse;
import com.alels.backend.serverops.overview.model.OverviewMonitorOverviewResponse;
import com.alels.backend.serverops.overview.model.OverviewMonitorOverviewResponse.MonitorModule;
import com.alels.backend.serverops.overview.model.OverviewMonitorOverviewResponse.OverviewDistribution;
import com.alels.backend.serverops.overview.model.OverviewMonitorOverviewResponse.OverviewInsight;
import com.alels.backend.serverops.overview.model.OverviewMonitorOverviewResponse.OverviewMetric;
import com.alels.backend.serverops.security.model.SecurityMonitorOverviewResponse;
import com.alels.backend.serverops.storage.model.StorageMonitorOverviewResponse;
import com.alels.backend.serverops.traffic.model.TrafficMonitorOverviewResponse;
import com.alels.backend.serverops.overview.repository.OverviewMonitorRepository;

@Service
public class OverviewMonitorService {

    private static final String ATTENTION = "ATTENTION";

    private final AiOpsMonitorService aiOpsMonitorService;
    private final GatewayMonitorService gatewayMonitorService;
    private final TrafficMonitorService trafficMonitorService;
    private final DatabaseMonitorService databaseMonitorService;
    private final StorageMonitorService storageMonitorService;
    private final SecurityMonitorService securityMonitorService;
    private final OverviewMonitorRepository repository;

    public OverviewMonitorService(
            AiOpsMonitorService aiOpsMonitorService,
            GatewayMonitorService gatewayMonitorService,
            TrafficMonitorService trafficMonitorService,
            DatabaseMonitorService databaseMonitorService,
            StorageMonitorService storageMonitorService,
            SecurityMonitorService securityMonitorService,
            OverviewMonitorRepository repository) {
        this.aiOpsMonitorService = aiOpsMonitorService;
        this.gatewayMonitorService = gatewayMonitorService;
        this.trafficMonitorService = trafficMonitorService;
        this.databaseMonitorService = databaseMonitorService;
        this.storageMonitorService = storageMonitorService;
        this.securityMonitorService = securityMonitorService;
        this.repository = repository;
    }

    public OverviewMonitorOverviewResponse getOverview() {
        List<MonitorModule> modules = new ArrayList<>();
        List<OverviewInsight> allInsights = new ArrayList<>();

        AiOpsOverviewResponse aiOps = safeAiOps();
        GatewayMonitorOverviewResponse gateway = safeGateway();
        TrafficMonitorOverviewResponse traffic = safeTraffic();
        DatabaseMonitorOverviewResponse database = safeDatabase();
        StorageMonitorOverviewResponse storage = safeStorage();
        SecurityMonitorOverviewResponse security = safeSecurity();

        modules.add(module("ai_ops", "AI Ops", aiOps.getSystemStatus(), scoreFromStatus(aiOps.getSystemStatus()), aiOps.getMainIssue(), aiOps.getRecommendedAction()));
        modules.add(module("gateway", "Gateway", gateway.getStatus(), gateway.getGatewayScore(), gateway.getSummary(), gateway.getRecommendation()));
        modules.add(module("traffic", "Traffic", traffic.getStatus(), traffic.getTrafficScore(), traffic.getSummary(), traffic.getRecommendation()));
        modules.add(module("database", "Database", database.getStatus(), database.getDatabaseScore(), database.getSummary(), database.getRecommendation()));
        modules.add(module("storage", "Storage", storage.getStatus(), storage.getStorageScore(), storage.getSummary(), storage.getRecommendation()));
        modules.add(module("security", "Security", security.getStatus(), security.getSecurityScore(), security.getSummary(), security.getRecommendation()));

        addAiOpsInsights(allInsights, aiOps);
        addGatewayInsights(allInsights, gateway);
        addTrafficInsights(allInsights, traffic);
        addDatabaseInsights(allInsights, database);
        addStorageInsights(allInsights, storage);
        addSecurityInsights(allInsights, security);

        List<OverviewInsight> actionableInsights = allInsights.stream()
                .filter(this::isActionableInsight)
                .sorted(Comparator.comparingInt(this::severityRank))
                .limit(5)
                .toList();

        long normalModules = modules.stream().filter((m) -> "NORMAL".equalsIgnoreCase(m.getStatus())).count();
        long attentionModules = modules.stream().filter((m) -> isAttentionStatus(m.getStatus())).count();
        long criticalModules = modules.stream().filter((m) -> isCriticalStatus(m.getStatus())).count();
        long warningFindings = actionableInsights.stream().filter((i) -> !"RESOLVED".equalsIgnoreCase(i.getStatus())).count();
        boolean securityFoundationReady = repository.securityEventFoundationReady();
        long openAlerts = repository.openAiOpsAlerts() + repository.openSecurityEvents();
        long onlineDevices = repository.onlineDevices();

        int overviewScore = averageScore(modules);
        String overallStatus = overviewHealthStatus(criticalModules, overviewScore);

        OverviewMonitorOverviewResponse response = new OverviewMonitorOverviewResponse();
        response.setStatus(overallStatus);
        response.setOverviewScore(overviewScore);
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(overallStatus, criticalModules, attentionModules, warningFindings));
        response.setRecommendation(recommendation(overallStatus, securityFoundationReady, warningFindings, attentionModules));
        response.setMetrics(List.of(
                metric("overview_score", "Overall Score", String.valueOf(overviewScore), "%", overallStatus, "Skor gabungan AI Ops, Gateway, Traffic, Database, Storage, dan Security."),
                metric("normal_modules", "Normal Modules", String.valueOf(normalModules), "module", "NORMAL", "Jumlah domain monitor dalam status normal."),
                metric("attention_modules", "Attention Modules", String.valueOf(attentionModules), "module", attentionModules > 0 ? ATTENTION : "NORMAL", "Jumlah domain monitor yang butuh perhatian operasional."),
                metric("critical_modules", "Critical Modules", String.valueOf(criticalModules), "module", criticalModules > 0 ? "CRITICAL" : "NORMAL", "Jumlah domain monitor dengan severity critical/emergency."),
                metric("warning_findings", "Warning Findings", String.valueOf(warningFindings), "finding", warningFindings > 0 ? ATTENTION : "NORMAL", "Finding lintas domain yang benar-benar perlu tindakan."),
                metric("total_devices", "Registered Devices", String.valueOf(repository.totalDevices()), "device", "NORMAL", "Total device terdaftar sebagai baseline kapasitas platform."),
                metric("online_devices", "Online Devices", String.valueOf(onlineDevices), "device", "NORMAL", "Device yang sedang online berdasarkan presence gateway/device table."),
                metric("active_users", "Active Users", String.valueOf(repository.activeUsers()), "user", "NORMAL", "Total user aktif untuk baseline security dan operational risk."),
                metric("security_foundation", "Security Foundation", securityFoundationReady ? "READY" : "BASELINE", "", securityFoundationReady ? "NORMAL" : ATTENTION, "Status security_events, audit_logs, dan login_events."),
                metric("active_alerts", "Active Alerts", String.valueOf(openAlerts), "alert", openAlerts > 0 ? ATTENTION : "NORMAL", "Alert realtime aktif dari AI Ops dan Security foundation.")
        ));
        response.setModules(modules);
        response.setHealthDistribution(healthDistribution(modules));
        response.setPriorityDistribution(priorityDistribution(modules));
        response.setInsights(actionableInsights);
        return response;
    }

    private MonitorModule module(String key, String label, String status, int score, String summary, String recommendation) {
        return new MonitorModule(key, label, normalize(status), clamp(score), safe(summary), safe(recommendation));
    }

    private OverviewMetric metric(String key, String label, String value, String unit, String severity, String description) {
        return new OverviewMetric(key, label, value, unit, normalize(severity), description);
    }

    private OverviewInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new OverviewInsight(id, normalize(severity), category, safe(title), safe(impact), safe(action), safe(status));
    }

    private void addAiOpsInsights(List<OverviewInsight> target, AiOpsOverviewResponse response) {
        for (AiOpsOverviewResponse.AiOpsFinding finding : response.getFindings()) {
            target.add(insight("ai-ops-" + finding.getId(), finding.getSeverity(), "AI Ops", finding.getTitle(), finding.getImpact(), finding.getRecommendedAction(), finding.getStatus()));
        }
    }

    private void addGatewayInsights(List<OverviewInsight> target, GatewayMonitorOverviewResponse response) {
        for (GatewayMonitorOverviewResponse.GatewayInsight finding : response.getInsights()) {
            target.add(insight("gateway-" + finding.getId(), finding.getSeverity(), "Gateway", finding.getTitle(), finding.getImpact(), finding.getAction(), finding.getStatus()));
        }
    }

    private void addTrafficInsights(List<OverviewInsight> target, TrafficMonitorOverviewResponse response) {
        for (TrafficMonitorOverviewResponse.TrafficInsight finding : response.getInsights()) {
            target.add(insight("traffic-" + finding.getId(), finding.getSeverity(), "Traffic", finding.getTitle(), finding.getImpact(), finding.getAction(), finding.getStatus()));
        }
    }

    private void addDatabaseInsights(List<OverviewInsight> target, DatabaseMonitorOverviewResponse response) {
        for (DatabaseMonitorOverviewResponse.DatabaseInsight finding : response.getInsights()) {
            target.add(insight("database-" + finding.getId(), finding.getSeverity(), "Database", finding.getTitle(), finding.getImpact(), finding.getAction(), finding.getStatus()));
        }
    }

    private void addStorageInsights(List<OverviewInsight> target, StorageMonitorOverviewResponse response) {
        for (StorageMonitorOverviewResponse.StorageInsight finding : response.getInsights()) {
            target.add(insight("storage-" + finding.getId(), finding.getSeverity(), "Storage", finding.getTitle(), finding.getImpact(), finding.getAction(), finding.getStatus()));
        }
    }

    private void addSecurityInsights(List<OverviewInsight> target, SecurityMonitorOverviewResponse response) {
        for (SecurityMonitorOverviewResponse.SecurityInsight finding : response.getInsights()) {
            target.add(insight("security-" + finding.getId(), finding.getSeverity(), "Security", finding.getTitle(), finding.getImpact(), finding.getAction(), finding.getStatus()));
        }
    }

    private List<OverviewDistribution> healthDistribution(List<MonitorModule> modules) {
        long normal = modules.stream().filter((m) -> "NORMAL".equalsIgnoreCase(m.getStatus())).count();
        long attention = modules.stream().filter((m) -> isAttentionStatus(m.getStatus())).count();
        long critical = modules.stream().filter((m) -> isCriticalStatus(m.getStatus())).count();
        long total = Math.max(1, modules.size());
        return List.of(
                distribution("Normal", normal, total),
                distribution("Attention", attention, total),
                distribution("Critical", critical, total)
        );
    }

    private List<OverviewDistribution> priorityDistribution(List<MonitorModule> modules) {
        return modules.stream()
                .sorted(Comparator.comparingInt((MonitorModule m) -> severityRank(m.getStatus())).thenComparingInt((MonitorModule m) -> -m.getScore()))
                .map((m) -> new OverviewDistribution(m.getLabel(), m.getScore(), m.getScore(), m.getScore() + "% " + moduleStatusLabel(m.getStatus())))
                .toList();
    }

    private OverviewDistribution distribution(String name, long count, long total) {
        double percent = total <= 0 ? 0.0 : Math.round((count * 1000.0 / total)) / 10.0;
        return new OverviewDistribution(name, count, percent, count + " module");
    }

    private String overviewHealthStatus(long criticalModules, int overviewScore) {
        if (criticalModules > 0 || overviewScore < 80) return "CRITICAL";
        if (overviewScore < 90) return "WARNING";
        return "NORMAL";
    }

    private String summary(String status, long criticalModules, long attentionModules, long warningFindings) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Server overview membutuhkan tindakan segera. Critical module " + criticalModules + ", warning finding " + warningFindings + ".";
        }
        if (attentionModules > 0 || warningFindings > 0) {
            return "Server overview stabil. Ada attention operasional " + attentionModules + " module dan warning finding " + warningFindings + " yang perlu review.";
        }
        return "Server overview stabil dan aman. Semua domain utama berada dalam threshold tahap awal.";
    }

    private String recommendation(String status, boolean securityFoundationReady, long warningFindings, long attentionModules) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan domain CRITICAL, cek Gateway/Traffic/Database terlebih dahulu, lalu eskalasi ke Storage/Security jika terkait.";
        }
        if (warningFindings > 0 || attentionModules > 0) {
            return warningFindings > 0
                    ? "Review Attention Summary. Prioritaskan retention policy, growth baseline, dan readiness security collector."
                    : "Review module attention dan lanjutkan monitoring sebelum user/device bertambah besar.";
        }
        if (!securityFoundationReady) {
            return "Lanjutkan monitoring dan selesaikan security event foundation agar Overview membaca security event nyata.";
        }
        return "System stable, safe, dan siap dipakai sebagai halaman utama NOC sebelum masuk ke domain detail.";
    }

    private int scoreFromStatus(String status) {
        return switch (normalize(status)) {
            case "EMERGENCY" -> 20;
            case "CRITICAL" -> 55;
            case "WARNING", "ATTENTION" -> 88;
            default -> 96;
        };
    }

    private int averageScore(List<MonitorModule> modules) {
        if (modules.isEmpty()) return 0;
        int total = modules.stream().mapToInt(MonitorModule::getScore).sum();
        return clamp(Math.round((float) total / modules.size()));
    }

    private boolean isActionableInsight(OverviewInsight insight) {
        return severityRank(insight.getSeverity()) <= severityRank("WARNING") && !"RESOLVED".equalsIgnoreCase(insight.getStatus());
    }

    private boolean isCriticalStatus(String status) {
        String normalized = normalize(status);
        return "CRITICAL".equals(normalized) || "EMERGENCY".equals(normalized);
    }

    private boolean isAttentionStatus(String status) {
        String normalized = normalize(status);
        return "WARNING".equals(normalized) || ATTENTION.equals(normalized);
    }

    private String moduleStatusLabel(String status) {
        String normalized = normalize(status);
        return "WARNING".equals(normalized) ? ATTENTION : normalized;
    }

    private int severityRank(OverviewInsight insight) {
        return severityRank(insight.getSeverity());
    }

    private int severityRank(String severity) {
        return switch (normalize(severity)) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING", "ATTENTION" -> 2;
            default -> 3;
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "NORMAL";
        String normalized = value.trim().toUpperCase();
        return "ATTENTION".equals(normalized) ? ATTENTION : normalized;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private AiOpsOverviewResponse safeAiOps() {
        try { return aiOpsMonitorService.getOverview(); } catch (Exception e) {
            AiOpsOverviewResponse fallback = new AiOpsOverviewResponse();
            fallback.setSystemStatus("WARNING");
            fallback.setMainIssue("AI Ops Monitor API belum terbaca");
            fallback.setRecommendedAction("Jalankan backend dan cek endpoint AI Ops.");
            return fallback;
        }
    }

    private GatewayMonitorOverviewResponse safeGateway() {
        try { return gatewayMonitorService.getOverview(); } catch (Exception e) {
            GatewayMonitorOverviewResponse fallback = new GatewayMonitorOverviewResponse();
            fallback.setStatus("WARNING");
            fallback.setGatewayScore(70);
            fallback.setSummary("Gateway Monitor API belum terbaca.");
            fallback.setRecommendation("Cek endpoint Gateway Monitor.");
            return fallback;
        }
    }

    private TrafficMonitorOverviewResponse safeTraffic() {
        try { return trafficMonitorService.getOverview(); } catch (Exception e) {
            TrafficMonitorOverviewResponse fallback = new TrafficMonitorOverviewResponse();
            fallback.setStatus("WARNING");
            fallback.setTrafficScore(70);
            fallback.setSummary("Traffic Monitor API belum terbaca.");
            fallback.setRecommendation("Cek endpoint Traffic Monitor.");
            return fallback;
        }
    }

    private DatabaseMonitorOverviewResponse safeDatabase() {
        try { return databaseMonitorService.getOverview(); } catch (Exception e) {
            DatabaseMonitorOverviewResponse fallback = new DatabaseMonitorOverviewResponse();
            fallback.setStatus("WARNING");
            fallback.setDatabaseScore(70);
            fallback.setSummary("Database Monitor API belum terbaca.");
            fallback.setRecommendation("Cek endpoint Database Monitor.");
            return fallback;
        }
    }

    private StorageMonitorOverviewResponse safeStorage() {
        try { return storageMonitorService.getOverview(); } catch (Exception e) {
            StorageMonitorOverviewResponse fallback = new StorageMonitorOverviewResponse();
            fallback.setStatus("WARNING");
            fallback.setStorageScore(70);
            fallback.setSummary("Storage Monitor API belum terbaca.");
            fallback.setRecommendation("Cek endpoint Storage Monitor.");
            return fallback;
        }
    }

    private SecurityMonitorOverviewResponse safeSecurity() {
        try { return securityMonitorService.getOverview(); } catch (Exception e) {
            SecurityMonitorOverviewResponse fallback = new SecurityMonitorOverviewResponse();
            fallback.setStatus("WARNING");
            fallback.setSecurityScore(70);
            fallback.setSummary("Security Monitor API belum terbaca.");
            fallback.setRecommendation("Cek endpoint Security Monitor.");
            return fallback;
        }
    }
}
