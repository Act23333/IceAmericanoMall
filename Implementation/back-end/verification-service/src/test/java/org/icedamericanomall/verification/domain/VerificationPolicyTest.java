package org.icedamericanomall.verification.domain;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码策略与通道解析单元测试 —— 纯领域，不依赖任何框架。
 *
 * <p>（原 {@code VerificationContractTest} 中守护「跨服务 Redis Key 契约（R5）」的用例已随
 * 契约解散删除；此处仅保留与键空间无关的策略/解析用例。）
 */
@DisplayName("校验 — 领域策略与通道解析")
class VerificationPolicyTest {

    @Test
    @DisplayName("通道解析 — 大小写/空格容错，非法与空值返回 empty")
    void shouldParseChannelTolerantly() {
        assertEquals(Optional.of(VerificationChannel.SMS), VerificationChannel.find(" sms "));
        assertEquals(Optional.of(VerificationChannel.EMAIL), VerificationChannel.find("email"));
        assertTrue(VerificationChannel.find("PUSH").isEmpty());
        assertTrue(VerificationChannel.find(null).isEmpty());
    }

    @Test
    @DisplayName("策略 — 默认值与业务约定一致（6 位 / 5 分钟 / 60 秒 / 5 次 / 30 分钟）")
    void shouldExposeDefaultPolicy() {
        VerificationPolicy policy = VerificationPolicy.DEFAULT;
        assertEquals(6, policy.codeLength());
        assertEquals(Duration.ofMinutes(5), policy.codeTtl());
        assertEquals(Duration.ofSeconds(60), policy.sendLimitWindow());
        assertEquals(5, policy.maxFailures());
        assertEquals(Duration.ofMinutes(30), policy.failureWindow());
    }

    @Test
    @DisplayName("策略 — 非法参数直接拒绝（位数越界 / 负数窗口）")
    void shouldRejectInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new VerificationPolicy(
                2, Duration.ofMinutes(1), Duration.ofSeconds(60), 5, Duration.ofMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> new VerificationPolicy(
                6, Duration.ZERO, Duration.ofSeconds(60), 5, Duration.ofMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> new VerificationPolicy(
                6, Duration.ofMinutes(5), Duration.ofSeconds(60), 0, Duration.ofMinutes(30)));
    }
}
