import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, Eye, EyeOff, Save, UserRound } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SquarePhotoEditor } from "@/components/media/square-photo-editor";
import { formatCompanyName, formatDateTime, OrganizationPageHeader, OrganizationTableCard, StatusBadge } from "@/features/organization/components/organization-ui";
import { getUserProfile, updateUserProfile } from "@/features/user/api/user-profile-api";
import { useAuthStore } from "@/stores/auth-store";

const fallbackAvatar = "/assets/logokecil.png";
const allowedPhotoTypes = ["image/jpeg", "image/png"];

function withCacheBust(url?: string | null) {
  if (!url) return null;
  return `${url}${url.includes("?") ? "&" : "?"}v=${Date.now()}`;
}

export function UserProfilePage() {
  const queryClient = useQueryClient();
  const authUser = useAuthStore((state) => state.user);
  const setSession = useAuthStore((state) => state.setSession);
  const token = useAuthStore((state) => state.token);
  const rememberMe = useAuthStore((state) => state.rememberMe);
  const { data: profile, isLoading, isError } = useQuery({ queryKey: ["user", "profile"], queryFn: getUserProfile, refetchInterval: 30_000 });

  const [editing, setEditing] = useState(false);
  const [showStoredPassword, setShowStoredPassword] = useState(false);
  const [changePassword, setChangePassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: updateUserProfile,
    onSuccess: (result) => {
      const updatedProfile = { ...result.profile, profilePhotoUrl: withCacheBust(result.profile.profilePhotoUrl) };
      queryClient.setQueryData(["user", "profile"], updatedProfile);
      if (authUser && token) {
        setSession(token, {
          ...authUser,
          username: updatedProfile.username,
          fullName: updatedProfile.fullName || updatedProfile.username,
          email: updatedProfile.email,
          profilePhotoUrl: updatedProfile.profilePhotoUrl
        }, rememberMe);
      }
      setEditing(false);
      clearPreparedPhoto();
      setNewPassword("");
      setChangePassword(false);
      setNotice("Profile berhasil diperbarui.");
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Update profile gagal.")
  });

  function startEdit() {
    if (!profile) return;
    setUsername(profile.username || "");
    setEmail(profile.email || "");
    setNewPassword("");
    setChangePassword(false);
    clearPreparedPhoto();
    setNotice(null);
    setEditing(true);
  }

  function clearPreparedPhoto() {
    setPhoto(null);
  }

  function cancelEdit() {
    clearPreparedPhoto();
    setNewPassword("");
    setChangePassword(false);
    setEditing(false);
    setNotice(null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!username.trim() || !email.trim()) return setNotice("Username dan email wajib diisi.");
    if (photo && !allowedPhotoTypes.includes(photo.type)) return setNotice("Foto profile harus JPG atau PNG.");
    if (photo && photo.size > 2_000_000) return setNotice("Ukuran foto maksimum 2 MB setelah crop.");
    if (changePassword && newPassword && newPassword.length < 8) return setNotice("Password baru minimum 8 karakter.");
    const trimmedUsername = username.trim();
    const trimmedEmail = email.trim();
    const payload = {
      username: trimmedUsername !== (profile?.username || "") ? trimmedUsername : undefined,
      email: trimmedEmail.toLowerCase() !== (profile?.email || "").toLowerCase() ? trimmedEmail : undefined,
      newPassword: changePassword && newPassword ? newPassword : undefined,
      photo: photo || undefined
    };
    mutation.mutate(payload);
  }

  const avatar = useMemo(() => profile?.profilePhotoUrl || authUser?.profilePhotoUrl || fallbackAvatar, [profile?.profilePhotoUrl, authUser?.profilePhotoUrl]);

  return (
    <section className="space-y-5 text-foreground">
      <OrganizationPageHeader icon={<UserRound className="h-5 w-5" />} title="User Profile" />
      <OrganizationTableCard>
        {notice ? <div className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">{notice}</div> : null}
        {isError ? <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">Profile API belum tersedia atau backend belum berjalan.</div> : null}
        {isLoading ? <div className="text-sm text-slate-300">Loading profile...</div> : null}
        {profile && !editing ? (
          <div className="grid gap-4 md:grid-cols-[160px_1fr]">
            <div className="flex flex-col items-center gap-3 rounded-xl border border-white/10 bg-black/20 p-4">
              <img src={avatar} alt="Profile" className="h-24 w-24 rounded-full object-cover" />
              <Button type="button" onClick={startEdit}><Camera className="h-4 w-4" /> Edit Profile</Button>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <Info label="Role"><StatusBadge status={profile.role} /></Info>
              <Info label="Company">{formatCompanyName(profile.companyName)}</Info>
              <Info label="Username">{profile.username}</Info>
              <Info label="User Email">{profile.email}</Info>
              <Info label="Password">
                <div className="flex gap-2">
                  <Input value={showStoredPassword ? "Password tersimpan aman sebagai BCrypt hash dan tidak bisa ditampilkan ulang" : "••••••••••••"} type="text" readOnly />
                  <Button type="button" variant="outline" size="icon" onClick={() => setShowStoredPassword((v) => !v)}>{showStoredPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</Button>
                </div>
              </Info>
              <Info label="Status"><StatusBadge status={profile.status} /></Info>
              <Info label="Created At">{formatDateTime(profile.createdAt)}</Info>
            </div>
          </div>
        ) : null}
        {profile && editing ? (
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submit}>
            <Info label="Role"><StatusBadge status={profile.role} /></Info>
            <Info label="Company">{formatCompanyName(profile.companyName)}</Info>
            <label className="space-y-1.5 text-sm font-semibold text-slate-200"><span>Username</span><Input value={username} onChange={(e) => setUsername(e.target.value)} required /></label>
            <label className="space-y-1.5 text-sm font-semibold text-slate-200"><span>User Email</span><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>

            <div className="md:col-span-2 rounded-xl border border-white/10 bg-black/20 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-white">Ganti Password</p>
                  <p className="text-xs text-slate-400">Tidak wajib diisi. Isi password baru hanya jika ingin mengganti password.</p>
                </div>
                <Button type="button" variant="outline" onClick={() => setChangePassword((value) => !value)}>{changePassword ? "Batal Ganti Password" : "Ganti Password"}</Button>
              </div>
              {changePassword ? (
                <div className="mt-4 max-w-xl">
                  <label className="space-y-1.5 text-sm font-semibold text-slate-200">
                    <span>Password Baru</span>
                    <div className="flex gap-2"><Input value={newPassword} onChange={(e) => setNewPassword(e.target.value)} type={showNewPassword ? "text" : "password"} autoComplete="new-password" placeholder="Minimum 8 karakter" /><Button type="button" variant="outline" size="icon" onClick={() => setShowNewPassword((v) => !v)}>{showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</Button></div>
                  </label>
                </div>
              ) : null}
            </div>

            <div className="md:col-span-2">
              <SquarePhotoEditor
                existingUrl={avatar}
                fallbackUrl={fallbackAvatar}
                value={photo}
                onChange={setPhoto}
                onError={setNotice}
                onPrepared={() => setNotice("Foto 512 x 512 siap disimpan. Klik Save Profile untuk menyimpan ke database.")}
                cameraEnabled={false}
                legacyProfileLayout
                acceptedTypes={allowedPhotoTypes}
                maximumBytes={5_000_000}
                labels={profilePhotoLabels}
              />
            </div>

            <div className="md:col-span-2 flex justify-end gap-3"><Button type="button" variant="outline" onClick={cancelEdit}>Cancel</Button><Button type="submit" disabled={mutation.isPending}><Save className="h-4 w-4" /> Save Profile</Button></div>
          </form>
        ) : null}
      </OrganizationTableCard>

    </section>
  );
}

const profilePhotoLabels = {
  photo: "Foto Profile",
  upload: "Upload Image",
  camera: "Capture Camera",
  replace: "Replace Photo",
  remove: "Remove Photo",
  cropTitle: "Crop Foto Profile",
  cropHelp: "Geser foto di dalam kotak. Area kotak akan disimpan sebagai avatar 512 x 512.",
  save: "Save",
  cancel: "Cancel",
  selectCamera: "Pilih Kamera",
  capture: "Capture",
  cameraError: "Kamera tidak dapat diakses.",
  invalidType: "Foto profile harus JPG, JPEG, PNG, atau WEBP.",
  maximumSize: "Ukuran foto maksimum 5 MB sebelum crop."
};

function Info({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="rounded-lg border border-white/10 bg-black/20 p-3"><p className="text-xs uppercase tracking-[0.12em] text-slate-400">{label}</p><div className="mt-1 font-bold text-white">{children || "-"}</div></div>;
}
