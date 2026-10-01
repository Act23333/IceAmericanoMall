package org.icedamericanomall.user.infrastructure.client;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.VerificationResultCode;

/**
 * 校验服务结果码 → 本上下文业务异常（基础设施层内部映射器，SMS 通道）。
 *
 * <p>参照 authorization-service 的 {@code VerificationResultMapper} 先例
 * （发码迁移决策见 ADR-016）：verification-service 的 {@code /internal/**} 端点用
 * {@code success + reason} 表达业务失败，调用方必须翻译成本上下文的 {@link ErrorCode}，
 * 否则前端只能看到笼统的 500。映射后：
 * <ul>
 *   <li>重发过频 → {@code SMS_RATE_LIMITED}；投递失败 → {@code SMS_SEND_FAILED}</li>
 *   <li>依赖不可用（含 Feign 熔断降级）→ {@code SYSTEM_BUSY}，绝不返回成功（fail-closed）</li>
 *   <li>未知结果码 → 同样按失败处理（兜底系统繁忙）</li>
 * </ul>
 */
public final class VerificationResultMapper {

    private VerificationResultMapper() {
    }

    /**
     * 发码（签发）路径的结果码映射。
     *
     * @param reason 结果码，取值见 {@link VerificationResultCode}
     * @return 对应的业务异常
     */
    public static BizException toIssueException(String reason) {
        return new BizException(codeOf(reason), messageOf(reason));
    }

    /**
     * 人机验证路径的结果码映射（校验未通过 → 验证码错误语义；依赖不可用 → 系统繁忙，fail-closed）。
     *
     * @param reason 结果码
     * @return 对应的业务异常
     */
    public static BizException toCaptchaException(String reason) {
        if (VerificationResultCode.SERVICE_UNAVAILABLE.equals(reason)) {
            return new BizException(ErrorCode.SYSTEM_BUSY, "人机验证服务暂不可用，请稍后重试");
        }
        return new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证失败");
    }

    private static ErrorCode codeOf(String reason) {
        if (reason == null) {
            return ErrorCode.SYSTEM_BUSY;
        }
        return switch (reason) {
            case VerificationResultCode.RATE_LIMITED -> ErrorCode.SMS_RATE_LIMITED;
            case VerificationResultCode.SEND_FAILED -> ErrorCode.SMS_SEND_FAILED;
            case VerificationResultCode.PARAM_INVALID -> ErrorCode.PARAM_ERROR;
            case VerificationResultCode.NOT_SUPPORTED -> ErrorCode.PARAM_ERROR;
            default -> ErrorCode.SYSTEM_BUSY;
        };
    }

    private static String messageOf(String reason) {
        if (reason == null) {
            return "短信验证服务暂不可用，请稍后重试";
        }
        return switch (reason) {
            case VerificationResultCode.RATE_LIMITED -> "短信发送过于频繁，请稍后再试";
            case VerificationResultCode.SEND_FAILED -> "短信发送失败，请稍后重试";
            case VerificationResultCode.PARAM_INVALID -> "验证请求参数不合法";
            case VerificationResultCode.NOT_SUPPORTED -> "不支持的验证通道";
            default -> "短信验证服务暂不可用，请稍后重试";
        };
    }
}
