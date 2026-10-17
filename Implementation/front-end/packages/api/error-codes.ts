/**
 * 后端 `ErrorCode` 枚举 → 前端提示文案 + 分流判定
 *
 * 单一事实来源:
 *   Implementation/back-end/ia-common/src/main/java/org/icedamericanomall/common/enums/ErrorCode.java
 *   （共 99 个码；本文件与之逐条对齐，码值与文案同源）
 * 契约参考: project-docs/contract/05-API-Specification.md §二（错误码体系）
 *
 * ── 报文约定 ──────────────────────────────────────────────────
 * 业务码 3000-3042 的 HTTP 状态为 **200**，即「业务失败 ≠ 传输失败」；
 * 前端必须按 body 内 `code` 分流，不能只看 `res.ok`（`apiClient` 已实现这一点：
 * `body.code !== 200` 时抛 `ApiError`）。
 *
 * ⚠️ **例外一：登录失败是 401，不是 200**（2026-10-18 批次 ④，抗账号枚举）。
 *   `3007 PASSWORD_ERROR`（登录失败唯一对外码）与 `3008 USER_STATUS_ABNORMAL` 的 HTTP 状态
 *   由 200 纠正为 **401**：登录失败若用 200 承载，它与「账号不存在」的 404 之间的差异
 *   就是账号枚举信道。故「登录失败」判定必须同时认 **HTTP 401** 与 **code 3007**
 *   （见 `isLoginFailure()` / `getLoginFailureMessage()`），且**文案恒定中性**。
 *
 * ⚠️ **例外二：1xxx 的 HTTP 状态是有意义的**（`ErrorCode` 枚举第二参已定死状态码）。
 *   其中 **`NO_RESOURCE_FOUND(1103)` = 真 HTTP 404** —— 自 2026-10-18 B8 起，SPU 详情
 *   `GET /api/item/spu/{spuId}`（P2）与 P38 短码端点**不再**用「200 信封 + 业务码」承载
 *   「商品不存在」，改为真 404。故「商品不存在」判定必须同时认 **HTTP 404**（见
 *   `isNotFound()`），只按 `body.code` 分流会把这类响应当成通用传输错误。
 *
 * ── 码段划分 ──────────────────────────────────────────────────
 *   1xxx  系统 / 认证 / 参数（HTTP 状态有意义：401/403/429/400/404）
 *   3xxx  业务逻辑（HTTP 200；**登录失败 3007/3008 为 401，见例外一**）
 *   31xx  AI 专项
 *   4xxx  第三方接口（按厂商分段）
 *   5xxx  加解密工具
 */

