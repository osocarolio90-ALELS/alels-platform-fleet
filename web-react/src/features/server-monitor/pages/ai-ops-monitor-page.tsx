import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  Bot,
  CheckCircle2,
  Database,
  HardDrive,
  Network,
  RadioTower,
  ShieldAlert,
  ShieldCheck,
  TrendingUp,
  Wifi,
  WifiOff
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getAiOpsOverview, type AiOpsFinding, type AiOpsMetric } from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDomainGrid,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  MonitorFindingsTable,
  toneFor,
  visualFor
} from "@/features/server-monitor/components/server-monitor-ui";
import { cn } from "@/lib/utils";

type Severity = "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;

const fallbackOverview = {
  systemStatus: "WARNING",
  mainIssue: "AI Ops API belum tersedia atau backend belum berjalan",
  impact: "UI bisa diuji, tetapi analisa real server belum aktif sampai backend endpoint /api/server-monitor/ai-ops/overview berjalan.",
  recommendedAction: "Jalankan backend, pastikan migration 004_ai_ops_monitor.sql sudah dieksekusi, lalu refresh halaman ini.",
  generatedAt: null,
  metrics: [
    {
      key: "api_status",
      label: "AI Ops API",
      value: "OFFLINE",
      unit: "",
      severity: "WARNING",
      description: "Status koneksi frontend ke backend AI Ops."
    }
  ],
  findings: [
    {
      id: "ai-ops-api-offline",
      severity: "WARNING",
      category: "AI Ops",
      title: "Backend AI Ops belum terbaca",
      problem: "Frontend belum mendapat response valid dari endpoint AI Ops.",
      impact: "Belum ada analisa server real-time.",
      recommendedAction: "Jalankan backend dan cek token login SUPERADMIN.",
      status: "OPEN"
    }
  ]
};


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

function buildAiOpsAttention(findings: AiOpsFinding[]) {
  const finding = findings.find((item) => isActionableSeverity(item.severity));
  if (!finding) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "AI Ops stable and safe",
      summary: "Tidak ada finding warning/critical dari AI Ops.",
      action: "Lanjutkan monitoring baseline dan tambah threshold setelah traffic nyata meningkat."
    };
  }
  return {
    status: finding.severity,
    label: "Attention Required",
    title: finding.title,
    summary: finding.impact || finding.problem || "Ada finding AI Ops yang perlu ditinjau.",
    action: finding.recommendedAction,
    footer: `${finding.category || "AI Ops"} · ${finding.status || "OPEN"}`
  };
}


function findMetric(metrics: AiOpsMetric[], keys: string[]) {
  return metrics.find((metric) => keys.includes(metric.key));
}

function metricValue(metrics: AiOpsMetric[], keys: string[], fallback = "0") {
  return findMetric(metrics, keys)?.value ?? fallback;
}

function metricSeverity(metrics: AiOpsMetric[], keys: string[], fallback = "NORMAL") {
  return findMetric(metrics, keys)?.severity ?? fallback;
}

function metricUnit(metrics: AiOpsMetric[], keys: string[], fallback = "") {
  return findMetric(metrics, keys)?.unit ?? fallback;
}

function getHealthScore(status: string, metrics: AiOpsMetric[]) {
  const baseScore = status === "NORMAL" ? 96 : status === "WARNING" ? 78 : status === "CRITICAL" ? 48 : 25;
  const penalty = metrics.reduce((total, metric) => {
    if (metric.severity === "WARNING") return total + 3;
    if (metric.severity === "CRITICAL") return total + 12;
    if (metric.severity === "EMERGENCY") return total + 20;
    return total;
  }, 0);

  return Math.max(1, Math.min(100, baseScore - penalty));
}

