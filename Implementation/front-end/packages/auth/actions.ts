/**
 * 认证操作 — 登录/注册/刷新/登出
 * 与后端 AuthController + OAuth2TokenResp (@JsonProperty snake_case) 对齐
 */

import { apiClient, setTokens, clearTokens, scheduleProactiveRefresh } from '@icedmall/api';
import type { LoginReq, RegisterReq, LoginResp } from '@icedmall/api';

/** 登录 — POST /api/auth/login */
export async function login(req: LoginReq): Promise<LoginResp> {
  const resp = await apiClient<LoginResp>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(req),
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 注册 — POST /api/auth/register */
export async function register(req: RegisterReq): Promise<LoginResp> {
  const resp = await apiClient<LoginResp>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      ...req,
      deviceId: req.deviceId || 'web',
    }),
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 刷新 Token — POST /api/auth/refresh?refresh_token=xxx */
export async function refreshToken(token: string): Promise<LoginResp> {
  const resp = await apiClient<LoginResp>(`/api/auth/refresh?refresh_token=${encodeURIComponent(token)}`, {
    method: 'POST',
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 登出 — POST /api/auth/logout?refresh_token=xxx */
export async function logout(token: string): Promise<void> {
  try {
    await apiClient(`/api/auth/logout?refresh_token=${encodeURIComponent(token)}`, {
      method: 'POST',
    });
  } finally {
    clearTokens();
  }
}
