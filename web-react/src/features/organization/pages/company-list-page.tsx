import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Building2, CheckCircle2, ChevronDown, Eye, EyeOff, Pencil, Plus, RotateCcw, Save, Search, ShieldOff, Trash2 } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/auth-store";
import { ALELS_ROOT_COMPANY, formatCompanyName, formatDate, formatDateTime, OrganizationPageHeader, OrganizationTableCard, StatusBadge } from "@/features/organization/components/organization-ui";
import { COMPANY_PLANS, COMPANY_TYPES, COUNTRIES, STORAGE_UNITS } from "@/constants/organization-options";
import { checkCompanyName, companyAction, createCompany, getCompanies, getParentCompanyOptions, updateCompany, type Company, type CompanyRegisterInput, type CompanyRegisterResponse } from "@/features/organization/api/organization-api";

type FormMode = "LIST" | "FORM" | "REVIEW" | "EDIT";
type EditForm = { id: number; companyName: string; parentCompanyId?: number | null; parentCompanyName: string };

type RegistrationForm = {
  adminRole: string;
  parentCompanyId?: number | null;
  parentCompanyName: string;
  companyName: string;
  companyType: string;
  plan: string;
  monthPacket: string;
  storageValue: string;
  storageUnit: "MB" | "GB" | "TB";
  country: string;
  username: string;
  adminEmail: string;
  temporaryPassword: string;
};

