import { useEffect, useMemo, useState } from "react";
import { ChevronDown } from "lucide-react";

import { Table, Td, Th } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { DataTablePagination } from "./data-table-pagination";
import { DataTableToolbar } from "./data-table-toolbar";
import {
  DataTableBulkAction,
  DataTableColumn,
  DataTableFilters,
  DataTableVisibleColumns
} from "./data-table-types";

type DataTableProps<T> = {
  data: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string | number;
  actions?: (row: T) => React.ReactNode;
  bulkActions?: DataTableBulkAction<T>[];
  selectable?: boolean;
  isRowSelectable?: (row: T) => boolean;
  emptyMessage?: string;
  searchPlaceholder?: string;
};

export function DataTable<T>({
  data,
  columns,
  rowKey,
  actions,
  bulkActions = [],
  selectable,
  isRowSelectable,
  emptyMessage = "No data found.",
  searchPlaceholder
}: DataTableProps<T>) {
  const selectionEnabled = selectable === true || bulkActions.length > 0;
  const [search, setSearch] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [showColumns, setShowColumns] = useState(false);
  const [filters, setFilters] = useState<DataTableFilters>({});
  const [visibleColumns, setVisibleColumns] = useState<DataTableVisibleColumns>(() =>
    Object.fromEntries(columns.map((column) => [column.key, column.visible !== false]))
  );
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [bulkMenuOpen, setBulkMenuOpen] = useState(false);

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

  const selectablePagedRows = useMemo(
    () => pagedData.filter((row) => isSelectable(row, isRowSelectable)),
    [pagedData, isRowSelectable]
  );

  const selectedRows = useMemo(() => {
    if (!selectionEnabled || selectedKeys.size === 0) return [];
    return data.filter((row) => selectedKeys.has(toKey(rowKey(row))) && isSelectable(row, isRowSelectable));
  }, [data, selectedKeys, rowKey, isRowSelectable, selectionEnabled]);

  const allPageRowsSelected = selectablePagedRows.length > 0 && selectablePagedRows.every((row) => selectedKeys.has(toKey(rowKey(row))));
  const somePageRowsSelected = selectablePagedRows.some((row) => selectedKeys.has(toKey(rowKey(row))));

  useEffect(() => {
    setPage(1);
  }, [search, filters, pageSize]);

  useEffect(() => {
    setSelectedKeys((current) => {
      if (current.size === 0) return current;
      const validKeys = new Set(data.map((row) => toKey(rowKey(row))));
      const next = new Set(Array.from(current).filter((key) => validKeys.has(key)));
      return next.size === current.size ? current : next;
    });
  }, [data, rowKey]);

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

  function clearSelection() {
    setSelectedKeys(new Set());
  }

  function toggleRow(row: T) {
    if (!isSelectable(row, isRowSelectable)) return;
    const key = toKey(rowKey(row));
    setSelectedKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  async function runBulkAction(action: DataTableBulkAction<T>) {
    if (selectedRows.length === 0 || action.disabled?.(selectedRows)) return;
    const confirmation = typeof action.confirmMessage === "function" ? action.confirmMessage(selectedRows) : action.confirmMessage;
    if (confirmation && !window.confirm(confirmation)) return;
    await action.onClick(selectedRows);
    setBulkMenuOpen(false);
    clearSelection();
  }

  const visibleBulkActions = selectedRows.length > 0
    ? bulkActions.filter((action) => !action.hidden?.(selectedRows))
    : bulkActions.filter((action) => !action.hidden?.([]));

  function togglePage() {
    setSelectedKeys((current) => {
      const next = new Set(current);
      if (allPageRowsSelected) {
        selectablePagedRows.forEach((row) => next.delete(toKey(rowKey(row))));
      } else {
        selectablePagedRows.forEach((row) => next.add(toKey(rowKey(row))));
      }
      return next;
    });
  }

  return (
    <div className="space-y-3">
      {bulkActions.length > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="relative">
            <Button
              type="button"
              variant={selectedRows.length > 0 ? "default" : "outline"}
              size="sm"
              disabled={selectedRows.length === 0}
              onClick={() => setBulkMenuOpen((value) => !value)}
              className={selectedRows.length > 0 ? "bg-sky-500 text-white hover:bg-sky-600" : ""}
            >
              Actions{selectedRows.length > 0 ? ` (${selectedRows.length} selected)` : ""}
              <ChevronDown className="h-4 w-4" />
            </Button>
            {bulkMenuOpen && selectedRows.length > 0 ? (
              <div data-bulk-actions-menu className="data-table-bulk-actions-menu absolute left-0 z-30 mt-2 min-w-56 rounded-md border border-sky-300 bg-sky-500 p-1 shadow-xl">
                {visibleBulkActions.length > 0 ? visibleBulkActions.map((action) => (
                  <button
                    key={action.key}
                    type="button"
                    disabled={action.disabled?.(selectedRows)}
                    onClick={() => void runBulkAction(action)}
                    className="flex w-full items-center gap-2 rounded-sm bg-sky-500 px-3 py-2 text-left text-sm font-semibold text-white hover:bg-sky-600 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {action.icon}
                    <span>{action.label}</span>
                  </button>
                )) : (
                  <div className="px-3 py-2 text-sm font-semibold text-white">No bulk action available.</div>
                )}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      <DataTableToolbar
        search={search}
        searchPlaceholder={searchPlaceholder}
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
            {selectionEnabled ? (
              <Th className="w-10">
                <input
                  type="checkbox"
                  aria-label="Select all rows on this page"
                  checked={allPageRowsSelected}
                  ref={(element) => {
                    if (element) element.indeterminate = !allPageRowsSelected && somePageRowsSelected;
                  }}
                  disabled={selectablePagedRows.length === 0}
                  onChange={togglePage}
                />
              </Th>
            ) : null}
            {activeColumns.map((column) => (
              <Th key={column.key} className={column.headerClassName}>
                {column.label}
              </Th>
            ))}
            {actions ? <Th>Action</Th> : null}
          </tr>

          {showFilters ? (
            <tr>
              {selectionEnabled ? <Th /> : null}
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
                colSpan={activeColumns.length + (actions ? 1 : 0) + (selectionEnabled ? 1 : 0)}
                className="text-center text-muted-foreground"
              >
                {emptyMessage}
              </Td>
            </tr>
          ) : (
            pagedData.map((row) => {
              const key = toKey(rowKey(row));
              const rowSelectable = isSelectable(row, isRowSelectable);
              return (
                <tr key={key}>
                  {selectionEnabled ? (
                    <Td>
                      <input
                        type="checkbox"
                        aria-label="Select row"
                        checked={selectedKeys.has(key)}
                        disabled={!rowSelectable}
                        onChange={() => toggleRow(row)}
                      />
                    </Td>
                  ) : null}
                  {activeColumns.map((column) => (
                    <Td key={column.key} className={column.className}>
                      {column.render ? column.render(row) : getColumnValue(row, column)}
                    </Td>
                  ))}
                  {actions ? <Td>{actions(row)}</Td> : null}
                </tr>
              );
            })
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

function toKey(value: string | number) {
  return String(value);
}

function isSelectable<T>(row: T, isRowSelectable?: (row: T) => boolean) {
  return isRowSelectable ? isRowSelectable(row) : true;
}
