import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, Eye, EyeOff, Save, UserRound, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formatCompanyName, formatDateTime, OrganizationPageHeader, OrganizationTableCard, StatusBadge } from "@/features/organization/components/organization-ui";
import { getUserProfile, updateUserProfile } from "@/features/user/api/user-profile-api";
import { useAuthStore } from "@/stores/auth-store";

const fallbackAvatar = "/assets/logokecil.png";
const allowedPhotoTypes = ["image/jpeg", "image/png"];
const avatarSize = 512;
const cropPreviewSize = 320;

function withCacheBust(url?: string | null) {
  if (!url) return null;
  return `${url}${url.includes("?") ? "&" : "?"}v=${Date.now()}`;
}

type ImageSize = { width: number; height: number };
type DragState = { startX: number; startY: number; imageX: number; imageY: number };

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
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState<string | null>(null);
  const [cropSourceFile, setCropSourceFile] = useState<File | null>(null);
  const [cropSourceUrl, setCropSourceUrl] = useState<string | null>(null);
  const [cropImageSize, setCropImageSize] = useState<ImageSize | null>(null);
  const [cropOffset, setCropOffset] = useState({ x: 0, y: 0 });
  const [dragState, setDragState] = useState<DragState | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const cropFrameRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    return () => {
      if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
      if (cropSourceUrl) URL.revokeObjectURL(cropSourceUrl);
    };
  }, [photoPreviewUrl, cropSourceUrl]);

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
      closeCropModal();
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
    closeCropModal();
    clearPreparedPhoto();
    setNotice(null);
    setEditing(true);
  }

  function clearPreparedPhoto() {
    if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
    setPhotoPreviewUrl(null);
    setPhoto(null);
  }

  function closeCropModal() {
    if (cropSourceUrl) URL.revokeObjectURL(cropSourceUrl);
    setCropSourceUrl(null);
    setCropSourceFile(null);
    setCropImageSize(null);
    setCropOffset({ x: 0, y: 0 });
    setDragState(null);
  }

  function cancelEdit() {
    closeCropModal();
    clearPreparedPhoto();
    setNewPassword("");
    setChangePassword(false);
    setEditing(false);
    setNotice(null);
  }

  function onSelectPhoto(file?: File | null) {
    if (!file) return;
    if (!allowedPhotoTypes.includes(file.type)) {
      setNotice("Foto profile harus JPG atau PNG.");
      return;
    }
    if (file.size > 5_000_000) {
      setNotice("Ukuran foto maksimum 5 MB sebelum crop.");
      return;
    }
    closeCropModal();
    const sourceUrl = URL.createObjectURL(file);
    setCropSourceFile(file);
    setCropSourceUrl(sourceUrl);
    setCropImageSize(null);
    setCropOffset({ x: 0, y: 0 });
    setNotice(null);
  }

  function onCropImageLoad(event: React.SyntheticEvent<HTMLImageElement>) {
    setCropImageSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight });
  }

  function clampOffset(offset: { x: number; y: number }, imageSize = cropImageSize) {
    if (!imageSize) return offset;
    const scale = Math.max(cropPreviewSize / imageSize.width, cropPreviewSize / imageSize.height);
    const drawWidth = imageSize.width * scale;
    const drawHeight = imageSize.height * scale;
    const maxX = Math.max(0, (drawWidth - cropPreviewSize) / 2);
    const maxY = Math.max(0, (drawHeight - cropPreviewSize) / 2);
    return {
      x: Math.max(-maxX, Math.min(maxX, offset.x)),
      y: Math.max(-maxY, Math.min(maxY, offset.y))
    };
  }

  function startDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (!cropSourceUrl) return;
    cropFrameRef.current?.setPointerCapture(event.pointerId);
    setDragState({ startX: event.clientX, startY: event.clientY, imageX: cropOffset.x, imageY: cropOffset.y });
  }

  function dragCrop(event: React.PointerEvent<HTMLDivElement>) {
    if (!dragState) return;
    const next = {
      x: dragState.imageX + event.clientX - dragState.startX,
      y: dragState.imageY + event.clientY - dragState.startY
    };
    setCropOffset(clampOffset(next));
  }

  function stopDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (dragState) cropFrameRef.current?.releasePointerCapture(event.pointerId);
    setDragState(null);
  }

  async function saveCrop() {
    if (!cropSourceFile || !cropSourceUrl || !cropImageSize) return;
    try {
      const image = await loadImage(cropSourceUrl);
      const canvas = document.createElement("canvas");
      canvas.width = avatarSize;
      canvas.height = avatarSize;
      const ctx = canvas.getContext("2d");
      if (!ctx) throw new Error("Canvas browser tidak tersedia.");

      const scale = Math.max(avatarSize / image.width, avatarSize / image.height);
      const drawWidth = image.width * scale;
      const drawHeight = image.height * scale;
      const offsetScale = avatarSize / cropPreviewSize;
      const dx = (avatarSize - drawWidth) / 2 + cropOffset.x * offsetScale;
      const dy = (avatarSize - drawHeight) / 2 + cropOffset.y * offsetScale;

      ctx.clearRect(0, 0, avatarSize, avatarSize);
      ctx.drawImage(image, dx, dy, drawWidth, drawHeight);
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob((result) => result ? resolve(result) : reject(new Error("Gagal membuat crop foto.")), "image/png", 0.92);
      });
      const cropped = new File([blob], `profile-${Date.now()}.png`, { type: "image/png" });
      if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
      setPhoto(cropped);
      setPhotoPreviewUrl(URL.createObjectURL(cropped));
      closeCropModal();
      setNotice("Foto 512 x 512 siap disimpan. Klik Save Profile untuk menyimpan ke database.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Crop foto gagal.");
    }
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

            <div className="md:col-span-2 rounded-xl border border-white/10 bg-black/20 p-4">
              <label className="space-y-1.5 text-sm font-semibold text-slate-200"><span>Foto Profile</span><Input type="file" accept="image/png,image/jpeg" onChange={(e) => onSelectPhoto(e.target.files?.[0] || null)} /></label>
              {photoPreviewUrl ? (
                <div className="mt-4 flex items-center gap-4 rounded-lg border border-sky-500/20 bg-sky-500/5 p-3">
                  <img src={photoPreviewUrl} alt="Foto profile siap simpan" className="h-20 w-20 rounded-full object-cover" />
                  <div className="text-sm text-slate-200">
                    <p className="font-bold text-white">Foto sudah dicrop 512 x 512.</p>
                    <p className="text-xs text-slate-400">Klik Save Profile untuk menyimpan foto ke database.</p>
                  </div>
                </div>
              ) : null}
            </div>

            <div className="md:col-span-2 flex justify-end gap-3"><Button type="button" variant="outline" onClick={cancelEdit}>Cancel</Button><Button type="submit" disabled={mutation.isPending}><Save className="h-4 w-4" /> Save Profile</Button></div>
          </form>
        ) : null}
      </OrganizationTableCard>

      {cropSourceUrl ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-xl rounded-2xl border border-white/10 bg-slate-950 p-5 shadow-2xl">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-white">Crop Foto Profile</h2>
                <p className="text-xs text-slate-400">Geser foto di dalam kotak. Area kotak akan disimpan sebagai avatar 512 x 512.</p>
              </div>
              <Button type="button" variant="outline" size="icon" onClick={closeCropModal}><X className="h-4 w-4" /></Button>
            </div>
            <div className="flex justify-center">
              <div
                ref={cropFrameRef}
                className="relative h-80 w-80 cursor-move overflow-hidden rounded-xl border-2 border-sky-400 bg-black select-none"
                onPointerDown={startDrag}
                onPointerMove={dragCrop}
                onPointerUp={stopDrag}
                onPointerCancel={stopDrag}
              >
                {cropImageSize ? (
                  <img
                    src={cropSourceUrl}
                    alt="Crop source"
                    draggable={false}
                    className="absolute left-1/2 top-1/2 max-w-none select-none"
                    style={{
                      width: `${cropImageSize.width * Math.max(cropPreviewSize / cropImageSize.width, cropPreviewSize / cropImageSize.height)}px`,
                      height: `${cropImageSize.height * Math.max(cropPreviewSize / cropImageSize.width, cropPreviewSize / cropImageSize.height)}px`,
                      transform: `translate(calc(-50% + ${cropOffset.x}px), calc(-50% + ${cropOffset.y}px))`
                    }}
                  />
                ) : null}
                <img src={cropSourceUrl} alt="Loader" className="hidden" onLoad={onCropImageLoad} />
                <div className="pointer-events-none absolute inset-0 rounded-xl ring-2 ring-inset ring-white/70" />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={closeCropModal}>Cancel</Button>
              <Button type="button" variant="outline" onClick={() => setCropOffset({ x: 0, y: 0 })}>Crop</Button>
              <Button type="button" onClick={saveCrop} disabled={!cropImageSize}>Save</Button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Gambar tidak bisa dibaca."));
    image.src = src;
  });
}

function Info({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="rounded-lg border border-white/10 bg-black/20 p-3"><p className="text-xs uppercase tracking-[0.12em] text-slate-400">{label}</p><div className="mt-1 font-bold text-white">{children || "-"}</div></div>;
}
