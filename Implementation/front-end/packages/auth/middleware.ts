import { NextRequest, NextResponse } from 'next/server';

/**
 * Next.js Middleware — 路由守卫
 *
 * 配置在 apps/web/middleware.ts 中引入：
 *   export { authMiddleware as middleware } from '@icedmall/auth';
 *
 * 保护路径：
 *   /cart, /checkout, /orders, /profile, /seller, /admin
 *
 * Token 逻辑：
 *   1. 从 Cookie 读取 access_token
 *   2. 验证 JWT 签名 (RS256 公钥)
 *   3. 通过 → 放行
 *   4. 过期 → 用 refresh_token 换新
 *   5. 失败 → 302 重定向到 /auth/login
 */

const PROTECTED_PATHS = [
  '/cart',
  '/checkout',
  '/orders',
  '/profile',
  '/addresses',
  '/seller',
  '/admin',
];

const PUBLIC_PATHS = [
  '/auth/login',
  '/auth/register',
  '/',
  '/products',
  '/categories',
];

export function authMiddleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 静态资源不拦截
  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/api') ||
    pathname.startsWith('/icons') ||
    pathname.includes('.')
  ) {
    return NextResponse.next();
  }

  const isProtected = PROTECTED_PATHS.some((p) => pathname.startsWith(p));
  const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  // 公开路径直接放行
  if (!isProtected) {
    // 已登录用户访问 login → 重定向到首页
    if (pathname.startsWith('/auth') && request.cookies.get('access_token')) {
      return NextResponse.redirect(new URL('/', request.url));
    }
    return NextResponse.next();
  }

  // 受保护路径 — 检查 token
  const accessToken = request.cookies.get('access_token')?.value;

  if (!accessToken) {
    const loginUrl = new URL('/auth/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Token 存在 — 放行（Gateway/JWT filter 会验证）
  // 如果 Token 过期，后端返回 401 → 客户端拦截器触发 refresh
  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|manifest.json).*)',
  ],
};
