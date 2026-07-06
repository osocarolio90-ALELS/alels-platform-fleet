import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { ClipboardList, Link2, Pencil, Plus, Unlink, UserMinus } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/ui/page-header";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { cn } from "@/lib/utils";
import {
  AssetAssignmentInput,
  AssetAssignmentRow,
  AssignmentLookupOption,
  createAssetAssignment,
  getAssetAssignments,
  getAssignmentDeviceOptions,
  getAssignmentDriverOptions,
  getAssignmentVehicleOptions,
  unpairAssignmentDevice,
  unpairAssignmentDriver,
  unpairAssignmentVehicle,
  updateAssetAssignment
} from "@/features/telemetry/assignment/api/assignment-api";

type FormMode = "list" | "form";

const blankForm: AssetAssignmentInput = {
  companyId: null,
  vehicleId: null,
  deviceId: null,
  driverId: null,
  notes: ""
};

export function AssignmentPage() {
  const [mode, setMode] = useState<FormMode>("list");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const [rows, setRows] = useState<AssetAssignmentRow[]>([]);
  const [vehicles, setVehicles] = useState<AssignmentLookupOption[]>([]);
  const [devices, setDevices] = useState<AssignmentLookupOption[]>([]);
  const [drivers, setDrivers] = useState<AssignmentLookupOption[]>([]);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<AssetAssignmentInput>(blankForm);

  useEffect(() => {
    void loadAll();
  }, []);

  useEffect(() => {
    if (!message) return;

    const timer = window.setTimeout(() => {
      setMessage("");
    }, 1200);

    return () => window.clearTimeout(timer);
  }, [message]);

  async function loadAll() {
    setLoading(true);
    setError("");
    try {
      const [assignmentRows, vehicleData, deviceData, driverData] = await Promise.all([
        getAssetAssignments(),
        getAssignmentVehicleOptions(),
        getAssignmentDeviceOptions(),
        getAssignmentDriverOptions()
      ]);
      setRows(assignmentRows);
      setVehicles(vehicleData);
      setDevices(deviceData);
      setDrivers(driverData);
    } catch (err) {
      setError(apiErrorMessage(err, "Assignment API belum tersedia atau backend belum berjalan."));
    } finally {
      setLoading(false);
    }
  }

  function optionItems(items: AssignmentLookupOption[]) {
    return items.map((item) => ({
      value: String(item.id),
      label: item.label,
      extra: [item.code, item.extra].filter(Boolean).join(" • ")
    }));
  }

  const availableVehicles = useMemo(() => {
    const pairedVehicleIds = new Set(
      rows
        .filter((row) => row.assignmentStatus === "ACTIVE" && row.vehicleId !== form.vehicleId)
        .map((row) => row.vehicleId)
        .filter(Boolean)
    );

    return vehicles.filter((vehicle) => vehicle.companyId === form.companyId && !pairedVehicleIds.has(vehicle.id));
  }, [form.companyId, form.vehicleId, rows, vehicles]);

  const availableDevices = useMemo(() => {
    const pairedDeviceIds = new Set(
      rows
        .filter((row) => row.assignmentStatus === "ACTIVE" && row.deviceId !== form.deviceId)
        .map((row) => row.deviceId)
        .filter(Boolean)
    );

    return devices.filter((device) => !pairedDeviceIds.has(device.id));
  }, [form.deviceId, rows, devices]);

  const availableDrivers = useMemo(
    () => drivers.filter((driver) => driver.companyId === form.companyId),
    [drivers, form.companyId]
  );
  const selectedDevice = devices.find((device) => device.id === form.deviceId);

  function selectDevice(value: string) {
    const deviceId = toNumber(value);
    const device = devices.find((item) => item.id === deviceId);
    setForm((current) => ({
      ...current,
      deviceId,
      companyId: device?.companyId || null,
      vehicleId: device?.companyId === current.companyId ? current.vehicleId : null,
      driverId: device?.companyId === current.companyId ? current.driverId : null
    }));
  }

  function startAdd() {
    setMessage("");
    setError("");
    setEditingId(null);
    setForm(blankForm);
    setMode("form");
  }

  function startEdit(row: AssetAssignmentRow) {
    setMessage("");
    setError("");
    setEditingId(row.id);
    setForm({
      companyId: row.companyId,
      vehicleId: row.vehicleId,
      deviceId: row.deviceId,
      driverId: row.driverId || null,
      notes: ""
    });
    setMode("form");
  }

  function cancelForm() {
    setMode("list");
    setEditingId(null);
    setForm(blankForm);
    setError("");
  }

  async function save() {
    setMessage("");
    if (!form.deviceId || !form.vehicleId) {
      setError("Device dan vehicle wajib diisi. Driver dan notes tidak wajib.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      if (editingId) await updateAssetAssignment(editingId, form);
      else await createAssetAssignment(form);
      setMessage("Asset pairing berhasil disimpan.");
      cancelForm();
      await loadAll();
    } catch (err) {
      setError(apiErrorMessage(err, "Gagal menyimpan asset pairing."));
    } finally {
      setLoading(false);
    }
  }

  async function unpairVehicle(row: AssetAssignmentRow) {
    setMessage("");
    if (!window.confirm(`Unpair vehicle ${row.vehicleName}? Device dan driver pairing terkait juga akan dilepas.`)) return;
    setLoading(true);
    try {
      await unpairAssignmentVehicle(row.id);
      setMessage("Vehicle pairing berhasil dilepas.");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gagal unpair vehicle.");
    } finally {
      setLoading(false);
    }
  }

  async function unpairDevice(row: AssetAssignmentRow) {
    setMessage("");
    if (!window.confirm(`Unpair device ${row.deviceImei}? Vehicle dan driver pairing terkait juga akan dilepas.`)) return;
    setLoading(true);
    try {
      await unpairAssignmentDevice(row.id);
      setMessage("Device pairing berhasil dilepas.");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gagal unpair device.");
    } finally {
      setLoading(false);
    }
  }

  async function unpairDriver(row: AssetAssignmentRow) {
    setMessage("");
    if (!row.driverId) return;
    if (!window.confirm(`Unpair driver ${row.driverName || "ini"}? Vehicle dan device tetap pairing.`)) return;
    setLoading(true);
    try {
      await unpairAssignmentDriver(row.id);
      setMessage("Driver pairing berhasil dilepas.");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gagal unpair driver.");
    } finally {
      setLoading(false);
    }
  }

  async function bulkUnpairDevice(selectedRows: AssetAssignmentRow[]) {
    setMessage("");
    if (!window.confirm(`Unpair ${selectedRows.length} asset pairing?`)) return;
    setLoading(true);
    try {
      await Promise.all(selectedRows.map((row) => unpairAssignmentDevice(row.id)));
      setMessage("Bulk unpair asset berhasil.");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Gagal bulk unpair asset.");
    } finally {
      setLoading(false);
    }
  }

  const columns = useMemo<DataTableColumn<AssetAssignmentRow>[]>(() => [
    { key: "companyName", label: "Company", searchable: true },
    { key: "vehicleName", label: "Vehicle", searchable: true },
    { key: "plateNumber", label: "Plate Number", searchable: true, value: (row) => row.plateNumber || "-" },
    { key: "deviceLabel", label: "Device", searchable: true },
    { key: "deviceImei", label: "IMEI", searchable: true },
    { key: "driverName", label: "Driver", searchable: true, value: (row) => row.driverName || "-" },
    { key: "rfidIbutton", label: "RFID/IButton", searchable: true, value: (row) => row.rfidIbutton || "-" },
    { key: "assignmentStatus", label: "Status", render: (row) => <StatusBadge value={row.assignmentStatus} /> },
    { key: "assignedAt", label: "Assigned At", value: (row) => row.assignedAt || "-" },
    { key: "assignedBy", label: "Assigned By", value: (row) => row.assignedBy || "-" }
  ], []);

  if (mode === "form") {
    return (
      <section className="space-y-5">
        <PageHeader title={editingId ? "Edit Asset Pairing" : "Pairing Your Asset"} icon={<Link2 className="h-5 w-5" />} />
        {error ? <Alert tone="error" message={error} /> : null}
        <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
          <div className="grid gap-4 md:grid-cols-3">
            <SearchableSelect required label="Device" value={String(form.deviceId || "")} onChange={selectDevice} options={optionItems(availableDevices)} />
            <label className="grid gap-1 text-xs font-bold text-muted-foreground">Company<Input value={selectedDevice?.companyName || ""} readOnly aria-readonly="true" /></label>
            <SearchableSelect required label="Vehicle" value={String(form.vehicleId || "")} onChange={(value) => setForm((current) => ({ ...current, vehicleId: toNumber(value) }))} options={optionItems(availableVehicles)} />
            <SearchableSelect label="Driver" value={String(form.driverId || "")} onChange={(value) => setForm((current) => ({ ...current, driverId: toNumber(value) }))} options={optionItems(availableDrivers)} />
            <label className="grid gap-1 text-xs font-bold text-slate-300 md:col-span-2">Notes<Input value={form.notes || ""} onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))} /></label>
          </div>
          <FormActions loading={loading} onCancel={cancelForm} onSave={save} />
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-5">
      <PageHeader
        title="Assignment Asset"
        icon={<ClipboardList className="h-5 w-5" />}
        actions={(
          <Button type="button" onClick={startAdd}>
            <Plus className="h-4 w-4" /> Add
          </Button>
        )}
      />
      {message ? <Alert tone="success" message={message} /> : null}
      {error ? <Alert tone="error" message={error} /> : null}
      <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
        <DataTable
          data={rows}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={loading ? "Loading assignment..." : "No asset pairing found."}
          bulkActions={[{ key: "unpair-device", label: "Unpair Asset", icon: <Unlink className="h-4 w-4" />, variant: "destructive", onClick: bulkUnpairDevice }]}
          actions={(row) => (
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /> Edit Pairing</Button>
              <Button type="button" variant="destructive" size="sm" onClick={() => void unpairVehicle(row)}><Unlink className="h-4 w-4" /> Unpair Vehicle</Button>
              <Button type="button" variant="destructive" size="sm" onClick={() => void unpairDevice(row)}><Unlink className="h-4 w-4" /> Unpair Device</Button>
              {row.driverId ? <Button type="button" variant="destructive" size="sm" onClick={() => void unpairDriver(row)}><UserMinus className="h-4 w-4" /> Unpair Driver</Button> : null}
            </div>
          )}
        />
      </div>
    </section>
  );
}

