import { Button } from "@/components/ui/button";

type DataTablePaginationProps = {
  page: number;
  pageSize: number;
  totalRows: number;
  filteredRows: number;
  onPageChange: (page: number) => void;
};

export function DataTablePagination({
  page,
  pageSize,
  totalRows,
  filteredRows,
  onPageChange
}: DataTablePaginationProps) {
  const pageCount = Math.max(1, Math.ceil(filteredRows / pageSize));
  const safePage = Math.min(Math.max(page, 1), pageCount);

  const start = filteredRows === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const end = Math.min(safePage * pageSize, filteredRows);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 pt-3 text-sm">
      <p className="text-muted-foreground">
        Showing {start}-{end} of {filteredRows}
        {filteredRows !== totalRows ? ` filtered from ${totalRows}` : ""}
      </p>

      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={safePage <= 1}
          onClick={() => onPageChange(safePage - 1)}
        >
          Previous
        </Button>

        <span className="rounded-md border border-border px-3 py-1">
          Page {safePage} of {pageCount}
        </span>

        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={safePage >= pageCount}
          onClick={() => onPageChange(safePage + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}