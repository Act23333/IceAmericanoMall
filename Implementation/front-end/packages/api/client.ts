/**
 * API 客户端 — Fetch 封装
 *
 * 双策略无感刷新：
 *   1. 主动刷新：解 JWT exp，提前 2 分钟定时续期（setTimeout 调度）
 *   2. 被动刷新：401 拦截器 + 互斥锁（兜底）
 */
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';

// ==================== 刷新互斥锁 ====================
let refreshMutex: Promise<string | undefined> | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

export function resetRefreshMutex() { refreshMutex = null; }

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? '';
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

/** 全局登出事件名 — 布局层监听并跳转登录页 */
export const AUTH_LOGOUT_EVENT = 'auth:logout';

/** 刷新失败 → 清理凭证 + 广播全局登出事件（京东标准） */
function handleRefreshFailure() {
  clearTokens();
  clearProactiveRefresh();
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_LOGOUT_EVENT));
  }
}

async function doRefresh(): Promise<string | undefined> {
  const rt = getRefreshToken();
  if (!rt) {
    handleRefreshFailure();
    return undefined;
  }
  try {
    const res = await fetch(`${BASE_URL}/api/auth/refresh?refresh_token=${encodeURIComponent(rt)}`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
    if (!res.ok) {
      handleRefreshFailure();
      return undefined;
    }
    const body = await res.json().catch(() => null);
    if (!body || body.code !== 200 || !body.data?.access_token) {
      handleRefreshFailure();
      return undefined;
    }
    setTokens(body.data.access_token, body.data.refresh_token);
    return body.data.access_token as string;
  } catch {
    handleRefreshFailure();
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

/**
 * 401 **不**代表「令牌过期」的端点 —— 认证端点自身的 401 是「凭证不正确」（登录失败）。
 *
 * 为什么必须排除（2026-10-18 批次 ④）：登录失败的 HTTP 状态由 200 改为 **401**，
 * 若仍走下面的「401 → 刷新令牌 → 重试」被动兜底，会把**一次输错密码**变成：
 * ① 多发一次 `/api/auth/refresh`（无 refresh token → 直接失败）；
 * ② 触发 `handleRefreshFailure()` 清空本地凭证并广播 `auth:logout`；
 * ③ 布局层 `AuthLogoutListener` 据此 `router.replace('/auth/login?redirect=...')` ——
 *    登录页被无谓地重新导航，刚设好的错误文案可能被冲掉，观感是「密码输错 → 页面闪一下」。
 * 认证端点的 401 是**业务结果**，不是掉线，故不参与刷新与重放。
 */
const AUTH_ENDPOINTS_WITHOUT_REFRESH: readonly string[] = [
  '/api/auth/web/login',
  '/api/auth/register',
  '/api/auth/reset-password',
  '/api/auth/web/wechat/login',
  '/api/auth/wechat/mini/login',
  '/api/auth/refresh',
];

/** 该 401 是否应该尝试刷新令牌并重放（认证端点的 401 一律不重试）。 */
function shouldAttemptRefresh(path: string): boolean {
  return !AUTH_ENDPOINTS_WITHOUT_REFRESH.some((authPath) => path.startsWith(authPath));
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

  // 被动兜底：401 → 互斥刷新 → 重试（认证端点除外，见 AUTH_ENDPOINTS_WITHOUT_REFRESH）
  if (res.status === 401 && shouldAttemptRefresh(path)) {
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
