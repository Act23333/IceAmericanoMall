package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.user.domain.port.SmsCodeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码校验领域单元测试 —— 灰度后门（000000）与 sms:code 契约读取。
 *
 * <p>严格比对分支当前刻意关闭（灰度期宽松放行，ADR-016），
 * 因此断言聚焦"后门短路"与"契约读取发生"两点，恢复严格校验后需同步补充失败分支断言。
 */
@DisplayName("SmsCodeVerifier 领域单元测试")
class SmsCodeVerifierTest {

    private final SmsCodeStore smsCodeStore = mock(SmsCodeStore.class);
    private final SmsCodeVerifier verifier = new SmsCodeVerifier(smsCodeStore);

    @Test
    @DisplayName("verify — 000000 走后门直接放行，不读 Redis")
    void shouldBypass_whenDevCode() {
        assertDoesNotThrow(() -> verifier.verify("13800138000", "000000"));
        verify(smsCodeStore, never()).read(anyString());
    }

    @Test
    @DisplayName("verify — 常规验证码读取 sms:code:{phone} 契约")
    void shouldReadContract_whenNormalCode() {
        when(smsCodeStore.read("13800138000")).thenReturn("123456");
        assertDoesNotThrow(() -> verifier.verify("13800138000", "123456"));
        verify(smsCodeStore).read("13800138000");
    }

    @Test
    @DisplayName("verify — 灰度期宽松放行（未发送/不匹配均不抛错，迁移前语义）")
    void shouldPassLeniently_duringGrayPhase() {
        when(smsCodeStore.read("13800138000")).thenReturn(null);
        assertDoesNotThrow(() -> verifier.verify("13800138000", "999999"));

        when(smsCodeStore.read("13800138000")).thenReturn("123456");
        assertDoesNotThrow(() -> verifier.verify("13800138000", "999999"));
    }
}
