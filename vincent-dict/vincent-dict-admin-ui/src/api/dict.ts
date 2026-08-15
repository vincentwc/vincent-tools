import { http, unwrap } from './http';
import type {
  AdminCapabilities,
  CreateDictPayload,
  CreateItemPayload,
  DictDetail,
  DictItemDetail,
  DictPageQuery,
  DictSummary,
  IdPayload,
  ItemPageQuery,
  PageResult,
  TenantOption,
  TenantPageQuery,
  UpdateDictPayload,
  UpdateItemPayload
} from './types';

export function pageDicts(query: DictPageQuery = {}): Promise<PageResult<DictSummary>> {
  return unwrap(http.get('/dicts', { params: query }));
}

export function createDict(payload: CreateDictPayload): Promise<IdPayload> {
  return unwrap(http.post('/dicts', payload));
}

export function getCapabilities(tenantId?: string): Promise<AdminCapabilities> {
  return unwrap(http.get('/capabilities', { params: tenantId ? { tenantId } : undefined }));
}

export function getDict(dictId: number, includeDeleted?: boolean): Promise<DictDetail> {
  return unwrap(http.get(`/dicts/${dictId}`, {
    params: includeDeleted === undefined ? undefined : { includeDeleted }
  }));
}

export function updateDict(dictId: number, payload: UpdateDictPayload): Promise<void> {
  return unwrap(http.put(`/dicts/${dictId}`, payload));
}

export function changeDictStatus(dictId: number, enabled: boolean): Promise<void> {
  return unwrap(http.patch(`/dicts/${dictId}/status`, { enabled }));
}

export function deleteDict(dictId: number): Promise<void> {
  return unwrap(http.delete(`/dicts/${dictId}`));
}

export function restoreDict(dictId: number): Promise<void> {
  return unwrap(http.post(`/dicts/${dictId}/restore`));
}

export function pageItems(dictId: number, query: ItemPageQuery = {}): Promise<PageResult<DictItemDetail>> {
  return unwrap(http.get(`/dicts/${dictId}/items`, { params: query }));
}

export function createDefaultItem(dictId: number, payload: CreateItemPayload): Promise<IdPayload> {
  return unwrap(http.post(`/dicts/${dictId}/items/default`, payload));
}

export function createTenantItem(
  dictId: number,
  tenantId: string,
  payload: CreateItemPayload
): Promise<IdPayload> {
  return unwrap(http.post(`/dicts/${dictId}/items/tenant`, { tenantId, ...payload }));
}

export function updateItem(itemId: number, payload: UpdateItemPayload): Promise<void> {
  return unwrap(http.put(`/items/${itemId}`, payload));
}

export function changeItemStatus(itemId: number, enabled: boolean): Promise<void> {
  return unwrap(http.patch(`/items/${itemId}/status`, { enabled }));
}

export function deleteItem(itemId: number): Promise<void> {
  return unwrap(http.delete(`/items/${itemId}`));
}

export function restoreItem(itemId: number): Promise<void> {
  return unwrap(http.post(`/items/${itemId}/restore`));
}

export function pageTenants(query: TenantPageQuery = {}): Promise<PageResult<TenantOption>> {
  return unwrap(http.get('/tenants', { params: query }));
}
