import { AxiosError, AxiosHeaders } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn()
}));

const axiosCreate = vi.hoisted(() => vi.fn(() => http));

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios');
  return {
    ...actual,
    default: Object.assign(actual.default, {
      create: axiosCreate
    })
  };
});

function ok<T>(data: T) {
  return Promise.resolve({
    data: { success: true, code: 'OK', message: 'OK', data }
  });
}

function apiAxiosError(code: string, message: string, status = 409) {
  return new AxiosError(
    message,
    AxiosError.ERR_BAD_REQUEST,
    { headers: new AxiosHeaders() } as InternalAxiosRequestConfig,
    undefined,
    {
      status,
      statusText: 'Error',
      headers: {},
      config: { headers: new AxiosHeaders() } as InternalAxiosRequestConfig,
      data: { success: false, code, message, data: null }
    }
  );
}

describe('typed dict admin client', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    axiosCreate.mockImplementation(() => http);
    Reflect.deleteProperty(globalThis, 'window');
  });

  it('uses injected apiPath as axios base URL', async () => {
    vi.stubGlobal('window', { __VIN_DICT_CONFIG__: { apiPath: '/custom/api' } });
    await import('./dict');
    expect(axiosCreate).toHaveBeenCalledWith(expect.objectContaining({
      baseURL: '/custom/api'
    }));
  });

  it('falls back to default admin api path', async () => {
    await import('./dict');
    expect(axiosCreate).toHaveBeenCalledWith(expect.objectContaining({
      baseURL: '/vincent/dict/admin/api/v1'
    }));
  });

  it('createTenantItem posts tenantId with payload to tenant items route', async () => {
    http.post.mockResolvedValue(ok({ id: 201 }));
    const { createTenantItem } = await import('./dict');
    const payload = { code: 'WAIT_PAY', name: 'Pay', sortNo: 21 };
    const result = await createTenantItem(10, 'tenant-b', payload);
    expect(http.post).toHaveBeenCalledWith('/dicts/10/items/tenant', {
      tenantId: 'tenant-b',
      ...payload
    });
    expect(result).toEqual({ id: 201 });
  });

  it('restoreDict posts to exact dict restore route', async () => {
    http.post.mockResolvedValue(ok(null));
    const { restoreDict } = await import('./dict');
    await restoreDict(11);
    expect(http.post).toHaveBeenCalledWith('/dicts/11/restore');
  });

  it('restoreItem posts to exact item restore route', async () => {
    http.post.mockResolvedValue(ok(null));
    const { restoreItem } = await import('./dict');
    await restoreItem(92);
    expect(http.post).toHaveBeenCalledWith('/items/92/restore');
  });

  it('preserves component error code for UI messages', async () => {
    http.post.mockRejectedValue(apiAxiosError('DICT_CODE_CONFLICT', 'code already exists'));
    const { createDict } = await import('./dict');
    await expect(createDict({ code: 'ORDER_STATUS', name: 'Dup' })).rejects.toMatchObject({
      name: 'ApiError',
      code: 'DICT_CODE_CONFLICT',
      message: 'code already exists'
    });
  });

  it('covers remaining admin endpoints', async () => {
    http.get.mockResolvedValue(ok({ items: [], total: 0, page: 1, size: 20 }));
    http.post.mockResolvedValue(ok({ id: 100 }));
    http.put.mockResolvedValue(ok(null));
    http.patch.mockResolvedValue(ok(null));
    http.delete.mockResolvedValue(ok(null));

    const api = await import('./dict');

    await api.pageDicts({ code: 'ORDER', name: 'Order', enabled: true, includeDeleted: false, page: 1, size: 20 });
    expect(http.get).toHaveBeenCalledWith('/dicts', {
      params: { code: 'ORDER', name: 'Order', enabled: true, includeDeleted: false, page: 1, size: 20 }
    });

    await api.createDict({ code: 'ORDER_TYPE', name: 'Order type', description: '', sortNo: 1 });
    expect(http.post).toHaveBeenCalledWith('/dicts', {
      code: 'ORDER_TYPE', name: 'Order type', description: '', sortNo: 1
    });

    await api.getCapabilities('tenant-b');
    expect(http.get).toHaveBeenCalledWith('/capabilities', { params: { tenantId: 'tenant-b' } });

    await api.getDict(10, true);
    expect(http.get).toHaveBeenCalledWith('/dicts/10', { params: { includeDeleted: true } });

    await api.updateDict(10, { name: 'Order lifecycle', description: 'Lifecycle', sortNo: 30 });
    expect(http.put).toHaveBeenCalledWith('/dicts/10', {
      name: 'Order lifecycle', description: 'Lifecycle', sortNo: 30
    });

    await api.changeDictStatus(10, false);
    expect(http.patch).toHaveBeenCalledWith('/dicts/10/status', { enabled: false });

    await api.deleteDict(10);
    expect(http.delete).toHaveBeenCalledWith('/dicts/10');

    await api.pageItems(10, { tenantId: 'tenant-b', code: 'WAIT', page: 1, size: 20 });
    expect(http.get).toHaveBeenCalledWith('/dicts/10/items', {
      params: { tenantId: 'tenant-b', code: 'WAIT', page: 1, size: 20 }
    });

    await api.createDefaultItem(10, { code: 'WAIT_CONFIRM', name: 'Waiting', sortNo: 20 });
    expect(http.post).toHaveBeenCalledWith('/dicts/10/items/default', {
      code: 'WAIT_CONFIRM', name: 'Waiting', sortNo: 20
    });

    await api.updateItem(90, { name: 'Waiting confirm', description: 'Updated', sortNo: 25 });
    expect(http.put).toHaveBeenCalledWith('/items/90', {
      name: 'Waiting confirm', description: 'Updated', sortNo: 25
    });

    await api.changeItemStatus(90, false);
    expect(http.patch).toHaveBeenCalledWith('/items/90/status', { enabled: false });

    await api.deleteItem(91);
    expect(http.delete).toHaveBeenCalledWith('/items/91');

    await api.pageTenants({ keyword: 'ten', tenantId: 'tenant-b', page: 1, size: 20 });
    expect(http.get).toHaveBeenCalledWith('/tenants', {
      params: { keyword: 'ten', tenantId: 'tenant-b', page: 1, size: 20 }
    });
  });
});