/** 具名错误码常量（值 = 后端 `ErrorCode.code`）。 */
export const ERROR_CODE = {
  // ---- 系统级 ----
  INTERNAL_ERROR: 1000,
  SYSTEM_BUSY: 1001,

  // ---- 认证 / 授权 ----
  UNAUTHORIZED: 1011,
  TOKEN_EXPIRED: 1012,
  FORBIDDEN: 1013,
  /** 流控统一出口：@RateLimit / 网关限流 / 验证码尝试过频一律 1014（不另设限流码）。 */
  FREQUENT_ERROR: 1014,
  ILLEGAL_REQUEST: 1015,
  CAPTCHA_REQUIRED: 1016,
  ACCOUNT_FROZEN: 1017,

  // ---- 参数 / 资源 ----
  PARAM_ERROR: 1101,
  USER_NOT_FOUND: 1102,
  NO_RESOURCE_FOUND: 1103,

  // ---- 业务：通用 ----
  BUSINESS_EXECUTION_EXCEPTION: 3000,
  CAPTCHA_ERROR: 3001,
  CAPTCHA_EXPIRED: 3002,
  PHONE_ALREADY_REGISTERED: 3003,
  USERNAME_ALREADY_TAKEN: 3004,
  BALANCE_INSUFFICIENT: 3005,
  USER_ALREADY_EXISTS: 3006,
  PASSWORD_ERROR: 3007,
  USER_STATUS_ABNORMAL: 3008,

  // ---- 业务：交易 / 订单 / 商品 (3009-3019) ----
  ORDER_NOT_FOUND: 3009,
  ORDER_STATUS_INVALID: 3010,
  /**
   * ⚠️ **已废弃（后端 javadoc 标 `@deprecated`）**：SPU 不存在**不再用本码** ——
   * 自 2026-10-18（后端 `265543d6`，B8）起 P2 / P38 统一抛
   * `NO_RESOURCE_FOUND(1103)` + **真 HTTP 404**。
   *
   * 保留枚举项的原因：① 后端 `ErrorCode` 枚举仍在（仅兼容存量引用，禁止新增使用），
   * 本文件与之逐条对齐才能通过 `scripts/check-error-codes.mjs`；② 判定侧走
   * `isNotFound()`（**1103 与 3011 并轨**），旧报文若仍以 200+3011 出现也照常识别为「不存在」。
   * 前端**不得**在新代码里把它当作「商品不存在」的判据 —— 用 `isNotFound()`。
   */
  PRODUCT_NOT_FOUND: 3011,
  STOCK_INSUFFICIENT: 3012,
  CART_EMPTY: 3013,
  CROSS_STORE_FORBIDDEN: 3014,
  SHOP_NOT_FOUND: 3015,
  SKU_NOT_FOUND: 3016,
  HOME_CONFIG_NOT_FOUND: 3017,
  REVIEW_ALREADY_EXISTS: 3018,
  COUPON_NOT_FOUND: 3019,

  // ---- 业务：秒杀 (3020-3024) ----
  FLASH_SALE_NOT_FOUND: 3020,
  FLASH_SALE_NOT_STARTED: 3021,
  FLASH_SALE_SOLD_OUT: 3022,
  FLASH_SALE_LIMIT_EXCEEDED: 3023,
  FLASH_SALE_FAILED: 3024,

  // ---- 业务：售后 / 申请 / 通知 / 评价 / 运费 (3030-3042) ----
  APPLICATION_EXISTS: 3030,
  AFTER_SALE_EXISTS: 3031,
  APPLICATION_NOT_FOUND: 3032,
  WITHDRAWAL_NOT_FOUND: 3033,
  NOTIFICATION_NOT_FOUND: 3034,
  LOGISTICS_NOT_FOUND: 3035,
  REVIEW_NOT_FOUND: 3036,
  REVIEW_ALREADY_REPLIED: 3037,
  REVIEW_APPEND_CONFLICT: 3038,
  REFUND_STATUS_INVALID: 3039,
  FREIGHT_TEMPLATE_NAME_EXISTS: 3040,
  FREIGHT_TEMPLATE_NOT_FOUND: 3041,
  /** 购物车条目数上限（C2 `POST /api/cart/item`；HTTP 200 信封）。B8 补齐（后端枚举已有，前端漏登记）。 */
  CART_ITEM_LIMIT_EXCEEDED: 3042,

  // ---- AI 专项 (3100-3104) ----
  AI_SERVICE_UNAVAILABLE: 3100,
  AI_TIMEOUT: 3101,
  AI_CONTENT_FILTERED: 3102,
  AI_RATE_LIMITED: 3103,
  AI_CONVERSATION_NOT_FOUND: 3104,

  // ---- 第三方：通用 (4000-4008) ----
  THIRD_PARTY_ERROR: 4000,
  THIRD_PARTY_TIMEOUT: 4001,
  THIRD_PARTY_CONNECT_FAILED: 4002,
  THIRD_PARTY_RATE_LIMITED: 4003,
  THIRD_PARTY_AUTH_FAILED: 4004,
  THIRD_PARTY_SIGN_ERROR: 4005,
  THIRD_PARTY_PARAM_ERROR: 4006,
  THIRD_PARTY_RESPONSE_PARSE_ERROR: 4007,
  THIRD_PARTY_UNKNOWN_ERROR: 4008,

  // ---- 第三方：微信 (4010-4018) ----
  WECHAT_ERROR: 4010,
  WECHAT_CODE_INVALID: 4011,
  WECHAT_AUTH_FAILED: 4012,
  WECHAT_OPENID_NOT_FOUND: 4013,
  WECHAT_PAY_ERROR: 4014,
  WECHAT_PAY_SIGN_ERROR: 4015,
  WECHAT_PAY_ORDER_NOT_FOUND: 4016,
  WECHAT_REFUND_ERROR: 4017,
  WECHAT_REFUND_AMOUNT_ERROR: 4018,

  // ---- 第三方：支付宝 (4020-4025) ----
  ALIPAY_ERROR: 4020,
  ALIPAY_AUTH_FAILED: 4021,
  ALIPAY_PAY_ERROR: 4022,
  ALIPAY_SIGN_ERROR: 4023,
  ALIPAY_REFUND_ERROR: 4024,
  ALIPAY_TRADE_NOT_FOUND: 4025,

  // ---- 第三方：短信 / 邮件 (4030-4035) ----
  SMS_SEND_FAILED: 4030,
  SMS_RATE_LIMITED: 4031,
  SMS_TEMPLATE_ERROR: 4032,
  SMS_SIGN_ERROR: 4033,
  SMS_BALANCE_INSUFFICIENT: 4034,
  EMAIL_SEND_FAILED: 4035,

  // ---- 第三方：OSS / 文件 (4040-4045) ----
  OSS_UPLOAD_FAILED: 4040,
  OSS_DOWNLOAD_FAILED: 4041,
  OSS_DELETE_FAILED: 4042,
  OSS_FILE_NOT_FOUND: 4043,
  OSS_FILE_TOO_LARGE: 4044,
  OSS_FILE_TYPE_NOT_ALLOWED: 4045,

  // ---- 第三方：地图 / 物流 (4050-4053) ----
  MAP_SERVICE_ERROR: 4050,
  MAP_GEOCODE_FAILED: 4051,
  LOGISTICS_QUERY_FAILED: 4052,
  LOGISTICS_COMPANY_NOT_FOUND: 4053,

  THIRD_PARTY_UNDEFINED: 4999,

  // ---- 加解密工具 (5000-5002) ----
  ENCRYPT_ERROR: 5000,
  DECRYPT_ERROR: 5001,
  HASH_ERROR: 5002,
} as const;

