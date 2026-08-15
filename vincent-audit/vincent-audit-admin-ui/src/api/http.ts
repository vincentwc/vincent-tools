import axios from 'axios';
import type { AxiosInstance, AxiosResponse } from 'axios';
import type { ApiResponse } from './types';

export const DEFAULT_API_PATH = '/vincent/audit/admin/api/v1';

export class ApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

export function resolveApiPath(): string {
  const apiPath = globalThis.window?.__VIN_AUDIT_CONFIG__?.apiPath;
  return apiPath && apiPath.length > 0 ? apiPath : DEFAULT_API_PATH;
}

export function createHttp(): AxiosInstance {
  return axios.create({
    baseURL: resolveApiPath()
  });
}

export const http = createHttp();

export async function unwrap<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  try {
    const response = await promise;
    const body = response.data;
    if (!body.success) {
      throw new ApiError(body.code, body.message);
    }
    return body.data;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (axios.isAxiosError(error)) {
      const body = error.response?.data as ApiResponse<unknown> | undefined;
      if (body && typeof body.code === 'string') {
        throw new ApiError(body.code, body.message ?? error.message);
      }
    }
    throw error;
  }
}
