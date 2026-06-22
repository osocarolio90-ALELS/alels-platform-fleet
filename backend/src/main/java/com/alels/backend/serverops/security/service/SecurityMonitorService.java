package com.alels.backend.serverops.security.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.alels.backend.serverops.security.model.SecurityMonitorOverviewResponse;
import com.alels.backend.serverops.security.model.SecurityMonitorOverviewResponse.SecurityDistribution;
import com.alels.backend.serverops.security.model.SecurityMonitorOverviewResponse.SecurityInsight;
import com.alels.backend.serverops.security.model.SecurityMonitorOverviewResponse.SecurityMetric;
import com.alels.backend.serverops.security.repository.SecurityMonitorRepository;

@Service
public class SecurityMonitorService {

    private final SecurityMonitorRepository repository;

    public SecurityMonitorService(SecurityMonitorRepository repository) {
        this.repository = repository;
    }

    public SecurityMonitorOverviewResponse getOverview() {
        long totalUsers = repository.totalUsers();
        long activeUsers = repository.activeUsers();
        long inactiveUsers = repository.inactiveUsers();
        long suspendedUsers = repository.suspendedUsers();
        long adminUsers = repository.adminUsers();
        long mustChangePasswordUsers = repository.mustChangePasswordUsers();
        long neverLoginUsers = repository.neverLoginUsers();
        long openSecurityAlerts = repository.openSecurityAlerts();
        long criticalSecurityAlerts = repository.criticalSecurityAlerts();
        long failedLogins = repository.failedLoginEvents24h();
        long unauthorized = repository.unauthorizedEvents24h();
        long tokenIssues = repository.tokenIssueEvents24h();
        long suspiciousIps = repository.suspiciousIpEvents24h();
        long loginEventCount = repository.loginEventCount24h();
        long auditLogCount = repository.auditLogCount24h();
        boolean dedicatedSecurityEventTable = repository.hasDedicatedSecurityEventTable();

        List<SecurityInsight> insights = new ArrayList<>();
        if (criticalSecurityAlerts > 0) {
            insights.add(insight("security-critical-alert", "CRITICAL", "Security", "Critical security alert terbuka", "Akun, API, atau akses sistem mungkin sedang dalam kondisi berisiko.", "Prioritaskan review alert security, blokir sumber serangan, dan lakukan rotasi credential jika diperlukan.", "OPEN"));
        }
        if (failedLogins >= 20) {
            insights.add(insight("failed-login-high", "CRITICAL", "Authentication", "Failed login tinggi dalam 24 jam", "Kemungkinan brute force atau credential stuffing pada akun pengguna.", "Aktifkan rate limit login, lockout sementara, CAPTCHA, dan audit IP sumber.", "OPEN"));
        } else if (failedLogins >= 5) {
            insights.add(insight("failed-login-warning", "WARNING", "Authentication", "Failed login mulai meningkat", "User experience dan keamanan login perlu dipantau sebelum jumlah user membesar.", "Pantau pola IP/email, siapkan throttling login, dan audit akun yang sering gagal login.", "OPEN"));
        }
        if (unauthorized > 0) {
            insights.add(insight("unauthorized-access", "WARNING", "Authorization", "Unauthorized access terdeteksi", "Ada request ke resource yang tidak sesuai role/scope pengguna.", "Audit RBAC, cek protected route, dan pastikan backend endpoint selalu validasi company scope dan role.", "OPEN"));
        }
        if (suspendedUsers > 0) {
            insights.add(insight("suspended-users", "WARNING", "User", "Ada user suspended", "User suspended tetap perlu diawasi agar tidak memiliki session/token aktif.", "Pastikan token user suspended tidak bisa mengakses API dan lakukan invalidasi session jika diperlukan.", "OPEN"));
        }
        if (mustChangePasswordUsers > 0) {
            insights.add(insight("password-change-required", "WARNING", "Password", "User wajib ganti password", "Akun dengan temporary password lebih berisiko jika tidak segera diganti.", "Paksa flow must-change-password pada login berikutnya dan audit user yang belum mengganti password.", "OPEN"));
        }
        if (!dedicatedSecurityEventTable) {
            insights.add(insight("security-event-baseline", "WARNING", "Audit", "Security event logging belum dedicated", "Security Monitor masih baseline dari users dan AI Ops alert; detail IP/device/session belum lengkap.", "Buat security_events, audit_logs, dan login_events untuk failed login, suspicious IP, token issue, admin action, dan RBAC violation.", "OPEN"));
        }
        if (insights.isEmpty()) {
            insights.add(insight("security-normal", "NORMAL", "Security", "Security dalam kondisi normal", "Belum ada anomali security melewati threshold tahap awal.", "Security event collector aktif. Lanjutkan monitoring, pantau failed login, suspicious IP, token issue, dan siapkan audit log retention.", "OPEN"));
        } else {
            insights.add(insight("security-baseline", "NORMAL", "Security", "Security baseline aktif", "Security Monitor sudah aktif untuk SUPERADMIN sebagai dasar audit tahap berikutnya.", dedicatedSecurityEventTable ? "Security event collector aktif. Gunakan findings sebagai prioritas audit dan pertahankan RBAC serta JWT validation." : "Gunakan baseline ini sampai event collector security dan audit log dibuat.", "OPEN"));
        }

        insights.sort(Comparator.comparingInt(this::severityRank));
        String status = insights.get(0).getSeverity();

        SecurityMonitorOverviewResponse response = new SecurityMonitorOverviewResponse();
        response.setStatus(status);
        response.setSecurityScore(score(status, failedLogins, unauthorized, tokenIssues, criticalSecurityAlerts, dedicatedSecurityEventTable));
        response.setGeneratedAt(Instant.now().toString());
        response.setSummary(summary(status, failedLogins, unauthorized, openSecurityAlerts));
        response.setRecommendation(recommendation(status, dedicatedSecurityEventTable));
        response.setMetrics(List.of(
                metric("failed_logins_24h", "Failed Login", failedLogins, "event", severityFor(failedLogins, 5, 20), "Failed login yang tercatat dari security/AI Ops alert 24 jam terakhir."),
                metric("suspicious_ip_24h", "Suspicious IP", suspiciousIps, "ip", severityFor(suspiciousIps, 1, 5), "IP mencurigakan yang terdeteksi dari alert security 24 jam terakhir."),
                metric("unauthorized_24h", "Unauthorized Access", unauthorized, "event", unauthorized > 0 ? "WARNING" : "NORMAL", "Request unauthorized/forbidden yang tercatat dalam 24 jam terakhir."),
                metric("token_issues_24h", "Token Issue", tokenIssues, "event", tokenIssues > 0 ? "WARNING" : "NORMAL", "JWT/token issue yang tercatat dalam 24 jam terakhir."),
                metric("open_security_alerts", "Open Security Alerts", openSecurityAlerts, "alert", severityFor(openSecurityAlerts, 1, 5), "Alert security yang masih OPEN."),
                metric("blocked_users", "Blocked Users", suspendedUsers, "user", suspendedUsers > 0 ? "WARNING" : "NORMAL", "User SUSPENDED yang harus dipastikan tidak memiliki akses aktif."),
                metric("admin_users", "Admin Users", adminUsers, "user", "NORMAL", "User dengan role SUPERADMIN/ADMIN/OWNER."),
                metric("must_change_password", "Must Change Password", mustChangePasswordUsers, "user", mustChangePasswordUsers > 0 ? "WARNING" : "NORMAL", "User yang wajib mengganti password."),
                metric("never_login_users", "Never Login Users", neverLoginUsers, "user", "NORMAL", "User yang belum pernah login."),
                metric("active_users", "Active Users", activeUsers, "user", "NORMAL", "User ACTIVE yang belum dihapus."),
                metric("inactive_users", "Inactive Users", inactiveUsers, "user", "NORMAL", "User INACTIVE yang belum dihapus."),
                metric("total_users", "Total Users", totalUsers, "user", "NORMAL", "Total user non-deleted di platform ALELS."),
                metric("login_events_24h", "Login Events", loginEventCount, "event", "NORMAL", "Total login success/failure yang dicatat dalam 24 jam terakhir."),
                metric("audit_logs_24h", "Audit Logs", auditLogCount, "event", dedicatedSecurityEventTable ? "NORMAL" : "WARNING", "Total audit log yang dicatat dalam 24 jam terakhir."),
                metric("security_event_collector", "Security Event Collector", dedicatedSecurityEventTable ? "READY" : "BASELINE", "", dedicatedSecurityEventTable ? "NORMAL" : "WARNING", "Ketersediaan tabel security_events, audit_logs, dan login_events.")
        ));
        response.setSignalDistribution(List.of(
                distribution("Failed Login", failedLogins, failedLogins + " event"),
                distribution("Unauthorized", unauthorized, unauthorized + " event"),
                distribution("Suspicious IP", suspiciousIps, suspiciousIps + " ip"),
                distribution("Token Issue", tokenIssues, tokenIssues + " event"),
                distribution("Open Alerts", openSecurityAlerts, openSecurityAlerts + " alert"),
                distribution("Login Events", loginEventCount, loginEventCount + " event")
        ));
        response.setUserRiskDistribution(List.of(
                distribution("Active Users", activeUsers, activeUsers + " user"),
                distribution("Admin Users", adminUsers, adminUsers + " user"),
                distribution("Blocked Users", suspendedUsers, suspendedUsers + " user"),
                distribution("Must Change Password", mustChangePasswordUsers, mustChangePasswordUsers + " user"),
                distribution("Never Login", neverLoginUsers, neverLoginUsers + " user")
        ));
        response.setInsights(insights);
        return response;
    }

