import { CarFront } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function VehicleRegisterPage() {
  return (
    <section className="space-y-4">
      <Card className="rounded-2xl border-white/10 bg-white/[0.03]">
        <CardHeader className="flex flex-row items-center gap-3 p-5">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-sky-400/40 bg-sky-500/10 text-sky-300">
            <CarFront className="h-5 w-5" />
          </div>
          <CardTitle className="text-xl font-extrabold text-white">Vehicle Register</CardTitle>
        </CardHeader>
      </Card>
      <Card className="rounded-2xl border-white/10 bg-white/[0.03]">
        <CardContent className="p-5 text-sm text-slate-300">
          Vehicle Register layout is ready. Content and form flow will be added in the next feature phase.
        </CardContent>
      </Card>
    </section>
  );
}
