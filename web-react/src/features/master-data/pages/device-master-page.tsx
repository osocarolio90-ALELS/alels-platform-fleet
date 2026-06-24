import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Database, Pencil, Plus, PowerOff, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import {
  createDeviceModel,
  deleteDeviceModel,
  getDeviceModels,
  updateDeviceModel,
  type DeviceMasterInput,
  type DeviceModelRow
} from "@/features/master-data/api/device-master-api";

export function DeviceMasterPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DeviceModelRow | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ["device-master", "models"],
    queryFn: () => getDeviceModels()
  });

  const saveMutation = useMutation({
    mutationFn: () => editing ? updateDeviceModel(editing.id, toInput(form)) : createDeviceModel(toInput(form)),
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["device-master"] });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (row: DeviceModelRow) => deleteDeviceModel(row.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["device-master"] })
  });

  const statusMutation = useMutation({
    mutationFn: ({ row, active }: { row: DeviceModelRow; active: boolean }) => updateDeviceModel(row.id, rowToInput(row, active)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["device-master"] })
  });

  const columns = useMemo<DataTableColumn<DeviceModelRow>[]>(() => [
    { key: "code", label: "Code", value: (row) => row.modelCode || "-", render: (row) => <span className="font-bold text-white">{row.modelCode || "-"}</span> },
    { key: "brandName", label: "Device Brand", value: (row) => row.brandName || "-", render: (row) => <span className="font-bold text-white">{row.brandName || "-"}</span> },
    { key: "modelName", label: "Device Model", value: (row) => row.modelName || "-", render: (row) => <span className="font-bold text-white">{row.modelName || "-"}</span> },
    { key: "createdAt", label: "Created at", value: (row) => row.createdAt || "", render: (row) => formatDateTime(row.createdAt) },
    { key: "createdBy", label: "Created By", value: (row) => row.createdBy || "-" },
    { key: "status", label: "Status", value: (row) => row.active ? "ACTIVE" : "INACTIVE", render: (row) => <StatusBadge status={row.active ? "ACTIVE" : "INACTIVE"} /> }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<DeviceModelRow>[]>(() => canEdit ? [
    {
      key: "set-active",
      label: "Set Active",
      icon: <CheckCircle2 className="h-4 w-4" />,
      confirmMessage: (selected) => `Activate ${selected.length} selected device master item(s)?`,
      onClick: async (selected) => {
        for (const row of selected) await updateDeviceModel(row.id, rowToInput(row, true));
        queryClient.invalidateQueries({ queryKey: ["device-master"] });
      }
    },
    {
      key: "set-inactive",
      label: "Set Inactive",
      icon: <PowerOff className="h-4 w-4" />,
      confirmMessage: (selected) => `Inactive ${selected.length} selected device master item(s)?`,
      onClick: async (selected) => {
        for (const row of selected) await updateDeviceModel(row.id, rowToInput(row, false));
        queryClient.invalidateQueries({ queryKey: ["device-master"] });
      }
    },
    {
      key: "delete",
      label: "Delete",
      icon: <Trash2 className="h-4 w-4" />,
      confirmMessage: (selected) => `Delete ${selected.length} selected device master item(s)?`,
      onClick: async (selected) => {
        for (const row of selected) await deleteDeviceModel(row.id);
        queryClient.invalidateQueries({ queryKey: ["device-master"] });
      }
    }
  ] : [], [canEdit, queryClient]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm());
    setFormOpen(true);
  }

  function startEdit(row: DeviceModelRow) {
    setEditing(row);
    setForm({
      modelCode: row.modelCode || "",
      brandName: row.brandName || "",
      modelName: row.modelName || "",
      active: row.active ? "ACTIVE" : "INACTIVE"
    });
    setFormOpen(true);
  }

  function closeForm() {
    setEditing(null);
    setForm(emptyForm());
    setFormOpen(false);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (canEdit) saveMutation.mutate();
  }

  if (formOpen) {
    return (
      <section className="space-y-4 text-foreground">
        <PageHeader
          title={editing ? "Edit Device Master" : "Create New Device Master"}
          icon={<Database className="h-4 w-4" />}
          actions={<Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button>}
        />
        <OrganizationTableCard>
          <form className="grid gap-3 md:grid-cols-3" onSubmit={submit}>
            <Field label="Code">
              <Input value={form.modelCode} onChange={(event) => setForm((current) => ({ ...current, modelCode: event.target.value.toUpperCase() }))} placeholder="AUTO / MANUAL" />
            </Field>
            <Field label="Device Brand">
              <Input value={form.brandName} onChange={(event) => setForm((current) => ({ ...current, brandName: event.target.value.toUpperCase() }))} required />
            </Field>
            <Field label="Device Model">
              <Input value={form.modelName} onChange={(event) => setForm((current) => ({ ...current, modelName: event.target.value.toUpperCase() }))} required />
            </Field>
            <Field label="Status">
              <select className="h-10 rounded-md border border-white/10 bg-[#070b12] px-3 text-sm text-white" value={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.value }))}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </Field>
            <div className="md:col-span-3 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button>
            </div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  return (
    <section className="space-y-4 text-foreground">
      <PageHeader
        title="Device Master"
        icon={<Database className="h-4 w-4" />}
        description="Master data device untuk Device Register dan gateway protocol mapping."
        actions={canEdit ? <Button type="button" size="sm" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button> : null}
      />
      <OrganizationTableCard>
        <DataTable
          data={rows}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={isLoading ? "Loading device master..." : "No device master found."}
          bulkActions={bulkActions}
          actions={(row) => canEdit ? (
            <div className="flex items-center gap-2">
              <Button type="button" size="icon" variant="outline" onClick={() => startEdit(row)} title="Edit"><Pencil className="h-4 w-4" /></Button>
              <Button type="button" size="icon" variant="destructive" onClick={() => deleteMutation.mutate(row)} title="Delete"><Trash2 className="h-4 w-4" /></Button>
              {row.active ? (
                <Button type="button" size="icon" variant="outline" onClick={() => statusMutation.mutate({ row, active: false })} title="Inactive"><PowerOff className="h-4 w-4" /></Button>
              ) : (
                <Button type="button" size="icon" variant="outline" onClick={() => statusMutation.mutate({ row, active: true })} title="Active"><CheckCircle2 className="h-4 w-4" /></Button>
              )}
            </div>
          ) : <span className="text-xs text-slate-400">Read only</span>}
        />
      </OrganizationTableCard>
    </section>
  );
}

type FormState = { modelCode: string; brandName: string; modelName: string; active: string };

function emptyForm(): FormState {
  return { modelCode: "", brandName: "", modelName: "", active: "ACTIVE" };
}

function toInput(form: FormState): DeviceMasterInput {
  return {
    brandId: null,
    brandName: form.brandName.trim().toUpperCase() || null,
    modelCode: form.modelCode.trim().toUpperCase() || null,
    modelName: form.modelName.trim().toUpperCase() || null,
    active: form.active === "ACTIVE"
  };
}

function rowToInput(row: DeviceModelRow, active: boolean): DeviceMasterInput {
  return {
    brandId: null,
    brandName: row.brandName || null,
    modelCode: row.modelCode || null,
    modelName: row.modelName || null,
    protocolCode: row.protocolCode || null,
    parserCode: row.parserCode || null,
    dictionaryCode: row.dictionaryCode || null,
    description: row.description || null,
    active,
    sortOrder: row.sortOrder || null
  };
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}</span>{children}</label>;
}
