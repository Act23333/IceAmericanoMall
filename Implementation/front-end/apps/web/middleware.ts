/**
 * Web App Middleware
 *
 * 重新导出 @icedmall/auth 的路由守卫
 * 如需 app-specific 逻辑，在此文件扩展
 */
export { authMiddleware as middleware } from '@icedmall/auth';

// 匹配所有需要路由守卫的路径
export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|manifest.json).*)',
  ],
};
