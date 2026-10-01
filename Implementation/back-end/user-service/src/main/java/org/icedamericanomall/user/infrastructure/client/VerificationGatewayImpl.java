package org.icedamericanomall.user.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.user.domain.gateway.VerificationGateway;
import org.icedamericanomall.user.domain.model.CaptchaAttempt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 校验能力网关实现（基础设施层，防腐层）—— Feign 调 verification-service。
 *
 * <p>发码链路（ADR-016 迁移后）：{@code /api/user/code} → 本实现 → verification-service
 * {@code /internal/verification/code/issue}（生成 + 5 分钟有效 + 60 秒重发限流 +
 * 经 notification-service 投递，写同一 {@code sms:code:} 契约）。
 * 本服务不再本地生成验证码、不再本地限流、不再直连短信通道。
 *
 * <p>人机验证：极验二次校验已收口 verification-service（ADR-007），
 * 本服务只做四要素透传与结果码翻译。
 *
 * <p>{@code requestId} 用于跨服务链路追踪（一次发码请求内保持一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationGatewayImpl implements VerificationGateway {

    /** 短信通道编码（ia-api 契约字符串） */
    private static final String CHANNEL_SMS = "SMS";

    /** 默认人机验证厂商 */
    private static final String CAPTCHA_PROVIDER = "GEETEST";

    private final VerificationClient verificationClient;

    @Override
    public int issueSmsCode(String target, String scene, String requestId) {
        VerificationCodeIssueReqCO request = new VerificationCodeIssueReqCO();
        request.setChannel(CHANNEL_SMS);
        request.setTarget(target);
        request.setScene(scene);
        request.setRequestId(requestId == null ? UUID.randomUUID().toString() : requestId);

        VerificationCodeIssueRespCO response = verificationClient.issueCode(request);
        if (response != null && response.isSuccess()) {
            log.info("[user][sms-issue][done] scene={}, target={}, expireSeconds={}",
                    scene, DesensitizeUtil.mask(target), response.getExpireSeconds());
            return response.getExpireSeconds() == null ? 0 : response.getExpireSeconds();
        }
        String reason = response == null ? null : response.getReason();
        log.warn("[user][sms-issue][reject] scene={}, target={}, reason={}",
                scene, DesensitizeUtil.mask(target), reason);
        throw VerificationResultMapper.toIssueException(reason);
    }

    @Override
    public boolean verifyCaptcha(CaptchaAttempt attempt) {
        CaptchaVerifyReqCO request = new CaptchaVerifyReqCO();
        request.setProvider(CAPTCHA_PROVIDER);
        request.setLotNumber(attempt.lotNumber());
        request.setCaptchaOutput(attempt.captchaOutput());
        request.setPassToken(attempt.passToken());
        request.setGenTime(attempt.genTime());
        request.setClientIp(attempt.clientIp());

        CaptchaVerifyRespCO response = verificationClient.verifyCaptcha(request);
        if (response != null && response.isSuccess()) {
            return true;
        }
        String reason = response == null ? null : response.getReason();
        log.warn("[user][captcha-verify][reject] reason={}", reason);
        throw VerificationResultMapper.toCaptchaException(reason);
    }
}
