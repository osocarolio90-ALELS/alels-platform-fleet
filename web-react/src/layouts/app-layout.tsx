import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";
import { Bot, Building2, CarFront, ChevronDown, CircleUserRound, Cpu, Database, DollarSign, Globe, IdCard, LogOut, Moon, PanelLeftClose, PanelLeftOpen, RadioTower, Recycle, ServerCog, Settings, Sun, UsersRound } from "lucide-react";

import { useAuthStore } from "@/stores/auth-store";
import { t, type TranslationKey } from "@/lib/i18n";
import { useLanguageStore } from "@/stores/language-store";
import { useThemeStore } from "@/stores/theme-store";
import { cn } from "@/lib/utils";
import { MASTER_DATA_ROLES, ORGANIZATION_ROLES, SERVER_MONITOR_ROLES, hasRole } from "@/lib/role-access";

type SidebarItem = { to: string; labelKey: TranslationKey; roles?: readonly string[] };

const organizationItems: SidebarItem[] = [
  { to: "/organization/company-list", labelKey: "companyList" },
  { to: "/organization/user-list", labelKey: "userList" },
  { to: "/organization/wasted", labelKey: "wasted" }
];

const serverMonitorItems: SidebarItem[] = [
  { to: "/server-monitor/overview", labelKey: "overviewMonitor" },
  { to: "/server-monitor/ai-ops", labelKey: "aiOpsMonitor" },
  { to: "/server-monitor/gateway", labelKey: "gatewayMonitor" },
  { to: "/server-monitor/traffic", labelKey: "trafficMonitor" },
  { to: "/server-monitor/database", labelKey: "databaseMonitor" },
  { to: "/server-monitor/storage", labelKey: "storageMonitor" },
  { to: "/server-monitor/security", labelKey: "securityMonitor" }
];

const assetRegisterItems: SidebarItem[] = [
  { to: "/asset-register/vehicle-register", labelKey: "vehicleRegister" },
  { to: "/asset-register/device-register", labelKey: "deviceRegister" },
  { to: "/asset-register/driver-register", labelKey: "driverRegister" },
  { to: "/asset-register/energy-price", labelKey: "energyPrice" },
  { to: "/asset-register/wasted", labelKey: "wasted" }
];