export function CompanyListPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const currentRole = normalizeRole(user?.role);
  const currentCompanyName = formatCompanyName(user?.companyName || ALELS_ROOT_COMPANY);
  const currentCompanyId = user?.companyId ?? null;
  const canOpenCompanyList = ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER"].includes(currentRole);
  const canChooseParent = ["SUPERADMIN", "ADMIN"].includes(currentRole);

  const companiesQuery = useQuery({ queryKey: ["organization", "companies"], queryFn: getCompanies, refetchInterval: 30_000 });
  const parentOptionsQuery = useQuery({ queryKey: ["organization", "parent-options"], queryFn: getParentCompanyOptions, enabled: canChooseParent, refetchInterval: 30_000 });
  const data = companiesQuery.data || [];
  const rootCompany = data.find((row) => row.companyCode === "ALELS_TECH_INDONESIA");
  const rootCompanyId = rootCompany?.id ?? currentCompanyId ?? null;

  const [mode, setMode] = useState<FormMode>("LIST");
  const [showPassword, setShowPassword] = useState(false);
  const [passwordGenerated, setPasswordGenerated] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [savedResult, setSavedResult] = useState<CompanyRegisterResponse | null>(null);
  const [form, setForm] = useState<RegistrationForm>(() => createInitialForm({ currentRole, currentCompanyId, currentCompanyName, rootCompanyId }));
  const [editForm, setEditForm] = useState<EditForm | null>(null);
  const [companyNameStatus, setCompanyNameStatus] = useState<"IDLE" | "CHECKING" | "AVAILABLE" | "DUPLICATE">("IDLE");

  const allowedRoles = useMemo(() => allowedCreatableRoles(currentRole), [currentRole]);
  const parentOptions = useMemo(() => parentOptionsQuery.data || [], [parentOptionsQuery.data]);

  const columns = useMemo<DataTableColumn<Company>[]>(() => [
    { key: "parentCompanyName", label: "Parent Company", value: parentCompany, render: (row) => <span>{parentCompany(row)}</span> },
    { key: "companyName", label: "Company", value: (row) => formatCompanyName(row.companyName), render: (row) => <span className="font-extrabold text-white">{formatCompanyName(row.companyName)}</span> },
    { key: "companyType", label: "Type", value: displayCompanyType, render: (row) => <StatusBadge status={displayCompanyType(row)} /> },
    { key: "plan", label: "Plan", value: displayPlan, render: (row) => <span>{displayPlan(row)}</span> },
    { key: "monthPacket", label: "Month", value: (row) => monthDisplay(row), render: (row) => <span>{monthDisplay(row)}</span> },
    { key: "storageQuotaMb", label: "Storage", value: (row) => storageDisplay(row.storageQuotaMb ?? row.storageSize), render: (row) => <span>{storageDisplay(row.storageQuotaMb ?? row.storageSize)}</span> },
    { key: "storageUsedMb", label: "Storage Used", value: (row) => storageAutoUnit(row.storageUsedMb), render: (row) => <span>{storageAutoUnit(row.storageUsedMb)}</span> },
    { key: "country", label: "Country", value: (row) => row.country || "-", render: (row) => <span>{row.country || "-"}</span> },
    { key: "createdAt", label: "Created at", value: (row) => formatDateTime(row.createdAt), render: (row) => <span>{formatDateTime(row.createdAt)}</span> },
    { key: "createdByName", label: "Created by", value: (row) => row.createdByName || row.createdByEmail || "-", render: (row) => <span>{row.createdByName || row.createdByEmail || "-"}</span> },
    { key: "status", label: "Status", value: (row) => row.status || "-", render: (row) => <StatusBadge status={row.status} /> },
    { key: "startedAt", label: "Started at", value: (row) => formatDate(row.startedAt || row.startAt), render: (row) => <span>{formatDate(row.startedAt || row.startAt)}</span> },
    { key: "expiredAt", label: "Expired at", value: (row) => formatDate(row.expiredAt), render: (row) => <span>{formatDate(row.expiredAt)}</span> }
  ], []);

  const createMutation = useMutation({
    mutationFn: (input: CompanyRegisterInput) => createCompany(input),
    onSuccess: (result) => {
      setSavedResult(result);
      queryClient.invalidateQueries({ queryKey: ["organization"] });
      openAccountPdf(result, form);
      resetToList();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Company registration failed.")
  });

  const actionMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: "delete" | "suspend" | "activate" }) => companyAction(id, action),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization"] }),
    onError: (error) => setNotice(error instanceof Error ? error.message : "Action failed.")
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: { companyName: string; parentCompanyId?: number | null } }) => updateCompany(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization"] }),
    onError: (error) => setNotice(error instanceof Error ? error.message : "Update failed.")
  });

  function startCreate() {
    const nextForm = createInitialForm({ currentRole, currentCompanyId, currentCompanyName, rootCompanyId });
    setForm(nextForm);
    setCompanyNameStatus("IDLE");
    setPasswordGenerated(false);
    setShowPassword(false);
    setNotice(null);
    setSavedResult(null);
    setMode("FORM");
  }

  function resetToList() {
    setMode("LIST");
    setNotice(null);
    setCompanyNameStatus("IDLE");
    setPasswordGenerated(false);
    setShowPassword(false);
  }

  function applyRole(nextRole: string) {
    setForm((current) => {
      const next = { ...current, adminRole: nextRole };
      if (currentRole === "SUPERADMIN" && nextRole === "ADMIN") {
        return { ...next, parentCompanyId: rootCompanyId, parentCompanyName: ALELS_ROOT_COMPANY, companyName: ALELS_ROOT_COMPANY, companyType: "ALELS", plan: "ALELS", monthPacket: "ALELS", storageValue: "0", storageUnit: "MB" };
      }
      return { ...next, parentCompanyId: canChooseParent ? current.parentCompanyId : currentCompanyId, parentCompanyName: canChooseParent ? current.parentCompanyName : currentCompanyName, companyName: current.companyName === ALELS_ROOT_COMPANY ? "" : current.companyName, companyType: current.companyType === "ALELS" ? "Rent" : current.companyType, plan: current.plan === "ALELS" ? "Sample" : current.plan, monthPacket: current.monthPacket === "ALELS" ? "1" : current.monthPacket, storageValue: current.storageValue === "0" ? "100" : current.storageValue };
    });
    setCompanyNameStatus("IDLE");
  }

  function update<K extends keyof RegistrationForm>(key: K, value: RegistrationForm[K]) {
    setForm((current) => {
      const next = { ...current, [key]: key === "companyName" ? String(value).toUpperCase() as RegistrationForm[K] : value };
      if (key === "plan" && String(value).toUpperCase() === "SAMPLE") return { ...next, monthPacket: "1", storageValue: "100", storageUnit: "MB" };
      return next;
    });
    if (key === "companyName") setCompanyNameStatus("IDLE");
  }

  async function checkName() {
    const companyName = form.companyName.trim();
    if (!companyName || isAdminSpecialCase(currentRole, form.adminRole)) return;
    setCompanyNameStatus("CHECKING");
    try {
      const result = await checkCompanyName(companyName);
      setCompanyNameStatus(result.exists ? "DUPLICATE" : "AVAILABLE");
      setNotice(result.exists ? "Nama company sudah ada di sistem global. Gunakan nama lain." : null);
    } catch {
      setCompanyNameStatus("IDLE");
      setNotice("Gagal cek nama company. Pastikan backend berjalan.");
    }
  }

  function generatePassword() {
    if (passwordGenerated) {
      setNotice("Password hanya boleh digenerate satu kali untuk satu proses registration.");
      return;
    }
    update("temporaryPassword", makePassword());
    setPasswordGenerated(true);
    setShowPassword(true);
    setNotice(null);
  }

  function submitRegistration(event: FormEvent) {
    event.preventDefault();
    const validation = validateForm(form, currentRole, allowedRoles, companyNameStatus);
    if (validation) return setNotice(validation);
    setNotice(null);
    setMode("REVIEW");
  }

  function saveRegistration() { createMutation.mutate(toPayload(form, currentRole)); }

  function editCompany(row: Company) {
    setEditForm({ id: row.id, companyName: formatCompanyName(row.companyName), parentCompanyId: row.parentCompanyId ?? rootCompanyId, parentCompanyName: parentCompany(row) });
    setCompanyNameStatus("IDLE");
    setNotice(null);
    setMode("EDIT");
  }

  async function checkEditName() {
    if (!editForm?.companyName.trim()) return;
    setCompanyNameStatus("CHECKING");
    try {
      const result = await checkCompanyName(editForm.companyName, editForm.id);
      setCompanyNameStatus(result.exists ? "DUPLICATE" : "AVAILABLE");
      setNotice(result.exists ? "Nama company sudah ada di sistem global. Gunakan nama lain." : null);
    } catch {
      setCompanyNameStatus("IDLE");
      setNotice("Gagal cek nama company. Pastikan backend berjalan.");
    }
  }

  function submitEdit(event: FormEvent) {
    event.preventDefault();
    if (!editForm) return;
    if (!editForm.companyName.trim()) return setNotice("Nama company wajib diisi.");
    if (companyNameStatus === "DUPLICATE") return setNotice("Nama company sudah ada di sistem global.");
    updateMutation.mutate({ id: editForm.id, input: { companyName: editForm.companyName.trim().toUpperCase(), parentCompanyId: editForm.parentCompanyId ?? null } }, { onSuccess: () => resetToList() });
  }

  if (!canOpenCompanyList) {
    return <section className="space-y-5 text-foreground"><OrganizationPageHeader icon={<Building2 className="h-5 w-5" />} title="Company List" /><OrganizationTableCard><Notice message="Role TECHUSER dan CLIENTUSER tidak memiliki akses ke menu Organization." /></OrganizationTableCard></section>;
  }

  if (mode === "FORM") {
    const adminSpecial = isAdminSpecialCase(currentRole, form.adminRole);
    const samplePlan = form.plan.toUpperCase() === "SAMPLE";
    return (
      <section className="space-y-5 text-foreground">
        <OrganizationPageHeader icon={<Building2 className="h-5 w-5" />} title="Company Registration" />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submitRegistration}>
            <Field label="Role"><Select value={form.adminRole} onChange={applyRole} options={allowedRoles} /></Field>
            <Field label="Parent Company">{canChooseParent && !adminSpecial ? <SearchablePicker value={form.parentCompanyName} options={parentOptions} placeholder="Search parent company..." onSelect={(item) => setForm((current) => ({ ...current, parentCompanyId: item.id, parentCompanyName: item.label }))} /> : <Input value={form.parentCompanyName} disabled />}</Field>
            <Field label="Company"><div className="flex gap-2"><Input value={form.companyName} onChange={(e) => update("companyName", e.target.value)} onBlur={checkName} placeholder="COMPANY NAME" disabled={adminSpecial} required /><Button type="button" variant="outline" size="icon" onClick={checkName} disabled={adminSpecial}><Search className="h-4 w-4" /></Button></div><CompanyNameStatus status={companyNameStatus} /></Field>
            <Field label="Type">{adminSpecial ? <Input value="ALELS" disabled /> : <SearchablePicker value={form.companyType} options={COMPANY_TYPES.map((label) => ({ label }))} placeholder="Search type..." onSelect={(item) => update("companyType", item.label)} />}</Field>
            <Field label="Plan"><Select value={form.plan} onChange={(v) => update("plan", v)} disabled={adminSpecial} options={adminSpecial ? ["ALELS"] : [...COMPANY_PLANS]} /></Field>
            <Field label="Month"><Input type="number" min={1} value={form.monthPacket} onChange={(e) => update("monthPacket", e.target.value)} disabled={adminSpecial || samplePlan} /></Field>
            <Field label="Storage"><div className="flex gap-2"><Input type="number" min={0} value={form.storageValue} onChange={(e) => update("storageValue", e.target.value)} disabled={adminSpecial || samplePlan} /><Select value={form.storageUnit} onChange={(v) => update("storageUnit", v as RegistrationForm["storageUnit"])} disabled={adminSpecial || samplePlan} options={[...STORAGE_UNITS]} /></div></Field>
            <Field label="Country"><CountryPicker value={form.country} onChange={(value) => update("country", value)} /></Field>
            <Field label="Username"><Input value={form.username} onChange={(e) => update("username", e.target.value)} required /></Field>
            <Field label="User Email"><Input type="email" value={form.adminEmail} onChange={(e) => update("adminEmail", e.target.value)} required /></Field>
            <Field label="Password"><div className="flex gap-2"><Input value={form.temporaryPassword} type={showPassword ? "text" : "password"} readOnly required /><Button type="button" variant="outline" onClick={generatePassword}>Generate</Button><Button type="button" variant="outline" size="icon" onClick={() => setShowPassword((current) => !current)}>{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</Button></div></Field>
            <div className="md:col-span-2 flex justify-end gap-3 pt-2"><Button type="button" variant="outline" onClick={resetToList}>Cancel</Button><Button type="submit"><Plus className="h-4 w-4" /> Created</Button></div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }


  if (mode === "EDIT" && editForm) {
    return (
      <section className="space-y-5 text-foreground">
        <OrganizationPageHeader icon={<Building2 className="h-5 w-5" />} title="Edit Company" />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submitEdit}>
            <Field label="Company"><div className="flex gap-2"><Input value={editForm.companyName} onChange={(e) => { setEditForm({ ...editForm, companyName: e.target.value.toUpperCase() }); setCompanyNameStatus("IDLE"); }} onBlur={checkEditName} required /><Button type="button" variant="outline" size="icon" onClick={checkEditName}><Search className="h-4 w-4" /></Button></div><CompanyNameStatus status={companyNameStatus} /></Field>
            <Field label="Parent Company"><SearchablePicker value={editForm.parentCompanyName} options={parentOptions} placeholder="Search parent company..." onSelect={(item) => setEditForm({ ...editForm, parentCompanyId: item.id, parentCompanyName: item.label })} /></Field>
            <div className="md:col-span-2 flex justify-end gap-3 pt-2"><Button type="button" variant="outline" onClick={resetToList}>Cancel</Button><Button type="submit" disabled={updateMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  if (mode === "REVIEW") {
    return <section className="space-y-5 text-foreground"><OrganizationPageHeader icon={<Building2 className="h-5 w-5" />} title="Company Registration Review" /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<div className="grid gap-3 md:grid-cols-2">{reviewRows(form).map((item) => <div key={item.label} className="rounded-lg border border-white/10 bg-black/20 p-3"><p className="text-xs uppercase tracking-[0.12em] text-slate-400">{item.label}</p><p className="mt-1 font-bold text-white">{item.value || "-"}</p></div>)}</div><div className="mt-5 flex justify-end gap-3"><Button type="button" variant="outline" onClick={() => setMode("FORM")}><ArrowLeft className="h-4 w-4" /> Back</Button><Button type="button" onClick={saveRegistration} disabled={createMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div></OrganizationTableCard></section>;
  }

  return (
    <section className="space-y-5 text-foreground">
      <OrganizationPageHeader icon={<Building2 className="h-5 w-5" />} title="Company List" />
      <OrganizationTableCard>
        <div className="mb-4 flex justify-end"><Button type="button" onClick={startCreate}><Plus className="h-4 w-4" /> Created New</Button></div>
        {notice ? <Notice message={notice} /> : null}
        {companiesQuery.isError ? <Notice message={companiesQuery.error instanceof Error ? companiesQuery.error.message : "Company API belum tersedia atau backend belum berjalan."} /> : null}
        {savedResult ? <div className="mb-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-100">Company berhasil disimpan. PDF account information sudah dibuka.</div> : null}
        <DataTable
          data={data}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={companiesQuery.isLoading ? "Loading companies..." : "No company found."}
          actions={(row) => canShowCompanyActions(row, currentCompanyId) ? (
            <div className="flex items-center gap-2">
              <Button type="button" size="sm" variant="outline" title="Edit company" onClick={() => editCompany(row)}>
                <Pencil className="h-4 w-4" />
              </Button>
              <Button type="button" size="sm" variant="outline" title="Delete" onClick={() => confirm("Move company to Wasted?") && actionMutation.mutate({ id: row.id, action: "delete" })}>
                <Trash2 className="h-4 w-4" />
              </Button>
              <Button type="button" size="sm" variant="outline" title="Suspend" className={actionButtonClass(row.status, "suspend")} onClick={() => actionMutation.mutate({ id: row.id, action: "suspend" })}>
                <ShieldOff className="h-4 w-4" />
              </Button>
              <Button type="button" size="sm" variant="outline" title="Activate" className={actionButtonClass(row.status, "activate")} onClick={() => actionMutation.mutate({ id: row.id, action: "activate" })}>
                <CheckCircle2 className="h-4 w-4" />
              </Button>
            </div>
          ) : <span className="text-xs text-slate-500">Protected</span>}
        />
      </OrganizationTableCard>
    </section>
  );
}

function canShowCompanyActions(row: Company, currentCompanyId: number | null) {
  if (row.id === currentCompanyId) return false;
  if (row.companyCode === "ALELS_TECH_INDONESIA") return false;
  return true;
}
function parentCompany(row: Company) { if (row.companyCode === "ALELS_TECH_INDONESIA") return ALELS_ROOT_COMPANY; return formatCompanyName(row.parentCompanyName || ALELS_ROOT_COMPANY); }
function displayCompanyType(row: Company) { if (row.companyCode === "ALELS_TECH_INDONESIA" || row.isInternal) return "ALELS"; return row.companyType || row.type || "-"; }
function displayPlan(row: Company) { if (row.companyCode === "ALELS_TECH_INDONESIA" || row.isInternal) return "ALELS"; return row.plan || row.subscriptionStatus || "-"; }
function monthDisplay(row: Company) {
  if (!row.monthPacket) return "-";
  if (!row.startedAt) return `${row.monthPacket}M/-`;
  return `${row.monthPacket}M/${elapsedSinceStart(row.startedAt)}`;
}
function elapsedSinceStart(value: string) {
  const start = new Date(value);
  if (Number.isNaN(start.getTime())) return "-";
  const now = new Date();
  const diffMs = Math.max(0, now.getTime() - start.getTime());
  const diffDays = Math.max(1, Math.floor(diffMs / 86_400_000) + 1);
  if (diffDays < 31) return `${diffDays}D`;
  const monthDiff = Math.max(1, (now.getFullYear() - start.getFullYear()) * 12 + now.getMonth() - start.getMonth() + 1);
  return `${monthDiff}M`;
}
function storageDisplay(value?: number | null) { if (value === null || value === undefined) return "-"; return storageAutoUnit(value); }
function storageAutoUnit(value?: number | null) {
  if (value === null || value === undefined) return "-";
  const mb = Number(value);
  if (!Number.isFinite(mb)) return "-";
  if (mb >= 1_000_000) return `${trimNumber(mb / 1_000_000)} TB`;
  if (mb >= 1_000) return `${trimNumber(mb / 1_000)} GB`;
  return `${Math.max(0, Math.round(mb)).toLocaleString("en-US")} MB`;
}
function trimNumber(value: number) {
  return value.toLocaleString("en-US", { maximumFractionDigits: value >= 100 ? 0 : 2 });
}
function actionButtonClass(status: string | null | undefined, action: "activate" | "suspend") {
  const normalized = (status || "").toUpperCase();
  if (action === "activate" && normalized === "ACTIVE") return "border-sky-400/60 bg-sky-500/15 text-sky-200 hover:bg-sky-500/25";
  if (action === "suspend" && normalized === "SUSPENDED") return "border-red-400/60 bg-red-500/15 text-red-200 hover:bg-red-500/25";
  return "";
}
function normalizeRole(role?: string | null) { return (role || "").toUpperCase().replace(/[\s_-]+/g, "").replace("TECHUSER", "TECHUSER").replace("CLIENTUSER", "CLIENTUSER"); }
function allowedCreatableRoles(role: string) { if (role === "SUPERADMIN") return ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "ADMIN") return ["OWNER", "MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "OWNER") return ["MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "MANAGER") return ["TECHUSER", "CLIENTUSER"]; return []; }
function isAdminSpecialCase(currentRole: string, targetRole: string) { return currentRole === "SUPERADMIN" && targetRole === "ADMIN"; }
function createInitialForm(input: { currentRole: string; currentCompanyId?: number | null; currentCompanyName: string; rootCompanyId?: number | null }): RegistrationForm { const ownParent = ["OWNER", "MANAGER"].includes(input.currentRole); return { adminRole: allowedCreatableRoles(input.currentRole)[0] || "CLIENTUSER", parentCompanyId: ownParent ? input.currentCompanyId : input.rootCompanyId ?? null, parentCompanyName: ownParent ? input.currentCompanyName : ALELS_ROOT_COMPANY, companyName: "", companyType: "Rent", plan: "Sample", monthPacket: "1", storageValue: "100", storageUnit: "MB", country: "Indonesia", username: "", adminEmail: "", temporaryPassword: "" }; }
function validateForm(form: RegistrationForm, currentRole: string, allowedRoles: string[], companyNameStatus: string) { if (!allowedRoles.includes(form.adminRole)) return "Role yang dipilih tidak diizinkan."; if (!form.username.trim()) return "Username wajib diisi."; if (!form.adminEmail.trim()) return "User email wajib diisi."; if (!form.temporaryPassword.trim()) return "Password wajib digenerate."; if (isAdminSpecialCase(currentRole, form.adminRole)) return null; if (!form.companyName.trim()) return "Nama company wajib diisi."; if (companyNameStatus === "DUPLICATE") return "Nama company sudah ada di sistem global."; if (!form.country.trim()) return "Country wajib dipilih."; return null; }
function toPayload(form: RegistrationForm, currentRole: string): CompanyRegisterInput { const adminSpecial = isAdminSpecialCase(currentRole, form.adminRole); return { parentCompanyId: form.parentCompanyId ?? undefined, companyName: adminSpecial ? ALELS_ROOT_COMPANY : form.companyName.trim().toUpperCase(), companyType: adminSpecial ? "ALELS" : form.companyType, type: adminSpecial ? "ALELS" : form.companyType, plan: adminSpecial ? "ALELS" : form.plan, monthPacket: adminSpecial ? undefined : Number(form.monthPacket), storageSize: adminSpecial ? 0 : toMegabytes(Number(form.storageValue), form.storageUnit), storageUnit: "MB", country: form.country, adminFullName: form.username.trim(), username: form.username.trim(), adminEmail: form.adminEmail.trim(), userEmail: form.adminEmail.trim(), adminRole: form.adminRole, temporaryPassword: form.temporaryPassword }; }
function toMegabytes(value: number, unit: string) { if (unit === "TB") return Math.round(value * 1_000_000); if (unit === "GB") return Math.round(value * 1_000); return Math.round(value); }
function makePassword() { const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%"; return `Al${Array.from({ length: 14 }, () => chars[Math.floor(Math.random() * chars.length)]).join("")}1!`; }
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="space-y-1.5 text-sm font-semibold text-slate-200"><span>{label}</span>{children}</label>; }
function Select({ value, onChange, options, disabled }: { value: string; onChange: (value: string) => void; options: readonly string[]; disabled?: boolean }) { return <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)} className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50">{options.map((option) => <option key={option} value={option}>{option}</option>)}</select>; }
function SearchablePicker({ value, options, placeholder, onSelect }: { value: string; options: Array<{ id?: number; label: string }>; placeholder: string; onSelect: (item: { id?: number; label: string }) => void }) { const [open, setOpen] = useState(false); const [keyword, setKeyword] = useState(""); const filtered = options.filter((item) => item.label.toLowerCase().includes(keyword.toLowerCase())); return <div className="relative space-y-2"><div className="flex gap-2"><Input value={value} readOnly /><Button type="button" variant="outline" size="icon" onClick={() => setOpen((current) => !current)}><ChevronDown className="h-4 w-4" /></Button></div>{open ? <div className="rounded-lg border border-white/10 bg-[#0b1220] p-2 shadow-xl"><Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder={placeholder} /><div className="mt-2 max-h-48 overflow-auto rounded-md border border-white/10">{filtered.length ? filtered.map((item) => <button key={`${item.id ?? item.label}`} type="button" className="block w-full px-3 py-2 text-left text-sm hover:bg-white/10" onClick={() => { onSelect(item); setOpen(false); setKeyword(""); }}>{item.label}</button>) : <div className="px-3 py-2 text-sm text-slate-400">No data found.</div>}</div></div> : null}</div>; }
function CountryPicker({ value, onChange }: { value: string; onChange: (value: string) => void }) { return <SearchablePicker value={value} options={COUNTRIES.map((label) => ({ label }))} placeholder="Search country..." onSelect={(item) => onChange(item.label)} />; }
function CompanyNameStatus({ status }: { status: "IDLE" | "CHECKING" | "AVAILABLE" | "DUPLICATE" }) { if (status === "CHECKING") return <p className="text-xs text-slate-400">Checking company name...</p>; if (status === "AVAILABLE") return <p className="text-xs text-emerald-300">Company name available.</p>; if (status === "DUPLICATE") return <p className="text-xs text-red-300">Company already exists.</p>; return null; }
function Notice({ message }: { message: string }) { return <div className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">{message}</div>; }
function reviewRows(form: RegistrationForm) { return [{ label: "Role", value: form.adminRole }, { label: "Parent Company", value: form.parentCompanyName }, { label: "Company", value: form.companyName }, { label: "Type", value: form.companyType }, { label: "Plan", value: form.plan }, { label: "Month", value: form.monthPacket }, { label: "Storage", value: `${form.storageValue} ${form.storageUnit} (${toMegabytes(Number(form.storageValue || 0), form.storageUnit)} MB)` }, { label: "Country", value: form.country }, { label: "Username", value: form.username }, { label: "User Email", value: form.adminEmail }, { label: "Password", value: form.temporaryPassword }]; }
function openAccountPdf(result: CompanyRegisterResponse, form: RegistrationForm) { const printable = window.open("", "_blank", "width=900,height=1100"); if (!printable) return; const companyName = formatCompanyName(result.companyName || form.companyName); const password = result.generatedPassword || form.temporaryPassword; printable.document.write(`<!doctype html><html><head><title>Your Info Account - ${companyName}</title><style>body{font-family:Arial,sans-serif;color:#111827;margin:0;padding:40px}.letter{max-width:760px;margin:0 auto;border:1px solid #d1d5db;padding:34px}.kop{display:flex;align-items:center;gap:16px;border-bottom:3px solid #0284c7;padding-bottom:18px}.kop img{width:54px;height:54px;object-fit:contain}h1{margin:0;font-size:22px;letter-spacing:.04em}h2{margin:6px 0 0;font-size:15px;color:#0369a1}.section{margin-top:28px}.grid{display:grid;grid-template-columns:190px 1fr;border:1px solid #e5e7eb}.grid div{padding:10px 12px;border-bottom:1px solid #e5e7eb}.grid div:nth-child(odd){background:#f8fafc;font-weight:bold}.footer{margin-top:30px;font-size:12px;color:#4b5563}@media print{body{padding:0}.letter{border:none}}</style></head><body><div class="letter"><div class="kop"><img src="/assets/logo-card.png" alt="ALELS"/><div><h1>ALELS TECH INDONESIA</h1><h2>Your Info Account</h2></div></div><div class="section"><p>Dear User,</p><p>Your ALELS platform account has been created. Please keep this information secure.</p><div class="grid"><div>Parent Company</div><div>${escapeHtml(form.parentCompanyName)}</div><div>Company</div><div>${escapeHtml(companyName)}</div><div>Role</div><div>${escapeHtml(result.adminRole || form.adminRole)}</div><div>Username</div><div>${escapeHtml(form.username)}</div><div>Email</div><div>${escapeHtml(result.adminEmail || form.adminEmail)}</div><div>Password</div><div><strong>${escapeHtml(password)}</strong></div></div></div><div class="footer">Generated by ALELS Platform. Confidential.</div></div><script>window.onload=()=>{window.print();};</script></body></html>`); printable.document.close(); }
function escapeHtml(value?: string | null) { return String(value || "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char] || char)); }
