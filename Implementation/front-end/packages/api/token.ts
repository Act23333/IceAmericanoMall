/**
 * Token 存储 —— access/refresh Token 存于 JS 可读 Cookie。
 * 客户端据此注入 `Authorization: Bearer`，middleware 亦可读 cookie 做路由守卫。
 * SSR 环境（无 document）下所有操作为 no-op。
 *
 * 注：access_token 放 JS 可读 cookie 便于统一（client Bearer + middleware 守卫），
 * 存在 XSS 暴露面；生产可改为 httpOnly + BFF 代理。
 */
const ACCESS = 'access_token';
const REFRESH = 'refresh_token';
const ACCESS_MAX_AGE = 60 * 30; // 30min
const REFRESH_MAX_AGE = 60 * 60 * 24 * 7; // 7d

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const m = document.cookie.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]!) : undefined;
}

function writeCookie(name: string, value: string, maxAgeSec: number): void {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSec}; samesite=lax`;
}

function deleteCookie(name: string): void {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=; path=/; max-age=0`;
}

export const getAccessToken = (): string | undefined => readCookie(ACCESS);
export const getRefreshToken = (): string | undefined => readCookie(REFRESH);

export function setTokens(accessToken?: string, refreshToken?: string): void {
  if (accessToken) writeCookie(ACCESS, accessToken, ACCESS_MAX_AGE);
  if (refreshToken) writeCookie(REFRESH, refreshToken, REFRESH_MAX_AGE);
}

export function clearTokens(): void {
  deleteCookie(ACCESS);
  deleteCookie(REFRESH);
}
