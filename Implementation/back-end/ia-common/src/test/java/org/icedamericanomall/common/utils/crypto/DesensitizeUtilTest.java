package org.icedamericanomall.common.utils.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 展示用脱敏规则单元测试 —— 掩码规则的唯一出口，逐字符锁定（含从 trade-service 归并的账号掩码）。
 */
@DisplayName("DesensitizeUtil — 展示层掩码规则")
class DesensitizeUtilTest {

    @Test
    @DisplayName("maskAccount — 长度 > 4：保留末 4 位，前缀以 **** 掩去（trade 提现出参口径）")
    void shouldKeepTailFour_whenAccountLongerThanFour() {
        assertEquals("****0123", DesensitizeUtil.maskAccount("6222021234567890123"));
        assertEquals("****5678", DesensitizeUtil.maskAccount("12345678"));
        assertEquals("****2345", DesensitizeUtil.maskAccount("12345"));
    }

    @Test
    @DisplayName("maskAccount — 长度 ≤ 4：全遮为 ****，不泄露原串长度")
    void shouldMaskAccountEntirely_whenLengthNotExceedingFour() {
        assertEquals("****", DesensitizeUtil.maskAccount("1234"));
        assertEquals("****", DesensitizeUtil.maskAccount("123"));
        assertEquals("****", DesensitizeUtil.maskAccount("1"));
    }

    @Test
    @DisplayName("maskAccount — null/空串：原样返回（保留「未填写」语义）")
    void shouldPreserveNullAndEmptyAccount() {
        assertNull(DesensitizeUtil.maskAccount(null));
        assertEquals("", DesensitizeUtil.maskAccount(""));
    }

    @Test
    @DisplayName("maskPhone — 11 位手机号保留前 3 后 4；长度不足原样返回")
    void shouldMaskPhone() {
        assertEquals("138****8000", DesensitizeUtil.maskPhone("13800138000"));
        assertEquals("139****9000", DesensitizeUtil.maskPhone("13900139000"));
        assertEquals("12345", DesensitizeUtil.maskPhone("12345"));
        assertNull(DesensitizeUtil.maskPhone(null));
    }

    @Test
    @DisplayName("maskEmail — 保留前 2 位；邮箱名 ≤ 2 位时整段掩去")
    void shouldMaskEmail() {
        assertEquals("ab***@example.com", DesensitizeUtil.maskEmail("abc@example.com"));
        assertEquals("***@example.com", DesensitizeUtil.maskEmail("ab@example.com"));
        assertEquals("***@example.com", DesensitizeUtil.maskEmail("a@example.com"));
        assertEquals("no-at-sign", DesensitizeUtil.maskEmail("no-at-sign"));
        assertNull(DesensitizeUtil.maskEmail(null));
    }

    @Test
    @DisplayName("mask — 按标识形态自动选择：手机号 / 邮箱 / 其它原样")
    void shouldAutoSelectRule_whenIdentifierMasked() {
        assertEquals("138****8000", DesensitizeUtil.mask("13800138000"));
        assertEquals("ab***@example.com", DesensitizeUtil.mask("abc@example.com"));
        assertEquals("some-openid", DesensitizeUtil.mask("some-openid"));
        assertNull(DesensitizeUtil.mask(null));
    }
}
