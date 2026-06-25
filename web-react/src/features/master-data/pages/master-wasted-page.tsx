import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Recycle, RotateCcw, Trash2 } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import { getMasterWasted, permanentDeleteMasterWasted, restoreMasterWasted, type MasterWastedRow, type MasterWastedType } from "@/features/master-data/api/master-wasted-api";

const tabs: { key: MasterWastedType; label: string }[] = [
  { key: "LICENSE", label: "License Master" },
  { key: "VEHICLE", label: "Vehicle Master" },
  { key: "DEVICE", label: "Device Master" },
  { key: "PRICE", label: "Harga Master" }
];

export function MasterWastedPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canPermanentDelete = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [activeTab, setActiveTab] = useState<MasterWastedType>("LICENSE");
  const wastedQuery = useQuery({ queryKey: ["master-wasted", activeTab], queryFn: () => getMasterWasted(activeTab) });
  const restoreMutation = useMutation({ mutationFn: (row: MasterWastedRow) => restoreMasterWasted(activeTab, row.id), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["master-wasted"] }) });
  const permanentDeleteMutation = useMutation({ mutationFn: (row: MasterWastedRow) => permanentDeleteMasterWasted(activeTab, row.id), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["master-wasted"] }) });

  const columns = useMemo<DataTableColumn<MasterWastedRow>[]>(() => [
    { key: "name", label: "Name", value: (row) => row.name || "-", render: (row) => <span className="font-bold text-white">{row.name || "-"}</span> },
    { key: "code", label: "Code", value: (row) => row.code || "-" },
    { key: "extra", label: "Info", value: (row) => row.extra || "-" },
    { key: "status", label: "Status", value: (row) => row.status || "DELETED", render: (row) => <StatusBadge status={row.status || "DELETED"} /> },
    { key: "deletedAt", label: "Deleted at", value: (row) => row.deletedAt || "", render: (row) => formatDateTime(row.deletedAt) },
    { key: "remainingDays", label: "Remaining", value: (row) => String(row.remainingDays ?? 0), render: (row) => <span>{row.remainingDays ?? 0} days</span> },
    { key: "deletePermanentAt", label: "Auto purge date", value: (row) => row.deletePermanentAt || "", render: (row) => formatDateTime(row.deletePermanentAt) },
    { key: "deletedByEmail", label: "Deleted by", value: (row) => row.deletedByEmail || "-" },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<MasterWastedRow>[]>(() => {
    const actions: DataTableBulkAction<MasterWastedRow>[] = [{ key: "restore", label: "Restore", icon: <RotateCcw className="h-4 w-4" />, confirmMessage: (rows) => `Restore ${rows.length} selected item(s)?`, onClick: async (rows) => { for (const row of rows) await restoreMasterWasted(activeTab, row.id); queryClient.invalidateQueries({ queryKey: ["master-wasted"] }); } }];
    if (canPermanentDelete) actions.push({ key: "permanent-delete", label: "Permanent Delete", icon: <Trash2 className="h-4 w-4" />, confirmMessage: (rows) => `Permanent delete ${rows.length} selected item(s)? This cannot be undone.`, onClick: async (rows) => { for (const row of rows) await permanentDeleteMasterWasted(activeTab, row.id); queryClient.invalidateQueries({ queryKey: ["master-wasted"] }); } });
    return actions;
  }, [activeTab, canPermanentDelete, queryClient]);

  return <section className="space-y-5 text-foreground"><PageHeader icon={<Recycle className="h-5 w-5" />} title="Wasted Master Data" /><OrganizationTableCard><div className="mb-4 flex flex-wrap gap-2">{tabs.map((tab) => <Button key={tab.key} type="button" variant={activeTab === tab.key ? "default" : "outline"} onClick={() => setActiveTab(tab.key)}>{tab.label}</Button>)}</div><DataTable data={wastedQuery.data || []} columns={columns} rowKey={(row) => `${row.itemType}-${row.id}`} emptyMessage={wastedQuery.isLoading ? "Loading wasted master data..." : "No wasted master data found."} bulkActions={bulkActions} actions={(row) => <div className="flex items-center gap-2"><Button type="button" size="sm" variant="outline" onClick={() => restoreMutation.mutate(row)}><RotateCcw className="h-4 w-4" /> Restore</Button>{canPermanentDelete ? <Button type="button" size="sm" variant="destructive" onClick={() => permanentDeleteMutation.mutate(row)}><Trash2 className="h-4 w-4" /> Permanent Delete</Button> : null}</div>} /></OrganizationTableCard></section>;
}
