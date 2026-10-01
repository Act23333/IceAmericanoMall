package org.icedamericanomall.verification.domain.enums;

/**
 * 校验结果（领域语义），适配层再映射为 ia-api 的
 * {@code VerificationResultCode} 字符串返回给调用方。
 *
 * <p>把结果建模成枚举而不是异常：验证码校验失败是<b>预期内</b>的业务分支
 * （用户输错、验证码过期、频繁出错），调用方需要区分原因并展示不同提示；
 * 而 {@code /internal/**} 的业务异常会被 {@code GlobalExceptionHandler} 直接 rethrow，
 * Feign 侧只能看到 HTTP 500，无法区分原因。
 */
public enum VerificationOutcome {

    /** 校验通过（验证码已一次性消费） */
    SUCCESS,

    /** 验证码错误（对应 ErrorCode.CAPTCHA_ERROR 语义） */
    CODE_MISMATCH,

    /** 验证码不存在 / 已过期 / 已被消费（对应 ErrorCode.CAPTCHA_EXPIRED 语义） */
    CODE_EXPIRED,

    /** 错误次数超限，验证码已作废（对应 ErrorCode.FREQUENT_ERROR 语义） */
    TOO_MANY_ATTEMPTS,

    /** 重发过于频繁（60 秒窗口内重复请求签发） */
    RATE_LIMITED,

    /** 通道投递失败（notification-service / 第三方失败） */
    SEND_FAILED,

    /** 入参非法（通道取值非法、目标为空等） */
    PARAM_INVALID,
    ;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