export type ErrorCodeValue = (typeof ERROR_CODE)[keyof typeof ERROR_CODE];

/**
 * 登录失败的**唯一**中性文案（2026-10-18 批次 ④，抗账号枚举）。
 *
 * 后端把「账号不存在 / 密码错 / 账号状态不可登录 / 风控冻结」四类原因收敛为同一响应
 * （HTTP `401` + `code 3007` + 中性 `msg`），前端**不得**再按 `code` 分支提示
 * 「该账号不存在」「密码错误」「账号被冻结」——任一可区分文案都是账号枚举信号。
 * 因此 `3007 / 3008 / 1017` 共用本常量，登录页也统一用它（见 `getLoginFailureMessage`）。
 */
export const LOGIN_FAILED_MESSAGE = '账号或密码不正确';

/** 错误码 → 用户可读中文消息（文案与后端 `ErrorCode.message` 对齐，可按体验微调）。 */
const ERROR_MAP: Record<number, string> = {
  // ---- 系统级 ----
  [ERROR_CODE.INTERNAL_ERROR]: '服务器繁忙，请稍后重试',
  [ERROR_CODE.SYSTEM_BUSY]: '系统繁忙，请稍后重试',

  // ---- 认证 / 授权（需分流：1011/1012 跳登录，1016 触发人机验证） ----
  [ERROR_CODE.UNAUTHORIZED]: '请先登录后再操作',
  [ERROR_CODE.TOKEN_EXPIRED]: '登录已过期，请重新登录',
  [ERROR_CODE.FORBIDDEN]: '无权限执行该操作',
  [ERROR_CODE.FREQUENT_ERROR]: '操作过于频繁，请稍后再试',
  [ERROR_CODE.ILLEGAL_REQUEST]: '非法请求',
  [ERROR_CODE.CAPTCHA_REQUIRED]: '需要完成人机验证',
  // ⚠ 1017 自 2026-10-18（批次 ④）起已退役：登录链路改抛 3007（见下），本码零生产引用。
  //   文案与 3007 同口径，防止误用时再次暴露「账号被冻结」这一可区分信号。
  [ERROR_CODE.ACCOUNT_FROZEN]: LOGIN_FAILED_MESSAGE,

  // ---- 参数 / 资源 ----
  [ERROR_CODE.PARAM_ERROR]: '请检查填写内容是否正确',
  [ERROR_CODE.USER_NOT_FOUND]: '用户不存在',
  [ERROR_CODE.NO_RESOURCE_FOUND]: '请求的资源不存在',

  // ---- 业务：通用 ----
  [ERROR_CODE.BUSINESS_EXECUTION_EXCEPTION]: '操作未能完成，请稍后重试',
  [ERROR_CODE.CAPTCHA_ERROR]: '验证码错误',
  [ERROR_CODE.CAPTCHA_EXPIRED]: '验证码已过期，请重新获取',
  [ERROR_CODE.PHONE_ALREADY_REGISTERED]: '该手机号已被注册',
  [ERROR_CODE.USERNAME_ALREADY_TAKEN]: '该用户名已被占用',
  [ERROR_CODE.BALANCE_INSUFFICIENT]: '账户余额不足，请更换支付方式',
  [ERROR_CODE.USER_ALREADY_EXISTS]: '请勿重复提交',
  // ⚠ 3007 / 3008 同为「登录失败」对外口径（2026-10-18 批次 ④）：文案必须中性且一致 ——
  //   不得区分「账号不存在 / 密码错 / 账号被冻结」，否则任意一处文案差异都会变成账号枚举信号。
  [ERROR_CODE.PASSWORD_ERROR]: LOGIN_FAILED_MESSAGE,
  [ERROR_CODE.USER_STATUS_ABNORMAL]: LOGIN_FAILED_MESSAGE,

  // ---- 业务：交易 / 订单 / 商品 ----
  [ERROR_CODE.ORDER_NOT_FOUND]: '订单不存在',
  [ERROR_CODE.ORDER_STATUS_INVALID]: '当前订单状态不允许该操作',
  [ERROR_CODE.PRODUCT_NOT_FOUND]: '商品不存在或已下架',
  [ERROR_CODE.STOCK_INSUFFICIENT]: '该商品库存不足，请调整数量',
  [ERROR_CODE.CART_EMPTY]: '购物车是空的，先去逛逛挑点好物吧',
  [ERROR_CODE.CROSS_STORE_FORBIDDEN]: '暂不支持跨店下单',
  [ERROR_CODE.SHOP_NOT_FOUND]: '店铺不存在或已关闭',
  [ERROR_CODE.SKU_NOT_FOUND]: '所选规格不存在，请重新选择',
  [ERROR_CODE.HOME_CONFIG_NOT_FOUND]: '页面配置不存在',
  [ERROR_CODE.REVIEW_ALREADY_EXISTS]: '该商品已评价',
  [ERROR_CODE.COUPON_NOT_FOUND]: '该优惠券不适用于当前订单',

  // ---- 业务：秒杀 ----
  [ERROR_CODE.FLASH_SALE_NOT_FOUND]: '秒杀活动不存在',
  [ERROR_CODE.FLASH_SALE_NOT_STARTED]: '秒杀活动尚未开始或已结束',
  [ERROR_CODE.FLASH_SALE_SOLD_OUT]: '已售罄，下次要更快哦',
  [ERROR_CODE.FLASH_SALE_LIMIT_EXCEEDED]: '已超出每人限购数量',
  [ERROR_CODE.FLASH_SALE_FAILED]: '抢购失败，请重试',

  // ---- 业务：售后 / 申请 / 通知 / 评价 / 运费 ----
  [ERROR_CODE.APPLICATION_EXISTS]: '已有审核中的申请，请耐心等待',
  [ERROR_CODE.AFTER_SALE_EXISTS]: '该订单已有售后申请',
  [ERROR_CODE.APPLICATION_NOT_FOUND]: '申请不存在',
  [ERROR_CODE.WITHDRAWAL_NOT_FOUND]: '提现申请不存在',
  [ERROR_CODE.NOTIFICATION_NOT_FOUND]: '通知不存在',
  [ERROR_CODE.LOGISTICS_NOT_FOUND]: '暂无物流信息',
  [ERROR_CODE.REVIEW_NOT_FOUND]: '评价不存在',
  [ERROR_CODE.REVIEW_ALREADY_REPLIED]: '该评价已回复',
  [ERROR_CODE.REVIEW_APPEND_CONFLICT]: '追评失败，请稍后重试',
  [ERROR_CODE.REFUND_STATUS_INVALID]: '当前退款状态不允许该操作',
  [ERROR_CODE.FREIGHT_TEMPLATE_NAME_EXISTS]: '运费模板名称已存在',
  [ERROR_CODE.FREIGHT_TEMPLATE_NOT_FOUND]: '运费模板不存在',
  [ERROR_CODE.CART_ITEM_LIMIT_EXCEEDED]: '购物车条目数已达上限，请先清理再添加',

  // ---- AI 专项 ----
  [ERROR_CODE.AI_SERVICE_UNAVAILABLE]: 'AI 助手暂不可用，请稍后再试',
  [ERROR_CODE.AI_TIMEOUT]: 'AI 响应超时，请重新提问',
  [ERROR_CODE.AI_CONTENT_FILTERED]: '内容未通过安全审核，请调整后再试',
  [ERROR_CODE.AI_RATE_LIMITED]: 'AI 助手正在忙碌中，请稍后再试',
  [ERROR_CODE.AI_CONVERSATION_NOT_FOUND]: '会话不存在或无权访问',

  // ---- 第三方：通用 ----
  [ERROR_CODE.THIRD_PARTY_ERROR]: '第三方服务异常，请稍后重试',
  [ERROR_CODE.THIRD_PARTY_TIMEOUT]: '第三方服务响应超时，请稍后重试',
  [ERROR_CODE.THIRD_PARTY_CONNECT_FAILED]: '第三方服务连接失败，请稍后重试',
  [ERROR_CODE.THIRD_PARTY_RATE_LIMITED]: '操作过于频繁，请稍后再试',
  [ERROR_CODE.THIRD_PARTY_AUTH_FAILED]: '第三方服务认证失败，请联系客服',
  [ERROR_CODE.THIRD_PARTY_SIGN_ERROR]: '第三方接口签名校验失败，请联系客服',
  [ERROR_CODE.THIRD_PARTY_PARAM_ERROR]: '第三方接口参数错误，请联系客服',
  [ERROR_CODE.THIRD_PARTY_RESPONSE_PARSE_ERROR]: '第三方响应解析失败，请稍后重试',
  [ERROR_CODE.THIRD_PARTY_UNKNOWN_ERROR]: '第三方服务异常，请稍后重试',

  // ---- 第三方：微信 ----
  [ERROR_CODE.WECHAT_ERROR]: '微信服务异常，请稍后重试',
  [ERROR_CODE.WECHAT_CODE_INVALID]: '微信授权已失效，请重新授权',
  [ERROR_CODE.WECHAT_AUTH_FAILED]: '微信授权失败，请重试',
  [ERROR_CODE.WECHAT_OPENID_NOT_FOUND]: '微信用户信息不存在，请重新登录',
  [ERROR_CODE.WECHAT_PAY_ERROR]: '微信支付调用失败，请更换支付方式',
  [ERROR_CODE.WECHAT_PAY_SIGN_ERROR]: '微信支付签名校验失败，请联系客服',
  [ERROR_CODE.WECHAT_PAY_ORDER_NOT_FOUND]: '微信支付订单不存在',
  [ERROR_CODE.WECHAT_REFUND_ERROR]: '微信退款失败，请稍后重试',
  [ERROR_CODE.WECHAT_REFUND_AMOUNT_ERROR]: '退款金额不合法',

  // ---- 第三方：支付宝 ----
  [ERROR_CODE.ALIPAY_ERROR]: '支付宝服务异常，请稍后重试',
  [ERROR_CODE.ALIPAY_AUTH_FAILED]: '支付宝授权失败，请重试',
  [ERROR_CODE.ALIPAY_PAY_ERROR]: '支付宝支付调用失败，请更换支付方式',
  [ERROR_CODE.ALIPAY_SIGN_ERROR]: '支付宝签名校验失败，请联系客服',
  [ERROR_CODE.ALIPAY_REFUND_ERROR]: '支付宝退款失败，请稍后重试',
  [ERROR_CODE.ALIPAY_TRADE_NOT_FOUND]: '支付宝交易不存在',

  // ---- 第三方：短信 / 邮件 ----
  [ERROR_CODE.SMS_SEND_FAILED]: '短信发送失败，请稍后重试',
  [ERROR_CODE.SMS_RATE_LIMITED]: '短信发送过于频繁，请稍后再试',
  [ERROR_CODE.SMS_TEMPLATE_ERROR]: '短信模板不可用，请联系客服',
  [ERROR_CODE.SMS_SIGN_ERROR]: '短信签名不合法，请联系客服',
  [ERROR_CODE.SMS_BALANCE_INSUFFICIENT]: '短信服务暂不可用，请稍后重试',
  [ERROR_CODE.EMAIL_SEND_FAILED]: '邮件发送失败，请稍后重试',

  // ---- 第三方：OSS / 文件 ----
  [ERROR_CODE.OSS_UPLOAD_FAILED]: '文件上传失败，请重试',
  [ERROR_CODE.OSS_DOWNLOAD_FAILED]: '文件下载失败，请重试',
  [ERROR_CODE.OSS_DELETE_FAILED]: '文件删除失败，请重试',
  [ERROR_CODE.OSS_FILE_NOT_FOUND]: '文件不存在',
  [ERROR_CODE.OSS_FILE_TOO_LARGE]: '文件大小超出限制',
  [ERROR_CODE.OSS_FILE_TYPE_NOT_ALLOWED]: '文件类型不允许',

  // ---- 第三方：地图 / 物流 ----
  [ERROR_CODE.MAP_SERVICE_ERROR]: '地址服务异常，请稍后重试',
  [ERROR_CODE.MAP_GEOCODE_FAILED]: '地址解析失败，请检查地址',
  [ERROR_CODE.LOGISTICS_QUERY_FAILED]: '物流信息查询失败，请稍后重试',
  [ERROR_CODE.LOGISTICS_COMPANY_NOT_FOUND]: '物流公司不存在',

  [ERROR_CODE.THIRD_PARTY_UNDEFINED]: '第三方服务异常，请稍后重试',

  // ---- 加解密（一般不应到达用户，保留兜底文案） ----
  [ERROR_CODE.ENCRYPT_ERROR]: '数据处理失败，请稍后重试',
  [ERROR_CODE.DECRYPT_ERROR]: '数据处理失败，请稍后重试',
  [ERROR_CODE.HASH_ERROR]: '数据处理失败，请稍后重试',
};