function StatusBadge({ value }: { value?: string | null }) {
  const normalized = (value || "-").toUpperCase();
  return <span className={cn("inline-flex rounded-md px-2 py-1 text-xs font-extrabold", normalized === "ACTIVE" ? "bg-sky-500/15 text-sky-300" : "bg-slate-500/15 text-slate-300")}>{normalized}</span>;
}

function Alert({ tone, message }: { tone: "success" | "error"; message: string }) {
  return <div className={cn("rounded-lg border px-4 py-3 text-sm font-bold", tone === "success" ? "border-amber-500/40 bg-amber-500/10 text-amber-200" : "border-red-500/40 bg-red-500/10 text-red-200")}>{message}</div>;
}

function FormActions({ loading, onCancel, onSave }: { loading: boolean; onCancel: () => void; onSave: () => void | Promise<void> }) {
  return <div className="mt-6 flex justify-end gap-2"><Button type="button" variant="outline" onClick={onCancel}>Cancel</Button><Button type="button" disabled={loading} onClick={() => void onSave()}>Save</Button></div>;
}

function toNumber(value: string) {
  if (!value) return null;
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return error instanceof Error ? error.message : fallback;
  const data = error.response?.data as { detail?: string; message?: string; error?: string } | undefined;
  return data?.detail || data?.message || data?.error || fallback;
}
