import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  Ban,
  KeyRound,
  LockKeyhole,
  ShieldAlert,
  ShieldCheck,
  Users
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { StatusIndicator } from "@/components/ui/status-indicator";
import { getSecurityMonitorOverview, type SecurityMetric, type SecurityMonitorInsight } from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDistributionBars,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  MonitorFindingsTable,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const metricIcons: Record<string, JSX.Element> = {
  failed_logins_24h: <LockKeyhole className="h-5 w-5" />,
  suspicious_ip_24h: <ShieldAlert className="h-5 w-5" />,
  unauthorized_24h: <Ban className="h-5 w-5" />,
  token_issues_24h: <KeyRound className="h-5 w-5" />,
  open_security_alerts: <AlertTriangle className="h-5 w-5" />,
  blocked_users: <Users className="h-5 w-5" />,
  admin_users: <Users className="h-5 w-5" />,
  security_event_collector: <ShieldCheck className="h-5 w-5" />
};

const fallbackOverview = {
  status: "WARNING",
  securityScore: 70,
  generatedAt: null,
  summary: "Security Monitor API belum tersedia.",
  recommendation: "Jalankan backend dan pastikan endpoint security overview tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "security_api", label: "Security API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data Security Monitor dari backend." }
  ],
  signalDistribution: [],
  userRiskDistribution: [],
  insights: [
    {
      id: "security-api-offline",
      severity: "WARNING",
      category: "Security",
      title: "Security Monitor API belum terbaca",
      impact: "Failed login, suspicious IP, token issue, user risk, dan audit readiness belum bisa dianalisa dari UI.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Security Monitor.",
      status: "OPEN"
    }
  ]
};

const snapshotKeys = [
  "failed_logins_24h",
  "unauthorized_24h",
  "suspicious_ip_24h",
  "token_issues_24h",
  "open_security_alerts",
  "blocked_users",
  "admin_users",
  "security_event_collector"
];


function isActionableSeverity(severity?: string | null) {
  const value = (severity || "NORMAL").toUpperCase();
  return value === "ATTENTION" || value === "WARNING" || value === "CRITICAL" || value === "EMERGENCY";
}

function healthStatusFromScore(score: number, fallbackStatus: string) {
  const status = (fallbackStatus || "NORMAL").toUpperCase();
  if (status === "CRITICAL" || status === "EMERGENCY") return status;
  if (score >= 90) return "NORMAL";
  return status;
}

function buildSecurityAttention(insights: SecurityMonitorInsight[]) {
  const insight = insights.find((item) => isActionableSeverity(item.severity));
  if (!insight) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "Security stable and safe",
      summary: "Tidak ada finding warning/critical pada failed login, suspicious IP, unauthorized access, token issue, atau open security alert.",
      action: "Lanjutkan monitoring security event collector, failed login, suspicious IP, token issue, dan audit retention."
    };
  }
  return {
    status: insight.severity,
    label: "Attention Required",
    title: insight.title,
    summary: insight.impact || "Ada finding security yang perlu ditinjau.",
    action: insight.action,
    footer: `${insight.category || "Security"} · ${insight.status || "OPEN"}`
  };
}