/* ================================================================
   分流判定 —— 供页面按类别做不同处理（跳登录 / 人机验证 / 冷却 / 字段高亮）
   ================================================================ */

/** 需跳转登录页（并触发 token 刷新）：1011 未认证、1012 已过期。 */
export function isAuthError(code?: number): boolean {
  return code === ERROR_CODE.UNAUTHORIZED || code === ERROR_CODE.TOKEN_EXPIRED;
}

/** 需渲染人机验证组件并带四要素重试：1016（响应 data 携带 provider / captcha_id）。 */
export function requiresCaptcha(code?: number): boolean {
  return code === ERROR_CODE.CAPTCHA_REQUIRED;
}

/** 需进入倒计时冷却（限流统一出口）：1014、3103、4031。 */
export function isRateLimited(code?: number): boolean {
  return (
    code === ERROR_CODE.FREQUENT_ERROR ||
    code === ERROR_CODE.AI_RATE_LIMITED ||
    code === ERROR_CODE.SMS_RATE_LIMITED ||
    code === ERROR_CODE.THIRD_PARTY_RATE_LIMITED
  );
}

/** 需做表单字段高亮 + 错误提示：1101。 */
export function isParamError(code?: number): boolean {
  return code === ERROR_CODE.PARAM_ERROR;
}

