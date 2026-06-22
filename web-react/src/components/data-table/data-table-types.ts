import { ReactNode } from "react";

export type DataTableColumn<T> = {
  key: string;
  label: string;
  visible?: boolean;
  filterable?: boolean;
  searchable?: boolean;
  className?: string;
  headerClassName?: string;
  render?: (row: T) => ReactNode;
  value?: (row: T) => string | number | null | undefined;
};

export type DataTableFilters = Record<string, string>;

export type DataTableVisibleColumns = Record<string, boolean>;