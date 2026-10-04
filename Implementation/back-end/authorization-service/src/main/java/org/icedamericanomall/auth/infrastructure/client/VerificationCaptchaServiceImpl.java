package org.icedamericanomall.auth.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.app.port.CaptchaService;
import org.icedamericanomall.auth.infrastructure.config.CaptchaProperties;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationResultCode;
import org.springframework.stereotype.Component;

/**
 * 人机验证实现（基础设施层）—— Feign 调 verification-service 的
 * {@code /internal/verification/captcha/verify}，由后者对接极验（第三方适配在 ia-integration）。
 *
 * <p>替代原先的 {@code CaptchaServiceImpl + CaptchaAdapterFactory + GeetestCaptchaAdapter}
 * （auth 直连极验、本地持密钥、本地做签名）：校验收口到 verification-service 后，
 * 本服务的配置里不再出现厂商密钥与校验地址。
 *
 * <p>失败语义：
 * <ul>
 *   <li>验证未通过（{@code CAPTCHA_FAILED} 等）→ 返回 {@code false}，由应用层抛业务异常</li>
 *   <li>校验服务不可用（Feign 熔断降级 {@code SERVICE_UNAVAILABLE}）→ 抛异常，
 *       <b>fail-closed</b>：不把依赖故障当成"验证通过"</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCaptchaServiceImpl implements CaptchaService {

    private final VerificationClient verificationClient;
    private final CaptchaProperties captchaProperties;

    @Override
    public boolean isEnabled() {
        return captchaProperties.isEnabled();
    }

    @Override
    public boolean verify(CaptchaVerifyParam param) {
        CaptchaVerifyReqCO request = new CaptchaVerifyReqCO();
        request.setProvider(resolveProvider(param));
        request.setParams(param.params());
        request.setClientIp(param.clientIp());

        CaptchaVerifyRespCO response = verificationClient.verifyCaptcha(request);
        if (response != null && response.isSuccess()) {
            log.info("[auth][captcha][pass] provider={}", request.getProvider());
            return true;
        }
        String reason = response == null ? null : response.getReason();
        log.warn("[auth][captcha][reject] provider={}, reason={}", request.getProvider(), reason);
        if (VerificationResultCode.SERVICE_UNAVAILABLE.equals(reason)) {
            throw VerificationResultMapper.toCaptchaException(reason);
        }
        return false;
    }

    /** 厂商标识：请求未指定时取服务端配置的默认厂商（不信任前端指定任意厂商） */
    private String resolveProvider(CaptchaVerifyParam param) {
        return param.provider() == null || param.provider().isBlank()
                ? captchaProperties.getDefaultProvider()
                : param.provider();
    }
}
