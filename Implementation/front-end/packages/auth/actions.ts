/**
 * 认证操作 — 登录/注册/刷新/登出
 * 与后端 AuthController + OAuth2TokenResp (@JsonProperty snake_case) 对齐
 */

import { apiClient } from '@icedmall/api';
import type { LoginReq, RegisterReq, LoginResp } from '@icedmall/api';

/** 登录 — POST /api/auth/login */
export async function login(req: LoginReq): Promise<LoginResp> {
  return apiClient<LoginResp>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

/** 注册 — POST /api/auth/register */
export async function register(req: RegisterReq): Promise<LoginResp> {
  return apiClient<LoginResp>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

/** 刷新 Token — POST /api/auth/refresh?refresh_token=xxx */
export async function refreshToken(token: string): Promise<LoginResp> {
  return apiClient<LoginResp>(`/api/auth/refresh?refresh_token=${encodeURIComponent(token)}`, {
    method: 'POST',
  });
}

/** 登出 — POST /api/auth/logout?refresh_token=xxx */
export async function logout(token: string): Promise<void> {
  await apiClient(`/api/auth/logout?refresh_token=${encodeURIComponent(token)}`, {
    method: 'POST',
  });
}
