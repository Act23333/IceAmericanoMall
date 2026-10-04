package org.icedamericanomall.verification.app;

import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.command.CodeIssueCommand;
import org.icedamericanomall.verification.app.command.CodeVerifyCommand;
import org.icedamericanomall.verification.app.port.CaptchaVerifyService;
import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.icedamericanomall.verification.domain.service.VerificationCodeVerifier;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 应用层编排单元测试 —— 通道解析、入参短路、厂商默认值。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校验 — 应用层编排")
class VerificationManagerTest {

    @Mock
    private VerificationCodeIssuer issuer;

    @Mock
    private VerificationCodeVerifier verifier;

    @Mock
    private CaptchaVerifyService captchaVerifyService;

    @InjectMocks
    private VerificationManager verificationManager;

    @Test
    @DisplayName("编排 — 非法通道：返回 PARAM_INVALID，不触碰领域服务")
    void shouldReturnParamInvalid_whenChannelUnknown() {
        VerificationCodeIssuer.IssuedCode result = verificationManager.issueCode(
                new CodeIssueCommand("PUSH", "13800001111", "LOGIN", "req-1"));

        assertEquals(VerificationOutcome.PARAM_INVALID, result.outcome());
        verifyNoInteractions(issuer);

        assertEquals(VerificationOutcome.PARAM_INVALID, verificationManager.verifyCode(
                new CodeVerifyCommand("", "13800001111", "123456")));
        verifyNoInteractions(verifier);
    }

    @Test
    @DisplayName("编排 — 合法通道：解析为领域枚举并委托（target 去除首尾空格）")
    void shouldDelegateToDomainService_whenChannelValid() {
        when(issuer.issue(eq(VerificationChannel.SMS), eq("13800001111"), eq("LOGIN"), eq("req-2")))
                .thenReturn(VerificationCodeIssuer.IssuedCode.ok(300));

        VerificationCodeIssuer.IssuedCode result = verificationManager.issueCode(
                new CodeIssueCommand(" sms ", " 13800001111 ", "LOGIN", "req-2"));

        assertTrue(result.isSuccess());
        assertEquals(300, result.expireSeconds());

        when(verifier.verify(VerificationChannel.EMAIL, "a@b.com", "123456"))
                .thenReturn(VerificationOutcome.SUCCESS);
        assertEquals(VerificationOutcome.SUCCESS, verificationManager.verifyCode(
                new CodeVerifyCommand("EMAIL", "a@b.com", "123456")));
    }

    @Test
    @DisplayName("编排 — 人机验证：provider 为空时默认 GEETEST")
    void shouldDefaultGeetestProvider_whenProviderBlank() {
        when(captchaVerifyService.verify(eq("GEETEST"), any())).thenReturn(true);

        boolean passed = verificationManager.verifyCaptcha(new CaptchaVerifyCommand(
                null, Map.of("lotNumber", "lot-1", "captchaOutput", "output",
                "passToken", "token", "genTime", "1700000000000"), "127.0.0.1"));

        assertTrue(passed);
        verify(captchaVerifyService).verify(eq("GEETEST"), any());
    }
}
