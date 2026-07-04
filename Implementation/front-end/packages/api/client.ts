/**
 * API 客户端 — Fetch 封装
 *
 * 自动: httpOnly Cookie, 解析 Result<T>, 错误转换
 */
const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.icedmall.com';

interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function apiClient<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...options?.headers,
    },
    credentials: 'include',
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(body.code || res.status, body.msg || `HTTP ${res.status}`);
  }

  const body: ApiResult<T> = await res.json();
  if (body.code !== 200) {
    throw new ApiError(body.code, body.msg);
  }

  return body.data;
}
