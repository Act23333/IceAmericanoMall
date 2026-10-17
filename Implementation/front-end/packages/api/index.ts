/**
 * @icedmall/api — 共享 API 层
 */
export * from './types';
export * from './hooks';
export * from './queries';
export { apiClient, ApiError } from './client';
export { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';
export { scheduleProactiveRefresh, clearProactiveRefresh, AUTH_LOGOUT_EVENT } from './client';
export { getUserMessage, ERROR_CODE } from './error-codes';
export type { ErrorCodeValue } from './error-codes';
export {
  isAuthError,
  requiresCaptcha,
  isRateLimited,
  isParamError,
  isThirdPartyError,
  /** 「资源不存在」判定（404 / `1103` / 存量 `3011` 并轨）—— SPU 详情 404 分支用它。 */
  isNotFound,
} from './error-codes';
export { getProducts, getProduct, getSpuByCode, getCategories, ServerFetchError } from './server';
