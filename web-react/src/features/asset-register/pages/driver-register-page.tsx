import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, IdCard, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { SquarePhotoEditor } from "@/components/media/square-photo-editor";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { getLicenseMaster } from "@/features/master-data/api/license-master-api";
import {
  activateDriver,
  createDriver,
  deleteDriver,
  getDriverCompanyOptions,
  getDriverPhoto,
  getDrivers,
  suspendDriver,
  updateDriverPhoto,
  removeDriverPhoto,
  updateDriver,
  type DriverRegisterInput,
  type DriverRegisterRow
} from "@/features/asset-register/api/driver-register-api";

type FormState = { companyId: string; driverId: string; employeeId: string; driverName: string; licenseNumber: string; countryCode: string; licenseMasterId: string; phoneNumber: string; rfidIbutton: string; status: string };

export function DriverRegisterPage() {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DriverRegisterRow | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [notice, setNotice] = useState<string | null>(null);
  const [photo, setPhoto] = useState<File | null>(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [previewDriver, setPreviewDriver] = useState<DriverRegisterRow | null>(null);

  const driversQuery = useQuery({ queryKey: ["driver-register"], queryFn: getDrivers });
  const companiesQuery = useQuery({ queryKey: ["driver-register", "companies"], queryFn: getDriverCompanyOptions });
  const licenseMasterQuery = useQuery({ queryKey: ["license-master"], queryFn: getLicenseMaster });

  const countryOptions = useMemo(() => {
    const map = new Map<string, { value: string; label: string; extra?: string | null }>();
    for (const license of licenseMasterQuery.data || []) {
      if (!license.active) continue;
      const code = license.countryCode || "";
      const name = license.countryName || code;
      if (!code || !name) continue;
      map.set(code, { value: code, label: name, extra: code });
    }
    return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label));
  }, [licenseMasterQuery.data]);

  const licenseOptions = useMemo(() => {
    return (licenseMasterQuery.data || [])
      .filter((license) => license.active && license.countryCode === form.countryCode)
      .map((license) => ({ value: String(license.id), label: license.name, extra: license.code }));
  }, [licenseMasterQuery.data, form.countryCode]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const driverId = editing ? (await updateDriver(editing.id, toInput(form)), editing.id) : (await createDriver(toInput(form))).id;
      if (removePhoto && editing?.photoUrl) await removeDriverPhoto(driverId);
      if (photo) await updateDriverPhoto(driverId, photo);
      return driverId;
    },
    onSuccess: () => { closeForm(); queryClient.invalidateQueries({ queryKey: ["driver-register"] }); },
    onError: (error: any) => setNotice(error?.response?.data?.message || "Driver gagal disimpan.")
  });
  const deleteMutation = useMutation({
    mutationFn: deleteDriver,
    onSuccess: () => {
      setNotice(null);
      queryClient.invalidateQueries({ queryKey: ["driver-register"] });
      queryClient.invalidateQueries({ queryKey: ["asset-register", "wasted"] });
    },
    onError: (error: any) => setNotice(error?.response?.data?.message || "Driver gagal dipindahkan ke Wasted.")
  });
  const suspendMutation = useMutation({ mutationFn: suspendDriver, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["driver-register"] }) });
  const activateMutation = useMutation({ mutationFn: activateDriver, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["driver-register"] }) });

  const columns = useMemo<DataTableColumn<DriverRegisterRow>[]>(() => [
    { key: "photoUrl", label: "Photo", searchable: false, filterable: false, value: (row) => row.photoUrl || "", render: (row) => <button type="button" className="rounded-full" onClick={() => setPreviewDriver(row)} title="View photo"><DriverPhoto photoUrl={row.photoUrl} alt={row.driverName} className="h-12 w-12 rounded-full border border-border object-cover" /></button> },
    { key: "companyName", label: "Company name", value: (row) => row.companyName || "-", render: (row) => <span className="font-bold text-white">{row.companyName || "-"}</span> },
    { key: "driverId", label: "Driver ID", value: (row) => row.driverId, render: (row) => <span className="font-bold text-white">{row.driverId}</span> },
    { key: "employeeId", label: "Employee ID", value: (row) => row.employeeId || "-" },
    { key: "driverName", label: "Driver Name", value: (row) => row.driverName, render: (row) => <span className="font-bold text-white">{row.driverName}</span> },
    { key: "licenseNumber", label: "License Number", value: (row) => row.licenseNumber },
    { key: "countryName", label: "Country", value: (row) => row.countryName || row.countryCode },
    { key: "licenseType", label: "License Type", value: (row) => row.licenseType || "-" },
    { key: "phoneNumber", label: "Phone No.", value: (row) => row.phoneNumber || "-" },
    { key: "rfidIbutton", label: "RFID/IButton", value: (row) => row.rfidIbutton || "-" },
    { key: "createdAt", label: "Created at", value: (row) => row.createdAt || "", render: (row) => formatDateTime(row.createdAt) },
    { key: "createdBy", label: "Created by", value: (row) => row.createdBy || "-" },
    { key: "status", label: "Status", value: (row) => row.status, render: (row) => <StatusBadge status={row.status || "ACTIVE"} /> }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<DriverRegisterRow>[]>(() => [
    { key: "delete", label: "Delete", icon: <Trash2 className="h-4 w-4" />, confirmMessage: (rows) => `Move ${rows.length} selected driver(s) to Wasted?`, onClick: async (rows) => { for (const row of rows) await deleteDriver(row.id); queryClient.invalidateQueries({ queryKey: ["driver-register"] }); queryClient.invalidateQueries({ queryKey: ["asset-register", "wasted"] }); } },
    { key: "suspend", label: "Suspend", confirmMessage: (rows) => `Suspend ${rows.length} selected driver(s)?`, onClick: async (rows) => { for (const row of rows) await suspendDriver(row.id); queryClient.invalidateQueries({ queryKey: ["driver-register"] }); } },
    { key: "activate", label: "Activate", confirmMessage: (rows) => `Activate ${rows.length} selected driver(s)?`, onClick: async (rows) => { for (const row of rows) await activateDriver(row.id); queryClient.invalidateQueries({ queryKey: ["driver-register"] }); } }
  ], [queryClient]);

  function openCreate() { setEditing(null); setPhoto(null); setRemovePhoto(false); setNotice(null); setForm(emptyForm(companiesQuery.data?.[0]?.value || "", countryOptions[0]?.value || "")); setFormOpen(true); }
  function openEdit(row: DriverRegisterRow) { setEditing(row); setPhoto(null); setRemovePhoto(false); setNotice(null); setForm({ companyId: String(row.companyId || ""), driverId: row.driverId || "", employeeId: row.employeeId || "", driverName: row.driverName || "", licenseNumber: row.licenseNumber || "", countryCode: row.countryCode || "ID", licenseMasterId: row.licenseMasterId ? String(row.licenseMasterId) : "", phoneNumber: row.phoneNumber || "", rfidIbutton: row.rfidIbutton || "", status: row.status || "ACTIVE" }); setFormOpen(true); }
  function closeForm() { setFormOpen(false); setEditing(null); setPhoto(null); setRemovePhoto(false); setNotice(null); }
  function submit(event: FormEvent) { event.preventDefault(); if (!requiredValid(form)) { setNotice("Company, Driver ID, Driver Name, License Number, Country, License Type, dan Phone Number wajib diisi."); return; } saveMutation.mutate(); }

  if (formOpen) {
    return <section className="space-y-5 text-foreground"><PageHeader icon={<IdCard className="h-5 w-5" />} title={editing ? "Edit Driver" : "Create New Driver"} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<form className="grid gap-4 md:grid-cols-3" onSubmit={submit}><div className="md:col-span-3"><DriverPhotoEditor photoUrl={removePhoto ? null : editing?.photoUrl} value={photo} onChange={(file) => { setPhoto(file); setRemovePhoto(false); }} onRemove={() => { setPhoto(null); setRemovePhoto(true); }} onError={setNotice} disabled={saveMutation.isPending} /></div><SearchableSelect label="Company name" value={form.companyId} onChange={(value) => setForm((current) => ({ ...current, companyId: value }))} options={(companiesQuery.data || []).map((company) => ({ value: company.value, label: company.label, extra: company.extra }))} required /><Field label="Driver ID" required><Input required value={form.driverId} onChange={(event) => setForm((current) => ({ ...current, driverId: event.target.value.toUpperCase() }))} /></Field><Field label="Employee ID"><Input value={form.employeeId} onChange={(event) => setForm((current) => ({ ...current, employeeId: event.target.value.toUpperCase() }))} /></Field><Field label="Driver Name" required><Input required value={form.driverName} onChange={(event) => setForm((current) => ({ ...current, driverName: event.target.value.toUpperCase() }))} /></Field><Field label="License Number" required><Input required value={form.licenseNumber} onChange={(event) => setForm((current) => ({ ...current, licenseNumber: event.target.value.toUpperCase() }))} /></Field><SearchableSelect label="Country" value={form.countryCode} onChange={(value) => setForm((current) => ({ ...current, countryCode: value, licenseMasterId: "" }))} options={countryOptions} required /><SearchableSelect label="License Type" value={form.licenseMasterId} onChange={(value) => setForm((current) => ({ ...current, licenseMasterId: value }))} options={licenseOptions} required disabled={!form.countryCode} /><Field label="Phone Number" required><Input required value={form.phoneNumber} onChange={(event) => setForm((current) => ({ ...current, phoneNumber: event.target.value }))} /></Field><Field label="RFID/IButton"><Input value={form.rfidIbutton} onChange={(event) => setForm((current) => ({ ...current, rfidIbutton: event.target.value.toUpperCase() }))} placeholder="HEX ID" /></Field><Field label="Status"><select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={form.status} onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}><option value="ACTIVE">ACTIVE</option><option value="SUSPENDED">SUSPENDED</option></select></Field><div className="md:col-span-3 flex justify-end gap-3"><Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div></form></OrganizationTableCard></section>;
  }

  return <section className="space-y-5 text-foreground"><PageHeader icon={<IdCard className="h-5 w-5" />} title="Driver Register" actions={<Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button>} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<DataTable data={driversQuery.data || []} columns={columns} rowKey={(row) => row.id} emptyMessage={driversQuery.isLoading ? "Loading drivers..." : "No driver found."} bulkActions={bulkActions} actions={(row) => <div className="flex items-center gap-2"><Button type="button" size="icon" variant="outline" onClick={() => setPreviewDriver(row)} title="View photo"><Eye className="h-4 w-4" /></Button><Button type="button" size="icon" variant="outline" onClick={() => openEdit(row)} title="Edit"><Pencil className="h-4 w-4" /></Button><Button type="button" size="icon" variant="outline" onClick={() => row.status === "SUSPENDED" ? activateMutation.mutate(row.id) : suspendMutation.mutate(row.id)} title={row.status === "SUSPENDED" ? "Active" : "Suspend"}>{row.status === "SUSPENDED" ? "On" : "Off"}</Button><Button type="button" size="icon" variant="destructive" onClick={() => confirm(`Move ${row.driverName} to Wasted?`) && deleteMutation.mutate(row.id)} title="Move to Wasted"><Trash2 className="h-4 w-4" /></Button></div>} /></OrganizationTableCard>{previewDriver ? <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true"><div className="w-full max-w-md rounded-2xl border border-border bg-card p-5 shadow-2xl"><div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-bold text-card-foreground">{previewDriver.driverName}</h2><Button type="button" variant="outline" size="icon" onClick={() => setPreviewDriver(null)}><X className="h-4 w-4" /></Button></div><DriverPhoto photoUrl={previewDriver.photoUrl} alt={previewDriver.driverName} className="aspect-square w-full rounded-xl object-cover" /></div></div> : null}</section>;
}

