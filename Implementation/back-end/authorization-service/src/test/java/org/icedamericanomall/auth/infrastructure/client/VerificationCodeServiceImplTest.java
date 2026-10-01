package org.icedamericanomall.auth.infrastructure.client;

import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.VerificationCodeVerifyReqCO;
import org.icedamericanomall.dto.VerificationCodeVerifyRespCO;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * VerificationCodeServiceImpl 单元测试 —— 走 verification-service 的校验与结果码映射。
 *
 * <p>最后一组用例直接用 ia-api 的降级工厂 {@link VerificationClientFallback} 产出"服务不可用"响应，
 * 验证 <b>fail-closed</b>：校验服务挂掉时登录必须被拒，绝不默认放行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeServiceImpl 单元测试")
class VerificationCodeServiceImplTest {

    @Mock
    private VerificationClient verificationClient;

    @InjectMocks
    private VerificationCodeServiceImpl codeService;

    @Test
    @DisplayName("校验通过 → 不抛异常，通道/目标/验证码按契约传参")
    void shouldPass_whenRemoteSuccess() {
        when(verificationClient.verifyCode(any())).thenReturn(VerificationCodeVerifyRespCO.ok());

        assertDoesNotThrow(() -> codeService.verifyAndConsume(
                VerificationChannelEnum.SMS, "13888888888", "123456"));

        ArgumentCaptor<VerificationCodeVerifyReqCO> captor =
                ArgumentCaptor.forClass(VerificationCodeVerifyReqCO.class);
        org.mockito.Mockito.verify(verificationClient).verifyCode(captor.capture());
        assertEquals("SMS", captor.getValue().getChannel());
        assertEquals("13888888888", captor.getValue().getTarget());
        assertEquals("123456", captor.getValue().getCode());
    }

    @Test
    @DisplayName("验证码错误 → CAPTCHA_ERROR")
    void shouldMapMismatch() {
        assertMapped(VerificationResultCode.CODE_MISMATCH, ErrorCode.CAPTCHA_ERROR);
    }

    @Test
    @DisplayName("验证码过期 → CAPTCHA_EXPIRED")
    void shouldMapExpired() {
        assertMapped(VerificationResultCode.CODE_EXPIRED, ErrorCode.CAPTCHA_EXPIRED);
    }

    @Test
    @DisplayName("错误次数超限 → FREQUENT_ERROR")
    void shouldMapTooManyAttempts() {
        assertMapped(VerificationResultCode.TOO_MANY_ATTEMPTS, ErrorCode.FREQUENT_ERROR);
    }

    @Test
    @DisplayName("短信通道重发过频 → SMS_RATE_LIMITED")
    void shouldMapRateLimitedForSms() {
        assertMapped(VerificationResultCode.RATE_LIMITED, ErrorCode.SMS_RATE_LIMITED);
    }

    @Test
    @DisplayName("Feign 降级（校验服务不可用）→ SYSTEM_BUSY，绝不放行（fail-closed）")
    void shouldFailClosed_whenServiceUnavailable() {
        when(verificationClient.verifyCode(any())).thenReturn(new VerificationClientFallback()
                .create(new RuntimeException("connection refused"))
                .verifyCode(new VerificationCodeVerifyReqCO()));

        BizException exception = assertThrows(BizException.class, () -> codeService.verifyAndConsume(
                VerificationChannelEnum.EMAIL, "buyer@example.com", "123456"));

        assertEquals(ErrorCode.SYSTEM_BUSY, exception.getErrorCode());
    }

    @Test
    @DisplayName("远端返回 null（异常契约）→ 按系统繁忙拒绝，不放行")
    void shouldFailClosed_whenResponseNull() {
        when(verificationClient.verifyCode(any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> codeService.verifyAndConsume(
                VerificationChannelEnum.SMS, "13888888888", "123456"));

        assertEquals(ErrorCode.SYSTEM_BUSY, exception.getErrorCode());
    }

    private void assertMapped(String reason, ErrorCode expected) {
        when(verificationClient.verifyCode(any())).thenReturn(VerificationCodeVerifyRespCO.fail(reason));

        BizException exception = assertThrows(BizException.class, () -> codeService.verifyAndConsume(
                VerificationChannelEnum.SMS, "13888888888", "000000"));

        assertEquals(expected, exception.getErrorCode());
    }
}
