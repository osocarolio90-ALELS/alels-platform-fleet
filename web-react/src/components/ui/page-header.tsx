import { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { translateUiLabel } from "@/lib/i18n";
import { useLanguageStore } from "@/stores/language-store";

type PageHeaderProps = {
  title: string;
  icon?: ReactNode;
  description?: string;
  actions?: ReactNode;
  className?: string;
};

export function PageHeader({ title, icon, description, actions, className }: PageHeaderProps) {
  const language = useLanguageStore((state) => state.language);
  return (
    <div className={cn("alels-page-header flex flex-col gap-2 rounded-2xl border px-3 py-2 shadow-sm md:flex-row md:items-center md:justify-between", className)}>
      <div className="flex items-center gap-2 pl-2">
        {icon ? (
          <div className="alels-page-header-icon flex h-9 w-9 items-center justify-center rounded-xl border">
            {icon}
          </div>
        ) : null}
        <div>
          <h1 className="text-xl font-extrabold leading-tight">{translateUiLabel(language, title)}</h1>
          {description ? <p className="mt-0.5 text-xs opacity-85">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  );
}
