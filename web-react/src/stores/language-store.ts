import { create } from "zustand";

const LANGUAGE_STORAGE_KEY = "alels-web-react-language";

export type Language = "en" | "id";

type LanguageState = {
  language: Language;
  toggleLanguage: () => void;
  setLanguage: (language: Language) => void;
};

function readLanguage(): Language {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY) === "id" ? "id" : "en";
  } catch {
    return "en";
  }
}

function writeLanguage(language: Language) {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Browser storage can be unavailable in strict privacy modes.
  }
}

export const useLanguageStore = create<LanguageState>()((set, get) => ({
  language: readLanguage(),
  toggleLanguage: () => {
    const nextLanguage = get().language === "en" ? "id" : "en";
    writeLanguage(nextLanguage);
    set({ language: nextLanguage });
  },
  setLanguage: (language) => {
    writeLanguage(language);
    set({ language });
  }
}));

