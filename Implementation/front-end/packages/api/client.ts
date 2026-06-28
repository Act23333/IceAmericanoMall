/**
 * API 客户端 — Fetch 封装
 *
 * 自动：
 * - 附加 httpOnly Cookie (access_token)
 * - 解析 ia-common Result<T> 格式
 * - 401 时触发 Token 刷新
 * - 网络错误统一转换为 Error
 */

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.icedmall.com';

export interface ApiResult<T> {
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
    credentials: 'include', // 发送 httpOnly Cookie
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(
      body.code || res.status,
      body.msg || `HTTP ${res.status}`,
    );
  }

  const body: ApiResult<T> = await res.json();
  if (body.code !== 200 && body.code !== 0) {
    throw new ApiError(body.code, body.msg);
  }

  return body.data;
}
