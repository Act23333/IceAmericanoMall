/**
 * @icedmall/api — 共享 API 层
 */
export * from './types';
export * from './hooks';
export * from './queries';
export { apiClient, ApiError } from './client';
export { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';
export { scheduleProactiveRefresh, clearProactiveRefresh, AUTH_LOGOUT_EVENT } from './client';
export { getUserMessage, ERROR_CODE, LOGIN_FAILED_MESSAGE } from './error-codes';
export type { ErrorCodeValue } from './error-codes';
export {
  isAuthError,
  requiresCaptcha,
  isRateLimited,
  isParamError,
  isThirdPartyError,
  /** 「资源不存在」判定（404 / `1103` / 存量 `3011` 并轨）—— SPU 详情 404 分支用它。 */
  isNotFound,
  /** 「登录失败」判定（401 / `3007` / 存量 `3008`·`1017`）—— 登录表单失败分支用它。 */
  isLoginFailure,
  /** 登录失败的用户提示：**恒定中性**，绝不区分「账号不存在 / 密码错 / 被冻结」。 */
  getLoginFailureMessage,
} from './error-codes';
export { getProducts, getProduct, getSpuByCode, getCategories, ServerFetchError } from './server';
