package org.icedamericanomall.auth.infrastructure.client;

import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.infrastructure.config.CaptchaProperties;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationResultCode;
import org.icedamericanomall.fallback.VerificationClientFallback;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VerificationCaptchaServiceImpl 单元测试 —— 人机验证转发 verification-service 与失败语义。
 *
 * <p>关键安全断言：远端降级（服务不可用）时抛异常而<b>不是</b>返回 true，
 * 即不会把依赖故障当成"验证通过"。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCaptchaServiceImpl 单元测试")
class VerificationCaptchaServiceImplTest {

    @Mock
    private VerificationClient verificationClient;

    @Mock
    private CaptchaProperties captchaProperties;

    @InjectMocks
    private VerificationCaptchaServiceImpl captchaService;

    @Test
    @DisplayName("验证通过 → true，四要素按契约透传")
    void shouldReturnTrue_whenRemotePass() {
        when(verificationClient.verifyCaptcha(any())).thenReturn(CaptchaVerifyRespCO.ok());

        boolean passed = captchaService.verify(param("GEETEST"));

        assertTrue(passed);
        ArgumentCaptor<CaptchaVerifyReqCO> captor = ArgumentCaptor.forClass(CaptchaVerifyReqCO.class);
        verify(verificationClient).verifyCaptcha(captor.capture());
        assertEquals("lot-1", captor.getValue().getLotNumber());
        assertEquals("output-1", captor.getValue().getCaptchaOutput());
        assertEquals("pass-1", captor.getValue().getPassToken());
        assertEquals("1700000000000", captor.getValue().getGenTime());
        assertEquals("127.0.0.1", captor.getValue().getClientIp());
    }

    @Test
    @DisplayName("前端未指定厂商 → 取服务端配置的默认厂商（不信任前端任填）")
    void shouldUseConfiguredProvider_whenProviderAbsent() {
        when(captchaProperties.getDefaultProvider()).thenReturn("GEETEST");
        when(verificationClient.verifyCaptcha(any())).thenReturn(CaptchaVerifyRespCO.ok());

        captchaService.verify(param(null));

        ArgumentCaptor<CaptchaVerifyReqCO> captor = ArgumentCaptor.forClass(CaptchaVerifyReqCO.class);
        verify(verificationClient).verifyCaptcha(captor.capture());
        assertEquals("GEETEST", captor.getValue().getProvider());
    }

    @Test
    @DisplayName("验证未通过 → false（由应用层抛业务异常）")
    void shouldReturnFalse_whenCaptchaFailed() {
        when(verificationClient.verifyCaptcha(any()))
                .thenReturn(CaptchaVerifyRespCO.fail(VerificationResultCode.CAPTCHA_FAILED));

        assertFalse(captchaService.verify(param("GEETEST")));
    }

    @Test
    @DisplayName("校验服务不可用（熔断降级）→ 抛异常，fail-closed 不放行")
    void shouldThrow_whenServiceUnavailable() {
        when(verificationClient.verifyCaptcha(any())).thenReturn(new VerificationClientFallback()
                .create(new RuntimeException("no instances available"))
                .verifyCaptcha(new CaptchaVerifyReqCO()));

        BizException exception = assertThrows(BizException.class,
                () -> captchaService.verify(param("GEETEST")));

        assertEquals(ErrorCode.SYSTEM_BUSY, exception.getErrorCode());
    }

    @Test
    @DisplayName("isEnabled — 直接反映 captcha.enabled 配置")
    void shouldReflectEnabledFlag() {
        when(captchaProperties.isEnabled()).thenReturn(true);
        assertTrue(captchaService.isEnabled());

        when(captchaProperties.isEnabled()).thenReturn(false);
        assertFalse(captchaService.isEnabled());
    }

    private CaptchaVerifyParam param(String provider) {
        return new CaptchaVerifyParam(provider, "lot-1", "output-1", "pass-1",
                "1700000000000", "127.0.0.1");
    }
}
