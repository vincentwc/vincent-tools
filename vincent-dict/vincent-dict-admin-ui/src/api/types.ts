export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface IdPayload {
  id: number;
}

export interface DictSummary {
  id: number;
  code: string;
  name: string;
  description: string;
  enabled: boolean;
  sortNo: number;
  deleted: boolean;
}

export interface DictDetail extends DictSummary {
  version: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export type DictItemSource = 'DEFAULT' | 'TENANT';

export interface DictItemDetail {
  id: number;
  dictId: number;
  code: string;
  name: string;
  tenantId: string | null;
  description: string;
  enabled: boolean;
  sortNo: number;
  deleted: boolean;
  source: DictItemSource;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface AdminCapabilities {
  tenantDirectoryAvailable: boolean;
  permissions: Record<string, boolean>;
}

export interface TenantOption {
  tenantId: string;
  name: string;
}

export interface DictPageQuery {
  code?: string;
  name?: string;
  enabled?: boolean;
  includeDeleted?: boolean;
  page?: number;
  size?: number;
}

export interface ItemPageQuery {
  tenantId?: string;
  code?: string;
  name?: string;
  enabled?: boolean;
  includeDeleted?: boolean;
  page?: number;
  size?: number;
}

export interface TenantPageQuery {
  keyword?: string;
  tenantId?: string;
  page?: number;
  size?: number;
}

export interface CreateDictPayload {
  code: string;
  name: string;
  description?: string;
  sortNo?: number;
}

export interface UpdateDictPayload {
  name: string;
  description?: string;
  sortNo?: number;
}

export interface CreateItemPayload {
  code: string;
  name: string;
  description?: string;
  sortNo?: number;
}

export interface UpdateItemPayload {
  name: string;
  description?: string;
  sortNo?: number;
}
