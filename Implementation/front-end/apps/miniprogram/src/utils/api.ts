/**
 * 小程序 API 客户端 — Taro 原生实现
 *
 * 与 Web 端 @icedmall/api 的区别:
 *   1. 无 document.cookie → 使用 Taro.setStorageSync
 *   2. 无 fetch → 使用 Taro.request
 *   3. 开发环境自动跳过域名校验
 */

import Taro from '@tarojs/taro';

// ==================== 配置 ====================

/** 后端 API 地址 — 本机开发使用 localhost，真机调试改为局域网 IP */
const BASE_URL = 'http://localhost:8080';

/** Token 存储 key */
const TOKEN_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';
const USER_KEY = 'user_info';

// ==================== Token 管理 ====================

export function getAccessToken(): string {
  return Taro.getStorageSync(TOKEN_KEY) || '';
}

export function getRefreshToken(): string {
  return Taro.getStorageSync(REFRESH_KEY) || '';
}

export function setTokens(accessToken: string, refreshToken?: string) {
  Taro.setStorageSync(TOKEN_KEY, accessToken);
  if (refreshToken) Taro.setStorageSync(REFRESH_KEY, refreshToken);
}

export function clearTokens() {
  Taro.removeStorageSync(TOKEN_KEY);
  Taro.removeStorageSync(REFRESH_KEY);
  Taro.removeStorageSync(USER_KEY);
}

export function getSavedUser(): any {
  try {
    const raw = Taro.getStorageSync(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch { return null; }
}

export function saveUser(user: any) {
  Taro.setStorageSync(USER_KEY, JSON.stringify(user));
}

// ==================== API 错误 ====================

export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

// ==================== 请求封装 ====================

interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

let refreshing: Promise<string> | null = null;

async function doRefresh(): Promise<string> {
  const rt = getRefreshToken();
  if (!rt) throw new Error('无 refresh token');
  const res = await Taro.request({
    url: `${BASE_URL}/api/auth/refresh?refresh_token=${encodeURIComponent(rt)}`,
    method: 'POST',
    header: { 'Content-Type': 'application/json' },
  });
  const body = res.data as ApiResult<any>;
  if (body.code !== 200 || !body.data?.access_token) throw new Error('刷新失败');
  setTokens(body.data.access_token, body.data.refresh_token);
  return body.data.access_token;
}

export async function apiClient<T>(path: string, options?: {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  data?: any;
  header?: Record<string, string>;
}): Promise<T> {
  const method = options?.method || 'GET';
  const token = getAccessToken();

  const doRequest = (authToken?: string) =>
    Taro.request({
      url: `${BASE_URL}${path}`,
      method,
      data: options?.data,
      header: {
        'Content-Type': 'application/json',
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        ...options?.header,
      },
    });

  let res = await doRequest(token);

  // 401 → 刷新重试
  if (res.statusCode === 401 && getRefreshToken()) {
    if (!refreshing) {
      refreshing = doRefresh().finally(() => { refreshing = null; });
    }
    try {
      const newToken = await refreshing;
      res = await doRequest(newToken);
    } catch {
      clearTokens();
      throw new ApiError(40100, '登录已过期');
    }
  }

  if (res.statusCode !== 200) {
    const body = res.data as any;
    throw new ApiError(body?.code || res.statusCode, body?.msg || '请求失败');
  }

  const body = res.data as ApiResult<T>;
  if (body.code !== 200) throw new ApiError(body.code, body.msg);
  return body.data;
}
