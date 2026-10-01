package org.icedamericanomall.dto;

/**
 * 校验类内部接口的结果码（跨服务共享词汇表）—— verification-service 返回，调用方判定。
 *
 * <p>为什么用「结构化结果码」而不是抛异常：{@code /internal/**} 端点的
 * {@code GlobalExceptionHandler} 会把业务异常直接 rethrow，Feign 调用方只能看到 HTTP 500，
 * 无法区分「验证码错误」与「验证码过期」。因此校验类端点统一返回
 * {@code success + reason}，仅基础设施故障（Redis 不可用等）才抛异常。
 *
 * <p>调用方判断约定：
 * <ul>
 *   <li>{@link #SUCCESS} → 放行</li>
 *   <li>其余 reason → 一律视为「校验未通过」，安全优先，禁止默认放行</li>
 * </ul>
 */
public final class VerificationResultCode {

    /** 校验通过 */
    public static final String SUCCESS = "SUCCESS";

    /** 验证码错误（对应 ErrorCode.CAPTCHA_ERROR 语义） */
    public static final String CODE_MISMATCH = "CODE_MISMATCH";

    /** 验证码已过期 / 未发送（对应 ErrorCode.CAPTCHA_EXPIRED 语义） */
    public static final String CODE_EXPIRED = "CODE_EXPIRED";

    /** 错误次数超限，验证码已作废（对应 ErrorCode.FREQUENT_ERROR 语义） */
    public static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";

    /** 重发过于频繁（60 秒窗口内重复请求签发） */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    /** 通道投递失败（notification-service / 第三方失败） */
    public static final String SEND_FAILED = "SEND_FAILED";

    /** 依赖服务不可用（Feign 降级） */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    /** 人机验证未通过 */
    public static final String CAPTCHA_FAILED = "CAPTCHA_FAILED";

    /** 通道不支持（如站内信） */
    public static final String NOT_SUPPORTED = "NOT_SUPPORTED";

    /** 入参非法（通道取值非法 / 目标格式错误） */
    public static final String PARAM_INVALID = "PARAM_INVALID";

    private VerificationResultCode() {
    }
}