function buildOperationsKpis(metrics: AiOpsMetric[]) {
  return [
    {
      label: "Online Devices",
      value: metricValue(metrics, ["connected_devices"], "0"),
      unit: "device",
      severity: metricSeverity(metrics, ["connected_devices"]),
      icon: <Wifi className="h-6 w-6" />,
      note: "Device aktif dari gateway atau presence ONLINE."
    },
    {
      label: "Offline Devices",
      value: "0",
      unit: "device",
      severity: "NORMAL",
      icon: <WifiOff className="h-6 w-6" />,
      note: "Akan mengikuti Gateway Monitor dengan threshold offline 7 menit."
    },
    {
      label: "Packet / Minute",
      value: metricValue(metrics, ["telemetry_minute"], "0"),
      unit: "pkt/min",
      severity: metricSeverity(metrics, ["telemetry_minute"]),
      icon: <RadioTower className="h-6 w-6" />,
      note: "Jumlah telemetry masuk dalam 1 menit terakhir."
    },
    {
      label: "Open Alerts",
      value: metricValue(metrics, ["open_ai_alerts"], "0"),
      unit: "alert",
      severity: metricSeverity(metrics, ["open_ai_alerts"]),
      icon: <ShieldAlert className="h-6 w-6" />,
      note: "Alert AI Ops yang belum diselesaikan."
    }
  ];
}

function buildHealthDomains(metrics: AiOpsMetric[]) {
  const dbStatus = findMetric(metrics, ["db_status"]);
  const dbConnections = findMetric(metrics, ["db_connections"]);
  const storage = findMetric(metrics, ["disk_used"]);
  const heap = findMetric(metrics, ["heap_used"]);
  const load = findMetric(metrics, ["system_load"]);
  const connected = findMetric(metrics, ["connected_devices"]);
  const traffic = findMetric(metrics, ["telemetry_minute"]);
  const alerts = findMetric(metrics, ["open_ai_alerts"]);

  return [
    {
      label: "Database",
      value: dbStatus?.severity === "NORMAL" ? "100%" : dbStatus ? "78%" : "-",
      severity: dbStatus?.severity ?? "NORMAL",
      icon: <Database className="h-5 w-5" />,
      details: [`Status ${dbStatus?.value ?? "UP"}`, `${dbConnections?.value ?? "0"} connection`, "Latency baseline: learning"]
    },
    {
      label: "Gateway",
      value: connected?.severity === "NORMAL" ? "95%" : connected ? "75%" : "-",
      severity: connected?.severity ?? "NORMAL",
      icon: <Network className="h-5 w-5" />,
      details: [`${connected?.value ?? "0"} online`, `${traffic?.value ?? "0"} pkt/min`, "GSM/WiFi detail di Gateway Monitor"]
    },
    {
      label: "Storage",
      value: storage?.severity === "NORMAL" ? "92%" : storage ? "70%" : "-",
      severity: storage?.severity ?? "NORMAL",
      icon: <HardDrive className="h-5 w-5" />,
      details: [`Used ${storage?.value ?? "0"}${storage?.unit ? ` ${storage.unit}` : "%"}`, "Growth: stable", "Retention: baseline"]
    },
    {
      label: "Backend & Security",
      value: alerts?.severity === "NORMAL" ? "98%" : alerts ? "76%" : "-",
      severity: alerts?.severity ?? "NORMAL",
      icon: <ShieldCheck className="h-5 w-5" />,
      details: [`Heap ${heap?.value ?? "-"}${heap?.unit ?? "%"}`, `Load ${load?.value ?? "-"}`, `${alerts?.value ?? "0"} open alert`]
    }
  ];
}

function buildTrendCards(status: string) {
  return [
    { label: "Storage Growth", value: "Stable", detail: "Trend detail aktif setelah collector storage berjalan.", severity: status },
    { label: "Device Traffic Baseline", value: "Learning", detail: "AI membuat baseline setelah traffic device nyata masuk.", severity: status },
    { label: "Security Risk", value: status === "NORMAL" ? "Low" : "Watch", detail: "Berdasarkan open alert dan event keamanan tahap awal.", severity: status }
  ];
}

function TrendSignal({ label, value, detail, severity }: { label: string; value: string; detail: string; severity: string }) {
  const visual = visualFor(severity);
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border bg-muted/45 dark:bg-muted/20 p-4">
      <div className={cn("grid h-11 w-11 shrink-0 place-items-center rounded-xl", visual.bg, visual.text)}>
        <TrendingUp className="h-5 w-5" />
      </div>
      <div>
        <p className="text-sm font-extrabold text-card-foreground">{label}: {value}</p>
        <p className="text-xs font-semibold leading-5 text-muted-foreground">{detail}</p>
      </div>
    </div>
  );
}

