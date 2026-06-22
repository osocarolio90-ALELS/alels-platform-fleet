import { create } from "zustand";

const THEME_STORAGE_KEY = "alels-web-react-theme";

export type ThemeMode = "dark" | "light";

type ThemeState = {
  theme: ThemeMode;
  toggleTheme: () => void;
  setTheme: (theme: ThemeMode) => void;
};

function readTheme(): ThemeMode {
  let stored: string | null = null;

  try {
    stored = localStorage.getItem(THEME_STORAGE_KEY);
  } catch {
    stored = null;
  }

  return stored === "light" ? "light" : "dark";
}

function writeTheme(theme: ThemeMode) {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Browser storage can be unavailable in strict privacy modes.
  }
}

export const useThemeStore = create<ThemeState>()((set, get) => ({
  theme: readTheme(),
  toggleTheme: () => {
    const nextTheme = get().theme === "dark" ? "light" : "dark";
    writeTheme(nextTheme);
    set({ theme: nextTheme });
  },
  setTheme: (theme) => {
    writeTheme(theme);
    set({ theme });
  }
}));
