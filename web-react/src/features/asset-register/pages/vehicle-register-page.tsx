import { FormEvent, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CarFront, Pencil, Plus, Save, Trash2, Wrench, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { getMasterCountries, getMasterOptions, getVehicleModels, type MasterOptionRow, type MasterCountryRow, type VehicleModelRow } from "@/features/master-data/api/master-data-api";
import { getEnergyPrices, type EnergyPriceRow } from "@/features/asset-register/api/energy-price-api";
import { createVehicle, deleteVehicle, getVehicleCompanyOptions, getVehicles, setVehicleMaintenance, updateVehicle, type VehicleRegisterInput, type VehicleRegisterRow } from "@/features/asset-register/api/vehicle-register-api";

const currentYear = new Date().getFullYear();
const YEARS = Array.from({ length: currentYear - 1970 + 2 }, (_, index) => currentYear + 1 - index);

type VehicleForm = {
  companyId: string;
  vehicleName: string;
  plateNumber: string;
  vehicleTypeId: string;
  brandId: string;
  modelId: string;
  yearManufacture: string;
  countryCode: string;
  energyCode: string;
  ownershipTypeId: string;
  capacityValue: string;
  capacityUnitId: string;
  notes: string;
};

export function VehicleRegisterPage() {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<VehicleRegisterRow | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<VehicleForm>(emptyForm());

  const vehiclesQuery = useQuery({ queryKey: ["asset-register", "vehicles"], queryFn: getVehicles, refetchInterval: 30_000 });
  const companiesQuery = useQuery({ queryKey: ["asset-register", "vehicle-company-options"], queryFn: getVehicleCompanyOptions, refetchInterval: 60_000 });
  const typeQuery = useQuery({ queryKey: ["master-data", "vehicleTypes"], queryFn: () => getMasterOptions("vehicleTypes"), refetchInterval: 60_000 });
  const brandQuery = useQuery({ queryKey: ["master-data", "vehicleBrands"], queryFn: () => getMasterOptions("vehicleBrands"), refetchInterval: 60_000 });
  const modelQuery = useQuery({ queryKey: ["master-data", "vehicleModels", form.brandId], queryFn: () => getVehicleModels(form.brandId ? Number(form.brandId) : null), refetchInterval: 60_000 });
  const ownershipQuery = useQuery({ queryKey: ["master-data", "ownershipTypes"], queryFn: () => getMasterOptions("ownershipTypes"), refetchInterval: 60_000 });
  const capacityUnitQuery = useQuery({ queryKey: ["master-data", "capacityUnits"], queryFn: () => getMasterOptions("capacityUnits"), refetchInterval: 60_000 });
  const countryQuery = useQuery({ queryKey: ["master-data", "countries"], queryFn: getMasterCountries, refetchInterval: 60_000 });
  const energyQuery = useQuery({ queryKey: ["asset-register", "energy-prices", form.countryCode], queryFn: () => getEnergyPrices(form.countryCode || "ID"), refetchInterval: 60_000 });

  const selectedEnergy = (energyQuery.data || []).find((item) => item.energyCode === form.energyCode);

  useEffect(() => {
    if (!form.countryCode && (countryQuery.data || []).length > 0) setForm((current) => ({ ...current, countryCode: countryQuery.data?.[0]?.countryCode || "ID" }));
  }, [countryQuery.data, form.countryCode]);

  const saveMutation = useMutation({
    mutationFn: ({ id, input }: { id?: number; input: VehicleRegisterInput }) => id ? updateVehicle(id, input) : createVehicle(input),
    onSuccess: () => { setNotice("Vehicle berhasil disimpan."); resetForm(); queryClient.invalidateQueries({ queryKey: ["asset-register", "vehicles"] }); },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Save vehicle gagal.")
  });
  const deleteMutation = useMutation({ mutationFn: deleteVehicle, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["asset-register", "vehicles"] }) });
  const maintenanceMutation = useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => setVehicleMaintenance(id, active), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["asset-register", "vehicles"] }) });

  const bulkActions = useMemo<DataTableBulkAction<VehicleRegisterRow>[]>(() => [
    { key: "maintenance-on", label: "Set Maintenance", icon: <Wrench className="h-4 w-4" />, variant: "outline", confirmMessage: (rows) => `Set ${rows.length} vehicle(s) to maintenance?`, onClick: (rows) => rows.forEach((row) => maintenanceMutation.mutate({ id: row.id, active: true })) },
    { key: "maintenance-off", label: "Clear Maintenance", icon: <Wrench className="h-4 w-4" />, variant: "outline", confirmMessage: (rows) => `Clear maintenance for ${rows.length} vehicle(s)?`, onClick: (rows) => rows.forEach((row) => maintenanceMutation.mutate({ id: row.id, active: false })) },
    { key: "delete", label: "Delete", icon: <Trash2 className="h-4 w-4" />, variant: "outline", confirmMessage: (rows) => `Move ${rows.length} vehicle(s) to Wasted?`, onClick: (rows) => rows.forEach((row) => deleteMutation.mutate(row.id)) }
  ], [deleteMutation, maintenanceMutation]);

  const columns = useMemo<DataTableColumn<VehicleRegisterRow>[]>(() => [
    { key: "companyName", label: "Company Name", value: (row) => row.companyName, render: (row) => <span className="font-bold text-white">{row.companyName}</span> },
    { key: "vehicleName", label: "Vehicle Name", value: (row) => row.vehicleName || "-", render: (row) => <span className="font-bold text-white">{row.vehicleName || "-"}</span> },
    { key: "vehicleType", label: "Vehicle Type", value: (row) => row.vehicleType || "-" },
    { key: "brand", label: "Brand", value: (row) => row.brand || "-" },
    { key: "model", label: "Model", value: (row) => row.model || "-" },
    { key: "yearManufacture", label: "Years", value: (row) => row.yearManufacture || "-" },
    { key: "plateNumber", label: "Plat Number", value: (row) => row.plateNumber, render: (row) => <span className="font-extrabold text-foreground">{row.plateNumber}</span> },
    { key: "energyName", label: "Energi", value: (row) => row.energyName || row.energyCode || "-" },
    { key: "energyPriceSnapshot", label: "Harga Energy", value: (row) => money(row.energyPriceSnapshot, row.energyCurrency), render: (row) => <span>{money(row.energyPriceSnapshot, row.energyCurrency)}</span> },
    { key: "countryName", label: "Country", value: (row) => row.countryName || row.countryCode },
    { key: "ownership", label: "Ownership", value: (row) => row.ownership || "-" },
    { key: "capacityValue", label: "Tank Capacity", value: (row) => `${row.capacityValue || "-"} ${row.capacityUnit || ""}`, render: (row) => <span>{row.capacityValue || "-"} {row.capacityUnit || ""}</span> },
    { key: "operationalStatus", label: "Status", value: (row) => row.operationalStatus, render: (row) => <VehicleStatus status={row.operationalStatus} /> },
    { key: "createdAt", label: "Created At", value: (row) => formatDateTime(row.createdAt), render: (row) => <span>{formatDateTime(row.createdAt)}</span> },
    { key: "createdByEmail", label: "Created By", value: (row) => row.createdByEmail || "-" }
  ], []);

  function startCreate() { setForm(emptyForm(countryQuery.data?.[0]?.countryCode || "ID")); setEditing(null); setFormOpen(true); setNotice(null); }
  function startEdit(row: VehicleRegisterRow) { setEditing(row); setForm(fromRow(row)); setFormOpen(true); setNotice(null); }
  function resetForm() { setEditing(null); setFormOpen(false); setForm(emptyForm(countryQuery.data?.[0]?.countryCode || "ID")); }
  function submit(event: FormEvent) { event.preventDefault(); saveMutation.mutate({ id: editing?.id, input: normalizeInput(form) }); }

  if (formOpen) {
    return (
      <section className="space-y-5 text-foreground">
        <PageHeader icon={<CarFront className="h-5 w-5" />} title={editing ? "Edit Vehicle" : "Vehicle Registration"} />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submit}>
            <SelectField label="Company" value={form.companyId} onChange={(v) => setForm((c) => ({ ...c, companyId: v }))} options={(companiesQuery.data || []).map((c) => ({ value: String(c.id), label: c.label, extra: c.extra }))} />
            <Field label="Vehicle Name"><Input required value={form.vehicleName} onChange={(e) => setForm((c) => ({ ...c, vehicleName: e.target.value.toUpperCase() }))} placeholder="VEHICLE NAME" /></Field>
            <Field label="Plat Number"><Input required value={form.plateNumber} onChange={(e) => setForm((c) => ({ ...c, plateNumber: normalizePlateNumber(e.target.value) }))} placeholder="B 1234 ABC" /></Field>
            <SelectField label="Vehicle Type" value={form.vehicleTypeId} onChange={(v) => setForm((c) => ({ ...c, vehicleTypeId: v }))} options={toOptions(typeQuery.data || [])} />
            <SelectField label="Brand" value={form.brandId} onChange={(v) => setForm((c) => ({ ...c, brandId: v, modelId: "" }))} options={toOptions(brandQuery.data || [])} />
            <SelectField label="Model" value={form.modelId} onChange={(v) => setForm((c) => ({ ...c, modelId: v }))} options={(modelQuery.data || []).map((m) => ({ value: String(m.id), label: m.modelName || m.name }))} />
            <SelectField label="Years" value={form.yearManufacture} onChange={(v) => setForm((c) => ({ ...c, yearManufacture: v }))} options={YEARS.map((year) => ({ value: String(year), label: String(year) }))} />
            <SelectField label="Country" value={form.countryCode} onChange={(v) => setForm((c) => ({ ...c, countryCode: v, energyCode: "" }))} options={(countryQuery.data || []).map((c) => ({ value: c.countryCode, label: `${c.countryName} (${c.currency})` }))} />
            <SelectField label="Energi" value={form.energyCode} onChange={(v) => setForm((c) => ({ ...c, energyCode: v }))} options={(energyQuery.data || []).map((e) => ({ value: e.energyCode, label: `${e.energyName} (${e.unit})` }))} />
            <ReadOnly label="Harga Energy" value={selectedEnergy ? money(selectedEnergy.priceEnergy, selectedEnergy.currency) : "-"} />
            <SelectField label="Ownership" value={form.ownershipTypeId} onChange={(v) => setForm((c) => ({ ...c, ownershipTypeId: v }))} options={toOptions(ownershipQuery.data || [])} />
            <Field label="Tank Capacity"><div className="grid grid-cols-[1fr_140px] gap-2"><Input type="number" min="0" step="0.01" value={form.capacityValue} onChange={(e) => setForm((c) => ({ ...c, capacityValue: e.target.value }))} placeholder="Capacity" /><SelectField label="Unit" hideLabel value={form.capacityUnitId} onChange={(v) => setForm((c) => ({ ...c, capacityUnitId: v }))} options={toOptions(capacityUnitQuery.data || [])} /></div></Field>
            <Field label="Notes"><Input value={form.notes} onChange={(e) => setForm((c) => ({ ...c, notes: e.target.value }))} /></Field>
            <div className="md:col-span-2 flex justify-end gap-3 pt-2"><Button type="button" variant="outline" onClick={resetForm}><X className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  return (
    <section className="space-y-5 text-foreground">
      <PageHeader icon={<CarFront className="h-5 w-5" />} title="Vehicle Register" actions={<Button type="button" onClick={startCreate}><Plus className="h-4 w-4" /> Created New</Button>} />
      <OrganizationTableCard>
        {notice ? <Notice message={notice} /> : null}
        <DataTable data={vehiclesQuery.data || []} columns={columns} rowKey={(row) => row.id} emptyMessage={vehiclesQuery.isLoading ? "Loading vehicles..." : "No vehicle found."} bulkActions={bulkActions} actions={(row) => <div className="flex gap-2"><Button type="button" size="sm" variant="outline" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /> Edit</Button><Button type="button" size="sm" variant="outline" onClick={() => maintenanceMutation.mutate({ id: row.id, active: row.operationalStatus !== "MAINTENANCE" })}><Wrench className="h-4 w-4" /> Maintenance</Button><Button type="button" size="sm" variant="destructive" onClick={() => deleteMutation.mutate(row.id)}><Trash2 className="h-4 w-4" /> Delete</Button></div>} />
      </OrganizationTableCard>
    </section>
  );
}

