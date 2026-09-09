/**
 * API 客户端 — Fetch 封装
 *
 * 双策略无感刷新：
 *   1. 主动刷新：解 JWT exp，提前 2 分钟定时续期（setTimeout 调度）
 *   2. 被动刷新：401 拦截器 + 互斥锁（兜底）
 */
import { getAccessToken, getRefreshToken, setTokens } from './token';

// ==================== 刷新互斥锁 ====================
let refreshMutex: Promise<string | undefined> | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

export function resetRefreshMutex() { refreshMutex = null; }

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.icedmall.com';
/** 提前多少毫秒触发主动刷新（默认 2 分钟） */
const REFRESH_AHEAD_MS = 2 * 60 * 1000;

// ==================== JWT 解码 ====================

/** 从 JWT payload 中读取 exp（秒级 Unix 时间戳）。 */
function jwtExp(token: string): number | undefined {
  try {
    const payload = token.split('.')[1];
    if (!payload) return undefined;
    const json = JSON.parse(atob(payload));
    return json.exp; // seconds since epoch
  } catch {
    return undefined;
  }
}

// ==================== 核心刷新逻辑 ====================

async function doRefresh(): Promise<string | undefined> {
  const rt = getRefreshToken();
  if (!rt) return undefined;
  try {
    const res = await fetch(`${BASE_URL}/api/auth/refresh?refresh_token=${encodeURIComponent(rt)}`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
    if (!res.ok) return undefined;
    const body = await res.json().catch(() => null);
    if (!body || body.code !== 200 || !body.data?.access_token) return undefined;
    setTokens(body.data.access_token, body.data.refresh_token);
    return body.data.access_token as string;
  } catch {
    return undefined;
  }
}

async function tryRefresh(): Promise<string | undefined> {
  if (refreshMutex) return refreshMutex;
  refreshMutex = (async () => {
    const token = await doRefresh();
    return token;
  })().finally(() => { refreshMutex = null; });
  return refreshMutex;
}

// ==================== 主动刷新调度 ====================

/** 根据 token 的 exp 调度下一次主动刷新（提前 REFRESH_AHEAD_MS）。 */
export function scheduleProactiveRefresh(token?: string) {
  if (refreshTimer) clearTimeout(refreshTimer);
  const t = token || getAccessToken();
  if (!t) return;
  const exp = jwtExp(t);
  if (!exp) return;
  const delay = Math.max(0, exp * 1000 - Date.now() - REFRESH_AHEAD_MS);
  if (delay <= 0) {
    // 已过期或即将过期 → 立即被动刷新（不阻塞，fire-and-forget）
    tryRefresh();
    return;
  }
  refreshTimer = setTimeout(async () => {
    const newToken = await doRefresh();
    if (newToken) scheduleProactiveRefresh(newToken); // 刷新成功 → 调度下一次
  }, delay);
}

/** 清除主动刷新定时器（logout 时调用）。 */
export function clearProactiveRefresh() {
  if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null; }
}

// ==================== API 客户端 ====================

interface ApiResult<T> { code: number; msg: string; data: T; }

export class ApiError extends Error {
  constructor(public code: number, message: string) { super(message); this.name = 'ApiError'; }
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

export async function apiClient<T>(path: string, options?: RequestInit): Promise<T> {
  const opts = options ? { ...options } : undefined;
  let res = await request(path, opts, getAccessToken());

  // 被动兜底：401 → 互斥刷新 → 重试
  if (res.status === 401) {
    const newToken = await tryRefresh();
    if (newToken) {
      res = await request(path, opts, newToken);
    }
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(body.code || res.status, body.msg || `HTTP ${res.status}`);
  }

  const body: ApiResult<T> = await res.json();
  if (body.code !== 200) throw new ApiError(body.code, body.msg);
  return body.data;
}
