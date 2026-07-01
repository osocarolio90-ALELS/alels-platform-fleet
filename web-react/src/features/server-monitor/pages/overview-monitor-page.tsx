import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  BrainCircuit,
  Database,
  Gauge,
  HardDrive,
  Network,
  ServerCog,
  ShieldCheck,
  Wifi
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  getOverviewMonitorOverview,
  type OverviewMetric,
  type OverviewMonitorInsight,
  type OverviewMonitorModule
} from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDistributionBars,
  MonitorFindingsTable,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const moduleIcons: Record<string, JSX.Element> = {
  ai_ops: <BrainCircuit className="h-4 w-4" />,
  gateway: <Wifi className="h-4 w-4" />,
  traffic: <Activity className="h-4 w-4" />,
  database: <Database className="h-4 w-4" />,
  storage: <HardDrive className="h-4 w-4" />,
  security: <ShieldCheck className="h-4 w-4" />
};

const fallbackOverview = {
  status: "WARNING",
  overviewScore: 70,
  generatedAt: null,
  summary: "Overview Monitor API belum tersedia.",
  recommendation: "Jalankan backend dan pastikan endpoint Overview Monitor tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "overview_api", label: "Overview API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data Overview Monitor dari backend." }
  ],
  modules: [],
  healthDistribution: [],
  priorityDistribution: [],
  insights: [
    {
      id: "overview-api-offline",
      severity: "WARNING",
      category: "Overview",
      title: "Overview Monitor API belum terbaca",
      impact: "Status AI Ops, Gateway, Traffic, Database, Storage, dan Security belum bisa diagregasi.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Overview Monitor.",
      status: "OPEN"
    }
  ]
};

const topMetricKeys = ["overview_score", "normal_modules", "attention_modules", "critical_modules"];
const platformMetricKeys = ["total_devices", "online_devices", "active_users", "security_foundation", "active_alerts", "warning_findings"];

