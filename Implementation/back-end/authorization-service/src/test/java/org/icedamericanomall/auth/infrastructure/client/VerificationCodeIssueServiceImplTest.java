package org.icedamericanomall.auth.infrastructure.client;

import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.client.VerificationClient;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VerificationCodeIssueServiceImpl 单元测试 —— 邮箱发码链路（签发 → 结果码映射）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeIssueServiceImpl 单元测试")
class VerificationCodeIssueServiceImplTest {

    @Mock
    private VerificationClient verificationClient;

    @InjectMocks
    private VerificationCodeIssueServiceImpl issueService;

    @Test
    @DisplayName("发码成功 → 返回有效期，场景/通道/目标按契约传参")
    void shouldReturnExpireSeconds_whenSuccess() {
        when(verificationClient.issueCode(any())).thenReturn(VerificationCodeIssueRespCO.ok(300));

        IssuedCodeDTO issued = issueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN);

        assertEquals(300, issued.expireSeconds());
        ArgumentCaptor<VerificationCodeIssueReqCO> captor =
                ArgumentCaptor.forClass(VerificationCodeIssueReqCO.class);
        verify(verificationClient).issueCode(captor.capture());
        assertEquals("EMAIL", captor.getValue().getChannel());
        assertEquals("buyer@example.com", captor.getValue().getTarget());
        assertEquals("LOGIN", captor.getValue().getScene());
        assertNotNull(captor.getValue().getRequestId());
    }

    @Test
    @DisplayName("邮件重发过频 → FREQUENT_ERROR")
    void shouldMapEmailRateLimited() {
        when(verificationClient.issueCode(any()))
                .thenReturn(VerificationCodeIssueRespCO.fail(VerificationResultCode.RATE_LIMITED));

        BizException exception = assertThrows(BizException.class, () -> issueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN));

        assertEquals(ErrorCode.FREQUENT_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("邮件投递失败 → EMAIL_SEND_FAILED")
    void shouldMapEmailSendFailed() {
        when(verificationClient.issueCode(any()))
                .thenReturn(VerificationCodeIssueRespCO.fail(VerificationResultCode.SEND_FAILED));

        BizException exception = assertThrows(BizException.class, () -> issueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN));

        assertEquals(ErrorCode.EMAIL_SEND_FAILED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Feign 降级（校验服务不可用）→ SYSTEM_BUSY，不伪装成功")
    void shouldMapServiceUnavailable_whenCircuitBreakerDegrades() {
        when(verificationClient.issueCode(any())).thenReturn(new VerificationClientFallback()
                .create(new RuntimeException("timeout"))
                .issueCode(new VerificationCodeIssueReqCO()));

        BizException exception = assertThrows(BizException.class, () -> issueService.issueCode(
                VerificationChannelEnum.EMAIL, "buyer@example.com", VerificationSceneEnum.LOGIN));

        assertEquals(ErrorCode.SYSTEM_BUSY, exception.getErrorCode());
    }
}
