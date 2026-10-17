package org.icedamericanomall.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 系统级错误 (HTTP 状态码统一用 500)
    INTERNAL_ERROR(1000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_BUSY(1001, "系统繁忙",  HttpStatus.SERVICE_UNAVAILABLE),

    // 认证/授权类错误 (HTTP 状态码用 401/403)
    UNAUTHORIZED(1011, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1012, "登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1013, "无权限操作", HttpStatus.FORBIDDEN),
    FREQUENT_ERROR(1014, "操作过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    ILLEGAL_REQUEST(1015, "非法请求", HttpStatus.BAD_REQUEST),
    CAPTCHA_REQUIRED(1016, "人机验证失败", HttpStatus.FORBIDDEN),
    /**
     * 账号被冻结 / 极高风险阻断 —— <b>登录链路已不再抛本码（2026-10-18 批次 ④）</b>。
     *
     * <p><b>为何退役</b>：本码原配 {@code 403}，而「密码错误」原配 {@code 200}、「账号不存在」原配
     * {@code 404} —— 三者状态码互不相同，攻击者<b>只看状态码</b>即可枚举「某账号是否存在 / 是否被冻结」。
     * 现按 {@code collaboration/09-constraints.md} §三·六「未认证 → 401」把登录失败收敛为
     * <b>唯一出口 {@link #PASSWORD_ERROR}（3007 / 401 / 中性文案）</b>，本码随之退役并纠正状态码
     * —— 即便将来有人误用，也只会得到 401，不会退化成「可区分的 403」。
     *
     * <p><b>对用户的「冻结」提示</b>只能走登录<b>成功</b>后的一次性提示或站内信，<b>不得</b>在登录失败
     * 响应里区分（否则「冻结 → 中性化」白做）。当前枚举中零生产引用（{@code AccountFreezeHandler}
     * 已改抛 3007），保留仅为兼容历史文档/契约引用。
     *
     * @deprecated 登录失败一律抛 {@link #PASSWORD_ERROR}（3007 / HTTP 401 + 中性文案）
     */
    @Deprecated(since = "batch-4-login-disclosure")
    ACCOUNT_FROZEN(1017, "用户账号异常", HttpStatus.UNAUTHORIZED),

    // 参数/资源类错误 (HTTP 状态码用 400/404)
    PARAM_ERROR(1101, "参数错误", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1102, "用户不存在", HttpStatus.NOT_FOUND),
    NO_RESOURCE_FOUND(1103, "静态资源不存在", HttpStatus.NOT_FOUND),

    // 业务逻辑错误 (HTTP 状态码统一用 200)
    //   例外（2026-10-18 批次 ④，按 09-constraints.md §三·六「未认证 → 401」）：
    //   3007 PASSWORD_ERROR / 3008 USER_STATUS_ABNORMAL 为 401 —— 登录失败若用 200 承载，
    //   「200 vs 404」本身就是账号是否存在／是否被冻结的枚举信道。
    BUSINESS_EXECUTION_EXCEPTION(3000, "业务执行异常", HttpStatus.OK),
    CAPTCHA_ERROR(3001, "验证码错误", HttpStatus.OK),
    CAPTCHA_EXPIRED(3002, "验证码已过期", HttpStatus.OK),
    PHONE_ALREADY_REGISTERED(3003, "手机号已被注册", HttpStatus.OK),
    USERNAME_ALREADY_TAKEN(3004, "用户名已被占用", HttpStatus.OK),
    BALANCE_INSUFFICIENT(3005, "余额不足", HttpStatus.OK),
    USER_ALREADY_EXISTS(3006, "用户已存在", HttpStatus.OK),
    /**
     * 登录失败 —— <b>全部失败原因的唯一对外出口</b>（2026-10-18 批次 ④，抗账号枚举）。
     *
     * <p>承载四类原因，<b>对外完全一致</b>：账号不存在 / 密码错误 / 账号状态不可登录（解绑、锁定）/
     * 极高风险阻断（原 {@link #ACCOUNT_FROZEN}）。原因细分只进服务端日志（{@code LoginFailureReason}），
     * 不进 HTTP 状态码、不进业务码、不进文案。
     *
     * <p><b>状态码为什么是 401</b>：见 {@code collaboration/09-constraints.md} §三·六
     * 「未认证 → 401」。此前本码配 {@code 200}、{@code USER_NOT_FOUND} 配 {@code 404}、
     * {@code ACCOUNT_FROZEN} 配 {@code 403} —— 状态码本身就构成「账号是否存在」的枚举信道。
     *
     * <p><b>文案为什么是中性</b>：{@code ErrorCode.message} 会在调用方未给自定义文案时成为对外
     * {@code Result.msg}，因此它自身也必须中性（调用方<b>不得</b>再传「账号不存在」「密码错误」
     * 这类可区分的自定义文案）。
     */
    PASSWORD_ERROR(3007, "账号或密码不正确", HttpStatus.UNAUTHORIZED),
    /**
     * 账号状态异常（解绑 / 锁定）—— <b>登录链路禁止使用</b>（2026-10-18 批次 ④）。
     *
     * <p>原配 {@code HttpStatus.OK}（200）：被冻结的账号登录失败返回 200 + 3008，而「账号不存在」
     * 返回 404 —— 状态码差异即可枚举账号。现按 {@code 09-constraints.md} §三·六 纠正为
     * {@code 401}（<b>安全默认</b>：即便遗漏调用也不会退化成 200），并把登录链路的抛出点改为
     * {@link #PASSWORD_ERROR}（唯一出口）。
     *
     * <p><b>使用边界（批次 ⑤b 收紧，逐字阅读）</b>：
     * <ul>
     *   <li>本码<b>仅限非登录域</b>的「账号状态异常」语义；<b>登录链路（authorization-service 登录策略
     *       / 风控处置）一律抛 {@link #PASSWORD_ERROR}</b>，否则「账号存在但不可用」会与「账号不存在」可区分。</li>
     *   <li><b>不得用于鉴权失败</b>：它在 HTTP 层是 {@code 401}（「未认证」语义）。已认证用户
     *       「状态异常 → 无权访问」属<b>403</b>（抛 {@link #FORBIDDEN}），参数非法属
     *       <b>400</b>（抛 {@link #PARAM_ERROR}）；错用本码会把一次普通的业务拒绝变成
     *       「清凭证 + 跳登录页」，冻结用户因此陷入「登录成功 → 立刻被登出」的死循环。</li>
     *   <li>当前<b>零生产引用</b>：2026-10-18 批次 ⑤b 已把两个非登录调用点分别改为
     *       {@code PARAM_ERROR(1101)}（user-service {@code UserStatusEnum#of} 管理端非法状态值）
     *       与 {@code FORBIDDEN(1013)}（{@code User#assertActive} 冻结用户访问用户资料）。
     *       保留本码仅为兼容契约/历史文档引用，<b>新增代码不应再抛它</b>。</li>
     * </ul>
     */
    USER_STATUS_ABNORMAL(3008, "用户状态异常", HttpStatus.UNAUTHORIZED),
    // 交易/订单/商品通用错误 (3009-3029)
    ORDER_NOT_FOUND(3009, "订单不存在", HttpStatus.OK),
    ORDER_STATUS_INVALID(3010, "当前订单状态不支持此操作", HttpStatus.OK),
    /**
     * 商品不存在 —— <b>已被 {@link #NO_RESOURCE_FOUND}（1103 / HTTP 404）取代，禁止新增使用</b>。
     *
     * <p><b>状态码订正（2026-10-18，批次 B7b）</b>：原 {@code HttpStatus.OK} 属
     * 「用 200 信封承载 not-found」的偏离。按 {@code collaboration/09-constraints.md}
     * §三·六「错误承载的四类划分」：<b>资源不存在 = 真 404 + {@code NO_RESOURCE_FOUND(1103)}</b>；
     * 只有<b>业务规则拒绝</b>（资源存在、请求合法但规则不允许）才用 200 + body code。
     * 故本枚举的 {@code HttpStatus} 按该表纠正为 {@code NOT_FOUND}（{@code 09 §三·六}
     * 末条：「以本表为准修改该枚举的 status，不要在新代码里绕过枚举手写状态码」）。
     *
     * <p><b>为何保留而不删除</b>：契约 §二 与历史文档仍引用该码；保留但把状态纠正后，
     * 即使有遗漏调用也不会退化成「200 + 查不到」，属安全默认。新增代码一律抛
     * {@link #NO_RESOURCE_FOUND}。
     *
     * @deprecated 资源不存在统一抛 {@link #NO_RESOURCE_FOUND}（1103 / HTTP 404）；本项仅为兼容存量引用保留
     */
    @Deprecated(since = "B7b")
    PRODUCT_NOT_FOUND(3011, "商品不存在", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(3012, "库存不足", HttpStatus.OK),
    CART_EMPTY(3013, "购物车为空", HttpStatus.OK),
    CROSS_STORE_FORBIDDEN(3014, "暂不支持跨店下单", HttpStatus.OK),
    SHOP_NOT_FOUND(3015, "店铺不存在", HttpStatus.OK),
    SKU_NOT_FOUND(3016, "SKU不存在", HttpStatus.OK),
    HOME_CONFIG_NOT_FOUND(3017, "首页配置不存在", HttpStatus.OK),
    REVIEW_ALREADY_EXISTS(3018, "该商品已评价", HttpStatus.OK),
    COUPON_NOT_FOUND(3019, "优惠券不存在", HttpStatus.OK),
    // 秒杀错误 (3020-3029)
    FLASH_SALE_NOT_FOUND(3020, "秒杀活动不存在", HttpStatus.OK),
    FLASH_SALE_NOT_STARTED(3021, "秒杀未开始或已结束", HttpStatus.OK),
    FLASH_SALE_SOLD_OUT(3022, "已售罄", HttpStatus.OK),
    FLASH_SALE_LIMIT_EXCEEDED(3023, "每人限购1件", HttpStatus.OK),
    FLASH_SALE_FAILED(3024, "抢购失败，请重试", HttpStatus.OK),
    // 售后/申请 (3030-3039)
    APPLICATION_EXISTS(3030, "已有审核中的申请", HttpStatus.OK),
    AFTER_SALE_EXISTS(3031, "已有售后申请", HttpStatus.OK),
    APPLICATION_NOT_FOUND(3032, "申请不存在", HttpStatus.OK),
    WITHDRAWAL_NOT_FOUND(3033, "提现申请不存在", HttpStatus.OK),
    // 通知/物流 (3034-3035)
    NOTIFICATION_NOT_FOUND(3034, "通知不存在", HttpStatus.OK),
    LOGISTICS_NOT_FOUND(3035, "物流信息不存在", HttpStatus.OK),
    REVIEW_NOT_FOUND(3036, "评价不存在", HttpStatus.OK),
    REVIEW_ALREADY_REPLIED(3037, "该评价已回复", HttpStatus.OK),
    REVIEW_APPEND_CONFLICT(3038, "追评失败", HttpStatus.OK),
    REFUND_STATUS_INVALID(3039, "退款状态不支持此操作", HttpStatus.OK),
    FREIGHT_TEMPLATE_NAME_EXISTS(3040, "运费模板名称已存在", HttpStatus.OK),
    FREIGHT_TEMPLATE_NOT_FOUND(3041, "运费模板不存在", HttpStatus.OK),
    /**
     * 购物车条目数达上限（B7b 新增）—— <b>业务规则拒绝</b>：资源存在、请求合法，
     * 只是条目数不允许再增，故按 09 §三·六 用 200 + body code（不是 4xx）。
     *
     * <p>上限值集中在 {@code common.constant.CsvIdLimits#MAX_CART_ITEMS}
     * （按「下单请求体预算」这条判据倒推，不按历史习惯值）。
     */
    CART_ITEM_LIMIT_EXCEEDED(3042, "购物车条目数已达上限", HttpStatus.OK),

    // AI 服务错误 (3100-3199)
    AI_SERVICE_UNAVAILABLE(3100, "AI 服务未启用或 API Key 未配置", HttpStatus.SERVICE_UNAVAILABLE),
    AI_TIMEOUT(3101, "AI 调用超时，请稍后重试", HttpStatus.GATEWAY_TIMEOUT),
    AI_CONTENT_FILTERED(3102, "内容未通过安全审核", HttpStatus.BAD_REQUEST),
    AI_RATE_LIMITED(3103, "AI 助手正在忙碌中，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    AI_CONVERSATION_NOT_FOUND(3104, "会话不存在或无权访问", HttpStatus.NOT_FOUND),

    // ============================================================
    // 第三方接口访问错误（4000-4999）
    // 设计原则：
    //   4000-4009：通用第三方错误（所有厂商共用）
    //   4010-4019：微信相关（登录、支付、消息）
    //   4020-4029：支付宝相关
    //   4030-4039：短信/邮件服务商
    //   4040-4049：OSS/文件存储服务商
    //   4050-4059：地图/物流等
    //   4999：未分类的第三方错误兜底
    // ============================================================

    // ---------- 通用第三方错误（4000-4009） ----------
    THIRD_PARTY_ERROR(4000, "第三方服务异常，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_TIMEOUT(4001, "第三方服务响应超时", HttpStatus.GATEWAY_TIMEOUT),
    THIRD_PARTY_CONNECT_FAILED(4002, "第三方服务连接失败", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_RATE_LIMITED(4003, "第三方服务调用频率超限", HttpStatus.TOO_MANY_REQUESTS),
    THIRD_PARTY_AUTH_FAILED(4004, "第三方服务认证失败", HttpStatus.UNAUTHORIZED),
    THIRD_PARTY_SIGN_ERROR(4005, "第三方接口签名验证失败", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_PARAM_ERROR(4006, "第三方接口参数错误", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_RESPONSE_PARSE_ERROR(4007, "第三方响应数据解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    THIRD_PARTY_UNKNOWN_ERROR(4008, "第三方未知错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---------- 微信相关（4010-4019） ----------
    WECHAT_ERROR(4010, "微信服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_CODE_INVALID(4011, "微信登录 code 无效或已过期", HttpStatus.BAD_REQUEST),
    WECHAT_AUTH_FAILED(4012, "微信授权失败", HttpStatus.UNAUTHORIZED),
    WECHAT_OPENID_NOT_FOUND(4013, "微信用户信息不存在", HttpStatus.NOT_FOUND),
    WECHAT_PAY_ERROR(4014, "微信支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_PAY_SIGN_ERROR(4015, "微信支付签名验证失败", HttpStatus.BAD_REQUEST),
    WECHAT_PAY_ORDER_NOT_FOUND(4016, "微信支付订单不存在", HttpStatus.NOT_FOUND),
    WECHAT_REFUND_ERROR(4017, "微信退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_REFUND_AMOUNT_ERROR(4018, "微信退款金额不合法", HttpStatus.BAD_REQUEST),

    // ---------- 支付宝相关（4020-4029） ----------
    ALIPAY_ERROR(4020, "支付宝服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_AUTH_FAILED(4021, "支付宝授权失败", HttpStatus.UNAUTHORIZED),
    ALIPAY_PAY_ERROR(4022, "支付宝支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_SIGN_ERROR(4023, "支付宝签名验证失败", HttpStatus.BAD_REQUEST),
    ALIPAY_REFUND_ERROR(4024, "支付宝退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_TRADE_NOT_FOUND(4025, "支付宝交易不存在", HttpStatus.NOT_FOUND),

    // ---------- 短信/邮件服务商（4030-4039） ----------
    SMS_SEND_FAILED(4030, "短信发送失败", HttpStatus.SERVICE_UNAVAILABLE),
    SMS_RATE_LIMITED(4031, "短信发送过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SMS_TEMPLATE_ERROR(4032, "短信模板不存在或未审核", HttpStatus.BAD_REQUEST),
    SMS_SIGN_ERROR(4033, "短信签名不合法", HttpStatus.BAD_REQUEST),
    SMS_BALANCE_INSUFFICIENT(4034, "短信服务商余额不足", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_SEND_FAILED(4035, "邮件发送失败", HttpStatus.SERVICE_UNAVAILABLE),

    // ---------- OSS/文件存储（4040-4049） ----------
    OSS_UPLOAD_FAILED(4040, "文件上传失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DOWNLOAD_FAILED(4041, "文件下载失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DELETE_FAILED(4042, "文件删除失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_FILE_NOT_FOUND(4043, "文件不存在", HttpStatus.NOT_FOUND),
    OSS_FILE_TOO_LARGE(4044, "文件大小超出限制", HttpStatus.BAD_REQUEST),
    OSS_FILE_TYPE_NOT_ALLOWED(4045, "文件类型不允许", HttpStatus.BAD_REQUEST),

    // ---------- 地图/物流等（4050-4059） ----------
    MAP_SERVICE_ERROR(4050, "地图服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    MAP_GEOCODE_FAILED(4051, "地址解析失败", HttpStatus.BAD_REQUEST),
    LOGISTICS_QUERY_FAILED(4052, "物流信息查询失败", HttpStatus.SERVICE_UNAVAILABLE),
    LOGISTICS_COMPANY_NOT_FOUND(4053, "物流公司不存在", HttpStatus.NOT_FOUND),

    // ---------- 兜底 ----------
    THIRD_PARTY_UNDEFINED(4999, "未分类的第三方错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ----------通用加密解密工具错误（5000-5020----------
    ENCRYPT_ERROR(5000, "加密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DECRYPT_ERROR(5001, "解密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    HASH_ERROR(5002, "哈希错误", HttpStatus.INTERNAL_SERVER_ERROR),

    ;


    private final int code;          // 业务码
    private final String message;    // 默认提示信息
    private final HttpStatus httpStatus; // HTTP 状态码

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}