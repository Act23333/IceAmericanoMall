package org.icedamericanomall.verification.domain;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;
import org.icedamericanomall.verification.domain.service.VerificationCodeVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码校验领域服务单元测试。
 *
 * <p>覆盖：一次性消费（原子比对并删除）、错误计数与超限作废、过期判定、入参校验。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校验 — 验证码校验领域服务")
class VerificationCodeVerifierTest {

    private static final String TARGET = "13800001111";
    private static final String CODE = "123456";

    @Mock
    private VerificationCodeRepository repository;

    private VerificationCodeVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new VerificationCodeVerifier(repository, VerificationPolicy.DEFAULT);
    }

    @Test
    @DisplayName("校验消费 — 原子比对成功即消费，并清空错误计数")
    void shouldConsumeCodeAndReturnSuccess_whenCodeMatches() {
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, CODE)).thenReturn(true);

        VerificationOutcome outcome = verifier.verify(VerificationChannel.SMS, TARGET, CODE);

        assertEquals(VerificationOutcome.SUCCESS, outcome);
        verify(repository).clearFailures(VerificationChannel.SMS, TARGET);
        // 成功路径不应触碰「存在性判断」与「错误计数」
        verify(repository, never()).exists(any(), any());
        verify(repository, never()).incrementFailures(any(), any(), any());
    }

    @Test
    @DisplayName("校验消费 — 原子性：验证码一经消费即不可重放（第二次校验返回已过期）")
    void shouldNotAllowReplay_whenCodeAlreadyConsumed() {
        // 第一次：比对成功（仓储内部已删除）
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, CODE)).thenReturn(true);
        assertEquals(VerificationOutcome.SUCCESS, verifier.verify(VerificationChannel.SMS, TARGET, CODE));

        // 第二次：验证码已不存在 → 比对失败且 exists=false
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, CODE)).thenReturn(false);
        when(repository.exists(VerificationChannel.SMS, TARGET)).thenReturn(false);

        assertEquals(VerificationOutcome.CODE_EXPIRED, verifier.verify(VerificationChannel.SMS, TARGET, CODE));
        // 重放不该再次消费或清除验证码
        verify(repository, never()).removeCode(any(), any());
    }

    @Test
    @DisplayName("校验 — 验证码错误（未达上限）→ CODE_MISMATCH，验证码保留可继续尝试")
    void shouldReturnCodeMismatch_whenCodeWrongAndUnderLimit() {
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, "000000")).thenReturn(false);
        when(repository.exists(VerificationChannel.SMS, TARGET)).thenReturn(true);
        when(repository.incrementFailures(eq(VerificationChannel.SMS), eq(TARGET), any())).thenReturn(1);

        VerificationOutcome outcome = verifier.verify(VerificationChannel.SMS, TARGET, "000000");

        assertEquals(VerificationOutcome.CODE_MISMATCH, outcome);
        verify(repository, never()).removeCode(any(), any());
    }

    @Test
    @DisplayName("校验 — 错误次数达到 5 次 → 作废验证码并清空计数（防爆破）")
    void shouldDestroyCode_whenFailuresReachMax() {
        when(repository.consumeIfMatches(VerificationChannel.EMAIL, TARGET, "999999")).thenReturn(false);
        when(repository.exists(VerificationChannel.EMAIL, TARGET)).thenReturn(true);
        when(repository.incrementFailures(eq(VerificationChannel.EMAIL), eq(TARGET), any())).thenReturn(5);

        VerificationOutcome outcome = verifier.verify(VerificationChannel.EMAIL, TARGET, "999999");

        assertEquals(VerificationOutcome.TOO_MANY_ATTEMPTS, outcome);
        verify(repository).removeCode(VerificationChannel.EMAIL, TARGET);
        verify(repository).clearFailures(VerificationChannel.EMAIL, TARGET);
    }

    @Test
    @DisplayName("校验 — 验证码不存在（未发送/已过期）→ CODE_EXPIRED，且不累加错误次数")
    void shouldReturnCodeExpired_whenCodeAbsent() {
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, CODE)).thenReturn(false);
        when(repository.exists(VerificationChannel.SMS, TARGET)).thenReturn(false);

        assertEquals(VerificationOutcome.CODE_EXPIRED, verifier.verify(VerificationChannel.SMS, TARGET, CODE));
        verify(repository, never()).incrementFailures(any(), any(), any());
    }

    @Test
    @DisplayName("校验 — 空验证码 → PARAM_INVALID，不访问仓储")
    void shouldReturnParamInvalid_whenCodeBlank() {
        assertEquals(VerificationOutcome.PARAM_INVALID, verifier.verify(VerificationChannel.SMS, TARGET, " "));
        verify(repository, never()).consumeIfMatches(any(), any(), any());
    }

    @Test
    @DisplayName("校验 — 错误计数窗口使用策略值（30 分钟）")
    void shouldUsePolicyFailureWindow() {
        when(repository.consumeIfMatches(VerificationChannel.SMS, TARGET, "000001")).thenReturn(false);
        when(repository.exists(VerificationChannel.SMS, TARGET)).thenReturn(true);
        when(repository.incrementFailures(VerificationChannel.SMS, TARGET, Duration.ofMinutes(30))).thenReturn(2);

        assertEquals(VerificationOutcome.CODE_MISMATCH,
                verifier.verify(VerificationChannel.SMS, TARGET, "000001"));

        verify(repository).incrementFailures(VerificationChannel.SMS, TARGET, Duration.ofMinutes(30));
    }
}
