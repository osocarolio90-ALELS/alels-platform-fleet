import { ReactNode } from "react";
import { cn } from "@/lib/utils";

type PageHeaderProps = {
  title: string;
  icon?: ReactNode;
  description?: string;
  actions?: ReactNode;
  className?: string;
};

export function PageHeader({ title, icon, description, actions, className }: PageHeaderProps) {
  return (
    <div className={cn("flex flex-col gap-2 rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-foreground shadow-sm md:flex-row md:items-center md:justify-between", className)}>
      <div className="flex items-center gap-2 pl-2">
        {icon ? (
          <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-sky-400/30 bg-sky-400/10 text-sky-300">
            {icon}
          </div>
        ) : null}
        <div>
          <h1 className="text-xl font-extrabold leading-tight text-white">{title}</h1>
          {description ? <p className="mt-0.5 text-xs text-slate-300">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  );
}
