import { useEffect, useState } from "react";
import { ArrowRightLeft, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { t } from "@/lib/i18n";
import { useLanguageStore } from "@/stores/language-store";

export type MoveCompanyOption = { value: string; label: string; extra?: string | null };

export function AssetMoveDialog({
  open,
  assetCount,
  companies,
  pending,
  error,
  onClose,
  onMove
}: {
  open: boolean;
  assetCount: number;
  companies: MoveCompanyOption[];
  pending: boolean;
  error?: string | null;
  onClose: () => void;
  onMove: (targetCompanyId: number) => void;
}) {
  const language = useLanguageStore((state) => state.language);
  const [targetCompany, setTargetCompany] = useState("");

  useEffect(() => {
    if (open) setTargetCompany("");
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="asset-move-title">
      <form
        className="w-full max-w-lg rounded-xl border border-border bg-card p-5 text-card-foreground shadow-2xl"
        onSubmit={(event) => {
          event.preventDefault();
          if (targetCompany) onMove(Number(targetCompany));
        }}
      >
        <div className="mb-5 flex items-center justify-between gap-3">
          <div>
            <h2 id="asset-move-title" className="flex items-center gap-2 text-lg font-bold"><ArrowRightLeft className="h-5 w-5" />{t(language, "move")}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{assetCount} asset selected</p>
          </div>
          <Button type="button" size="icon" variant="outline" onClick={onClose} aria-label={t(language, "cancel")}><X className="h-4 w-4" /></Button>
        </div>

        <SearchableSelect
          required
          label={t(language, "targetCompany")}
          value={targetCompany}
          onChange={setTargetCompany}
          options={companies}
        />
        {error ? <div className="mt-4 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error}</div> : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>{t(language, "cancel")}</Button>
          <Button type="submit" disabled={!targetCompany || pending}><ArrowRightLeft className="h-4 w-4" />{t(language, "move")}</Button>
        </div>
      </form>
    </div>
  );
}
