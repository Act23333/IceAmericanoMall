package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.VerificationCodeVerifyReqCO;
import org.icedamericanomall.dto.VerificationCodeVerifyRespCO;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.stereotype.Component;

/**
 * 验证码校验实现（基础设施层）—— Feign 调 verification-service 的
 * {@code /internal/verification/code/verify}。
 *
 * <p>替换了原先"直读 Redis {@code sms:code:} "的实现（R5 解耦）：验证码的生成、存储、
 * 一次性消费与错误计数全部在 verification-service，本上下文不再关心 Key 布局；
 * 短信 Key 契约（{@code sms:code:{phone}} / 5 分钟）由 user-service 与 verification-service 两端保证互通。
 *
 * <p><b>安全约定</b>：只有 {@code success=true} 才放行；其余（含 Feign 熔断降级返回的
 * {@code SERVICE_UNAVAILABLE}）一律抛出业务异常，绝不默认放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final VerificationClient verificationClient;

    @Override
    public void verifyAndConsume(VerificationChannelEnum channel, String target, String code) {
        VerificationCodeVerifyReqCO request = new VerificationCodeVerifyReqCO();
        request.setChannel(channel.getCode());
        request.setTarget(target);
        request.setCode(code);

        VerificationCodeVerifyRespCO response = verificationClient.verifyCode(request);
        if (response != null && response.isSuccess()) {
            log.info("[auth][code-verify][pass] channel={}, target={}",
                    channel.getCode(), DesensitizeUtil.mask(target));
            return;
        }
        String reason = response == null ? null : response.getReason();
        log.warn("[auth][code-verify][reject] channel={}, target={}, reason={}",
                channel.getCode(), DesensitizeUtil.mask(target), reason);
        throw VerificationResultMapper.toVerifyException(reason, channel);
    }
}