/**
 * 「资源不存在」判定 —— **1103 与 3011 并轨**（见 `PRODUCT_NOT_FOUND` 处的说明）。
 *
 * 为什么不只看 `code`：自 2026-10-18 B8 起 SPU 详情（P2）/ 短码（P38）的「不存在」
 * 是 **真 HTTP 404**，`ApiError.code` 取的是 `body.code || res.status` —— 若网关/中间层
 * 在 404 上不带 body（或 body 无 `code`），落到前端的就是 `code = 404`（HTTP 状态）。
 * 三态都认：「404」= HTTP 状态，「1103」= 新语义业务码，「3011」= 存量报文兼容。
 *
 * @param err 任意 `{ code?, message? }`（`ApiError` / `ServerFetchError` 均满足）
 */
export function isNotFound(err?: { code?: number } | null): boolean {
  const code = err?.code;
  return (
    code === 404 ||
    code === ERROR_CODE.NO_RESOURCE_FOUND ||
    code === ERROR_CODE.PRODUCT_NOT_FOUND // 存量兼容（后端已 deprecated，P2 不再使用）
  );
}

/** 第三方渠道可用性问题（可提示「更换支付方式 / 稍后重试」）：4xxx 全段。 */
export function isThirdPartyError(code?: number): boolean {
  return code !== undefined && code >= 4000 && code < 5000;
}

