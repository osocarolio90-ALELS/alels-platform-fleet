import axios from "axios";
import { api } from "@/lib/api";

export type UserProfile = {
  id: number;
  companyId?: number | null;
  companyName?: string | null;
  username: string;
  fullName?: string | null;
  email: string;
  role: string;
  status: string;
  createdAt?: string | null;
  profilePhotoUrl?: string | null;
};

export async function getUserProfile() {
  const response = await api.get<UserProfile>("/api/user/profile");
  return response.data;
}

export async function updateUserProfile(input: {
  username?: string;
  email?: string;
  newPassword?: string;
  photo?: File | null;
}) {
  const formData = new FormData();
  if (input.username !== undefined) formData.append("username", input.username);
  if (input.email !== undefined) formData.append("email", input.email);
  if (input.newPassword) formData.append("newPassword", input.newPassword);
  if (input.photo) formData.append("photo", input.photo);

  try {
    const response = await api.put<{ success: boolean; profile: UserProfile }>("/api/user/profile", formData);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const data = error.response?.data as { message?: string; error?: string } | string | undefined;
      const message = typeof data === "string" ? data : data?.message || data?.error;
      throw new Error(message || `Update profile gagal dengan status ${error.response?.status || "network"}.`);
    }
    throw error;
  }
}