const photoLabels = { photo: "Driver Photo (Optional)", upload: "Upload Image", camera: "Capture Camera", replace: "Replace Photo", remove: "Remove Photo", cropTitle: "Crop Driver Photo", cropHelp: "Move the image inside the square. Output is 512 x 512.", save: "Save", cancel: "Cancel", selectCamera: "Select Camera", capture: "Capture", cameraError: "Camera cannot be accessed.", invalidType: "Photo must be JPG, JPEG, PNG, or WEBP.", maximumSize: "Maximum photo size is 5 MB." };

function DriverPhoto({ photoUrl, alt, className }: { photoUrl?: string | null; alt: string; className: string }) {
  const source = useDriverPhotoSource(photoUrl);
  return <img src={source} alt={alt} className={className} />;
}

function DriverPhotoEditor({ photoUrl, value, onChange, onRemove, onError, disabled }: { photoUrl?: string | null; value: File | null; onChange: (file: File | null) => void; onRemove: () => void; onError: (message: string) => void; disabled: boolean }) {
  const source = useDriverPhotoSource(photoUrl);
  return <SquarePhotoEditor existingUrl={photoUrl ? source : null} fallbackUrl="/assets/logokecil.png" value={value} onChange={onChange} onRemove={onRemove} onError={onError} disabled={disabled} outputType="image/jpeg" labels={photoLabels} />;
}