    private SecurityMetric metric(String key, String label, long value, String unit, String severity, String description) {
        return new SecurityMetric(key, label, String.valueOf(value), unit, severity, description);
    }

    private SecurityMetric metric(String key, String label, String value, String unit, String severity, String description) {
        return new SecurityMetric(key, label, value, unit, severity, description);
    }

    private SecurityDistribution distribution(String name, long count, String displayValue) {
        return new SecurityDistribution(name, Math.max(0, count), 0, displayValue);
    }

    private SecurityInsight insight(String id, String severity, String category, String title, String impact, String action, String status) {
        return new SecurityInsight(id, severity, category, title, impact, action, status);
    }

    private String severityFor(long value, long warning, long critical) {
        if (value >= critical) return "CRITICAL";
        if (value >= warning) return "WARNING";
        return "NORMAL";
    }

    private int severityRank(SecurityInsight insight) {
        return switch (insight.getSeverity()) {
            case "EMERGENCY" -> 0;
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            default -> 3;
        };
    }

    private int score(String status, long failedLogins, long unauthorized, long tokenIssues, long criticalAlerts, boolean dedicatedSecurityEventTable) {
        int base = switch (status) {
            case "EMERGENCY" -> 20;
            case "CRITICAL" -> 52;
            case "WARNING" -> 88;
            default -> 96;
        };
        int penalty = 0;
        if (failedLogins >= 5) penalty += 3;
        if (failedLogins >= 20) penalty += 8;
        if (unauthorized > 0) penalty += 4;
        if (tokenIssues > 0) penalty += 3;
        if (criticalAlerts > 0) penalty += 12;
        if (!dedicatedSecurityEventTable) penalty += 0; // baseline warning is informational at this stage
        return Math.max(1, Math.min(100, base - penalty));
    }

