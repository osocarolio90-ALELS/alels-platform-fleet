import { useEffect, useMemo, useState } from "react";

import { Table, Td, Th } from "@/components/ui/table";
import { DataTablePagination } from "./data-table-pagination";
import { DataTableToolbar } from "./data-table-toolbar";
import {
  DataTableColumn,
  DataTableFilters,
  DataTableVisibleColumns
} from "./data-table-types";

type DataTableProps<T> = {
  data: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string | number;
  actions?: (row: T) => React.ReactNode;
  emptyMessage?: string;
  searchPlaceholder?: string;
};

export function DataTable<T>({
  data,
  columns,
  rowKey,
  actions,
  emptyMessage = "No data found.",
  searchPlaceholder
}: DataTableProps<T>) {
  const [search, setSearch] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [showColumns, setShowColumns] = useState(false);
  const [filters, setFilters] = useState<DataTableFilters>({});
  const [visibleColumns, setVisibleColumns] = useState<DataTableVisibleColumns>(() =>
    Object.fromEntries(columns.map((column) => [column.key, column.visible !== false]))
  );
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const activeColumns = useMemo(
    () => columns.filter((column) => visibleColumns[column.key] !== false),
    [columns, visibleColumns]
  );

  const filteredData = useMemo(() => {
    const keyword = search.trim().toLowerCase();

    return data.filter((row) => {
      const searchableText = columns
        .filter((column) => column.searchable !== false)
        .map((column) => getColumnValue(row, column))
        .join(" ")
        .toLowerCase();

      const matchSearch = !keyword || searchableText.includes(keyword);

      const matchFilters = columns.every((column) => {
        const filterValue = filters[column.key]?.trim().toLowerCase();
        if (!filterValue) return true;

        const value = getColumnValue(row, column).toLowerCase();
        return value.includes(filterValue);
      });

      return matchSearch && matchFilters;
    });
  }, [data, columns, search, filters]);

  const pagedData = useMemo(() => {
    const start = (page - 1) * pageSize;
    return filteredData.slice(start, start + pageSize);
  }, [filteredData, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [search, filters, pageSize]);

  function updateFilter(key: string, value: string) {
    setFilters((current) => ({
      ...current,
      [key]: value
    }));
  }

  function clearFilters() {
    setSearch("");
    setFilters({});
    setPage(1);
  }

  return (
    <div className="space-y-3">
      <DataTableToolbar
        search={search}
        onSearchChange={setSearch}
        showFilters={showFilters}
        onToggleFilters={() => setShowFilters((value) => !value)}
        showColumns={showColumns}
        onToggleColumns={() => setShowColumns((value) => !value)}
        columns={columns}
        visibleColumns={visibleColumns}
        onVisibleColumnsChange={setVisibleColumns}
        pageSize={pageSize}
        onPageSizeChange={setPageSize}
        onClearFilters={clearFilters}
      />

      <Table>
        <thead>
          <tr>
            {activeColumns.map((column) => (
              <Th key={column.key} className={column.headerClassName}>
                {column.label}
              </Th>
            ))}
            {actions ? <Th>Action</Th> : null}
          </tr>

          {showFilters ? (
            <tr>
              {activeColumns.map((column) => (
                <Th key={column.key}>
                  {column.filterable === false ? null : (
                    <input
                      value={filters[column.key] || ""}
                      onChange={(event) => updateFilter(column.key, event.target.value)}
                      className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs outline-none focus:ring-2 focus:ring-ring"
                    />
                  )}
                </Th>
              ))}
              {actions ? <Th /> : null}
            </tr>
          ) : null}
        </thead>

        <tbody>
          {pagedData.length === 0 ? (
            <tr>
              <Td
                colSpan={activeColumns.length + (actions ? 1 : 0)}
                className="text-center text-muted-foreground"
              >
                {emptyMessage}
              </Td>
            </tr>
          ) : (
            pagedData.map((row) => (
              <tr key={rowKey(row)}>
                {activeColumns.map((column) => (
                  <Td key={column.key} className={column.className}>
                    {column.render ? column.render(row) : getColumnValue(row, column)}
                  </Td>
                ))}
                {actions ? <Td>{actions(row)}</Td> : null}
              </tr>
            ))
          )}
        </tbody>
      </Table>

      <DataTablePagination
        page={page}
        pageSize={pageSize}
        totalRows={data.length}
        filteredRows={filteredData.length}
        onPageChange={setPage}
      />
    </div>
  );
}

function getColumnValue<T>(row: T, column: DataTableColumn<T>) {
  if (column.value) {
    const value = column.value(row);
    return value == null ? "" : String(value);
  }

  const value = (row as Record<string, unknown>)[column.key];
  return value == null ? "" : String(value);
}