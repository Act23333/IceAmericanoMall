package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;
import org.icedamericanomall.auth.app.port.VerificationCodeIssueService;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 验证码签发实现（基础设施层）—— Feign 调 verification-service 的
 * {@code /internal/verification/code/issue}。
 *
 * <p>本上下文不生成验证码、不写 Redis、不投递：签发（生成 + 有效期 + 60 秒重发限流）、
 * 投递（经 notification-service 的短信/邮件通道）全在远端完成，失败原因按契约结果码映射为业务异常。
 *
 * <p>{@code requestId} 用于跨服务链路追踪（一次发码请求内保持一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeIssueServiceImpl implements VerificationCodeIssueService {

    private final VerificationClient verificationClient;

    @Override
    public IssuedCodeDTO issueCode(VerificationChannelEnum channel,
                                   String target,
                                   VerificationSceneEnum scene) {
        VerificationCodeIssueReqCO request = new VerificationCodeIssueReqCO();
        request.setChannel(channel.getCode());
        request.setTarget(target);
        request.setScene(scene.getCode());
        request.setRequestId(UUID.randomUUID().toString());

        VerificationCodeIssueRespCO response = verificationClient.issueCode(request);
        if (response != null && response.isSuccess()) {
            log.info("[auth][code-issue][done] channel={}, scene={}, target={}, expireSeconds={}",
                    channel.getCode(), scene.getCode(), DesensitizeUtil.mask(target),
                    response.getExpireSeconds());
            return new IssuedCodeDTO(response.getExpireSeconds());
        }
        String reason = response == null ? null : response.getReason();
        log.warn("[auth][code-issue][reject] channel={}, scene={}, target={}, reason={}",
                channel.getCode(), scene.getCode(), DesensitizeUtil.mask(target), reason);
        throw VerificationResultMapper.toIssueException(reason, channel);
    }
}
