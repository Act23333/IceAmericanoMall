package org.icedamericanomall.verification.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.verification.adapter.request.VerificationCodeIssueReq;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.verification.app.VerificationManager;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.command.CodeIssueCommand;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("验证码公开端点 — 编排与失败映射")
@ExtendWith(MockitoExtension.class)
class VerificationCodeControllerTest {

    @Mock
    private VerificationManager verificationManager;

    @InjectMocks
    private VerificationCodeController controller;

    private VerificationCodeIssueReq request(String channel, String target, String scene) {
        VerificationCodeIssueReq request = new VerificationCodeIssueReq();
        request.setChannel(channel);
        request.setTarget(target);
        request.setScene(scene);
        return request;
    }

    @Test
    @DisplayName("签发成功 → Result.ok（含有效期）")
    void shouldReturnExpireSeconds_whenIssued() {
        when(verificationManager.issueCode(any(CodeIssueCommand.class), any(CaptchaVerifyCommand.class)))
                .thenReturn(VerificationCodeIssuer.IssuedCode.ok(300));

        Result<VerificationCodeIssueRespCO> result = controller.issueCode(
                request("SMS", "13800001111", "REGISTER"), mock(HttpServletRequest.class));

        assertEquals(200, result.getCode());
        assertEquals(300, result.getData().getExpireSeconds());
    }

    @Test
    @DisplayName("重发限流 → FREQUENT_ERROR（公开端点异常作为唯一失败出口）")
    void shouldThrowFrequent_whenRateLimited() {
        when(verificationManager.issueCode(any(CodeIssueCommand.class), any(CaptchaVerifyCommand.class)))
                .thenReturn(VerificationCodeIssuer.IssuedCode.fail(VerificationOutcome.RATE_LIMITED));

        BizException ex = assertThrows(BizException.class, () -> controller.issueCode(
                request("SMS", "13800001111", "REGISTER"), mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.FREQUENT_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("校验类结果 → 按语义映射（CODE_MISMATCH→CAPTCHA_ERROR / CODE_EXPIRED→CAPTCHA_EXPIRED / TOO_MANY_ATTEMPTS→FREQUENT_ERROR）")
    void shouldMapValidationOutcomes_bySemantics() {
        assertMapped(VerificationOutcome.CODE_MISMATCH, ErrorCode.CAPTCHA_ERROR);
        assertMapped(VerificationOutcome.CODE_EXPIRED, ErrorCode.CAPTCHA_EXPIRED);
        assertMapped(VerificationOutcome.TOO_MANY_ATTEMPTS, ErrorCode.FREQUENT_ERROR);
        assertMapped(VerificationOutcome.SEND_FAILED, ErrorCode.SYSTEM_BUSY);
    }

    private void assertMapped(VerificationOutcome outcome, ErrorCode expected) {
        when(verificationManager.issueCode(any(CodeIssueCommand.class), any(CaptchaVerifyCommand.class)))
                .thenReturn(VerificationCodeIssuer.IssuedCode.fail(outcome));

        BizException ex = assertThrows(BizException.class, () -> controller.issueCode(
                request("SMS", "13800001111", "REGISTER"), mock(HttpServletRequest.class)));

        assertEquals(expected, ex.getErrorCode());
    }

    @Test
    @DisplayName("携带人机验证参数 → 厂商中立参数集透传应用层（校验失败 CAPTCHA_ERROR）")
    void shouldPassCaptchaParams_toManager() {
        when(verificationManager.issueCode(any(CodeIssueCommand.class), any(CaptchaVerifyCommand.class)))
                .thenThrow(new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证未通过"));

        VerificationCodeIssueReq req = request("SMS", "13800001111", "LOGIN");
        req.setCaptchaProvider("GEETEST");
        req.setCaptchaParams(Map.of("lotNumber", "lot"));

        BizException ex = assertThrows(BizException.class,
                () -> controller.issueCode(req, mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.CAPTCHA_ERROR, ex.getErrorCode());
        ArgumentCaptor<CaptchaVerifyCommand> captor = ArgumentCaptor.forClass(CaptchaVerifyCommand.class);
        verify(verificationManager).issueCode(any(CodeIssueCommand.class), captor.capture());
        assertEquals("GEETEST", captor.getValue().provider());
        assertEquals("lot", captor.getValue().params().get("lotNumber"));
    }
}
