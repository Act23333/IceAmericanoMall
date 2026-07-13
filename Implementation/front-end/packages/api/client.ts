/**
 * API 客户端 — Fetch 封装
 *
 * 自动: 注入 Authorization: Bearer（来自 token cookie）+ 携带 Cookie，
 *       401 时用 refresh_token 换一次新 token 后重试，解析 Result<T>，错误转换。
 */
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';

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

function request(path: string, options?: RequestInit, token?: string): Promise<Response> {
  return fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
    credentials: 'include',
  });
}

/** 用 refresh_token 换新 access_token；成功返回新 token 并写入 cookie，失败返回 undefined。 */
async function tryRefresh(): Promise<string | undefined> {
  const rt = getRefreshToken();
  if (!rt) return undefined;
  try {
    const res = await request(`/api/auth/refresh?refresh_token=${encodeURIComponent(rt)}`, { method: 'POST' });
    if (!res.ok) return undefined;
    const body = await res.json().catch(() => null);
    if (!body || body.code !== 200 || !body.data?.access_token) return undefined;
    setTokens(body.data.access_token, body.data.refresh_token);
    return body.data.access_token as string;
  } catch {
    return undefined;
  }
}

export async function apiClient<T>(path: string, options?: RequestInit): Promise<T> {
  let res = await request(path, options, getAccessToken());

  // access token 过期 → 刷新一次后重试
  if (res.status === 401) {
    const newToken = await tryRefresh();
    if (newToken) {
      res = await request(path, options, newToken);
    } else {
      clearTokens();
    }
  }

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
