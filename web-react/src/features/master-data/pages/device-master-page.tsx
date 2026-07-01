import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Database, Pencil, Plus, PowerOff, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import {
  createDeviceModel,
  deleteDeviceModel,
  getDeviceBrands,
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
  const [notice, setNotice] = useState<string | null>(null);

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ["device-master", "models"],
    queryFn: () => getDeviceModels()
  });
  const { data: brands = [] } = useQuery({ queryKey: ["device-master", "brands"], queryFn: getDeviceBrands });

  const saveMutation = useMutation({
    mutationFn: () => editing ? updateDeviceModel(editing.id, toInput(form)) : createDeviceModel(toInput(form)),
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["device-master"] });
    },
    onError: (error: any) => setNotice(error?.response?.data?.message || "Device Master gagal disimpan.")
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
    { key: "code", label: "Code", value: (row) => row.modelCode || "-", render: (row) => <span className="font-bold text-foreground">{row.modelCode || "-"}</span> },
    { key: "brandName", label: "Device Brand", value: (row) => row.brandName || "-", render: (row) => <span className="font-bold text-foreground">{row.brandName || "-"}</span> },
    { key: "modelName", label: "Device Model", value: (row) => row.modelName || "-", render: (row) => <span className="font-bold text-foreground">{row.modelName || "-"}</span> },
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
    setNotice(null);
    setFormOpen(true);
  }

  function startEdit(row: DeviceModelRow) {
    setEditing(row);
    setNotice(null);
    setForm({
      modelCode: row.modelCode || "",
      brandId: row.brandId ? String(row.brandId) : "",
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
    if (!canEdit) return;
    if (!form.brandId) { setNotice("Device Brand wajib dipilih."); return; }
    saveMutation.mutate();
  }

  if (formOpen) {
    return (
      <section className="space-y-5 text-foreground">
        <PageHeader
          title={editing ? "Edit Device Master" : "Create New Device Master"}
          icon={<Database className="h-5 w-5" />}
        />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-3 md:grid-cols-3" onSubmit={submit}>
            <Field label="Code">
              <Input value={form.modelCode} onChange={(event) => setForm((current) => ({ ...current, modelCode: event.target.value.toUpperCase() }))} placeholder="AUTO / MANUAL" />
            </Field>
            <SearchableSelect label="Device Brand" required value={form.brandId} onChange={(value) => setForm((current) => ({ ...current, brandId: value }))} options={brands.map((brand) => ({ value: String(brand.id), label: brand.brandName, extra: brand.brandCode }))} />
            <Field label="Device Model">
              <Input value={form.modelName} onChange={(event) => setForm((current) => ({ ...current, modelName: event.target.value }))} required />
            </Field>
            <Field label="Status">
              <select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.value }))}>
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
    <section className="space-y-5 text-foreground">
      <PageHeader
        title="Device Master"
        icon={<Database className="h-5 w-5" />}
        actions={canEdit ? <Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button> : null}
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

type FormState = { modelCode: string; brandId: string; modelName: string; active: string };

function emptyForm(): FormState {
  return { modelCode: "", brandId: "", modelName: "", active: "ACTIVE" };
}

function toInput(form: FormState): DeviceMasterInput {
  return {
    brandId: form.brandId ? Number(form.brandId) : null,
    modelCode: form.modelCode || null,
    modelName: form.modelName || null,
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
  return <label className="grid gap-2 text-xs font-bold text-foreground"><span>{label}</span>{children}</label>;
}

function Notice({ message }: { message: string }) {
  return <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-200">{message}</div>;
}
