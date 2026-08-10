import * as React from "react";
import { cn } from "@/lib/utils";

type BadgeProps = React.HTMLAttributes<HTMLSpanElement> & {
  tone?: "default" | "success" | "warning" | "danger" | "muted";
};

const tones = {
  default: "border border-primary/25 bg-primary/10 text-primary",
  success: "border border-emerald-500/25 bg-emerald-100 text-emerald-700",
  warning: "border border-amber-500/25 bg-amber-100 text-amber-700",
  danger: "border border-red-500/25 bg-red-100 text-red-700",
  muted: "border border-border bg-muted text-muted-foreground"
};

export function Badge({ className, tone = "default", ...props }: BadgeProps) {
  return (
    <span
      data-ui-badge
      data-tone={tone}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium before:h-1.5 before:w-1.5 before:shrink-0 before:rounded-full before:bg-current",
        tones[tone],
        className
      )}
      {...props}
    />
  );
}