    private String summary(String status, long failedLogins, long unauthorized, long openAlerts) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Security butuh perhatian segera. Failed login " + failedLogins + ", unauthorized " + unauthorized + ", open alert " + openAlerts + ".";
        }
        if ("WARNING".equals(status)) {
            return "Security baseline mode aktif. Event collector dan audit policy perlu dikunci sebelum skala user besar.";
        }
        return "Security normal. Failed login " + failedLogins + ", unauthorized " + unauthorized + ", open alert " + openAlerts + ".";
    }

    private String recommendation(String status, boolean dedicatedSecurityEventTable) {
        if ("CRITICAL".equals(status) || "EMERGENCY".equals(status)) {
            return "Prioritaskan blokir sumber serangan, audit token/session, dan lindungi endpoint admin serta login dari brute force.";
        }
        if (!dedicatedSecurityEventTable) {
            return "Buat security_events, audit_logs, dan login_events untuk failed login, suspicious IP, JWT issue, admin action, dan RBAC violation.";
        }
        if ("WARNING".equals(status)) {
            return "Tinjau security findings, validasi RBAC backend, dan aktifkan throttling login sebelum user aktif meningkat.";
        }
        return "Lanjutkan monitoring. Pertahankan RBAC, JWT validation, dan audit event collector untuk skala user besar.";
    }
}
