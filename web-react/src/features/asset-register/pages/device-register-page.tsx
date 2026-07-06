import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRightLeft, CheckCircle2, Cpu, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { getDeviceEndpoint } from "@/lib/api";
import { OrganizationTableCard, formatDateTime, formatCompanyName } from "@/features/organization/components/organization-ui";
import { moveAssets } from "@/features/asset-register/api/asset-move-api";
import { AssetMoveDialog } from "@/features/asset-register/components/asset-move-dialog";
import { normalizeRole } from "@/lib/role-access";
import { useAuthStore } from "@/stores/auth-store";
import {
  createDevice,
  deleteDevice,
  getDeviceCompanyOptions,
  getDeviceModelOptions,
  getDevices,
  updateDevice,
  type DeviceRegisterInput,
  type DeviceRegisterRow
} from "@/features/asset-register/api/device-register-api";

export function DeviceRegisterPage() {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DeviceRegisterRow | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [notice, setNotice] = useState<string | null>(null);
  const [moveRows, setMoveRows] = useState<DeviceRegisterRow[]>([]);
  const user = useAuthStore((state) => state.user);
  const canMove = ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER"].includes(normalizeRole(user?.role) || "");

  const { data: rows = [], isLoading } = useQuery({ queryKey: ["asset-register", "devices"], queryFn: getDevices });
  const { data: companies = [] } = useQuery({ queryKey: ["asset-register", "devices", "companies"], queryFn: getDeviceCompanyOptions });
  const { data: deviceOptions = [] } = useQuery({ queryKey: ["asset-register", "devices", "device-options"], queryFn: () => getDeviceModelOptions() });

  const saveMutation = useMutation({
    mutationFn: () => editing ? updateDevice(editing.id, toInput(form)) : createDevice(toInput(form)),
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["asset-register", "devices"] });
      queryClient.invalidateQueries({ queryKey: ["asset-wasted"] });
    },
    onError: (error: unknown) => setNotice(error instanceof Error ? error.message : "Device register gagal disimpan.")
  });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteDevice(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["asset-register", "devices"] });
      queryClient.invalidateQueries({ queryKey: ["asset-wasted"] });
    }
  });
  const moveMutation = useMutation({
    mutationFn: (targetCompanyId: number) => moveAssets("DEVICE", moveRows.map((row) => row.id), targetCompanyId),
    onSuccess: () => {
      setMoveRows([]);
      setNotice("Device berhasil dipindahkan.");
      queryClient.invalidateQueries({ queryKey: ["asset-register", "devices"] });
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Device gagal dipindahkan.")
  });

  const columns = useMemo<DataTableColumn<DeviceRegisterRow>[]>(() => [
    { key: "companyName", label: "Company", value: (row) => row.companyName, render: (row) => <span className="font-bold text-white">{formatCompanyName(row.companyName)}</span> },
    { key: "device", label: "Device", value: (row) => formatDevice(row), render: (row) => <span className="font-bold text-white">{formatDevice(row)}</span> },
    { key: "imei", label: "Device IMEI", value: (row) => row.imei, render: (row) => <span className="font-bold text-white">{row.imei}</span> },
    { key: "gsmNumber", label: "GSM Number", value: (row) => row.gsmNumber || "" },
    { key: "tcpHost", label: "TCP IP/URL", value: (row) => row.tcpHost || "" },
    { key: "tcpPort", label: "TCP port", value: (row) => row.tcpPort || "" },
    { key: "status", label: "Status", value: (row) => row.status, render: (row) => <StatusCell status={row.status} /> },
    { key: "createdAt", label: "Created at", value: (row) => row.createdAt || "", render: (row) => formatDateTime(row.createdAt) },
    { key: "createdBy", label: "Created by", value: (row) => row.createdBy || "-" }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<DeviceRegisterRow>[]>(() => [...(canMove ? [{
    key: "move",
    label: "Move",
    icon: <ArrowRightLeft className="h-4 w-4" />,
    onClick: async (selected: DeviceRegisterRow[]) => setMoveRows(selected)
  }] : []), {
    key: "delete",
    label: "Delete",
    icon: <Trash2 className="h-4 w-4" />,
    confirmMessage: (selected) => `Delete ${selected.length} selected device(s)?`,
    onClick: async (selected) => {
      for (const row of selected) await deleteDevice(row.id);
      queryClient.invalidateQueries({ queryKey: ["asset-register", "devices"] });
      queryClient.invalidateQueries({ queryKey: ["asset-wasted"] });
    }
  }], [canMove, queryClient]);

  function openCreate() {
    setNotice(null);
    setEditing(null);
    setForm(emptyForm(companies[0]?.id));
    setFormOpen(true);
  }

  function startEdit(row: DeviceRegisterRow) {
    setNotice(null);
    setEditing(row);
    setForm({
      companyId: String(row.companyId || ""),
      deviceModelId: String(row.deviceModelId || ""),
      imei: row.imei || "",
      gsmNumber: row.gsmNumber || "",
      tcpHost: row.tcpHost || "",
      tcpPort: String(row.tcpPort || ""),
      notes: ""
    });
    setFormOpen(true);
  }

  function closeForm() {
    setNotice(null);
    setEditing(null);
    setForm(emptyForm());
    setFormOpen(false);
  }

  async function checkTcpEndpoint() {
    setNotice(null);
    try {
      const endpoint = await getDeviceEndpoint();
      const host = pickEndpointHost(endpoint);
      const port = pickEndpointPort(endpoint);
      if (!host && !port) {
        setNotice("TCP endpoint belum tersedia dari server. Save tetap bisa dilakukan tanpa TCP IP/URL dan TCP Port.");
        return;
      }
      setForm((current) => ({ ...current, tcpHost: host || current.tcpHost, tcpPort: port || current.tcpPort }));
    } catch {
      const fallbackHost = typeof window !== "undefined" ? window.location.hostname : "";
      if (fallbackHost) {
        setForm((current) => ({ ...current, tcpHost: current.tcpHost || fallbackHost }));
        setNotice("TCP endpoint server belum lengkap. TCP IP/URL memakai host aplikasi; TCP Port bisa dikosongkan atau diisi manual.");
      } else {
        setNotice("TCP endpoint belum tersedia dari server. Save tetap bisa dilakukan tanpa TCP IP/URL dan TCP Port.");
      }
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    if (!form.companyId) return setNotice("Company wajib diisi.");
    if (!form.deviceModelId) return setNotice("Device wajib diisi.");
    if (!form.imei.trim()) return setNotice("Device IMEI wajib diisi.");
    saveMutation.mutate();
  }

  if (formOpen) {
    return <section className="space-y-4 text-foreground">
      <PageHeader title={editing ? "Edit Device Register" : "Create New Device Register"} icon={<Cpu className="h-5 w-5" />} />
      <OrganizationTableCard>
        {notice ? <div className="mb-3 rounded-md border border-amber-500/50 bg-amber-950/40 px-3 py-2 text-xs font-bold text-amber-100">{notice}</div> : null}
        <form className="grid gap-3 md:grid-cols-3" onSubmit={submit}>
          <SearchableSelect label="Company" value={form.companyId} onChange={(value) => setForm((current) => ({ ...current, companyId: value }))} options={companies.map((company) => ({ value: String(company.id), label: company.label, extra: company.extra || company.code }))} required disabled={Boolean(editing)} />
          <SearchableSelect label="Device" value={form.deviceModelId} onChange={(value) => setForm((current) => ({ ...current, deviceModelId: value }))} options={deviceOptions.map((device) => ({ value: String(device.id), label: device.extra ? `${device.extra} ${device.label}` : device.label, extra: device.code }))} required />
          <Field label="Device IMEI" required><Input value={form.imei} onChange={(event) => setForm((current) => ({ ...current, imei: event.target.value }))} /></Field>
          <Field label="GSM Number"><Input value={form.gsmNumber} onChange={(event) => setForm((current) => ({ ...current, gsmNumber: event.target.value }))} /></Field>
          <TcpField label="TCP IP/URL" value={form.tcpHost} onChange={(value) => setForm((current) => ({ ...current, tcpHost: value }))} onCheck={checkTcpEndpoint} placeholder="Click check" />
          <TcpField label="TCP Port" type="number" value={form.tcpPort} onChange={(value) => setForm((current) => ({ ...current, tcpPort: value }))} onCheck={checkTcpEndpoint} placeholder="Click check" />
          <div className="md:col-span-3"><Field label="Notes"><Input value={form.notes} onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))} /></Field></div>
          <div className="md:col-span-3 flex justify-end gap-2"><Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div>
        </form>
      </OrganizationTableCard>
    </section>;
  }

  return <section className="space-y-5 text-foreground"><PageHeader title="Device Register" icon={<Cpu className="h-5 w-5" />} actions={<Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button>} /><OrganizationTableCard>{notice ? <div className="mb-3 rounded-lg border border-border bg-muted p-3 text-sm">{notice}</div> : null}<DataTable data={rows} columns={columns} rowKey={(row) => row.id} emptyMessage={isLoading ? "Loading devices..." : "No device found."} bulkActions={bulkActions} actions={(row) => <div className="flex items-center gap-2">{canMove ? <Button type="button" size="icon" variant="outline" onClick={() => setMoveRows([row])} title="Move"><ArrowRightLeft className="h-4 w-4" /></Button> : null}<Button type="button" size="icon" variant="outline" onClick={() => startEdit(row)} title="Edit"><Pencil className="h-4 w-4" /></Button><Button type="button" size="icon" variant="destructive" onClick={() => deleteMutation.mutate(row.id)} title="Delete"><Trash2 className="h-4 w-4" /></Button></div>} /></OrganizationTableCard><AssetMoveDialog open={moveRows.length > 0} assetCount={moveRows.length} companies={companies.map((company) => ({ value: String(company.id), label: company.label, extra: company.extra }))} pending={moveMutation.isPending} error={moveMutation.isError ? notice : null} onClose={() => setMoveRows([])} onMove={(target) => moveMutation.mutate(target)} /></section>;
}

