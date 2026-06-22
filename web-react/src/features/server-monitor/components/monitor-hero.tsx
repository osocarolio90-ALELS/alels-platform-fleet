import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { formatMonitorTimestamp, toneFor, visualFor } from "./monitor-utils";

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
  const visual = visualFor(status);
  const attentionVisual = visualFor(attention?.status ?? "NORMAL");

  return (
    <div className={cn("rounded-2xl border bg-card p-5 shadow-sm", visual.border)}>
      <div className="flex flex-col gap-5 2xl:flex-row 2xl:items-center 2xl:justify-between">
        <div className="max-w-5xl">
          <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-sky-50 px-3 py-1 text-sm font-extrabold text-sky-800 ring-1 ring-sky-100">
            {icon}
            {eyebrow}
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-card-foreground">{title}</h1>
          <div className="mt-2 max-w-4xl text-sm font-semibold leading-6 text-muted-foreground">{description}</div>
        </div>

        <div className={cn("grid min-w-[320px] auto-rows-fr gap-3", attention ? "2xl:min-w-[620px] 2xl:grid-cols-2" : "")}>
          <div className={cn("flex min-h-[180px] flex-col rounded-2xl border p-4 ring-4", visual.bg, visual.border, visual.ring)}>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-xs font-extrabold uppercase tracking-wide text-slate-700 dark:text-slate-200">{statusLabel}</span>
              <span className={cn("h-3 w-3 rounded-full", visual.dot)} />
            </div>
            <div className="flex items-end justify-between gap-4">
              <div>
                <Badge tone={toneFor(status)} className="px-3 py-1 text-sm font-extrabold">
                  {status}
                </Badge>
                <p className={cn("mt-2 text-sm font-extrabold", visual.text)}>{visual.label}</p>
                <p className="mt-1 text-xs font-bold text-slate-700 dark:text-slate-200">Trend: {trendLabel}</p>
              </div>
              <div className="text-right">
                <p className="text-xs font-extrabold uppercase text-slate-700 dark:text-slate-200">{scoreLabel}</p>
                <p className="text-5xl font-black tracking-tight text-slate-950 dark:text-white drop-shadow-sm">{score}%</p>
              </div>
            </div>
            {scoreNote ? (
              <div className="mt-auto border-t border-slate-300/60 pt-3 text-xs font-bold leading-5 text-slate-700 dark:border-slate-700/70 dark:text-slate-200">
                {scoreNote}
              </div>
            ) : null}
            <p className={cn("text-xs font-semibold text-slate-700 dark:text-slate-200", scoreNote ? "mt-1" : "mt-3")}>Updated: {formatMonitorTimestamp(updatedAt)}</p>
          </div>

          {attention ? (
            <div className={cn("flex min-h-[180px] flex-col rounded-2xl border p-4 ring-4", attentionVisual.bg, attentionVisual.border, attentionVisual.ring)}>
              <div className="mb-3 flex items-center justify-between">
                <span className="text-xs font-extrabold uppercase tracking-wide text-slate-700 dark:text-slate-200">Attention Summary</span>
                <span className={cn("h-3 w-3 rounded-full", attentionVisual.dot)} />
              </div>
              <Badge tone={toneFor(attention.status)} className="px-3 py-1 text-sm font-extrabold">
                {attention.label}
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