/**
 * 「登录失败」判定 —— 后端登录链路的失败口径（2026-10-18 批次 ④）。
 *
 * 认四种形态，覆盖「body 带 code」与「body 缺失只剩 HTTP 状态」两种到达方式：
 *   1. **`401`（HTTP 状态）** —— `ApiError.code` 取 `body.code || res.status`，若中间层剥掉 body
 *      就会落到 401；`3007` 的 HTTP 状态已由 200 改为 401；
 *   2. **`3007`** —— 登录失败的唯一对外业务码；
 *   3. **`3008` / `1017`** —— 存量报文兼容（后端已分别改为「仅非登录域使用」/ 退役）；
 *   4. **`1102`** —— 最老报文的兼容（修复前「账号不存在」返回 `1102 / 404`）。**只为登录表单
 *      兜底**：若上游/中间层仍吐旧形态，登录页也绝不能显示「用户不存在」。
 *
 * ⚠ 不要用它判断「未登录」（那是 `1011 / 1012`，见 `isAuthError`）：登录**表单**的 401 意味着
 * 「凭证不正确」，而带 token 的普通请求 401 意味着「令牌失效」，两者处置完全不同。
 *
 * @param err 任意 `{ code?, message? }`（`ApiError` / `ServerFetchError` 均满足）
 */
