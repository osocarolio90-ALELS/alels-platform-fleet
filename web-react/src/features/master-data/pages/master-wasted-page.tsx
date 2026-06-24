import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, Trash2, Database } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { getMasterWasted, permanentDeleteMasterWasted, restoreMasterWasted, type MasterWastedCategory, type MasterWastedRow } from "@/features/master-data/api/master-wasted-api";
import { useAuthStore } from "@/stores/auth-store";

const tabs: { key: MasterWastedCategory; label: string }[] = [
  { key: "vehicle", label: "Vehicle Master" },
  { key: "device", label: "Device Master" },
  { key: "harga", label: "Harga Master" }
];

export function MasterWastedPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const role = String(user?.role || "").toUpperCase().replace(/[^A-Z]/g, "");
  const canPermanentDelete = role === "SUPERADMIN";
  const [activeTab, setActiveTab] = useState<MasterWastedCategory>("vehicle");

  const { data = [], isLoading } = useQuery({
    queryKey: ["master-data", "wasted", activeTab],
    queryFn: () => getMasterWasted(activeTab)
  });

  const restoreMutation = useMutation({
    mutationFn: restoreMasterWasted,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["master-data"] })
  });

  const permanentMutation = useMutation({
    mutationFn: permanentDeleteMasterWasted,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["master-data"] })
  });

  const columns = useMemo<DataTableColumn<MasterWastedRow>[]>(() => [
    { key: "masterType", label: "Master Type", value: (row) => row.masterType, render: (row) => <span className="font-bold text-white">{row.masterType}</span> },
    { key: "code", label: "Code", value: (row) => row.code, render: (row) => <span className="font-semibold text-slate-100">{row.code}</span> },
    { key: "name", label: "Name", value: (row) => row.name, render: (row) => <span className="font-bold text-white">{row.name}</span> },
    { key: "parentName", label: "Parent", value: (row) => row.parentName || "-" },
    { key: "status", label: "Status", value: (row) => row.status || "-", render: (row) => <StatusBadge status={row.status || "INACTIVE"} /> },
    { key: "deletedAt", label: "Deleted at", value: (row) => formatDateTime(row.deletedAt), render: (row) => <span>{formatDateTime(row.deletedAt)}</span> },
    { key: "remainingDays", label: "Remaining", value: (row) => `${row.remainingDays ?? 0} days`, render: (row) => <span className="font-bold text-amber-200">{row.remainingDays ?? 0} days</span> },
    { key: "deletePermanentAt", label: "Auto purge date", value: (row) => formatDateTime(row.deletePermanentAt), render: (row) => <span>{formatDateTime(row.deletePermanentAt)}</span> },
    { key: "deletedBy", label: "Deleted By", value: (row) => row.deletedBy || "-" },
    { key: "reason", label: "Reason", value: (row) => row.reason || "-" }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<MasterWastedRow>[]>(() => {
    const actions: DataTableBulkAction<MasterWastedRow>[] = [
      {
        key: "restore",
        label: "Restore",
        icon: <RotateCcw className="h-4 w-4" />,
        confirmMessage: (rows) => `Restore ${rows.length} selected master data item(s)?`,
        onClick: async (rows) => { for (const row of rows) await restoreMutation.mutateAsync(row); }
      }
    ];
    if (canPermanentDelete) {
      actions.push({
        key: "permanent-delete",
        label: "Permanent Delete",
        icon: <Trash2 className="h-4 w-4" />,
        variant: "destructive",
        confirmMessage: (rows) => `Permanent delete ${rows.length} selected master data item(s)? This cannot be undone.`,
        onClick: async (rows) => { for (const row of rows) await permanentMutation.mutateAsync(row); }
      });
    }
    return actions;
  }, [canPermanentDelete, restoreMutation, permanentMutation]);

  return (
    <section className="space-y-4 text-foreground">
      <PageHeader title="Wasted Master Data" icon={<Database className="h-4 w-4" />} />
      <OrganizationTableCard>
        <div className="mb-4 flex flex-wrap gap-2">
          {tabs.map((tab) => (
            <Button key={tab.key} type="button" size="sm" variant={activeTab === tab.key ? "default" : "outline"} onClick={() => setActiveTab(tab.key)}>
              {tab.label} ({activeTab === tab.key ? data.length : ""})
            </Button>
          ))}
        </div>
        <DataTable
          data={data}
          columns={columns}
          rowKey={(row) => `${row.category}-${row.masterType}-${row.id}`}
          emptyMessage={isLoading ? "Loading wasted master data..." : "No wasted master data found."}
          bulkActions={bulkActions}
          actions={(row) => (
            <div className="flex items-center gap-2">
              <Button type="button" size="icon" variant="outline" onClick={() => restoreMutation.mutate(row)} title="Restore"><RotateCcw className="h-4 w-4" /></Button>
              {canPermanentDelete ? <Button type="button" size="icon" variant="destructive" onClick={() => { if (window.confirm(`Permanent delete ${row.name}?`)) permanentMutation.mutate(row); }} title="Permanent Delete"><Trash2 className="h-4 w-4" /></Button> : null}
            </div>
          )}
        />
      </OrganizationTableCard>
    </section>
  );
}
