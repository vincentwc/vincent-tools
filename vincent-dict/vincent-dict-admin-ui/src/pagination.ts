export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
export const PAGE_SIZES = [20, 50, 100];

export function clampPageSize(size: number): number {
  if (!Number.isFinite(size) || size < 1) {
    return DEFAULT_PAGE_SIZE;
  }
  return Math.min(Math.floor(size), MAX_PAGE_SIZE);
}