const masterDataItems: SidebarItem[] = [
  { to: "/master-data/vehicle-master", labelKey: "vehicleMaster" },
  { to: "/master-data/device-master", labelKey: "deviceMaster" },
  { to: "/master-data/reference-price", labelKey: "referencePrice" },
  { to: "/master-data/license-master", labelKey: "licenseMaster" },
  { to: "/master-data/wasted", labelKey: "wasted" }
];
const telemetryItems: SidebarItem[] = [
  { to: "/assignment", labelKey: "assignment" },
  { to: "/telemetry/group", labelKey: "telemetryGroup" },
  { to: "/telemetry/device", labelKey: "device" }
];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [sidebarHovered, setSidebarHovered] = useState(false);
  const [serverMonitorOpen, setServerMonitorOpen] = useState(location.pathname.startsWith("/server-monitor"));
  const [organizationOpen, setOrganizationOpen] = useState(location.pathname.startsWith("/organization"));
  const [assetRegisterOpen, setAssetRegisterOpen] = useState(location.pathname.startsWith("/asset-register"));
  const [masterDataOpen, setMasterDataOpen] = useState(location.pathname.startsWith("/master-data"));
  const [telemetryOpen, setTelemetryOpen] = useState(location.pathname.startsWith("/telemetry") || location.pathname === "/assignment");
  const [profileOpen, setProfileOpen] = useState(false);
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const language = useLanguageStore((state) => state.language);
  const toggleLanguage = useLanguageStore((state) => state.toggleLanguage);
  const theme = useThemeStore((state) => state.theme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);

  const canViewServerMonitor = hasRole(user?.role, SERVER_MONITOR_ROLES);
  const canViewOrganization = hasRole(user?.role, ORGANIZATION_ROLES);
  const canViewMasterData = hasRole(user?.role, MASTER_DATA_ROLES);
  const visibleAssetRegisterItems = assetRegisterItems.filter((item) => !item.roles || hasRole(user?.role, item.roles as any));
  const sidebarIsCompact = sidebarCollapsed && !sidebarHovered;
  const currentCompanyName = (user?.companyName || "ALELS TECH INDONESIA").trim().toUpperCase();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className={cn("legacy-shell", sidebarIsCompact && "sidebar-collapsed", theme === "light" ? "theme-light" : "theme-dark")}>
      <aside
        className={cn(
          "legacy-sidebar fixed inset-y-0 left-0 z-20 hidden flex-col px-3.5 py-[18px] lg:flex",
          sidebarIsCompact ? "w-[76px]" : "w-[250px]"
        )}
        onMouseEnter={() => sidebarCollapsed && setSidebarHovered(true)}
        onMouseLeave={() => setSidebarHovered(false)}
      >
        <div className="mb-7 flex items-center gap-3">
          <div className="legacy-brand-logo">
            <img
              src={theme === "dark" ? "/assets/alels-mark-dark.png" : "/assets/alels-mark-light.png"}
              alt="ALELS"
              className="h-full w-full object-cover"
            />
          </div>
          <div className="sidebar-text">
            <strong className="block text-base font-extrabold tracking-tight">
              <span className="alels-brand-text-white">Alels</span><span className="alels-brand-text-tech">Tech</span><span className="alels-brand-text-white">IDN</span>
            </strong>
            <small className="alels-sidebar-welcome text-[11px] font-semibold">{t(language, "welcomeAti")}</small>
          </div>
          <button
            className="legacy-sidebar-toggle ml-auto"
            type="button"
            onClick={() => { setSidebarCollapsed((current) => !current); setSidebarHovered(false); }}
            aria-label={sidebarCollapsed ? t(language, "showSidebar") : t(language, "hideSidebar")}
            title={sidebarCollapsed ? t(language, "showSidebar") : t(language, "hideSidebar")}
          >
            {sidebarCollapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
          </button>
        </div>

        <nav className="flex-1 space-y-1">
          {canViewServerMonitor ? (
            <div>
              <p className="sidebar-text mb-2 mt-[18px] px-2 text-[11px] uppercase tracking-[0.04em] text-[#8e96a8]">{t(language, "serverOperations")}</p>
              <button
                type="button"
                className={cn("legacy-nav-link mb-[3px] flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium", location.pathname.startsWith("/server-monitor") && "active")}
                onClick={() => setServerMonitorOpen((current) => !current)}
                title={t(language, "serverOperations")}
              >
                <ServerCog className="h-4 w-4 shrink-0" />
                <span className="sidebar-text flex-1 text-left">{t(language, "serverOperations")}</span>
                <ChevronDown className={cn("sidebar-text h-4 w-4 transition-transform", serverMonitorOpen && "rotate-180")} />
              </button>
              {serverMonitorOpen && !sidebarIsCompact ? (
                <div className="legacy-subnav mb-1 ml-8 space-y-1">
                  {serverMonitorItems.map((item) => (
                    <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("legacy-subnav-link", isActive && "active")}>
                      <span className="inline-flex items-center gap-2"><Bot className="h-3.5 w-3.5" />{t(language, item.labelKey)}</span>
                    </NavLink>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          {canViewOrganization ? (
            <div>
              <p className="sidebar-text mb-2 mt-[18px] px-2 text-[11px] uppercase tracking-[0.04em] text-[#8e96a8]">{t(language, "organizations")}</p>
              <button
                type="button"
                className={cn("legacy-nav-link mb-[3px] flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium", location.pathname.startsWith("/organization") && "active")}
                onClick={() => setOrganizationOpen((current) => !current)}
                title={t(language, "organizations")}
              >
                <Building2 className="h-4 w-4 shrink-0" />
                <span className="sidebar-text flex-1 text-left">{t(language, "organizations")}</span>
                <ChevronDown className={cn("sidebar-text h-4 w-4 transition-transform", organizationOpen && "rotate-180")} />
              </button>
              {organizationOpen && !sidebarIsCompact ? (
                <div className="legacy-subnav mb-1 ml-8 space-y-1">
                  {organizationItems.map((item) => (
                    <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("legacy-subnav-link", isActive && "active")}>
                      <span className="inline-flex items-center gap-2"><Building2 className="h-3.5 w-3.5" />{t(language, item.labelKey)}</span>
                    </NavLink>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          <div>
            <p className="sidebar-text mb-2 mt-[18px] px-2 text-[11px] uppercase tracking-[0.04em] text-[#8e96a8]">{t(language, "assetRegister")}</p>
            <button
              type="button"
              className={cn("legacy-nav-link mb-[3px] flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium", location.pathname.startsWith("/asset-register") && "active")}
              onClick={() => setAssetRegisterOpen((current) => !current)}
              title={t(language, "assetRegister")}
            >
              <CarFront className="h-4 w-4 shrink-0" />
              <span className="sidebar-text flex-1 text-left">{t(language, "assetRegister")}</span>
              <ChevronDown className={cn("sidebar-text h-4 w-4 transition-transform", assetRegisterOpen && "rotate-180")} />
            </button>
            {assetRegisterOpen && !sidebarIsCompact ? (
              <div className="legacy-subnav mb-1 ml-8 space-y-1">
                {visibleAssetRegisterItems.map((item) => (
                  <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("legacy-subnav-link", isActive && "active")}>
                    <span className="inline-flex items-center gap-2">
                      {item.to.includes("device") ? <Cpu className="h-3.5 w-3.5" /> : item.to.includes("driver") ? <IdCard className="h-3.5 w-3.5" /> : item.to.includes("energy-reference") ? <DollarSign className="h-3.5 w-3.5" /> : item.to.includes("energy") ? <DollarSign className="h-3.5 w-3.5" /> : item.to.includes("wasted") ? <Recycle className="h-3.5 w-3.5" /> : <CarFront className="h-3.5 w-3.5" />}
                      {t(language, item.labelKey)}
                    </span>
                  </NavLink>
                ))}
              </div>
            ) : null}
          </div>

          {canViewMasterData ? (
            <div>
              <p className="sidebar-text mb-2 mt-[18px] px-2 text-[11px] uppercase tracking-[0.04em] text-[#8e96a8]">{t(language, "masterData")}</p>
              <button
                type="button"
                className={cn("legacy-nav-link mb-[3px] flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium", location.pathname.startsWith("/master-data") && "active")}
                onClick={() => setMasterDataOpen((current) => !current)}
                title={t(language, "masterData")}
              >
                <Database className="h-4 w-4 shrink-0" />
                <span className="sidebar-text flex-1 text-left">{t(language, "masterData")}</span>
                <ChevronDown className={cn("sidebar-text h-4 w-4 transition-transform", masterDataOpen && "rotate-180")} />
              </button>
              {masterDataOpen && !sidebarIsCompact ? (
                <div className="legacy-subnav mb-1 ml-8 space-y-1">
                  {masterDataItems.map((item) => (
                    <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("legacy-subnav-link", isActive && "active")}>
                      <span className="inline-flex items-center gap-2"><Database className="h-3.5 w-3.5" />{t(language, item.labelKey)}</span>
                    </NavLink>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          <div>
            <p className="sidebar-text mb-2 mt-[18px] px-2 text-[11px] uppercase tracking-[0.04em] text-[#8e96a8]">{t(language, "telemetry")}</p>
            <button type="button" className={cn("legacy-nav-link mb-[3px] flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium", (location.pathname.startsWith("/telemetry") || location.pathname === "/assignment") && "active")} onClick={() => setTelemetryOpen(v => !v)} title={t(language, "telemetry")}>
              <RadioTower className="h-4 w-4 shrink-0" /><span className="sidebar-text flex-1 text-left">{t(language, "telemetry")}</span><ChevronDown className={cn("sidebar-text h-4 w-4 transition-transform", telemetryOpen && "rotate-180")} />
            </button>
            {telemetryOpen && !sidebarIsCompact ? <div className="legacy-subnav mb-1 ml-8 space-y-1">{telemetryItems.map(item => <NavLink key={item.to} to={item.to} className={({isActive})=>cn("legacy-subnav-link",isActive&&"active")}><span className="inline-flex items-center gap-2"><UsersRound className="h-3.5 w-3.5"/>{t(language,item.labelKey)}</span></NavLink>)}</div> : null}
          </div>

        </nav>
        <div className="mt-auto border-t border-white/10 pt-3">
          <NavLink
            to="/user/profile"
            className={({ isActive }) =>
              cn(
                "legacy-nav-link flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium",
                isActive && "active"
              )
            }
            title={t(language, "userProfile")}
          >
            <CircleUserRound className="h-4 w-4 shrink-0" />
            <span className="sidebar-text">{t(language, "userProfile")}</span>
          </NavLink>
        </div>
      </aside>

      <div className={cn("legacy-content", sidebarIsCompact ? "lg:pl-[76px]" : "lg:pl-[250px]")}> 
        <header className="legacy-topbar sticky top-0 z-10 flex h-[60px] items-center justify-between px-[22px]">
          <div className="alels-topbar-company-badge inline-flex h-9 min-w-[210px] items-center gap-2 rounded-lg border px-3">
            <ServerCog className="alels-topbar-company-icon h-4 w-4" />
            <strong className="alels-topbar-company-name max-w-[280px] truncate text-sm font-extrabold tracking-wide">{currentCompanyName}</strong>
          </div>
          <div className="flex items-center gap-2.5">
            <button className="alels-topbar-language-button inline-flex h-9 min-w-[68px] items-center justify-center gap-2 rounded-lg border px-3 text-sm font-extrabold" type="button" onClick={toggleLanguage} aria-label={t(language, "switchLanguage")} title={language === "en" ? "Bahasa Indonesia" : "English"}>
              <Globe className="h-4 w-4" /><span>{language === "en" ? "EN" : "ID"}</span>
            </button>
            <button className="legacy-icon-btn" type="button" onClick={toggleTheme} aria-label={theme === "dark" ? t(language, "switchLight") : t(language, "switchDark")} title={theme === "dark" ? t(language, "lightMode") : t(language, "darkMode")}>
              {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
            <div className="relative">
              <button className="legacy-icon-btn h-10 w-10 overflow-hidden rounded-full p-0" type="button" onClick={() => setProfileOpen((current) => !current)} aria-label={t(language, "userProfile")} title={t(language, "userProfile")}>
                <img src={user?.profilePhotoUrl || "/assets/logokecil.png"} alt="User" className="h-full w-full rounded-full object-cover" />
              </button>
              {profileOpen ? (
                <div className="absolute right-0 mt-2 w-72 rounded-xl border border-white/10 bg-[#0b1220] p-3 shadow-2xl">
                  <div className="border-b border-white/10 pb-3">
                    <p className="text-xs uppercase tracking-[0.12em] text-slate-400">{t(language, "company")}</p>
                    <p className="truncate text-sm font-bold text-white">{currentCompanyName}</p>
                    <p className="mt-2 text-xs uppercase tracking-[0.12em] text-slate-400">{t(language, "role")}</p>
                    <p className="truncate text-sm font-bold text-white">{(user?.role || "-").toUpperCase()}</p>
                    <p className="mt-2 text-xs uppercase tracking-[0.12em] text-slate-400">{t(language, "username")}</p>
                    <p className="truncate text-sm font-bold text-white">{user?.username || user?.fullName || "-"}</p>
                    <p className="mt-2 text-xs uppercase tracking-[0.12em] text-slate-400">{t(language, "userEmail")}</p>
                    <p className="truncate text-sm font-bold text-white">{user?.email || "-"}</p>
                  </div>
                  <button className="mt-3 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-100 hover:bg-white/10" type="button" onClick={() => { setProfileOpen(false); navigate("/user/profile"); }}>
                    <Settings className="h-4 w-4" /> {t(language, "settings")}
                  </button>
                  <button className="mt-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-red-200 hover:bg-red-500/10" type="button" onClick={handleLogout}>
                    <LogOut className="h-4 w-4" /> {t(language, "logout")}
                  </button>
                </div>
              ) : null}
            </div>
            <button
              className="legacy-icon-btn text-red-200 hover:bg-red-500/10 hover:text-red-100"
              type="button"
              onClick={handleLogout}
              aria-label={t(language, "logout")}
              title={t(language, "logout")}
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </header>
        <main className="px-[22px] py-5"><Outlet /></main>
      </div>
    </div>
  );
}
