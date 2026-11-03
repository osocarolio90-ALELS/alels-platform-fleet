export function normalizeBleMac(value: string | null | undefined): string | null {
  const input = value?.trim() ?? "";
  if (!/^([\da-f]{12}|([\da-f]{2}:){5}[\da-f]{2}|([\da-f]{2}-){5}[\da-f]{2})$/i.test(input)) return null;
  return input.replace(/[:-]/g, "").toUpperCase().match(/.{2}/g)!.join(":");
}
