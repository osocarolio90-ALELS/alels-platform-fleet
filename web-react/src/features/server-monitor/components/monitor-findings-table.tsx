import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DataTable, type DataTableColumn } from "@/components/data-table";
import { toneFor } from "./monitor-utils";

export function MonitorFindingsTable<T>({
  title,
  description,
  status,
  findings,
  columns,
  rowKey,
  searchPlaceholder,
  emptyMessage,
  isLoading,
  errorMessage
}: {
  title: string;
  description: ReactNode;
  status: string;
  findings: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string | number;
  searchPlaceholder?: string;
  emptyMessage?: string;
  isLoading?: boolean;
  errorMessage?: ReactNode;
}) {
  return (
    <Card className="border shadow-sm">
      <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <CardTitle className="text-lg text-card-foreground">{title}</CardTitle>
          <p className="mt-1 text-sm font-semibold text-muted-foreground">{description}</p>
        </div>
        <Badge tone={toneFor(status)} className="w-fit px-3 py-1 font-extrabold">
          {findings.length} finding
        </Badge>
      </CardHeader>
      <CardContent>
        {isLoading ? <div className="rounded-lg border border-border bg-muted/45 dark:bg-muted/20 p-4 text-sm font-semibold text-muted-foreground">Loading monitor findings...</div> : null}
        {errorMessage ? <div className="mb-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm font-bold text-amber-900">{errorMessage}</div> : null}
        <DataTable data={findings} columns={columns} rowKey={rowKey} searchPlaceholder={searchPlaceholder} emptyMessage={emptyMessage} />
      </CardContent>
    </Card>
  );
}
