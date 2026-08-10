import { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { StatusIndicator, statusIndicatorTone } from "@/components/ui/status-indicator";
import { Card, CardContent } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { translateUiLabel } from "@/lib/i18n";
import { useLanguageStore } from "@/stores/language-store";

export function OrganizationPageHeader({ title, icon, description, actions }: { title: string; icon?: ReactNode; description?: string; actions?: ReactNode }) {
  return <PageHeader title={title} icon={icon} description={description} actions={actions} />;
}

export function OrganizationTableCard({ children, title, description }: { children: ReactNode; title?: string; description?: string }) {
  const language = useLanguageStore((state) => state.language);
  return (
    <Card className="border-white/10 bg-white/5 text-foreground">
      <CardContent>
        {title || description ? (
          <div className="mb-4">
            {title ? <h2 className="text-lg font-extrabold text-white">{translateUiLabel(language, title)}</h2> : null}
            {description ? <p className="mt-1 text-sm text-slate-300">{description}</p> : null}
          </div>
        ) : null}
        {children}
      </CardContent>
    </Card>
  );
}

export function StatusBadge({ status }: { status?: string | null }) {
  return statusIndicatorTone(status) === "neutral"
    ? <Badge tone="muted" className="px-3 py-1 font-extrabold">{status || "-"}</Badge>
    : <StatusIndicator status={status} />;
}

export function formatDate(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium" }).format(date);
}

export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("id-ID", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function formatCompanyName(value?: string | null) {
  return (value || "-").trim().toUpperCase();
}

export const ALELS_ROOT_COMPANY = "ALELS TECH INDONESIA";