type FormState = { companyId: string; deviceModelId: string; imei: string; gsmNumber: string; tcpHost: string; tcpPort: string; notes: string };
function emptyForm(companyId?: number): FormState { return { companyId: companyId ? String(companyId) : "", deviceModelId: "", imei: "", gsmNumber: "", tcpHost: "", tcpPort: "", notes: "" }; }
function toInput(form: FormState): DeviceRegisterInput { return { companyId: toNumber(form.companyId), deviceModelId: toNumber(form.deviceModelId), imei: form.imei.trim(), gsmNumber: emptyToNull(form.gsmNumber), tcpHost: emptyToNull(form.tcpHost), tcpPort: toNumber(form.tcpPort), notes: emptyToNull(form.notes) }; }
function toNumber(value: string) { const parsed = Number(value); return Number.isFinite(parsed) && value !== "" ? parsed : null; }
function emptyToNull(value: string) { const cleaned = value.trim(); return cleaned ? cleaned : null; }
function formatDevice(row: DeviceRegisterRow) { return [row.deviceBrand, row.deviceModel].filter(Boolean).join(" ") || "-"; }
function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) { return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}{required ? <span className="ml-1 text-red-400">*</span> : null}</span>{children}</label>; }
function TcpField({ label, value, type = "text", placeholder, onChange, onCheck }: { label: string; value: string; type?: string; placeholder?: string; onChange: (value: string) => void; onCheck: () => void }) { return <Field label={label}><div className="flex gap-2"><Input type={type} min={type === "number" ? 1 : undefined} max={type === "number" ? 65535 : undefined} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} /><Button type="button" size="icon" variant="outline" onClick={onCheck} title={`Check ${label}`}><CheckCircle2 className="h-4 w-4" /></Button></div></Field>; }
function StatusCell({ status }: { status?: string | null }) { const online = (status || "").toUpperCase() === "ONLINE"; return <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-extrabold text-white"><span className={online ? "h-2.5 w-2.5 rounded-full bg-emerald-400" : "h-2.5 w-2.5 rounded-full bg-red-500"} />{online ? "ONLINE" : "OFFLINE"}</span>; }
function pickEndpointHost(endpoint: unknown) { const data = endpoint as Record<string, unknown>; const host = data?.host ?? data?.tcpHost ?? data?.ip ?? data?.url ?? data?.hostname; return typeof host === "string" ? host.trim() : host == null ? "" : String(host); }
function pickEndpointPort(endpoint: unknown) { const data = endpoint as Record<string, unknown>; const port = data?.port ?? data?.tcpPort ?? data?.gatewayPort; return typeof port === "string" ? port.trim() : port == null ? "" : String(port); }
