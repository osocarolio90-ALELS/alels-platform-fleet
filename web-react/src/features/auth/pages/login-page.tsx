import { FormEvent, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Navigate, useNavigate } from "react-router-dom";
import { Eye, EyeOff, Headphones, Lock, Moon, Sun, UserRound } from "lucide-react";
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
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
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
          <div className="legacy-hero-art" role="img" aria-label="ALELS trusted telemetry and transport platform" />
        </section>

        <section className="legacy-login-form-panel">
          <form className="legacy-login-card" onSubmit={onSubmit}>
            <h1 className="legacy-login-title">
              Welcome Back
            </h1>
            <p className="legacy-login-subtitle">
              Sign in to your <strong>ALELS</strong> account
            </p>

            <label className="legacy-field">
              <span>Email or Username</span>
              <div className="legacy-input-box">
                <UserRound className="legacy-field-icon" />
                <input
                  className="legacy-input"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="Enter your email or username"
                  type="text"
                  autoComplete="username"
                />
              </div>
            </label>

            <label className="legacy-field">
              <span>Password</span>
              <div className="legacy-input-box">
                <Lock className="legacy-field-icon" />
                <input
                  className="legacy-input"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Enter your password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
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

            <div className="legacy-login-options">
              <label className="legacy-remember">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                />
                Remember me
              </label>
              <a href="mailto:support@alels.co.id?subject=ALELS%20Password%20Support">Forgot password?</a>
            </div>

            {mutation.error ? (
              <p className="mb-4 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-200">
                {getLoginErrorMessage(mutation.error)}
              </p>
            ) : null}

            <button className="legacy-login-btn" disabled={mutation.isPending}>
              {mutation.isPending ? "Signing In..." : "Sign In"}
            </button>

            <div className="legacy-login-support">
              <Headphones aria-hidden="true" />
              <span>Need help? Contact <a href="mailto:support@alels.co.id">ALELS Support</a></span>
            </div>
          </form>
        </section>
      </div>
      <footer className="legacy-login-footer">© {new Date().getFullYear()} ALELS. All rights reserved.</footer>
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
