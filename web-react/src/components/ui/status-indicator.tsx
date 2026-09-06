import * as React from "react";
import { cn } from "@/lib/utils";

export type StatusIndicatorTone = "blue" | "green" | "yellow" | "red" | "neutral";

const dotTone: Record<StatusIndicatorTone, string> = {
  blue: "bg-blue-500",
  green: "bg-emerald-500",
  yellow: "bg-amber-400",
  red: "bg-red-500",
  neutral: "bg-slate-400"
};

const blueStatuses = new Set([
  "ACTIVE", "MOVING", "TRIP", "NORMAL", "HEALTHY", "CONNECTED", "OPERATIONAL", "VALID", "RESOLVED"
]);
const greenStatuses = new Set(["ONLINE", "LIVE"]);
const yellowStatuses = new Set(["IDLE", "WARNING", "PENDING", "MAINTENANCE", "ATTENTION"]);
const redStatuses = new Set([
  "DEACTIVE", "DEACTIVATED", "INACTIVE", "STOP", "STOPPED", "OFFLINE", "SUSPENDED",
  "REJECTED", "DELETED", "EXPIRED", "ERROR", "CRITICAL", "DANGER", "EMERGENCY"
]);

export function statusIndicatorTone(status?: string | null): StatusIndicatorTone {
  const normalized = (status || "").trim().toUpperCase();
  if (blueStatuses.has(normalized)) return "blue";
  if (greenStatuses.has(normalized)) return "green";
  if (yellowStatuses.has(normalized)) return "yellow";
  if (redStatuses.has(normalized)) return "red";
  return "neutral";
}

type StatusIndicatorProps = React.HTMLAttributes<HTMLSpanElement> & {
  status?: string | null;
  label?: React.ReactNode;
  tone?: StatusIndicatorTone;
};

type StatusDotProps = React.HTMLAttributes<HTMLSpanElement> & {
  status?: string | null;
  tone?: StatusIndicatorTone;
};

export function StatusDot({ status, tone, className, ...props }: StatusDotProps) {
  const resolvedTone = tone || statusIndicatorTone(status);
  return <span data-ui-status-dot data-status-tone={resolvedTone} aria-hidden="true" className={cn("h-2.5 w-2.5 shrink-0 rounded-full", dotTone[resolvedTone], className)} {...props} />;
}

export function StatusIndicator({ status, label, tone, className, ...props }: StatusIndicatorProps) {
  const normalized = (status || "-").trim().toUpperCase() || "-";
  const resolvedTone = tone || statusIndicatorTone(normalized);
  return (
    <span
      data-ui-status-indicator
      data-status-tone={resolvedTone}
      className={cn(
        "inline-flex items-center gap-2 rounded-md border border-border bg-background px-3 py-1 text-xs font-extrabold text-foreground",
        className
      )}
      {...props}
    >
      <StatusDot tone={resolvedTone} />
      {label ?? normalized}
    </span>
  );
}
