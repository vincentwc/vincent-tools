import { describe, expect, it, vi } from 'vitest';
import { searchRecords } from './audit';
import { http } from './http';

vi.mock('./http', () => ({
  http: {
    get: vi.fn()
  },
  unwrap: vi.fn(async (promise) => {
    const response = await promise;
    const body = response.data;
    if (!body.success) {
      throw new Error(body.message);
    }
    return body.data;
  }),
  DEFAULT_API_PATH: '/vincent/audit/admin/api/v1',
  ApiError: class ApiError extends Error {
    code: string;
    constructor(code: string, message: string) {
      super(message);
      this.code = code;
    }
  },
  resolveApiPath: () => '/vincent/audit/admin/api/v1',
  createHttp: () => http
}));

describe('searchRecords', () => {
  it('calls GET /records with query params', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        success: true,
        code: 'OK',
        message: '',
        data: { items: [], total: 0, page: 1, size: 20 }
      }
    });

    await searchRecords({ tenantId: 'tenant-a', action: 'UPDATE', page: 1, size: 20 });

    expect(http.get).toHaveBeenCalledWith('/records', {
      params: { tenantId: 'tenant-a', action: 'UPDATE', page: 1, size: 20 }
    });
  });
});
