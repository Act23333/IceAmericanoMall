package org.icedamericanomall.verification.domain;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.gateway.NotificationGateway;
import org.icedamericanomall.verification.domain.model.VerificationCode;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.domain.port.CodeGenerator;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证码签发领域服务单元测试 —— 纯 POJO + Mock 端口。
 *
 * <p>覆盖：生成规则（位数）、重发限流、投递失败回滚。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校验 — 验证码签发领域服务")
class VerificationCodeIssuerTest {

    private static final String TARGET = "13800001111";
    private static final String SCENE = "LOGIN";

    @Mock
    private VerificationCodeRepository repository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private NotificationGateway notificationGateway;

    private VerificationCodeIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new VerificationCodeIssuer(repository, codeGenerator, notificationGateway,
                VerificationPolicy.DEFAULT);
    }

    @Test
    @DisplayName("签发 — 正常路径：生成 6 位码 → 存 5 分钟 → 投递成功，返回有效期秒数")
    void shouldIssueAndSendCode_whenQuotaAvailable() {
        when(repository.tryAcquireSendQuota(eq(VerificationChannel.SMS), eq(TARGET), any())).thenReturn(true);
        when(codeGenerator.generate(VerificationPolicy.DEFAULT.codeLength())).thenReturn("123456");

        VerificationCodeIssuer.IssuedCode result = issuer.issue(VerificationChannel.SMS, TARGET, SCENE, "req-1");

        assertTrue(result.isSuccess());
        assertEquals(VerificationOutcome.SUCCESS, result.outcome());
        assertEquals(300, result.expireSeconds());
        // 生成规则：按策略位数生成
        verify(codeGenerator).generate(6);
        // 存储：TTL 与 user-service 一致（5 分钟）
        verify(repository).save(VerificationChannel.SMS, TARGET, "123456", Duration.ofMinutes(5));
        // 投递：领域对象携带明文验证码交给网关
        verify(notificationGateway).sendVerificationCode(
                new VerificationCode(VerificationChannel.SMS, TARGET, "123456"), "req-1", SCENE);
    }

    @Test
    @DisplayName("签发 — 60 秒窗口内重复请求 → RATE_LIMITED，且不生成、不存储、不投递")
    void shouldReturnRateLimited_whenQuotaNotAcquired() {
        when(repository.tryAcquireSendQuota(eq(VerificationChannel.SMS), eq(TARGET), any())).thenReturn(false);

        VerificationCodeIssuer.IssuedCode result = issuer.issue(VerificationChannel.SMS, TARGET, SCENE, "req-2");

        assertFalse(result.isSuccess());
        assertEquals(VerificationOutcome.RATE_LIMITED, result.outcome());
        verifyNoInteractions(codeGenerator);
        verifyNoInteractions(notificationGateway);
        verify(repository, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("签发 — 投递失败 → SEND_FAILED，并回滚验证码与限流窗口（允许立即重试）")
    void shouldRollback_whenSendFailed() {
        when(repository.tryAcquireSendQuota(eq(VerificationChannel.EMAIL), eq(TARGET), any())).thenReturn(true);
        when(codeGenerator.generate(any(Integer.class))).thenReturn("654321");
        doThrow(new IllegalStateException("notification-service down"))
                .when(notificationGateway).sendVerificationCode(any(), any(), any());

        VerificationCodeIssuer.IssuedCode result = issuer.issue(VerificationChannel.EMAIL, TARGET, SCENE, "req-3");

        assertFalse(result.isSuccess());
        assertEquals(VerificationOutcome.SEND_FAILED, result.outcome());
        verify(repository).removeCode(VerificationChannel.EMAIL, TARGET);
        verify(repository).releaseSendQuota(VerificationChannel.EMAIL, TARGET);
    }

    @Test
    @DisplayName("签发 — 邮件通道：策略位数与通道参数正确传递")
    void shouldIssueEmailCode_whenChannelIsEmail() {
        when(repository.tryAcquireSendQuota(eq(VerificationChannel.EMAIL), eq("a@b.com"), any())).thenReturn(true);
        when(codeGenerator.generate(6)).thenReturn("112233");

        VerificationCodeIssuer.IssuedCode result = issuer.issue(VerificationChannel.EMAIL, "a@b.com", "RESET_PASSWORD", "req-4");

        assertTrue(result.isSuccess());
        verify(repository).save(VerificationChannel.EMAIL, "a@b.com", "112233", Duration.ofMinutes(5));
        verify(notificationGateway).sendVerificationCode(
                new VerificationCode(VerificationChannel.EMAIL, "a@b.com", "112233"), "req-4", "RESET_PASSWORD");
    }
}
