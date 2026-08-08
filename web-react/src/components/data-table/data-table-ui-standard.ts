export const DATA_TABLE_UI_STANDARD = {
  selectionColumnWidthPx: 40,
  firstStickyColumnWidthPx: 220,
  secondStickyColumnWidthPx: 180,
  stickyHeaderClassName: "sticky z-30 isolate overflow-hidden bg-card bg-clip-padding",
  stickyCellClassName: "sticky z-20 isolate overflow-hidden bg-card bg-clip-padding",
  stickySelectionHeaderClassName: "sticky left-0 z-40 isolate overflow-hidden bg-card bg-clip-padding",
  stickySelectionCellClassName: "sticky left-0 z-30 isolate overflow-hidden bg-card bg-clip-padding",
  stickyBoundaryClassName: "shadow-[6px_0_8px_-8px_hsl(var(--foreground))]"
} as const;

const SOLID_STICKY_BACKGROUND = "hsl(var(--card))";

export function stickyColumnStyle(columnIndex: number, selectionEnabled: boolean) {
  if (columnIndex > 1) return undefined;

  const selectionOffset = selectionEnabled ? DATA_TABLE_UI_STANDARD.selectionColumnWidthPx : 0;
  const left = columnIndex === 0
    ? selectionOffset
    : selectionOffset + DATA_TABLE_UI_STANDARD.firstStickyColumnWidthPx;
  const width = columnIndex === 0
    ? DATA_TABLE_UI_STANDARD.firstStickyColumnWidthPx
    : DATA_TABLE_UI_STANDARD.secondStickyColumnWidthPx;

  return {
    left,
    minWidth: width,
    width,
    maxWidth: width,
    backgroundColor: SOLID_STICKY_BACKGROUND
  };
}

export function stickySelectionStyle() {
  return {
    left: 0,
    minWidth: DATA_TABLE_UI_STANDARD.selectionColumnWidthPx,
    width: DATA_TABLE_UI_STANDARD.selectionColumnWidthPx,
    maxWidth: DATA_TABLE_UI_STANDARD.selectionColumnWidthPx,
    backgroundColor: SOLID_STICKY_BACKGROUND
  };
}

export function stickyColumnClassName(columnIndex: number, cell: "header" | "body") {
  if (columnIndex > 1) return undefined;

  return [
    cell === "header"
      ? DATA_TABLE_UI_STANDARD.stickyHeaderClassName
      : DATA_TABLE_UI_STANDARD.stickyCellClassName,
    columnIndex === 1 ? DATA_TABLE_UI_STANDARD.stickyBoundaryClassName : undefined
  ];
}