export function AiOpsMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["ai-ops-overview"],
    queryFn: getAiOpsOverview,
    refetchInterval: 30_000,
    staleTime: 15_000
  });

  const overview = data ?? fallbackOverview;
  const healthScore = getHealthScore(overview.systemStatus, overview.metrics);
  const operationsKpis = buildOperationsKpis(overview.metrics);
  const healthDomains = buildHealthDomains(overview.metrics);
  const trendCards = buildTrendCards(overview.systemStatus);

  const columns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: AiOpsFinding) => row.severity,
        render: (row: AiOpsFinding) => (
          <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">
            {row.severity}
          </Badge>
        )
      },
      {
        key: "title",
        label: "Problem",
        value: (row: AiOpsFinding) => row.title,
        render: (row: AiOpsFinding) => <span className="font-extrabold text-card-foreground">{row.title}</span>
      },
      { key: "impact", label: "Impact", value: (row: AiOpsFinding) => row.impact || "-" },
      { key: "recommendedAction", label: "Action", value: (row: AiOpsFinding) => row.recommendedAction },
      {
        key: "status",
        label: "Status",
        value: (row: AiOpsFinding) => row.status,
        render: (row: AiOpsFinding) => <Badge tone="muted">{row.status}</Badge>
      }
    ],
    []
  );

  return (
    <section className="server-operations-ui space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS AI OPS CENTER"
        icon={<Bot className="h-4 w-4" />}
        title="Server Monitor AI Analysis"
        description={
          <>
            NOC-style AI monitor untuk membaca health backend, PostgreSQL, gateway, storage, device connection, packet traffic,
            lalu memberi prioritas tindakan operasional untuk Superadmin.
          </>
        }
        statusLabel="Overall Health"
        status={healthStatusFromScore(healthScore, overview.systemStatus)}
        scoreLabel="Health Score"
        score={healthScore}
        updatedAt={overview.generatedAt}
        trendLabel="Stable"
        attention={buildAiOpsAttention(overview.findings)}
      />

      <MonitorKpiGrid cards={operationsKpis} />

      <div className="grid gap-4 xl:grid-cols-[1.25fr_1fr]">
        <MonitorAssessmentCard
          title="AI Assessment & Recommended Action"
          icon={overview.systemStatus === "NORMAL" ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <AlertTriangle className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.mainIssue}
          statusDetail="AI Ops menampilkan prioritas operasional, bukan hanya angka mentah."
          impact={overview.impact || "-"}
          action={overview.recommendedAction}
          severity={overview.systemStatus}
        />

        <Card className="border shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg text-card-foreground">AI Trend Signals</CardTitle>
            <p className="text-sm font-semibold text-muted-foreground">Baseline awal untuk prediksi storage, traffic, dan security.</p>
          </CardHeader>
          <CardContent className="grid gap-3">
            {trendCards.map((trend) => (
              <TrendSignal key={trend.label} {...trend} />
            ))}
          </CardContent>
        </Card>
      </div>

      <MonitorDomainGrid cards={healthDomains} />

      <MonitorSnapshot
        title="Raw Metric Snapshot"
        description="Ringkasan kecil untuk verifikasi teknis. Detail besar dipindah ke submenu spesifik agar tidak duplikatif."
        fallbackIcon={<Activity className="h-4 w-4" />}
        items={[
          { key: "db_status", label: "PostgreSQL", value: metricValue(overview.metrics, ["db_status"], "UP") },
          { key: "db_connections", label: "DB Connections", value: metricValue(overview.metrics, ["db_connections"], "0"), unit: "conn" },
          { key: "heap_used", label: "Backend Heap", value: metricValue(overview.metrics, ["heap_used"], "0"), unit: metricUnit(overview.metrics, ["heap_used"], "%") },
          { key: "system_load", label: "System Load", value: metricValue(overview.metrics, ["system_load"], "0") }
        ]}
      />

      <MonitorFindingsTable
        title="AI Findings & Actions"
        description="Tabel ringkas untuk masalah, dampak, dan tindakan. Kolom dibuat minimal agar operator cepat mengambil keputusan."
        status={overview.systemStatus}
        findings={overview.findings}
        columns={columns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search AI finding..."
        emptyMessage="No AI Ops findings."
        isLoading={isLoading}
        errorMessage={isError ? "Backend AI Ops belum terbaca. Fallback UI ditampilkan agar layout tetap bisa diuji." : null}
      />
    </section>
  );
}
