package org.icedamericanomall.verification.infrastructure.constant;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link VerificationRedisKeys} 场景隔离 Key 布局单元测试（ADR-051）。
 *
 * <p>码池隔离的**承载点就是 Key 组成**：签发与消费共用本类的 key 构建器，
 * 若未来重构把 scene 从 Key 中摘除，则场景隔离静默失效（mock 测试全绿也发现不了）
 * ——本测试是该机制的守卫。
 */
@DisplayName("VerificationRedisKeys — 场景参与 Key 组成（ADR-051 码池隔离守卫）")
class VerificationRedisKeysTest {

    private static final String TARGET = "13800138000";

    @Test
    @DisplayName("codeKey — 同一目标不同场景 Key 互不相同，且格式为 {prefix}:{scene}:{target}")
    void shouldIsolateCodeKeysByScene() {
        assertEquals("sms:code:REGISTER:" + TARGET,
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "REGISTER", TARGET));
        assertEquals("email:code:LOGIN:a@b.com",
                VerificationRedisKeys.codeKey(VerificationChannel.EMAIL, "LOGIN", "a@b.com"));
        assertNotEquals(
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "REGISTER", TARGET),
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "LOGIN", TARGET),
                "注册码与登录码必须落在不同 Key（跨场景互不可用）");
        assertNotEquals(
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "LOGIN", TARGET),
                VerificationRedisKeys.codeKey(VerificationChannel.SMS, "RESET_PASSWORD", TARGET),
                "登录码不可用于重置密码");
    }

    @Test
    @DisplayName("limitKey/failureKey — 限流与失败计数同样按场景独立预算")
    void shouldIsolateQuotaAndFailureKeysByScene() {
        assertEquals("sms:limit:LOGIN:" + TARGET,
                VerificationRedisKeys.limitKey(VerificationChannel.SMS, "LOGIN", TARGET));
        assertEquals("sms:fail:RESET_PASSWORD:" + TARGET,
                VerificationRedisKeys.failureKey(VerificationChannel.SMS, "RESET_PASSWORD", TARGET));
        assertEquals("email:limit:REGISTER:a@b.com",
                VerificationRedisKeys.limitKey(VerificationChannel.EMAIL, "REGISTER", "a@b.com"));
        assertNotEquals(
                VerificationRedisKeys.limitKey(VerificationChannel.SMS, "REGISTER", TARGET),
                VerificationRedisKeys.limitKey(VerificationChannel.SMS, "LOGIN", TARGET),
                "一个场景被刷不得耗尽其他场景的重发预算");
    }

    @Test
    @DisplayName("dailyKey — 全局日配额跨场景共享（无 scene 段，ADR-051 修订）")
    void dailyKeyIsGlobalAcrossScenes() {
        assertEquals("sms:daily:" + TARGET, VerificationRedisKeys.dailyKey(VerificationChannel.SMS, TARGET));
        assertEquals("email:daily:a@b.com", VerificationRedisKeys.dailyKey(VerificationChannel.EMAIL, "a@b.com"));
    }
}