function findMetric(metrics: OverviewMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: OverviewMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricNumber(metrics: OverviewMetric[], key: string) {
  return Number(metricValue(metrics, key, "0")) || 0;
}

function buildTopCards(metrics: OverviewMetric[]) {
  return topMetricKeys.map((key) => {
    const metric = findMetric(metrics, key);
    return {
      key,
      label: metric?.label ?? key,
      value: metric?.value ?? "0",
      unit: metric?.unit ?? "",
      severity: metric?.severity ?? "NORMAL",
      icon:
        key === "overview_score" ? <Gauge className="h-6 w-6" /> :
        key === "critical_modules" ? <AlertTriangle className="h-6 w-6" /> :
        key === "normal_modules" ? <ShieldCheck className="h-6 w-6" /> :
        <ServerCog className="h-6 w-6" />,
      note: metric?.description ?? "Overview metric."
    };
  });
}

function moduleSnapshotItems(modules: OverviewMonitorModule[]) {
  return modules.map((module) => ({
    key: module.key,
    label: module.label,
    value: String(module.score),
    unit: `% ${module.status === "WARNING" ? "ATTENTION" : module.status}`,
    icon: moduleIcons[module.key] ?? <ServerCog className="h-4 w-4" />
  }));
}

function metricSnapshotItems(metrics: OverviewMetric[]) {
  return platformMetricKeys.map((key) => {
    const metric = findMetric(metrics, key);
    return {
      key,
      label: metric?.label ?? key,
      value: metric?.value ?? "0",
      unit: metric?.unit,
      icon:
        key === "security_foundation" ? <ShieldCheck className="h-4 w-4" /> :
        key === "online_devices" ? <Wifi className="h-4 w-4" /> :
        key === "warning_findings" ? <AlertTriangle className="h-4 w-4" /> :
        <Activity className="h-4 w-4" />
    };
  });
}

function scoreExplanation(modules: OverviewMonitorModule[]) {
  if (!modules.length) return "Waiting domain score baseline.";
  const lowest = [...modules].sort((a, b) => a.score - b.score)[0];
  return `Weighted average of ${modules.length} domains. Lowest: ${lowest.label} ${lowest.score}%.`;
}

export function OverviewMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["overview-monitor"],
    queryFn: getOverviewMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const warningFindings = metricValue(overview.metrics, "warning_findings", String(overview.insights.length));
  const attentionModules = metricNumber(overview.metrics, "attention_modules");
  const criticalModules = metricNumber(overview.metrics, "critical_modules");
  const firstActionableInsight = overview.insights.find((insight) => ["ATTENTION", "WARNING", "CRITICAL", "EMERGENCY"].includes(insight.severity?.toUpperCase?.() ?? ""));
  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: OverviewMonitorInsight) => row.severity,
        render: (row: OverviewMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "category", label: "Domain", value: (row: OverviewMonitorInsight) => row.category },
      { key: "title", label: "Problem", value: (row: OverviewMonitorInsight) => row.title, render: (row: OverviewMonitorInsight) => <span className="font-extrabold text-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: OverviewMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: OverviewMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: OverviewMonitorInsight) => row.status, render: (row: OverviewMonitorInsight) => <Badge tone="muted">{row.status}</Badge> }
    ],
    []
  );

  return (
    <section className="server-operations-ui space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS OVERVIEW NOC"
        icon={<ServerCog className="h-4 w-4" />}
        title="Overview Monitor"
        description={
          <>
            Halaman Executive NOC untuk menggabungkan status AI Ops, Gateway, Traffic, Database, Storage, dan Security sebelum ALELS masuk skala 1 juta device aktif.
          </>
        }
        statusLabel="System Health"
        status={overview.status}
        scoreLabel="Score"
        score={overview.overviewScore}
        trendLabel={metricValue(overview.metrics, "security_foundation", "BASELINE") === "READY" ? "Foundation ready" : "Baseline mode"}
        updatedAt={overview.generatedAt}
        scoreNote={scoreExplanation(overview.modules)}
        attention={{
          status: criticalModules > 0 ? "CRITICAL" : attentionModules > 0 || Number(warningFindings) > 0 ? "WARNING" : "NORMAL",
          label: criticalModules > 0 ? "Critical Attention" : attentionModules > 0 || Number(warningFindings) > 0 ? "Attention Required" : "No Attention",
          title: firstActionableInsight ? `${firstActionableInsight.category}: ${firstActionableInsight.title}` : "System stable, secure, and safe.",
          summary: firstActionableInsight?.action ?? "Tidak ada attention atau critical action lintas domain.",
          footer: `Attention: ${attentionModules} module · Actions: ${Number(warningFindings) || 0} finding · Critical: ${criticalModules}`
        }}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr_1fr]">
        <MonitorAssessmentCard
          title="Executive Assessment"
          icon={overview.status === "NORMAL" ? <ShieldCheck className="h-5 w-5 text-emerald-500" /> : <AlertTriangle className="h-5 w-5 text-amber-500" />}
          statusTitle={overview.summary}
          statusDetail="Fokus NOC: gateway ingestion, traffic parser, PostgreSQL, storage capacity, security event, dan AI Ops action."
          impact={criticalModules > 0 ? "Ada domain critical yang harus ditangani segera." : attentionModules > 0 || Number(warningFindings) > 0 ? "System health normal. Attention dipisahkan agar user non-teknis paham action yang perlu diarahkan ke tim teknikal." : "Seluruh domain utama berada dalam threshold awal dan siap menjadi NOC entry point."}
          action={overview.recommendation}
          severity={overview.status}
        />
        <MonitorDistributionBars
          title="Health Distribution"
          subtitle="Jumlah modul normal, attention, dan critical."
          icon={<Gauge className="h-5 w-5" />}
          items={overview.healthDistribution}
          emptyMessage="Belum ada health distribution."
        />
        <MonitorDistributionBars
          title="Module Scores"
          subtitle="Score breakdown otomatis dari seluruh domain monitor."
          icon={<Network className="h-5 w-5" />}
          items={overview.priorityDistribution}
          emptyMessage="Belum ada module score."
        />
      </div>

      <MonitorSnapshot
        title="Operations Snapshot"
        description="Gabungan domain score dan metrik platform prioritas agar halaman tetap padat dan tidak perlu 3 kali scroll."
        fallbackIcon={<ServerCog className="h-4 w-4" />}
        items={[...moduleSnapshotItems(overview.modules), ...metricSnapshotItems(overview.metrics)]}
      />

      <MonitorFindingsTable
        title="Overview Actions"
        description={`Hanya menampilkan warning/critical/attention lintas domain. Action finding aktif: ${warningFindings}.`}
        status={criticalModules > 0 ? "CRITICAL" : attentionModules > 0 || Number(warningFindings) > 0 ? "ATTENTION" : overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search overview action..."
        emptyMessage="Tidak ada attention, warning, atau critical action lintas domain. System stable, secure, and safe."
        isLoading={isLoading}
        errorMessage={isError ? "Overview Monitor API belum tersedia atau gagal mengambil data." : null}
      />
    </section>
  );
}
