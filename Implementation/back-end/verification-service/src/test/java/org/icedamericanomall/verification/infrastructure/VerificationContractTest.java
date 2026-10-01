package org.icedamericanomall.verification.infrastructure;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.infrastructure.constant.VerificationRedisKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨服务契约与策略常量测试（R5）。
 *
 * <p>本测试是「验证码 Redis Key 契约」的守护测试：只要有人改动前缀或 TTL，
 * 就会在此失败 —— 因为 {@code sms:code:} / {@code sms:limit:} 是与 user-service
 * 直发链路<b>共享</b>的 Key，改动即导致验证码互通失效。
 */
@DisplayName("校验 — 跨服务契约与策略")
class VerificationContractTest {

    @Test
    @DisplayName("R5 契约 — 短信 Key 前缀与 user-service 保持一致（sms:code: / sms:limit:）")
    void shouldKeepSmsKeyContractWithUserService() {
        assertEquals("sms:code:13800001111",
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "13800001111"));
        assertEquals("sms:limit:13800001111",
                VerificationRedisKeys.limitKey(VerificationChannel.SMS, "13800001111"));
        assertEquals("sms:fail:13800001111",
                VerificationRedisKeys.failureKey(VerificationChannel.SMS, "13800001111"));

        assertEquals("sms:code:", VerificationRedisKeys.SMS_CODE_PREFIX);
        assertEquals("sms:limit:", VerificationRedisKeys.SMS_LIMIT_PREFIX);
        assertEquals(Duration.ofMinutes(5), VerificationRedisKeys.CODE_TTL);
        assertEquals(Duration.ofSeconds(60), VerificationRedisKeys.LIMIT_TTL);
    }

    @Test
    @DisplayName("契约 — 邮件 Key 沿用同一命名规则（email:code: / email:limit: / email:fail:）")
    void shouldBuildEmailKeys() {
        assertEquals("email:code:a@b.com",
                VerificationRedisKeys.codeKey(VerificationChannel.EMAIL, "a@b.com"));
        assertEquals("email:limit:a@b.com",
                VerificationRedisKeys.limitKey(VerificationChannel.EMAIL, "a@b.com"));
        assertEquals("email:fail:a@b.com",
                VerificationRedisKeys.failureKey(VerificationChannel.EMAIL, "a@b.com"));
    }

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