function useDriverPhotoSource(photoUrl?: string | null) {
  const [source, setSource] = useState("/assets/logokecil.png");
  useEffect(() => {
    let objectUrl: string | null = null;
    let active = true;
    if (!photoUrl) {
      setSource("/assets/logokecil.png");
      return () => { active = false; };
    }
    void getDriverPhoto(photoUrl).then((blob) => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setSource(objectUrl);
    }).catch(() => active && setSource("/assets/logokecil.png"));
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photoUrl]);
  return source;
}

function emptyForm(companyId = "", countryCode = ""): FormState { return { companyId, driverId: "", employeeId: "", driverName: "", licenseNumber: "", countryCode, licenseMasterId: "", phoneNumber: "", rfidIbutton: "", status: "ACTIVE" }; }
function requiredValid(form: FormState) { return Boolean(form.companyId && form.driverId.trim() && form.driverName.trim() && form.licenseNumber.trim() && form.countryCode && form.licenseMasterId && form.phoneNumber.trim()); }
function toInput(form: FormState): DriverRegisterInput { return { companyId: form.companyId ? Number(form.companyId) : null, driverId: form.driverId.trim().toUpperCase(), employeeId: form.employeeId.trim().toUpperCase() || null, driverName: form.driverName.trim().toUpperCase(), licenseNumber: form.licenseNumber.trim().toUpperCase(), countryCode: form.countryCode, licenseMasterId: form.licenseMasterId ? Number(form.licenseMasterId) : null, phoneNumber: form.phoneNumber.trim(), rfidIbutton: form.rfidIbutton.trim().toUpperCase() || null, status: form.status }; }
function Field({ label, children, required }: { label: string; children: ReactNode; required?: boolean }) { return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}{required ? <span className="ml-1 text-red-400">*</span> : null}</span>{children}</label>; }
function Notice({ message }: { message: string }) { return <div className="rounded-lg border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-100">{message}</div>; }