function findMetric(metrics: SecurityMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: SecurityMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricSeverity(metrics: SecurityMetric[], key: string, fallback = "NORMAL") {
  return findMetric(metrics, key)?.severity ?? fallback;
}

function buildTopCards(metrics: SecurityMetric[]) {
  return [
    {
      key: "failed_logins_24h",
      label: "Failed Login",
      value: metricValue(metrics, "failed_logins_24h"),
      unit: "event",
      severity: metricSeverity(metrics, "failed_logins_24h"),
      icon: <LockKeyhole className="h-6 w-6" />,
      note: "Failed login 24 jam terakhir."
    },
    {
      key: "suspicious_ip_24h",
      label: "Suspicious IP",
      value: metricValue(metrics, "suspicious_ip_24h"),
      unit: "ip",
      severity: metricSeverity(metrics, "suspicious_ip_24h"),
      icon: <ShieldAlert className="h-6 w-6" />,
      note: "IP mencurigakan untuk rate limit/blokir."
    },
    {
      key: "unauthorized_24h",
      label: "Unauthorized",
      value: metricValue(metrics, "unauthorized_24h"),
      unit: "event",
      severity: metricSeverity(metrics, "unauthorized_24h"),
      icon: <Ban className="h-6 w-6" />,
      note: "Request forbidden/unauthorized yang wajib diaudit."
    },
    {
      key: "token_issues_24h",
      label: "Token Issue",
      value: metricValue(metrics, "token_issues_24h"),
      unit: "event",
      severity: metricSeverity(metrics, "token_issues_24h"),
      icon: <KeyRound className="h-6 w-6" />,
      note: "Masalah token/session/expiry tidak valid."
    }
  ];
}

export function SecurityMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["security-monitor-overview"],
    queryFn: getSecurityMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: SecurityMonitorInsight) => row.severity,
        render: (row: SecurityMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "title", label: "Problem", value: (row: SecurityMonitorInsight) => row.title, render: (row: SecurityMonitorInsight) => <span className="font-extrabold text-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: SecurityMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: SecurityMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: SecurityMonitorInsight) => row.status, render: (row: SecurityMonitorInsight) => <StatusIndicator status={row.status} /> }
    ],
    []
  );

  return (
    <section className="server-operations-ui space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS SECURITY NOC"
        icon={<ShieldCheck className="h-4 w-4" />}
        title="Security Monitor"
        description={
          <>
            Halaman operasi security untuk memantau failed login, suspicious IP, unauthorized access, token issue, user risk,
            audit readiness, dan proteksi role sebelum skala user aktif membesar.
          </>
        }
        statusLabel="Security Status"
        status={healthStatusFromScore(overview.securityScore, overview.status)}
        scoreLabel="Security Score"
        score={overview.securityScore}
        trendLabel={metricValue(overview.metrics, "security_event_collector", "BASELINE") === "READY" ? "Event collector ready" : "Baseline mode"}
        updatedAt={overview.generatedAt}
        attention={buildSecurityAttention(overview.insights)}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-5 xl:grid-cols-[1.1fr_1fr_1fr]">
        <MonitorAssessmentCard
          title="Security Assessment"
          icon={overview.status === "NORMAL" ? <ShieldCheck className="h-5 w-5 text-emerald-600" /> : <ShieldAlert className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.summary}
          statusDetail="Fokus pada authentication, authorization, token readiness, suspicious IP, user risk, admin role, dan audit readiness."
          impact={overview.status === "NORMAL" ? "Belum ada anomali security melewati threshold tahap awal." : "Security baseline berjalan, tetapi audit/event collector dedicated perlu dikunci sebelum user aktif membesar."}
          action={overview.recommendation}
          severity={overview.status}
        />
        <MonitorDistributionBars
          title="Security Signals"
          subtitle="Sinyal utama dari login, authorization, token, IP, dan alert terbuka."
          icon={<AlertTriangle className="h-5 w-5" />}
          items={overview.signalDistribution}
          emptyMessage="Belum ada signal security aktif."
        />
        <MonitorDistributionBars
          title="User & Role Risk"
          subtitle="Distribusi user aktif, admin, blocked, password reset, dan never-login."
          icon={<Users className="h-5 w-5" />}
          items={overview.userRiskDistribution}
          emptyMessage="Belum ada data user risk."
        />
      </div>

      <MonitorSnapshot
        title="Security Technical Snapshot"
        description="Metrik prioritas tanpa mengulang semua KPI utama agar halaman tetap padat."
        fallbackIcon={<ShieldCheck className="h-4 w-4" />}
        items={snapshotKeys.map((key) => {
          const metric = findMetric(overview.metrics, key);
          return {
            key,
            label: metric?.label ?? key,
            value: metric?.value ?? "0",
            unit: metric?.unit,
            icon: metricIcons[key] ?? <ShieldCheck className="h-4 w-4" />
          };
        })}
      />

      <MonitorFindingsTable
        title="Security Findings & Actions"
        description="Masalah security yang perlu diprioritaskan sebelum jumlah user aktif bertambah besar."
        status={overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search security finding..."
        emptyMessage="Belum ada finding security."
        isLoading={isLoading}
        errorMessage={isError ? "Security Monitor API belum tersedia atau gagal mengambil data." : null}
      />
    </section>
  );
}
