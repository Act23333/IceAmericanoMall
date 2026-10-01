package org.icedamericanomall.user.domain.gateway;

import org.icedamericanomall.user.domain.model.CaptchaAttempt;

/**
 * 校验能力网关（防腐层：调用 verification-service）。
 *
 * <p>领域只声明"签发一条短信验证码 / 校验一次人机验证"两项能力，
 * 参数与返回值均为本上下文的语义；Feign 契约（ia-api 的 {@code VerificationClient}）
 * 与结果码翻译由基础设施层完成（{@code infrastructure/client}）。
 *
 * <p>失败语义：实现必须把远端结果码翻译为本上下文业务异常（参照 authorization-service
 * 的 {@code VerificationResultMapper} 先例）—— 调用方拿到的要么是成功，要么是 BizException。
 */
public interface VerificationGateway {

    /**
     * 签发短信验证码（verification-service：生成 + 5 分钟有效 + 60 秒重发限流 + 投递）。
     *
     * @param target    手机号
     * @param scene     业务场景（REGISTER / LOGIN / RESET_PASSWORD，用于投递文案与埋点归因）
     * @param requestId 幂等 / 链路追踪标识（可空）
     * @return 验证码有效期（秒）
     * @throws org.icedamericanomall.common.exception.BizException 限流（SMS_RATE_LIMITED）/ 投递失败（SMS_SEND_FAILED）/ 依赖不可用（SYSTEM_BUSY）
     */
    int issueSmsCode(String target, String scene, String requestId);

    /**
     * 人机验证（verification-service → 极验 Geetest 二次校验）。
     *
     * @param attempt 极验四要素 + 客户端 IP
     * @return true = 验证通过
     * @throws org.icedamericanomall.common.exception.BizException 校验未通过（CAPTCHA_ERROR）/ 依赖不可用（SYSTEM_BUSY，fail-closed）
     */
    boolean verifyCaptcha(CaptchaAttempt attempt);
}
