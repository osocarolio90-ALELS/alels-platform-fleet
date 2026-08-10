import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Gauge,
  Network,
  RadioTower,
  Route,
  ShieldAlert,
  TimerReset,
  Waves
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { StatusIndicator } from "@/components/ui/status-indicator";
import { getTrafficMonitorOverview, type TrafficMetric, type TrafficMonitorInsight } from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDistributionBars,
  MonitorDomainGrid,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  MonitorFindingsTable,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const metricIcons: Record<string, JSX.Element> = {
  packets_minute: <RadioTower className="h-5 w-5" />,
  packets_second: <Activity className="h-5 w-5" />,
  telemetry_minute: <Waves className="h-5 w-5" />,
  invalid_packets_hour: <ShieldAlert className="h-5 w-5" />,
  unknown_io_count: <AlertTriangle className="h-5 w-5" />,
  avg_packet_size: <Gauge className="h-5 w-5" />,
  raw_packets_hour: <TimerReset className="h-5 w-5" />,
  parser_valid_rate: <CheckCircle2 className="h-5 w-5" />
};

const fallbackOverview = {
  status: "WARNING",
  trafficScore: 70,
  generatedAt: null,
  summary: "Traffic Monitor API belum tersedia atau backend belum berjalan.",
  recommendation: "Jalankan backend dan pastikan endpoint /api/server-monitor/traffic/overview tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "traffic_api", label: "Traffic API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data traffic dari backend." }
  ],
  protocolDistribution: [],
  channelDistribution: [],
  insights: [
    {
      id: "traffic-api-offline",
      severity: "WARNING",
      category: "Traffic",
      title: "Traffic Monitor API belum terbaca",
      impact: "Traffic telemetry belum bisa dianalisa dari UI.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Traffic Monitor.",
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

function buildTrafficAttention(insights: TrafficMonitorInsight[]) {
  const insight = insights.find((item) => isActionableSeverity(item.severity));
  if (!insight) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "Traffic stable and safe",
      summary: "Tidak ada finding warning/critical pada packet traffic, parser quality, atau ingestion baseline.",
      action: "Lanjutkan monitoring dan validasi threshold setelah traffic device nyata bertambah."
    };
  }
  return {
    status: insight.severity,
    label: "Attention Required",
    title: insight.title,
    summary: insight.impact || "Ada finding traffic yang perlu ditinjau.",
    action: insight.action,
    footer: `${insight.category || "Traffic"} · ${insight.status || "OPEN"}`
  };
}


