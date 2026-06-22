import { Button } from "@/components/ui/button";
import { DataTableColumn, DataTableVisibleColumns } from "./data-table-types";

type DataTableToolbarProps<T> = {
  search: string;
  onSearchChange: (value: string) => void;

  showFilters: boolean;
  onToggleFilters: () => void;

  showColumns: boolean;
  onToggleColumns: () => void;

  columns: DataTableColumn<T>[];
  visibleColumns: DataTableVisibleColumns;
  onVisibleColumnsChange: (columns: DataTableVisibleColumns) => void;

  pageSize: number;
  onPageSizeChange: (size: number) => void;

  onClearFilters?: () => void;
};

export function DataTableToolbar<T>({
  search,
  onSearchChange,
  showFilters,
  onToggleFilters,
  showColumns,
  onToggleColumns,
  columns,
  visibleColumns,
  onVisibleColumnsChange,
  pageSize,
  onPageSizeChange,
  onClearFilters
}: DataTableToolbarProps<T>) {
  function toggleColumn(key: string) {
    onVisibleColumnsChange({
      ...visibleColumns,
      [key]: !visibleColumns[key]
    });
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="Search..."
          className="h-9 w-full max-w-sm rounded-md border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
        />

        <Button type="button" variant="outline" size="sm" onClick={onToggleFilters}>
          {showFilters ? "Hide Filters" : "Show Filters"}
        </Button>

        <Button type="button" variant="outline" size="sm" onClick={onToggleColumns}>
          {showColumns ? "Hide Columns" : "Show Columns"}
        </Button>

        {onClearFilters ? (
          <Button type="button" variant="outline" size="sm" onClick={onClearFilters}>
            Clear
          </Button>
        ) : null}

        <div className="ml-auto flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Show</span>
          <select
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
            className="h-9 rounded-md border border-input bg-background px-2 text-sm outline-none focus:ring-2 focus:ring-ring"
          >
            {[10, 25, 50, 100].map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
          <span className="text-muted-foreground">rows</span>
        </div>
      </div>

      {showColumns ? (
        <div className="flex flex-wrap gap-2 rounded-lg border border-border bg-muted/20 p-3">
          {columns.map((column) => (
            <label
              key={column.key}
              className="flex items-center gap-2 rounded-md border border-border bg-background px-3 py-1.5 text-sm"
            >
              <input
                type="checkbox"
                checked={visibleColumns[column.key] !== false}
                onChange={() => toggleColumn(column.key)}
              />
              <span>{column.label}</span>
            </label>
          ))}
        </div>
      ) : null}
    </div>
  );
}