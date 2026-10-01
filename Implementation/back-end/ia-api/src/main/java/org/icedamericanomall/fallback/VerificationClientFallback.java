package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.dto.VerificationCodeVerifyReqCO;
import org.icedamericanomall.dto.VerificationCodeVerifyRespCO;
import org.icedamericanomall.dto.VerificationResultCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 校验服务降级工厂 —— <b>安全优先（fail-closed）</b>。
 *
 * <p>降级策略说明：verification-service 不可用时，
 * <ul>
 *   <li>{@code verifyCode} / {@code verifyCaptcha} 返回 {@code success=false}，
 *       即「校验失败」而非「放行」。理由：验证码与人机验证是反自动化 / 反撞库的最后一道闸门，
 *       任何一次「降级放行」都是在攻击窗口期主动开门；宁可让用户重试，
 *       也不能让攻击者以打挂校验服务的方式绕过验证。</li>
 *   <li>{@code issueCode} 返回 {@code success=false}（无法签发时不得伪装成功，
 *       否则用户会等待一条永远不会到达的验证码）。</li>
 * </ul>
 *
 * <p>与 {@code NotificationClientFallback} 的差异：通知通道失败抛异常（调用方需要感知），
 * 校验通道失败返回结构化「失败」（调用方无需 try/catch，直接看 success）。
 */
@Slf4j
@Component
public class VerificationClientFallback implements FallbackFactory<VerificationClient> {

    @Override
    public VerificationClient create(Throwable cause) {
        return new VerificationClient() {

            @Override
            public VerificationCodeIssueRespCO issueCode(VerificationCodeIssueReqCO request) {
                log.error("校验服务降级：验证码签发失败 channel={}, scene={}",
                        request.getChannel(), request.getScene(), cause);
                return VerificationCodeIssueRespCO.fail(VerificationResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public VerificationCodeVerifyRespCO verifyCode(VerificationCodeVerifyReqCO request) {
                log.error("校验服务降级：验证码校验按失败处理（fail-closed） channel={}",
                        request.getChannel(), cause);
                return VerificationCodeVerifyRespCO.fail(VerificationResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public CaptchaVerifyRespCO verifyCaptcha(CaptchaVerifyReqCO request) {
                log.error("校验服务降级：人机验证按失败处理（fail-closed） provider={}",
                        request.getProvider(), cause);
                return CaptchaVerifyRespCO.fail(VerificationResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