function VehicleStatus({ status }: { status?: string | null }) {
  const normalized = (status || "UNKNOWN").toUpperCase();
  const className = normalized === "MOVING" ? "bg-emerald-500" : normalized === "IDLE" ? "bg-yellow-400" : normalized === "STOP" ? "bg-red-500" : normalized === "MAINTENANCE" ? "bg-sky-500" : "bg-white";
  return <span className="inline-flex items-center gap-2"><span className={`h-3 w-3 rounded-full ${className}`} /><StatusBadge status={normalized} /></span>;
}
function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="grid gap-1 text-xs font-bold text-slate-300"><span>{label}</span>{children}</label>; }
function SelectField({ label, value, onChange, options, hideLabel = false }: { label: string; value: string; onChange: (value: string) => void; options: { value: string; label: string; extra?: string | null }[]; hideLabel?: boolean }) { return hideLabel ? <SearchableSelect label="Unit" value={value} onChange={onChange} options={options} placeholder="Unit" /> : <SearchableSelect label={label} value={value} onChange={onChange} options={options} />; }
function ReadOnly({ label, value }: { label: string; value: string }) { return <div className="grid gap-1 text-xs font-bold text-slate-300"><span>{label}</span><div className="flex h-10 items-center rounded-md border border-white/10 bg-white/5 px-3 text-sm text-white">{value}</div></div>; }
function Notice({ message }: { message: string }) { return <div className="rounded-lg border border-amber-400/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">{message}</div>; }
function emptyForm(countryCode = "ID"): VehicleForm { return { companyId: "", vehicleName: "", plateNumber: "", vehicleTypeId: "", brandId: "", modelId: "", yearManufacture: String(currentYear), countryCode, energyCode: "", ownershipTypeId: "", capacityValue: "", capacityUnitId: "", notes: "" }; }
function fromRow(row: VehicleRegisterRow): VehicleForm { return { companyId: String(row.companyId), vehicleName: row.vehicleName || "", plateNumber: normalizePlateNumber(row.plateNumber || ""), vehicleTypeId: row.vehicleTypeId ? String(row.vehicleTypeId) : "", brandId: row.brandId ? String(row.brandId) : "", modelId: row.modelId ? String(row.modelId) : "", yearManufacture: row.yearManufacture ? String(row.yearManufacture) : String(currentYear), countryCode: row.countryCode || "ID", energyCode: row.energyCode || "", ownershipTypeId: row.ownershipTypeId ? String(row.ownershipTypeId) : "", capacityValue: row.capacityValue ? String(row.capacityValue) : "", capacityUnitId: row.capacityUnitId ? String(row.capacityUnitId) : "", notes: "" }; }
function normalizePlateNumber(value: string) { return value.toUpperCase().replace(/\s+/g, ""); }
function normalizeInput(form: VehicleForm): VehicleRegisterInput { return { companyId: form.companyId ? Number(form.companyId) : null, vehicleCode: null, vehicleName: form.vehicleName.trim() || null, plateNumber: normalizePlateNumber(form.plateNumber), vehicleTypeId: form.vehicleTypeId ? Number(form.vehicleTypeId) : null, brandId: form.brandId ? Number(form.brandId) : null, modelId: form.modelId ? Number(form.modelId) : null, yearManufacture: form.yearManufacture ? Number(form.yearManufacture) : null, countryCode: form.countryCode || "ID", energyCode: form.energyCode || null, ownershipTypeId: form.ownershipTypeId ? Number(form.ownershipTypeId) : null, capacityValue: form.capacityValue ? Number(form.capacityValue) : null, capacityUnitId: form.capacityUnitId ? Number(form.capacityUnitId) : null, operationalStatus: "UNKNOWN", notes: form.notes.trim() || null }; }
function toOptions(rows: MasterOptionRow[]) { return rows.filter((row) => row.active).map((row) => ({ value: String(row.id), label: row.name })); }
function money(value?: number | null, currency = "IDR") { if (value === null || value === undefined) return "-"; return new Intl.NumberFormat("id-ID", { style: "currency", currency, maximumFractionDigits: 2 }).format(value); }
