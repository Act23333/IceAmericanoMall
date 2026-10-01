package org.icedamericanomall.integration.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码生成器测试 —— 安全敏感工具（登录/注册验证码的唯一生成入口）。
 *
 * <p>此前 {@code SmsCodeGenerator} 零测试覆盖（V5.2 补强）。核心约束：
 * 密码学安全随机源、长度精确、首位非零（避免被 {@code parseInt} 吃掉前导零导致位数不符）。
 */
@DisplayName("SmsCodeGenerator — 验证码生成")
class SmsCodeGeneratorTest {

    @Test
    @DisplayName("生成 6 位验证码 → 长度精确为 6 且首位非 0")
    void shouldGenerateSixDigitsWithoutLeadingZero() {
        for (int i = 0; i < 50; i++) {
            String code = SmsCodeGenerator.generateCode(6);
            assertEquals(6, code.length());
            assertNotEquals('0', code.charAt(0), "首位不得为 0：验证码 " + code);
            assertTrue(code.chars().allMatch(Character::isDigit), "必须全为数字：" + code);
        }
    }

    @Test
    @DisplayName("允许前导零 → 长度精确为 6，且能生成以 0 开头的验证码")
    void shouldGenerateFixedLengthWithLeadingZerosAllowed() {
        boolean sawLeadingZero = false;
        for (int i = 0; i < 200; i++) {
            String code = SmsCodeGenerator.generateCodeWithLeadingZeros(6);
            assertEquals(6, code.length());
            assertTrue(code.chars().allMatch(Character::isDigit));
            if (code.charAt(0) == '0') {
                sawLeadingZero = true;
            }
        }
        assertTrue(sawLeadingZero, "200 次采样中应至少出现一次首位为 0（概率 1 - 0.9^200 ≈ 1）");
    }

    @Test
    @DisplayName("长度超过 9 位且不允许前导零 → 依然精确长度且首位非 0（走字符串分支）")
    void shouldHandleLengthOver9WithoutLeadingZero() {
        String code = SmsCodeGenerator.generateCode(12, false);
        assertEquals(12, code.length());
        assertNotEquals('0', code.charAt(0));
        // 默认重载（不允许前导零）在长度 >9 时同样走字符串分支
        String codeByOverload = SmsCodeGenerator.generateCode(12);
        assertEquals(12, codeByOverload.length());
        assertNotEquals('0', codeByOverload.charAt(0));
    }

    @Test
    @DisplayName("非法长度 → 抛 IllegalArgumentException（参数守卫）")
    void shouldRejectNonPositiveLength() {
        assertThrows(IllegalArgumentException.class, () -> SmsCodeGenerator.generateCode(0));
        assertThrows(IllegalArgumentException.class, () -> SmsCodeGenerator.generateCode(-1));
        assertThrows(IllegalArgumentException.class, () -> SmsCodeGenerator.generateCodeWithLeadingZeros(0));
    }
}
