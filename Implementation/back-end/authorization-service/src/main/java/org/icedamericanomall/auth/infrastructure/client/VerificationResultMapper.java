package org.icedamericanomall.auth.infrastructure.client;

import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.dto.VerificationResultCode;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 校验服务结果码 → 本上下文业务异常（基础设施层内部映射器）。
 *
 * <p>为什么要显式映射：verification-service 的 {@code /internal/**} 端点用
 * {@code success + reason}（结构化结果）表达业务失败，调用方必须把它翻译成本上下文的
 * {@link ErrorCode}，否则前端只能看到笼统的 500。映射后：
 * <ul>
 *   <li>用户可理解的失败（验证码错误/过期/过频）→ 业务错误码（HTTP 200/403）</li>
 *   <li>依赖不可用（{@link VerificationResultCode#SERVICE_UNAVAILABLE}，含 Feign 熔断降级）
 *       → {@code SYSTEM_BUSY}，且<b>绝不返回成功</b>（fail-closed）</li>
 *   <li>未知结果码 → 同样按失败处理（不新造枚举，直接兜底为系统繁忙）</li>
 * </ul>
 *
 * <p>不新增 {@code ErrorCode} 取值：现有枚举已覆盖全部语义（任务约束）。
 */
public final class VerificationResultMapper {

    private VerificationResultMapper() {
    }

    /**
     * 校验（消费）路径的结果码映射。
     *
     * @param reason  结果码，取值见 {@link VerificationResultCode}
     * @param channel 验证通道（决定投递类失败的措辞，如短信/邮件）
     * @return 对应的业务异常
     */
    public static BizException toVerifyException(String reason, VerificationChannelEnum channel) {
        return new BizException(codeOf(reason, channel), messageOf(reason, channel));
    }

    /**
     * 签发（发码）路径的结果码映射。
     *
     * @param reason  结果码，取值见 {@link VerificationResultCode}
     * @param channel 验证通道
     * @return 对应的业务异常
     */
    public static BizException toIssueException(String reason, VerificationChannelEnum channel) {
        return new BizException(codeOf(reason, channel), messageOf(reason, channel));
    }

    /**
     * 人机验证路径的结果码映射（校验未通过由调用方按 {@code false} 处理，本方法只处理异常类失败）。
     *
     * @param reason 结果码
     * @return 对应的业务异常
     */
    public static BizException toCaptchaException(String reason) {
        if (VerificationResultCode.SERVICE_UNAVAILABLE.equals(reason)) {
            return new BizException(ErrorCode.SYSTEM_BUSY, "人机验证服务暂不可用，请稍后重试");
        }
        return new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证未通过");
    }

    private static ErrorCode codeOf(String reason, VerificationChannelEnum channel) {
        boolean sms = channel == VerificationChannelEnum.SMS;
        if (reason == null) {
            return ErrorCode.SYSTEM_BUSY;
        }
        return switch (reason) {
            case VerificationResultCode.CODE_MISMATCH -> ErrorCode.CAPTCHA_ERROR;
            case VerificationResultCode.CODE_EXPIRED -> ErrorCode.CAPTCHA_EXPIRED;
            case VerificationResultCode.TOO_MANY_ATTEMPTS -> ErrorCode.FREQUENT_ERROR;
            case VerificationResultCode.RATE_LIMITED -> sms ? ErrorCode.SMS_RATE_LIMITED : ErrorCode.FREQUENT_ERROR;
            case VerificationResultCode.SEND_FAILED -> sms ? ErrorCode.SMS_SEND_FAILED : ErrorCode.EMAIL_SEND_FAILED;
            case VerificationResultCode.PARAM_INVALID -> ErrorCode.PARAM_ERROR;
            case VerificationResultCode.NOT_SUPPORTED -> ErrorCode.PARAM_ERROR;
            default -> ErrorCode.SYSTEM_BUSY;
        };
    }

    private static String messageOf(String reason, VerificationChannelEnum channel) {
        boolean sms = channel == VerificationChannelEnum.SMS;
        String channelName = sms ? "短信" : "邮件";
        if (reason == null) {
            return channelName + "验证服务暂不可用，请稍后重试";
        }
        return switch (reason) {
            case VerificationResultCode.CODE_MISMATCH -> "验证码错误";
            case VerificationResultCode.CODE_EXPIRED -> "验证码已过期或未发送";
            case VerificationResultCode.TOO_MANY_ATTEMPTS -> "验证码错误次数过多，请重新获取";
            case VerificationResultCode.RATE_LIMITED -> channelName + "发送过于频繁，请稍后再试";
            case VerificationResultCode.SEND_FAILED -> channelName + "发送失败，请稍后重试";
            case VerificationResultCode.PARAM_INVALID -> "验证请求参数不合法";
            case VerificationResultCode.NOT_SUPPORTED -> "不支持的验证通道";
            default -> "验证服务暂不可用，请稍后重试";
        };
    }
}