export function isLoginFailure(err?: { code?: number } | null): boolean {
  const code = err?.code;
  return (
    code === 401 ||
    code === ERROR_CODE.PASSWORD_ERROR ||
    code === ERROR_CODE.USER_STATUS_ABNORMAL ||
    code === ERROR_CODE.ACCOUNT_FROZEN ||
    code === ERROR_CODE.USER_NOT_FOUND
  );
}

/**
 * 登录失败时的用户提示 —— **恒定中性**，绝不区分失败原因。
 *
 * 只在「登录表单提交 / 微信登录」这类**认证请求**的失败分支使用；
 * 其它场景的错误提示继续走 `getUserMessage`。
 *
 * @param err 登录请求抛出的错误
 * @returns 中性文案（`LOGIN_FAILED_MESSAGE`）；非登录失败类错误回落 `getUserMessage`
 */
export function getLoginFailureMessage(err?: { code?: number; message?: string } | null): string {
  if (isLoginFailure(err)) return LOGIN_FAILED_MESSAGE;
  return getUserMessage(err ?? {});
}

/**
 * 将 `ApiError` 转为用户可读消息。
 *
 * 优先级（既有语义，保持不变）：
 *   1. `ERROR_MAP[code]` —— 已知业务码的稳定中文文案（覆盖 99 个码全集）
 *   2. **`isNotFound()`** —— 纯 HTTP 404（body 无 `code`，`ApiError.code = 404`）没有
 *      `ERROR_MAP` 条目，落到这里给「资源不存在」而不是「网络异常」
 *   3. `err.message` —— 后端 `Result.msg`（更具体的动态消息，如「验证码错误次数过多，请稍后重试」）；
 *      形如 `HTTP 500` 的占位消息不采用
 *   4. 兜底「网络异常，请检查连接后重试」
 */
export function getUserMessage(err: { code?: number; message?: string }): string {
  const mapped = err.code !== undefined ? ERROR_MAP[err.code] : undefined;
  if (mapped) return mapped;
  // 纯 HTTP 404（P2 新语义的回落形态）—— 不是网络问题，别给「检查连接」的误导文案
  if (isNotFound(err)) return '请求的资源不存在，可能已被删除';
  // 后端返回的 msg 优先 (如 "验证码错误次数过多，请稍后重试" 等动态消息)
  if (err.message && !/^HTTP\s\d{3}$/.test(err.message)) return err.message;
  return '网络异常，请检查连接后重试';
}
