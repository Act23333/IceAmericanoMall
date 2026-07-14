/**
 * API 客户端 — Fetch 封装
 *
 * 自动: 注入 Authorization: Bearer（来自 token cookie）+ 携带 Cookie，
 *       401 时用 refresh_token 换一次新 token 后重试，解析 Result<T>，错误转换。
 */
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';

// 刷新互斥锁：防止并发 401 同时触发多次 refresh
let refreshMutex: Promise<string | undefined> | null = null;

/** 显式登出时重置互斥锁。 */
export function resetRefreshMutex() { refreshMutex = null; }

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

async function tryRefresh(): Promise<string | undefined> {
  if (refreshMutex) return refreshMutex; // 已有刷新进行中，复用等待

  refreshMutex = (async () => {
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
    } finally {
      refreshMutex = null; // 释放锁
    }
  })();

  return refreshMutex;
}

export async function apiClient<T>(path: string, options?: RequestInit): Promise<T> {
  // 防御：确保 POST/PUT/PATCH 的 body 不丢失（尤其是 401 重试路径）
  const opts = options ? { ...options } : undefined;
  let res = await request(path, opts, getAccessToken());

  // access token 过期 → 刷新一次后重试
  if (res.status === 401) {
    const newToken = await tryRefresh();
    if (newToken) {
      res = await request(path, opts, newToken);
    }
    // 刷新失败不删 refresh_token（可能是网络抖动），只等显式 logout 才清
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
