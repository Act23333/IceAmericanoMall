/**
 * 认证操作 — 登录 / 注册 / 刷新 / 登出
 *
 * 路径与字段以后端 `authorization-service/adapter/web/AuthController.java` 为准：
 *   - A1 `POST /api/auth/web/login`（★ 不是 `/api/auth/login`）
 *   - A2 `POST /api/auth/register`
 *   - A3 `POST /api/auth/refresh?refresh_token=`
 *   - A4 `POST /api/auth/logout?refresh_token=`
 *
 * 请求体 `WebLoginReq` / `RegisterReq` 无 `@JsonProperty`，故全平台 SNAKE_CASE
 * 策略下键名为 `identity_type` / `credential_type` / `device_id`。
 * `refresh_token` 是 query 参数（由 `@RequestParam` 决定），保持 snake_case。
 */

import { apiClient, setTokens, clearTokens, scheduleProactiveRefresh } from '@icedmall/api';
import type { LoginReq, RegisterReq, LoginResp, OAuth2TokenResp } from '@icedmall/api';

/** 登录 — `POST /api/auth/web/login`（密码 / 短信码 / 邮箱码由 `credential_type` 路由）。 */
export async function login(req: LoginReq): Promise<LoginResp> {
  const resp = await apiClient<LoginResp>('/api/auth/web/login', {
    method: 'POST',
    body: JSON.stringify(req),
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 注册（注册即登录）— `POST /api/auth/register`。 */
export async function register(req: RegisterReq): Promise<LoginResp> {
  const resp = await apiClient<LoginResp>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      ...req,
      device_id: req.device_id || 'web',
    }),
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 刷新 Token — `POST /api/auth/refresh?refresh_token=xxx`；返回 `OAuth2TokenResp`（不含 user_id）。 */
export async function refreshToken(token: string): Promise<OAuth2TokenResp> {
  const resp = await apiClient<OAuth2TokenResp>(`/api/auth/refresh?refresh_token=${encodeURIComponent(token)}`, {
    method: 'POST',
  });
  setTokens(resp.access_token, resp.refresh_token);
  scheduleProactiveRefresh(resp.access_token);
  return resp;
}

/** 登出 — `POST /api/auth/logout?refresh_token=xxx`（成功后当前 access token 按 jti 入网关黑名单）。 */
export async function logout(token: string): Promise<void> {
  try {
    await apiClient(`/api/auth/logout?refresh_token=${encodeURIComponent(token)}`, {
      method: 'POST',
    });
  } finally {
    clearTokens();
  }
}
