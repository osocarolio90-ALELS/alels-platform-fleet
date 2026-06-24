import { ReactNode } from "react";
import { type ButtonProps } from "@/components/ui/button";

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

export type DataTableBulkAction<T> = {
  key: string;
  label: string;
  icon?: ReactNode;
  variant?: ButtonProps["variant"];
  confirmMessage?: string | ((rows: T[]) => string);
  disabled?: (rows: T[]) => boolean;
  hidden?: (rows: T[]) => boolean;
  onClick: (rows: T[]) => void | Promise<void>;
};

export type DataTableFilters = Record<string, string>;

export type DataTableVisibleColumns = Record<string, boolean>;
