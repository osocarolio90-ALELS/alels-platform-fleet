import { DataTableColumn, DataTableVisibleColumns } from "./data-table-types";

type DataTableColumnSelectorProps<T> = {
  columns: DataTableColumn<T>[];
  visibleColumns: DataTableVisibleColumns;
  onChange: (columns: DataTableVisibleColumns) => void;
};

export function DataTableColumnSelector<T>({
  columns,
  visibleColumns,
  onChange
}: DataTableColumnSelectorProps<T>) {
  function toggleColumn(key: string) {
    onChange({
      ...visibleColumns,
      [key]: !visibleColumns[key]
    });
  }

  return (
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
  );
}