import type { FocusEvent } from "react";
import { useMemo, useRef, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export type SearchableSelectOption = {
  value: string;
  label: string;
  extra?: string | null;
};

type SearchableSelectProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: SearchableSelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
};

export function SearchableSelect({ label, value, onChange, options, placeholder = "Select option", disabled = false, required = false }: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const rootRef = useRef<HTMLLabelElement>(null);
  const selected = options.find((option) => option.value === value);
  const filteredOptions = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return options;
    return options.filter((option) => `${option.label} ${option.extra || ""}`.toLowerCase().includes(term));
  }, [options, query]);

  function select(nextValue: string) {
    onChange(nextValue);
    setQuery("");
    setOpen(false);
  }

  function closeWhenLeaving(event: FocusEvent<HTMLLabelElement>) {
    if (!rootRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
  }

  return (
    <label ref={rootRef} onBlur={closeWhenLeaving} className="relative grid gap-1 text-xs font-bold text-slate-300">
      <span>{label}{required ? <span className="text-red-300"> *</span> : null}</span>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        className="flex h-10 w-full items-center justify-between gap-2 rounded-md border border-white/10 bg-[#070b12] px-3 text-left text-sm text-white disabled:cursor-not-allowed disabled:opacity-60"
      >
        <span className={selected ? "truncate" : "truncate text-slate-500"}>{selected?.label || placeholder}</span>
        <ChevronDown className="h-4 w-4 shrink-0 text-slate-400" />
      </button>
      {required ? <select className="sr-only" tabIndex={-1} value={value} required onChange={() => undefined}><option value="" />{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : null}
      {open ? (
        <div className="absolute left-0 right-0 top-[4.25rem] z-50 rounded-xl border border-white/10 bg-[#080d16] p-2 shadow-2xl">
          <div className="relative mb-2">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <Input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search..." className="pl-9 pr-9" />
            {query ? <Button type="button" variant="ghost" size="icon" className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2" onClick={() => setQuery("")}><X className="h-3.5 w-3.5" /></Button> : null}
          </div>
          <div className="max-h-56 overflow-y-auto rounded-lg border border-white/5">
            <button type="button" className="block w-full px-3 py-2 text-left text-sm text-slate-400 hover:bg-white/5" onMouseDown={(event) => event.preventDefault()} onClick={() => select("")}>-</button>
            {filteredOptions.map((option) => (
              <button key={option.value} type="button" className="block w-full px-3 py-2 text-left text-sm text-white hover:bg-sky-500/15" onMouseDown={(event) => event.preventDefault()} onClick={() => select(option.value)}>
                <span className="block truncate font-bold">{option.label}</span>
                {option.extra ? <span className="block truncate text-xs text-slate-400">{option.extra}</span> : null}
              </button>
            ))}
            {filteredOptions.length === 0 ? <div className="px-3 py-3 text-sm text-slate-400">No option found.</div> : null}
          </div>
        </div>
      ) : null}
    </label>
  );
}