function findMetric(metrics: TrafficMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: TrafficMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricSeverity(metrics: TrafficMetric[], key: string, fallback = "NORMAL") {
  return findMetric(metrics, key)?.severity ?? fallback;
}

function buildTopCards(metrics: TrafficMetric[]) {
  return [
    {
      key: "packets_second",
      label: "Packet / Second",
      value: metricValue(metrics, "packets_second"),
      unit: "pkt/sec",
      severity: metricSeverity(metrics, "packets_second"),
      icon: <Activity className="h-6 w-6" />,
      note: "Estimasi throughput packet dalam 60 detik terakhir."
    },
    {
      key: "packets_minute",
      label: "Packet / Minute",
      value: metricValue(metrics, "packets_minute"),
      unit: "pkt/min",
      severity: metricSeverity(metrics, "packets_minute"),
      icon: <RadioTower className="h-6 w-6" />,
      note: "Raw packet yang masuk gateway/backend 1 menit terakhir."
    },
    {
      key: "invalid_packets_hour",
      label: "Invalid Packet",
      value: metricValue(metrics, "invalid_packets_hour"),
      unit: "pkt/hour",
      severity: metricSeverity(metrics, "invalid_packets_hour"),
      icon: <ShieldAlert className="h-6 w-6" />,
      note: "Packet parse_status bukan VALID dalam 1 jam terakhir."
    },
    {
      key: "unknown_io_count",
      label: "Unknown IO",
      value: metricValue(metrics, "unknown_io_count"),
      unit: "io",
      severity: metricSeverity(metrics, "unknown_io_count"),
      icon: <AlertTriangle className="h-6 w-6" />,
      note: "IO numeric yang belum dimapping tetapi tetap disimpan."
    }
  ];
}

function buildTrafficDomains(metrics: TrafficMetric[]) {
  return [
    {
      label: "Throughput",
      value: `${metricValue(metrics, "packets_second")} pkt/sec`,
      severity: metricSeverity(metrics, "packets_second"),
      icon: <Gauge className="h-5 w-5" />,
      details: [`${metricValue(metrics, "packets_minute")} pkt/min`, `${metricValue(metrics, "raw_packets_hour")} raw/hour`, "Baseline traffic: learning"]
    },
    {
      label: "Parser Quality",
      value: `${metricValue(metrics, "parser_valid_rate", "100")}%`,
      severity: metricSeverity(metrics, "parser_valid_rate"),
      icon: <CheckCircle2 className="h-5 w-5" />,
      details: [`${metricValue(metrics, "invalid_packets_hour")} invalid/hour`, "Codec8/8E/ALELS JSON readiness", "Parser threshold tahap awal"]
    },
    {
      label: "Payload Size",
      value: metricValue(metrics, "avg_packet_size"),
      severity: metricSeverity(metrics, "avg_packet_size"),
      icon: <Waves className="h-5 w-5" />,
      details: [`Average ${metricValue(metrics, "avg_packet_size")} bytes`, "Raw packet storage impact", "Retention baseline"]
    },
    {
      label: "Queue Risk",
      value: "Learning",
      severity: "NORMAL",
      icon: <TimerReset className="h-5 w-5" />,
      details: ["Kafka/ingestion lag: next phase", "Direct DB insert risk monitored", "Siap untuk queue backlog metric"]
    }
  ];
}

export function TrafficMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["traffic-monitor-overview"],
    queryFn: getTrafficMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const trafficDomains = buildTrafficDomains(overview.metrics);

  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: TrafficMonitorInsight) => row.severity,
        render: (row: TrafficMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "title", label: "Problem", value: (row: TrafficMonitorInsight) => row.title, render: (row: TrafficMonitorInsight) => <span className="font-extrabold text-card-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: TrafficMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: TrafficMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: TrafficMonitorInsight) => row.status, render: (row: TrafficMonitorInsight) => <StatusIndicator status={row.status} /> }
    ],
    []
  );

  return (
    <section className="server-operations-ui space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS TRAFFIC NOC"
        icon={<Activity className="h-4 w-4" />}
        title="Traffic Monitor"
        description={
          <>
            Halaman operasi traffic untuk memantau packet rate, telemetry rate, parser quality, unknown IO, payload size,
            dan risiko bottleneck ingestion sebelum sistem masuk skala 1 juta device.
          </>
        }
        statusLabel="Traffic Status"
        status={healthStatusFromScore(overview.trafficScore, overview.status)}
        scoreLabel="Traffic Score"
        score={overview.trafficScore}
        updatedAt={overview.generatedAt}
        trendLabel="Baseline learning"
        attention={buildTrafficAttention(overview.insights)}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-4 xl:grid-cols-[1.15fr_1fr_1fr]">
        <MonitorAssessmentCard
          title="Traffic Assessment"
          icon={overview.status === "NORMAL" ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <AlertTriangle className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.summary}
          statusDetail="Assessment fokus pada packet throughput, parser quality, unknown IO, payload size, dan kesiapan ingestion pipeline."
          impact="Belum ada anomali traffic melewati threshold tahap awal. Baseline akan semakin akurat saat device aktif dan telemetry nyata bertambah."
          action={overview.recommendation}
          severity={overview.status}
        />

        <MonitorDistributionBars
          title="Traffic by Channel"
          subtitle="Distribusi raw/telemetry berdasarkan channel GSM, WiFi, atau unknown."
          icon={<Route className="h-5 w-5" />}
          items={overview.channelDistribution}
          emptyMessage="Belum ada traffic channel aktif."
        />

        <MonitorDistributionBars
          title="Traffic by Protocol"
          subtitle="Codec8, Codec8E, Codec12, ALELS JSON, dan protocol lain."
          icon={<Network className="h-5 w-5" />}
          items={overview.protocolDistribution}
          emptyMessage="Belum ada traffic protocol aktif."
        />
      </div>

      <MonitorDomainGrid cards={trafficDomains} />

      <MonitorSnapshot
        title="Traffic Technical Snapshot"
        description="Snapshot kecil untuk verifikasi angka teknis tanpa mengulang KPI utama di atas."
        fallbackIcon={<Activity className="h-4 w-4" />}
        items={overview.metrics.map((metric) => ({
          key: metric.key,
          label: metric.label,
          value: metric.value,
          unit: metric.unit,
          icon: metricIcons[metric.key]
        }))}
      />

      <MonitorFindingsTable
        title="Traffic Findings & Actions"
        description="Masalah traffic dan parser yang perlu diprioritaskan sebelum device bertambah besar."
        status={overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search traffic finding..."
        emptyMessage="No traffic findings."
        isLoading={isLoading}
        errorMessage={isError ? "Backend Traffic Monitor belum terbaca. Fallback UI ditampilkan." : null}
      />
    </section>
  );
}
