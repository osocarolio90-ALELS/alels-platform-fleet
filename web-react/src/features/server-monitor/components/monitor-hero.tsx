import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { useLanguageStore } from "@/stores/language-store";
import { formatMonitorTimestamp, localizeMonitorTerm, monitorLabel, severityLabel, toneFor, visualFor } from "./monitor-utils";

type MonitorHeroAttention = {
  status: string;
  label: string;
  title: ReactNode;
  summary: ReactNode;
  action?: ReactNode;
  footer?: ReactNode;
};

export function MonitorHero({
  eyebrow,
  icon,
  title,
  description,
  statusLabel,
  status,
  scoreLabel,
  score,
  updatedAt,
  trendLabel = "Stable",
  scoreNote,
  attention
}: {
  eyebrow: string;
  icon: ReactNode;
  title: string;
  description: ReactNode;
  statusLabel: string;
  status: string;
  scoreLabel: string;
  score: number | string;
  updatedAt?: string | null;
  trendLabel?: string;
  scoreNote?: ReactNode;
  attention?: MonitorHeroAttention;
}) {
  const language = useLanguageStore((state) => state.language);
  const visual = visualFor(status);
  const attentionVisual = visualFor(attention?.status ?? "NORMAL");

  return (
    <div className={cn("server-monitor-card rounded-lg border bg-card p-5 shadow-sm", visual.border)}>
      <div className="flex flex-col gap-5 2xl:flex-row 2xl:items-center 2xl:justify-between">
        <div className="max-w-5xl">
          <div className="server-monitor-eyebrow mb-2 inline-flex items-center gap-2 rounded-full px-3 py-1 text-sm font-extrabold ring-1 ring-border">
            {icon}
            {localizeMonitorTerm(language, eyebrow)}
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-card-foreground">{localizeMonitorTerm(language, title)}</h1>
          <div className="mt-2 max-w-4xl text-sm font-semibold leading-6 text-muted-foreground">{description}</div>
        </div>

        <div className={cn("grid min-w-[320px] auto-rows-fr gap-3", attention ? "2xl:min-w-[620px] 2xl:grid-cols-2" : "")}>
          <div className={cn("server-monitor-card flex min-h-[180px] flex-col rounded-lg border bg-card p-4 shadow-sm", visual.border)}>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{localizeMonitorTerm(language, statusLabel)}</span>
              <span className={cn("server-monitor-status-dot h-3 w-3 rounded-full", visual.dot)} />
            </div>
            <div className="flex items-end justify-between gap-4">
              <div>
                <Badge tone={toneFor(status)} className="px-3 py-1 text-sm font-extrabold">
                  {severityLabel(language, status)}
                </Badge>
                <p className={cn("mt-2 text-sm font-extrabold", visual.text)}>{severityLabel(language, status)}</p>
                <p className="mt-1 text-xs font-bold text-muted-foreground">{monitorLabel(language, "trend")}: {trendLabel}</p>
              </div>
              <div className="text-right">
                <p className="text-xs font-extrabold uppercase text-muted-foreground">{localizeMonitorTerm(language, scoreLabel)}</p>
                <p className="text-5xl font-black tracking-tight text-slate-950 dark:text-white drop-shadow-sm">{score}%</p>
              </div>
            </div>
            {scoreNote ? (
              <div className="mt-auto border-t border-slate-300/60 pt-3 text-xs font-bold leading-5 text-slate-700 dark:border-slate-700/70 dark:text-slate-200">
                {scoreNote}
              </div>
            ) : null}
            <p className={cn("text-xs font-semibold text-muted-foreground", scoreNote ? "mt-1" : "mt-3")}>{monitorLabel(language, "updated")}: {formatMonitorTimestamp(updatedAt)}</p>
          </div>

          {attention ? (
            <div className={cn("server-monitor-card flex min-h-[180px] flex-col rounded-lg border bg-card p-4 shadow-sm", attentionVisual.border)}>
              <div className="mb-3 flex items-center justify-between">
                <span className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{monitorLabel(language, "attentionSummary")}</span>
                <span className={cn("server-monitor-status-dot h-3 w-3 rounded-full", attentionVisual.dot)} />
              </div>
              <Badge tone={toneFor(attention.status)} className="px-3 py-1 text-sm font-extrabold">
                {localizeMonitorTerm(language, attention.label)}
              </Badge>
              <p className={cn("mt-3 text-sm font-extrabold", attentionVisual.text)}>{attention.title}</p>
              <div className="mt-2 flex-1 text-xs font-bold leading-5 text-slate-800 dark:text-slate-100">{attention.summary}</div>
              {attention.action ? (
                <div className="mt-3 border-t border-slate-300/60 pt-3 text-xs font-semibold leading-5 text-slate-700 dark:border-slate-700/70 dark:text-slate-200">
                  {attention.action}
                </div>
              ) : null}
              {attention.footer ? (
                <div className="mt-2 text-xs font-extrabold text-slate-700 dark:text-slate-200">{attention.footer}</div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
