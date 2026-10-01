package org.icedamericanomall.verification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.captcha.CaptchaClient;
import org.icedamericanomall.integration.captcha.Geetest4ValidateRequest;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.port.CaptchaVerifyService;
import org.springframework.stereotype.Component;

/**
 * 人机验证端口实现（基础设施层）—— 按 provider 路由到 ia-integration 的验证厂商客户端。
 *
 * <p>当前仅接入极验（GEETEST）；未知 provider 一律返回 {@code false}（安全优先：拒绝而非放行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaVerifyServiceImpl implements CaptchaVerifyService {

    private static final String PROVIDER_GEETEST = "GEETEST";

    private final CaptchaClient<Geetest4ValidateRequest> geetestCaptchaClient;

    @Override
    public boolean verify(String provider, CaptchaVerifyCommand command) {
        if (PROVIDER_GEETEST.equalsIgnoreCase(provider)) {
            Geetest4ValidateRequest request = Geetest4ValidateRequest.builder()
                    .lotNumber(command.lotNumber())
                    .captchaOutput(command.captchaOutput())
                    .passToken(command.passToken())
                    .genTime(command.genTime())
                    .build();
            return geetestCaptchaClient.verify(request);
        }
        log.warn("不支持的人机验证厂商: {}", provider);
        return false;
    }
}
