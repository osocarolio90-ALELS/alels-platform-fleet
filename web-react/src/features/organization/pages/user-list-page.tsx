import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, Eye, EyeOff, KeyRound, Pencil, Plus, Save, ShieldOff, Trash2, UsersRound } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { createUser, getCompanyOptions, getUsers, updateUser, userAction, type OptionRow, type UpdateUserResponse, type User } from "@/features/organization/api/organization-api";
import { formatCompanyName, formatDateTime, OrganizationPageHeader, OrganizationTableCard, StatusBadge } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";

type Mode = "LIST" | "CREATE" | "EDIT";
type UserForm = { id?: number; companyId?: number | null; companyName: string; username: string; email: string; role: string; temporaryPassword: string };

export function UserListPage() {
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const currentRole = normalizeRole(currentUser?.role);
  const allowedRoles = allowedCreatableRoles(currentRole);
  const canOpen = ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER"].includes(currentRole);
  const [mode, setMode] = useState<Mode>("LIST");
  const [notice, setNotice] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [form, setForm] = useState<UserForm>(() => initialForm(currentUser, allowedRoles));
  const { data = [], isLoading, isError } = useQuery({ queryKey: ["organization", "users"], queryFn: getUsers, refetchInterval: 30_000, enabled: canOpen });
  const { data: companyOptions = [] } = useQuery({ queryKey: ["organization", "company-options"], queryFn: getCompanyOptions, refetchInterval: 30_000, enabled: canOpen });

  const actionMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: "delete" | "suspend" | "activate" }) => userAction(id, action),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization"] }),
    onError: (error) => setNotice(error instanceof Error ? error.message : "Action failed.")
  });
  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["organization", "users"] }); reset(); },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Create user failed.")
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: { username: string; fullName: string; email: string; temporaryPassword?: string } }) => updateUser(id, input),
    onSuccess: (response) => {
      if (response.generatedPassword) openPasswordPdf(response, form);
      queryClient.invalidateQueries({ queryKey: ["organization", "users"] });
      reset();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Update failed.")
  });

  const columns = useMemo<DataTableColumn<User>[]>(() => [
    { key: "companyName", label: "Company", value: (row) => formatCompanyName(row.companyName), render: (row) => <span>{formatCompanyName(row.companyName)}</span> },
    { key: "fullName", label: "Full Name", value: (row) => row.fullName || row.username || "-", render: (row) => <span className="font-extrabold text-white">{row.fullName || row.username}</span> },
    { key: "email", label: "Email", value: (row) => row.email || "-" },
    { key: "role", label: "Role", value: (row) => row.role || "-", render: (row) => <StatusBadge status={row.role} /> },
    { key: "createdAt", label: "Created at", value: (row) => formatDateTime(row.createdAt), render: (row) => <span>{formatDateTime(row.createdAt)}</span> },
    { key: "createdByName", label: "Created by", value: (row) => row.createdByName || row.createdByEmail || "-", render: (row) => <span>{row.createdByName || row.createdByEmail || "-"}</span> },
    { key: "status", label: "Status", value: (row) => row.status || "-", render: (row) => <StatusBadge status={row.status} /> }
  ], []);

  function reset() { setMode("LIST"); setNotice(null); setShowPassword(false); setForm(initialForm(currentUser, allowedRoles)); }
  function startCreate() { setForm(initialForm(currentUser, allowedRoles)); setShowPassword(false); setNotice(null); setMode("CREATE"); }
  function startEdit(row: User) { setForm({ id: row.id, companyId: row.companyId, companyName: formatCompanyName(row.companyName), username: row.username || row.fullName || "", email: row.email || "", role: row.role || "", temporaryPassword: "" }); setShowPassword(false); setNotice(null); setMode("EDIT"); }
  function update<K extends keyof UserForm>(key: K, value: UserForm[K]) { setForm((current) => ({ ...current, [key]: value })); }
  function generatePassword() { update("temporaryPassword", makePassword()); setShowPassword(true); }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.username.trim() || !form.email.trim()) return setNotice("Username dan email wajib diisi.");
    if (mode === "CREATE") {
      if (!allowedRoles.includes(form.role)) return setNotice("Role tidak diizinkan untuk user login saat ini.");
      if (!form.temporaryPassword.trim()) return setNotice("Password wajib digenerate.");
      createMutation.mutate({ companyId: form.companyId, username: form.username.trim(), fullName: form.username.trim(), email: form.email.trim(), role: form.role, temporaryPassword: form.temporaryPassword });
      return;
    }
    if (mode === "EDIT" && form.id) {
      updateMutation.mutate({ id: form.id, input: { username: form.username.trim(), fullName: form.username.trim(), email: form.email.trim(), temporaryPassword: form.temporaryPassword || undefined } });
    }
  }

  if (!canOpen) return <section className="space-y-5 text-foreground"><OrganizationPageHeader icon={<UsersRound className="h-5 w-5" />} title="User List" /><OrganizationTableCard><Notice message="Role TECHUSER dan CLIENTUSER tidak memiliki akses ke menu User List." /></OrganizationTableCard></section>;

  if (mode !== "LIST") {
    return <section className="space-y-5 text-foreground"><OrganizationPageHeader icon={<UsersRound className="h-5 w-5" />} title={mode === "CREATE" ? "Create New User" : "Edit User"} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<form className="grid gap-4 md:grid-cols-2" onSubmit={submit}><Field label="Company">{mode === "CREATE" ? <CompanyPicker value={form.companyName} options={companyOptions} onSelect={(item) => setForm((current) => ({ ...current, companyId: item.id, companyName: item.label }))} /> : <Input value={form.companyName} disabled />}</Field><Field label="Role">{mode === "CREATE" ? <Select value={form.role} options={allowedRoles} onChange={(value) => update("role", value)} /> : <Input value={form.role} disabled />}</Field><Field label="Username"><Input value={form.username} onChange={(e) => update("username", e.target.value)} required /></Field><Field label="User Email"><Input type="email" value={form.email} onChange={(e) => update("email", e.target.value)} required /></Field><Field label="Password">{mode === "EDIT" ? <p className="mb-2 text-xs text-slate-400">Klik Generate hanya jika ingin mengganti password user.</p> : null}<div className="flex gap-2"><Input value={form.temporaryPassword} type={showPassword ? "text" : "password"} readOnly placeholder={mode === "CREATE" ? "Generate password" : "Kosongkan jika tidak mengganti password"} required={mode === "CREATE"} /><Button type="button" variant="outline" onClick={generatePassword}><KeyRound className="h-4 w-4" /> Generate</Button><Button type="button" variant="outline" size="icon" onClick={() => setShowPassword((v) => !v)}>{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</Button></div></Field><div className="md:col-span-2 flex justify-end gap-3 pt-2"><Button type="button" variant="outline" onClick={reset}><ArrowLeft className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div></form></OrganizationTableCard></section>;
  }

  return (
    <section className="space-y-5 text-foreground">
      <OrganizationPageHeader icon={<UsersRound className="h-5 w-5" />} title="User List" />
      <OrganizationTableCard>
        <div className="mb-4 flex justify-end"><Button type="button" onClick={startCreate}><Plus className="h-4 w-4" /> Created New</Button></div>
        {notice ? <Notice message={notice} /> : null}
        {isError ? <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">User API belum tersedia atau backend belum berjalan.</div> : null}
        <DataTable data={data} columns={columns} rowKey={(row) => row.id} emptyMessage={isLoading ? "Loading users..." : "No user found."} actions={(row) => canShowUserActions(row, currentUser?.id ?? null) ? <div className="flex items-center gap-2"><Button type="button" size="sm" variant="outline" title="Edit user" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /></Button><Button type="button" size="sm" variant="outline" title="Delete" onClick={() => confirm("Move user to Wasted?") && actionMutation.mutate({ id: row.id, action: "delete" })}><Trash2 className="h-4 w-4" /></Button><Button type="button" size="sm" variant="outline" title="Suspend" className={actionButtonClass(row.status, "suspend")} onClick={() => actionMutation.mutate({ id: row.id, action: "suspend" })}><ShieldOff className="h-4 w-4" /></Button><Button type="button" size="sm" variant="outline" title="Activate" className={actionButtonClass(row.status, "activate")} onClick={() => actionMutation.mutate({ id: row.id, action: "activate" })}><CheckCircle2 className="h-4 w-4" /></Button></div> : <span className="text-xs text-slate-500">Protected</span>} />
      </OrganizationTableCard>
    </section>
  );
}

function canShowUserActions(row: User, currentUserId: number | null) {
  if (row.id === currentUserId) return false;
  return normalizeRole(row.role) !== "SUPERADMIN";
}
function initialForm(user: ReturnType<typeof useAuthStore.getState>["user"], roles: string[]): UserForm { return { companyId: user?.companyId ?? null, companyName: formatCompanyName(user?.companyName), username: "", email: "", role: roles[0] || "CLIENTUSER", temporaryPassword: "" }; }
function normalizeRole(role?: string | null) { return (role || "").toUpperCase().replace(/[\s_-]+/g, ""); }
function allowedCreatableRoles(role: string) { if (role === "SUPERADMIN") return ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "ADMIN") return ["OWNER", "MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "OWNER") return ["MANAGER", "TECHUSER", "CLIENTUSER"]; if (role === "MANAGER") return ["TECHUSER", "CLIENTUSER"]; return []; }
function makePassword() { const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#"; const bytes = crypto.getRandomValues(new Uint32Array(14)); return Array.from(bytes, (value) => alphabet[value % alphabet.length]).join(""); }
function actionButtonClass(status: string | null | undefined, action: "activate" | "suspend") { const normalized = (status || "").toUpperCase(); if (action === "activate" && normalized === "ACTIVE") return "border-sky-400/60 bg-sky-500/15 text-sky-200 hover:bg-sky-500/25"; if (action === "suspend" && normalized === "SUSPENDED") return "border-red-400/60 bg-red-500/15 text-red-200 hover:bg-red-500/25"; return ""; }
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}</span>{children}</label>; }
function Select({ value, onChange, options }: { value: string; onChange: (value: string) => void; options: readonly string[] }) { return <select value={value} onChange={(e) => onChange(e.target.value)} className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring">{options.map((option) => <option key={option} value={option}>{option}</option>)}</select>; }
function CompanyPicker({ value, options, onSelect }: { value: string; options: OptionRow[]; onSelect: (item: OptionRow) => void }) { return <select value={value} onChange={(e) => { const selected = options.find((item) => item.label === e.target.value); if (selected) onSelect(selected); }} className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"><option value="">Select company</option>{options.map((item) => <option key={`${item.id}-${item.label}`} value={item.label}>{item.label}</option>)}</select>; }
function Notice({ message }: { message: string }) { return <div className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">{message}</div>; }
function openPasswordPdf(result: UpdateUserResponse, form: UserForm) { const password = result.generatedPassword || form.temporaryPassword; if (!password) return; const printable = window.open("", "_blank", "width=900,height=1100"); if (!printable) return; printable.document.write(`<!doctype html><html><head><title>Your Info Account - ${escapeHtml(form.companyName)}</title><style>body{font-family:Arial,sans-serif;color:#111827;margin:0;padding:40px}.letter{max-width:760px;margin:0 auto;border:1px solid #d1d5db;padding:34px}.kop{display:flex;align-items:center;gap:16px;border-bottom:3px solid #0284c7;padding-bottom:18px}.kop img{width:54px;height:54px;object-fit:contain}h1{margin:0;font-size:22px;letter-spacing:.04em}h2{margin:6px 0 0;font-size:15px;color:#0369a1}.section{margin-top:28px}.grid{display:grid;grid-template-columns:190px 1fr;border:1px solid #e5e7eb}.grid div{padding:10px 12px;border-bottom:1px solid #e5e7eb}.grid div:nth-child(odd){background:#f8fafc;font-weight:bold}.footer{margin-top:30px;font-size:12px;color:#4b5563}@media print{body{padding:0}.letter{border:none}}</style></head><body><div class="letter"><div class="kop"><img src="/assets/logo-card.png" alt="ALELS"/><div><h1>ALELS TECH INDONESIA</h1><h2>Your Info Account</h2></div></div><div class="section"><p>Dear User,</p><p>Your ALELS platform account password has been updated. Please keep this information secure.</p><div class="grid"><div>Company</div><div>${escapeHtml(form.companyName)}</div><div>Username</div><div>${escapeHtml(form.username)}</div><div>Email</div><div>${escapeHtml(form.email)}</div><div>Password Baru</div><div><strong>${escapeHtml(password)}</strong></div></div></div><div class="footer">Generated by ALELS Platform. Confidential.</div></div><script>window.onload=()=>{window.print();};</script></body></html>`); printable.document.close(); }
function escapeHtml(value?: string | null) { return String(value || "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char] || char)); }
