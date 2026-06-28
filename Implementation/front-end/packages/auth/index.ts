/**
 * @icedmall/auth — 认证模块
 *
 * 提供：
 * 1. JWT token 管理 (httpOnly Cookie + Zustand store)
 * 2. Next.js Middleware (路由守卫)
 * 3. 登录/注册/刷新 token API 调用
 */

export { useAuthStore } from './store';
export { authMiddleware } from './middleware';
export { login, register, refreshToken, logout } from './actions';
