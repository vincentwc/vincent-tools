import type { AdminCapabilities } from './api/types';

export function hasPermission(capabilities: AdminCapabilities | null, name: string): boolean {
  return capabilities?.permissions[name] === true;
}
