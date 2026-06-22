import { create } from "zustand";
import type { User } from "@/lib/api";

const AUTH_STORAGE_KEY = "alels-web-react-auth";

type AuthState = {
  token: string | null;
  user: User | null;
  rememberMe: boolean;
  setSession: (token: string, user: User, rememberMe: boolean) => void;
  logout: () => void;
};

function readStoredSession() {
  const stored = readStorage(localStorage) ?? readStorage(sessionStorage);

  if (!stored) {
    return { token: null, user: null, rememberMe: false };
  }

  try {
    const parsed = JSON.parse(stored) as {
      token?: string;
      user?: User;
      rememberMe?: boolean;
    };

    return {
      token: parsed.token ?? null,
      user: parsed.user ?? null,
      rememberMe: Boolean(parsed.rememberMe)
    };
  } catch {
    removeStorage(localStorage);
    removeStorage(sessionStorage);
    return { token: null, user: null, rememberMe: false };
  }
}

function writeStoredSession(token: string, user: User, rememberMe: boolean) {
  const value = JSON.stringify({ token, user, rememberMe });

  if (rememberMe) {
    removeStorage(sessionStorage);
    writeStorage(localStorage, value);
    return;
  }

  removeStorage(localStorage);
  writeStorage(sessionStorage, value);
}

function clearStoredSession() {
  removeStorage(localStorage);
  removeStorage(sessionStorage);
}

function readStorage(storage: Storage) {
  try {
    return storage.getItem(AUTH_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStorage(storage: Storage, value: string) {
  try {
    storage.setItem(AUTH_STORAGE_KEY, value);
  } catch {
    // Browser storage can be unavailable in strict privacy modes.
  }
}

function removeStorage(storage: Storage) {
  try {
    storage.removeItem(AUTH_STORAGE_KEY);
  } catch {
    // Browser storage can be unavailable in strict privacy modes.
  }
}

const initialSession = readStoredSession();

export const useAuthStore = create<AuthState>()((set) => ({
  token: initialSession.token,
  user: initialSession.user,
  rememberMe: initialSession.rememberMe,
  setSession: (token, user, rememberMe) => {
    writeStoredSession(token, user, rememberMe);
    set({ token, user, rememberMe });
  },
  logout: () => {
    clearStoredSession();
    set({ token: null, user: null, rememberMe: false });
  }
}));
