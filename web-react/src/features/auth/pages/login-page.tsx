import { FormEvent, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Navigate, useNavigate } from "react-router-dom";
import { Eye, EyeOff, Lock, Mail, Moon, Sun } from "lucide-react";
import axios from "axios";

import { login } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import { cn } from "@/lib/utils";

export function LoginPage() {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const storedRememberMe = useAuthStore((state) => state.rememberMe);
  const setSession = useAuthStore((state) => state.setSession);
  const theme = useThemeStore((state) => state.theme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);
  const [email, setEmail] = useState("osocarolio90@gmail.com");
  const [password, setPassword] = useState("Alels@2026!");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(storedRememberMe);

  const mutation = useMutation({
    mutationFn: () => login(email.trim(), password),
    onSuccess: (data) => {
      setSession(data.token, data.user, rememberMe);
      navigate("/server-monitor/overview");
    }
  });

  if (token) {
    return <Navigate to="/server-monitor/overview" replace />;
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <main className={cn("legacy-login", theme === "light" ? "theme-light" : "theme-dark")}>
      <button
        className="legacy-login-theme-btn"
        type="button"
        onClick={toggleTheme}
        aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
        title={theme === "dark" ? "Light mode" : "Dark mode"}
      >
        {theme === "dark" ? (
          <Sun className="h-4 w-4" />
        ) : (
          <Moon className="h-4 w-4" />
        )}
      </button>
      <div className="legacy-login-page">
        <section className="legacy-hero-panel" aria-label="ALELS TECH Main Logo">
          <div className="legacy-hero-logo" />
        </section>

        <section className="flex w-full items-center justify-center">
          <form className="legacy-login-card" onSubmit={onSubmit}>
            <img
              src="/assets/logo-card.png"
              alt="ALELS TECH"
              className="legacy-login-logo"
            />

            <h1 className="text-center text-2xl font-semibold leading-tight text-white">
              Welcome Back
            </h1>
            <p className="mb-6 mt-2 text-center text-sm text-[#9ca8bd]">
              Please login to your account
            </p>

            <label className="mb-3 block">
              <span className="sr-only">Email</span>
              <div className="legacy-input-box">
                <Mail className="mr-3 h-4 w-4 text-[#9eb1c9]" />
                <input
                  className="legacy-input"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="Email"
                  type="email"
                />
              </div>
            </label>

            <label className="mb-3 block">
              <span className="sr-only">Password</span>
              <div className="legacy-input-box">
                <Lock className="mr-3 h-4 w-4 text-[#9eb1c9]" />
                <input
                  className="legacy-input"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Password"
                  type={showPassword ? "text" : "password"}
                />
                <button
                  type="button"
                  className="legacy-password-toggle"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  title={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </label>

            <div className="mb-5 mt-1 flex items-center gap-3 text-xs">
              <label className="flex items-center gap-2 text-[#d9e1ef]">
                <input
                  type="checkbox"
                  className="h-3.5 w-3.5 accent-cyan-400"
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                />
                Remember me
              </label>
            </div>

            {mutation.error ? (
              <p className="mb-4 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-200">
                {getLoginErrorMessage(mutation.error)}
              </p>
            ) : null}

            <button className="legacy-login-btn" disabled={mutation.isPending}>
              {mutation.isPending ? "SIGNING IN..." : "LOGIN"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}

function getLoginErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as
      | { message?: string; success?: boolean }
      | string
      | undefined;

    if (typeof data === "string" && data.trim()) {
      return `Login gagal (${status ?? "network"}): ${data}`;
    }

    if (typeof data === "object" && data?.message) {
      return `Login gagal (${status ?? "network"}): ${data.message}`;
    }

    if (error.code === "ERR_NETWORK") {
      return "Login gagal: frontend tidak bisa terhubung ke backend.";
    }

    return `Login gagal (${status ?? "network"}): ${error.message}`;
  }

  return "Login gagal. Periksa email/password atau backend.";
}
