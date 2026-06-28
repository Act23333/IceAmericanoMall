/**
 * 认证操作 — 登录/注册/刷新/登出
 */

import { apiClient, ApiError } from '@icedmall/api';

interface LoginReq {
  identity: string; // 手机号 / 用户名
  credential: string; // 密码 / 验证码
  identityType: 'PHONE' | 'USERNAME';
  credentialType: 'PASSWORD' | 'SMS_CODE';
}

interface LoginResp {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export async function login(req: LoginReq): Promise<LoginResp> {
  return apiClient<LoginResp>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

interface RegisterReq {
  phone: string;
  password: string;
  smsCode: string;
  nickname?: string;
}

export async function register(req: RegisterReq): Promise<LoginResp> {
  return apiClient<LoginResp>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function refreshToken(): Promise<LoginResp> {
  return apiClient<LoginResp>('/api/auth/refresh', {
    method: 'POST',
  });
}

export async function logout(): Promise<void> {
  await apiClient('/api/auth/logout', { method: 'POST' });
}
